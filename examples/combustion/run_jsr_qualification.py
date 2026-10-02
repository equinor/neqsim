"""Run a source-locked isothermal JSR comparison with Cantera.

The calculation preserves the catalogued mechanism bytes, inlet composition,
residence time, pressure, measurement basis and uncertainty values. It reports
normalized residuals but does not promote this C2 reactor comparison to heater,
C3/C4, emissions-instrument or plant qualification.
"""

import argparse
import json
import math
from pathlib import Path

from benchmark_qualification import QualificationInputError, load_catalog, verify_mechanism


DEFAULT_BENCHMARK_ID = "cong-bedjanian-dagaut-2010-ethylene-jsr-phi-0.5"
DEFAULT_MECHANISM_ID = "creck-s-2.0.0-zenodo-22982859"
MAX_RESIDENCE_CYCLES = 200
MIN_RESIDENCE_CYCLES = 10
STEADY_MOLE_FRACTION_DELTA = 1.0e-10


def _catalog_item(catalog, collection, item_id):
    for item in catalog[collection]:
        if item["id"] == item_id:
            return item
    raise QualificationInputError(f"unknown {collection[:-1]} id: {item_id}")


def _cantera():
    try:
        import cantera
    except ImportError as error:
        raise QualificationInputError("Cantera 3.2 is required for the JSR comparison") from error
    return cantera


def _simulate_observation(cantera, mechanism_path, benchmark, observation):
    conditions = benchmark["conditions"]
    gas = cantera.Solution(str(mechanism_path))
    required_species = set(conditions["inletMoleFractions"])
    required_species.update(observation["values"])
    missing = sorted(required_species.difference(gas.species_names))
    if missing:
        raise QualificationInputError(f"mechanism omits exact benchmark species: {missing}")

    gas.TPX = (
        observation["temperatureK"],
        conditions["pressurePa"],
        conditions["inletMoleFractions"],
    )
    inlet = cantera.Reservoir(gas, clone=True)
    reactor = cantera.IdealGasConstPressureReactor(
        gas,
        energy="off",
        volume=conditions["reactorVolumeM3"],
        clone=True,
    )
    outlet = cantera.Reservoir(gas, clone=True)
    residence_time = conditions["residenceTimeS"]
    mass_flow = cantera.MassFlowController(
        inlet,
        reactor,
        mdot=reactor.mass / residence_time,
    )
    pressure_controller = cantera.PressureController(
        reactor,
        outlet,
        primary=mass_flow,
        K=1.0e-5,
    )
    # Retain flow devices for the duration of integration.
    _ = pressure_controller
    network = cantera.ReactorNet([reactor])
    previous = reactor.phase.X.copy()
    converged = False
    delta = None
    for cycle in range(1, MAX_RESIDENCE_CYCLES + 1):
        network.advance(cycle * residence_time)
        current = reactor.phase.X.copy()
        delta = float(max(abs(current - previous)))
        previous = current
        if cycle >= MIN_RESIDENCE_CYCLES and delta < STEADY_MOLE_FRACTION_DELTA:
            converged = True
            break
    if not converged:
        raise QualificationInputError(
            f"JSR did not converge after {MAX_RESIDENCE_CYCLES} residence cycles; delta={delta}"
        )

    predictions = {
        species: float(reactor.phase[species].X[0] * 1.0e6)
        for species in observation["values"]
    }
    residuals = {
        species: predictions[species] - observation["values"][species]
        for species in predictions
    }
    normalized = {
        species: residuals[species] / observation["uncertainties"][species]
        for species in predictions
    }
    return {
        "temperatureK": observation["temperatureK"],
        "equivalenceRatio": observation["equivalenceRatio"],
        "predictedPpmv": predictions,
        "observedPpmv": observation["values"],
        "evaluatedStandardDeviationPpmv": observation["uncertainties"],
        "residualPpmv": residuals,
        "normalizedResidual": normalized,
        "residenceCycles": cycle,
        "finalMoleFractionDelta": delta,
        "actualResidenceTimeS": float(reactor.mass / mass_flow.mass_flow_rate),
    }


def summarize_results(results):
    """Calculate species and overall residual statistics from JSR result rows."""
    species = sorted(results[0]["normalizedResidual"])
    per_species = {}
    all_normalized = []
    for name in species:
        normalized = [row["normalizedResidual"][name] for row in results]
        residuals = [row["residualPpmv"][name] for row in results]
        all_normalized.extend(normalized)
        per_species[name] = {
            "rmsePpmv": math.sqrt(sum(value * value for value in residuals) / len(residuals)),
            "rmsNormalizedResidual": math.sqrt(
                sum(value * value for value in normalized) / len(normalized)
            ),
            "maxAbsoluteNormalizedResidual": max(abs(value) for value in normalized),
        }
    return {
        "pointCount": len(results),
        "species": per_species,
        "overallMaxAbsoluteNormalizedResidual": max(abs(value) for value in all_normalized),
    }


def run_qualification(catalog_path, mechanism_path, benchmark_id, mechanism_id):
    """Execute the catalogued C2 JSR comparison and return a JSON-ready record."""
    catalog = load_catalog(catalog_path)
    benchmark = _catalog_item(catalog, "benchmarks", benchmark_id)
    mechanism = _catalog_item(catalog, "mechanisms", mechanism_id)
    if benchmark["dataAvailability"] != "quantitative":
        raise QualificationInputError(f"{benchmark_id} has no quantitative observations")
    verify_mechanism(mechanism, mechanism_path, for_qualification=True)
    cantera = _cantera()
    if cantera.__version__ != "3.2.0":
        raise QualificationInputError(
            f"Cantera 3.2.0 is required for reproducibility, found {cantera.__version__}"
        )
    results = [
        _simulate_observation(cantera, mechanism_path, benchmark, observation)
        for observation in benchmark["observations"]
    ]
    return {
        "benchmarkId": benchmark_id,
        "mechanismId": mechanism_id,
        "mechanismSha256": mechanism["expectedSha256"],
        "canteraVersion": cantera.__version__,
        "model": {
            "reactor": "isothermal ideal-gas constant-pressure perfectly stirred reactor",
            "energy": "disabled at each measured reactor temperature",
            "residenceTime": "fixed inlet-state reactor mass divided by mass flow",
            "measurementBasis": benchmark["measurementBasis"],
        },
        "results": results,
        "summary": summarize_results(results),
        "scope": benchmark["limitation"],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mechanism", type=Path)
    parser.add_argument(
        "--catalog",
        type=Path,
        default=Path(__file__).with_name("benchmark_catalog.json"),
    )
    parser.add_argument("--benchmark-id", default=DEFAULT_BENCHMARK_ID)
    parser.add_argument("--mechanism-id", default=DEFAULT_MECHANISM_ID)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--max-absolute-normalized-residual", type=float)
    arguments = parser.parse_args()
    record = run_qualification(
        arguments.catalog,
        arguments.mechanism,
        arguments.benchmark_id,
        arguments.mechanism_id,
    )
    rendered = json.dumps(record, indent=2, sort_keys=True)
    if arguments.output:
        arguments.output.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)
    threshold = arguments.max_absolute_normalized_residual
    if threshold is not None and record["summary"]["overallMaxAbsoluteNormalizedResidual"] > threshold:
        raise SystemExit(
            "overall normalized residual exceeds declared threshold: "
            f"{record['summary']['overallMaxAbsoluteNormalizedResidual']} > {threshold}"
        )


if __name__ == "__main__":
    main()
