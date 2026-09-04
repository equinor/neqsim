"""Compare NeqSim's COMP.csv against a UniSim reference table.

This script is read-only. It never edits COMP.csv. Its output is evidence for
a human to act on.

Rows are joined on CAS number, and only where that CAS number appears exactly
once in each file. A CAS number carried by several rows is ambiguous, and an
ambiguous match is skipped rather than resolved to the nearest candidate.

Two dispositions are reported separately, because they call for different
actions:

``correction candidate``
    MOLARMASS, LIQDENS and NORMBOIL. Errors in these columns in COMP.csv are
    known to include whole values copied from an unrelated component, which is
    a defect rather than a modelling choice.

``review only``
    TC, PC and ACSFACT. These are believed to be correct in COMP.csv and may
    be deliberately tuned. A deviation here is a lead to investigate against an
    independent source such as NIST, never an automatic correction.

Units are derived from the data rather than assumed. Scale-type columns are
resolved by the median ratio against a small set of candidate factors, and
temperature columns by testing whether COMP.csv agrees better with UniSim's
Celsius value or with that value converted to Kelvin. If neither resolves
cleanly the script aborts, because a wrong unit would make every deviation
meaningless.

Usage::

    python compare_comp_to_unisim.py --library devtools/unisim_library.csv
    python compare_comp_to_unisim.py --library devtools/unisim_library.csv \
        --out devtools/comp_vs_unisim.csv
"""

from __future__ import annotations

import argparse
import collections
import csv
import os
import sys

# COMP.csv column name -> UniSim CSV column name.
SCALE_COLUMNS = [
    # (comp column, unisim column, candidate factors, label)
    ("MOLARMASS", "molar_mass_g_per_mol", (1.0, 0.001, 1000.0), "molar mass"),
    ("LIQDENS", "std_liquid_density_kg_per_m3", (0.001, 1.0, 1000.0), "liquid density"),
    ("PC", "critical_pressure_kPa", (0.01, 1.0, 100.0, 0.001), "critical pressure"),
    ("ACSFACT", "acentric_factor", (1.0,), "acentric factor"),
]

TEMPERATURE_COLUMNS = [
    ("NORMBOIL", "normal_boiling_point_C", "normal boiling point"),
    ("TC", "critical_temperature_C", "critical temperature"),
]

# Disposition of each COMP.csv column.
CORRECTION_CANDIDATE = "correction candidate"
REVIEW_ONLY = "review only"

DISPOSITION = {
    "MOLARMASS": CORRECTION_CANDIDATE,
    "LIQDENS": CORRECTION_CANDIDATE,
    "NORMBOIL": CORRECTION_CANDIDATE,
    "TC": REVIEW_ONLY,
    "PC": REVIEW_ONLY,
    "ACSFACT": REVIEW_ONLY,
}

# Deviation beyond which a row is reported. Temperatures use an absolute
# threshold in kelvin: a percentage on a Celsius scale that crosses zero is
# meaningless.
RELATIVE_THRESHOLD = {
    "MOLARMASS": 0.01,
    "LIQDENS": 0.02,
    "PC": 0.05,
    "ACSFACT": 0.10,
}
ABSOLUTE_THRESHOLD_K = {
    "NORMBOIL": 5.0,
    "TC": 5.0,
}
# Acentric factors are small; ignore differences that are numerically trivial
# even when the relative difference looks large.
ACENTRIC_FLOOR = 0.02

# A derived scale factor must land within this fraction of a candidate factor.
UNIT_RATIO_TOLERANCE = 0.25
# The better temperature basis must be at least this many times better.
TEMPERATURE_BASIS_MARGIN = 3.0
KELVIN_OFFSET = 273.15
# Rows used to derive units: molar mass must already agree this closely.
ANCHOR_TOLERANCE = 0.01
MIN_ANCHOR_ROWS = 20


def parse_float(text):
    """Convert a CSV cell to float, returning None when it is not numeric.

    :param text: str or None, the raw cell contents.
    :returns: float value, or None when the cell is empty or unparseable.
    """
    if text is None:
        return None
    text = text.strip()
    if not text:
        return None
    try:
        return float(text)
    except ValueError:
        return None


def read_comp(path):
    """Read COMP.csv into a list of dicts keyed by column name.

    :param path: str path to COMP.csv.
    :returns: list of dict rows.
    """
    with open(path, "r", encoding="utf-8-sig", newline="") as handle:
        # csv handles the quoted names that contain commas.
        return list(csv.DictReader(handle))


def read_library(path):
    """Read the extracted UniSim reference CSV.

    :param path: str path to the UniSim library CSV.
    :returns: list of dict rows.
    """
    with open(path, "r", encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def index_by_unique_cas(rows, cas_field):
    """Index rows by CAS number, keeping only CAS numbers that occur once.

    Ambiguity is dropped rather than resolved: a CAS number shared by several
    rows cannot identify a single substance.

    :param rows: list of dict rows.
    :param cas_field: str name of the CAS column in those rows.
    :returns: tuple (dict mapping CAS to row, int count of dropped CAS values).
    """
    counts = collections.Counter()
    for row in rows:
        cas = (row.get(cas_field) or "").strip()
        if cas:
            counts[cas] += 1

    unique = {}
    for row in rows:
        cas = (row.get(cas_field) or "").strip()
        if cas and counts[cas] == 1:
            unique[cas] = row
    dropped = sum(1 for cas, n in counts.items() if n > 1)
    return unique, dropped


def median(values):
    """Return the median of a sequence of floats.

    :param values: non-empty sequence of float.
    :returns: float median.
    """
    ordered = sorted(values)
    middle = len(ordered) // 2
    if len(ordered) % 2:
        return ordered[middle]
    return 0.5 * (ordered[middle - 1] + ordered[middle])


def derive_scale_factor(pairs, candidates, label):
    """Derive the COMP-to-UniSim unit factor for a scale-type column.

    :param pairs: sequence of (comp value, unisim value) float tuples.
    :param candidates: sequence of plausible float factors.
    :param label: str used in messages.
    :returns: float, the candidate factor supported by the data.
    :raises RuntimeError: when the median ratio matches no candidate, which
        means the unit is not one of the expected ones.
    """
    ratios = [c / u for c, u in pairs if u not in (None, 0.0) and c is not None]
    if len(ratios) < MIN_ANCHOR_ROWS:
        raise RuntimeError(
            "only %d usable rows for %s; too few to derive its unit"
            % (len(ratios), label)
        )
    observed = median(ratios)
    best = min(candidates, key=lambda f: abs(observed - f) / f)
    error = abs(observed - best) / best
    print(
        "  unit  %-20s median COMP/UniSim = %-12.6g -> factor %-8g (%.1f%% off)"
        % (label, observed, best, 100.0 * error)
    )
    if error > UNIT_RATIO_TOLERANCE:
        raise RuntimeError(
            "median ratio %.6g for %s matches no expected factor %s; the "
            "COMP.csv unit is not what this comparison assumes"
            % (observed, label, list(candidates))
        )
    return best


def derive_temperature_basis(pairs, label):
    """Decide whether a COMP.csv temperature column is Celsius or Kelvin.

    :param pairs: sequence of (comp value, unisim Celsius value) float tuples.
    :param label: str used in messages.
    :returns: float offset to add to the UniSim Celsius value to reach the
        COMP.csv basis: 0.0 for Celsius, 273.15 for Kelvin.
    :raises RuntimeError: when neither basis is clearly better.
    """
    usable = [(c, u) for c, u in pairs if c is not None and u is not None]
    if len(usable) < MIN_ANCHOR_ROWS:
        raise RuntimeError(
            "only %d usable rows for %s; too few to derive its basis"
            % (len(usable), label)
        )
    as_celsius = median([abs(c - u) for c, u in usable])
    as_kelvin = median([abs(c - (u + KELVIN_OFFSET)) for c, u in usable])
    print(
        "  basis %-20s median |diff| = %8.3f as degC, %8.3f as K"
        % (label, as_celsius, as_kelvin)
    )
    if as_celsius * TEMPERATURE_BASIS_MARGIN < as_kelvin:
        return 0.0
    if as_kelvin * TEMPERATURE_BASIS_MARGIN < as_celsius:
        return KELVIN_OFFSET
    raise RuntimeError(
        "cannot tell whether COMP.csv %s is Celsius or Kelvin (median |diff| "
        "%.3f vs %.3f); refusing to guess" % (label, as_celsius, as_kelvin)
    )


def main(argv=None):
    """Command line entry point.

    :param argv: optional list of str arguments; defaults to sys.argv[1:].
    :returns: int process exit code, 0 on success.
    """
    here = os.path.dirname(os.path.abspath(__file__))
    default_comp = os.path.join(
        here, os.pardir, "src", "main", "resources", "data", "COMP.csv"
    )

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--library", required=True, help="UniSim reference CSV")
    parser.add_argument("--comp", default=os.path.normpath(default_comp))
    parser.add_argument("--out", default=None, help="optional deviation CSV")
    args = parser.parse_args(argv)

    comp_rows = read_comp(args.comp)
    lib_rows = read_library(args.library)
    print("COMP.csv rows      : %d" % len(comp_rows))
    print("UniSim rows        : %d" % len(lib_rows))

    comp_index, comp_dropped = index_by_unique_cas(comp_rows, "CASnumber")
    lib_index, lib_dropped = index_by_unique_cas(lib_rows, "cas")
    print("ambiguous CAS dropped: %d in COMP.csv, %d in UniSim" % (comp_dropped, lib_dropped))

    shared = sorted(set(comp_index) & set(lib_index))
    print("unambiguously matched: %d components" % len(shared))
    if len(shared) < MIN_ANCHOR_ROWS:
        print("too few matches to compare; stopping")
        return 1

    # Anchor on rows whose molar mass already agrees, so the unit derivation is
    # not skewed by the corrupt rows the comparison is meant to find.
    mass_pairs = []
    for cas in shared:
        c = parse_float(comp_index[cas].get("MOLARMASS"))
        u = parse_float(lib_index[cas].get("molar_mass_g_per_mol"))
        if c is not None and u:
            mass_pairs.append((c, u))
    print("\nderiving units from the data:")
    mass_factor = derive_scale_factor(mass_pairs, (1.0, 0.001, 1000.0), "molar mass")

    anchors = []
    for cas in shared:
        c = parse_float(comp_index[cas].get("MOLARMASS"))
        u = parse_float(lib_index[cas].get("molar_mass_g_per_mol"))
        if c is not None and u and abs(c - mass_factor * u) <= ANCHOR_TOLERANCE * mass_factor * u:
            anchors.append(cas)
    print("  anchor rows (molar mass agrees): %d" % len(anchors))

    factors = {"MOLARMASS": mass_factor}
    for comp_col, lib_col, candidates, label in SCALE_COLUMNS:
        if comp_col == "MOLARMASS":
            continue
        pairs = []
        for cas in anchors:
            c = parse_float(comp_index[cas].get(comp_col))
            u = parse_float(lib_index[cas].get(lib_col))
            if c is not None and u:
                pairs.append((c, u))
        factors[comp_col] = derive_scale_factor(pairs, candidates, label)

    offsets = {}
    for comp_col, lib_col, label in TEMPERATURE_COLUMNS:
        pairs = []
        for cas in anchors:
            c = parse_float(comp_index[cas].get(comp_col))
            u = parse_float(lib_index[cas].get(lib_col))
            if c is not None and u is not None:
                pairs.append((c, u))
        offsets[comp_col] = derive_temperature_basis(pairs, label)

    findings = []
    for cas in shared:
        comp_row = comp_index[cas]
        lib_row = lib_index[cas]
        name = (comp_row.get("NAME") or "").strip()

        for comp_col, lib_col, _candidates, label in SCALE_COLUMNS:
            c = parse_float(comp_row.get(comp_col))
            u = parse_float(lib_row.get(lib_col))
            if c is None or u is None:
                continue
            expected = factors[comp_col] * u
            if expected == 0.0:
                continue
            deviation = abs(c - expected) / abs(expected)
            if comp_col == "ACSFACT" and abs(c - expected) < ACENTRIC_FLOOR:
                continue
            if deviation > RELATIVE_THRESHOLD[comp_col]:
                findings.append(
                    {
                        "disposition": DISPOSITION[comp_col],
                        "property": comp_col,
                        "name": name,
                        "cas": cas,
                        "comp_value": c,
                        "unisim_value": expected,
                        "deviation_pct": 100.0 * deviation,
                        "deviation_abs": c - expected,
                    }
                )

        for comp_col, lib_col, _label in TEMPERATURE_COLUMNS:
            c = parse_float(comp_row.get(comp_col))
            u = parse_float(lib_row.get(lib_col))
            if c is None or u is None:
                continue
            expected = u + offsets[comp_col]
            difference = c - expected
            if abs(difference) > ABSOLUTE_THRESHOLD_K[comp_col]:
                findings.append(
                    {
                        "disposition": DISPOSITION[comp_col],
                        "property": comp_col,
                        "name": name,
                        "cas": cas,
                        "comp_value": c,
                        "unisim_value": expected,
                        "deviation_pct": float("nan"),
                        "deviation_abs": difference,
                    }
                )

    print("\ndeviations by property:")
    by_property = collections.Counter(f["property"] for f in findings)
    for comp_col in ("MOLARMASS", "LIQDENS", "NORMBOIL", "TC", "PC", "ACSFACT"):
        print(
            "  %-10s %-22s %4d"
            % (comp_col, DISPOSITION[comp_col], by_property.get(comp_col, 0))
        )
    print("  %-10s %-22s %4d" % ("TOTAL", "", len(findings)))

    for disposition in (CORRECTION_CANDIDATE, REVIEW_ONLY):
        subset = [f for f in findings if f["disposition"] == disposition]
        subset.sort(key=lambda f: -abs(f["deviation_abs"]))
        print("\n%s -- %d findings, largest 12:" % (disposition, len(subset)))
        for finding in subset[:12]:
            print(
                "  %-10s %-26s %-12s COMP %14.5g  UniSim %14.5g"
                % (
                    finding["property"],
                    finding["name"][:26],
                    finding["cas"],
                    finding["comp_value"],
                    finding["unisim_value"],
                )
            )

    if args.out:
        with open(args.out, "w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(
                handle,
                fieldnames=[
                    "disposition",
                    "property",
                    "name",
                    "cas",
                    "comp_value",
                    "unisim_value",
                    "deviation_pct",
                    "deviation_abs",
                ],
            )
            writer.writeheader()
            for finding in sorted(
                findings, key=lambda f: (f["disposition"], f["property"], f["name"])
            ):
                writer.writerow(finding)
        print("\nwrote %s" % args.out)

    return 0


if __name__ == "__main__":
    sys.exit(main())
