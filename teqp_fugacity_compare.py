"""Compare NeqSim vs teqp fugacity coefficients at specific VLE state points."""
import teqp
import numpy as np

# SAFT-VR Mie coefficients for CH4 and C2H6
coeffs = [
    {"name": "Methane", "m": 1.0, "sigma_Angstrom": 3.7412, "epsilon_over_k": 153.36,
     "lambda_r": 12.65, "lambda_a": 6.0, "BibTeXKey": "Lafitte-2013"},
    {"name": "Ethane", "m": 1.4373, "sigma_Angstrom": 3.7257, "epsilon_over_k": 206.12,
     "lambda_r": 12.4, "lambda_a": 6.0, "BibTeXKey": "Lafitte-2013"},
]

model = teqp.make_model({'kind': 'SAFT-VR-Mie', 'model': {'coeffs': coeffs}})

T = 250.0  # K
R = 8.314462618

print("=" * 80)
print("Comparison of fugacity coefficients at NeqSim VLE solution state points")
print(f"T = {T} K")
print("=" * 80)

# NeqSim VLE solution at P=30 bar (from test output):
# Gas: x_CH4=0.603, x_C2H6=0.397, Z=0.800, eta=0.031, Vm_neqsim=55.42 → rho=1805
# Liq: x_CH4=0.206, x_C2H6=0.794, Z=0.097, eta=0.298, Vm_neqsim=6.71 → rho=14903

# Phase states from NeqSim
phases = [
    {"name": "Gas (P=30)", "x": np.array([0.603277, 0.396723]),
     "Vm_neqsim": 55.4199, "Z_neqsim": 0.799858,
     "dFdN_neqsim": [-0.32134075, -0.62303441]},
    {"name": "Liq (P=30)", "x": np.array([0.206253, 0.793747]),
     "Vm_neqsim": 6.7059, "Z_neqsim": 0.096784,
     "dFdN_neqsim": [-1.36001332, -3.42851048]},
]

for ph in phases:
    x = ph["x"]
    Vm_SI = ph["Vm_neqsim"] * 1e-5  # m³/mol
    rho = 1.0 / Vm_SI  # mol/m³

    # teqp: alpha_r and derivatives
    z = x  # mole fractions
    rhovec = rho * z  # component densities

    alpha_r = model.get_Ar00(T, rho, z)
    Ar01 = model.get_Ar01(T, rho, z)  # rho * d(alpha_r)/d(rho)
    Z_teqp = 1.0 + Ar01

    # Pressure from EOS
    P_teqp = rho * R * T * Z_teqp  # Pa
    P_bar = P_teqp / 1e5

    # Fugacity coefficients
    lnphi_teqp = np.array(model.get_fugacity_coefficients(T, rhovec))
    phi_teqp = np.exp(lnphi_teqp)

    # dFdN from teqp: ln(phi_i) = dF/dN_i - ln(Z) for constant-T-V derivative
    # So dF/dN_i = ln(phi_i) + ln(Z)
    dFdN_teqp = lnphi_teqp + np.log(Z_teqp)

    # NeqSim values
    Z_neqsim = ph["Z_neqsim"]
    dFdN_neqsim = ph["dFdN_neqsim"]
    lnphi_neqsim = [d - np.log(Z_neqsim) for d in dFdN_neqsim]

    print(f"\n--- {ph['name']} ---")
    print(f"  Composition: x_CH4={x[0]:.6f}, x_C2H6={x[1]:.6f}")
    print(f"  Vm = {Vm_SI:.6e} m³/mol,  rho = {rho:.1f} mol/m³")
    print(f"  P(teqp) = {P_bar:.4f} bar,  P(target) = 30.0 bar")
    print(f"  Z:  teqp={Z_teqp:.8f}  neqsim={Z_neqsim:.8f}  diff={Z_neqsim-Z_teqp:.2e}")
    print(f"  alpha_r = {alpha_r:.10f}")
    for i, name in enumerate(["CH4", "C2H6"]):
        dFdN_err = dFdN_neqsim[i] - dFdN_teqp[i]
        print(f"  {name}:")
        print(f"    dFdN:   teqp={dFdN_teqp[i]:.10f}  neqsim={dFdN_neqsim[i]:.10f}  diff={dFdN_err:.6e}")
        print(f"    lnphi:  teqp={lnphi_teqp[i]:.8f}  neqsim={lnphi_neqsim[i]:.8f}")
        print(f"    phi:    teqp={phi_teqp[i]:.8f}  neqsim={np.exp(lnphi_neqsim[i]):.8f}")

# Also compute the CORRECT teqp VLE at T=250, P=30 bar
print("\n" + "=" * 80)
print("teqp VLE at T=250K, P=30 bar (reference)")
print("=" * 80)

# Bubble point: start from liquid x_CH4=0.165
# We need to solve for VLE numerically
from scipy.optimize import fsolve

def vle_equations(unknowns, T, P, z_feed):
    """VLE: equal fugacity at T,P."""
    x1 = unknowns[0]  # liquid CH4 mole frac
    y1 = unknowns[1]  # vapor CH4 mole frac
    V = unknowns[2]   # vapor phase fraction (not used here, but needed for composition)

    x = np.array([x1, 1-x1])
    y = np.array([y1, 1-y1])

    # Find liquid and vapor densities at these compositions
    # For liquid: guess high density
    rho_liq = find_density(T, P, x, phase='liquid')
    rho_vap = find_density(T, P, y, phase='vapor')

    if rho_liq is None or rho_vap is None:
        return [1e10, 1e10, 1e10]

    lnphi_liq = np.array(model.get_fugacity_coefficients(T, rho_liq * x))
    lnphi_vap = np.array(model.get_fugacity_coefficients(T, rho_vap * y))

    # Equilibrium: x_i * phi_i^L * P = y_i * phi_i^V * P
    # => x_i * exp(lnphi_i^L) = y_i * exp(lnphi_i^V)
    eq1 = x1 * np.exp(lnphi_liq[0]) - y1 * np.exp(lnphi_vap[0])
    eq2 = (1-x1) * np.exp(lnphi_liq[1]) - (1-y1) * np.exp(lnphi_vap[1])

    # Material balance
    eq3 = z_feed - x1 * (1 - V) - y1 * V

    return [eq1, eq2, eq3]

def find_density(T, P, x, phase='liquid'):
    """Find density at given T,P,x for specified phase."""
    from scipy.optimize import brentq

    def pressure_residual(rho):
        if rho < 1:
            return -P
        Ar01 = model.get_Ar01(T, rho, x)
        P_calc = rho * R * T * (1 + Ar01)
        return P_calc - P * 1e5

    if phase == 'liquid':
        # Scan for liquid root (high density)
        try:
            rho = brentq(pressure_residual, 5000, 25000)
            return rho
        except:
            return None
    else:
        # Scan for vapor root (low density)
        try:
            rho = brentq(pressure_residual, 10, 5000)
            return rho
        except:
            return None

# Try to find VLE at several pressures
for P_bar_target in [20, 30, 40, 50, 60]:
    P_Pa = P_bar_target * 1e5
    print(f"\nP = {P_bar_target} bar:")

    # Try different liquid compositions
    best_x1 = None
    best_y1 = None
    best_err = 1e10

    for x1_guess in np.linspace(0.02, 0.8, 40):
        x = np.array([x1_guess, 1-x1_guess])
        rho_liq = find_density(T, P_Pa/1e5, x, 'liquid')
        if rho_liq is None:
            continue
        lnphi_liq = np.array(model.get_fugacity_coefficients(T, rho_liq * x))
        Ki = np.exp(lnphi_liq) / 1.0  # Assuming ideal gas for initial estimate

        # Better: for each x, find the K-values from liquid fugacities
        # y_i = K_i * x_i where K_i = phi_L_i / phi_V_i
        # First pass: estimate y from Raoult's law analog
        y_tmp = x * np.exp(lnphi_liq)
        y_sum = y_tmp.sum()
        if y_sum < 0.01 or y_sum > 100:
            continue
        y = y_tmp / y_sum

        rho_vap = find_density(T, P_Pa/1e5, y, 'vapor')
        if rho_vap is None:
            continue
        lnphi_vap = np.array(model.get_fugacity_coefficients(T, rho_vap * y))

        # Check equilibrium
        fug_liq = x * np.exp(lnphi_liq) * P_Pa
        fug_vap = y * np.exp(lnphi_vap) * P_Pa

        err = np.sum((fug_liq - fug_vap)**2) / np.sum(fug_liq**2)
        if err < best_err:
            best_err = err
            best_x1 = x[0]
            best_y1 = y[0]

    if best_x1 is not None and best_err < 0.01:
        # Refine with iteration
        x = np.array([best_x1, 1-best_x1])
        for _ in range(50):
            rho_liq = find_density(T, P_Pa/1e5, x, 'liquid')
            if rho_liq is None:
                break
            lnphi_liq = np.array(model.get_fugacity_coefficients(T, rho_liq * x))

            y = x * np.exp(lnphi_liq)
            y_sum = y.sum()
            y = y / y_sum

            rho_vap = find_density(T, P_Pa/1e5, y, 'vapor')
            if rho_vap is None:
                break
            lnphi_vap = np.array(model.get_fugacity_coefficients(T, rho_vap * y))

            # Update x from: x_i = y_i * phi_V_i / phi_L_i
            x_new = y * np.exp(lnphi_vap - lnphi_liq)
            x_sum = x_new.sum()
            x_new = x_new / x_sum

            if np.max(np.abs(x_new - x)) < 1e-10:
                break
            x = x_new

        print(f"  x_CH4 = {x[0]:.6f}  y_CH4 = {y[0]:.6f}")

        # Get densities and fugacities at solution
        rho_liq = find_density(T, P_Pa/1e5, x, 'liquid')
        rho_vap = find_density(T, P_Pa/1e5, y, 'vapor')
        lnphi_liq = np.array(model.get_fugacity_coefficients(T, rho_liq * x))
        lnphi_vap = np.array(model.get_fugacity_coefficients(T, rho_vap * y))

        Z_liq = P_Pa / (rho_liq * R * T)
        Z_vap = P_Pa / (rho_vap * R * T)

        dFdN_liq = lnphi_liq + np.log(Z_liq)
        dFdN_vap = lnphi_vap + np.log(Z_vap)

        print(f"  Liquid: rho={rho_liq:.1f} Z={Z_liq:.6f}")
        print(f"    dFdN: CH4={dFdN_liq[0]:.8f}  C2H6={dFdN_liq[1]:.8f}")
        print(f"    lnphi: CH4={lnphi_liq[0]:.8f}  C2H6={lnphi_liq[1]:.8f}")
        print(f"  Vapor:  rho={rho_vap:.1f} Z={Z_vap:.6f}")
        print(f"    dFdN: CH4={dFdN_vap[0]:.8f}  C2H6={dFdN_vap[1]:.8f}")
        print(f"    lnphi: CH4={lnphi_vap[0]:.8f}  C2H6={lnphi_vap[1]:.8f}")
    else:
        print(f"  Could not find VLE (best_err={best_err:.2e})")
