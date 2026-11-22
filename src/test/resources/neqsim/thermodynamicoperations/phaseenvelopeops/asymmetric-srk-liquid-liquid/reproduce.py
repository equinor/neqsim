"""Independently solve the fixed-feed SRK liquid-liquid onset with decimal arithmetic.

Only the Python standard library is used. No NeqSim pressure, volume, fugacity,
flash, continuation, derivative or linear-algebra implementation is called.
All source coefficients are explicit in inputs.json; they are model inputs,
not independent physical-property measurements.
"""

import json
from decimal import Decimal, localcontext
from pathlib import Path

ROOT = Path(__file__).resolve().parent
D = Decimal


def linear_solve(matrix, rhs):
    """Solve the small Newton system by pivoted Gaussian elimination."""
    augmented = [list(row) + [value] for row, value in zip(matrix, rhs)]
    for column in range(len(rhs)):
        pivot_row = max(
            range(column, len(rhs)),
            key=lambda row: abs(augmented[row][column]),
        )
        augmented[column], augmented[pivot_row] = (
            augmented[pivot_row],
            augmented[column],
        )
        pivot = augmented[column][column]
        augmented[column] = [value / pivot for value in augmented[column]]
        for row in range(len(rhs)):
            if row != column:
                factor = augmented[row][column]
                augmented[row] = [
                    value - factor * pivot_value
                    for value, pivot_value in zip(
                        augmented[row], augmented[column]
                    )
                ]
    return [row[-1] for row in augmented]


def calculate(inputs, precision):
    """Solve onset chemical potentials and inspect both liquid minima."""
    with localcontext() as context:
        context.prec = precision
        temperature = D(inputs["temperature_K"])
        gas_constant = D(inputs["R"])
        feed = list(map(D, inputs["feed_mole_fractions"]))
        attraction = list(map(D, inputs["aT"]))
        covolume = list(map(D, inputs["b"]))
        interaction = [list(map(D, row)) for row in inputs["kij"]]
        pair_attraction = [
            [
                (attraction[i] * attraction[j]).sqrt()
                * (1 - interaction[i][j])
                for j in range(3)
            ]
            for i in range(3)
        ]

        def phase_values(composition, pressure, root_guess):
            """Evaluate the SRK cubic and its closed-form fugacity equation."""
            mixed_b = sum(composition[i] * covolume[i] for i in range(3))
            mixed_a = sum(
                composition[i] * composition[j] * pair_attraction[i][j]
                for i in range(3)
                for j in range(3)
            )
            reduced_a = mixed_a * pressure / (gas_constant * temperature) ** 2
            reduced_b = mixed_b * pressure / (gas_constant * temperature)
            compressibility = D(root_guess)
            for _ in range(100):
                polynomial = (
                    compressibility ** 3
                    - compressibility ** 2
                    + (reduced_a - reduced_b - reduced_b ** 2)
                    * compressibility
                    - reduced_a * reduced_b
                )
                derivative = (
                    3 * compressibility ** 2
                    - 2 * compressibility
                    + reduced_a - reduced_b - reduced_b ** 2
                )
                correction = polynomial / derivative
                compressibility -= correction
                if abs(correction) < D(10) ** (-precision + 10):
                    break
            else:
                raise ArithmeticError("SRK cubic root did not converge")
            if compressibility <= reduced_b:
                raise ArithmeticError("Root violates covolume exclusion")
            log_phi = []
            for component in range(3):
                cross_sum = sum(
                    composition[j] * pair_attraction[component][j]
                    for j in range(3)
                )
                b_ratio = covolume[component] / mixed_b
                log_phi.append(
                    b_ratio * (compressibility - 1)
                    - (compressibility - reduced_b).ln()
                    - reduced_a / reduced_b
                    * (2 * cross_sum / mixed_a - b_ratio)
                    * (1 + reduced_b / compressibility).ln()
                )
            volume = compressibility * gas_constant * temperature / pressure
            calculated_pressure = (
                gas_constant * temperature / (volume - mixed_b)
                - mixed_a / (volume * (volume + mixed_b))
            )
            pressure_derivative = (
                -gas_constant * temperature / (volume - mixed_b) ** 2
                + mixed_a * (2 * volume + mixed_b)
                / (volume * (volume + mixed_b)) ** 2
            )
            return (
                log_phi, compressibility, volume, calculated_pressure,
                pressure_derivative,
            )

        def incipient_composition(variables):
            weights = [variables[1].exp(), variables[2].exp(), D(1)]
            return [value / sum(weights) for value in weights]

        def residual(variables):
            pressure = variables[0].exp()
            incipient = incipient_composition(variables)
            feed_phi = phase_values(feed, pressure, inputs["feed_Z_guess"])[0]
            incipient_phi = phase_values(
                incipient, pressure, inputs["incipient_Z_guess"]
            )[0]
            return [
                feed[i].ln() + feed_phi[i]
                - incipient[i].ln() - incipient_phi[i]
                for i in range(3)
            ]

        composition_guess = list(map(D, inputs["incipient_composition_guess"]))
        variables = [
            D(inputs["pressure_guess_bara"]).ln(),
            (composition_guess[0] / composition_guess[2]).ln(),
            (composition_guess[1] / composition_guess[2]).ln(),
        ]
        derivative_step = D("1e-20")
        for _ in range(30):
            equations = residual(variables)
            if max(map(abs, equations)) < D("1e-50"):
                break
            columns = []
            for column in range(3):
                plus = list(variables)
                minus = list(variables)
                plus[column] += derivative_step
                minus[column] -= derivative_step
                columns.append([
                    (p - m) / (2 * derivative_step)
                    for p, m in zip(residual(plus), residual(minus))
                ])
            correction = linear_solve(list(zip(*columns)), equations)
            variables = [
                value - delta for value, delta in zip(variables, correction)
            ]
        else:
            raise ArithmeticError("Liquid-liquid onset did not converge")
        pressure = variables[0].exp()
        incipient = incipient_composition(variables)
        phases = []
        for composition, root_guess in [
            (feed, inputs["feed_Z_guess"]),
            (incipient, inputs["incipient_Z_guess"]),
        ]:
            _, z_factor, volume, calculated_pressure, dp_dv = phase_values(
                composition, pressure, root_guess
            )

            def reduced_gibbs(first, second):
                fractions = [first, second, 1 - first - second]
                log_phi = phase_values(fractions, pressure, root_guess)[0]
                return sum(
                    fractions[i] * (fractions[i].ln() + log_phi[i])
                    for i in range(3)
                )

            first, second = composition[:2]
            step = D("1e-10")
            gibbs = reduced_gibbs(first, second)
            h11 = (
                reduced_gibbs(first + step, second)
                + reduced_gibbs(first - step, second) - 2 * gibbs
            ) / step ** 2
            h22 = (
                reduced_gibbs(first, second + step)
                + reduced_gibbs(first, second - step) - 2 * gibbs
            ) / step ** 2
            h12 = (
                reduced_gibbs(first + step, second + step)
                + reduced_gibbs(first - step, second - step)
                - reduced_gibbs(first + step, second - step)
                - reduced_gibbs(first - step, second + step)
            ) / (4 * step ** 2)
            gap = ((h11 - h22) ** 2 + 4 * h12 ** 2).sqrt()
            eigenvalues = [(h11 + h22 - gap) / 2, (h11 + h22 + gap) / 2]
            assert dp_dv < 0
            assert all(value > 0 for value in eigenvalues)
            phases.append({
                "composition": list(map(str, composition)),
                "Z": str(z_factor),
                "molar_volume_internal": str(volume),
                "EOS_pressure_residual_bara": str(calculated_pressure - pressure),
                "dP_dV": str(dp_dv),
                "reduced_Gibbs_composition_Hessian_eigenvalues": list(
                    map(str, eigenvalues)
                ),
            })
        return {
            "decimal_precision": precision,
            "pressure_bara": str(pressure),
            "maximum_log_fugacity_residual": str(max(map(abs, residual(variables)))),
            "Hessian_finite_difference_step": "1e-10",
            "phases": phases,
        }


def main():
    """Reproduce two precisions and retain inspectable numerical evidence."""
    inputs = json.loads((ROOT / "inputs.json").read_text())
    references = [calculate(inputs, precision) for precision in (60, 80)]
    assert abs(
        D(references[0]["pressure_bara"]) - D(references[1]["pressure_bara"])
    ) < D("1e-50")
    (ROOT / "reference.json").write_text(json.dumps(references, indent=2) + "\n")
    print("Independent 60/80-digit SRK liquid-liquid onset reproduced")
    print("Pressure: " + references[1]["pressure_bara"] + " bara")


if __name__ == "__main__":
    main()
