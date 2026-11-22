"""Run a source-locked methane shock-tube ignition-delay comparison.

The calculation uses the exact catalogued inlet, post-shock temperature and
pressure in an adiabatic constant-volume Cantera reactor. Predicted ignition is
the maximum pressure-rise rate, matching the experimental record. It reports
signed and normalized residuals without fitting mechanism parameters or direct
emission factors.
"""

import argparse
import json
import math
from pathlib import Path

from benchmark_qualification import QualificationInputError, load_catalog, verify_mechanism


DEFAULT_BENCHMARK_ID = "aul-et-al-2013-methane-shock-tube-mixture-4"
DEFAULT_MECHANISM_ID = "creck-s-2.0.0-zenodo-22982859"
COARSE_SAMPLE_COUNT = 1000
FINE_SAMPLE_COUNT = 2000
HORIZON_MULTIPLIER = 5.0
MAX_SAMPLING_DIFFERENCE_FRACTION = 0.01


def _catalog_item(catalog, collection, item_id):
    for item in catalog[collection]:
        if item["id"] == item_id:
            return item
    raise QualificationInputError(f"unknown {collection[:-1]} id: {item_id}")


def _cantera_and_numpy():
    try:
        import cantera
        import numpy
    except ImportError as error:
        raise QualificationInputError(
            "Cantera 3.2 and its NumPy dependency are required for the ignition comparison"
        ) from error
    return cantera, numpy


def _pressure_ignition_delay_us(
    cantera,
    numpy,
    gas,
    observation,
    inlet_mole_fractions,
    sample_count,
):
    required_species = set(inlet_mole_fractions)
    missing = sorted(required_species.difference(gas.species_names))
    if missing:
        raise QualificationInputError(f"mechanism omits exact benchmark species: {missing}")

    gas.TPX = (
        observation["temperatureK"],
        observation["pressurePa"],
        inlet_mole_fractions,
    )
    reactor = cantera.IdealGasReactor(
        gas,
        energy="on",
        volume=1.0,
        clone=True,
    )
    network = cantera.ReactorNet([reactor])
    observed_delay_s = observation["values"]["ignitionDelay"] * 1.0e-6
    end_time_s = HORIZON_MULTIPLIER * observed_delay_s
    times_s = numpy.linspace(0.0, end_time_s, sample_count + 1)
    pressures_pa = numpy.empty_like(times_s)
    pressures_pa[0] = reactor.phase.P
    for index, time_s in enumerate(times_s[1:], start=1):
        network.advance(float(time_s))
        pressures_pa[index] = reactor.phase.P

    pressure_rate = numpy.gradient(pressures_pa, times_s, edge_order=2)
    peak_index = int(numpy.argmax(pressure_rate))
    if peak_index == 0 or peak_index == sample_count:
        raise QualificationInputError(
            "maximum pressure-rise rate is on the integration boundary; "
            f"temperature={observation['temperatureK']} K"
        )
    return {
        "ignitionDelayUs": float(times_s[peak_index] * 1.0e6),
        "peakPressureRatePaPerS": float(pressure_rate[peak_index]),
        "sampleCount": sample_count,
        "endTimeUs": end_time_s * 1.0e6,
    }


def _simulate_observation(cantera, numpy, gas, benchmark, observation):
    inlet = benchmark["conditions"]["inletMoleFractions"]
    coarse = _pressure_ignition_delay_us(
        cantera,
        numpy,
        gas,
        observation,
        inlet,
        COARSE_SAMPLE_COUNT,
    )
    fine = _pressure_ignition_delay_us(
        cantera,
        numpy,
        gas,
        observation,
        inlet,
        FINE_SAMPLE_COUNT,
    )
    observed = observation["values"]["ignitionDelay"]
    uncertainty = observation["uncertainties"]["ignitionDelay"]
    predicted = fine["ignitionDelayUs"]
    sampling_difference = predicted - coarse["ignitionDelayUs"]
    sampling_difference_fraction = abs(sampling_difference) / observed
    if sampling_difference_fraction > MAX_SAMPLING_DIFFERENCE_FRACTION:
        raise QualificationInputError(
            "ignition-delay sampling refinement exceeds the declared limit: "
            f"{sampling_difference_fraction} > {MAX_SAMPLING_DIFFERENCE_FRACTION}"
        )
    residual = predicted - observed
    return {
        "temperatureK": observation["temperatureK"],
        "pressurePa": observation["pressurePa"],
        "equivalenceRatio": observation["equivalenceRatio"],
        "predictedIgnitionDelayUs": predicted,
        "observedIgnitionDelayUs": observed,
        "evaluatedStandardDeviationUs": uncertainty,
        "residualUs": residual,
        "normalizedResidual": residual / uncertainty,
        "peakPressureRatePaPerS": fine["peakPressureRatePaPerS"],
        "integrationEndTimeUs": fine["endTimeUs"],
        "coarseSampleCount": coarse["sampleCount"],
        "fineSampleCount": fine["sampleCount"],
        "samplingDifferenceUs": sampling_difference,
        "samplingDifferenceFractionOfObserved": sampling_difference_fraction,
    }


def summarize_results(results):
    """Calculate residual and sampling-refinement statistics."""
    residuals = [row["residualUs"] for row in results]
    normalized = [row["normalizedResidual"] for row in results]
    refinement = [row["samplingDifferenceUs"] for row in results]
    return {
        "pointCount": len(results),
        "rmseUs": math.sqrt(sum(value * value for value in residuals) / len(residuals)),
        "rmsNormalizedResidual": math.sqrt(
            sum(value * value for value in normalized) / len(normalized)
        ),
        "maxAbsoluteNormalizedResidual": max(abs(value) for value in normalized),
        "maxAbsoluteSamplingDifferenceUs": max(abs(value) for value in refinement),
        "maxSamplingDifferenceFractionOfObserved": max(
            row["samplingDifferenceFractionOfObserved"] for row in results
        ),
    }


def run_qualification(catalog_path, mechanism_path, benchmark_id, mechanism_id):
    """Execute the catalogued methane ignition-delay comparison."""
    catalog = load_catalog(catalog_path)
    benchmark = _catalog_item(catalog, "benchmarks", benchmark_id)
    mechanism = _catalog_item(catalog, "mechanisms", mechanism_id)
    if benchmark["dataAvailability"] != "quantitative":
        raise QualificationInputError(f"{benchmark_id} has no quantitative observations")
    if benchmark["apparatus"] != "SHOCK_TUBE":
        raise QualificationInputError(f"{benchmark_id} is not a shock-tube benchmark")
    verify_mechanism(mechanism, mechanism_path, for_qualification=True)
    cantera, numpy = _cantera_and_numpy()
    if cantera.__version__ != "3.2.0":
        raise QualificationInputError(
            f"Cantera 3.2.0 is required for reproducibility, found {cantera.__version__}"
        )
    gas = cantera.Solution(str(mechanism_path))
    results = [
        _simulate_observation(cantera, numpy, gas, benchmark, observation)
        for observation in benchmark["observations"]
    ]
    return {
        "benchmarkId": benchmark_id,
        "mechanismId": mechanism_id,
        "mechanismSha256": mechanism["expectedSha256"],
        "canteraVersion": cantera.__version__,
        "model": {
            "reactor": "adiabatic ideal-gas constant-volume batch reactor",
            "ignitionDefinition": benchmark["ignitionDefinition"],
            "integrationHorizon": f"{HORIZON_MULTIPLIER} times the observed delay",
            "samplingRefinement": (
                f"{COARSE_SAMPLE_COUNT} versus {FINE_SAMPLE_COUNT} uniform intervals; "
                f"maximum allowed difference {MAX_SAMPLING_DIFFERENCE_FRACTION:.1%} "
                "of the observed delay"
            ),
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
    if threshold is not None and record["summary"]["maxAbsoluteNormalizedResidual"] > threshold:
        raise SystemExit(
            "maximum normalized residual exceeds declared threshold: "
            f"{record['summary']['maxAbsoluteNormalizedResidual']} > {threshold}"
        )


if __name__ == "__main__":
    main()
