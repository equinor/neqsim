"""Mass, component and energy balances for the process-study-report toolkit.

All balances are computed from the extracted data set (``psr_extract``), never
from the simulator, so that a report can be re-checked offline. Sign
convention: heat added to the process fluid (heater duty) and shaft work added
to the fluid (compressor or pump power) are positive; heat removed (cooler
duty) is negative. The energy balance of a unit is

    sum(H_out) - sum(H_in) = Q + W            (kW)

where H is the stream enthalpy flow on the NeqSim enthalpy reference.
"""

DEFAULT_TOLERANCES = {
    "mass_rel": 1.0e-4,        # |residual| / throughput, per unit and per area
    "component_rel": 1.0e-4,   # max over components, relative to unit throughput
    "energy_rel": 1.0e-3,      # relative to the larger of |sum H_in|, |sum H_out|, |Q|+|W|
    "energy_abs_kw": 1.0,      # absolute floor below which a residual always passes
    "mass_abs_kg_h": 1.0e-3,   # absolute floor (kg/h)
    "overall_mass_rel": 1.0e-3,
}

# Unit categories whose energy balance is defined by stream enthalpies alone.
ADIABATIC = {"valve", "mixer", "splitter", "separator", "ejector", "recycle", "pipe"}
DUTY_UNITS = {"heater", "cooler"}
WORK_UNITS = {"compressor", "pump", "expander"}


def _num(value, default=0.0):
    return default if value is None else float(value)


def _stream_index(data):
    return {s["id"]: s for s in data["streams"]}


def _sum_streams(ids, streams, case, key):
    total = 0.0
    missing = 0
    for sid in ids:
        res = streams[sid]["results"].get(case, {})
        val = res.get(key) if res.get("available") else None
        if val is None:
            missing += 1
        else:
            total += val
    return total, missing


def _component_residual(in_ids, out_ids, streams, case, comps):
    worst = 0.0
    for name in comps:
        a = sum(((streams[s]["results"][case].get("flow_kg_h") or {}).get(name) or 0.0) for s in in_ids
                if streams[s]["results"].get(case, {}).get("available"))
        b = sum(((streams[s]["results"][case].get("flow_kg_h") or {}).get(name) or 0.0) for s in out_ids
                if streams[s]["results"].get(case, {}).get("available"))
        worst = max(worst, abs(b - a))
    return worst


def unit_balance(unit, streams, case, comps, tol):
    """Balance of one unit for one case."""
    res = unit["results"].get(case, {})
    m_in, miss_in = _sum_streams(unit["inlet_streams"], streams, case, "mass_kg_h")
    m_out, miss_out = _sum_streams(unit["outlet_streams"], streams, case, "mass_kg_h")
    h_in, _ = _sum_streams(unit["inlet_streams"], streams, case, "H_kw")
    h_out, _ = _sum_streams(unit["outlet_streams"], streams, case, "H_kw")
    throughput = max(m_in, m_out, 0.0)
    rec = {"tag": unit["tag"], "area": unit["area"], "category": unit["category"], "type": unit["java_class"],
           "mass_in_kg_h": m_in, "mass_out_kg_h": m_out, "mass_res_kg_h": m_out - m_in,
           "mass_rel": (abs(m_out - m_in) / throughput) if throughput > 0 else 0.0}
    rec["mass_status"] = "PASS" if (abs(m_out - m_in) <= tol["mass_abs_kg_h"] or rec["mass_rel"] <= tol["mass_rel"]) else "FAIL"
    comp_res = _component_residual(unit["inlet_streams"], unit["outlet_streams"], streams, case, comps)
    rec["component_max_res_kg_h"] = comp_res
    rec["component_status"] = "PASS" if (comp_res <= tol["mass_abs_kg_h"] or
                                         (throughput > 0 and comp_res / throughput <= tol["component_rel"])) else "FAIL"
    rec["h_in_kw"], rec["h_out_kw"] = h_in, h_out
    q = res.get("duty_kw")
    w = res.get("power_kw")
    rec["duty_kw"], rec["power_kw"] = q, w
    rec["implied_energy_kw"] = h_out - h_in
    category = unit["category"]
    if category in ADIABATIC:
        expected = 0.0
    elif category in DUTY_UNITS and q is not None:
        expected = q
    elif category in WORK_UNITS and w is not None:
        expected = w
    else:
        expected = None
    if expected is None or miss_in or miss_out:
        rec["energy_res_kw"] = None
        rec["energy_status"] = "N/A"
        rec["energy_note"] = "no reported duty/power" if expected is None else "stream data missing"
    else:
        residual = (h_out - h_in) - expected
        scale = max(abs(h_in), abs(h_out), abs(_num(q)) + abs(_num(w)))
        rec["energy_res_kw"] = residual
        rec["energy_rel"] = abs(residual) / scale if scale > 0 else 0.0
        ok = abs(residual) <= tol["energy_abs_kw"] or rec["energy_rel"] <= tol["energy_rel"]
        rec["energy_status"] = "PASS" if ok else "FAIL"
        rec["energy_note"] = ""
    return rec


def area_balances(data, case, tol):
    """Boundary balance of every area (mass, energy with duties and work)."""
    streams = _stream_index(data)
    unit_area = {u["tag"]: u["area"] for u in data["equipment"]}
    rows = []
    for area in data["areas"]:
        name = area["name"]
        inflow, outflow = [], []
        for s in data["streams"]:
            prod_in = [t for t in s["producers"] if unit_area.get(t) == name]
            cons_in = [t for t in s["consumers"] if unit_area.get(t) == name]
            if cons_in and not prod_in:
                inflow.append(s["id"])
            if prod_in and not cons_in:
                outflow.append(s["id"])
            elif prod_in and cons_in:
                continue
        # streams produced in the area and consumed only elsewhere or nowhere are outflows
        for s in data["streams"]:
            prod_in = [t for t in s["producers"] if unit_area.get(t) == name]
            cons_out = [t for t in s["consumers"] if unit_area.get(t) != name]
            if prod_in and cons_out and s["id"] not in outflow:
                outflow.append(s["id"])
        m_in, _ = _sum_streams(inflow, streams, case, "mass_kg_h")
        m_out, _ = _sum_streams(outflow, streams, case, "mass_kg_h")
        h_in, _ = _sum_streams(inflow, streams, case, "H_kw")
        h_out, _ = _sum_streams(outflow, streams, case, "H_kw")
        q = sum(_num(u["results"].get(case, {}).get("duty_kw")) for u in data["equipment"] if u["area"] == name)
        w = sum(_num(u["results"].get(case, {}).get("power_kw")) for u in data["equipment"] if u["area"] == name)
        throughput = max(m_in, m_out)
        mass_res = m_out - m_in
        mass_rel = abs(mass_res) / throughput if throughput > 0 else 0.0
        e_res = (h_out - h_in) - (q + w)
        scale = max(abs(h_in), abs(h_out), abs(q) + abs(w))
        e_rel = abs(e_res) / scale if scale > 0 else 0.0
        rows.append({
            "area": name, "n_inflow": len(inflow), "n_outflow": len(outflow),
            "mass_in_kg_h": m_in, "mass_out_kg_h": m_out, "mass_res_kg_h": mass_res, "mass_rel": mass_rel,
            "mass_status": "PASS" if (abs(mass_res) <= tol["mass_abs_kg_h"] or mass_rel <= tol["mass_rel"]) else "FAIL",
            "h_in_kw": h_in, "h_out_kw": h_out, "duty_kw": q, "power_kw": w, "energy_res_kw": e_res, "energy_rel": e_rel,
            "energy_status": "PASS" if (abs(e_res) <= tol["energy_abs_kw"] or e_rel <= tol["energy_rel"]) else "FAIL",
            "inflow_streams": inflow, "outflow_streams": outflow,
        })
    return rows


def overall_balance(data, case, tol):
    """Plant-level boundary balance: feeds in, products out, all duties and work."""
    streams = _stream_index(data)
    feeds = [s["id"] for s in data["streams"] if s["kind"] == "feed"]
    products = [s["id"] for s in data["streams"] if s["kind"] == "product"]
    comps = [c["name"] for c in data["components"]]
    m_in, miss_f = _sum_streams(feeds, streams, case, "mass_kg_h")
    m_out, miss_p = _sum_streams(products, streams, case, "mass_kg_h")
    h_in, _ = _sum_streams(feeds, streams, case, "H_kw")
    h_out, _ = _sum_streams(products, streams, case, "H_kw")
    q_total = sum(_num(u["results"].get(case, {}).get("duty_kw")) for u in data["equipment"])
    w_total = sum(_num(u["results"].get(case, {}).get("power_kw")) for u in data["equipment"])
    comp_rows = []
    for name in comps:
        a = sum(((streams[s]["results"][case].get("flow_kg_h") or {}).get(name) or 0.0) for s in feeds)
        b = sum(((streams[s]["results"][case].get("flow_kg_h") or {}).get(name) or 0.0) for s in products)
        comp_rows.append({"component": name, "in_kg_h": a, "out_kg_h": b, "res_kg_h": b - a,
                          "rel": abs(b - a) / a if a > 0 else (0.0 if b == 0 else None)})
    throughput = max(m_in, m_out)
    mass_rel = abs(m_out - m_in) / throughput if throughput > 0 else 0.0
    e_res = (h_out - h_in) - (q_total + w_total)
    scale = max(abs(h_in), abs(h_out), abs(q_total) + abs(w_total))
    e_rel = abs(e_res) / scale if scale > 0 else 0.0
    return {
        "n_feeds": len(feeds), "n_products": len(products), "feed_streams": feeds, "product_streams": products,
        "mass_in_kg_h": m_in, "mass_out_kg_h": m_out, "mass_res_kg_h": m_out - m_in, "mass_rel": mass_rel,
        "mass_status": "PASS" if mass_rel <= tol["overall_mass_rel"] else "FAIL",
        "h_in_kw": h_in, "h_out_kw": h_out, "duty_kw": q_total, "power_kw": w_total,
        "energy_res_kw": e_res, "energy_rel": e_rel,
        "energy_status": "PASS" if (abs(e_res) <= tol["energy_abs_kw"] or e_rel <= tol["energy_rel"]) else "FAIL",
        "components": comp_rows, "missing_streams": miss_f + miss_p,
    }


def compute_balances(data, tolerances=None):
    """Compute unit, area and overall balances for every case.

    Args:
        data: extracted (and merged) data set.
        tolerances: optional overrides for :data:`DEFAULT_TOLERANCES`.

    Returns:
        dict with ``tolerances``, ``cases`` (per case ``units``, ``areas``,
        ``overall``) and a ``summary`` of PASS/FAIL/N/A counts.
    """
    tol = dict(DEFAULT_TOLERANCES)
    tol.update(tolerances or {})
    streams = _stream_index(data)
    comps = [c["name"] for c in data["components"]]
    out = {"tolerances": tol, "sign_convention": "Q and W positive when added to the process fluid; "
           "sum(H_out) - sum(H_in) = Q + W", "cases": {}, "summary": {}}
    counts = {"PASS": 0, "FAIL": 0, "N/A": 0}
    for case in data["cases"]:
        cid = case["id"]
        units = [unit_balance(u, streams, cid, comps, tol) for u in data["equipment"]
                 if u["category"] != "logic" and (u["inlet_streams"] or u["outlet_streams"])]
        areas = area_balances(data, cid, tol)
        overall = overall_balance(data, cid, tol)
        out["cases"][cid] = {"units": units, "areas": areas, "overall": overall}
        for rec in units:
            for key in ("mass_status", "component_status", "energy_status"):
                counts[rec[key]] += 1
        for rec in areas:
            for key in ("mass_status", "energy_status"):
                counts[rec[key]] += 1
        counts[overall["mass_status"]] += 1
        counts[overall["energy_status"]] += 1
    out["summary"] = counts
    out["fail_items"] = [
        {"case": cid, "level": level, "id": rec.get("tag") or rec.get("area") or "overall", "check": key,
         "residual": rec.get(key.replace("_status", "_res_kg_h")) if key != "energy_status" else rec.get("energy_res_kw")}
        for cid, blocks in out["cases"].items()
        for level, recs in (("unit", blocks["units"]), ("area", blocks["areas"]), ("overall", [blocks["overall"]]))
        for rec in recs for key in ("mass_status", "component_status", "energy_status") if rec.get(key) == "FAIL"]
    return out


def utility_summary(data, case):
    """Power, heating and cooling roll-up for one case (kW), listed per unit."""
    rows = {"power": [], "heating": [], "cooling": []}
    for u in data["equipment"]:
        res = u["results"].get(case, {})
        if res.get("power_kw") is not None and u["category"] in WORK_UNITS:
            rows["power"].append((u["tag"], u["category"], res["power_kw"]))
        duty = res.get("duty_kw")
        if duty is not None and u["category"] in ("heater", "cooler", "exchanger"):
            rows["heating" if duty > 0 else "cooling"].append((u["tag"], u["category"], duty))
    totals = {
        "power_in_kw": sum(r[2] for r in rows["power"] if r[2] > 0),
        "power_out_kw": sum(r[2] for r in rows["power"] if r[2] < 0),
        "heating_kw": sum(r[2] for r in rows["heating"]),
        "cooling_kw": sum(r[2] for r in rows["cooling"]),
    }
    return rows, totals
