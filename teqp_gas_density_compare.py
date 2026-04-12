"""Compare F/n at gas density rho=1804 for multiple compositions."""
import teqp
import numpy as np

coeffs = [
    {"name": "Methane", "m": 1.0, "sigma_Angstrom": 3.7412, "epsilon_over_k": 153.36,
     "lambda_r": 12.65, "lambda_a": 6.0, "BibTeXKey": "Lafitte-2013"},
    {"name": "Ethane", "m": 1.4373, "sigma_Angstrom": 3.7257, "epsilon_over_k": 206.12,
     "lambda_r": 12.4, "lambda_a": 6.0, "BibTeXKey": "Lafitte-2013"},
]

model = teqp.make_model({'kind': 'SAFT-VR-Mie', 'model': {'coeffs': coeffs}})

T = 250.0
R = 8.314462618

print("=" * 90)
print("teqp reference: F/n and dFdN at gas density rho=1804 mol/m³, T=250K")
print("=" * 90)

rho = 1804.0
print(f"{'x_CH4':>8} {'alpha_r':>14} {'Ar01':>10} {'Z':>10} {'dFdN_CH4':>14} {'dFdN_C2H6':>14} {'lnphi_CH4':>12} {'lnphi_C2H6':>12}")

for x1 in [0.001, 0.05, 0.10, 0.20, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90, 0.999]:
    z = np.array([x1, 1.0 - x1])
    rhovec = rho * z

    alpha_r = model.get_Ar00(T, rho, z)
    Ar01 = model.get_Ar01(T, rho, z)
    Z = 1.0 + Ar01

    lnphi = np.array(model.get_fugacity_coefficients(T, rhovec))
    dFdN = lnphi + np.log(Z)

    print(f"{x1:8.3f} {alpha_r:14.8f} {Ar01:10.6f} {Z:10.6f} {dFdN[0]:14.8f} {dFdN[1]:14.8f} {lnphi[0]:12.6f} {lnphi[1]:12.6f}")

# Also at liquid density
print("\n" + "=" * 90)
print("teqp reference: F/n and dFdN at liquid density rho=14481 mol/m³, T=250K")
print("=" * 90)
rho2 = 14481.0
print(f"{'x_CH4':>8} {'alpha_r':>14} {'Ar01':>10} {'Z':>10} {'dFdN_CH4':>14} {'dFdN_C2H6':>14}")

for x1 in [0.001, 0.05, 0.10, 0.20, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90, 0.999]:
    z = np.array([x1, 1.0 - x1])
    rhovec = rho2 * z

    alpha_r = model.get_Ar00(T, rho2, z)
    Ar01 = model.get_Ar01(T, rho2, z)
    Z = 1.0 + Ar01

    lnphi = np.array(model.get_fugacity_coefficients(T, rhovec))
    dFdN = lnphi + np.log(Z)

    print(f"{x1:8.3f} {alpha_r:14.8f} {Ar01:10.6f} {Z:10.6f} {dFdN[0]:14.8f} {dFdN[1]:14.8f}")

# Compute numerical dFdN at rho=1804, x=0.6 from composition perturbation
print("\n=== Numerical dFdN at gas state: rho=1804, x_CH4=0.6 ===")
x1 = 0.6
h = 1e-7
n = 1.0  # total moles
V_total = n / rho  # m³

# F(n_CH4+h, n_C2H6) with V_total fixed
n_new = n + h
rho_new = n_new / V_total
z_new = np.array([(x1*n + h)/n_new, (1-x1)*n/n_new])
alpha_new = model.get_Ar00(T, rho_new, z_new)
F_plus = n_new * alpha_new

# F(n_CH4-h, n_C2H6)
n_new2 = n - h
rho_new2 = n_new2 / V_total
z_new2 = np.array([(x1*n - h)/n_new2, (1-x1)*n/n_new2])
alpha_new2 = model.get_Ar00(T, rho_new2, z_new2)
F_minus = n_new2 * alpha_new2

# Base
alpha_base = model.get_Ar00(T, rho, np.array([x1, 1-x1]))
F_base = n * alpha_base

dFdN_num = (F_plus - F_minus) / (2*h)
print(f"  F_base = {F_base:.12f}")
print(f"  F_plus = {F_plus:.12f}")
print(f"  F_minus= {F_minus:.12f}")
print(f"  dFdN_CH4(numerical) = {dFdN_num:.10f}")

# Compare with the analytical (from get_fugacity_coefficients)
lnphi = np.array(model.get_fugacity_coefficients(T, rho * np.array([x1, 1-x1])))
Ar01 = model.get_Ar01(T, rho, np.array([x1, 1-x1]))
Z = 1 + Ar01
dFdN_anal = lnphi + np.log(Z)
print(f"  dFdN_CH4(analytical) = {dFdN_anal[0]:.10f}")
print(f"  alpha_r = {alpha_base:.12f}")
print(f"  Z = {Z:.10f}")
