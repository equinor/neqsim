"""Compare image-verified thesis and retained compiler nitrogen/water data.

Preserve both versions, their row order and distinct uncertainty meanings. Two
explicit temperature-discrepancy mappings are diagnostic only: no nearest-state
join, replacement observation, fitted feed or physical acceptance is inferred.
"""

import argparse
import csv
from decimal import Decimal, InvalidOperation
import hashlib
import io
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / (
    "src/test/resources/neqsim/thermo/mixingrule/hv-water-nitrogen")
SHA256 = {
    "water-in-nitrogen": "1bd37fa55ba6999e838017a75599a52d5fc0ec41ab05ab9db4d17e773158ad88",
    "nitrogen-in-water": "d1b60914f01b77b5cf6b0359de2e5652f82e1dfb971c79920f16e87a3de6948d",
}
COMPILER_GIT = {
    "water-in-nitrogen": "d93283d10b92b52f321c9d4cf03273902964dc74",
    "nitrogen-in-water": "8c13577dcb86338acab7010c67b8caa1bf88564c",
}
HEADERS = {
    "water-in-nitrogen": ["T_K", "P_MPa", "y_exp_times_1e4"],
    "nitrogen-in-water": ["T_K", "P_MPa", "x_exp_times_1e3", "AD_exp_percent_as_printed"],
}
COMPILER_HEADER = ["T_K", "P_kPa", "mole_fraction", "compiler_U95_absolute"]
# Exact recorded version differences, not a temperature matching tolerance.
TEMPERATURE_VERSIONS = {
    (Decimal("293.1"), Decimal("984")): Decimal("293.12"),
    (Decimal("363.00"), Decimal("6842")): Decimal("363.02"),
}


def require(condition, message):
    """Retain scientific gates when Python assertions are optimized out."""
    if not condition:
        raise ValueError(message)


def number(text):
    """Parse a finite decimal without adding binary rounding or source digits."""
    try:
        result = Decimal(text)
    except (InvalidOperation, TypeError) as error:
        raise ValueError("Invalid source number") from error
    require(result.is_finite(), "Nonfinite source number")
    return result


def rows(raw, header):
    """Validate exact columns, cardinality and finite numeric cells."""
    reader = csv.DictReader(io.StringIO(raw.decode("utf-8")))
    require(reader.fieldnames == header, "Wrong source direction, scale or units")
    result = list(reader)
    require(all(set(row) == set(header) and all(row[k] is not None for k in header)
                for row in result), "Incomplete or extra source cells")
    for row in result:
        for value in row.values():
            number(value)
    return result


def reconcile(thesis, compiler, direction):
    """Return all row-level differences, keeping both source representations."""
    require(direction in HEADERS, "Unknown solubility direction")
    original = rows(thesis, HEADERS[direction])
    retained = rows(compiler, COMPILER_HEADER)
    count = 35 if direction == "water-in-nitrogen" else 52
    require(len(original) == len(retained) == count, "Missing or duplicated observations")
    scale = Decimal("0.0001") if count == 35 else Decimal("0.001")
    index = {}
    for ordinal, row in enumerate(retained, 1):
        key = (number(row["T_K"]), number(row["P_kPa"]))
        require(key not in index, "Ambiguous compiler state")
        require(key[0] > 0 and key[1] > 0
                and 0 < number(row["mole_fraction"]) < 1
                and number(row["compiler_U95_absolute"]) > 0, "Invalid compiler measurement")
        index[key] = (ordinal, row)
    seen = set()
    comparisons = []
    for ordinal, row in enumerate(original, 1):
        temperature = number(row["T_K"])
        pressure = number(row["P_MPa"]) * 1000
        fraction = number(row[HEADERS[direction][2]]) * scale
        require(temperature > 0 and pressure > 0 and 0 < fraction < 1,
                "Invalid thesis measurement")
        key = (temperature, pressure)
        exact_state = key in index
        if not exact_state:
            require(direction == "nitrogen-in-water" and key in TEMPERATURE_VERSIONS,
                    "Unrecorded state discrepancy; no nearest-state fallback")
            key = (TEMPERATURE_VERSIONS[key], pressure)
        require(key in index and key not in seen, "Missing or reused compiler state")
        seen.add(key)
        compiler_ordinal, other = index[key]
        difference = number(other["mole_fraction"]) - fraction
        comparison = {
            "thesis_row_1_based": ordinal, "compiler_row_1_based": compiler_ordinal,
            "thesis_T_K": row["T_K"], "compiler_T_K": other["T_K"],
            "thesis_P_MPa": row["P_MPa"], "compiler_P_kPa": other["P_kPa"],
            "thesis_scaled_fraction": row[HEADERS[direction][2]],
            "thesis_mole_fraction": str(fraction),
            "compiler_mole_fraction": other["mole_fraction"],
            "compiler_U95_absolute": other["compiler_U95_absolute"],
            "compiler_minus_thesis_mole_fraction": str(difference),
            "exact_TP": exact_state, "exact_TPx": exact_state and difference == 0,
        }
        if direction == "nitrogen-in-water":
            require(number(row["AD_exp_percent_as_printed"]) >= 0, "Invalid printed AD_exp")
            comparison["AD_exp_percent_as_printed"] = row["AD_exp_percent_as_printed"]
        comparisons.append(comparison)
    require(len(seen) == count, "Incomplete version mapping")
    common = [r for r in comparisons if r["exact_TP"]]
    return {
        "rows": count, "exact_TP": len(common),
        "exact_TPx": sum(r["exact_TPx"] for r in comparisons),
        "changed_x_at_common_TP": sum(number(r["compiler_minus_thesis_mole_fraction"]) != 0
                                      for r in common),
        "max_absolute_fraction_difference": str(max(
            abs(number(r["compiler_minus_thesis_mole_fraction"])) for r in comparisons)),
        "temperature_discrepancy_thesis_rows": [r["thesis_row_1_based"]
                                               for r in comparisons if not r["exact_TP"]],
        "comparisons": comparisons,
    }


def validate_metadata(metadata):
    """Reject promotion, direction/scale changes and conflated uncertainty."""
    require(metadata["status"] == "source-version audit; physical qualification unresolved"
            and metadata["parameter_activation"] is False,
            "Changed qualification status")
    primary = metadata["primary"]
    require(primary["identifier"] == "pastel-00001202"
            and primary["pdf_bytes"] == 3845263 and primary["pdf_pages"] == 259
            and primary["pdf_sha256"] == (
                "0f9e770078beb574a3113c75c26f0b5ff0cfb7a4726da0f2d1e7771daae08b10"),
            "Changed primary source identity")
    for direction, table, page, component, doi in (
            ("water-in-nitrogen", "6.6", 162, "water", "10.1021/je049676q"),
            ("nitrogen-in-water", "6.16", 190, "nitrogen", "10.1021/je049869d")):
        entry = metadata["tables"][direction]
        require(entry["table"] == table and entry["pdf_page_1_based"] == page
                and entry["printed_page"] == page - 1
                and entry["columns"] == HEADERS[direction]
                and entry["measured_component"] == component
                and entry["journal_doi"] == doi and entry["sha256"] == SHA256[direction]
                and entry["fraction_scale"] == ("0.0001" if page == 162 else "0.001")
                and entry["rows"] == (35 if page == 162 else 52), "Changed table semantics")
    require(metadata["uncertainties"] == {
        "thesis_liquid_calibration": {"relative_percent": "2.5", "quantity": "moles of nitrogen",
                                      "confidence_level": None, "pdf_page_1_based": 189,
                                      "printed_page": 188},
        "thesis_liquid_AD_exp": "As printed; not assigned a confidence level or interpreted as point uncertainty",
        "compiler": "Data file compiler expanded 95% absolute mole-fraction uncertainty; unchanged",
        "gas_author_uncertainty": "Not established by this table audit"},
        "Changed uncertainty semantics")
    require(metadata["units"] == {
        "temperature": "K", "pressure": "absolute MPa; multiply by 1000 for compiler kPa",
        "composition": "mole fraction; retain printed scaled values"}, "Changed units")
    require(metadata["lineage"] == "Same-author experimental lineage, not independent holdout data; final-journal transcription history unresolved"
            and metadata["missing"] == ["final-journal table/version reconciliation",
                                       "complete original measurement uncertainty",
                                       "source-compatible PR pure-component and alpha inputs",
                                       "bidirectional physical and performance qualification"],
            "Changed lineage or qualification boundary")


def audit(directory=ROOT):
    """Check immutable source bytes, metadata and the complete retained comparison."""
    directory = Path(directory)
    thesis_directory = directory / "thesis-2004"
    validate_metadata(json.loads((thesis_directory / "provenance.json").read_text(encoding="utf-8")))
    result = {}
    for direction in HEADERS:
        thesis = (thesis_directory / (direction + ".csv")).read_bytes()
        compiler = (directory / (direction + ".csv")).read_bytes()
        require(hashlib.sha256(thesis).hexdigest() == SHA256[direction], "Changed thesis bytes")
        blob = hashlib.sha1(b"blob " + str(len(compiler)).encode() + b"\0" + compiler).hexdigest()
        require(blob == COMPILER_GIT[direction], "Changed compiler bytes")
        result[direction] = reconcile(thesis, compiler, direction)
    expected = json.loads((thesis_directory / "comparison.json").read_text(encoding="utf-8"))
    require(result == expected, "Changed retained comparison")
    return result


def main():
    """Print a complete read-only audit or its compact summary as JSON."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, default=ROOT)
    parser.add_argument("--summary", action="store_true")
    args = parser.parse_args()
    result = audit(args.directory)
    if args.summary:
        result = {direction: {k: v for k, v in values.items() if k != "comparisons"}
                  for direction, values in result.items()}
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
