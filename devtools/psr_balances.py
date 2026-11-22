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
    "energy_dt_bookkeeping_k": 2.0,    # relaxed energy limit (equivalent temperature shift, K) for units whose NeqSim energy bookkeeping is not closed
    "energy_rel_bookkeeping": 2.0e-2,  # fallback relative limit when no heat capacity is available
    "recycle_mass_rel": 1.0e-3,        # relaxed mass limit for the tear residual of a Recycle unit
}

# Units whose balance residual is a property of the NeqSim implementation, not of the flowsheet. They are
# reported as NOTE (never as PASS) and only while the residual stays below the relaxed tolerance above;
# a larger residual is still a genuine FAIL. Mass and component residuals of ordinary units are never relaxed.
BOOKKEEPING_ENERGY_CLASSES = (
    ("ComponentSplitter", "component splitter: every outlet is re-flashed at the inlet temperature and pressure, "
                          "so the energy residual is the implicit heat of the separation, not a closure error"),
)
BOOKKEEPING_ENERGY_CATEGORIES = {
    "separator": "separator or scrubber: the gas and liquid outlets are re-flashed isothermally at the vessel "
                 "temperature and pressure, so the energy residual is an implicit re-flash term, not a closure error",
}
RECYCLE_NOTE = ("recycle: the mass residual is the tear-stream convergence residual of the iteration, "
                "not a flowsheet closure error")

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


def bookkeeping_note(unit, check):
    """Explanation when a failing ``check`` of ``unit`` is a known implementation property, else None.

    Args:
        unit: equipment record (``category`` and ``java_class`` are used).
        check: ``"energy"`` or ``"mass"``.
    """
    if check == "energy":
        for key, text in BOOKKEEPING_ENERGY_CLASSES:
            if key in str(unit.get("java_class") or ""):
                return text
        return BOOKKEEPING_ENERGY_CATEGORIES.get(unit["category"])
    if check == "mass" and unit["category"] == "recycle":
        return RECYCLE_NOTE
    return None


def unit_balance(unit, streams, case, comps, tol):
    """Balance of one unit for one case.

    A failing check is relabelled NOTE only for the equipment classes in :func:`bookkeeping_note` and only while
    the residual is inside the relaxed tolerance; ``*_note`` then carries the reason.
    """
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
    rec["mass_note"] = ""
    note = bookkeeping_note(unit, "mass")
    if rec["mass_status"] == "FAIL" and note and rec["mass_rel"] <= tol["recycle_mass_rel"]:
        rec["mass_status"], rec["mass_note"] = "NOTE", note
    comp_res = _component_residual(unit["inlet_streams"], unit["outlet_streams"], streams, case, comps)
    rec["component_max_res_kg_h"] = comp_res
    rec["component_status"] = "PASS" if (comp_res <= tol["mass_abs_kg_h"] or
                                         (throughput > 0 and comp_res / throughput <= tol["component_rel"])) else "FAIL"
    if rec["component_status"] == "FAIL" and note and throughput > 0 and comp_res / throughput <= tol["recycle_mass_rel"]:
        rec["component_status"], rec["component_note"] = "NOTE", note
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
        cp_flow = sum((_num(r.get("mass_kg_h")) / 3600.0) * _num(r.get("cp_kj_kg_k")) for r in
                      (streams[s]["results"].get(case, {}) for s in unit["inlet_streams"]) if r.get("available"))
        rec["energy_dt_equiv_k"] = abs(residual) / cp_flow if cp_flow > 0 else None
        ok = abs(residual) <= tol["energy_abs_kw"] or rec["energy_rel"] <= tol["energy_rel"]
        rec["energy_status"] = "PASS" if ok else "FAIL"
        rec["energy_note"] = ""
        note = bookkeeping_note(unit, "energy")
        within = (rec["energy_dt_equiv_k"] <= tol["energy_dt_bookkeeping_k"]) if rec["energy_dt_equiv_k"] is not None \
            else rec["energy_rel"] <= tol["energy_rel_bookkeeping"]
        if not ok and note and within:
            rec["energy_status"], rec["energy_note"] = "NOTE", note
    if "StreamSaturator" in str(unit.get("java_class") or ""):
        _saturator_note(rec, unit, streams, case, comps)
    return rec


SATURATOR_NOTE = ("stream saturator: the unit adds the saturating component (water) to the stream, so the residual is the "
                  "boundary supply of that component (a feed generator), not a closure error")


def _saturator_note(rec, unit, streams, case, comps):
    """Relabel the mass/component/energy residual of a stream saturator as NOTE when it is the added component.

    The unit's mass residual must equal the sum of the component increments (everything added is a modelled component)
    and the enthalpy increment is reported as the implied energy of the added material.
    """
    in_ids, out_ids = unit["inlet_streams"], unit["outlet_streams"]
    added = 0.0
    for name in comps:
        a = sum(((streams[s]["results"][case].get("flow_kg_h") or {}).get(name) or 0.0) for s in in_ids
                if streams[s]["results"].get(case, {}).get("available"))
        b = sum(((streams[s]["results"][case].get("flow_kg_h") or {}).get(name) or 0.0) for s in out_ids
                if streams[s]["results"].get(case, {}).get("available"))
        if b - a > 0:
            added += b - a
    if added <= 0 or abs(rec["mass_res_kg_h"] - added) > max(1e-3, 1e-4 * added):
        return
    rec["mass_status"], rec["mass_note"] = "NOTE", SATURATOR_NOTE
    rec["component_status"], rec["component_note"] = "NOTE", SATURATOR_NOTE
    if rec.get("energy_status") == "N/A" and rec.get("implied_energy_kw") is not None:
        rec["energy_res_kw"] = rec["implied_energy_kw"]
        rec["energy_status"], rec["energy_note"] = "NOTE", SATURATOR_NOTE + " (energy residual = enthalpy increment of the added material)"


def _explained(residual, attributed, scale, rel_tol, abs_tol):
    """True when what is left of ``residual`` after removing the bookkeeping part ``attributed`` is within tolerance."""
    left = abs(residual - attributed)
    return left <= abs_tol or (scale > 0 and left / scale <= rel_tol)


def _note_status(status, residual, units, key_status, key_res, scale, rel_tol, abs_tol, tol_limit):
    """Relabel a failing boundary check as NOTE when the NOTE-class unit residuals inside it explain it."""
    if status != "FAIL":
        return status, ""
    noted = [u for u in units if u.get(key_status) == "NOTE" and u.get(key_res) is not None]
    if not noted:
        return status, ""
    attributed = sum(u[key_res] for u in noted)
    key_note = key_status.replace("_status", "_note")
    sat = sum(u[key_res] for u in noted if str(u.get(key_note) or "").startswith("stream saturator"))
    # the size limit guards recycle/re-flash bookkeeping; a stream saturator's boundary supply is explained in full
    if tol_limit is not None and scale > 0 and abs(residual - sat) / scale > tol_limit:
        return status, ""
    if _explained(residual, attributed, scale, rel_tol, abs_tol):
        return "NOTE", "explained by the bookkeeping residual of: %s" % ", ".join(u["tag"] for u in noted[:6])
    return status, ""


def _ext_duty(unit, case):
    """External heat duty of a unit for the area and overall energy balances.

    A two-stream heat exchanger (two inlets and two outlets in the model) moves heat from one modelled stream to the
    other: its ``getDuty()`` is an internal transfer, not an external duty, so it is excluded. Columns report the sum
    of reboiler and condenser duty (extracted by ``psr_extract.unit_results``).
    """
    if unit.get("category") == "exchanger" and len(unit.get("inlet_streams") or []) >= 2 \
            and len(unit.get("outlet_streams") or []) >= 2:
        return 0.0
    return _num(unit["results"].get(case, {}).get("duty_kw"))


def area_balances(data, case, tol, unit_recs=None):
    """Boundary balance of every area (mass, energy with duties and work).

    ``unit_recs`` (from :func:`unit_balance`) lets a failing area check be explained by NOTE-class units inside it.
    """
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
        q = sum(_ext_duty(u, case) for u in data["equipment"] if u["area"] == name)
        w = sum(_num(u["results"].get(case, {}).get("power_kw")) for u in data["equipment"] if u["area"] == name)
        throughput = max(m_in, m_out)
        mass_res = m_out - m_in
        mass_rel = abs(mass_res) / throughput if throughput > 0 else 0.0
        e_res = (h_out - h_in) - (q + w)
        scale = max(abs(h_in), abs(h_out), abs(q) + abs(w))
        e_rel = abs(e_res) / scale if scale > 0 else 0.0
        mass_status = "PASS" if (abs(mass_res) <= tol["mass_abs_kg_h"] or mass_rel <= tol["mass_rel"]) else "FAIL"
        energy_status = "PASS" if (abs(e_res) <= tol["energy_abs_kw"] or e_rel <= tol["energy_rel"]) else "FAIL"
        members = [u for u in (unit_recs or []) if u["area"] == name]
        mass_status, mass_note = _note_status(mass_status, mass_res, members, "mass_status", "mass_res_kg_h", throughput,
                                              tol["mass_rel"], tol["mass_abs_kg_h"], tol["recycle_mass_rel"])
        energy_status, energy_note = _note_status(energy_status, e_res, members, "energy_status", "energy_res_kw", scale,
                                                  tol["energy_rel"], tol["energy_abs_kw"], None)
        rows.append({
            "area": name, "n_inflow": len(inflow), "n_outflow": len(outflow),
            "mass_in_kg_h": m_in, "mass_out_kg_h": m_out, "mass_res_kg_h": mass_res, "mass_rel": mass_rel,
            "mass_status": mass_status, "mass_note": mass_note,
            "h_in_kw": h_in, "h_out_kw": h_out, "duty_kw": q, "power_kw": w, "energy_res_kw": e_res, "energy_rel": e_rel,
            "energy_status": energy_status, "energy_note": energy_note,
            "inflow_streams": inflow, "outflow_streams": outflow,
        })
    return rows


def overall_balance(data, case, tol, unit_recs=None):
    """Plant-level boundary balance: feeds in, products out, all duties and work."""
    streams = _stream_index(data)
    feeds = [s["id"] for s in data["streams"] if s["kind"] == "feed"]
    products = [s["id"] for s in data["streams"] if s["kind"] == "product"]
    comps = [c["name"] for c in data["components"]]
    m_in, miss_f = _sum_streams(feeds, streams, case, "mass_kg_h")
    m_out, miss_p = _sum_streams(products, streams, case, "mass_kg_h")
    h_in, _ = _sum_streams(feeds, streams, case, "H_kw")
    h_out, _ = _sum_streams(products, streams, case, "H_kw")
    q_total = sum(_ext_duty(u, case) for u in data["equipment"])
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
    mass_status = "PASS" if mass_rel <= tol["overall_mass_rel"] else "FAIL"
    energy_status = "PASS" if (abs(e_res) <= tol["energy_abs_kw"] or e_rel <= tol["energy_rel"]) else "FAIL"
    members = list(unit_recs or [])
    mass_status, mass_note = _note_status(mass_status, m_out - m_in, members, "mass_status", "mass_res_kg_h", throughput,
                                          tol["overall_mass_rel"], tol["mass_abs_kg_h"], tol["recycle_mass_rel"])
    energy_status, energy_note = _note_status(energy_status, e_res, members, "energy_status", "energy_res_kw", scale,
                                              tol["energy_rel"], tol["energy_abs_kw"], None)
    return {
        "n_feeds": len(feeds), "n_products": len(products), "feed_streams": feeds, "product_streams": products,
        "mass_in_kg_h": m_in, "mass_out_kg_h": m_out, "mass_res_kg_h": m_out - m_in, "mass_rel": mass_rel,
        "mass_status": mass_status, "mass_note": mass_note,
        "h_in_kw": h_in, "h_out_kw": h_out, "duty_kw": q_total, "power_kw": w_total,
        "energy_res_kw": e_res, "energy_rel": e_rel,
        "energy_status": energy_status, "energy_note": energy_note,
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
    counts = {"PASS": 0, "FAIL": 0, "NOTE": 0, "N/A": 0}
    for case in data["cases"]:
        cid = case["id"]
        units = [unit_balance(u, streams, cid, comps, tol) for u in data["equipment"]
                 if u["category"] != "logic" and (u["inlet_streams"] or u["outlet_streams"])]
        areas = area_balances(data, cid, tol, units)
        overall = overall_balance(data, cid, tol, units)
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
    out["fail_items"] = _status_items(out, "FAIL")
    out["note_items"] = _status_items(out, "NOTE")
    return out


def _residual(rec, key):
    """Residual belonging to a status key (kg/h for mass and component checks, kW for energy)."""
    if key == "energy_status":
        return rec.get("energy_res_kw")
    if key == "component_status":
        return rec.get("component_max_res_kg_h")
    return rec.get("mass_res_kg_h")


def _status_items(result, status):
    """List every balance check with the given status (FAIL or NOTE) across cases and levels."""
    items = []
    for cid, blocks in result["cases"].items():
        for level, recs in (("unit", blocks["units"]), ("area", blocks["areas"]), ("overall", [blocks["overall"]])):
            for rec in recs:
                for key in ("mass_status", "component_status", "energy_status"):
                    if rec.get(key) != status:
                        continue
                    item = {"case": cid, "level": level, "id": rec.get("tag") or rec.get("area") or "overall", "check": key,
                            "residual": _residual(rec, key)}
                    note = rec.get(key.replace("_status", "_note"))
                    if status == "NOTE":
                        item["note"] = note or "bookkeeping residual of a NeqSim equipment type"
                        if key == "energy_status" and rec.get("energy_dt_equiv_k") is not None:
                            item["note"] += "; equals %.2f K of inlet heat-capacity flow" % rec["energy_dt_equiv_k"]
                    items.append(item)
    return items


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
