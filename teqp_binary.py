"""Generate teqp reference data for CH4/C2H6 binary VLE.

Uses trace_VLE_isotherm_binary to trace the full Pxy diagram
starting from the pure ethane endpoint.
"""
import teqp
import numpy as np

# SAFT-VR Mie parameters
coeffs = [
    {'name': 'methane', 'm': 1.0, 'sigma_m': 3.7412e-10,
     'epsilon_over_k': 153.36, 'lambda_r': 12.65, 'lambda_a': 6.0,
     'BibTeXKey': 'Lafitte2013'},
    {'name': 'ethane', 'm': 1.4373, 'sigma_m': 3.7257e-10,
     'epsilon_over_k': 206.12, 'lambda_r': 12.4, 'lambda_a': 6.0,
     'BibTeXKey': 'Lafitte2013'}
]

# Binary model
model = teqp.make_model({
    'kind': 'SAFT-VR-Mie',
    'model': {'coeffs': coeffs}
})

# Pure ethane model for initial guess
model_C2 = teqp.make_model({
    'kind': 'SAFT-VR-Mie',
    'model': {'coeffs': [coeffs[1]]}
})

R = 8.314462618


def get_pressure(m, T, rhovec):
    """Compute pressure from (T, rhovec) using teqp."""
    rhotot = sum(rhovec)
    x = np.array(rhovec) / rhotot if rhotot > 0 else np.array(rhovec)
    return rhotot * R * T * (1.0 + m.get_Ar01(T, rhotot, x))


def trace_isotherm(T, label=""):
    """Trace binary VLE isotherm at temperature T starting from pure C2H6."""
    print("=== Binary CH4/C2H6 VLE at T={:.1f}K (teqp SAFT-VR Mie) {} ===".format(T, label))

    # Step 1: Pure C2H6 VLE at T
    # Initial guesses for C2H6 densities (mol/m3)
    rhoL_guess = 15000.0  # liquid
    rhoV_guess = 50.0     # vapor
    try:
        rhoL_pure, rhoV_pure = model_C2.pure_VLE_T(T, rhoL_guess, rhoV_guess, 200)
    except Exception as e:
        print("  Pure C2H6 VLE failed at T={}: {}".format(T, e))
        return

    P_pure = rhoL_pure * R * T * (1.0 + model_C2.get_Ar01(T, rhoL_pure, np.array([1.0])))
    print("  Pure C2H6: rhoL={:.2f}, rhoV={:.4f} mol/m3, P={:.4f} bar".format(
        rhoL_pure, rhoV_pure, P_pure / 1e5))

    # Step 2: Start trace from pure C2H6 (component index 1)
    # rhovecL0 = [tiny CH4, big C2H6], rhovecV0 = [tiny CH4, small C2H6]
    eps = 1e-6  # tiny amount of CH4
    rhovecL0 = np.array([eps * rhoL_pure, (1 - eps) * rhoL_pure])
    rhovecV0 = np.array([eps * rhoV_pure, (1 - eps) * rhoV_pure])

    try:
        o = model.trace_VLE_isotherm_binary(T, rhovecL0, rhovecV0)
    except Exception as e:
        print("  Trace failed: {}".format(e))
        return []

    # o is a list of dicts, each with 'rhoL / mol/m^3', 'rhoV / mol/m^3', 'pL / Pa'
    print("{:>10s} {:>12s} {:>10s} {:>12s} {:>12s}".format(
        "x_C1", "y_C1", "P_bar", "rhoL_tot", "rhoV_tot"))

    results = []
    prev_x = -1.0
    for pt in o:
        rhoL_vec = pt['rhoL / mol/m^3']
        rhoV_vec = pt['rhoV / mol/m^3']
        pL = pt['pL / Pa']
        rhoL_tot = sum(rhoL_vec)
        rhoV_tot = sum(rhoV_vec)
        if rhoL_tot < 1e-10 or rhoV_tot < 1e-10:
            continue
        x_C1 = rhoL_vec[0] / rhoL_tot
        y_C1 = rhoV_vec[0] / rhoV_tot
        P = pL / 1e5  # Pa to bar

        # Only print when x_C1 changes significantly (avoid dense output)
        if abs(x_C1 - prev_x) > 0.01 or len(results) == 0:
            print("{:10.6f} {:12.6f} {:10.4f} {:12.2f} {:12.2f}".format(
                x_C1, y_C1, P, rhoL_tot, rhoV_tot))
            prev_x = x_C1
            results.append((x_C1, y_C1, P, rhoL_tot, rhoV_tot))

    print()
    return results


# Trace at T=200K and T=250K
for T in [200.0, 250.0]:
    trace_isotherm(T)
