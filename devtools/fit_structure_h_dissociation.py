#!/usr/bin/env python3
"""Qualify figure-derived structure-H data and fit system P(T) correlations.

The coefficients describe a methane-water-promoter system. They are not
component Langmuir or Kihara parameters and cannot be exported to COMP.
The default calculation is offline and uses only the Python standard library.
PyMuPDF is needed only for the optional audit against the source PDF.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
from typing import Sequence


ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "src/test/resources/data/chemistry_benchmarks/structure_h_jin2023_digitized.csv"
PROVENANCE = DATA.with_suffix(".json")
REFERENCE_TEMPERATURE_K = 280.0


def fit_coefficients(temperature: Sequence[float], pressure: Sequence[float]) -> tuple[float, float]:
    """Fit ln(P/MPa) = c + B (1/280 K - 1/T), without clipping inputs."""
    if len(temperature) != len(pressure) or len(temperature) < 2:
        raise ValueError("At least two paired temperature/pressure observations are required")
    if any(not math.isfinite(value) or value <= 0 for value in (*temperature, *pressure)):
        raise ValueError("Temperatures and reported pressures must be finite and positive")
    x = [1.0 / REFERENCE_TEMPERATURE_K - 1.0 / value for value in temperature]
    y = [math.log(value) for value in pressure]
    x_mean = math.fsum(x) / len(x)
    y_mean = math.fsum(y) / len(y)
    variance = math.fsum((value - x_mean) ** 2 for value in x)
    if variance == 0:
        raise ValueError("At least two distinct temperatures are required")
    slope = math.fsum((xx - x_mean) * (yy - y_mean) for xx, yy in zip(x, y)) / variance
    intercept = y_mean - slope * x_mean
    if not math.isfinite(intercept) or not math.isfinite(slope):
        raise ValueError("Nonfinite regression coefficients")
    return intercept, slope


def predict(coefficients: tuple[float, float], temperature: float) -> float:
    """Predict reported pressure in MPa within the fitted system's data interval."""
    if not math.isfinite(temperature) or temperature <= 0:
        raise ValueError("Temperature must be finite and positive")
    intercept, slope = coefficients
    return math.exp(intercept + slope * (1.0 / REFERENCE_TEMPERATURE_K - 1.0 / temperature))


def errors(predicted: Sequence[float], measured: Sequence[float]) -> dict[str, float]:
    """Return pressure errors against extracted markers, not measurement accuracy."""
    relative = [abs(100.0 * (estimate / actual - 1.0)) for estimate, actual in zip(predicted, measured)]
    return {"AARD_percent": math.fsum(relative) / len(relative), "maximum_relative_error_percent": max(relative)}


def load_dataset(data_path: Path = DATA, provenance_path: Path = PROVENANCE) -> tuple[list[dict], dict]:
    """Read and verify the frozen primary-source data and its provenance."""
    provenance = json.loads(provenance_path.read_text(encoding="utf-8"))
    if hashlib.sha256(data_path.read_bytes()).hexdigest() != provenance["derived_csv_sha256"]:
        raise ValueError("Dataset checksum differs from its reviewed provenance")
    with data_path.open(encoding="utf-8", newline="") as stream:
        raw_rows = list(csv.DictReader(stream))
    rows = []
    for row in raw_rows:
        if row["system_id"] not in provenance["systems"]:
            raise ValueError("Unknown experimental system")
        values = {key: float(row[key]) for key in (
            "temperature_K", "reported_pressure_MPa", "marker_x_pdf_pt", "marker_y_pdf_pt"
        )}
        if any(not math.isfinite(value) for value in values.values()):
            raise ValueError("Nonfinite experimental data")
        if values["temperature_K"] <= 0 or values["reported_pressure_MPa"] <= 0:
            raise ValueError("Nonpositive temperature or reported pressure")
        rows.append({"system_id": row["system_id"], **values})
    for system_id, system in provenance["systems"].items():
        selected = [row for row in rows if row["system_id"] == system_id]
        if len(selected) != system["number_of_points"]:
            raise ValueError("Experimental marker count differs from provenance: " + system_id)
        if len({row["temperature_K"] for row in selected}) != len(selected):
            raise ValueError("Duplicate temperature within experimental system: " + system_id)
    return rows, provenance


def audit_source_pdf(pdf_path: Path, rows: list[dict], provenance: dict) -> None:
    """Re-extract the colored markers and reject any source or coordinate mismatch."""
    if hashlib.sha256(pdf_path.read_bytes()).hexdigest() != provenance["source_pdf_sha256"]:
        raise ValueError("Source PDF checksum differs from the reviewed source")
    import fitz

    colors = provenance["vector_extraction"]["marker_colors_rgb"]
    axes = provenance["vector_extraction"]["axes_pdf_pt"]
    found = []
    with fitz.open(pdf_path) as document:
        page = document[provenance["source_page_one_based"] - 1]
        vertical, horizontal = [], []
        drawings = page.get_drawings()
        for drawing in drawings:
            rectangle = drawing["rect"]
            if drawing["color"] != (0.0, 0.0, 0.0):
                continue
            if rectangle.height > 200 and rectangle.width < 0.01:
                vertical.append(rectangle.x0)
            if rectangle.width > 200 and rectangle.height < 0.01:
                horizontal.append(rectangle.y0)
        actual_axes = sorted(vertical) + sorted(horizontal)
        expected_axes = [axes["left"], axes["right"], axes["top"], axes["bottom"]]
        if len(actual_axes) != 4 or any(abs(a - b) > 1e-6 for a, b in zip(actual_axes, expected_axes)):
            raise ValueError("Source figure axes differ from the reviewed calibration")
        for drawing in drawings:
            rectangle = drawing["rect"]
            if drawing["type"] != "fs" or drawing["fill"] is None:
                continue
            if not (3.9 < rectangle.width < 4.2 and 3.9 < rectangle.height < 4.2):
                continue
            system_id = next((key for key, color in colors.items()
                              if all(abs(a - b) < 1e-6 for a, b in zip(drawing["fill"], color))), None)
            if system_id is None:
                continue
            x = (rectangle.x0 + rectangle.x1) / 2
            y = (rectangle.y0 + rectangle.y1) / 2
            if not (axes["left"] < x < axes["right"] and axes["top"] < y < axes["bottom"]):
                continue
            if 220 < x < 290 and 270 < y < 330:
                continue  # Legend circles, not experimental observations.
            inverse_temperature = 0.00345 + (x - axes["left"]) / (axes["right"] - axes["left"]) * 0.00020
            pressure = 10 ** ((axes["bottom"] - y) / (axes["bottom"] - axes["top"]))
            found.append((system_id, 1.0 / inverse_temperature, pressure, x, y))
    expected = [(row["system_id"], row["temperature_K"], row["reported_pressure_MPa"],
                 row["marker_x_pdf_pt"], row["marker_y_pdf_pt"]) for row in rows]
    if len(found) != len(expected):
        raise ValueError("Source figure marker count differs from the frozen dataset")
    for actual, frozen in zip(sorted(found), sorted(expected)):
        if actual[0] != frozen[0] or any(abs(a - b) > 1e-9 for a, b in zip(actual[1:], frozen[1:])):
            raise ValueError("Source figure marker differs from the frozen dataset")


def analyze(rows: list[dict], provenance: dict) -> dict:
    """Fit each system and perform fixed internal holdout and leave-one-out checks."""
    systems = {}
    for system_id, basis in provenance["systems"].items():
        group = sorted((row for row in rows if row["system_id"] == system_id), key=lambda row: row["temperature_K"])
        if len(group) < 5:
            raise ValueError("At least five observations are required for internal validation")
        temperature = [row["temperature_K"] for row in group]
        pressure = [row["reported_pressure_MPa"] for row in group]
        full_fit = fit_coefficients(temperature, pressure)
        holdout = [1, len(group) - 2]
        training = [i for i in range(len(group)) if i not in holdout]
        training_fit = fit_coefficients([temperature[i] for i in training], [pressure[i] for i in training])
        loo_predictions = []
        for i, value in enumerate(temperature):
            selected = [j for j in range(len(group)) if j != i]
            fit = fit_coefficients([temperature[j] for j in selected], [pressure[j] for j in selected])
            loo_predictions.append(predict(fit, value))
        intercept, slope = full_fit
        if slope <= 0:
            raise ValueError("The fitted dissociation pressure must increase with temperature: " + system_id)
        systems[system_id] = {
            "large_guest": basis["large_guest"],
            "number_of_points": len(group),
            "temperature_range_K": [min(temperature), max(temperature)],
            "reported_pressure_range_MPa": [min(pressure), max(pressure)],
            "A_dimensionless": intercept + slope / REFERENCE_TEMPERATURE_K,
            "B_K": slope,
            "all_points": errors([predict(full_fit, value) for value in temperature], pressure),
            "internal_holdout": {
                "training_count": len(training), "holdout_count": len(holdout),
                "holdout_temperatures_K": [temperature[i] for i in holdout],
                **errors([predict(training_fit, temperature[i]) for i in holdout], [pressure[i] for i in holdout]),
            },
            "leave_one_out": errors(loo_predictions, pressure),
        }
    return {
        "source_doi": provenance["source_doi"],
        "source_pdf_sha256": provenance["source_pdf_sha256"],
        "derived_csv_sha256": provenance["derived_csv_sha256"],
        "coefficient_type": "METHANE_WATER_PROMOTER_SYSTEM_CORRELATION",
        "equation": "ln(P_reported/MPa) = A_dimensionless - B_K/T_K",
        "weighting": "Unweighted least squares in log pressure; measurement uncertainties unavailable",
        "qualification": {
            "accepted_for_COMP": False,
            "experimental_pressure_basis": provenance["reported_pressure_basis"],
            "measurement_uncertainty": provenance["measurement_uncertainty"],
            "independent_study_validation": False,
            "physical_component_fit": "BLOCKED: complete sH host/helper basis and fluid fugacity inputs unavailable",
            "reasons": [
                "Fitted coefficients describe whole systems, not component/cavity interactions",
                "Source markers are figure-derived, not author-supplied numerical tables",
                "Source supplement does not establish all fluid-phase compositions and fugacities",
                "No sH component parameters, phase selection, or amount-flash capability is qualified",
            ],
        },
        "systems": systems,
    }


def main() -> None:
    """Write the offline benchmark fit and optional primary-PDF audit result."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, help="Write JSON here instead of stdout")
    parser.add_argument("--source-pdf", type=Path, help="Audit the reviewed source PDF using PyMuPDF")
    args = parser.parse_args()
    rows, provenance = load_dataset()
    if args.source_pdf is not None:
        audit_source_pdf(args.source_pdf, rows, provenance)
    result = analyze(rows, provenance)
    result["source_pdf_audit"] = "PASSED" if args.source_pdf is not None else "NOT_RUN"
    serialized = json.dumps(result, indent=2, allow_nan=False) + "\n"
    if args.output is None:
        print(serialized, end="")
    else:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(serialized, encoding="utf-8")


if __name__ == "__main__":
    main()
