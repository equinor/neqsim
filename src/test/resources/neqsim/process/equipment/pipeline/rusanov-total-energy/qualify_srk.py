"""Audit the complete SRK local-flux replay with an80-digit internal-energy reference.

Run RusanovRichGasFluxTest's main first. This checks numerical fluxes and EOS API units,
not independent EOS accuracy, elapsed pooling or evaporation rates. Standard library only.
"""
import argparse
import csv
from decimal import Decimal as D, localcontext
import itertools
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parent
GATE = D("3e-14")
EOS_GATE = D("3e-13")
COMPONENTS = ["methane", "ethane", "propane", "n-butane", "n-pentane", "n-heptane"]
PRESSURES = [10.0, 40.0, 80.0, 115.0, 125.0, 150.0]
FIELDS = ["id", "T_K", "pL_bara", "pR_bara", "phase", "fraction_scale", "uL_m_s", "uR_m_s", "area_m2"]
for side in ["L", "R"]:
    FIELDS += [f"alpha{side}", f"rho{side}_kg_m3", f"c{side}_m_s", f"h{side}_J_kg", f"e{side}_J_kg"]
    FIELDS += [f"rhoEOS{side}_kg_m3", f"eMechanical{side}_J_kg"]
    FIELDS += [f"x{side}_{name}" for name in COMPONENTS]
FIELDS += ["mass_native_kg_s", "momentum_native_N", "energy_native_W", "mass_reference_kg_s",
           "momentum_reference_N", "energy_reference_W", "max_normalized_error"]


def evaluate(native, output):
    """Reject missing/reordered/invalid evidence and compare all native and Java-reference fluxes."""
    output.mkdir(parents=True, exist_ok=True)
    receipt = output / "srk-summary.json"
    receipt.unlink(missing_ok=True)
    with native.open(newline="") as stream:
        reader = csv.reader(stream)
        if next(reader, None) != FIELDS:
            raise ValueError("Invalid SRK schema")
        rows = list(reader)
    identities = list(itertools.product([260.0, 277.15, 290.0], range(6), ["gas", "oil"],
                                       [1.0, 1e-12, 1e-30], [(0.0, 0.0), (-5.0, 3.0), (7.0, -2.0)]))
    if len(rows) != len(identities) or len(rows) != 324:
        raise ValueError("Incomplete SRK matrix")
    maximum = D(0)
    maximum_eos = D(0)
    transition_rows = 0
    negative_rows = 0
    maximum_volume_work = D(0)
    references = []
    with localcontext() as context:
        context.prec = 80
        for index, (raw, identity) in enumerate(zip(rows, identities)):
            if len(raw) != len(FIELDS) or raw[0] != str(index):
                raise ValueError("Malformed or reordered SRK row")
            row = dict(zip(FIELDS, raw))
            numbers = {key: float(value) for key, value in row.items() if key != "phase"}
            if not all(math.isfinite(value) for value in numbers.values()):
                raise ValueError("Nonfinite SRK evidence")
            temperature, pressure_index, phase, factor, velocities = identity
            expected = [temperature, PRESSURES[pressure_index], PRESSURES[(pressure_index + 1) % 6],
                        factor, *velocities, math.pi * 1.0148 * 1.0148 / 4]
            actual = [numbers[key] for key in ["T_K", "pL_bara", "pR_bara", "fraction_scale",
                                               "uL_m_s", "uR_m_s", "area_m2"]]
            if actual != expected or row["phase"] != phase:
                raise ValueError("Changed SRK input identity")
            q = {key: D.from_float(value) for key, value in numbers.items()}
            states = []
            fluxes = []
            speeds = []
            for side in ["L", "R"]:
                alpha, rho, sound, h, e = [q[key] for key in
                    [f"alpha{side}", f"rho{side}_kg_m3", f"c{side}_m_s", f"h{side}_J_kg", f"e{side}_J_kg"]]
                composition = [q[f"x{side}_{name}"] for name in COMPONENTS]
                rho_eos = q[f"rhoEOS{side}_kg_m3"]
                e_mechanical = q[f"eMechanical{side}_J_kg"]
                if alpha < 0 or alpha > q["fraction_scale"]:
                    raise ValueError("Invalid phase fraction")
                if alpha == 0:
                    if any(value != 0 for value in [rho, sound, h, e, rho_eos, e_mechanical, *composition]):
                        raise ValueError("Absent SRK extension is not the declared zero record")
                else:
                    if rho <= 0 or rho_eos <= 0 or sound <= 0 or any(x < 0 or x > 1 for x in composition):
                        raise ValueError("Invalid occupied SRK state")
                    if abs(sum(composition) - 1) > EOS_GATE:
                        raise ValueError("Incomplete phase composition")
                    pv = q[f"p{side}_bara"] * 100000 / rho_eos
                    error = abs(h - e - pv) / (abs(h) + abs(e) + pv)
                    if error > EOS_GATE:
                        raise ValueError("SRK h-e unit identity failed")
                    maximum_eos = max(maximum_eos, error)
                    correction = q[f"p{side}_bara"] * 100000 * (1 / rho_eos - 1 / rho)
                    converted = e + correction
                    if abs(e_mechanical - converted) > EOS_GATE * (abs(e) + abs(correction)):
                        raise ValueError("Invalid explicit volume-work conversion")
                    maximum_volume_work = max(maximum_volume_work, abs(correction))
                    e = converted
                velocity = q[f"u{side}_m_s"]
                mass = alpha * rho
                energy = mass * (e + velocity * velocity / 2)
                traction = alpha * q[f"p{side}_bara"] * 100000
                states.append([mass, mass * velocity, energy])
                fluxes.append([mass * velocity, mass * velocity * velocity + traction,
                               velocity * (energy + traction)])
                speeds.append(abs(velocity) + sound if alpha else D(0))
            speed = max(speeds)
            reference = [q["area_m2"] * ((fluxes[0][k] + fluxes[1][k]) / 2
                         - speed * (states[1][k] - states[0][k]) / 2) for k in range(3)]
            for k, name in enumerate(["mass", "momentum", "energy"]):
                unit = ["kg_s", "N", "W"][k]
                scale = q["area_m2"] * (abs(fluxes[0][k]) + abs(fluxes[1][k])
                        + speed * (abs(states[0][k]) + abs(states[1][k])))
                for kind in ["native", "reference"]:
                    error = abs(q[f"{name}_{kind}_{unit}"] - reference[k])
                    if (scale == 0 and error != 0) or (scale and error / scale > GATE):
                        raise ValueError(f"SRK flux gate failed at row{index}, {name}, {kind}")
                    if scale:
                        maximum = max(maximum, error / scale)
            transition_rows += (q["alphaL"] == 0) != (q["alphaR"] == 0)
            negative_rows += any(q[f"alpha{s}"] > 0 and q[f"e{s}_J_kg"] < 0 for s in ["L", "R"])
            references.append([index, *reference])
    if not transition_rows or not negative_rows:
        raise ValueError("Missing appearance/disappearance or negative reference-energy controls")
    with (output / "srk-reference.csv").open("w", newline="") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(["id", "mass_kg_s", "momentum_N", "energy_W"])
        writer.writerows(references)
    summary = {"rows": 324, "decimal_digits": 80, "normalized_flux_gate": str(GATE),
               "normalized_eos_unit_gate": str(EOS_GATE), "maximum_normalized_flux_error": str(maximum),
               "maximum_normalized_eos_unit_error": str(maximum_eos), "transition_rows": transition_rows,
               "negative_reference_energy_rows": negative_rows,
               "maximum_absolute_volume_work_J_kg": str(maximum_volume_work),
               "scope": "SRK phase API/units and optional local numerical flux; no independent EOS/rate or transient qualification"}
    receipt.write_text(json.dumps(summary, indent=2, allow_nan=False) + "\n")
    return summary


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--native", type=Path, default=ROOT / "srk-native.csv")
    parser.add_argument("--output-dir", type=Path, default=ROOT)
    args = parser.parse_args()
    print(json.dumps(evaluate(args.native, args.output_dir), indent=2, allow_nan=False))
