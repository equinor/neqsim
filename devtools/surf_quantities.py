"""Registers, quantities (MTO/BOQ) and SURF cost for one concept, all derived from the canonical specification geometry.

Counts and lengths come from the same normalised edges and nodes that built the hydraulic model; the cost is the existing
``SubseaProductionSystem.estimateSurfCosts`` / ``SURFCostEstimator`` on a cost-only canonical topology that carries both
services, so wells, trees, manifolds, risers, flowlines, jumpers and the umbilical are each priced once. Installed length is
route length x (1 + stated allowance), used for hydraulics, mass and cost alike (no allowance is added twice).
"""
import json
import math

import surf_model
import surf_spec

STEEL_RHO = 7850.0


def _f(v, nd=4):
    try:
        v = float(v)
    except (TypeError, ValueError):
        return None
    return round(v, nd) if math.isfinite(v) else None


def line_register(spec, model, concept_id):
    """One row per physical line (edge) with route/installed length, OD/wall/ID, material, thermal and design data."""
    rows = []
    for eid, e in surf_spec.case_for_concept(model, spec, concept_id).items():
        if e["kind"] == "pump":
            continue
        ins = e.get("insulation") or {}
        rows.append({"id": eid, "tag": e["tag"], "kind": e["kind"], "service": e["service"], "from": e["from"], "to": e["to"], "construction": e.get("construction", "rigid"),
                     "route_length_m": e["route_length_m"], "allowance_fraction": float(e.get("allowance_fraction", 0.0)), "installed_length_m": e["installed_length_m"],
                     "od_mm": e.get("od_mm"), "wall_mm": e.get("wall_mm"), "id_mm": e["id_m"] * 1000.0, "corrosion_allowance_mm": e.get("corrosion_allowance_mm"),
                     "grade": e.get("grade"), "insulation": "%s %.0f mm" % (ins["material"], ins["thickness_mm"]) if ins.get("material") else "none (explicit U-value)",
                     "design_pressure_bara": (e.get("design") or {}).get("pressure_bara"), "design_temperature_c": (e.get("design") or {}).get("temperature_c"),
                     "installation": (e.get("installation") or {}).get("method"), "burial": (e.get("installation") or {}).get("burial"),
                     "existing": bool(e.get("existing", False)),
                     "hydraulic_model": e["hydraulic"]["model"], "segments": e["hydraulic"]["segments"]})
    return rows


def equipment_register(spec, model, concept_id):
    """Nodes active in the concept with tag, kind, service, structure, position and the wells' basis (specified, never solved here)."""
    cdef = model["concepts"][concept_id]
    rows = []
    for nid in cdef["nodes"]:
        n = model["nodes"][nid]
        rows.append({"id": nid, "tag": n["tag"], "kind": n["kind"], "service": n["service"], "structure": n.get("structure"), "x_m": n["x_m"], "y_m": n["y_m"], "z_m": n["z_m"],
                     "note": n.get("note"), "well": n.get("well"), "existing": bool(n.get("existing", False))})
    return rows


def service_register(spec):
    """Umbilical services with kind, consumer, tube/channel data and the assessment responsibility."""
    umb = spec.get("umbilical") or {}
    rows = []
    for s in umb.get("services", []):
        owner = {"chemical": "NeqSim single-phase tube hydraulics (this study)", "hydraulic": "control-system supplier (not hydraulically modelled)",
                 "power": "electrical specialist (voltage drop, ampacity, protection)", "signal": "control-system / telecom supplier"}[s["kind"]]
        rows.append(dict(s, assessment_owner=owner))
    return rows


def mto(spec, model, concept_id):
    """Material take-off derived from geometry: steel mass, insulation volume and lengths per line, counts per equipment kind."""
    lines = []
    tot_steel = tot_ins = 0.0
    for r in line_register(spec, model, concept_id):
        if r.get("existing"):
            lines.append({"id": r["id"], "tag": r["tag"], "item": "EXISTING line reused (not procured): ID %.0f mm" % r["id_mm"], "length_m": r["installed_length_m"], "steel_mass_kg": None,
                          "insulation_m3": None, "basis": "existing asset: excluded from MTO, cost and procurement"})
            continue
        if r["construction"] == "flexible":
            lines.append({"id": r["id"], "tag": r["tag"], "item": "flexible pipe, ID %.0f mm" % r["id_mm"], "length_m": r["installed_length_m"], "steel_mass_kg": None, "insulation_m3": None,
                          "basis": "supplier-defined construction: mass not derived"})
            continue
        od, idm = r["od_mm"] / 1000.0, r["id_mm"] / 1000.0
        steel = STEEL_RHO * math.pi / 4.0 * (od ** 2 - idm ** 2) * r["installed_length_m"]
        thick = float(r["insulation"].split()[1]) / 1000.0 if r["insulation"].split()[0] != "none" else 0.0
        ins = math.pi / 4.0 * ((od + 2 * thick) ** 2 - od ** 2) * r["installed_length_m"]
        tot_steel += steel
        tot_ins += ins
        lines.append({"id": r["id"], "tag": r["tag"], "item": "%s %.1f x %.1f mm %s" % (r["kind"], r["od_mm"], r["wall_mm"], r["grade"]), "length_m": r["installed_length_m"],
                      "steel_mass_kg": steel, "insulation_m3": ins, "basis": "pi/4 (OD^2 - ID^2) x installed length x 7850 kg/m3; insulation annulus volume"})
    equip = {}
    for n in equipment_register(spec, model, concept_id):
        equip[n["kind"]] = equip.get(n["kind"], 0) + 1
    return {"lines": lines, "total_steel_kg": tot_steel, "total_insulation_m3": tot_ins, "equipment_counts": equip,
            "umbilical_length_m": (spec.get("umbilical") or {}).get("length_m"), "structure_count": len(model["structures"])}


def cost_topology(spec, model, concept_id, jp):
    """Cost-only canonical topology with both services (same IDs, roles, ports and installed lengths); it is never solved."""
    C = surf_model.Classes(jp)
    edges = surf_spec.case_for_concept(model, spec, concept_id)
    cdef = model["concepts"][concept_id]
    FNT = C("FieldNetworkTopology")
    Svc = lambda v: C.enum("FieldNetworkTopology", "Service", v)  # noqa: E731
    Role = lambda v: C.enum("FieldNetworkTopology", "NodeRole", v)  # noqa: E731
    ERole = lambda v: C.enum("FieldNetworkTopology", "EdgeRole", v)  # noqa: E731
    direction = C.enum("FieldNetworkTopology", "FlowDirection", "FROM_TO")
    top = FNT("%s cost topology %s" % (spec["study"]["id"], concept_id))
    for nid in cdef["nodes"]:
        n = model["nodes"][nid]
        role, svc = surf_model.NODE_ROLE[n["kind"]], n["service"].upper()
        if n.get("existing") and n["kind"] in ("plet", "plem", "manifold", "tie_in"):
            role = "JUNCTION"  # reused existing structure: priced as a plain junction (no new structure cost)
        if n["kind"] == "host":
            top.addFixedPressureSink(nid, n["tag"], Role(role), Svc(svc), 40.0, n["z_m"])
        elif n["kind"] == "injection_source":
            top.addPressureSource(nid, n["tag"], Role(role), Svc(svc), 10.0, 0.0, n["z_m"])
        elif n["kind"] in ("production_well", "injection_well"):
            top.addLiveWellNode(nid, n["tag"], Role(role), Svc(svc), 100.0, n["z_m"])
        else:
            top.addJunction(nid, n["tag"], Role(role), Svc(svc), n["z_m"])
    sps = C("neqsim.process.fielddevelopment.subsea.SubseaProductionSystem")("%s cost %s" % (spec["study"]["id"], concept_id))
    sps.setWaterDepthM(float(spec["study"]["water_depth_m"]))
    sps.setUmbilicalLengthKm(float(spec["umbilical"]["length_m"]) / 1000.0)
    region = spec["solver"].get("cost", {}).get("region", "NORWAY")
    sps.setCostRegion(C.enum("neqsim.process.mechanicaldesign.subsea.SubseaCostEstimator", "Region", region))
    for eid, e in edges.items():
        if e.get("existing"):
            continue  # reused existing asset: not part of the cost topology (no procurement/installation cost)
        if e["kind"] == "pump":
            top.addPump(eid, e["tag"], Svc(e["service"].upper()), direction, e["from"], e["from_port"], e["to"], e["to_port"], e["pump"]["discharge_pressure_bara"], e["pump"]["efficiency"])
            continue
        top.addPipe(eid, e["tag"], ERole(surf_model.ROLE[e["kind"]]), Svc(e["service"].upper()), direction, e["from"], e["from_port"], e["to"], e["to_port"],
                    e["installed_length_m"], e["id_m"], C.enum("LoopedPipeNetwork", "PipeModelType", "DARCY_WEISBACH"))
    plan = surf_model.build_plan(spec, model, concept_id, next(iter(model["cases"])), "production") + surf_model.build_plan(spec, model, concept_id, next(iter(model["cases"])), "injection")
    for o in plan:
        if o["op"] == "line_design" and not edges[o["edge"]].get("existing"):
            sps.setLineDesign(o["edge"], surf_model.line_design(C, o))
    return top, sps


def cost(spec, model, concept_id, jp):
    """SURF cost estimate (existing estimator) with material take-off, basis metadata and the topology-derived design-basis counts."""
    top, sps = cost_topology(spec, model, concept_id, jp)
    est = sps.estimateSurfCosts(top)
    data = json.loads(str(est.toJson()).replace("NaN", "null").replace("Infinity", "null"))
    basis = sps.createSurfDesignBasis(top)
    counts = {"production_wells": int(basis.getProductionWellCount()), "injection_wells": int(basis.getInjectionWellCount()), "trees": int(basis.getTreeCount()),
              "manifolds": int(basis.getManifoldCount()), "distribution_units": int(basis.getDistributionUnitCount()), "plets": int(basis.getPletCount()),
              "plems": int(basis.getPlemCount()), "jumpers": int(basis.getJumperCount()), "production_risers": int(basis.getProductionRiserCount()),
              "injection_risers": int(basis.getInjectionRiserCount()), "total_risers": int(basis.getTotalRiserCount())}
    segs = [{"id": str(s.getId()), "tag": str(s.getEquipmentTag()), "length_m": _f(s.getLengthM(), 3), "diameter_m": _f(s.getDiameterM(), 5), "role": str(s.getRole())} for s in basis.getLineSegments()]
    caps = data.get("capitalCosts_USD", {})
    total = (data.get("capitalCostSummary_USD") or {}).get("totalSURF")
    b = data.get("estimateBasis", {})
    return {"total_usd": total, "capital_usd": caps, "estimate_class": b.get("estimateClass"), "accuracy_low": b.get("accuracyLowFraction"), "accuracy_high": b.get("accuracyHighFraction"),
            "currency": b.get("currencyCode"), "cost_year": b.get("costYear"), "location": b.get("locationBasis"), "method": b.get("estimatingMethod"),
            "material_take_off": data.get("materialTakeOff", []), "quality_flags": data.get("qualityFlags", []), "design_basis_counts": counts, "design_basis_segments": segs,
            "project_costs_usd": data.get("projectCosts_USD", {}), "quantity_basis": data.get("quantityBasis", {}),
            "scope": "SURF only: trees, manifolds, PLETs/PLEMs, jumpers, risers, flowlines, umbilical, installation as priced by the estimator",
            "excluded": ["wells (drilling and completion)", "host modifications", "subsea control-system engineering beyond the estimator basis", "owner's cost"]}


def reconcile(spec, model, concept_id, register_lines, equipment, cost_rec):
    """Model/register/design-basis/cost reconciliation: counts by kind and line lengths against the cost design-basis segments."""
    out = []
    kinds = {}
    for n in equipment:
        if n.get("existing") and n["kind"] in ("plet", "plem", "manifold", "tie_in"):
            continue
        kinds[n["kind"]] = kinds.get(n["kind"], 0) + 1
    counts = cost_rec["design_basis_counts"]
    new_lines = [r for r in register_lines if not r.get("existing")]
    pairs = [("production wells", kinds.get("production_well", 0), counts["production_wells"]), ("injection wells", kinds.get("injection_well", 0), counts["injection_wells"]),
             ("PLEMs", kinds.get("plem", 0), counts["plems"]), ("PLETs", kinds.get("plet", 0), counts["plets"]),
             ("distribution units (manifolds)", kinds.get("manifold", 0) + kinds.get("injection_manifold", 0), counts["manifolds"]),
             ("jumpers", sum(1 for r in new_lines if r["kind"] == "jumper"), counts["jumpers"]),
             ("risers", sum(1 for r in new_lines if r["kind"] == "riser"), counts["total_risers"])]
    for label, a, b in pairs:
        out.append({"item": label, "register": a, "design_basis": b, "status": "PASS" if a == b else "FAIL"})
    segs = {s["id"]: s for s in cost_rec["design_basis_segments"]}
    for r in new_lines:
        s = segs.get(r["id"])
        ok = s is not None and abs(s["length_m"] - r["installed_length_m"]) <= 0.01 and abs(s["diameter_m"] * 1000.0 - r["id_mm"]) <= 0.01
        out.append({"item": "line %s length and ID" % r["id"], "register": "%.1f m / %.1f mm" % (r["installed_length_m"], r["id_mm"]),
                    "design_basis": "%.1f m / %.1f mm" % (s["length_m"], s["diameter_m"] * 1000.0) if s else "missing", "status": "PASS" if ok else "FAIL"})
    return out
