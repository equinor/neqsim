"""Regenerate independent frozen aqueous states with IPhreeqc 3.7.3.

Usage: python generate_reference.py --database pitzer.dat --library libiphreeqc.so

The input must be the exact public-domain PHREEQC Git blob recorded below.
Only unsupported gas-EOS reporting and MEAN_GAMMAS blocks are removed for the
older reference executable; all aqueous species, reactions and PITZER rows
are unchanged. MacInnes scaling is explicitly disabled.
"""

import argparse
import csv
import hashlib
import math
from pathlib import Path

from phreeqpy.iphreeqc.phreeqc_dll import IPhreeqc

SOURCE_BLOB = "324f852784be84650b77bd7f07f8316aafd8188b"
SOURCE_COMMIT = "b0b3be767158ccc3322d2c816625cf470045e67e"


def reference_database(path):
    """Check source identity and remove only sections unused by aqueous tests."""
    raw = path.read_bytes()
    git_blob = b"blob " + str(len(raw)).encode() + b"\0" + raw
    if hashlib.sha1(git_blob).hexdigest() != SOURCE_BLOB:
        raise ValueError("Input database does not match the pinned Git blob")
    lines = raw.decode("latin-1").splitlines()
    output = []
    skip = False
    headers = {
        "SOLUTION_MASTER_SPECIES", "SOLUTION_SPECIES", "PHASES", "PITZER", "END",
    }
    for line in lines:
        data = line.split("#", 1)[0].strip()
        if data in {"GAS_BINARY_PARAMETERS", "MEAN_GAMMAS"}:
            skip = True
            continue
        if skip and data in headers:
            skip = False
        if not skip and data:
            output.append(data)
    return "\n".join(output) + "\n"


def generate(database, library, output):
    """Generate acidic NaCl, KCl and mixed-chloride speciation states."""
    reference = IPhreeqc(str(library))
    reference.load_database(str(database))
    if reference.get_error_string():
        raise RuntimeError(reference.get_error_string())
    species = ("Na+", "K+", "Cl-", "H+", "OH-", "HCO3-", "CO3-2", "CO2")
    rows = []
    for temperature in (298.15, 313.15, 323.15):
        for salt in (0.1, 1.0, 3.0):
            for sodium_fraction in (0.0, 0.5, 1.0):
                for carbon in (0.001, 0.1):
                    script = (
                        "DELETE; -all\nPITZER\n-macinnes false\nSOLUTION 1\n"
                        f"temp {temperature - 273.15:.12g}\nunits mol/kgw\n"
                        f"pH 4 charge\nNa {salt * sodium_fraction:.12g}\n"
                        f"K {salt * (1 - sodium_fraction):.12g}\n"
                        f"Cl {salt:.12g}\nC(4) {carbon:.12g}\n"
                        "SELECTED_OUTPUT\n-reset false\n-pH true\n"
                        "-ionic_strength true\n-molalities "
                        + " ".join(species)
                        + "\n-activities "
                        + " ".join(species)
                        + " H2O\nEND\n"
                    )
                    reference.run_string(script)
                    table = reference.get_selected_output_array()
                    result = dict(zip(table[0], table[-1]))
                    molalities = [result[f"m_{name}(mol/kgw)"] for name in species]
                    log_gammas = [
                        result[f"la_{name}"] - math.log10(molality)
                        if molality > 1e-30 else float("nan")
                        for name, molality in zip(species, molalities)
                    ]
                    rows.append(
                        [temperature, 1.01325]
                        + molalities
                        + [result["pH"], 10 ** result["la_H2O"], result["mu"]]
                        + log_gammas
                    )
    with output.open("w", newline="") as stream:
        stream.write(f"# IPhreeqc 3.7.3; source commit {SOURCE_COMMIT}\n")
        stream.write(f"# Database Git blob {SOURCE_BLOB}; MacInnes false\n")
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(
            ["temperature_K", "pressure_bara", "sodium_m", "potassium_m",
             "chloride_m", "hydronium_m", "hydroxide_m", "bicarbonate_m",
             "carbonate_m", "co2_m", "pH_unscaled", "water_activity",
             "ionic_strength"]
            + [f"log_gamma_{name}" for name in species]
        )
        writer.writerows(rows)
    return len(rows)



def published_dissociation_reference(output):
    """Evaluate the independent Millero et al. 2007 published NaCl correlation."""
    with output.open("w", newline="") as stream:
        stream.write("# Millero et al. (2007), DOI 10.1016/j.gca.2006.08.041; published fitted correlation\n")
        stream.write("# Pure-water pK1: Harned-Davis (1943); pK2: Harned-Scholes (1941)\n")
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(["temperature_K", "nacl_m", "pK1_star", "pK2_star", "fit_sigma_1", "fit_sigma_2"])
        for temperature in (298.15, 313.15, 323.15):
            for salt in (0.1, 1.0, 3.0, 5.0):
                root = math.sqrt(salt)
                first_shift = (
                    35.2911 * root + 0.8491 * salt - 0.32 * salt ** 1.5
                    + 0.055 * salt ** 2 - 1583.09 * root / temperature
                    - 5.4366 * root * math.log(temperature)
                )
                second_shift = (
                    38.2746 * root + 1.6057 * salt - 0.647 * salt ** 1.5
                    + 0.113 * salt ** 2 - 1738.16 * root / temperature
                    - 6.0346 * root * math.log(temperature)
                )
                first = 3404.71 / temperature + 0.032786 * temperature - 14.8435 + first_shift
                second = 2902.39 / temperature + 0.02379 * temperature - 6.4980 + second_shift
                writer.writerow([temperature, salt, first, second, 0.013, 0.020])


def main():
    """Validate source bytes and generate the deterministic reference CSV."""
    parser = argparse.ArgumentParser()
    parser.add_argument("--database", type=Path, required=True)
    parser.add_argument("--library", type=Path, required=True)
    args = parser.parse_args()
    folder = Path(__file__).resolve().parent
    database = folder / "reference-aqueous-temporary.dat"
    database.write_text(reference_database(args.database), encoding="ascii")
    try:
        count = generate(database, args.library, folder / "iphreeqc-states.csv")
    finally:
        database.unlink()
    published_dissociation_reference(folder / "millero2007-dissociation.csv")
    print(f"Generated {count} independent IPhreeqc states and 12 published-correlation states")


if __name__ == "__main__":
    main()
