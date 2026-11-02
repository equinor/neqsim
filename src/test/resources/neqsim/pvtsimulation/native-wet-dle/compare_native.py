"""Compare complete native exports to independently predicted wet DLE states.

Frozen bounds: relative volume and absolute phase composition/amount 1e-8,
component closure 1e-10 mol and separator closure 1e-8 mol, one-mole sample.
No assertions that disappear under optimized Python; no fitting or clipping.
"""
import json
import math
from pathlib import Path

root = Path(__file__).parent
references = json.loads((root / "reference.json").read_text())
native = json.loads((root / "native-states.json").read_text())


def finite_tree(value):
    """Reject non-finite data before extrema can conceal NaN values."""
    if isinstance(value, dict):
        for child in value.values():
            finite_tree(child)
    elif isinstance(value, list):
        for child in value:
            finite_tree(child)
    elif isinstance(value, (int, float)) and not math.isfinite(value):
        raise RuntimeError("Non-finite numerical evidence")


finite_tree(references)
finite_tree(native)
metrics = dict(volume_relative=0., composition_absolute=0., amount_absolute=0.,
               component_closure_mol=0., separator_closure_mol=0.)
cumulative = [0., 0., 0.]
if len(references) != 3 or len(native) != 3:
    raise RuntimeError("Incomplete declared experiment")
for reference, row in zip(references, native):
    if reference["pressureBara"] != row["pressureBara"]:
        raise RuntimeError("Misaligned pressure path")
    if sorted(phase["type"] for phase in row["equilibrium"]["phases"]) != ["aqueous", "gas", "oil"]:
        raise RuntimeError("Incorrect equilibrium phases")
    if any(len(phase["x"]) != 3 for phase in row["equilibrium"]["phases"]):
        raise RuntimeError("Incomplete phase composition")
    for kind, key in [("equilibrium", "equilibriumVolumeM3"), ("retained", "retainedVolumeM3")]:
        metrics["volume_relative"] = max(metrics["volume_relative"],
            abs(row[kind]["volumeM3"] / reference[key] - 1))
    for index, name in enumerate(["gas", "oil", "aqueous"]):
        actual = next(phase for phase in row["equilibrium"]["phases"] if phase["type"] == name)
        metrics["amount_absolute"] = max(metrics["amount_absolute"],
            abs(actual["moles"] - reference["phaseMoles"][index]))
        metrics["composition_absolute"] = max(metrics["composition_absolute"],
            max(abs(a-b) for a, b in zip(actual["x"], reference["phaseCompositions"][index])))
    for component, initial in enumerate([.6, .2, .2]):
        removed = row["withdrawnMoles"][component]
        retained = row["retained"]["componentMoles"][component]
        cumulative[component] += removed
        metrics["amount_absolute"] = max(metrics["amount_absolute"],
            abs(removed-reference["withdrawnMoles"][component]),
            abs(retained-reference["retainedMoles"][component]))
        metrics["component_closure_mol"] = max(metrics["component_closure_mol"],
            abs(initial-cumulative[component]-retained))
        for products, amount in [("retainedProductMoles", retained), ("withdrawnProductMoles", removed)]:
            metrics["separator_closure_mol"] = max(metrics["separator_closure_mol"],
                abs(sum(phase[component] for phase in row[products])-amount))
for name, value in metrics.items():
    bound = 1e-10 if name == "component_closure_mol" else 1e-8
    if not 0 <= value <= bound:
        raise RuntimeError(f"Unchanged {name} bound exceeded: {value}")
print(json.dumps(metrics, indent=2, sort_keys=True, allow_nan=False))
