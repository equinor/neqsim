"""Design assessment of solved SURF cases: derived quantities, acceptance criteria, hydrate margin, cooldown, umbilical delivery,
structural-kernel screening results, hand checks and the concept comparison.

Calculation owners stay where they are: hydraulics and thermal state come from the solved ``LoopedPipeNetwork`` records, hydrate
equilibrium from the CPA hydrate curves of the flow-assurance toolkit, the lumped cooldown from ``fa_thermal`` (with its RK4
check), structural limit states from the existing DNV-ST-F101 kernel through ``SubseaProductionSystem``. This module only reads
those results, applies the project criteria and records evidence and status. Nothing is promoted beyond its qualification.
"""
import math
import re

import fa_thermal
import fa_units
import surf_model
import surf_spec

G = 9.80665
STEEL_CP = 480.0
STEEL_RHO = 7850.0
WATER_CP = 4180.0
MU_WATER = 1.0e-3


def _num(v):
    return isinstance(v, (int, float)) and not isinstance(v, bool) and math.isfinite(v)


# --- hydrate ----------------------------------------------------------------------------------------
def hydrate_curves(spec, fluid_ids, log=None):
    """CPA hydrate-formation curves (uninhibited) per production fluid on the study pressure grid; unavailable points stay unavailable."""
    import fa_solver

    ns = fa_solver.neqsim_handle()
    grid = spec["solver"]["hydrate_grid_bara"]
    out = {}
    for fid in fluid_ids:
        comps = {k: v for k, v in spec["fluids"][fid]["components"].items() if k != "water"}
        total = sum(comps.values())
        fd = {"id": fid, "components": [{"name": k, "mole_fraction": v / total} for k, v in comps.items()]}
        factory = fa_solver.FluidFactory(ns, fd)
        hc = fa_solver.HydrateCurves(ns, factory)
        curve = hc.curve(grid[0], grid[-1], 0.0, grid, "MEG")
        curve["findings"] = hc.findings
        out[fid] = curve
        if log:
            log("hydrate curve %s: %d of %d points" % (fid, curve["n_available"], curve["n_requested"]))
    return out


def t_hyd(curve, p_bara):
    """Hydrate temperature (degC) at a pressure by ln(p) interpolation; ``None`` outside the available curve."""
    ps, ts = curve["p_bara"], curve["t_c"]
    if len(ps) < 2 or p_bara < ps[0] or p_bara > ps[-1]:
        return None
    for i in range(len(ps) - 1):
        if ps[i] <= p_bara <= ps[i + 1]:
            w = (math.log(p_bara) - math.log(ps[i])) / (math.log(ps[i + 1]) - math.log(ps[i]))
            return ts[i] + w * (ts[i + 1] - ts[i])
    return None


# --- structural kernel ------------------------------------------------------------------------------
def structural_screening(spec, model, concept_id, jp):
    """DNV-ST-F101 limit-state screening through ``SubseaProductionSystem`` for every edge with complete project input.

    Edges without input are reported as ``not_assessed`` with the missing-evidence reason; nothing is inferred.
    """
    edges = surf_spec.case_for_concept(model, spec, concept_id)
    out = {}
    for eid, e in edges.items():
        if e["kind"] == "pump":
            continue
        if not (e.get("structural") or {}).get("dnv_st_f101"):
            out[eid] = {"status": "not_assessed", "reason": "no complete DNV-ST-F101 project input in the specification (specialist hand-off)" if e.get("construction", "rigid") == "rigid"
                        else "flexible pipe: API 17J/supplier qualification, not a steel limit state", "checks": []}
    by_domain = {}
    for dom in ("production", "injection"):
        plan = surf_model.build_plan(spec, model, concept_id, next(iter(model["cases"])), dom)
        if not any(o["op"] == "line_design" and o.get("dnv_st_f101") for o in plan):
            continue
        d = surf_model.execute_plan(plan, jp)
        res = d.sps.assessDnvStF101LineDesigns(d.topology)
        for k in res.keySet():
            r = res.get(k)
            val = r.getValue()
            checks = []
            if val is not None:
                for c in val.getChecks():
                    checks.append({"limit_state": str(c.getLimitState()), "status": str(c.getStatus()), "utilization": _f(c.getUtilization()), "demand": _f(c.getDemand()),
                                   "resistance": _f(c.getResistance()), "unit": str(c.getUnit()), "method": str(c.getMethod()), "note": str(c.getNote())})
            out[str(k)] = {"status": "screened_review_required" if val is not None else "blocked", "kernel_status": str(r.getStatus()), "message": str(r.getMessage()),
                           "all_checks_passing": bool(val.areAllScreeningChecksPassing()) if val is not None else None,
                           "standard_basis": str(val.getStandardBasis()) if val is not None else None, "checks": checks,
                           "input_class": spec_input_class(edges[str(k)])}
        by_domain[dom] = True
    return out


def spec_input_class(e):
    return (e.get("structural") or {}).get("dnv_st_f101", {}).get("input_class", "specified")


def _f(v, nd=6):
    try:
        v = float(v)
    except (TypeError, ValueError):
        return None
    return round(v, nd) if math.isfinite(v) else None


# --- derived quantities per concept/case ------------------------------------------------------------
def derive_case(spec, model, concept_id, case_id, prod, inj, umb, curves):
    """Governing quantities of one concept/case with the location of each extremum and the per-edge table."""
    edges = surf_spec.case_for_concept(model, spec, concept_id)
    case = model["cases"][case_id]
    curve = curves[case["production_fluid"]]
    c_si = spec["solver"]["erosional_c_factor_si"]
    per_edge, q = {}, {}
    for dom, rec in (("production", prod), ("injection", inj)):
        for eid, r in rec["edges"].items():
            e = edges[eid]
            if e["kind"] == "pump":
                per_edge[eid] = {"domain": dom, "kind": "pump", "flow_kg_s": r["flow_kg_s"], "power_kw": r.get("power_kw"), "inlet_pressure_bara": r["inlet_pressure_bara"],
                                 "outlet_pressure_bara": r["outlet_pressure_bara"]}
                continue
            prof = r["profile"]
            p_all = [p for p in prof["pressure_bara"] if p is not None]
            t_all = [t for t in prof["temperature_c"] if t is not None]
            dsg = (e.get("design") or {}).get("pressure_bara")
            row = {"domain": dom, "kind": e["kind"], "flow_kg_s": r["flow_kg_s"], "inlet_pressure_bara": r["inlet_pressure_bara"], "outlet_pressure_bara": r["outlet_pressure_bara"],
                   "p_max_bara": max(p_all), "p_min_bara": min(p_all), "t_max_c": max(t_all), "t_min_c": min(t_all),
                   "inlet_temperature_c": t_all[0], "outlet_temperature_c": r["outlet_temperature_c"], "dp_bar": r["inlet_pressure_bara"] - r["outlet_pressure_bara"],
                   "design_pressure_bara": dsg, "pressure_to_design_ratio": (max(p_all) / dsg) if dsg else None, "profile_kind": prof["kind"], "u_value_w_m2k": r["u_value_w_m2k"]}
            if dom == "production" and prof["kind"] == "distributed":
                rho = (r.get("outlet_fluid") or {}).get("density_kg_m3")
                vm = [(a or 0.0) + (b or 0.0) for a, b in zip(prof["vsg_m_s"], prof["vsl_m_s"])]
                row["v_mix_max_m_s"] = max(vm)
                row["erosional_velocity_m_s"] = c_si / math.sqrt(rho) if rho else None
                row["erosional_ratio"] = row["v_mix_max_m_s"] / row["erosional_velocity_m_s"] if row["erosional_velocity_m_s"] else None
                row["holdup_max"] = max(h for h in prof["liquid_holdup"] if h is not None)
                margins = []
                for pos, p, t in zip(prof["position_m"], prof["pressure_bara"], prof["temperature_c"]):
                    th = t_hyd(curve, p) if p is not None else None
                    if th is not None and t is not None:
                        margins.append((t - th, pos))
                if margins:
                    m, pos = min(margins)
                    row["hydrate_margin_min_k"], row["hydrate_margin_position_m"] = m, pos
            per_edge[eid] = row
    dp_rows = [(v["pressure_to_design_ratio"], k) for k, v in per_edge.items() if v.get("pressure_to_design_ratio") is not None]
    q["pressure_to_design_ratio_max"] = _q(max(dp_rows) if dp_rows else None, "-")
    hm = [(v["hydrate_margin_min_k"], k) for k, v in per_edge.items() if v.get("hydrate_margin_min_k") is not None]
    q["hydrate_margin_min_k"] = _q(min(hm) if hm else None, "dK")
    er = [(v["erosional_ratio"], k) for k, v in per_edge.items() if v.get("erosional_ratio") is not None]
    q["erosional_ratio_max"] = _q(max(er) if er else None, "-")
    q["host_arrival_temperature_c"] = _q((prod["host"]["temperature_c"], "HOST-IN"), "C")
    q["host_inlet_rate_kg_s"] = _q((prod["host"]["mass_rate_kg_s"], "HOST-IN"), "kg/s")
    fr = []
    for w in inj["wells"]:
        lim = model["nodes"][w["node"]]["well"]["fracture_pressure_bara"]
        if w["pressure_bara"] is not None:
            fr.append((lim - w["pressure_bara"], w["node"]))
    q["injection_fracture_margin_bar"] = _q(min(fr) if fr else None, "bar")
    um_rows, um_out = [], {}
    for sid, u in (umb or {}).items():
        p_node = prod["nodes"].get(u["consumer"], {}).get("pressure_bara")
        if p_node is not None and u["delivery_pressure_bara"] is not None:
            um_out[sid] = {"delivery_pressure_bara": u["delivery_pressure_bara"], "production_pressure_bara": p_node, "margin_bar": u["delivery_pressure_bara"] - p_node, "consumer": u["consumer"]}
            um_rows.append((u["delivery_pressure_bara"] - p_node, sid))
    q["umbilical_margin_bar"] = _q(min(um_rows) if um_rows else None, "bar")
    cool = cooldown(spec, model, edges, prod, per_edge, curve)
    q["cooldown_time_h"] = _q((cool["time_h"], cool["edge"]) if cool else None, "h")
    problems = []
    for dom, rec in (("production", prod), ("injection", inj)):
        if not (rec["coupling"]["converged"] and rec["unit_solved"] and rec["model_converged"]):
            problems.append("%s network did not converge (%s)" % (dom, rec["coupling"]["message"]))
        for eid, r in rec["edges"].items():
            st = str(r.get("model_status") or "")
            if re.search(r"fallback|fail|stale|not_converged|unconverged", st, re.I):
                problems.append("hydraulic fallback or failure on %s (%s)" % (eid, st))
            if dom == "production" and r.get("profile") and r["profile"]["kind"] != "distributed" and edges[eid]["kind"] != "pump":
                problems.append("no distributed profile on %s" % eid)
    return {"per_edge": per_edge, "quantities": q, "cooldown": cool, "umbilical": um_out, "problems": problems, "converged": not problems,
            "wells": {w["node"]: w for w in prod["wells"] + inj["wells"]}}


def _q(pair, unit):
    if pair is None:
        return {"value": None, "where": None, "unit": unit}
    return {"value": pair[0], "where": pair[1], "unit": unit}


def cooldown(spec, model, edges, prod, per_edge, curve):
    """Lumped cooldown time from the solved trunk-line state to its hydrate temperature (time to a temperature, not a time to plug)."""
    trunk = next((eid for eid, e in edges.items() if e["kind"] == "trunkline"), None)
    if trunk is None:
        return None
    e, r, row = edges[trunk], prod["edges"][trunk], per_edge[trunk]
    fl = r.get("outlet_fluid") or {}
    if not (e.get("od_m") and e.get("id_m") and fl.get("density_kg_m3") and fl.get("cp_j_kgk") and r.get("u_value_w_m2k")):
        return None
    steel = STEEL_RHO * math.pi / 4.0 * (e["od_m"] ** 2 - e["id_m"] ** 2)
    tau = fa_thermal.tau_s(fl["density_kg_m3"], fl["cp_j_kgk"], e["id_m"], steel, STEEL_CP, r["u_value_w_m2k"])
    t0 = 0.5 * (row["inlet_temperature_c"] + row["outlet_temperature_c"])
    th = t_hyd(curve, row["p_max_bara"])
    if th is None:
        return {"edge": trunk, "time_h": None, "status": "unavailable", "reason": "hydrate temperature unavailable at the shut-in pressure"}
    amb = e["thermal"]["ambient_c"]
    t = fa_thermal.time_to_temperature_s(t0, amb, th, tau)
    rk = fa_thermal.rk4_time_to_temperature_s(t0, amb, th, tau)
    return {"edge": trunk, "time_h": t / 3600.0, "rk4_time_h": rk / 3600.0, "tau_h": tau / 3600.0, "t0_c": t0, "ambient_c": amb, "hydrate_t_c": th, "shut_in_pressure_bara": row["p_max_bara"],
            "status": "calculated", "note": "time to reach the hydrate temperature; hydrate heat, natural convection and plugging time are not modelled"}


# --- criteria ---------------------------------------------------------------------------------------
def evaluate_criteria(spec, derived_by_case):
    """PASS/FAIL per criterion and case; a case that did not converge, or a missing quantity, is never PASS."""
    out = []
    for c in spec["acceptance_criteria"]:
        by_case, overall = {}, "PASS"
        for cid, d in derived_by_case.items():
            qv = d["quantities"].get(c["quantity"])
            if not d["converged"]:
                by_case[cid] = {"status": "FAIL", "value": None, "where": None, "reason": "case not usable: " + "; ".join(d["problems"])[:160]}
            elif qv is None or qv["value"] is None:
                by_case[cid] = {"status": "NOT ASSESSED", "value": None, "where": None, "reason": "quantity unavailable"}
            else:
                v = qv["value"]
                ok = {">=": v >= c["value"], "<=": v <= c["value"], ">": v > c["value"], "<": v < c["value"]}[c["operator"]]
                margin = (v - c["value"]) if c["operator"] in (">=", ">") else (c["value"] - v)
                by_case[cid] = {"status": "PASS" if ok else "FAIL", "value": v, "where": qv["where"], "limit_margin": margin}
        states = {x["status"] for x in by_case.values()}
        overall = "FAIL" if "FAIL" in states else ("NOT ASSESSED" if "NOT ASSESSED" in states else "PASS")
        out.append(dict(c, by_case=by_case, overall=overall))
    return out


def concept_comparison(spec, concepts, criteria_by_concept, cost_by_concept):
    """Feasibility first (all criteria pass in all cases), then cost among the feasible concepts only."""
    rows, feasible = [], []
    for cid in concepts:
        crit = criteria_by_concept[cid]
        fails = [(c["id"], k) for c in crit for k, v in c["by_case"].items() if v["status"] == "FAIL"]
        open_ = [(c["id"], k) for c in crit for k, v in c["by_case"].items() if v["status"] == "NOT ASSESSED"]
        status = "infeasible" if fails else ("not_assessed" if open_ else "feasible")
        cost = (cost_by_concept.get(cid) or {}).get("total_usd")
        rows.append({"concept": cid, "feasibility": status, "failed": ["%s/%s" % f for f in fails], "open": ["%s/%s" % f for f in open_], "capex_usd": cost})
        if status == "feasible":
            feasible.append(rows[-1])
    selected = min(feasible, key=lambda r: r["capex_usd"] if r["capex_usd"] is not None else math.inf)["concept"] if feasible else None
    return {"rows": rows, "selected": selected,
            "rule": "a concept is ranked on cost only if every acceptance criterion is PASS in every case; an unassessed or infeasible concept is not ranked"}


# --- hand checks and benchmarks ---------------------------------------------------------------------
def swamee_jain(re_, rel_rough):
    """Darcy friction factor (turbulent Swamee-Jain, laminar 64/Re below 2300)."""
    if re_ < 2300.0:
        return 64.0 / re_
    return 0.25 / (math.log10(rel_rough / 3.7 + 5.74 / re_ ** 0.9)) ** 2


def hand_checks(spec, model, concept_id, case_id, prod, inj, umb):
    """Independent hydrostatic, single-phase friction, thermal and mass-closure hand checks against the solved records."""
    edges = surf_spec.case_for_concept(model, spec, concept_id)
    checks = []
    for eid in ("RI-INJ", "IFL-1"):
        if eid not in inj["edges"]:
            continue
        e, r = edges[eid], inj["edges"][eid]
        rho = (r.get("outlet_fluid") or {}).get("density_kg_m3")
        if not rho:
            continue
        q = abs(r["flow_kg_s"])
        v = q / (rho * math.pi * e["id_m"] ** 2 / 4.0)
        re_ = rho * v * e["id_m"] / MU_WATER
        f = swamee_jain(re_, 0.05e-3 / e["id_m"])
        dz = e["points"][-1][2] - e["points"][0][2]
        dp_fric = f * e["installed_length_m"] / e["id_m"] * rho * v * v / 2.0 / 1.0e5
        dp_hand = dp_fric - rho * G * (-dz) / 1.0e5  # pressure drop in the flow direction: friction minus the hydrostatic gain of a descending line
        dp_model = r["inlet_pressure_bara"] - r["outlet_pressure_bara"]
        tol = max(0.5, 0.01 * abs(dp_model))
        checks.append({"id": "HC-HYDRO-%s" % eid, "description": "Single-phase hydrostatic head plus Darcy friction on %s (water, Swamee-Jain, mu %.1f mPa s)" % (eid, MU_WATER * 1000),
                       "model": dp_model, "hand": dp_hand, "unit": "bar", "tolerance": tol, "status": "PASS" if abs(dp_model - dp_hand) <= tol else "FAIL",
                       "independence": "analytical hand calculation; NeqSim friction and density are not reused except the solved rate and density"})
        t_in, t_out = r["profile"]["temperature_c"][0], r["outlet_temperature_c"]
        cp = (r.get("outlet_fluid") or {}).get("cp_j_kgk") or WATER_CP
        amb = e["thermal"]["ambient_c"]
        ua = r["u_value_w_m2k"] * math.pi * e["id_m"] * e["installed_length_m"]
        t_hand = amb + (t_in - amb) * math.exp(-ua / (q * cp))
        t_status = "PASS" if abs(t_out - t_hand) <= 0.5 else "FAIL"
        checks.append({"id": "HC-THERMAL-%s" % eid, "description": "Plug-flow arrival temperature on %s with the stated U-value (cp %.0f J/kgK from the solved fluid)" % (eid, cp),
                       "model": t_out, "hand": t_hand, "unit": "degC", "tolerance": 0.5, "status": t_status,
                       "independence": "analytical energy balance; checks the thermal-hydraulic solution, not the SRK heat capacity"})
    wells = sum(w["applied_rate_kg_s"] for w in prod["wells"])
    host = prod["host"]["mass_rate_kg_s"]
    checks.append({"id": "HC-MASS-PROD", "description": "Total well rate equals host inlet rate (production)", "model": host, "hand": wells, "unit": "kg/s",
                   "tolerance": 1.0e-4 * max(wells, 1.0), "status": "PASS" if abs(host - wells) <= 1.0e-4 * max(wells, 1.0) else "FAIL", "independence": "mass closure"})
    pw = sum(w["applied_rate_kg_s"] for w in inj["wells"])
    pump = abs(inj["edges"]["INJ-PUMP"]["flow_kg_s"]) if "INJ-PUMP" in inj["edges"] else None
    if pump is not None:
        checks.append({"id": "HC-MASS-INJ", "description": "Pump rate equals total injection well rate", "model": pump, "hand": pw, "unit": "kg/s", "tolerance": 1.0e-4 * max(pw, 1.0),
                       "status": "PASS" if abs(pump - pw) <= 1.0e-4 * max(pw, 1.0) else "FAIL", "independence": "mass closure"})
    for nid, b in prod["node_balance"].items():
        if model["nodes"][nid]["kind"] in ("manifold", "plem", "injection_manifold", "plet", "junction"):
            checks.append({"id": "HC-NODE-%s" % nid, "description": "Mass balance at node %s from the extracted edge rates" % nid, "model": b["residual_kg_s"], "hand": 0.0, "unit": "kg/s",
                           "tolerance": 1.0e-3, "status": "PASS" if abs(b["residual_kg_s"]) <= 1.0e-3 else "FAIL", "independence": "recomputed from signed edge rates"})
    return checks


def water_property_check(inj):
    """Deviation of the SRK pure-water density and heat capacity from tabulated water at 15 degC and 1 atm (no pressure correction)."""
    rec = next((e for e in inj["edges"].values() if e.get("outlet_fluid")), None)
    if rec is None:
        return []
    fl = rec["outlet_fluid"]
    out = []
    for name, val, ref_, unit in (("density", fl["density_kg_m3"], 999.1, "kg/m3"), ("heat capacity", fl["cp_j_kgk"], 4189.0, "J/kgK")):
        dev = abs(val - ref_) / ref_
        out.append({"id": "FP-WATER-" + name.split()[0].upper(), "description": "SRK pure-water %s against tabulated water (15 degC, 1 atm; pressure effect below 2%% at 250 bara)" % name,
                    "model": val, "reference": ref_, "unit": unit, "deviation": dev, "tolerance": 0.05, "status": "PASS" if dev <= 0.05 else "WARN"})
    return out


def hydraulic_diameter_checks(spec, model, concept_id):
    """OD/wall/ID relations and the corrosion allowance sign for every rigid line (ID is computed from OD and wall; CA is not a wall reduction)."""
    out = []
    for eid, e in surf_spec.case_for_concept(model, spec, concept_id).items():
        if e["kind"] == "pump" or e.get("construction") == "flexible":
            continue
        ca = e.get("corrosion_allowance_mm", 0.0)
        ok = abs(e["id_m"] * 1000.0 - (e["od_mm"] - 2 * e["wall_mm"])) < 1.0e-6 and ca < e["wall_mm"]
        out.append({"edge": eid, "od_mm": e["od_mm"], "wall_mm": e["wall_mm"], "id_mm": e["id_m"] * 1000.0, "corrosion_allowance_mm": ca,
                    "status": "PASS" if ok else "FAIL"})
    return out


def mesh_sensitivity(spec, base_rec, fine_rec, edges):
    """Differences in arrival pressure, temperature and per-edge outlet state between the study mesh and a finer one."""
    tol = spec["solver"]["mesh_sensitivity"]["tolerances"]
    rows = []
    for eid, r in base_rec["edges"].items():
        rf = fine_rec["edges"].get(eid)
        if rf is None or edges[eid]["kind"] == "pump":
            continue
        dp = abs(r["outlet_pressure_bara"] - rf["outlet_pressure_bara"])
        dt = abs((r["outlet_temperature_c"] or 0.0) - (rf["outlet_temperature_c"] or 0.0))
        rows.append({"edge": eid, "dp_bar": dp, "dt_k": dt})
    host = {"dp_bar": 0.0, "dt_k": abs(base_rec["host"]["temperature_c"] - fine_rec["host"]["temperature_c"]),
            "rate_rel": abs(base_rec["host"]["mass_rate_kg_s"] - fine_rec["host"]["mass_rate_kg_s"]) / max(base_rec["host"]["mass_rate_kg_s"], 1e-9)}
    worst_p = max([r["dp_bar"] for r in rows] + [0.0])
    worst_t = max([r["dt_k"] for r in rows] + [host["dt_k"]])
    return {"rows": rows, "host": host, "max_dp_bar": worst_p, "max_dt_k": worst_t, "tolerances": tol, "factor": spec["solver"]["mesh_sensitivity"]["factor"],
            "status": "PASS" if worst_p <= tol["p_bar"] and worst_t <= tol["t_k"] else "FAIL"}
