"""Independent pure-water CPA alpha calibration and IAPWS-95 qualification.

Original numerical experiment for NeqSim issue #3762. No third-party EOS
implementation is copied. Requires numpy, scipy and iapws. Run with --refit
to repeat fitting; the default qualifies the stored fitted coefficients.

Training: 12 temperatures, 278.15 to 333.15 K in 5 K increments, 0.1 MPa.
Each state contributes the relative cp error / 0.02, relative unshifted density
error / 0.01, and log liquid/vapor fugacity ratio at IAPWS saturation pressure
/ 0.02. The density weight preserves baseline density rather than asserting
that the unshifted EOS describes density within 0.5%. NeqSim uses an existing
physical-property volume correction, which is outside this independent model.

This calibration is inspired by Palma et al. (2017), but its coefficients are
new and are not the coefficients published by Palma et al.
"""

from pathlib import Path
import argparse
import csv
import json
import importlib.metadata
import numpy as np
from scipy.optimize import brentq, least_squares
from iapws import IAPWS95

R = 8.314462618
tc = 647.3
a0 = 0.12277
b = 1.4515e-05
eps = 16655.0
beta = 0.0692
mw = 0.018015
cpcoef = np.array([36.54003, -0.034802404, 0.000116811, -1.3e-07, 5.254448e-11])


def load_repository_water_parameters():
    """Use the repository's existing water parameters when this file is in it."""
    for parent in Path(__file__).resolve().parents:
        source = parent / "src/main/resources/data/COMP.csv"
        if not source.is_file():
            continue
        with source.open(newline="") as handle:
            water = next(
                (row for row in csv.DictReader(handle) if row["NAME"] == "water")
            )
        return water
    return None


repository_water = load_repository_water_parameters()
if repository_water is not None:
    tc = float(repository_water["TC"]) + 273.15
    a0 = float(repository_water["aCPA_SRK"]) * 1e-05
    b = float(repository_water["bCPA_SRK"]) * 1e-05
    eps = float(repository_water["associationenergy"])
    beta = float(repository_water["associationboundingvolume_SRK"])
    mw = float(repository_water["MOLARMASS"]) / 1000
    cpcoef = np.array(
        [float(repository_water[key]) for key in ["CPA", "CPB", "CPC", "CPD", "CPE"]]
    )
base = np.array([0.67359, 0.0, 0.0, 0.0, 0.0])


def aa(t, c):
    z = 1 - np.sqrt(t / tc)
    zt = -1 / (2 * np.sqrt(t * tc))
    ztt = 1 / (4 * np.sqrt(tc) * t**1.5)
    h = 1 + sum((c[j - 1] * z**j for j in range(1, 6)))
    hz = sum((j * c[j - 1] * z ** (j - 1) for j in range(1, 6)))
    hzz = sum((j * (j - 1) * c[j - 1] * z ** (j - 2) for j in range(2, 6)))
    return (
        a0 * h * h,
        2 * a0 * h * hz * zt,
        2 * a0 * ((hz * zt) ** 2 + h * (hzz * zt**2 + hz * ztt)),
    )


def state(t, v, c):
    a, at, att = aa(t, c)
    w = v - 0.475 * b
    ex = np.exp(eps / R / t)
    D = 8 * b * beta * (ex - 1) / w
    s = np.sqrt(1 + D)
    x = 2 / (1 + s)
    Dt = -8 * b * beta * ex * eps / (R * t * t) / w
    Dtt = 8 * b * beta * ex * (2 * eps / (R * t**3) + (eps / (R * t * t)) ** 2) / w
    st = Dt / (2 * s)
    stt = Dtt / (2 * s) - Dt**2 / (4 * s**3)
    ut = -st / (1 + s)
    utt = -stt / (1 + s) + st**2 / (1 + s) ** 2
    xt = x * ut
    xtt = x * (utt + ut**2)
    xv = -x * (-D / w / (2 * s)) / (1 + s)
    p = R * t / (v - b) - a / (v * (v + b)) - 2 * R * t * (1 - x) / w
    pt = R / (v - b) - at / (v * (v + b)) - 2 * R * (1 - x) / w + 2 * R * t * xt / w
    pv = (
        -R * t / (v - b) ** 2
        + a * (2 * v + b) / (v * v * (v + b) ** 2)
        + 2 * R * t * (xv / w + (1 - x) / w**2)
    )
    ar = (
        -R * t * np.log(1 - b / v)
        - a / b * np.log(1 + b / v)
        + 4 * R * t * (np.log(x) - x / 2 + 0.5)
    )
    artt = (
        -att / b * np.log(1 + b / v)
        + 8 * R * (ut - xt / 2)
        + 4 * R * t * (utt - xtt / 2)
    )
    cp0 = sum((cpcoef[j] * t**j for j in range(5)))
    cp = cp0 - R - t * artt - t * pt**2 / pv
    return (p, cp, ar)


def vol(t, p, c, phase="l"):
    if phase == "l":
        return brentq(lambda v: state(t, v, c)[0] - p, b * 1.00001, b * 5, xtol=1e-17)
    return brentq(
        lambda v: state(t, v, c)[0] - p, R * t / p * 0.5, R * t / p * 1.5, xtol=1e-13
    )


def eqres(t, p, c):
    vl = vol(t, p, c)
    vv = vol(t, p, c, "v")
    return (state(t, vl, c)[2] - state(t, vv, c)[2] + p * (vl - vv)) / (R * t) - np.log(
        vl / vv
    )


train = []
for t in np.arange(278.15, 333.16, 5):
    ref = IAPWS95(T=t, P=0.1)
    sat = IAPWS95(T=t, x=0)
    train.append((t, ref.cp * 1000, ref.rho, sat.P * 1000000.0))


def objective(c, w=(0.02, 0.005, 0.02), extra=False):
    out = []
    for t, cp, rho, ps in train:
        v = vol(t, 100000.0, c)
        out.extend(
            [
                (state(t, v, c)[1] / mw / cp - 1) / w[0],
                (mw / v / rho - 1) / w[1],
                eqres(t, ps, c) / w[2],
            ]
        )
    if extra:
        for t in [373.15, 423.15, 523.15, 647.3]:
            out.append((aa(t, c)[0] / aa(t, base)[0] - 1) / 0.01)
    return out


def report(c):
    for t in [278.15, 283.15, 298.15, 313.15, 333.15, 353.15, 373.15, 398.15, 423.15]:
        p = 100000.0 if t <= 353.15 else 1000000.0
        ref = IAPWS95(T=t, P=p / 1000000.0)
        sat = IAPWS95(T=t, x=0)
        v = vol(t, p, c)
        cp = state(t, v, c)[1] / mw
        rho = mw / v
        ee = eqres(t, sat.P * 1000000.0, c)
        print(
            round(t, 2),
            round(cp, 2),
            round(100 * (cp / (1000 * ref.cp) - 1), 3),
            round(rho, 2),
            round(100 * (rho / ref.rho - 1), 3),
            round(ee * 100, 3),
        )


FITTED = np.array(
    [
        0.6670190973128074,
        -0.25656681951681287,
        1.3687767086793399,
        -3.1891110889699097,
        5.218580308820144,
    ]
)


def qualification(c):
    rows = []
    for t in [
        280.65,
        290.65,
        305.65,
        325.65,
        343.15,
        353.15,
        363.15,
        373.15,
        398.15,
        423.15,
    ]:
        ps = IAPWS95(T=t, x=0).P * 1000000.0
        for p in [100000.0, 1000000.0, 5000000.0, 10000000.0]:
            if p <= ps:
                continue
            ref = IAPWS95(T=t, P=p / 1000000.0)
            v = vol(t, p, c)
            vbase = vol(t, p, base)
            cp = state(t, v, c)[1] / mw
            cpbase = state(t, vbase, base)[1] / mw
            rows.append(
                {
                    "T_K": t,
                    "p_Pa": p,
                    "reference_cp_J_kg_K": ref.cp * 1000,
                    "model_cp_J_kg_K": cp,
                    "base_cp_J_kg_K": cpbase,
                    "relative_cp_error_percent": 100 * (cp / (ref.cp * 1000) - 1),
                    "reference_rho_kg_m3": ref.rho,
                    "model_untranslated_rho_kg_m3": mw / v,
                    "base_untranslated_rho_kg_m3": mw / vbase,
                    "density_change_from_base_percent": 100 * (vbase / v - 1),
                }
            )
    saturation = []
    for t in [278.15, 280.65, 298.15, 305.65, 333.15, 353.15, 373.15, 398.15, 423.15]:
        ps = IAPWS95(T=t, x=0).P * 1000000.0
        calculated = brentq(lambda p: eqres(t, p, c), ps * 0.8, ps * 1.2)
        saturation.append(
            {
                "T_K": t,
                "reference_psat_Pa": ps,
                "model_psat_Pa": calculated,
                "relative_psat_error_percent": 100 * (calculated / ps - 1),
            }
        )
    return (rows, saturation)


def write_test_references(output, coefficients):
    """Generate public equation-based values; no measured table is copied."""
    temperatures_C = [5, 10, 25, 40, 60, 7.5, 18, 32, 55, 80, 100, 125, 150]
    training_temperatures = [row[0] for row in train]
    rows = []
    for temperature_C in temperatures_C:
        temperature = temperature_C + 273.15
        psat = IAPWS95(T=temperature, x=0).P * 10
        for pressure_bar in [1.01325, 10.0, 50.0, 100.0]:
            if pressure_bar <= psat:
                continue
            ref = IAPWS95(T=temperature, P=pressure_bar / 10)
            split = (
                "training_temperature_pressure_holdout"
                if any((abs(temperature - t) < 1e-07 for t in training_temperatures))
                else "temperature_and_pressure_holdout"
            )
            rows.append(
                {
                    "T_K": temperature,
                    "P_bar": pressure_bar,
                    "cp_IAPWS95_J_kg_K": ref.cp * 1000,
                    "rho_IAPWS95_kg_m3": ref.rho,
                    "psat_IAPWS95_bar": psat,
                    "phase": "liquid",
                    "split": split,
                    "density_acceptance_applicable": str(
                        278.15 <= temperature <= 333.15
                    ).lower(),
                }
            )
    for temperature, pressure_bar in [
        (298.0, 0.001),
        (373.0, 0.1),
        (423.0, 1.0),
        (473.0, 1.0),
    ]:
        ref = IAPWS95(T=temperature, P=pressure_bar / 10)
        rows.append(
            {
                "T_K": temperature,
                "P_bar": pressure_bar,
                "cp_IAPWS95_J_kg_K": ref.cp * 1000,
                "rho_IAPWS95_kg_m3": ref.rho,
                "psat_IAPWS95_bar": IAPWS95(T=temperature, x=0).P * 10,
                "phase": "vapor",
                "split": "steam_holdout",
                "density_acceptance_applicable": "false",
            }
        )
    with output.open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    steam_report = []
    for row in rows:
        if row["phase"] != "vapor":
            continue
        t, p = (row["T_K"], row["P_bar"] * 100000.0)
        v = vol(t, p, coefficients, "v")
        vbase = vol(t, p, base, "v")
        cp = state(t, v, coefficients)[1] / mw
        cpbase = state(t, vbase, base)[1] / mw
        steam_report.append(
            {
                "T_K": t,
                "P_bar": p / 100000.0,
                "reference_cp_J_kg_K": row["cp_IAPWS95_J_kg_K"],
                "model_cp_J_kg_K": cp,
                "base_cp_J_kg_K": cpbase,
                "cp_change_from_base_percent": 100 * (cp / cpbase - 1),
                "relative_cp_error_percent": 100 * (cp / row["cp_IAPWS95_J_kg_K"] - 1),
            }
        )
    return (len(rows), steam_report)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--refit", action="store_true")
    parser.add_argument("--output-dir", type=Path, default=Path(__file__).parent)
    args = parser.parse_args()
    coefficients = FITTED.copy()
    fit_report = None
    if args.refit:
        fit = least_squares(
            lambda c: objective(c, (0.02, 0.01, 0.02)),
            base,
            diff_step=1e-05,
            xtol=1e-11,
            ftol=1e-11,
            gtol=1e-08,
            max_nfev=2500,
        )
        coefficients = fit.x
        fit_report = {
            "cost": fit.cost,
            "evaluations": fit.nfev,
            "success": bool(fit.success),
            "message": fit.message,
        }
    holdout, saturation = qualification(coefficients)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    reference_output = args.output_dir / "water_caloric_alpha_iapws95_reference.csv"
    reference_count, steam_report = write_test_references(
        reference_output, coefficients
    )
    data = {
        "issue": "https://github.com/equinor/neqsim/issues/3762",
        "description": "Original NeqSim water alpha calibration; not Palma published parameters",
        "alpha": "(1 + sum(c[j-1]*(1-sqrt(T/Tc))**j, j=1..5))**2",
        "coefficients": coefficients.tolist(),
        "fixed_parameters_SI": {
            "R_J_mol_K": R,
            "Tc_K": tc,
            "a0_Pa_m6_mol2": a0,
            "b_m3_mol": b,
            "association_energy_J_mol": eps,
            "association_volume": beta,
            "molar_mass_kg_mol": mw,
            "sites": 4,
            "association_scheme": "4C",
            "g": "1/(1-0.475*b/v)",
            "ideal_cp_polynomial_J_mol_K": cpcoef.tolist(),
        },
        "objective": "sum over training states of ((cp/cpref-1)/.02)^2 + ((rho/rhoref-1)/.01)^2 + (ln(fL/fV) at psat_ref/.02)^2",
        "training_reference": [
            {
                "T_K": t,
                "p_Pa": 100000.0,
                "reference_cp_J_kg_K": cp,
                "reference_rho_kg_m3": rho,
                "reference_psat_Pa": ps,
            }
            for t, cp, rho, ps in train
        ],
        "independent_holdouts": holdout,
        "saturation_verification": saturation,
        "steam_preservation_verification": steam_report,
        "test_reference_csv": reference_output.name,
        "test_reference_row_count": reference_count,
        "repository_parameters_loaded": repository_water is not None,
        "max_holdout_abs_cp_error_percent": max(
            (abs(r["relative_cp_error_percent"]) for r in holdout)
        ),
        "fit_report": fit_report,
        "reference_implementation": {
            "name": "iapws.IAPWS95",
            "version": importlib.metadata.version("iapws"),
            "license": "GPLv3; used as a numerical oracle only, no source copied",
        },
        "optimizer": {
            "name": "scipy.optimize.least_squares",
            "scipy_version": importlib.metadata.version("scipy"),
            "initial_coefficients": base.tolist(),
            "diff_step": 1e-05,
            "xtol": 1e-11,
            "ftol": 1e-11,
            "gtol": 1e-08,
            "max_nfev": 2500,
        },
        "sources": [
            "https://iapws.org/technical-guidance/release/IAPWS-95",
            "https://path.web.ua.pt/publications/acs.iecr.7b03522.pdf",
            "https://doi.org/10.1021/acs.jced.0c00649",
        ],
    }
    out = args.output_dir / "water_caloric_alpha_fit.json"
    out.write_text(json.dumps(data, indent=2) + "\n")
    print(
        json.dumps(
            {
                "coefficients": coefficients.tolist(),
                "holdout_count": len(holdout),
                "max_holdout_abs_cp_error_percent": data[
                    "max_holdout_abs_cp_error_percent"
                ],
                "output": str(out),
            },
            indent=2,
        )
    )
