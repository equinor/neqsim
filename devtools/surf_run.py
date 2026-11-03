"""Case execution and result extraction for the SURF study.

Each (concept, case) is solved once: the generated production and injection domains are composed with a host separator in a
``ProcessModel`` (one ``ProcessSystem`` per area), run, and every number needed later (node pressures, edge rates, route
profiles, well coupling evidence, host arrival, balances) is extracted once into a JSON-serialisable record. Records are
cached on a key that holds the specification, concept, case, mesh factor, NeqSim version and toolkit version.
"""
import hashlib
import json
import math
import os
import time

import surf_model
import surf_spec
from fa_units import FlowAssuranceError

RUN_VERSION = "1.0"


class RunError(FlowAssuranceError):
    """A case could not be solved or extracted."""


def cache_key(plan, domain, segment_factor):
    """Hash of everything that influences a domain result: the operation plan (all NeqSim inputs), mesh factor, NeqSim and toolkit version."""
    body = [dict(plan[0], name=None)] + list(plan[1:])
    payload = {"plan": body, "domain": domain, "mesh": segment_factor, "neqsim": surf_model.neqsim_version(), "run": RUN_VERSION}
    return hashlib.sha256(json.dumps(payload, sort_keys=True, default=str).encode("utf-8")).hexdigest()[:20]


def _f(v, nd=6):
    try:
        v = float(v)
    except (TypeError, ValueError):
        return None
    return round(v, nd) if math.isfinite(v) else None


def _fluid_props(C, fluid):
    """Mixture density (kg/m3), heat capacity (J/kgK), gas fraction and phase count of a clone flashed at its own state."""
    if fluid is None:
        return None
    f = fluid.clone()
    C("neqsim.thermodynamicoperations.ThermodynamicOperations")(f).TPflash()
    f.initProperties()
    n = int(f.getNumberOfPhases())
    return {"density_kg_m3": _f(f.getDensity("kg/m3")), "cp_j_kgk": _f(f.getCp("J/kgK")), "n_phases": n, "temperature_c": _f(f.getTemperature("C")),
            "pressure_bara": _f(f.getPressure("bara"))}


def extract_edge(C, domain, eid, e):
    """Result record of one canonical edge: rate, end states, profile, fluid properties at the outlet."""
    net = domain.topology.getHydraulicNetwork()
    pipe = net.getPipe(eid)
    rec = {"id": eid, "tag": e["tag"], "flow_kg_s": _f(pipe.getFlowRate()), "length_m": _f(pipe.getLength()), "diameter_m": _f(pipe.getDiameter()),
           "model": str(pipe.getHydraulicModelType()), "model_status": str(pipe.getHydraulicModelStatus()) if hasattr(pipe, "getHydraulicModelStatus") else None,
           "u_value_w_m2k": _f(pipe.getOverallHeatTransferCoeff()), "ambient_c": _f(pipe.getAmbientTemperature() - 273.15)}
    try:
        rec["outlet_temperature_c"] = _f(pipe.getOutletTemperature() - 273.15)
    except Exception:  # noqa: BLE001
        rec["outlet_temperature_c"] = None
    hp = pipe.getHydraulicProfile()
    p_from, p_to = net.getNodePressure(e["from"]), net.getNodePressure(e["to"])
    if hp is not None and len(hp.getPositionM()) > 0:
        pos = [float(x) for x in hp.getPositionM()]
        rec["profile"] = {"position_m": [_f(x, 3) for x in pos], "pressure_bara": [_f(x / 1.0e5, 4) for x in hp.getPressurePa()],
                          "temperature_c": [_f(x - 273.15, 3) for x in hp.getTemperatureK()], "liquid_holdup": [_f(x, 5) for x in hp.getLiquidHoldup()],
                          "vsg_m_s": [_f(x, 4) for x in hp.getGasSuperficialVelocityMs()], "vsl_m_s": [_f(x, 4) for x in hp.getLiquidSuperficialVelocityMs()],
                          "kind": "distributed"}
    else:
        try:
            t_in = _f(pipe.getInletFluid().getTemperature("C"), 3)
        except Exception:  # noqa: BLE001
            t_in = _f(net.getNode(e["from"]).getTemperature() - 273.15, 3)
        rec["profile"] = {"position_m": [0.0, _f(pipe.getLength(), 3)], "pressure_bara": [_f(p_from, 4), _f(p_to, 4)],
                          "temperature_c": [t_in, rec["outlet_temperature_c"]], "liquid_holdup": None, "vsg_m_s": None, "vsl_m_s": None, "kind": "end_values"}
    rec["inlet_pressure_bara"], rec["outlet_pressure_bara"] = _f(p_from, 4), _f(p_to, 4)
    try:
        rec["outlet_fluid"] = _fluid_props(C, pipe.getOutletFluid())
    except Exception:  # noqa: BLE001
        rec["outlet_fluid"] = None
    return rec


def extract_domain(C, domain, edges, wells_meta):
    """Everything downstream needs from one solved domain (no further solver calls)."""
    net = domain.topology.getHydraulicNetwork()
    res = domain.unit.getLastCouplingResult()
    out = {"coupling": {"converged": bool(res.isConverged()), "iterations": int(res.getIterations()), "rate_residual_kg_s": _f(res.getMaximumRateResidualKgS(), 9),
                        "pressure_residual_bar": _f(res.getMaximumPressureResidualBar(), 9), "network_residual_pa": _f(res.getNetworkResidualPa(), 9),
                        "mass_balance_residual_kg_s": _f(res.getMassBalanceResidualKgS(), 9), "message": str(res.getMessage())}}
    out["unit_solved"] = bool(domain.unit.solved())
    out["wells"] = [{"node": str(w.getNodeId()), "status": str(w.getStatus()), "pressure_bara": _f(w.getNodePressureBara(), 4), "target_rate_kg_s": _f(w.getTargetRateKgS(), 5),
                     "applied_rate_kg_s": _f(w.getAppliedRateKgS(), 5), "inner_converged": bool(w.isInnerConverged()), "message": str(w.getMessage())}
                    for w in res.getWellResults()]
    out["nodes"] = {str(n): {"pressure_bara": _f(net.getNodePressure(str(n)), 4), "temperature_c": _f(net.getNode(str(n)).getTemperature() - 273.15, 3)}
                    for n in net.getNodeNames()}
    out["edges"] = {}
    for p in domain.plan[1:]:
        if p["op"] in ("pipe", "pump"):
            eid = p["id"]
            e = edges[eid]
            if p["op"] == "pipe":
                out["edges"][eid] = extract_edge(C, domain, eid, e)
            else:
                pipe = net.getPipe(eid)
                out["edges"][eid] = {"id": eid, "tag": e["tag"], "flow_kg_s": _f(pipe.getFlowRate()), "inlet_pressure_bara": _f(net.getNodePressure(e["from"]), 4),
                                     "outlet_pressure_bara": _f(net.getNodePressure(e["to"]), 4), "power_kw": _f(domain.unit.getPower("kW"), 4), "profile": None}
    out["node_balance"] = node_balance(domain, edges, out)
    return out


def node_balance(domain, edges, out):
    """Independent mass balance per node from the extracted signed edge rates (kg/s): sum(in) - sum(out) and the well supply/demand."""
    wells = {w["node"]: w["applied_rate_kg_s"] for w in out["wells"]}
    bal = {}
    for p in domain.plan[1:]:
        if p["op"] == "node":
            bal[p["id"]] = {"in": 0.0, "out": 0.0}
    for eid, rec in out["edges"].items():
        q = rec["flow_kg_s"] or 0.0
        a, b = edges[eid]["from"], edges[eid]["to"]
        if a in bal:
            bal[a]["out" if q >= 0 else "in"] += abs(q)
        if b in bal:
            bal[b]["in" if q >= 0 else "out"] += abs(q)
    return {n: {"in_kg_s": _f(v["in"], 6), "out_kg_s": _f(v["out"], 6), "residual_kg_s": _f(v["in"] - v["out"], 6)} for n, v in bal.items()}


def _host_block(C, unit, sep, host_node):
    stream = unit.getOutletStream(host_node)
    fluid = stream.getFluid()
    phases = {"gas_kg_s": 0.0, "oil_kg_s": 0.0, "aqueous_kg_s": 0.0}
    for i in range(int(fluid.getNumberOfPhases())):
        ph = fluid.getPhase(i)
        key = {"gas": "gas_kg_s", "aqueous": "aqueous_kg_s"}.get(str(ph.getPhaseTypeName()), "oil_kg_s")
        phases[key] += float(ph.getFlowRate("kg/sec"))
    return {"mass_rate_kg_s": _f(stream.getFlowRate("kg/sec"), 5), "temperature_c": _f(stream.getTemperature("C"), 3), "pressure_bara": _f(stream.getPressure("bara"), 4),
            "phases": {k: _f(v, 5) for k, v in phases.items()}, "separator_gas_kg_s": _f(sep.getGasOutStream().getFlowRate("kg/sec"), 5),
            "separator_liquid_kg_s": _f(sep.getLiquidOutStream().getFlowRate("kg/sec"), 5)}


def run_domain_case(spec, model, concept_id, case_id, domain_name, segment_factor=1, cache_dir=None, log=None, force=False):
    """Solve and extract one domain of one concept/case; returns the cached record when the key matches."""
    say = log or (lambda m: None)
    plan = surf_model.build_plan(spec, model, concept_id, case_id, domain_name, segment_factor)
    if not any(o["op"] == "node" for o in plan[1:]):
        # a service that is absent from the study (for example no injection system): nothing to solve; an explicit empty, converged record
        return {"coupling": {"converged": True, "iterations": 0, "rate_residual_kg_s": 0.0, "pressure_residual_bar": 0.0, "network_residual_pa": 0.0, "mass_balance_residual_kg_s": 0.0,
                             "message": "no %s service in this study" % domain_name}, "unit_solved": True, "wells": [], "nodes": {}, "edges": {}, "node_balance": {},
                "domain": domain_name, "concept": concept_id if domain_name == "production" else None, "case": case_id, "mesh_factor": segment_factor, "model_converged": True,
                "plan_ops": len(plan), "timings_s": {"build": 0.0, "solve": 0.0, "extract": 0.0}, "cache": {"hit": True, "key": "empty"}, "pump_power_kw": 0.0, "empty_service": True}
    key = cache_key(plan, domain_name, segment_factor)
    path = os.path.join(cache_dir, "%s_%s_%s_x%s_%s.json" % (domain_name, concept_id if domain_name == "production" else "all", case_id, segment_factor, key)) if cache_dir else None
    if path and os.path.isfile(path) and not force:
        with open(path, encoding="utf-8") as fh:
            rec = json.load(fh)
        rec["cache"] = {"hit": True, "key": key}
        say("%s/%s/%s: cache hit" % (domain_name, concept_id, case_id))
        return rec
    jp = surf_model.start_jvm()
    C = surf_model.Classes(jp)
    edges = surf_spec.case_for_concept(model, spec, concept_id)
    t0 = time.time()
    domain = surf_model.execute_plan(plan, jp)
    t_build = time.time() - t0
    t0 = time.time()
    uuid = C("java.util.UUID")
    try:
        domain.unit.run(uuid.randomUUID())
    except Exception as exc:  # noqa: BLE001
        raise RunError("%s/%s/%s: the field network raised %s" % (domain_name, concept_id, case_id, str(exc).splitlines()[0][:200]))
    sep = None
    ps = C("neqsim.process.processmodel.ProcessSystem")("SURF %s area" % domain_name)
    ps.add(domain.unit)
    if domain_name == "production":
        host_node = next(n["id"] for n in plan[1:] if n["op"] == "node" and n["role"] == "HOST")
        outlet = domain.unit.getOutletStream(host_node)
        if outlet is None:
            raise RunError("%s/%s/%s: no host outlet stream after the field network solve (%s)" % (domain_name, concept_id, case_id,
                                                                                                 str(domain.unit.getLastCouplingResult().getMessage())))
        sep = C("neqsim.process.equipment.separator.Separator")("host inlet separator", outlet)
        ps.add(sep)
    pm = C("neqsim.process.processmodel.ProcessModel")()
    pm.add("SURF-%s" % domain_name, ps)
    try:
        pm.run()
    except Exception as exc:  # noqa: BLE001
        raise RunError("%s/%s/%s: the process model raised %s" % (domain_name, concept_id, case_id, str(exc).splitlines()[0][:200]))
    t_solve = time.time() - t0
    t0 = time.time()
    rec = extract_domain(C, domain, edges, None)
    rec.update({"domain": domain_name, "concept": concept_id if domain_name == "production" else None, "case": case_id, "mesh_factor": segment_factor,
                "model_converged": bool(pm.isModelConverged()), "plan_ops": len(plan), "timings_s": {"build": round(t_build, 2), "solve": round(t_solve, 2)}})
    if sep is not None:
        rec["host"] = _host_block(C, domain.unit, sep, host_node)
    if domain_name == "injection":
        rec["pump_power_kw"] = _f(domain.unit.getPower("kW"), 4)
    rec["timings_s"]["extract"] = round(time.time() - t0, 2)
    rec["cache"] = {"hit": False, "key": key}
    if path:
        os.makedirs(cache_dir, exist_ok=True)
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(rec, fh, indent=1)
    say("%s/%s/%s: solved in %.1f s (converged %s)" % (domain_name, concept_id, case_id, t_solve, rec["model_converged"] and rec["unit_solved"]))
    return rec


def run_umbilical(spec, model, cache_dir=None, log=None):
    """Solve the chemical tube networks once (they do not depend on the case) and extract delivery pressure, drop, velocity and Reynolds number."""
    umb_inputs = {"umbilical": spec["umbilical"], "fluids": {s["fluid"]: spec["fluids"][s["fluid"]] for s in spec["umbilical"]["services"] if s["kind"] == "chemical"},
                  "z": {s["consumer"]: model["nodes"][s["consumer"]]["z_m"] for s in spec["umbilical"]["services"] if s.get("consumer")}, "host_z": spec["host"]["inlet_elevation_m"]}
    key = hashlib.sha256((json.dumps(umb_inputs, sort_keys=True, default=str) + surf_model.neqsim_version() + RUN_VERSION).encode("utf-8")).hexdigest()[:20]
    path = os.path.join(cache_dir, "umbilical_%s.json" % key) if cache_dir else None
    if path and os.path.isfile(path):
        with open(path, encoding="utf-8") as fh:
            return json.load(fh)
    nets = surf_model.build_umbilical_services(spec, model)
    out = {}
    for sid, rec in nets.items():
        net = rec["network"]
        net.run()
        s = rec["service"]
        supply = spec["umbilical"]["chemical_supply_pressure_bara"]
        deliv = net.getNodePressure("consumer")
        area = math.pi * (s["tube_id_mm"] / 1000.0) ** 2 / 4.0
        rho = None
        try:
            f = net.getPipe(sid).getOutletFluid()
            rho = _fluid_props(surf_model.Classes(surf_model.start_jvm()), f)["density_kg_m3"]
        except Exception:  # noqa: BLE001
            pass
        out[sid] = {"id": sid, "consumer": s["consumer"], "fluid": s["fluid"], "rate_kg_s": s["rate_kg_s"], "tube_id_mm": s["tube_id_mm"], "length_m": rec["length_m"],
                    "supply_pressure_bara": supply, "delivery_pressure_bara": _f(deliv, 4), "pressure_drop_bar": _f(supply - deliv, 4), "converged": bool(net.isConverged()),
                    "velocity_m_s": _f(net.getPipeVelocity(sid), 4), "reynolds": _f(net.getPipeReynoldsNumber(sid), 1), "density_kg_m3": rho,
                    "elevation_consumer_m": model["nodes"][s["consumer"]]["z_m"], "elevation_host_m": spec["host"]["inlet_elevation_m"],
                    "flow_area_m2": area}
    if path:
        os.makedirs(cache_dir, exist_ok=True)
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(out, fh, indent=1)
    return out
