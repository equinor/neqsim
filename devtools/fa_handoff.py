"""External-simulator hand-off packages and result adapters for the flow-assurance study report.

* ``write_handoffs`` writes one controlled JSON per external event (restart, pigging, slug receiving ...): fluids,
  geometry, boundaries, steady initial state, event schedule, required outputs and comparison criteria for a separately
  qualified simulator or experiment. Missing external results stay OPEN.
* ``attach_external_results`` records results that a person supplies; they are labelled as supplied and not verified here.
* ``two_fluid_profile`` converts a ``TwoFluidPipeResponse`` JSON (the existing NeqSim response contract) into the profile
  layout of the study dataset - no competing physics schema is introduced.
"""
import json
import os

from fa_units import FlowAssuranceError

SCHEMA = "flow_assurance_external_handoff.v1"


def write_handoffs(ds, out_dir):
    """Write ``handoff/<event>.json`` for every external event; returns ``{event_id: relative path}``."""
    paths = {}
    hdir = os.path.join(out_dir, "handoff")
    os.makedirs(hdir, exist_ok=True)
    segs = {s["id"]: s for s in ds["route"]["segments"]}
    for ev in ds["events"]:
        if ev["analysis"] != "external":
            continue
        res = ds["results"][ev["case"]]
        case = next(c for c in ds["cases"] if c["id"] == ev["case"])
        pkg = {"schema": SCHEMA, "event": {k: ev[k] for k in ("id", "type", "description", "case") if k in ev}, "status": "open",
               "dataset_hash": ds["meta"]["dataset_hash"], "source_study": ds["document"]["document_number"] + " rev " + str(ds["document"]["revision"]),
               "synthetic": ds["route"]["status"] == "synthetic",
               "fluid": {"id": case["fluid"], "components_mole_fraction": ds["fluids"][case["fluid"]]["components"],
                         "note": "water and inhibitor are added by mass rate per source; the equation of state and mixing rule must be chosen by the receiving tool"},
               "geometry": {"chainage_convention": ds["route"]["chainage"], "vertical_datum": ds["route"]["vertical_datum"],
                            "paths": {pid: [{"segment_id": sid, "length_m": segs[sid]["length_m"], "elev_start_m": segs[sid]["elev_start_m"], "elev_end_m": segs[sid]["elev_end_m"],
                                             "id_m": segs[sid]["id_m"], "roughness_m": segs[sid]["roughness_m"], "layers": segs[sid]["layers"], "burial": segs[sid].get("burial"),
                                             "ambient": segs[sid]["ambient"], "u_inner_w_m2k": segs[sid]["thermal"]["u_inner_w_m2k"]} for sid in chain]
                                      for pid, chain in res["active_paths"].items()}},
               "boundaries": {"sources": res["sources"], "host_interface_p_bara": res["host_p_bara"], "pressure_reference": ds["conventions"]["pressure_reference"]},
               "initial_state_steady": {"streams": res["streams"], "source_note": "steady-state Beggs and Brill result of case %s (not a qualified initial state for a transient)" % ev["case"]},
               "event_schedule": {"time_origin": ev.get("time_origin", "t = 0 at the start of the event"), "description": ev["description"]},
               "required_outputs": ev["required_outputs"], "comparison_criteria": ev["comparison_criteria"],
               "qualification_note": "NeqSim has no qualified transient result for this event type (see the TwoFluidPipe evidence matrix); do not substitute the steady results.",
               "handoff_target": "separately qualified transient simulator or experiment (for example through the olga-simulation-agent workflow); result to be supplied back as external_results/%s.json" % ev["id"]}
        path = os.path.join(hdir, "%s.json" % ev["id"])
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(pkg, fh, indent=1, default=str)
        paths[ev["id"]] = os.path.relpath(path, out_dir).replace("\\", "/")
    return paths


def attach_external_results(ds, results_dir):
    """Mark events whose ``<results_dir>/<event>.json`` exists as externally supplied (not verified); others stay open."""
    if not results_dir or not os.path.isdir(results_dir):
        return []
    attached = []
    for ev in ds["events"]:
        p = os.path.join(results_dir, "%s.json" % ev["id"])
        if ev["analysis"] == "external" and os.path.isfile(p):
            with open(p, encoding="utf-8") as fh:
                data = json.load(fh)
            if not all(k in data for k in ("tool", "version", "outputs")):
                raise FlowAssuranceError("external result %s needs tool, version and outputs" % p)
            ev["status"] = "external_supplied"
            ev["result"] = {"tool": data["tool"], "version": data["version"], "outputs": list(data["outputs"].keys()) if isinstance(data["outputs"], dict) else data["outputs"],
                            "verified_by_toolkit": False}
            attached.append(ev["id"])
    return attached


def two_fluid_profile(response, segments_in_order, name="TwoFluidPipe"):
    """Convert a ``TwoFluidPipeResponse`` dict to the study profile layout for one path.

    Args:
        response: parsed JSON of a ``TwoFluidPipeResponse`` (``profile.positionM`` etc.).
        segments_in_order: route segment dicts of the path in flow order (``id``, ``length_m``); positions are mapped to segments by chainage.
        name: provenance label.

    Elevation and mixture density are not part of the response and are kept ``None`` (unavailable, never zero).
    Raises :class:`FlowAssuranceError` when the response has no profile or its length does not match the route.
    """
    prof = (response or {}).get("profile")
    if not prof or not prof.get("positionM"):
        raise FlowAssuranceError("the response has no profile.positionM; export it with DetailLevel FULL")
    pos = [float(v) for v in prof["positionM"]]
    total = sum(float(s["length_m"]) for s in segments_in_order)
    if abs(pos[-1] - total) > 0.02 * total:
        raise FlowAssuranceError("response length %.1f m does not match the route path length %.1f m" % (pos[-1], total))
    n = len(pos)
    for key in ("pressureBara", "temperatureC", "liquidHoldup"):
        if len(prof.get(key) or []) != n:
            raise FlowAssuranceError("profile.%s has %d points, expected %d" % (key, len(prof.get(key) or []), n))
    seg_id, edges = [], []
    acc = 0.0
    for s in segments_in_order:
        edges.append((acc, acc + float(s["length_m"]), s["id"]))
        acc += float(s["length_m"])
    for x in pos:
        seg_id.append(next((sid for a, b, sid in edges if a - 1e-9 <= x <= b + 1e-9), edges[-1][2]))
    gas = prof.get("gasVelocityMS") or [None] * n
    liq = prof.get("liquidVelocityMS") or [None] * n
    return {"chainage_m": pos, "segment_id": seg_id, "elevation_m": [None] * n, "p_bara": [float(v) for v in prof["pressureBara"]],
            "t_c": [float(v) for v in prof["temperatureC"]], "holdup": [float(v) for v in prof["liquidHoldup"]], "v_sg_m_s": [None if v is None else float(v) for v in gas],
            "v_sl_m_s": [None if v is None else float(v) for v in liq],
            "v_mix_m_s": [None if (g is None or l is None) else float(g) + float(l) for g, l in zip(gas, liq)], "rho_mix_kg_m3": [None] * n,
            "regime": [str(v) for v in (prof.get("flowRegime") or ["unavailable"] * n)], "provenance": {"tool": name, "scalars": {k: response.get(k) for k in (
                "simulationTimeSeconds", "inletPressureBara", "outletPressureBara", "hydrateRiskSectionCount", "hasWaxRisk", "outletSlugCount") if k in response},
                "qualification": "model-reported steady profile; qualification follows the TwoFluidPipe evidence matrix and is not promoted by this adapter"}}
