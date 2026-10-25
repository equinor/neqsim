"""NeqSim execution layer of the flow-assurance study report: steady hydraulics, thermal, hydrate equilibrium.

One case is solved ONCE; everything downstream (margins, drawings, tables, narrative) works on the returned plain
dictionaries and never calls the JVM again. Method scope (see ``fa_methods.METHODS``):

* Hydraulics: ``PipeBeggsAndBrills`` chain per segment, SRK equation of state, classic mixing rule, multiphase check.
  Steady state only. Beggs and Brill is a steady holdup/pressure-drop correlation; it does not resolve slugging.
* Hydrate equilibrium: ``SystemSrkCPAstatoil`` + ``ThermodynamicOperations.hydrateFormationTemperature``, evaluated on a
  pressure grid per inhibitor mass fraction and interpolated in ln(p).
* Network: backward solution for the required source pressures from a specified host-interface pressure; one merge
  (junction) at most.
"""
import hashlib
import json
import math
import os
import time

from fa_units import FlowAssuranceError, aqueous_wt_after_injection, lean_injection_rate, mass_rate_kg_s, check_rate, MW, convert

SOLVER_VERSION = "1.0"
HYDRATE_WATER_MOL = 0.05
INNER_TOL_BAR = 0.05
OUTER_TOL_BAR = 0.05
MAX_ITER = 14
STD_T_C, STD_P_BARA = 15.0, 1.01325
EROSIONAL_C_DEFAULT = None  # never defaulted: the erosional C-factor is a sourced project input


def neqsim_handle():
    """Return the ``jneqsim.neqsim`` package (starts the JVM) or raise :class:`FlowAssuranceError` when it is not installed."""
    try:
        from jneqsim import neqsim
    except ImportError as exc:  # pragma: no cover - environment dependent
        raise FlowAssuranceError("jneqsim is not installed; the NeqSim model runner needs it (%s)" % exc)
    return neqsim


def neqsim_version():
    """Best-effort NeqSim jar name/version (``unknown`` when it cannot be determined)."""
    try:
        import jpype

        neqsim_handle()
        for part in str(jpype.getClassPath()).split(os.pathsep):
            base = os.path.basename(part)
            if base.lower().startswith("neqsim") and base.lower().endswith(".jar"):
                return base
    except Exception:  # noqa: BLE001
        pass
    return "unknown"


class SolverError(FlowAssuranceError):
    """A calculation could not be completed (non-convergence, hydraulic domain failure)."""


# --- fluids -----------------------------------------------------------------------------------------
class FluidFactory:
    """Builds NeqSim fluids for a study fluid definition (hydrocarbon mole composition, optional water/inhibitor masses)."""

    def __init__(self, ns, fluid_def):
        self.ns = ns
        self.fd = fluid_def
        comps = fluid_def.get("components") or []
        if not comps:
            raise FlowAssuranceError("fluid %s has no components" % fluid_def.get("id"))
        names = [c["name"] for c in comps]
        if len(set(names)) != len(names):
            raise FlowAssuranceError("fluid %s has duplicate components" % fluid_def.get("id"))
        total = sum(float(c["mole_fraction"]) for c in comps)
        if abs(total - 1.0) > 1.0e-6:
            raise FlowAssuranceError("fluid %s: mole fractions sum to %.8f, expected 1 (+-1e-6)" % (fluid_def.get("id"), total))
        self.comps = [(c["name"], float(c["mole_fraction"])) for c in comps]
        probe = ns.thermo.system.SystemSrkEos(300.0, 1.0)
        for n, x in self.comps:
            probe.addComponent(n, x)
        self.mw = {n: probe.getComponent(n).getMolarMass() for n, _ in self.comps}
        self.mw_hc = sum(self.mw[n] * x for n, x in self.comps)  # kg/mol

    def _system(self, eos):
        s = self.ns.thermo.system
        return s.SystemSrkCPAstatoil(300.0, 100.0) if eos == "SRK-CPA" else s.SystemSrkEos(300.0, 100.0)

    def build(self, hc_kg_s, water_kg_s=0.0, inhibitor_kg_s=0.0, inhibitor="MEG", eos="SRK", t_c=20.0, p_bara=100.0):
        """Fluid with the given mass rates. Amounts are moles per second; the returned fluid is initialised but not flashed."""
        f = self._system(eos)
        n_hc = hc_kg_s / self.mw_hc
        for n, x in self.comps:
            f.addComponent(n, n_hc * x)
        if water_kg_s > 0:
            f.addComponent("water", water_kg_s / (MW["water"] * 1.0e-3))
        if inhibitor_kg_s > 0:
            f.addComponent(inhibitor, inhibitor_kg_s / (MW[inhibitor] * 1.0e-3))
        f.setMixingRule(10 if eos == "SRK-CPA" else "classic")
        f.setMultiPhaseCheck(True)
        if eos == "SRK-CPA":
            f.setHydrateCheck(True)
        f.setTemperature(273.15 + t_c)
        f.setPressure(p_bara)
        return f

    def std_gas_density(self, hc_kg_s=1.0):
        """Gas-phase density (kg/Sm3) of the dry hydrocarbon fluid at the declared standard conditions, and the gas mass fraction."""
        f = self.build(hc_kg_s, t_c=STD_T_C, p_bara=STD_P_BARA)
        self.ns.thermodynamicoperations.ThermodynamicOperations(f).TPflash()
        f.initProperties()
        if not f.hasPhaseType("gas"):
            return None, 0.0
        gas = f.getPhase("gas")
        beta = float(f.getPhase("gas").getBeta())
        return float(gas.getDensity("kg/m3")), beta * float(gas.getMolarMass()) / float(f.getMolarMass())


def phase_summary(stream):
    """Phase split of a solved stream: kg/s per phase type, volumetric flows (m3/s), density and Cp of the mixture."""
    fl = stream.getFluid()
    out = {"gas_kg_s": 0.0, "oil_kg_s": 0.0, "aqueous_kg_s": 0.0, "gas_m3_s": 0.0, "liquid_m3_s": 0.0}
    for i in range(int(fl.getNumberOfPhases())):
        ph = fl.getPhase(i)
        kind = str(ph.getPhaseTypeName())
        m = float(ph.getFlowRate("kg/sec"))
        rho = float(ph.getDensity("kg/m3"))
        if kind == "gas":
            out["gas_kg_s"] += m
            out["gas_m3_s"] += m / rho
        elif kind == "aqueous":
            out["aqueous_kg_s"] += m
            out["liquid_m3_s"] += m / rho
        else:
            out["oil_kg_s"] += m
            out["liquid_m3_s"] += m / rho
    out["n_phases"] = int(fl.getNumberOfPhases())
    return out


def stream_state(stream):
    """Scalar state of a solved stream (bara, degC, kg/s, phases, mixture density, Cp, enthalpy flow in W)."""
    fl = stream.getFluid()
    fl.initProperties()
    st = {"p_bara": float(stream.getPressure("bara")), "t_c": float(stream.getTemperature("C")),
          "mass_kg_s": float(stream.getFlowRate("kg/sec")), "rho_mix_kg_m3": float(fl.getDensity("kg/m3")),
          "cp_j_kgk": float(fl.getCp("J/kgK")), "enthalpy_w": float(fl.getEnthalpy())}
    st.update(phase_summary(stream))
    return st


# --- hydraulics -------------------------------------------------------------------------------------
def _segment_inlet(ns, name, fluid_state, mass_kg_s):
    st = ns.process.equipment.stream.Stream(name, fluid_state)
    st.setFlowRate(mass_kg_s, "kg/sec")
    st.run()
    return st


def run_segment(ns, seg, inlet_stream, ambient_t_c, increments, u_inner):
    """Solve one segment with ``PipeBeggsAndBrills``; returns ``(outlet_stream, record)``.

    Raises :class:`SolverError` when the solver rejects the state (e.g. non-positive pressure).
    """
    pbb = ns.process.equipment.pipeline.PipeBeggsAndBrills(seg["id"], inlet_stream)
    pbb.setLength(float(seg["length_m"]))
    pbb.setElevation(float(seg["dz_m"]))
    pbb.setDiameter(float(seg["id_m"]))
    pbb.setPipeWallRoughness(float(seg["roughness_m"]))
    pbb.setNumberOfIncrements(int(increments))
    pbb.setConstantSurfaceTemperature(float(ambient_t_c), "C")
    pbb.setHeatTransferCoefficient(float(u_inner))
    try:
        pbb.run()
    except Exception as exc:  # noqa: BLE001 - Java exceptions surface as generic Exception
        raise SolverError("segment %s: %s" % (seg["id"], str(exc).splitlines()[0][:160]))
    out = pbb.getOutletStream()
    p_out = float(out.getPressure("bara"))
    if not math.isfinite(p_out) or p_out <= 0.0:
        raise SolverError("segment %s: non-positive or non-finite outlet pressure" % seg["id"])
    pos = [float(x) for x in pbb.getLengthProfile()]
    rec = {"segment_id": seg["id"], "position_m": pos,
           "elevation_m": [float(seg["elev_start_m"]) + float(x) for x in pbb.getElevationProfile()],
           "p_bara": [float(x) for x in pbb.getPressureProfile()],
           "t_c": [float(x) - 273.15 for x in pbb.getTemperatureProfile()],
           "holdup": [float(x) for x in pbb.getLiquidHoldupProfile()],
           "v_sg_m_s": [float(x) for x in pbb.getGasSuperficialVelocityProfile()],
           "v_sl_m_s": [float(x) for x in pbb.getLiquidSuperficialVelocityProfile()],
           "v_mix_m_s": [float(x) for x in pbb.getMixtureSuperficialVelocityProfile()],
           "rho_mix_kg_m3": [float(x) for x in pbb.getMixtureDensityProfile()],
           "regime": [str(x) for x in pbb.getFlowRegimeProfileList()]}
    n = len(rec["position_m"])
    for key, arr in rec.items():
        if isinstance(arr, list) and len(arr) != n:
            raise SolverError("segment %s: profile '%s' has %d points, expected %d" % (seg["id"], key, len(arr), n))
    for key in ("p_bara", "t_c", "holdup", "rho_mix_kg_m3"):
        if not all(math.isfinite(v) for v in rec[key]) or (key == "p_bara" and min(rec[key]) <= 0.0):
            raise SolverError("segment %s: non-finite or non-positive %s" % (seg["id"], key))
    return out, rec


class Chain:
    """Series of segments solved as one unit for a given source stream definition."""

    def __init__(self, ns, segs, ambient, increments, state_provider=None):
        self.ns, self.segs, self.ambient, self.increments = ns, segs, ambient, increments

    def forward(self, fluid, mass_kg_s):
        """Run all segments in order from ``fluid`` (flashed state at the inlet). Returns ``(outlet_stream, records)``."""
        stream = _segment_inlet(self.ns, "in_" + self.segs[0]["id"], fluid, mass_kg_s)
        records = []
        for seg in self.segs:
            out, rec = run_segment(self.ns, seg, stream, self.ambient[seg["id"]], self.increments, seg["thermal"]["u_inner_w_m2k"])
            rec["state_in"] = stream_state(stream)
            rec["state_out"] = stream_state(out)
            records.append(rec)
            stream = _segment_inlet(self.ns, "in_next", out.getFluid().clone(), mass_kg_s)
        return stream, records


def secant_root(func, x0, x1, tol, max_iter=MAX_ITER, lower=None):
    """Secant solve of ``func(x) = 0``; ``func`` may raise :class:`SolverError` (treated as a large negative residual).

    Returns ``(x, residual, iterations, converged, payload)`` where ``payload`` is what ``func`` returned for the best x.
    """
    valid, infeasible = [], []

    def evaluate(x):
        try:
            r, payload = func(x)
        except SolverError:
            infeasible.append(x)
            return
        valid.append((x, r, payload))

    evaluate(x0)
    evaluate(x1)
    for it in range(max_iter + 1):
        best = min(valid, key=lambda t: abs(t[1])) if valid else None
        if best is not None and abs(best[1]) <= tol:
            return best[0], best[1], it, True, best[2]
        if it == max_iter:
            break
        if len(valid) >= 2:
            (xa, ra, _), (xb, rb, _) = sorted(valid, key=lambda t: abs(t[1]))[:2]
            x_new = xa - ra * (xa - xb) / (ra - rb) if ra != rb else xa - ra
        elif len(valid) == 1:
            x_new = valid[0][0] - valid[0][1]
        else:
            x_new = max(infeasible) * 1.5
        if best is not None:
            x_new = min(max(x_new, 0.5 * best[0]), 2.0 * best[0])
        floor = max(infeasible) * 1.02 if infeasible else None
        if lower is not None:
            floor = lower if floor is None else max(floor, lower)
        if floor is not None:
            x_new = max(x_new, floor)
        if any(abs(x_new - v[0]) < 1.0e-9 for v in valid):
            x_new = x_new + 0.5 * tol
        evaluate(x_new)
    best = min(valid, key=lambda t: abs(t[1])) if valid else None
    if best is None:
        return None, None, max_iter, False, None
    return best[0], best[1], max_iter, False, best[2]


# --- inhibitor / water bookkeeping ------------------------------------------------------------------
def source_streams(case, fluid_factory, std_density):
    """Per-source mass bookkeeping for a case: HC, water, lean inhibitor kg/s and the aqueous inhibitor mass fraction."""
    inh = case.get("inhibitor") or {}
    purity = float(inh.get("purity_wt", 1.0)) if inh else 1.0
    out = {}
    for node, spec in (case.get("sources") or {}).items():
        hc = mass_rate_kg_s(spec["hc_rate"], std_density_kg_sm3=std_density, mean_mw=fluid_factory.mw_hc * 1000.0)
        water = mass_rate_kg_s(spec["water_rate"]) if spec.get("water_rate") else 0.0
        lean = 0.0
        if spec.get("inhibitor_rate"):
            lean = mass_rate_kg_s(spec["inhibitor_rate"])
        elif spec.get("inhibitor_target_wt") is not None and water > 0:
            lean = lean_injection_rate(water, float(spec["inhibitor_target_wt"]), purity)
        wt = aqueous_wt_after_injection(water, lean, purity) if water > 0 else 0.0
        out[node] = {"hc_kg_s": hc, "water_kg_s": water, "lean_inhibitor_kg_s": lean, "purity_wt": purity if lean else None,
                     "aqueous_inhibitor_wt": wt, "t_c": float(spec["t_c"]),
                     "inhibitor_kg_s": lean * purity, "inhibitor_water_kg_s": lean * (1.0 - purity)}
    return out


# --- hydrate ----------------------------------------------------------------------------------------
class HydrateCurves:
    """Hydrate-formation temperature vs pressure for given aqueous inhibitor mass fractions (CPA), with a cache."""

    def __init__(self, ns, factory, cache=None):
        self.ns, self.factory, self.cache = ns, factory, cache if cache is not None else {}
        self.findings = []

    def point(self, p_bara, wt, inhibitor="MEG"):
        """T_hyd (degC) at one pressure and aqueous inhibitor mass fraction; ``None`` when the CPA solver does not return."""
        key = ("%.4f" % p_bara, "%.5f" % wt, inhibitor)
        if key in self.cache:
            return self.cache[key]
        water_mass = HYDRATE_WATER_MOL / (1.0 - HYDRATE_WATER_MOL) * MW["water"] * 1.0e-3  # kg water per mol hydrocarbon
        inh_mass = water_mass * wt / (1.0 - wt) if wt > 0 else 0.0
        f = self.factory.build(self.factory.mw_hc, water_kg_s=water_mass, inhibitor_kg_s=inh_mass, inhibitor=inhibitor, eos="SRK-CPA",
                               t_c=20.0, p_bara=p_bara)
        t0 = 273.15 + 20.0
        val = None
        try:
            self.ns.thermodynamicoperations.ThermodynamicOperations(f).hydrateFormationTemperature()
            t = float(f.getTemperature())
            if math.isfinite(t) and abs(t - t0) > 1.0e-6 and 230.0 < t < 330.0:
                val = t - 273.15
        except Exception as exc:  # noqa: BLE001
            self.findings.append({"level": "warning", "code": "HYDRATE-NOCONV", "message": "hydrate T not returned at %.1f bara, %.1f wt%% %s: %s"
                                  % (p_bara, 100 * wt, inhibitor, str(exc).splitlines()[0][:100])})
        if val is None:
            self.findings.append({"level": "warning", "code": "HYDRATE-UNAVAILABLE", "message": "hydrate equilibrium temperature unavailable at "
                                  "%.1f bara, %.1f wt%% %s (reported as unavailable, never as hydrate-free)" % (p_bara, 100 * wt, inhibitor)})
        self.cache[key] = val
        return val

    def curve(self, p_min, p_max, wt, grid, inhibitor="MEG"):
        """Hydrate curve on the study pressure grid points that bracket ``[p_min, p_max]``.

        Returns ``{wt, inhibitor, p_bara, t_c, n_requested, n_available, interpolation_check_k}``; the check is the difference
        between the ln(p) interpolation and a direct CPA point at the geometric midpoint of the first interval.
        Raises :class:`FlowAssuranceError` when the range lies outside the grid (extend ``solver.hydrate_grid_bara``).
        """
        grid = sorted(float(g) for g in grid)
        if p_min < grid[0] or p_max > grid[-1]:
            raise FlowAssuranceError("pressure range %.1f-%.1f bara lies outside the hydrate grid %.1f-%.1f bara; extend solver.hydrate_grid_bara"
                                     % (p_min, p_max, grid[0], grid[-1]))
        lo = max([g for g in grid if g <= p_min] or [grid[0]])
        hi = min([g for g in grid if g >= p_max] or [grid[-1]])
        pts = [g for g in grid if lo <= g <= hi]
        if len(pts) < 2:
            pts = grid[:2]
        temps = [self.point(p, wt, inhibitor) for p in pts]
        ok = [(p, t) for p, t in zip(pts, temps) if t is not None]
        check = None
        if len(ok) >= 2:
            pm = round(math.sqrt(ok[0][0] * ok[1][0]), 2)
            tm = self.point(pm, wt, inhibitor)
            if tm is not None:
                lin = ok[0][1] + (ok[1][1] - ok[0][1]) * (math.log(pm) - math.log(ok[0][0])) / (math.log(ok[1][0]) - math.log(ok[0][0]))
                check = abs(lin - tm)
        return {"wt": wt, "inhibitor": inhibitor, "p_bara": [p for p, _ in ok], "t_c": [t for _, t in ok],
                "n_requested": len(pts), "n_available": len(ok), "interpolation_check_k": check}


def interp_log_p(curve, p_bara):
    """Interpolate a hydrate curve in ln(p) (linear extrapolation at the ends is refused: returns ``None`` outside the grid)."""
    ps, ts = curve["p_bara"], curve["t_c"]
    if len(ps) < 2 or p_bara < ps[0] * (1.0 - 1e-9) or p_bara > ps[-1] * (1.0 + 1e-9):
        return None
    for i in range(len(ps) - 1):
        if ps[i] - 1e-9 <= p_bara <= ps[i + 1] + 1e-9:
            w = (math.log(p_bara) - math.log(ps[i])) / (math.log(ps[i + 1]) - math.log(ps[i]))
            return ts[i] + w * (ts[i + 1] - ts[i])
    return None


# --- case execution ---------------------------------------------------------------------------------
def _json_default(o):
    return str(o)


def cache_key(study_inputs, case, extra=None):
    """Complete key for a case result: fluid, geometry, case, solver settings, NeqSim version, solver version."""
    payload = {"fluid": study_inputs["fluid"], "segments": study_inputs["segments"], "case": case, "solver": study_inputs["solver"],
               "neqsim": study_inputs.get("neqsim_version"), "solver_version": SOLVER_VERSION, "extra": extra}
    return hashlib.sha256(json.dumps(payload, sort_keys=True, default=_json_default).encode("utf-8")).hexdigest()[:24]


def _ambient_for(case, seg):
    ov = (case.get("ambient_override_t_c") or {})
    if seg["id"] in ov:
        return float(ov[seg["id"]])
    if seg.get("kind") in ov:
        return float(ov[seg["kind"]])
    amb = seg.get("ambient") or {}
    if amb.get("t_c") is None:
        raise FlowAssuranceError("segment %s has no ambient temperature" % seg["id"])
    return float(amb["t_c"])


def run_case(ns, route_model, variant, fluid_def, case, solver, hydrate_cache=None, log=None):
    """Solve one case (steady hydraulics, thermal, hydrate curves) and return the case result dict.

    Args:
        ns: ``jneqsim.neqsim`` handle.
        route_model: model from :func:`fa_geometry.check_route`.
        variant: dict returned by :func:`fa_geometry.apply_variant`.
        fluid_def: study fluid definition.
        case: case definition (sources, boundary, inhibitor, ...).
        solver: settings ``{increments, ...}``.
        hydrate_cache: optional shared dict for hydrate points.
        log: optional callable for progress messages.

    The function never raises for a calculation that does not converge: the result has ``status`` ``failed`` and a
    finding that says why. Input errors raise :class:`FlowAssuranceError`.
    """
    t_start = time.time()
    say = log or (lambda m: None)
    factory = FluidFactory(ns, fluid_def)
    std_rho, _gas_frac = factory.std_gas_density()
    inc = int(solver.get("increments", 6))
    segs = {s["id"]: s for s in route_model["segments"]}
    paths = variant["active_paths"]
    ambient = {sid: _ambient_for(case, segs[sid]) for chain in paths.values() for sid in chain}
    srcs = source_streams(case, factory, std_rho)
    for node in variant["inactive_sources"]:
        if node in srcs and srcs[node]["hc_kg_s"] > 0:
            raise FlowAssuranceError("case %s gives a rate for isolated source %s in variant %s" % (case["id"], node, variant["id"]))
    for pid in paths:
        node = pid.split("->")[0]
        if node not in srcs:
            raise FlowAssuranceError("case %s has no source definition for active source %s" % (case["id"], node))
    out_bnd = (case.get("boundary") or {}).get("outlet") or {}
    if out_bnd.get("type") != "pressure":
        raise FlowAssuranceError("case %s: only an outlet boundary of type 'pressure' is supported (got %r)" % (case["id"], out_bnd.get("type")))
    p_host = convert(float(out_bnd["value"]["value"]), out_bnd["value"]["unit"], "bara")
    findings, solves = [], []
    # shared chain: segments common to all active paths
    chains = list(paths.values())
    common = []
    for col in zip(*[list(reversed(c)) for c in chains]):
        if len(set(col)) == 1:
            common.append(col[0])
        else:
            break
    common.reverse()
    branches = {pid: chain[: len(chain) - len(common)] for pid, chain in paths.items()}
    junction = None
    if len(paths) > 1:
        if not common:
            raise FlowAssuranceError("active paths do not share a trunk; a network without a common downstream segment is not supported")
        junction = segs[common[0]]["from"]
        if any(not b for b in branches.values()):
            raise FlowAssuranceError("a nested junction is not supported (a branch has no private segments); extension point: fa_solver.run_case")
    trunk = Chain(ns, [segs[s] for s in common], ambient, inc) if common else None
    records = {}
    node_states = {}

    def solve_chain(node, seg_ids, p_target, guess, label):
        """Required source pressure so that the chain outlet equals ``p_target``; returns ``((out_stream, recs, p_in), ok)``."""
        chain = Chain(ns, [segs[s] for s in seg_ids], ambient, inc)
        lean = srcs[node]
        mass = lean["hc_kg_s"] + lean["water_kg_s"] + lean["lean_inhibitor_kg_s"]

        def residual(p_in):
            f = factory.build(lean["hc_kg_s"], lean["water_kg_s"] + lean["inhibitor_water_kg_s"], lean["inhibitor_kg_s"], "MEG", "SRK",
                              lean["t_c"], p_in)
            ns.thermodynamicoperations.ThermodynamicOperations(f).TPflash()
            out_stream, recs = chain.forward(f, mass)
            return float(out_stream.getPressure("bara")) - p_target, (out_stream, recs, p_in)

        x, r, it, ok, payload = secant_root(residual, guess, guess + 2.0, INNER_TOL_BAR, lower=p_target * 1.0001)
        solves.append({"what": label, "iterations": it, "residual_bar": r, "converged": ok, "required_p_in_bara": x})
        if payload is None:
            raise SolverError("%s: no feasible inlet pressure found" % label)
        return payload, ok

    junction_balance = None
    try:
        if junction is None:
            pid = next(iter(paths))
            node = pid.split("->")[0]
            (out_stream, recs, p_in), ok = solve_chain(node, paths[pid], p_host, p_host * 1.5, "single path %s" % pid)
            records[pid] = recs
            node_states[node] = dict(recs[0]["state_in"])
            node_states[route_model["sink"]] = stream_state(out_stream)
            converged = ok
        else:
            memo = {"p_j": None, "p_in": {pid: p_host * 1.8 for pid in paths}}

            def outer(p_j):
                branch_results = {}
                for pid in paths:
                    guess = memo["p_in"][pid] + ((p_j - memo["p_j"]) if memo["p_j"] is not None else 0.0)
                    payload, _ok = solve_chain(pid.split("->")[0], branches[pid], p_j, guess, "branch %s" % pid)
                    memo["p_in"][pid] = payload[2]
                    branch_results[pid] = payload
                memo["p_j"] = p_j
                mixer = ns.process.equipment.mixer.Mixer("junction")
                for out_stream, _recs, _p in branch_results.values():
                    mixer.addStream(out_stream)
                mixer.run()
                mout = mixer.getOutletStream()
                t_chain, t_recs = trunk.forward(mout.getFluid().clone(), float(mout.getFlowRate("kg/sec")))
                return float(t_chain.getPressure("bara")) - p_host, (branch_results, mout, t_chain, t_recs, p_j)

            x, r, it, ok, payload = secant_root(outer, p_host * 1.3, p_host * 1.3 + 3.0, OUTER_TOL_BAR, max_iter=8, lower=p_host * 1.0001)
            solves.append({"what": "junction pressure", "iterations": it, "residual_bar": r, "converged": ok, "required_p_in_bara": x})
            if payload is None:
                raise SolverError("no feasible junction pressure found")
            branch_results, mout, t_chain, t_recs, p_j = payload
            for pid, (out_stream, recs, p_in) in branch_results.items():
                records[pid] = recs
                node_states[pid.split("->")[0]] = dict(recs[0]["state_in"])
            junction_state = stream_state(mout)
            node_states[junction] = junction_state
            node_states[route_model["sink"]] = stream_state(t_chain)
            records["TRUNK"] = t_recs
            in_mass = sum(float(o.getFlowRate("kg/sec")) for o, _r, _p in branch_results.values())
            in_h = sum(stream_state(o)["enthalpy_w"] for o, _r, _p in branch_results.values())
            junction_balance = {"mass_in_kg_s": in_mass, "mass_out_kg_s": junction_state["mass_kg_s"],
                                "mass_rel_err": abs(in_mass - junction_state["mass_kg_s"]) / max(in_mass, 1e-12),
                                "enthalpy_in_w": in_h, "enthalpy_out_w": junction_state["enthalpy_w"],
                                "enthalpy_rel_err": abs(in_h - junction_state["enthalpy_w"]) / max(abs(in_h), 1.0)}
            converged = ok and all(s["converged"] for s in solves)
    except SolverError as exc:
        findings.append({"level": "error", "code": "SOLVER-DOMAIN", "message": str(exc)})
        say("case %s: FAILED (%s)" % (case["id"], exc))
        return {"case_id": case["id"], "status": "failed", "variant": variant["id"], "active_paths": {k: list(v) for k, v in paths.items()},
                "branch_segments": branches, "trunk_segments": common, "junction": junction, "sink": route_model["sink"],
                "host_p_bara": p_host, "sources": srcs, "streams": {}, "segments": {}, "profiles": {}, "hydrate_curves": {},
                "junction_balance": None, "solves": solves, "findings": findings, "std_gas_density_kg_sm3": std_rho,
                "mean_hc_molar_mass_g_mol": factory.mw_hc * 1000.0,
                "timings_s": {"case_total": round(time.time() - t_start, 2), "hydrate_curves": 0.0}}
    for s in solves:
        if not s["converged"]:
            findings.append({"level": "error", "code": "SOLVER-NONCONVERGED", "message": "%s did not reach %.3f bar in %d iterations (residual %s bar)"
                             % (s["what"], INNER_TOL_BAR, s["iterations"], "n/a" if s["residual_bar"] is None else "%.3f" % s["residual_bar"])})
    # profiles per path (branch segments, then trunk)
    profiles, seg_results = {}, {}
    for pid, chain_ids in paths.items():
        recs = list(records.get(pid, [])) if junction is not None else list(records[pid])
        if junction is not None:
            recs = recs + list(records["TRUNK"])
        prof = {k: [] for k in ("chainage_m", "segment_id", "elevation_m", "p_bara", "t_c", "holdup", "v_sg_m_s", "v_sl_m_s", "v_mix_m_s",
                                "rho_mix_kg_m3", "regime")}
        ch0 = 0.0
        for rec in recs:
            for i, pos in enumerate(rec["position_m"]):
                if prof["chainage_m"] and i == 0:
                    continue
                prof["chainage_m"].append(ch0 + pos)
                prof["segment_id"].append(rec["segment_id"])
                for k in ("elevation_m", "p_bara", "t_c", "holdup", "v_sg_m_s", "v_sl_m_s", "v_mix_m_s", "rho_mix_kg_m3", "regime"):
                    prof[k].append(rec[k][i])
            ch0 += rec["position_m"][-1]
            seg = segs[rec["segment_id"]]
            hold = rec["holdup"]
            area = math.pi / 4.0 * seg["id_m"] ** 2
            seg_results[rec["segment_id"]] = {
                "p_in_bara": rec["p_bara"][0], "p_out_bara": rec["p_bara"][-1], "dp_bar": rec["p_bara"][0] - rec["p_bara"][-1],
                "t_in_c": rec["t_c"][0], "t_out_c": rec["t_c"][-1], "length_m": seg["length_m"], "dz_m": seg["dz_m"],
                "u_inner_w_m2k": seg["thermal"]["u_inner_w_m2k"], "ambient_t_c": ambient[rec["segment_id"]],
                "holdup_mean": sum(hold) / len(hold), "holdup_max": max(hold), "v_mix_max_m_s": max(rec["v_mix_m_s"]),
                "rho_mix_mean_kg_m3": sum(rec["rho_mix_kg_m3"]) / len(rec["rho_mix_kg_m3"]),
                "cp_j_kgk": 0.5 * (rec["state_in"]["cp_j_kgk"] + rec["state_out"]["cp_j_kgk"]),
                "liquid_inventory_m3": sum(h * area * seg["length_m"] / len(hold) for h in hold),
                "regimes": sorted(set(rec["regime"])), "mass_kg_s": rec["state_in"]["mass_kg_s"]}
        profiles[pid] = prof
    # hydrate curves per distinct aqueous inhibitor fraction along the active paths
    t_h = time.time()
    hyd = HydrateCurves(ns, factory, hydrate_cache)
    wts = {"uninhibited": 0.0}
    tot_water = sum(s["water_kg_s"] for n, s in srcs.items() if n in {p.split("->")[0] for p in paths})
    for node, s in srcs.items():
        if node in {p.split("->")[0] for p in paths}:
            wts["source:%s" % node] = s["aqueous_inhibitor_wt"]
    if junction is not None and tot_water > 0:
        inh_mass = sum(s["inhibitor_kg_s"] for n, s in srcs.items() if n in {p.split("->")[0] for p in paths})
        wat = tot_water + sum(s["inhibitor_water_kg_s"] for n, s in srcs.items() if n in {p.split("->")[0] for p in paths})
        wts["mixed:%s" % junction] = inh_mass / (inh_mass + wat)
    all_p = [p for prof in profiles.values() for p in prof["p_bara"]]
    so = (case.get("shut_in") or {}).get("settle_out_pressure_bara")
    p_lo, p_hi = min(all_p + ([float(so)] if so else [])), max(all_p + ([float(so)] if so else []))
    curves = {}
    grid = solver.get("hydrate_grid_bara") or [40, 55, 70, 90, 110, 140, 180, 230]
    for key, wt in wts.items():
        curves[key] = hyd.curve(p_lo, p_hi, wt, grid)
    findings.extend(hyd.findings)
    t_hyd_s = time.time() - t_h
    result = {"case_id": case["id"], "status": "converged" if converged else "failed", "variant": variant["id"],
              "active_paths": {k: list(v) for k, v in paths.items()}, "branch_segments": branches, "trunk_segments": common,
              "junction": junction, "sink": route_model["sink"], "host_p_bara": p_host,
              "sources": srcs, "streams": node_states, "segments": seg_results, "profiles": profiles,
              "hydrate_curves": curves, "junction_balance": junction_balance, "solves": solves, "findings": findings,
              "std_gas_density_kg_sm3": std_rho, "mean_hc_molar_mass_g_mol": factory.mw_hc * 1000.0,
              "timings_s": {"case_total": round(time.time() - t_start, 2), "hydrate_curves": round(t_hyd_s, 2)}}
    say("case %s: %s in %.1f s" % (case["id"], result["status"], time.time() - t_start))
    return result
