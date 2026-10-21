"""Synthetic process-data generator for layout tests (no JVM): random layered networks with cross-links."""
import random


def synthetic_data(n_layers=6, width=4, seed=1, cross_prob=0.5, max_in=2):
    """Return a ``psr_extract``-style data dict of a layered network of valves and mixers.

    Each unit takes 1..max_in inlet streams from units of the previous layer(s); first-layer units are fed from boundary
    streams; units without consumers produce boundary products. The topology is a DAG, so layouts have no back edges.
    """
    rnd = random.Random(seed)
    units, streams = [], []
    layer_units = []
    sid = 0

    def new_stream(source, label):
        nonlocal sid
        sid += 1
        sdict = {"id": "S%03d" % sid, "name": label, "label": label, "kind": "internal", "producers": [source] if source else [],
                 "consumers": [], "source": source, "destination": None, "area": "area",
                 "results": {"A": {"available": True, "phases": [{"type": "gas", "mass_fraction": 1.0, "mole_fraction": 1.0}],
                                   "state": "gas", "mass_kg_h": 1000.0}}}
        streams.append(sdict)
        return sdict

    for li in range(n_layers):
        row = []
        for wi in range(width):
            tag = "U%d_%d" % (li, wi)
            ins = []
            if li == 0:
                f = new_stream(None, "feed %d" % wi)
                f["kind"] = "feed"
                ins.append(f)
            else:
                pool = layer_units[li - 1] + (layer_units[li - 2] if li > 1 and rnd.random() < cross_prob else [])
                for src in rnd.sample(pool, min(len(pool), rnd.randint(1, max_in))):
                    out = new_stream(src["tag"], "%s to %s" % (src["tag"], tag))
                    src["outlet_streams"].append(out["id"])
                    ins.append(out)
            unit = {"tag": tag, "name": tag, "area": "area", "java_class": "ThrottlingValve" if len(ins) == 1 else "Mixer",
                    "category": "valve" if len(ins) == 1 else "mixer", "inlet_streams": [s["id"] for s in ins], "outlet_streams": [],
                    "results": {"A": {"duty_kw": None, "power_kw": None, "dp_bar": 1.0, "extras": {}}}}
            for s in ins:
                s["consumers"].append(tag)
                s["destination"] = tag
            units.append(unit)
            row.append(unit)
        layer_units.append(row)
    for unit in units:
        if not unit["outlet_streams"]:
            p = new_stream(unit["tag"], "%s product" % unit["tag"])
            p["kind"] = "product"
            unit["outlet_streams"].append(p["id"])
    by_id = {s["id"]: s for s in streams}
    for unit in units:
        for sid_ in unit["outlet_streams"]:
            by_id[sid_]["producers"] = [unit["tag"]]
            by_id[sid_]["source"] = unit["tag"]
    return {"schema": "neqsim.process_study_report.data/1", "meta": {"model_name": "synthetic"}, "cases": [{"id": "A", "label": "A"}],
            "areas": [{"name": "area", "units": [u["tag"] for u in units]}], "components": [], "equipment": units, "streams": streams}
