"""Audit primary MEG/water/natural-gas data without constructing an HV fit.

The loading volumes are not an independently measured overall molar feed.
Repeatability, incomplete observations and source inequalities are retained.
No simulator, network access or third-party Python package is required.
"""

import argparse
from collections import Counter
import csv
from decimal import Decimal
import hashlib
import io
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / (
    "src/test/resources/neqsim/thermo/mixingrule/hv-meg-natural-gas")
DOI = "10.1021/acs.jced.8b00495"
HASHES = {
    "gas-feed.csv": "e905800b6a972ca51546a82806d64d20beedc20fc56ba70d2e686de17948b1a8",
    "states.csv": "6e081046a47846ed8dde34afa9f86019dd76e6b4cc56b9bede4e47cc6f80a906",
    "observations.csv": "3ffe5ec33457434103fb36d30d8ca623e269ac5e7d884cdca3d49e1b2e772f2a",
}
COMPONENTS = ("MEG", "water", "CO2", "nitrogen", "methane", "ethane", "propane",
              "n-butane", "i-butane", "n-pentane", "i-pentane")
SOURCE_CONFLICTS = {
    "table3_MEG_purity_mole_percent": "99.8",
    "table3_MEG_water_content_percent_upper_bound": "<0.003",
    "table3_water_content_basis": "not stated",
    "table5_MEG_feed_mass_fraction_experiments10_11": ">0.998",
    "printed23_PDF24_model_assumption_species_as_printed": "H2O",
    "printed23_PDF24_model_assumption_mole_percent": "99.8",
    "resolution": "unresolved; do not substitute MEG for the printed H2O or infer exact residual water",
}


def require(condition, message):
    """Enforce a source boundary under normal and optimized interpreters."""
    if not condition:
        raise ValueError(message)


def number(value):
    """Read a finite source decimal without guessing missing observations."""
    result = Decimal(value)
    require(result.is_finite(), "Nonfinite source number")
    return result


def read_rows(raw):
    """Decode literal CSV cells, rejecting extra or missing columns."""
    rows = list(csv.DictReader(io.StringIO(raw.decode("utf-8"))))
    require(rows and all(None not in r and None not in r.values() for r in rows),
            "Missing or extra CSV cell")
    return rows


def project_observations(rows):
    """Check direction/units and exactly convert ppm; never complete a phase."""
    expected = [(str(e), str(t), c) for t, components in
                ((9, COMPONENTS), (10, COMPONENTS[2:]))
                for e in range(1, 12) for c in components]
    require([(r["experiment"], r["source_table"], r["component"]) for r in rows] == expected,
            "Missing, duplicated, reordered or relabeled observation")
    result = []
    for row in rows:
        table, experiment, component = row["source_table"], row["experiment"], row["component"]
        quantity = ("y" if component in COMPONENTS[:2] else "x") if table == "9" else "y/x"
        unit = "ppm_mole_fraction" if table == "9" else "dimensionless"
        require(row["quantity"] == quantity and row["unit"] == unit,
                "Changed phase direction or units")
        absent = table == "9" and component == "MEG" and experiment in ("6", "9")
        value, rsd = row["value_as_printed"], row["relative_standard_deviation_percent"]
        require((value == "-") == absent and (rsd == "-") == absent,
                "Missing MEG observation replaced or invented")
        footnote = "b" if table == "9" and component == "water" and experiment in ("10", "11") else "-"
        require(row["uncertainty_footnote"] == footnote, "Lost source exclusion footnote")
        if absent:
            continue
        value, rsd = number(value), number(rsd)
        require(value > 0 and rsd >= 0, "Invalid observation or repeatability")
        projected = value / 1000000 if table == "9" else value
        require(table != "9" or projected < 1, "Invalid mole fraction")
        result.append({"experiment": int(experiment), "component": component,
                       "quantity": quantity, "value": projected,
                       "relative_standard_deviation_percent": rsd,
                       "uncertainty_footnote": footnote})
    return result


def check_states(rows):
    """Keep measured loading states separate from the nominal result labels."""
    require([r["experiment"] for r in rows] == [str(i) for i in range(1, 12)],
            "Missing or reordered experimental state")
    for row in rows:
        e = int(row["experiment"])
        require(row["wMEG_feed_table5_g_g"] == ("0.9" if e < 10 else ">0.998"),
                "Changed mass basis or replaced purity inequality")
        for field in ("T_table5_K", "P_table5_MPa", "Vliquid_table5_mL", "Vgas_table5_mL",
                      "T_tables9_10_K", "P_tables9_10_MPa"):
            require(number(row[field]) > 0, "Nonpositive loading state")
    return {
        "temperature_range_K": [str(min(number(r["T_table5_K"]) for r in rows)),
                                str(max(number(r["T_table5_K"]) for r in rows))],
        "pressure_range_MPa": [str(min(number(r["P_table5_MPa"]) for r in rows)),
                               str(max(number(r["P_table5_MPa"]) for r in rows))],
        "nominal_temperature_differs_from_loading_state": sum(
            number(r["T_table5_K"]) != number(r["T_tables9_10_K"]) for r in rows),
        "nominal_pressure_differs_from_loading_state": sum(
            number(r["P_table5_MPa"]) != number(r["P_tables9_10_MPa"]) for r in rows),
        "purity_inequality_experiments": [10, 11],
    }


def check_gas(rows):
    """Retain all loading-gas components, source totals and a printed alias conflict."""
    require([r["component_number"] for r in rows] == [str(i) for i in range(3, 21)],
            "Incomplete loading-gas composition")
    require(rows[10]["subscript_as_printed"] == rows[12]["subscript_as_printed"] == "22DMC4"
            and rows[10]["CAS"] == "75-83-2" and rows[12]["CAS"] == "79-29-8",
            "Source alias conflict silently repaired or species conflated")
    require([r["CAS"] for r in rows[-2:]] == ["-", "-"]
            and [r["name"] for r in rows[-2:]] == ["heptanes total", "octanes total"],
            "Lumped source totals assigned pure-component identities")
    values = [number(r["mole_percent"]) for r in rows]
    require(all(0 < v < 100 for v in values), "Invalid loading-gas composition")
    return str(sum(values))


def audit(directory=ROOT):
    """Verify immutable source transcriptions and explicit qualification limits."""
    directory = Path(directory)
    metadata = json.loads((directory / "provenance.json").read_text(encoding="utf-8"))
    data = {}
    for name, expected in HASHES.items():
        raw = (directory / name).read_bytes()
        require(hashlib.sha256(raw).hexdigest() == metadata["files"][name] == expected,
                "Changed primary transcription: " + name)
        data[name] = read_rows(raw)
    require(metadata["doi"] == DOI
            and metadata["status"] == "quarantined source data; no HV activation"
            and metadata["source_model"] == "CPA; coefficients not transferred"
            and metadata["parameter_activation"] is False
            and metadata["measured_overall_molar_feed"] is False
            and metadata["source_averaging"] ==
            "Each reported result is the mean of two experiments; individual replicates not supplied"
            and metadata["correlation"] ==
            "Concentrations and partition coefficients from the same experiments are not independent samples; covariance not supplied",
            "Source receipts promoted to HV parameters or measured molar feed")
    require(metadata["uncertainty"]["composition"] ==
            "author relative standard deviation percent; one sigma repeatability; not full combined uncertainty"
            and metadata["uncertainty"]["temperature_standard_K"] == "0.05"
            and metadata["uncertainty"]["pressure_standard_relative"] == "0.002"
            and metadata["uncertainty"]["missing_vapor_MEG_experiments"] == [6, 9]
            and len(metadata["feed_limits"]) == 5 and len(metadata["missing"]) == 5,
            "Changed uncertainty semantics or erased evidence blockers")
    apparatus = metadata["apparatus_table2"]
    require(metadata["source_conflicts"] == SOURCE_CONFLICTS
            and apparatus["relative_percent"] ==
            {"vapor_water": "3", "vapor_MEG": "12", "liquid_water": "2"}
            and apparatus["flow_calibration_deviation_percent"] == "0.09"
            and apparatus["mass_uncertainty_g"] == "0.001"
            and apparatus["density_uncertainty_value_as_printed"] == "0.00007"
            and apparatus["density_uncertainty_unit_as_printed"] == "g mol^-1",
            "Silently resolved purity conflict or erased apparatus uncertainty")
    source = metadata["source_documents"]
    require(source["manuscript"]["sha256"] ==
            "06fb84d7b24cfc92ce79b2bf74b336c4f465c8932bcdf5370510bcf94f9eb0f6"
            and source["supplement"]["sha256"] ==
            "3aea40a41b06b5abaaa4e24840e9b784e185c2a46cce8ab0d3ce546bb5e75e3b",
            "Changed primary source identity")
    projected = project_observations(data["observations.csv"])
    counts = Counter(r["quantity"] for r in projected)
    zeros = [(r["experiment"], r["quantity"], r["component"]) for r in projected
             if r["relative_standard_deviation_percent"] == 0]
    require(zeros == [(3, "y", "water"), (4, "x", "i-butane"), (5, "y", "water")],
            "Rounded zero repeatability changed or treated as a missing observation")
    return {"doi": DOI, "states": len(data["states.csv"]),
            "source_observation_slots": len(data["observations.csv"]),
            "retained_observations": len(projected), "quantities": dict(counts),
            "missing_vapor_MEG_experiments": [6, 9], "printed_zero_RSD_cells": zeros,
            "loading_gas_components": len(data["gas-feed.csv"]),
            "loading_gas_mole_percent_sum_as_printed": check_gas(data["gas-feed.csv"]),
            "states_and_rounding": check_states(data["states.csv"]),
            "qualification": metadata["status"],
            "source_averaging": metadata["source_averaging"],
            "correlation": metadata["correlation"],
            "source_conflicts": metadata["source_conflicts"],
            "apparatus_table2": apparatus,
            "limits": metadata["feed_limits"], "next_dependencies": metadata["missing"]}


def main():
    """Print the documented read-only audit, with no implicit file generation."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, default=ROOT)
    args = parser.parse_args()
    print(json.dumps(audit(args.directory), indent=2))


if __name__ == "__main__":
    main()
