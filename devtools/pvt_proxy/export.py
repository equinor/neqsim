"""Proxy definition (YAML/JSON) and downstream exports.

The proxy definition is the single source for every export. Exporters are registered in
:data:`EXPORTERS`; add a function ``f(definition, out_dir) -> [paths]`` to support a new target.
"""
from __future__ import annotations

import csv
import json
import math
from pathlib import Path
from typing import Any, Callable, Dict, List

STOCK_TANK = "stock_tank"
PURPOSE = ("Forward surface-separation process for PVT-table generation (Eclipse 300, PVTsim, FluidMagic, "
           "OLGA/black-oil tables). Not a method for allocating molar production from measured rates.")


def flow_sequence(stages: List[Dict[str, Any]], scrubber: Dict[str, Any], std: Dict[str, float]) -> List[Dict[str, Any]]:
    """Units in flow order with 1-based numbers and liquid/vapour destinations (0 = product)."""
    names = [s["name"] for s in stages] + [STOCK_TANK]
    order: List[Dict[str, Any]] = []
    for s in stages:
        order.append({"name": s["name"], "kind": "separator", "pressure_bara": s["pressure_bara"],
                      "temperature_C": s["temperature_C"]})
    order.append({"name": STOCK_TANK, "kind": "stock_tank", "pressure_bara": std["pressure_bara"],
                  "temperature_C": std["temperature_K"] - 273.15})
    if scrubber:
        pos = names.index(scrubber["liquid_destination"])
        order.insert(pos, {"name": scrubber["name"], "kind": "scrubber", "pressure_bara": scrubber["pressure_bara"],
                           "temperature_C": scrubber["temperature_C"]})
    number = {u["name"]: i + 1 for i, u in enumerate(order)}
    sources = set(scrubber["gas_sources"]) if scrubber else set()
    liquid_chain = [u["name"] for u in order if u["kind"] != "scrubber"]
    for u in order:
        u["number"] = number[u["name"]]
        if u["kind"] == "scrubber":
            u["liquid_destination"] = number[scrubber["liquid_destination"]]
        elif u["kind"] == "stock_tank":
            u["liquid_destination"] = 0
        else:
            u["liquid_destination"] = number[liquid_chain[liquid_chain.index(u["name"]) + 1]]
        u["vapour_destination"] = number[scrubber["name"]] if u["name"] in sources else 0
    return order


def _clean(o: Any) -> Any:
    if isinstance(o, float):
        return o if math.isfinite(o) else None
    if isinstance(o, dict):
        return {str(k): _clean(v) for k, v in o.items()}
    if isinstance(o, (list, tuple)):
        return [_clean(v) for v in o]
    return o


def write_definition(defn: Dict[str, Any], out_dir: Path) -> List[Path]:
    """Write ``proxy_definition.yaml`` and ``.json``."""
    import yaml
    out_dir.mkdir(parents=True, exist_ok=True)
    data = _clean(defn)
    pj = out_dir / "proxy_definition.json"
    py = out_dir / "proxy_definition.yaml"
    pj.write_text(json.dumps(data, indent=2), encoding="utf-8")
    py.write_text(yaml.safe_dump(data, sort_keys=False, allow_unicode=True), encoding="utf-8")
    return [py, pj]


def load_definition(path) -> Dict[str, Any]:
    """Read a proxy definition (YAML or JSON)."""
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    if p.suffix.lower() == ".json":
        return json.loads(text)
    import yaml
    return yaml.safe_load(text)


def _seq(defn):
    return defn["proxy_process"]["sequence"]


def export_neqsim(defn: Dict[str, Any], out_dir: Path) -> List[Path]:
    """Fixed-value ``proxy`` block for a pvt_proxy configuration plus a rebuild example."""
    pp = defn["proxy_process"]
    block = {"name": pp["name"], "min_pressure_step_bar": 0.0,
             "stages": [{"name": s["name"], "pressure_bara": s["pressure_bara"], "temperature_C": s["temperature_C"]}
                        for s in pp["stages"]]}
    if pp.get("scrubber"):
        sc = pp["scrubber"]
        block["scrubber"] = {"name": sc["name"], "pressure_bara": sc["pressure_bara"], "temperature_C": sc["temperature_C"],
                             "gas_sources": sc["gas_sources"], "liquid_destination": sc["liquid_destination"]}
    path = out_dir / "neqsim_proxy_process.json"
    path.write_text(json.dumps({"standard_conditions": pp["standard_conditions"], "proxy": block}, indent=2),
                    encoding="utf-8")
    example = out_dir / "neqsim_proxy_example.py"
    example.write_text(
        '"""Rebuild the fitted forward proxy for one fluid with NeqSim (generated)."""\n'
        "import json\n\n"
        "from pvt_proxy.config import FeedConditions, ProxyTopology, StandardConditions\n"
        "from pvt_proxy.engine import Engine, fixed_parameters\n"
        "from pvt_proxy.fluids import ComponentDef, FluidTemplate\n\n"
        "spec = json.load(open(%r))\n"
        "topology = ProxyTopology.from_dict(spec['proxy'])\n"
        "std = StandardConditions(**{k: v for k, v in spec['standard_conditions'].items() if k in ('temperature_K', 'pressure_bara')})\n"
        "engine = Engine(std, FeedConditions(pressure_bara=150.0, temperature_C=80.0, rate_kmol_h=100.0))\n"
        "template = FluidTemplate(eos='srk', components=[ComponentDef('methane'), ComponentDef('propane'), ComponentDef('n-decane')])\n"
        "result = engine.forward_train(template, [0.6, 0.2, 0.2], topology).run(fixed_parameters(topology))\n"
        "print(result['oil_Sm3_h'], result['gas_Sm3_h'], result['gor_Sm3_Sm3'])\n" % path.name, encoding="utf-8")
    return [path, example]


def export_e300(defn: Dict[str, Any], out_dir: Path) -> List[Path]:
    """SEPCOND-style separator definition (METRIC units: degC, bara)."""
    pp = defn["proxy_process"]
    lines = ["-- Forward PVT proxy '%s' fitted %s (NeqSim pvt_proxy)" % (pp["name"], pp["calibration"]["fitted_at"]),
             "-- Purpose: surface process for PVT tables. Fit status: %s" % pp["calibration"]["status"]["status"],
             "-- Standard conditions: %.2f K, %.5f bara (set the same in the deck)." %
             (pp["standard_conditions"]["temperature_K"], pp["standard_conditions"]["pressure_bara"]),
             "-- Items: separator name, group, stage, temperature degC, pressure bara, liquid destination stage,",
             "--        vapour destination stage (0 = to the product stream).",
             "-- CHECK the SEPCOND item order and destination conventions against the E300 reference manual",
             "-- of your simulator version before use; the stage table below is the authoritative definition.",
             "SEPCOND"]
    for u in _seq(defn):
        lines.append("  PROXY  FIELD  %d  %.3f  %.4f  %d  %d /  -- %s (%s)" % (
            u["number"], u["temperature_C"], u["pressure_bara"], u["liquid_destination"], u["vapour_destination"],
            u["name"], u["kind"]))
    lines.append("/")
    path = out_dir / "e300_sepcond.inc"
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return [path]


def _stage_table(defn: Dict[str, Any], path: Path) -> Path:
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["stage_number", "name", "kind", "pressure_bara", "temperature_C", "liquid_destination",
                    "vapour_destination"])
        for u in _seq(defn):
            w.writerow([u["number"], u["name"], u["kind"], "%.6g" % u["pressure_bara"], "%.6g" % u["temperature_C"],
                        u["liquid_destination"], u["vapour_destination"]])
    return path


def export_pvtsim(defn: Dict[str, Any], out_dir: Path) -> List[Path]:
    """Generic separator-test definition for PVTsim / FluidMagic style tools."""
    pp = defn["proxy_process"]
    data = {"separator_test": {"name": pp["name"], "purpose": PURPOSE,
                               "standard_conditions": pp["standard_conditions"],
                               "stages": [{"stage": u["number"], "name": u["name"], "type": u["kind"],
                                           "pressure_bara": u["pressure_bara"], "temperature_C": u["temperature_C"],
                                           "liquid_to_stage": u["liquid_destination"],
                                           "vapour_to_stage": u["vapour_destination"]} for u in _seq(defn)],
                               "notes": ["Stage numbers are in flow order; destination 0 = product stream.",
                                         "Tools that only support a linear cascade can use the separator stages and "
                                         "stock tank; the scrubber then has to be represented manually.",
                                         "Calibration status: %s" % pp["calibration"]["status"]["status"]]}}
    path = out_dir / "pvtsim_separator_test.json"
    path.write_text(json.dumps(_clean(data), indent=2), encoding="utf-8")
    return [path, _stage_table(defn, out_dir / "separator_stages.csv")]


def export_blackoil(defn: Dict[str, Any], out_dir: Path) -> List[Path]:
    """Stage table and conditions note for OLGA / black-oil table workflows."""
    pp = defn["proxy_process"]
    note = out_dir / "blackoil_surface_process.md"
    note.write_text(
        "# Surface process for black-oil / OLGA PVT tables\n\n"
        "Use the stages in `separator_stages.csv` (flow order, destination 0 = product) to flash the fluid to stock-tank "
        "conditions when generating Rs, Bo, Bg and GOR tables. Standard conditions: %.2f K, %.5f bara. "
        "Oil volumes are stock-tank liquid at standard conditions; gas volumes are ideal-gas standard volumes.\n\n"
        "Calibration status: **%s**. %s\n" % (
            pp["standard_conditions"]["temperature_K"], pp["standard_conditions"]["pressure_bara"],
            pp["calibration"]["status"]["status"], PURPOSE), encoding="utf-8")
    return [_stage_table(defn, out_dir / "separator_stages.csv"), note]


EXPORTERS: Dict[str, Callable[[Dict[str, Any], Path], List[Path]]] = {
    "neqsim": export_neqsim,
    "e300": export_e300,
    "pvtsim": export_pvtsim,
    "fluidmagic": export_pvtsim,
    "blackoil": export_blackoil,
    "olga": export_blackoil,
}


def run_exports(defn: Dict[str, Any], targets: List[str], out_dir: Path) -> List[Path]:
    """Run the requested exporters into ``out_dir``."""
    out_dir.mkdir(parents=True, exist_ok=True)
    written: List[Path] = []
    for t in targets:
        if t not in EXPORTERS:
            raise ValueError("unknown export target %r (known: %s)" % (t, ", ".join(sorted(EXPORTERS))))
        written.extend(EXPORTERS[t](defn, out_dir))
    return written
