"""Synthetic process data sets for testing the professional process overview (no JVM needed).

:func:`gas_plant_data` builds a ``neqsim.process_study_report.data/1`` dictionary with everything the overview
renderer must handle: a multiphase inlet separation, branching (splitter to two identical compression trains),
a two-sided gas/gas heat exchanger, a recycle (anti-surge return through a numerical Recycle element), an
in-line valve, oil stabilisation with a heater, pump and second separator, produced-water treatment and three
boundary products. Values are plausible but invented; nothing refers to a real facility.
"""
from __future__ import annotations

SCHEMA = "neqsim.process_study_report.data/1"


def _phases(gas=0.0, oil=0.0, water=0.0):
    out = []
    for t, v in (("gas", gas), ("oil", oil), ("aqueous", water)):
        if v > 0:
            out.append({"type": t, "mass_fraction": v, "mole_fraction": v})
    return out


def _stream(sid, label, kind, source, dest, mass, t_c, p_bara, phases, area, case="A"):
    dests = dest if isinstance(dest, list) else ([dest] if dest else [])
    return {
        "id": sid, "name": label, "label": label, "kind": kind, "source": source, "destination": dests[0] if dests else None,
        "producers": [source] if source else [], "consumers": dests, "area": area,
        "results": {case: {"available": True, "mass_kg_h": mass, "t_c": t_c, "p_bara": p_bara, "p_barg": p_bara - 1.01325,
                           "mole_kmol_h": mass / 22.0, "std_vol_ideal_sm3_h": mass / 22.0 * 23.64,
                           "state": "+".join(p["type"] for p in phases), "phases": phases}},
    }


def _unit(tag, cat, jc, area, ins, outs, duty_kw=None, power_kw=None, dp_bar=None, case="A"):
    return {"tag": tag, "name": tag, "area": area, "java_class": jc, "category": cat, "inlet_streams": ins,
            "outlet_streams": outs, "design": {},
            "results": {case: {"duty_kw": duty_kw, "power_kw": power_kw, "dp_bar": dp_bar, "extras": {}}}}


def gas_plant_data(case="A", case_label="Design case (synthetic)"):
    """Two-train gas plant with recycle, two-sided exchanger and three products (synthetic)."""
    G, O, W = _phases(gas=1.0), _phases(oil=1.0), _phases(water=1.0)
    MP = _phases(gas=0.55, oil=0.35, water=0.10)
    st = [
        _stream("S001", "Well A-1", "feed", None, "HD-100", 60000.0, 60.0, 72.0, MP, "inlet separation"),
        _stream("S002", "Well A-2", "feed", None, "HD-100", 50000.0, 58.0, 72.0, MP, "inlet separation"),
        _stream("S003", "Well A-3", "feed", None, "HD-100", 40000.0, 62.0, 72.0, MP, "inlet separation"),
        _stream("S004", "Inlet header", "internal", "HD-100", "V-100", 150000.0, 60.0, 71.5, MP, "inlet separation"),
        _stream("S005", "HP gas", "internal", "V-100", "E-100", 82000.0, 60.0, 70.0, G, "inlet separation"),
        _stream("S006", "HP oil", "internal", "V-100", "VLV-200", 53000.0, 60.0, 70.0, O, "inlet separation"),
        _stream("S007", "HP water", "internal", "V-100", "V-400", 15000.0, 60.0, 70.0, W, "inlet separation"),
        # gas/gas exchanger: hot side HP gas -> cold side export gas
        _stream("S008", "Cooled HP gas", "internal", "E-100", "SP-300", 82000.0, 35.0, 69.5, G, "inlet separation"),
        # oil stabilisation
        _stream("S009", "Oil letdown", "internal", "VLV-200", "H-200", 53000.0, 58.0, 20.0, O, "oil stabilisation"),
        _stream("S010", "Heated oil", "internal", "H-200", "V-200", 53000.0, 85.0, 19.5, O, "oil stabilisation"),
        _stream("S011", "Flash gas", "internal", "V-200", "SP-300", 6000.0, 85.0, 19.5, G, "oil stabilisation"),
        _stream("S012", "Stabilised oil", "internal", "V-200", "P-200", 47000.0, 85.0, 19.5, O, "oil stabilisation"),
        _stream("S013", "Oil export", "product", "P-200", None, 47000.0, 86.0, 45.0, O, "oil stabilisation"),
        # compression: splitter to two identical trains
        _stream("S014", "Gas to compression", "internal", "SP-300", "MX-300", 88000.0, 38.0, 19.5, G, "compression train A"),
        _stream("S015", "Train A suction", "internal", "MX-300", "V-301", 46000.0, 38.0, 19.5, G, "compression train A"),
        _stream("S016", "Train A scrubbed gas", "internal", "V-301", "K-301", 45500.0, 38.0, 19.3, G, "compression train A"),
        _stream("S017", "Train A scrubber liquid", "internal", "V-301", "V-200", 500.0, 38.0, 19.3, O, "compression train A"),
        _stream("S018", "Train A discharge", "internal", "K-301", "E-301", 45500.0, 135.0, 70.0, G, "compression train A"),
        _stream("S019", "Train A cooled", "internal", "E-301", "SP-301", 45500.0, 40.0, 69.5, G, "compression train A"),
        _stream("S020", "Train A to export", "internal", "SP-301", "MX-500", 43500.0, 40.0, 69.5, G, "compression train A"),
        _stream("S021", "Train A anti-surge", "internal", "SP-301", "R-301", 2000.0, 40.0, 69.5, G, "compression train A"),
        _stream("S022", "Train A recycle", "internal", "R-301", "MX-300", 2000.0, 40.0, 19.6, G, "compression train A"),
        _stream("S023", "Train B suction", "internal", "MX-302", "V-302", 46000.0, 38.0, 19.5, G, "compression train B"),
        _stream("S024", "Train B scrubbed gas", "internal", "V-302", "K-302", 45500.0, 38.0, 19.3, G, "compression train B"),
        _stream("S025", "Train B scrubber liquid", "internal", "V-302", "V-200", 500.0, 38.0, 19.3, O, "compression train B"),
        _stream("S026", "Train B discharge", "internal", "K-302", "E-302", 45500.0, 135.0, 70.0, G, "compression train B"),
        _stream("S027", "Train B cooled", "internal", "E-302", "SP-302", 45500.0, 40.0, 69.5, G, "compression train B"),
        _stream("S028", "Train B to export", "internal", "SP-302", "MX-500", 43500.0, 40.0, 69.5, G, "compression train B"),
        _stream("S029", "Train B anti-surge", "internal", "SP-302", "R-302", 2000.0, 40.0, 69.5, G, "compression train B"),
        _stream("S030", "Train B recycle", "internal", "R-302", "MX-302", 2000.0, 40.0, 19.6, G, "compression train B"),
        _stream("S031", "Gas to train B", "internal", "SP-300", "MX-302", 44000.0, 38.0, 19.5, G, "compression train B"),
        # export: mixer, exchanger cold side, export compressor
        _stream("S032", "Export header", "internal", "MX-500", "E-100", 87000.0, 40.0, 69.5, G, "gas export"),
        _stream("S033", "Preheated export gas", "internal", "E-100", "K-500", 87000.0, 52.0, 69.0, G, "gas export"),
        _stream("S034", "Export gas", "product", "K-500", None, 87000.0, 95.0, 180.0, G, "gas export"),
        # produced water
        _stream("S035", "Degassed water", "internal", "V-400", "P-400", 14800.0, 60.0, 5.0, W, "produced water"),
        _stream("S036", "Water gas", "internal", "V-400", "SP-300", 200.0, 60.0, 5.0, G, "produced water"),
        _stream("S037", "Water to disposal", "product", "P-400", None, 14800.0, 60.0, 12.0, W, "produced water"),
    ]
    units = [
        _unit("HD-100", "mixer", "Mixer", "inlet separation", ["S001", "S002", "S003"], ["S004"]),
        _unit("V-100", "separator", "ThreePhaseSeparator", "inlet separation", ["S004"], ["S005", "S006", "S007"]),
        _unit("E-100", "exchanger", "HeatExchanger", "inlet separation", ["S005", "S032"], ["S008", "S033"], duty_kw=2100.0),
        _unit("VLV-200", "valve", "ThrottlingValve", "oil stabilisation", ["S006"], ["S009"], dp_bar=50.0),
        _unit("H-200", "heater", "Heater", "oil stabilisation", ["S009"], ["S010"], duty_kw=1450.0),
        _unit("V-200", "separator", "Separator", "oil stabilisation", ["S010", "S017", "S025"], ["S011", "S012"]),
        _unit("P-200", "pump", "Pump", "oil stabilisation", ["S012"], ["S013"], power_kw=48.0),
        _unit("SP-300", "mixer", "Mixer", "inlet separation", ["S008", "S011", "S036"], ["S014", "S031"]),
        _unit("MX-300", "mixer", "Mixer", "compression train A", ["S014", "S022"], ["S015"]),
        _unit("V-301", "separator", "GasScrubber", "compression train A", ["S015"], ["S016", "S017"]),
        _unit("K-301", "compressor", "Compressor", "compression train A", ["S016"], ["S018"], power_kw=2650.0, dp_bar=-50.7),
        _unit("E-301", "cooler", "Cooler", "compression train A", ["S018"], ["S019"], duty_kw=-3100.0),
        _unit("SP-301", "splitter", "Splitter", "compression train A", ["S019"], ["S020", "S021"]),
        _unit("R-301", "recycle", "Recycle", "compression train A", ["S021"], ["S022"]),
        _unit("MX-302", "mixer", "Mixer", "compression train B", ["S031", "S030"], ["S023"]),
        _unit("V-302", "separator", "GasScrubber", "compression train B", ["S023"], ["S024", "S025"]),
        _unit("K-302", "compressor", "Compressor", "compression train B", ["S024"], ["S026"], power_kw=2650.0, dp_bar=-50.7),
        _unit("E-302", "cooler", "Cooler", "compression train B", ["S026"], ["S027"], duty_kw=-3100.0),
        _unit("SP-302", "splitter", "Splitter", "compression train B", ["S027"], ["S028", "S029"]),
        _unit("R-302", "recycle", "Recycle", "compression train B", ["S029"], ["S030"]),
        _unit("MX-500", "mixer", "Mixer", "gas export", ["S020", "S028"], ["S032"]),
        _unit("K-500", "compressor", "Compressor", "gas export", ["S033"], ["S034"], power_kw=7800.0, dp_bar=-111.0),
        _unit("V-400", "separator", "Separator", "produced water", ["S007"], ["S035", "S036"]),
        _unit("P-400", "pump", "Pump", "produced water", ["S035"], ["S037"], power_kw=12.0),
    ]
    # SP-300 is a splitter in function (one combined gas stream to two trains); keep as category splitter
    units[7]["category"] = "splitter"
    units[7]["java_class"] = "Splitter"
    # but it has three inlets: model it as mixer feeding a splitter would double the node; the data keeps one node
    areas = []
    for a in ("inlet separation", "oil stabilisation", "compression train A", "compression train B", "gas export", "produced water"):
        areas.append({"name": a, "units": [u["tag"] for u in units if u["area"] == a]})
    return {
        "schema": SCHEMA,
        "meta": {"model_name": "Synthetic two-train gas plant", "generated_utc": "2026-01-01T00:00:00Z",
                 "standard_conditions": {"t_c": 15.0, "p_bara": 1.01325}, "neqsim_version": "synthetic", "eos": "none",
                 "data_file": "ppo_examples.gas_plant_data"},
        "cases": [{"id": case, "label": case_label}],
        "areas": areas,
        "components": [],
        "equipment": units,
        "streams": st,
    }


def main(argv=None):
    """Write the synthetic data set to a JSON file: ``python ppo_examples.py out.json``."""
    import json
    import sys

    argv = sys.argv[1:] if argv is None else argv
    path = argv[0] if argv else "gas_plant_process_data.json"
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(gas_plant_data(), fh, indent=1)
    print(path)
    return 0


if __name__ == "__main__":
    main()
