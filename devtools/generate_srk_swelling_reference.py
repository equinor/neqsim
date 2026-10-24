"""Generate an independent, synthetic SRK bubble-branch swelling benchmark.

Uses only Python's standard library, with no NeqSim imports or fitted data.
Equation: Soave (1972), DOI 10.1016/0009-2509(72)80096-4, classical quadratic
attraction / linear covolume mixing, kij=0 and no volume translation.
The pure-component parameters below are frozen from COMP.csv at NeqSim commit
98d185ded48c5c0c12b2faec374544b6b6b043b1. This checks numerical agreement for
identical model inputs; it is not an experimental reservoir-fluid benchmark.

Run from any directory; JSON is written to stdout. The liquid composition is
fixed at each addition and incipient vapor composition is iterated at a trial
pressure. A bracketed pressure root enforces sum(x_i K_i)=1. Both fugacity and
EOS pressure residuals are reported to reject a trivial or spurious solution.
"""

import json
import math

R = 8.3144621  # J/(mol K), same numerical constant as the Java benchmark
TEMPERATURE_K = 353.15
TC_K = (190.56, 617.70)
PC_PA = (45.99e5, 21.1e5)
ACENTRIC = (0.0115, 0.4923)


def cube_root(value):
    """Return the real cube root, including for negative arguments."""
    return math.copysign(abs(value) ** (1.0 / 3.0), value)


def compressibility_roots(attraction, covolume):
    """Solve the SRK compressibility polynomial analytically."""
    linear = attraction - covolume - covolume**2
    constant = -attraction * covolume
    depressed_p = linear - 1.0 / 3.0
    depressed_q = -2.0 / 27.0 + linear / 3.0 + constant
    discriminant = (depressed_q / 2.0) ** 2 + (depressed_p / 3.0) ** 3
    if discriminant >= 0.0:
        root = math.sqrt(discriminant)
        values = [cube_root(-depressed_q / 2.0 + root)
                  + cube_root(-depressed_q / 2.0 - root) + 1.0 / 3.0]
    else:
        radius = 2.0 * math.sqrt(-depressed_p / 3.0)
        argument = -depressed_q / (2.0 * math.sqrt(-(depressed_p / 3.0) ** 3))
        angle = math.acos(max(-1.0, min(1.0, argument))) / 3.0
        values = [radius * math.cos(angle - 2.0 * math.pi * k / 3.0) + 1.0 / 3.0
                  for k in range(3)]
    return sorted(value for value in values if value > covolume)


def phase(composition, pressure_pa, vapor):
    """Calculate SRK fugacity coefficients and molar volume from cubic roots."""
    root_two = 2.0 ** (1.0 / 3.0)
    omega_a = 1.0 / (9.0 * (root_two - 1.0))
    omega_b = (root_two - 1.0) / 3.0
    pure_a = []
    pure_b = []
    for tc, pc, acentric in zip(TC_K, PC_PA, ACENTRIC):
        m = 0.48 + 1.574 * acentric - 0.176 * acentric**2
        alpha = (1.0 + m * (1.0 - math.sqrt(TEMPERATURE_K / tc))) ** 2
        pure_a.append(omega_a * R**2 * tc**2 / pc * alpha)
        pure_b.append(omega_b * R * tc / pc)
    cross_a = [[math.sqrt(ai * aj) for aj in pure_a] for ai in pure_a]
    mixed_a = sum(composition[i] * composition[j] * cross_a[i][j]
                  for i in range(2) for j in range(2))
    mixed_b = sum(x * b for x, b in zip(composition, pure_b))
    attraction = mixed_a * pressure_pa / (R * TEMPERATURE_K) ** 2
    covolume = mixed_b * pressure_pa / (R * TEMPERATURE_K)
    roots = compressibility_roots(attraction, covolume)
    z_factor = roots[-1] if vapor else roots[0]
    log_phi = []
    for i in range(2):
        partial_a = sum(composition[j] * cross_a[i][j] for j in range(2))
        b_ratio = pure_b[i] / mixed_b
        log_phi.append(b_ratio * (z_factor - 1.0) - math.log(z_factor - covolume)
                       - attraction / covolume * (2.0 * partial_a / mixed_a - b_ratio)
                       * math.log1p(covolume / z_factor))
    volume = z_factor * R * TEMPERATURE_K / pressure_pa
    eos_pressure = (R * TEMPERATURE_K / (volume - mixed_b)
                    - mixed_a / (volume * (volume + mixed_b)))
    residual = abs(eos_pressure / pressure_pa - 1.0)
    return log_phi, volume, residual


def bubble_residual(liquid, pressure_pa):
    """Iterate incipient vapor and return the unnormalized vapor mole sum."""
    log_liquid, liquid_volume, liquid_residual = phase(liquid, pressure_pa, False)
    vapor = [0.999, 0.001]
    for _ in range(1000):
        log_vapor, _, _ = phase(vapor, pressure_pa, True)
        unnormalized = [liquid[i] * math.exp(log_liquid[i] - log_vapor[i])
                        for i in range(2)]
        total = sum(unnormalized)
        updated = [value / total for value in unnormalized]
        error = max(abs(a - b) for a, b in zip(vapor, updated))
        vapor = updated
        if error < 1.0e-14:
            break
    else:
        raise ArithmeticError("Incipient vapor iteration did not converge")
    log_vapor, vapor_volume, vapor_residual = phase(vapor, pressure_pa, True)
    fugacity_residual = max(abs(math.log(liquid[i] / vapor[i])
                               + log_liquid[i] - log_vapor[i]) for i in range(2))
    if abs(vapor[0] - liquid[0]) < 0.01:
        raise ArithmeticError("Trivial equal-composition solution")
    return (total - 1.0, liquid_volume, fugacity_residual,
            max(liquid_residual, vapor_residual), vapor_volume)


def reference_points():
    """Calculate a fixed-temperature methane-addition series on the bubble branch."""
    results = []
    original_volume = None
    for added_percent in (0.0, 10.0, 20.0):
        total_moles = 1.0 + added_percent / 100.0
        liquid = [(0.2 + added_percent / 100.0) / total_moles, 0.8 / total_moles]
        lower, upper = 1.0e5, 100.0e5
        if not bubble_residual(liquid, lower)[0] > 0.0:
            raise ArithmeticError("Lower pressure does not bracket the bubble point")
        if not bubble_residual(liquid, upper)[0] < 0.0:
            raise ArithmeticError("Upper pressure does not bracket the bubble point")
        for _ in range(100):
            midpoint = (lower + upper) / 2.0
            if bubble_residual(liquid, midpoint)[0] > 0.0:
                lower = midpoint
            else:
                upper = midpoint
            if upper - lower < 1.0e-5:
                break
        pressure = (lower + upper) / 2.0
        _, molar_volume, fugacity_error, eos_error, vapor_volume = bubble_residual(liquid, pressure)
        volume = molar_volume * total_moles
        if original_volume is None:
            original_volume = volume
        assert fugacity_error < 1.0e-10
        assert eos_error < 1.0e-10
        assert vapor_volume > molar_volume
        results.append({"added_gas_mol_percent_of_original_oil": added_percent,
                        "pressure_bara": pressure / 1.0e5,
                        "swelling_factor": volume / original_volume,
                        "liquid_volume_m3": volume,
                        "max_log_fugacity_residual": fugacity_error,
                        "max_relative_eos_pressure_residual": eos_error})
    return results


if __name__ == "__main__":
    print(json.dumps({"temperature_K": TEMPERATURE_K,
                      "components": ["methane", "nC10"],
                      "initial_moles": [0.2, 0.8],
                      "kij": 0.0,
                      "volume_translation": False,
                      "points": reference_points()}, indent=2))
