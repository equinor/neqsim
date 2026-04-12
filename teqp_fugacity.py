"""Compare SAFT-VR Mie fugacity coefficients: teqp vs NeqSim."""
import teqp
import numpy as np

# Methane/Ethane SAFT-VR Mie parameters (Lafitte 2013)
coeffs = [
    {'name': 'methane', 'm': 1.0, 'sigma_m': 3.7412e-10,
     'epsilon_over_k': 153.36, 'lambda_r': 12.65, 'lambda_a': 6.0,
     'BibTeXKey': 'Lafitte2013'},
    {'name': 'ethane', 'm': 1.4373, 'sigma_m': 3.7257e-10,
     'epsilon_over_k': 206.12, 'lambda_r': 12.4, 'lambda_a': 6.0,
     'BibTeXKey': 'Lafitte2013'}
]
model = teqp.make_model({
    'kind': 'SAFT-VR-Mie',
    'model': {'coeffs': coeffs}
})

T = 250.0  # K
x = np.array([0.30, 0.70])  # liquid composition

# Find density at given T, P for liquid phase
pressures = [15, 20, 30, 40, 50, 60]

print("T=250K, x_CH4=0.30, x_C2H6=0.70")
print(f"{'P_bar':>8s} {'rhoL_mol/m3':>14s} {'lnphi_CH4':>12s} {'lnphi_C2H6':>12s} {'phi_CH4':>10s} {'phi_C2H6':>10s} {'Z':>8s}")

for P_bar in pressures:
    P_Pa = P_bar * 1e5

    # Get liquid density
    try:
        # Use solver to find densities
        rhoL = model.solver_rhoT_Tp(T, P_Pa, x)  # liquid density
    except:
        try:
            # Alternative: iterate to find density
            # Start from high density guess
            rho_guess = 15000.0  # mol/m^3 (liquid-like)
            from scipy.optimize import brentq

            def pressure_error(rho):
                z = np.array([0.3, 0.7])
                p_calc = rho * 8.314 * T * (1.0 + model.get_Ar01(T, rho, z))
                return p_calc - P_Pa

            # Scan for liquid root (high density)
            rho_low, rho_high = 5000.0, 25000.0
            rhoL = brentq(pressure_error, rho_low, rho_high)
        except Exception as e:
            print(f"{P_bar:>8.0f} Failed to find liquid density: {e}")
            continue

    # Compute fugacity coefficients
    ln_phi = model.get_ln_fugacity_coefficients(T, rhoL, x)
    Z = P_Pa / (rhoL * 8.314 * T)

    print(f"{P_bar:>8.0f} {rhoL:>14.2f} {ln_phi[0]:>12.6f} {ln_phi[1]:>12.6f} {np.exp(ln_phi[0]):>10.4f} {np.exp(ln_phi[1]):>10.4f} {Z:>8.6f}")

# Also compute for gas phase at P=30 bar
print("\nGas phase at P=30 bar:")
try:
    # Low density guess for gas
    from scipy.optimize import brentq

    def pressure_error_gas(rho):
        z = np.array([0.3, 0.7])
        p_calc = rho * 8.314 * T * (1.0 + model.get_Ar01(T, rho, z))
        return p_calc - 30.0e5

    rho_low, rho_high = 100.0, 5000.0
    rhoV = brentq(pressure_error_gas, rho_low, rho_high)
    ln_phi_gas = model.get_ln_fugacity_coefficients(T, rhoV, x)
    Z_gas = 30.0e5 / (rhoV * 8.314 * T)
    print(f"rhoV={rhoV:.2f} mol/m3, Z={Z_gas:.6f}")
    print(f"ln_phi_CH4={ln_phi_gas[0]:.6f}, ln_phi_C2H6={ln_phi_gas[1]:.6f}")
    print(f"phi_CH4={np.exp(ln_phi_gas[0]):.6f}, phi_C2H6={np.exp(ln_phi_gas[1]):.6f}")
except Exception as e:
    print(f"Failed: {e}")

# Bubble point for x_CH4=0.30
print("\n=== Bubble point calculation ===")
x_liq = np.array([0.30, 0.70])
from scipy.optimize import brentq

# Simple bubble P iteration
P = 13.0e5  # Start near ethane Psat
for iteration in range(50):
    P_Pa = P

    # Liquid density
    def perr_liq(rho):
        return rho * 8.314 * T * (1.0 + model.get_Ar01(T, rho, x_liq)) - P_Pa
    try:
        rhoL = brentq(perr_liq, 5000.0, 25000.0)
    except:
        break

    # Gas density
    def perr_gas(rho):
        return rho * 8.314 * T * (1.0 + model.get_Ar01(T, rho, x_liq)) - P_Pa
    try:
        rhoV = brentq(perr_gas, 10.0, 4000.0)
    except:
        break

    ln_phi_L = model.get_ln_fugacity_coefficients(T, rhoL, x_liq)
    ln_phi_V = model.get_ln_fugacity_coefficients(T, rhoV, x_liq)

    K = np.exp(ln_phi_L - ln_phi_V)
    y = K * x_liq
    ytotal = np.sum(y)

    if iteration < 10 or iteration % 5 == 0:
        print(f"  iter={iteration:3d} P={P/1e5:8.3f} bar  K_CH4={K[0]:.4f} K_C2H6={K[1]:.4f} ytot={ytotal:.6f}")

    if abs(ytotal - 1.0) < 1e-8:
        print(f"  CONVERGED at P={P/1e5:.4f} bar")
        print(f"  y_CH4={y[0]/ytotal:.4f}, y_C2H6={y[1]/ytotal:.4f}")
        break

    # Update gas composition for next iteration (not needed for bubble point - same x in both phases)
    P = P * ytotal
