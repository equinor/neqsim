"""Verification and benchmark fixtures for the flow-assurance solver layer.

Each function returns a record ``{id, title, kind, status, value, reference, deviation, tolerance, unit, provenance, note}``.
``kind`` separates ANALYTIC verification (closed-form or first-principles checks of the implementation),
INDEPENDENT validation (published or measured data not used for tuning) and CALIBRATION (data the model was tuned to).
Tolerances are declared here before the comparison is made; they are never loosened to pass.
"""
import math

import fa_solver
import fa_thermal
from fa_units import MW, hammerschmidt_depression_k

G = 9.80665


def _rec(bid, title, kind, value, reference, tol, unit, provenance, note="", tol_kind="abs"):
    dev = None if value is None or reference is None else value - reference
    if tol_kind == "rel" and dev is not None and reference:
        dev = dev / reference
    status = "UNAVAILABLE" if dev is None else ("PASS" if abs(dev) <= tol else "FAIL")
    return {"id": bid, "title": title, "kind": kind, "status": status, "value": value, "reference": reference, "deviation": dev,
            "tolerance": tol, "tolerance_kind": tol_kind, "unit": unit, "provenance": provenance, "note": note}


def _water_stream(ns, p_bara, t_c, kg_s):
    f = ns.thermo.system.SystemSrkEos(273.15 + t_c, p_bara)
    f.addComponent("water", 1.0)
    f.setMixingRule("classic")
    st = ns.process.equipment.stream.Stream("w", f)
    st.setFlowRate(kg_s, "kg/sec")
    st.run()
    return st


def hydrostatic(ns):
    """Vertical water column: dP must equal rho*g*h (+ negligible friction) within 1.5 %."""
    st = _water_stream(ns, 50.0, 20.0, 0.5)
    seg = {"id": "BM-HYDROSTATIC", "length_m": 100.0, "dz_m": 100.0, "elev_start_m": 0.0, "id_m": 0.2, "roughness_m": 4.5e-5}
    out, rec = fa_solver.run_segment(ns, seg, st, 20.0, 5, 1.0e-6)
    rho = 0.5 * (rec["rho_mix_kg_m3"][0] + rec["rho_mix_kg_m3"][-1])
    dp_model = (rec["p_bara"][0] - rec["p_bara"][-1]) * 1.0e5
    return _rec("BM-HYDROSTATIC", "Hydrostatic head of a water-filled 100 m vertical pipe", "analytic_verification", dp_model / (rho * G * 100.0),
                1.0, 0.015, "ratio dP_model / (rho g h)", "analytic: dP = rho g h", "Mean of inlet and outlet mixture density; friction negligible at 0.016 m/s.")


def single_phase_friction(ns):
    """Dry single-phase gas friction vs Darcy-Weisbach with Colebrook friction factor (3 % band)."""
    f = ns.thermo.system.SystemSrkEos(273.15 + 20.0, 100.0)
    f.addComponent("methane", 1.0)
    f.setMixingRule("classic")
    st = ns.process.equipment.stream.Stream("g", f)
    st.setFlowRate(8.0, "kg/sec")
    st.run()
    seg = {"id": "BM-FRICTION", "length_m": 5000.0, "dz_m": 0.0, "elev_start_m": 0.0, "id_m": 0.3, "roughness_m": 4.5e-5}
    out, rec = fa_solver.run_segment(ns, seg, st, 20.0, 5, 1.0e3)
    d, length, eps = 0.3, 5000.0, 4.5e-5
    fl_in, fl_out = st.getFluid(), out.getFluid()
    fl_in.initProperties()
    fl_out.initProperties()
    rho = 0.5 * (fl_in.getDensity("kg/m3") + fl_out.getDensity("kg/m3"))
    mu = 0.5 * (fl_in.getViscosity("kg/msec") + fl_out.getViscosity("kg/msec"))
    area = math.pi * d * d / 4.0
    v = 8.0 / (rho * area)
    re = rho * v * d / mu
    ff = 0.02
    for _ in range(60):
        ff = (-2.0 * math.log10(eps / (3.7 * d) + 2.51 / (re * math.sqrt(ff)))) ** -2
    dp_ref = ff * length / d * rho * v * v / 2.0 / 1.0e5
    dp_model = rec["p_bara"][0] - rec["p_bara"][-1]
    return _rec("BM-FRICTION", "Single-phase gas friction pressure drop (methane, 100 bara, 5 km, 0.3 m)", "analytic_verification", dp_model, dp_ref,
                0.03, "relative deviation vs Darcy-Weisbach/Colebrook", "analytic: Darcy-Weisbach, Colebrook-White, mean properties",
                "Reynolds number %.2e; reference dP %.4f bar." % (re, dp_ref), tol_kind="rel")


def heat_loss_inner_area(ns):
    """Single-phase water with a specified U: outlet temperature vs the closed-form NTU solution (0.3 K)."""
    st = _water_stream(ns, 20.0, 60.0, 10.0)
    seg = {"id": "BM-HEATLOSS", "length_m": 2000.0, "dz_m": 0.0, "elev_start_m": 0.0, "id_m": 0.2, "roughness_m": 4.5e-5}
    u, t_amb = 5.0, 4.0
    out, rec = fa_solver.run_segment(ns, seg, st, t_amb, 10, u)
    cp = st.getFluid().getCp("J/kgK")
    ntu = u * math.pi * 0.2 * 2000.0 / (10.0 * cp)
    t_ref = t_amb + (60.0 - t_amb) * math.exp(-ntu)
    return _rec("BM-HEATLOSS", "Heat loss with specified U referenced to the INNER area (water, 2 km)", "analytic_verification", rec["t_c"][-1], t_ref,
                0.3, "K", "analytic: T_out = T_amb + (T_in - T_amb) exp(-U pi D L / (m cp))",
                "Confirms that PipeBeggsAndBrills SPECIFIED_U uses pi*ID*L, so U must be the inner-area-referenced value.")


def hydrate_methane(ns):
    """CPA hydrate equilibrium for methane + water at three pressures against nominal literature points (1.5 K band)."""
    ref = ((43.0, 5.0), (76.0, 10.0), (140.0, 15.0))
    fd = {"id": "methane", "components": [{"name": "methane", "mole_fraction": 1.0}]}
    factory = fa_solver.FluidFactory(ns, fd)
    hyd = fa_solver.HydrateCurves(ns, factory)
    out = []
    for p, t_ref in ref:
        val = hyd.point(p, 0.0)
        out.append(_rec("BM-HYDRATE-CH4-%d" % int(p), "Methane hydrate equilibrium temperature at %.0f bara" % p, "independent_validation", val, t_ref, 1.5,
                        "degC", "secondary: nominal methane-water points quoted in the repository skill neqsim-flow-assurance; the primary "
                                "literature table was NOT re-read for this study",
                        "Independent of the study fluid; not used for tuning. Provenance is secondary, so the status is a screening check only."))
    return out


def hydrate_water_basis_sensitivity(ns, fluid_def, p_bara=100.0):
    """Change of T_hyd when the free-water mole fraction of the evaluation fluid changes (information, no pass/fail)."""
    factory = fa_solver.FluidFactory(ns, fluid_def)
    old = fa_solver.HYDRATE_WATER_MOL
    vals = {}
    try:
        for x in (0.02, 0.05):
            fa_solver.HYDRATE_WATER_MOL = x
            vals[x] = fa_solver.HydrateCurves(ns, factory).point(p_bara, 0.0)
    finally:
        fa_solver.HYDRATE_WATER_MOL = old
    spread = None if None in vals.values() else abs(vals[0.02] - vals[0.05])
    rec = _rec("SENS-HYD-WATER", "Sensitivity of T_hyd to the free-water mole fraction of the evaluation fluid (2 % vs 5 %)", "sensitivity", spread, 0.0,
               1.0, "K", "model sensitivity", "T_hyd values %s; judged against a 1 K band." % {str(k): v for k, v in vals.items()})
    return rec


def hammerschmidt_crosscheck(ns, fluid_def, p_bara=100.0, wt=0.20):
    """CPA hydrate depression for ``wt`` MEG vs the Hammerschmidt screening estimate (information only)."""
    factory = fa_solver.FluidFactory(ns, fluid_def)
    hyd = fa_solver.HydrateCurves(ns, factory)
    t0, t1 = hyd.point(p_bara, 0.0), hyd.point(p_bara, wt)
    depression = None if t0 is None or t1 is None else t0 - t1
    hs = hammerschmidt_depression_k(wt)
    return {"id": "XCHECK-HAMMERSCHMIDT", "title": "CPA MEG depression vs the Hammerschmidt screening estimate at %.0f wt%% MEG, %.0f bara" % (100 * wt, p_bara),
            "kind": "crosscheck", "status": "INFO", "value": depression, "reference": hs, "deviation": None if depression is None else depression - hs,
            "tolerance": None, "tolerance_kind": None, "unit": "K", "provenance": "empirical screening rule (Hammerschmidt); not an independent validation",
            "note": "The empirical rule is not a validation; the difference is reported, not removed. Inhibited margins use the CPA values."}


def cooldown_ode_check():
    """RK4 integration against the closed-form cooldown time (0.5 % band)."""
    tau = fa_thermal.tau_s(250.0, 3000.0, 0.25, 90.0, 500.0, 3.0)
    closed = fa_thermal.time_to_temperature_s(60.0, 4.0, 20.0, tau)
    num = fa_thermal.rk4_time_to_temperature_s(60.0, 4.0, 20.0, tau, dt_s=30.0)
    return _rec("BM-COOLDOWN-ODE", "Lumped cooldown: RK4 integration vs closed-form time to 20 degC", "analytic_verification", num / 3600.0, closed / 3600.0,
                0.005, "relative deviation", "analytic: exponential cooldown", "Verifies the implementation of the lumped formula only; it is not an experimental qualification.", tol_kind="rel")


def field_validation(basis, results):
    """Model-versus-measured records declared in ``basis['field_validation']`` (measured data supplied by the study team).

    Each declaration: ``{id, title, kind (field_calibration | field_validation), case, quantity (source_pressure_bara |
    host_arrival_t_c), node (source node for source pressure), measured, unit, tolerance, tolerance_kind, provenance, note}``.
    The tolerance is part of the declaration (declared before the comparison); records are never loosened here. A case that
    did not converge gives status UNAVAILABLE, never a pass. ``field_calibration`` marks data the model inputs were tuned to.
    """
    out = []
    for d in basis.get("field_validation") or []:
        res = results.get(d.get("case"))
        value = None
        if res and res.get("status") == "converged":
            if d.get("quantity") == "source_pressure_bara":
                st = (res.get("streams") or {}).get(d.get("node"))
                value = None if not st else st.get("p_bara")
            elif d.get("quantity") == "host_arrival_t_c":
                st = (res.get("streams") or {}).get(res.get("sink"))
                value = None if not st else st.get("t_c")
        kind = d.get("kind", "field_validation")
        out.append(_rec(d["id"], d["title"], kind, value, d.get("measured"), float(d["tolerance"]), d.get("unit", ""), d.get("provenance", "measured (unverified)"),
                        d.get("note", ""), tol_kind=d.get("tolerance_kind", "abs")))
    return out


def run_all(ns, fluid_def, log=None):
    """Run every fixture; a fixture that raises is recorded as FAIL with the message (never silently skipped)."""
    say = log or (lambda m: None)
    records = []
    for fn in (hydrostatic, single_phase_friction, heat_loss_inner_area, cooldown_ode_check):
        try:
            args = (ns,) if fn is not cooldown_ode_check else ()
            records.append(fn(*args))
        except Exception as exc:  # noqa: BLE001
            records.append({"id": fn.__name__.upper(), "title": fn.__name__, "kind": "analytic_verification", "status": "FAIL", "value": None,
                            "reference": None, "deviation": None, "tolerance": None, "tolerance_kind": None, "unit": "", "provenance": "fixture error",
                            "note": str(exc)[:200]})
    for fn in (lambda: hydrate_methane(ns), lambda: [hydrate_water_basis_sensitivity(ns, fluid_def)], lambda: [hammerschmidt_crosscheck(ns, fluid_def)]):
        try:
            records.extend(fn())
        except Exception as exc:  # noqa: BLE001
            records.append({"id": "FIXTURE-ERROR", "title": "benchmark fixture", "kind": "independent_validation", "status": "FAIL", "value": None,
                            "reference": None, "deviation": None, "tolerance": None, "tolerance_kind": None, "unit": "", "provenance": "fixture error",
                            "note": str(exc)[:200]})
    say("benchmarks: %s" % {r["id"]: r["status"] for r in records})
    return records
