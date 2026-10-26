#!/usr/bin/env python3
"""Independently solve a classical SRK incipient split with standard-library Decimal.

No NeqSim implementation or saved phase output is imported. Scalar a_i/b_i use
NeqSim's internal pressure-volume scaling; A and B below are dimensionless.
"""

import json
from decimal import Decimal as D, localcontext
from pathlib import Path


def linear_solve(matrix, rhs):
    """Solve the small Newton system by pivoted elimination."""
    n = len(rhs)
    augmented = [list(row) + [rhs[i]] for i, row in enumerate(matrix)]
    for column in range(n):
        pivot = max(range(column, n), key=lambda row: abs(augmented[row][column]))
        augmented[column], augmented[pivot] = augmented[pivot], augmented[column]
        scale = augmented[column][column]
        augmented[column] = [value / scale for value in augmented[column]]
        for row in range(n):
            if row == column:
                continue
            factor = augmented[row][column]
            augmented[row] = [
                augmented[row][j] - factor * augmented[column][j]
                for j in range(n + 1)
            ]
    return [row[-1] for row in augmented]


def solve(precision, seed_factor):
    """Solve log-fugacity equality and Rachford-Rice, returning qualification data."""
    with localcontext() as context:
        context.prec = precision
        data = json.loads(Path(__file__).with_name("inputs.json").read_text())
        temperature = D(data["temperature_K"])
        pressure = D(data["pressure_bara"])
        gas_constant = D(data["R_J_per_mol_K"])
        amounts = list(map(D, data["amounts"]))
        total = sum(amounts)
        feed = [amount / total for amount in amounts]
        covolumes = list(map(D, data["b_i"]))
        attractions = list(map(D, data["a_i"]))
        interaction = [list(map(D, row)) for row in data["kij"]]
        n = len(feed)
        pair_attractions = [
            [
                (attractions[i] * attractions[j]).sqrt() * (1 - interaction[i][j])
                for j in range(n)
            ]
            for i in range(n)
        ]

        def phase(composition, gas):
            """Evaluate SRK log phi using an independently solved cubic root."""
            attraction = sum(
                composition[i] * composition[j] * pair_attractions[i][j]
                for i in range(n)
                for j in range(n)
            )
            covolume = sum(composition[i] * covolumes[i] for i in range(n))
            a = attraction * pressure / (gas_constant * temperature) ** 2
            b = covolume * pressure / (gas_constant * temperature)
            z = D("0.60") if gas else D("0.29")
            for _ in range(80):
                residual = z**3 - z**2 + (a - b - b**2) * z - a * b
                derivative = 3 * z**2 - 2 * z + a - b - b**2
                step = residual / derivative
                z -= step
                if abs(step) < D("1e-48"):
                    break
            else:
                raise AssertionError("Cubic root did not converge")
            assert z > b
            # For an admissible SRK root, this sign is equivalent to dP/dV < 0.
            assert 3 * z**2 - 2 * z + a - b - b**2 > 0
            log_phi = [
                covolumes[i] / covolume * (z - 1)
                - (z - b).ln()
                - a / b
                * (
                    2 * sum(composition[j] * pair_attractions[i][j] for j in range(n))
                    / attraction
                    - covolumes[i] / covolume
                )
                * (1 + b / z).ln()
                for i in range(n)
            ]
            return log_phi, z

        def evaluate(variables):
            """Return fugacity and inventory residuals plus the two phase states."""
            ratios = [value.exp() for value in variables[:n]]
            beta = variables[n]
            liquid = [feed[i] / (1 + beta * (ratios[i] - 1)) for i in range(n)]
            vapor = [ratios[i] * liquid[i] for i in range(n)]
            liquid_phi, liquid_z = phase(liquid, False)
            vapor_phi, vapor_z = phase(vapor, True)
            residuals = [
                variables[i] + vapor_phi[i] - liquid_phi[i] for i in range(n)
            ] + [sum(vapor) - sum(liquid)]
            return residuals, liquid, vapor, liquid_z, vapor_z

        variables = [
            (D(value) * seed_factor).ln() for value in ("1.44", "0.57", "0.29", "0.15")
        ] + [D("0.000035") * seed_factor]
        step = D("1e-20")
        for _ in range(30):
            residuals = evaluate(variables)[0]
            if max(map(abs, residuals)) < D("1e-40"):
                break
            jacobian = [[D(0)] * (n + 1) for _ in range(n + 1)]
            for column in range(n + 1):
                plus, minus = variables.copy(), variables.copy()
                plus[column] += step
                minus[column] -= step
                upper = evaluate(plus)[0]
                lower = evaluate(minus)[0]
                for row in range(n + 1):
                    jacobian[row][column] = (upper[row] - lower[row]) / (2 * step)
            update = linear_solve(jacobian, [-value for value in residuals])
            variables = [value + delta for value, delta in zip(variables, update)]
        else:
            raise AssertionError("Flash equations did not converge")
        residuals, liquid, vapor, liquid_z, vapor_z = evaluate(variables)
        beta = variables[n]
        assert 0 < beta < 1
        assert abs(sum(liquid) - 1) < D("1e-38")
        assert abs(sum(vapor) - 1) < D("1e-38")
        assert vapor_z > liquid_z
        balance = max(
            abs((1 - beta) * liquid[i] + beta * vapor[i] - feed[i]) for i in range(n)
        )
        assert balance < D("1e-38")
        return {
            "precision": precision,
            "seed_factor": str(seed_factor),
            "vapor_fraction": str(beta),
            "liquid_z": str(liquid_z),
            "vapor_z": str(vapor_z),
            "maximum_log_fugacity_or_RR_residual": str(max(map(abs, residuals))),
            "material_balance_residual": str(balance),
        }


if __name__ == "__main__":
    results = [
        solve(precision, D(seed))
        for precision in (60, 80)
        for seed in ("0.98", "1", "1.02")
    ]
    print(json.dumps(results, indent=2))
