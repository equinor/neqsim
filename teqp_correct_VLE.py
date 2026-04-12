"""Compute correct binary CH4/C2H6 VLE using teqp's Ar00/Ar01 (not buggy fugacity_coefficients)."""
import teqp
import numpy as np
from scipy.optimize import brentq

coeffs = [
    {"name": "Methane", "m": 1.0, "sigma_Angstrom": 3.7412, "epsilon_over_k": 153.36,
     "lambda_r": 12.65, "lambda_a": 6.0, "BibTeXKey": "Lafitte-2013"},
    {"name": "Ethane", "m": 1.4373, "sigma_Angstrom": 3.7257, "epsilon_over_k": 206.12,
     "lambda_r": 12.4, "lambda_a": 6.0, "BibTeXKey": "Lafitte-2013"},
]

model = teqp.make_model({'kind': 'SAFT-VR-Mie', 'model': {'coeffs': coeffs}})
R = 8.314462618

def compute_lnphi_numerical(T, rho, z, model):
    """Compute ln(phi) for each component using numerical dFdN from Ar00."""
    n = 1.0
    V = n / rho
    alpha_base = model.get_Ar00(T, rho, z)
    F_base = n * alpha_base

    Ar01 = model.get_Ar01(T, rho, z)
    Z = 1.0 + Ar01

    h = 1e-7
    nc = len(z)
    lnphi = np.zeros(nc)

    for i in range(nc):
        # F(n_i + h)
        n_plus = n + h
        n_vec_p = z * n
        n_vec_p[i] += h
        x_p = n_vec_p / n_plus
        rho_p = n_plus / V
        alpha_p = model.get_Ar00(T, rho_p, x_p)
        F_plus = n_plus * alpha_p

        # F(n_i - h)
        n_minus = n - h
        n_vec_m = z * n
        n_vec_m[i] -= h
        x_m = n_vec_m / n_minus
        rho_m = n_minus / V
        alpha_m = model.get_Ar00(T, rho_m, x_m)
        F_minus = n_minus * alpha_m

        dFdN = (F_plus - F_minus) / (2 * h)
        lnphi[i] = dFdN - np.log(Z)

    return lnphi

def find_density(T, P_Pa, z, model, phase='liquid'):
    """Find molar density at given T, P, z."""
    def pressure_resid(rho):
        if rho <= 0:
            return -P_Pa
        try:
            Ar01 = model.get_Ar01(T, rho, z)
            P_calc = rho * R * T * (1.0 + Ar01)
            return P_calc - P_Pa
        except:
            return -P_Pa

    if phase == 'liquid':
        try:
            rho = brentq(pressure_resid, 5000, 25000, xtol=1e-6)
            return rho
        except:
            return None
    else:
        try:
            rho = brentq(pressure_resid, 10, 5000, xtol=1e-6)
            return rho
        except:
            return None

def solve_VLE(T, P_bar, model, x_init=0.15, max_iter=200, tol=1e-10):
    """Solve binary VLE at T, P using numerical fugacity coefficients."""
    P_Pa = P_bar * 1e5
    x = np.array([x_init, 1-x_init])

    for iteration in range(max_iter):
        rho_liq = find_density(T, P_Pa, x, model, 'liquid')
        if rho_liq is None:
            return None, None, "No liquid root"

        lnphi_liq = compute_lnphi_numerical(T, rho_liq, x, model)

        # Compute K-values: K_i = phi_L_i / phi_V_i, initial guess from Raoult
        y_raw = x * np.exp(lnphi_liq)
        y_sum = y_raw.sum()
        y = y_raw / y_sum

        rho_vap = find_density(T, P_Pa, y, model, 'vapor')
        if rho_vap is None:
            return None, None, "No vapor root"

        lnphi_vap = compute_lnphi_numerical(T, rho_vap, y, model)

        # Update x: x_i = y_i * phi_V_i / phi_L_i
        K = np.exp(lnphi_liq - lnphi_vap)
        x_new = y / K
        x_new = x_new / x_new.sum()

        change = np.max(np.abs(x_new - x))
        x = x_new

        if change < tol:
            return x, y, "converged"

    return x, y, f"not converged (change={change:.2e})"

print("=" * 80)
print("CORRECT binary CH4/C2H6 VLE using numerical fugacity coefficients")
print("=" * 80)

T = 250.0
print(f"\nT = {T} K\n")
print(f"{'P(bar)':>8} {'x_CH4':>10} {'y_CH4':>10} {'K_CH4':>10} {'K_C2H6':>10} {'status':>10}")

for P_bar in [13.5, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70]:
    x_guess = min(max((P_bar - 13) / 80, 0.02), 0.6)
    x, y, status = solve_VLE(T, P_bar, model, x_init=x_guess)
    if x is not None:
        K_CH4 = y[0] / x[0] if x[0] > 0 else float('nan')
        K_C2H6 = y[1] / x[1] if x[1] > 0 else float('nan')
        print(f"{P_bar:8.1f} {x[0]:10.6f} {y[0]:10.6f} {K_CH4:10.4f} {K_C2H6:10.4f} {status:>10}")
    else:
        print(f"{P_bar:8.1f} {'---':>10} {'---':>10} {'---':>10} {'---':>10} {status:>10}")
