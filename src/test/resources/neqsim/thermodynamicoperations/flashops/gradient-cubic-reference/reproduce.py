"""Reproduce SRK/PR gradient references with standard-library Decimal arithmetic.

The calculation uses explicit cubic log-fugacity equations and centered-difference
Jacobians, without invoking NeqSim or reusing its analytic derivatives.
"""

import json
from decimal import Decimal, localcontext
from pathlib import Path

ROOT = Path(__file__).resolve().parent
INPUTS = json.loads((ROOT / "inputs.json").read_text())


def solve_linear(jacobian, residual):
    """Solve a small dense system using pivoted Gaussian elimination."""
    size = len(residual)
    matrix = [jacobian[row][:] + [residual[row]] for row in range(size)]
    for column in range(size):
        pivot = max(range(column, size), key=lambda row: abs(matrix[row][column]))
        matrix[column], matrix[pivot] = matrix[pivot], matrix[column]
        divisor = matrix[column][column]
        matrix[column] = [value / divisor for value in matrix[column]]
        for row in range(size):
            if row == column:
                continue
            multiplier = matrix[row][column]
            matrix[row] = [
                matrix[row][index] - multiplier * matrix[column][index]
                for index in range(size + 1)
            ]
    return [row[-1] for row in matrix]


def compute(precision, model):
    """Solve the same 20 finite depth increments at arbitrary precision."""
    with localcontext() as context:
        context.prec = precision
        data = INPUTS["models"][model]
        covolume = list(map(Decimal, data["b"]))
        mass = list(map(Decimal, data["molar_mass_kg_mol"]))
        interaction = [list(map(Decimal, row)) for row in data["kij"]]
        attraction = [list(map(Decimal, row)) for row in data["aT_by_step"]]
        gas_constant = Decimal(INPUTS["R"])
        gravity = Decimal(INPUTS["gravity_m_s2"])
        temperature = Decimal(INPUTS["initial_temperature_K"])
        temperature_step = Decimal(INPUTS["temperature_step_K"])
        height_step = Decimal(INPUTS["height_step_m"])
        components = len(covolume)
        pair_attraction = [
            [
                [
                    (row[first] * row[second]).sqrt()
                    * (1 - interaction[first][second])
                    for second in range(components)
                ]
                for first in range(components)
            ]
            for row in attraction
        ]

        def log_fugacity(step, composition, pressure):
            """Evaluate cubic EOS log(phi_i*x_i*P) using the largest real root."""
            current_temperature = temperature + temperature_step * step
            mixed_b = sum(
                composition[index] * covolume[index] for index in range(components)
            )
            mixed_a = sum(
                composition[first]
                * composition[second]
                * pair_attraction[step][first][second]
                for first in range(components)
                for second in range(components)
            )
            reduced_a = mixed_a * pressure / (gas_constant * current_temperature) ** 2
            reduced_b = mixed_b * pressure / (gas_constant * current_temperature)
            compressibility = Decimal(1)
            for iteration in range(100):
                if model == "PR":
                    quadratic = -(1 - reduced_b)
                    linear = reduced_a - 3 * reduced_b**2 - 2 * reduced_b
                    constant = -(reduced_a * reduced_b - reduced_b**2 - reduced_b**3)
                else:
                    quadratic = Decimal(-1)
                    linear = reduced_a - reduced_b - reduced_b**2
                    constant = -reduced_a * reduced_b
                polynomial = (
                    compressibility**3
                    + quadratic * compressibility**2
                    + linear * compressibility
                    + constant
                )
                derivative = (
                    3 * compressibility**2 + 2 * quadratic * compressibility + linear
                )
                correction = polynomial / derivative
                compressibility -= correction
                if abs(correction) < Decimal(10) ** (-precision + 8):
                    break
            else:
                raise ArithmeticError("Cubic root did not converge")
            assert compressibility > reduced_b

            logarithms = []
            for index in range(components):
                relative_b = covolume[index] / mixed_b
                partial_a = 2 * sum(
                    composition[other] * pair_attraction[step][index][other]
                    for other in range(components)
                ) / mixed_a - relative_b
                if model == "PR":
                    root_two = Decimal(2).sqrt()
                    ratio = (
                        compressibility + (1 + root_two) * reduced_b
                    ) / (compressibility + (1 - root_two) * reduced_b)
                    attractive_term = (
                        reduced_a / (2 * root_two * reduced_b) * partial_a * ratio.ln()
                    )
                else:
                    attractive_term = (
                        reduced_a
                        / reduced_b
                        * partial_a
                        * (1 + reduced_b / compressibility).ln()
                    )
                log_phi = (
                    relative_b * (compressibility - 1)
                    - (compressibility - reduced_b).ln()
                    - attractive_term
                )
                logarithms.append(log_phi + composition[index].ln() + pressure.ln())
            return logarithms

        composition = list(map(Decimal, INPUTS["feed"]))
        pressure = Decimal(INPUTS["initial_pressure_bara"])
        for step in range(1, INPUTS["steps"] + 1):
            previous = log_fugacity(step - 1, composition, pressure)
            previous_temperature = temperature + temperature_step * (step - 1)
            target = [
                previous[index]
                + mass[index] * gravity * height_step / (gas_constant * previous_temperature)
                for index in range(components)
            ]
            state = composition + [pressure]

            def residual(values):
                logarithms = log_fugacity(step, values[:-1], values[-1])
                return [
                    value - reference for value, reference in zip(logarithms, target)
                ] + [sum(values[:-1]) - 1]

            difference_step = Decimal(10) ** (-precision // 3)
            for iteration in range(30):
                values = residual(state)
                if max(map(abs, values)) < Decimal(10) ** (-precision + 10):
                    break
                size = components + 1
                jacobian = [[Decimal(0)] * size for row in range(size)]
                for column in range(size):
                    plus = state[:]
                    minus = state[:]
                    plus[column] += difference_step
                    minus[column] -= difference_step
                    upper = residual(plus)
                    lower = residual(minus)
                    for row in range(size):
                        jacobian[row][column] = (
                            upper[row] - lower[row]
                        ) / (2 * difference_step)
                correction = solve_linear(jacobian, values)
                state = [value - update for value, update in zip(state, correction)]
            else:
                raise ArithmeticError("Gradient equations did not converge")
            composition = state[:-1]
            pressure = state[-1]

        return {
            "step": step,
            "T": str(temperature + temperature_step * step),
            "P": str(pressure),
            "x": list(map(str, composition)),
            "residual": str(max(map(abs, residual(state)))),
            "iterations": iteration,
        }


if __name__ == "__main__":
    result = {
        model + str(precision): compute(precision, model)
        for model in ["SRK", "PR"]
        for precision in [60, 80]
    }
    for model in ["SRK", "PR"]:
        with localcontext() as context:
            context.prec = 80
            lower = result[model + "60"]
            higher = result[model + "80"]
            assert abs(Decimal(lower["P"]) - Decimal(higher["P"])) < Decimal("1e-50")
            assert max(
                abs(Decimal(first) - Decimal(second))
                for first, second in zip(lower["x"], higher["x"])
            ) < Decimal("1e-50")
    print(json.dumps(result, indent=2))
