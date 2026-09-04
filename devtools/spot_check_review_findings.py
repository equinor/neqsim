"""Spot-check COMP.csv deviations against an independent third source.

``compare_comp_to_unisim.py`` reports where COMP.csv and UniSim disagree, but a
disagreement does not say which of the two is wrong. This script adds a third,
independent opinion from the ``chemicals`` package, whose critical properties
and acentric factors come from published compilations (Poling/Reid, DIPPR,
NIST-derived data), and reports which source the third opinion supports.

The verdict is deliberately conservative:

``COMP supported``
    The third source agrees with COMP.csv and not with UniSim. No action; the
    UniSim value is the outlier.

``UniSim supported``
    The third source agrees with UniSim and not with COMP.csv. This is a lead
    worth investigating and correcting, but only after human review.

``both differ``
    The third source agrees with neither. Needs a human and a primary source.

``no reference``
    The third source has no value for this CAS number. No conclusion drawn.

This script is read-only and never edits COMP.csv.

Usage::

    python spot_check_review_findings.py --findings devtools/comp_vs_unisim.csv
"""

from __future__ import annotations

import argparse
import csv
import sys

KELVIN_OFFSET = 273.15

# Agreement tolerance per property, in the unit COMP.csv uses.
TOLERANCE = {
    "TC": 5.0,        # degrees Celsius
    "PC": 2.0,        # bara
    "ACSFACT": 0.03,  # dimensionless
    "NORMBOIL": 5.0,  # degrees Celsius
    "MOLARMASS": 0.5,  # g/mol
}

COMP_SUPPORTED = "COMP supported"
UNISIM_SUPPORTED = "UniSim supported"
BOTH_DIFFER = "both differ"
NO_REFERENCE = "no reference"


def reference_value(prop, cas):
    """Look up an independent reference value for one property and CAS number.

    Values are converted into the unit COMP.csv uses: Celsius for temperatures,
    bara for pressure, g/mol for molar mass.

    :param prop: str COMP.csv column name (TC, PC, ACSFACT, NORMBOIL, MOLARMASS).
    :param cas: str CAS registry number.
    :returns: float reference value in COMP.csv units, or None when the
        reference source has no entry.
    """
    import chemicals

    try:
        if prop == "TC":
            value = chemicals.critical.Tc(cas)
            return None if value is None else value - KELVIN_OFFSET
        if prop == "PC":
            value = chemicals.critical.Pc(cas)
            return None if value is None else value / 1.0e5  # Pa -> bar
        if prop == "ACSFACT":
            return chemicals.acentric.omega(cas)
        if prop == "NORMBOIL":
            value = chemicals.miscdata.Tb(cas)
            return None if value is None else value - KELVIN_OFFSET
        if prop == "MOLARMASS":
            return chemicals.elements.MW(chemicals.identifiers.search_chemical(cas).formula)
    except Exception:
        return None
    return None


def judge(prop, comp_value, unisim_value, ref_value):
    """Decide which source the independent reference supports.

    :param prop: str COMP.csv column name.
    :param comp_value: float value held in COMP.csv.
    :param unisim_value: float UniSim value, already in COMP.csv units.
    :param ref_value: float independent reference value, or None.
    :returns: str one of the verdict constants.
    """
    if ref_value is None:
        return NO_REFERENCE
    tolerance = TOLERANCE[prop]
    near_comp = abs(ref_value - comp_value) <= tolerance
    near_unisim = abs(ref_value - unisim_value) <= tolerance
    if near_comp and not near_unisim:
        return COMP_SUPPORTED
    if near_unisim and not near_comp:
        return UNISIM_SUPPORTED
    if near_comp and near_unisim:
        # Both within tolerance of the reference: the disagreement is not
        # material for this property.
        return COMP_SUPPORTED
    return BOTH_DIFFER


def main(argv=None):
    """Command line entry point.

    :param argv: optional list of str arguments; defaults to sys.argv[1:].
    :returns: int process exit code, 0 on success.
    """
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--findings", required=True, help="comp_vs_unisim.csv")
    parser.add_argument(
        "--disposition",
        default=None,
        help="only check findings with this disposition",
    )
    parser.add_argument("--out", default=None, help="optional adjudicated CSV")
    args = parser.parse_args(argv)

    with open(args.findings, "r", encoding="utf-8", newline="") as handle:
        findings = list(csv.DictReader(handle))

    if args.disposition:
        findings = [f for f in findings if f["disposition"] == args.disposition]

    verdicts = {COMP_SUPPORTED: [], UNISIM_SUPPORTED: [], BOTH_DIFFER: [], NO_REFERENCE: []}

    for finding in findings:
        prop = finding["property"]
        if prop not in TOLERANCE:
            continue
        comp_value = float(finding["comp_value"])
        unisim_value = float(finding["unisim_value"])
        ref_value = reference_value(prop, finding["cas"])
        verdict = judge(prop, comp_value, unisim_value, ref_value)
        finding["reference_value"] = "" if ref_value is None else "%.5g" % ref_value
        finding["verdict"] = verdict
        verdicts[verdict].append(finding)

    print("adjudicated %d findings against an independent source\n" % len(findings))
    for verdict in (UNISIM_SUPPORTED, BOTH_DIFFER, COMP_SUPPORTED, NO_REFERENCE):
        rows = verdicts[verdict]
        print("%-18s %4d" % (verdict, len(rows)))

    for verdict in (UNISIM_SUPPORTED, BOTH_DIFFER):
        rows = verdicts[verdict]
        if not rows:
            continue
        print("\n%s -- COMP.csv looks wrong here:" % verdict)
        print(
            "  %-9s %-24s %-11s %12s %12s %12s"
            % ("property", "name", "cas", "COMP", "UniSim", "reference")
        )
        for row in sorted(rows, key=lambda r: (r["property"], r["name"])):
            print(
                "  %-9s %-24s %-11s %12.5g %12.5g %12s"
                % (
                    row["property"],
                    row["name"][:24],
                    row["cas"],
                    float(row["comp_value"]),
                    float(row["unisim_value"]),
                    row["reference_value"] or "-",
                )
            )

    if args.out:
        fieldnames = list(findings[0].keys()) if findings else []
        with open(args.out, "w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=fieldnames)
            writer.writeheader()
            for finding in findings:
                writer.writerow(finding)
        print("\nwrote %s" % args.out)

    return 0


if __name__ == "__main__":
    sys.exit(main())
