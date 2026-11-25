"""Independent all-root, sampled common-tangent diagnostic for the frozen PR case.

The reference uses derivative isolation/bisection for its cubic roots. This test
uses Cardano/trigonometric roots and an algebraically summed Gibbs expression.
Sampling establishes evidence for this path, not a general stability proof.
Run with the selected Python, including ``-O``; stdout contains the JSON receipt.
"""

import hashlib
import importlib.util
import json
import math
from pathlib import Path
import unittest


FOLDER = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("binary_stability_reference", FOLDER / "reference.py")
REFERENCE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(REFERENCE)
R = 8.3144621
TC = (190.56, 540.2)
PC = (45.99e5, 27.4e5)
OMEGA = (0.0115, 0.349)
OA, OB, KIJ = 0.45724333333, 0.077803333, 0.02
BOUND = 1e-9


def real_roots(a, b, c):
    """Solve the monic cubic independently of the reference bisection routine."""
    p = b - a * a / 3
    q = 2 * a * a * a / 27 - a * b / 3 + c
    discriminant = (q / 2) ** 2 + (p / 3) ** 3
    def cube_root(value):
        return math.copysign(abs(value) ** (1 / 3), value)
    if discriminant == 0:
        value = cube_root(-q / 2)
        return sorted(set((2 * value - a / 3, -value - a / 3)))
    if discriminant > 0:
        return [cube_root(-q / 2 + math.sqrt(discriminant))
                + cube_root(-q / 2 - math.sqrt(discriminant)) - a / 3]
    angle = math.acos(max(-1, min(1, -q / (2 * math.sqrt(-(p / 3) ** 3)))))
    return [2 * math.sqrt(-p / 3) * math.cos((angle + 2 * k * math.pi) / 3) - a / 3
            for k in range(3)]


def mixture(temperature, pressure, methane_fraction):
    """Compute PR a/b and A/B from separately frozen constants, SI pressure."""
    fractions = (methane_fraction, 1 - methane_fraction)
    covolumes = [OB * R * tc / pc for tc, pc in zip(TC, PC)]
    attractions = []
    for tc, pc, omega in zip(TC, PC, OMEGA):
        kappa = 0.37464 + 1.54226 * omega - 0.26992 * omega * omega
        attractions.append(OA * (R * tc) ** 2 / pc
                           * (1 + kappa * (1 - math.sqrt(temperature / tc))) ** 2)
    cross = (1 - KIJ) * math.sqrt(attractions[0] * attractions[1])
    a = (fractions[0] ** 2 * attractions[0]
         + 2 * fractions[0] * fractions[1] * cross
         + fractions[1] ** 2 * attractions[1])
    b = sum(x * value for x, value in zip(fractions, covolumes))
    return a, b, a * pressure / (R * temperature) ** 2, b * pressure / (R * temperature)


def accepted_states(initial_moles=None):
    """Reconstruct initial, sixteen extraction endpoints and one surface state."""
    temperature, pressure, volume = 323.15, 80e5, 100.0
    x, y, liquid_volume, gas_volume = REFERENCE.tie(temperature, pressure)
    amounts = [70 / gas_volume * y + 30 / liquid_volume * x,
               70 / gas_volume * (1 - y) + 30 / liquid_volume * (1 - x)]
    if initial_moles is not None:
        amounts = list(initial_moles)
    initial = amounts[:]
    states = [(temperature, pressure, amounts[:])]
    cumulative_production = [0.0, 0.0]
    largest_balance_error = 0.0
    for interval in range(4):
        produced = [0.0, 0.0]
        for substep in range(4):
            _, beta, x, y = REFERENCE.equilibrium(temperature, pressure, amounts)
            if not (2000 < beta * sum(amounts) and 500 < (1 - beta) * sum(amounts)):
                raise ValueError("Frozen schedule exceeds available phase inventory")
            withdrawal = [2000 * y + 500 * x, 2000 * (1 - y) + 500 * (1 - x)]
            amounts = [amounts[i] + (50, 2.5)[i] - withdrawal[i] for i in range(2)]
            produced = [produced[i] + withdrawal[i] for i in range(2)]
            pressure = REFERENCE.tv(temperature, amounts, volume, pressure)
            states.append((temperature, pressure, amounts[:]))
        cumulative_production = [cumulative_production[i] + produced[i] for i in range(2)]
        for i in range(2):
            residual = initial[i] + (interval + 1) * (200, 10)[i]
            residual -= cumulative_production[i] + amounts[i]
            largest_balance_error = max(largest_balance_error, abs(residual))
    states.append((288.15, 1.01325e5, produced))
    return states, largest_balance_error


def require_common_tangent(gaps):
    """Reject nonfinite samples and any violation of the frozen Gibbs bound."""
    if not gaps or any(not math.isfinite(value) or value < -BOUND for value in gaps):
        raise ValueError("Sampled common-tangent stability check failed")


def diagnose(initial_moles=None, closed_cell_states=()):
    """Check every physical root at 1001 grid points plus accepted phase points."""
    states, balance_error = accepted_states(initial_moles)
    states.extend(closed_cell_states)
    minima, root_checks = [], 0
    cubic_error = pressure_error = fugacity_error = volume_error = lever_error = 0.0
    state_results = []
    for index, (temperature, pressure, amounts) in enumerate(states):
        x, y, vl, vg = REFERENCE.tie(temperature, pressure)
        liquid_mu = REFERENCE.phase(temperature, pressure, x, True)[1]
        vapor_mu = REFERENCE.phase(temperature, pressure, y, False)[1]
        fugacity_error = max(fugacity_error, *(abs(a - b) for a, b in zip(liquid_mu, vapor_mu)))
        total = sum(amounts)
        beta = (amounts[0] / total - x) / (y - x)
        if not (0 < beta < 1 and 0 < x < y < 1 and 0 < vl < vg):
            raise ValueError("Invalid phase/domain state")
        for i, (liquid_fraction, vapor_fraction) in enumerate(((x, y), (1 - x, 1 - y))):
            reconstructed = total * ((1 - beta) * liquid_fraction + beta * vapor_fraction)
            lever_error = max(lever_error, abs(reconstructed - amounts[i]) / total)
        if index < 17:
            volume_error = max(volume_error, abs(total * ((1 - beta) * vl + beta * vg) / 100 - 1))
        for phase_fraction, molar_volume in ((x, vl), (y, vg)):
            a, b, _, _ = mixture(temperature, pressure, phase_fraction)
            eos_pressure = R * temperature / (molar_volume - b)
            eos_pressure -= a / (molar_volume ** 2 + 2 * b * molar_volume - b * b)
            pressure_error = max(pressure_error, abs(eos_pressure / pressure - 1))
        gaps = []
        for fraction in [i / 1000 for i in range(1001)] + [x, y]:
            _, _, A, B = mixture(temperature, pressure, fraction)
            a, b, c = B - 1, A - 3 * B * B - 2 * B, B ** 3 + B * B - A * B
            tangent = liquid_mu[1] + fraction * (liquid_mu[0] - liquid_mu[1])
            entropy = sum(z * math.log(z) for z in (fraction, 1 - fraction) if z)
            physical = [z for z in real_roots(a, b, c) if z > B]
            if not physical:
                raise ValueError("Independent cubic has no physical root")
            for z in physical:
                root_checks += 1
                residual = ((z + a) * z + b) * z + c
                scale = 1 + abs(z ** 3) + abs(a * z * z) + abs(b * z) + abs(c)
                cubic_error = max(cubic_error, abs(residual) / scale)
                gibbs = entropy + z - 1 - math.log(z - B)
                gibbs -= A / (2 * math.sqrt(2) * B) * math.log(
                    (z + (1 + math.sqrt(2)) * B) / (z + (1 - math.sqrt(2)) * B))
                gaps.append(gibbs - tangent)
        require_common_tangent(gaps)
        minima.append(min(gaps))
        state_results.append({"temperature_k": temperature, "pressure_bara": pressure / 1e5,
                              "liquid_methane_fraction": x, "vapor_methane_fraction": y,
                              "minimum_gibbs_gap": min(gaps)})
    metrics = [cubic_error, pressure_error, volume_error, lever_error]
    if len(states) != 18 + len(closed_cell_states) or any(not math.isfinite(value) or value > BOUND for value in metrics):
        raise ValueError("Frozen numerical/domain gate failed")
    if not math.isfinite(fugacity_error) or fugacity_error > 2e-9:
        raise ValueError("Fugacity equality failed")
    if not math.isfinite(balance_error) or balance_error / sum(states[0][2]) > BOUND:
        raise ValueError("Global component conservation failed")
    return {"schema": "binary_pr_sampled_stability.v1", "states": state_results,
            "state_count": len(states), "physical_root_checks": root_checks,
            "minimum_gibbs_gap": min(minima), "maximum_normalized_cubic_residual": cubic_error,
            "maximum_phase_pressure_relative_residual": pressure_error,
            "maximum_log_fugacity_residual": fugacity_error,
            "maximum_fixed_volume_relative_residual": volume_error,
            "maximum_lever_rule_relative_residual": lever_error,
            "maximum_global_component_balance_mol": balance_error,
            "source_sha256": hashlib.sha256((FOLDER / "reference.py").read_bytes()).hexdigest(),
            "review_source_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
            "limits": "Sampled frozen path only; no general global-stability or critical-domain proof"}


class IndependentStabilityTests(unittest.TestCase):
    """Retain independent root and admission controls without optimized-away asserts."""

    def test_independent_cubic(self):
        self.assertEqual(sorted(round(z, 12) for z in real_roots(-6, 11, -6)), [1, 2, 3])
        self.assertAlmostEqual(real_roots(0, 0, -8)[0], 2, places=12)
        self.assertEqual(real_roots(0, -3, 2), [-2, 1])

    def test_all_accepted_states(self):
        result = diagnose()
        self.assertEqual(result["state_count"], 18)
        self.assertGreater(result["physical_root_checks"], 18000)

    def test_corrupted_potential_controls(self):
        for values in ([], [float("nan")], [float("inf")], [-0.01]):
            with self.subTest(values=values), self.assertRaises(ValueError):
                require_common_tangent(values)


if __name__ == "__main__":
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(IndependentStabilityTests)
    outcome = unittest.TextTestRunner(verbosity=1).run(suite)
    if not outcome.wasSuccessful():
        raise SystemExit(1)
    print(json.dumps(diagnose(), indent=2, allow_nan=False))
