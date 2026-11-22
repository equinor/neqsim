"""Extract the UniSim Design traditional component library to a CSV file.

The CSV produced by this script is a *reference* table only. It is never
written back into NeqSim automatically; ``compare_comp_to_unisim.py`` reads it
and reports differences for human review.

Units returned by the UniSim COM API (confirmed against methane before any row
is written, see ``confirm_units``):

    MolecularWeightValue        g/mol
    NormalBoilingPointValue     degrees Celsius
    CriticalTemperatureValue    degrees Celsius
    CriticalPressureValue       kPa
    AcentricityValue            dimensionless
    StdLiquidDensityValue       kg/m3

The temperature unit matters: UniSim reports Celsius, not Kelvin. Writing
Celsius values into a column that is read as Kelvin would corrupt every
comparison, so the unit check aborts the run rather than guessing.

Components are added in small batches, each into a freshly created component
list. Adding thousands of components to a single list is quadratic in practice
and does not finish in reasonable time.

Usage::

    python extract_unisim_library.py --out unisim_library.csv
    python extract_unisim_library.py --out trial.csv --limit 300
"""

from __future__ import annotations

import argparse
import csv
import sys
import time

BATCH_SIZE = 100

FIELDS = [
    "name",
    "cas",
    "formula",
    "molar_mass_g_per_mol",
    "normal_boiling_point_C",
    "critical_temperature_C",
    "critical_pressure_kPa",
    "acentric_factor",
    "std_liquid_density_kg_per_m3",
]

# Methane, used to prove the unit of each numeric accessor before trusting any
# value. Tolerances are wide enough to accept UniSim's own parameter set (its
# critical pressure is 4640.7 kPa against a literature 4599 kPa) but far too
# tight for a Celsius value to pass as Kelvin, or g/mol as kg/mol.
UNIT_CHECKS = [
    ("molar_mass_g_per_mol", 16.043, 0.5, "g/mol"),
    ("normal_boiling_point_C", -161.5, 5.0, "degC"),
    ("critical_temperature_C", -82.6, 5.0, "degC"),
    ("critical_pressure_kPa", 4599.0, 250.0, "kPa"),
]

METHANE_CAS = "74-82-8"


def read_value(component, accessor):
    """Read a numeric COM property, returning None when it is unavailable.

    :param component: UniSim component COM object.
    :param accessor: str, name of the COM property to read.
    :returns: float value, or None if the property raised or was not numeric.
    """
    try:
        return float(getattr(component, accessor))
    except Exception:
        return None


def read_text(component, accessors):
    """Read the first readable string COM property from a list of candidates.

    :param component: UniSim component COM object.
    :param accessors: sequence of str property names, tried in order.
    :returns: stripped str value, or "" when none of the accessors worked.
    """
    for accessor in accessors:
        try:
            value = getattr(component, accessor)
        except Exception:
            continue
        if value is None:
            continue
        text = str(value).strip()
        if text and text != "?":
            return text
    return ""


def describe(component):
    """Convert one UniSim component into a plain dict of reference values.

    :param component: UniSim component COM object.
    :returns: dict keyed by the entries of FIELDS.
    """
    return {
        "name": read_text(component, ("name",)),
        # CAS_Number raises on this UniSim build; CAS_Number2 is the working one.
        "cas": read_text(component, ("CAS_Number2", "CAS_Number")),
        "formula": read_text(component, ("Formula",)),
        "molar_mass_g_per_mol": read_value(component, "MolecularWeightValue"),
        "normal_boiling_point_C": read_value(component, "NormalBoilingPointValue"),
        "critical_temperature_C": read_value(component, "CriticalTemperatureValue"),
        "critical_pressure_kPa": read_value(component, "CriticalPressureValue"),
        "acentric_factor": read_value(component, "AcentricityValue"),
        "std_liquid_density_kg_per_m3": read_value(component, "StdLiquidDensityValue"),
    }


def confirm_units(rows):
    """Verify UniSim's numeric units against methane's known properties.

    :param rows: list of dicts as produced by ``describe``.
    :raises RuntimeError: if methane is absent or any value is off by more than
        its stated tolerance, which would mean the assumed unit is wrong.
    """
    methane = None
    for row in rows:
        if row["cas"] == METHANE_CAS:
            methane = row
            break
    if methane is None:
        raise RuntimeError(
            "methane (CAS %s) not present in the extracted rows; cannot confirm "
            "units, so the extraction is not trustworthy" % METHANE_CAS
        )

    for field, expected, tolerance, unit in UNIT_CHECKS:
        actual = methane[field]
        if actual is None:
            raise RuntimeError("methane %s is missing; cannot confirm units" % field)
        deviation = abs(actual - expected)
        status = "ok" if deviation <= tolerance else "FAILED"
        print(
            "  unit check %-28s %12.4f vs %9.3f %-6s  %s"
            % (field, actual, expected, unit, status)
        )
        if deviation > tolerance:
            raise RuntimeError(
                "methane %s read as %.4f but %.3f %s was expected; the UniSim "
                "unit is not what this script assumes -- investigate before "
                "using any extracted value" % (field, actual, expected, unit)
            )


def extract(limit=None):
    """Read the UniSim traditional component library.

    :param limit: optional int, read only the first N library names (for a
        quick timing trial). None reads the whole library.
    :returns: list of dicts as produced by ``describe``.
    """
    import pythoncom
    import win32com.client as com
    from win32com.client import VARIANT

    app = com.Dispatch("UnisimDesign.Application")
    try:
        app.Visible = False
    except Exception:
        pass

    case = app.SimulationCases.Add()
    lists = case.BasisManager.ComponentLists

    probe = lists.Add("probe")
    names = list(probe.AvailableTraditionalLibraryComponentNames())
    print("library components offered: %d" % len(names))
    if limit is not None:
        names = names[:limit]
        print("limited to first %d names" % len(names))

    rows = []
    started = time.time()
    try:
        for start in range(0, len(names), BATCH_SIZE):
            batch = names[start : start + BATCH_SIZE]
            # A fresh list per batch: a single list holding thousands of
            # components becomes quadratically slow to add to.
            group = lists.Add("batch_%d" % start)
            try:
                group.AddTraditionalLibraryComponents(
                    VARIANT(pythoncom.VT_ARRAY | pythoncom.VT_BSTR, list(batch))
                )
            except Exception:
                for one in batch:
                    try:
                        group.AddTraditionalLibraryComponents(
                            VARIANT(pythoncom.VT_ARRAY | pythoncom.VT_BSTR, [one])
                        )
                    except Exception:
                        pass

            components = group.Components
            for index in range(components.Count):
                try:
                    rows.append(describe(components.Item(index)))
                except Exception:
                    pass

            try:
                lists.Remove("batch_%d" % start)
            except Exception:
                pass

            elapsed = time.time() - started
            done = min(start + BATCH_SIZE, len(names))
            print(
                "  %5d/%5d names, %5d rows, %6.1fs elapsed"
                % (done, len(names), len(rows), elapsed),
                flush=True,
            )
    finally:
        try:
            case.Close(False)
        except Exception:
            pass
        try:
            app.Quit()
        except Exception:
            pass

    return rows


def main(argv=None):
    """Command line entry point.

    :param argv: optional list of str arguments; defaults to sys.argv[1:].
    :returns: int process exit code, 0 on success.
    """
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True, help="path of the CSV to write")
    parser.add_argument(
        "--limit",
        type=int,
        default=None,
        help="read only the first N library names (timing trial)",
    )
    args = parser.parse_args(argv)

    rows = extract(limit=args.limit)
    print("components read: %d" % len(rows))
    if not rows:
        print("nothing was read; not writing a file")
        return 1

    confirm_units(rows)

    with open(args.out, "w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=FIELDS)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)

    with_cas = sum(1 for row in rows if row["cas"])
    print("wrote %s (%d rows, %d with a CAS number)" % (args.out, len(rows), with_cas))
    return 0


if __name__ == "__main__":
    sys.exit(main())
