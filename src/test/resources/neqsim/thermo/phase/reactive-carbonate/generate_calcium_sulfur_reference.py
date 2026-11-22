"""Generate independent Ca/sulfate/sulfide activity states from the pinned source.

Use the same --database and --library arguments as generate_reference.py.
This generator retains source H2S dimer chemistry and does not equilibrate any
mineral or couple sulfate to sulfide by redox. NeqSim replays free species;
the very dilute dimer is recorded to expose the speciation-set difference.
"""

import argparse
import csv
import itertools
import math
from pathlib import Path

from phreeqpy.iphreeqc.phreeqc_dll import IPhreeqc

from generate_reference import SOURCE_BLOB, SOURCE_COMMIT, reference_database

SPECIES = ("Na+", "K+", "Ca+2", "Cl-", "H+", "OH-", "HCO3-", "CO3-2",
           "SO4-2", "HSO4-", "CO2", "H2Sg", "HSg-")


def cases():
    """Provide CaCl2, mixed chlorides, sulfate-only and acidic-sulfate cases."""
    for temperature, salt, mixture, fraction, sulfide in itertools.product(
        (298.15, 313.15, 323.15), (0.1, 1.0, 3.0),
        ((0.0, 0.0, 0.5), (0.5, 0.0, 0.25), (0.0, 0.5, 0.25)),
        (0.0, 0.2), (0.0, 0.001),
    ):
        sodium, potassium, calcium = (salt * value for value in mixture)
        sulfate = 0.5 * salt * fraction
        chloride = salt * (1.0 - fraction)
        yield (temperature, sodium, potassium, calcium, chloride, sulfate, 0.001, sulfide, "4 charge", "")
    for temperature, sulfate, cation in itertools.product(
        (298.15, 313.15, 323.15), (0.001, 0.01), ("Na", "K", "Ca"),
    ):
        sodium = 2 * sulfate if cation == "Na" else 0.0
        potassium = 2 * sulfate if cation == "K" else 0.0
        calcium = sulfate if cation == "Ca" else 0.0
        yield (temperature, sodium, potassium, calcium, 0.0, sulfate, 0.0, 0.0, "7 charge", "")
    for temperature, ph in itertools.product((298.15, 313.15, 323.15), (2, 3, 4)):
        yield (temperature, 0.5, 0.0, 0.25, 0.8, 0.1, 0.001, 0.001, str(ph), " charge")


def generate(database, library, output):
    """Store free species and native activities from IPhreeqc 3.7.3."""
    reference = IPhreeqc(str(library))
    reference.load_database(str(database))
    if reference.get_error_string():
        raise RuntimeError(reference.get_error_string())
    rows = []
    for temperature, sodium, potassium, calcium, chloride, sulfate, carbon, sulfide, ph, charge in cases():
        script = (
            "DELETE; -all\nPITZER\n-macinnes false\nSOLUTION 1\n"
            f"temp {temperature - 273.15:.12g}\nunits mol/kgw\npH {ph}\n"
            f"Na {sodium:.12g}\nK {potassium:.12g}\nCa {calcium:.12g}\n"
            f"Cl {chloride:.12g}{charge}\nS(6) {sulfate:.12g}\n"
            f"C(4) {carbon:.12g}\nSg {sulfide:.12g}\n"
            "SELECTED_OUTPUT\n-reset false\n-pH true\n-ionic_strength true\n"
            "-molalities " + " ".join(SPECIES) + " (H2Sg)2\n-activities "
            + " ".join(SPECIES) + " H2O\nEND\n"
        )
        reference.run_string(script)
        table = reference.get_selected_output_array()
        result = dict(zip(table[0], table[-1]))
        molalities = [result[f"m_{name}(mol/kgw)"] for name in SPECIES]
        log_gammas = [
            result[f"la_{name}"] - math.log10(molality)
            if molality > 1e-30 else float("nan")
            for name, molality in zip(SPECIES, molalities)
        ]
        rows.append([temperature, 1.01325] + molalities
                    + [result["pH"], 10 ** result["la_H2O"], result["mu"]]
                    + log_gammas + [result["m_(H2Sg)2(mol/kgw)"]])
    with output.open("w", newline="") as stream:
        stream.write(f"# IPhreeqc 3.7.3; source commit {SOURCE_COMMIT}\n")
        stream.write(f"# Database Git blob {SOURCE_BLOB}; MacInnes false; free-species replay\n")
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(["temperature_K", "pressure_bara"]
                        + [f"molality_{name}" for name in SPECIES]
                        + ["pH_unscaled", "water_activity", "ionic_strength"]
                        + [f"log_gamma_{name}" for name in SPECIES]
                        + ["h2s_dimer_m"])
        writer.writerows(rows)
    return len(rows)


def main():
    """Verify the source identity and generate deterministic aqueous states."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--database", type=Path, required=True)
    parser.add_argument("--library", type=Path, required=True)
    args = parser.parse_args()
    folder = Path(__file__).resolve().parent
    temporary = folder / "reference-calcium-sulfur-temporary.dat"
    temporary.write_text(reference_database(args.database), encoding="ascii")
    try:
        count = generate(temporary, args.library, folder / "iphreeqc-calcium-sulfur-states.csv")
    finally:
        temporary.unlink()
    print(f"Generated {count} independent calcium/sulfur states")


if __name__ == "__main__":
    main()
