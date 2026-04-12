"""Compare alpha_r(T,rho,x) between teqp and NeqSim SAFT-VR Mie at multiple compositions."""
import teqp
import numpy as np

coeffs = [
    {'name': 'methane', 'm': 1.0, 'sigma_m': 3.7412e-10,
     'epsilon_over_k': 153.36, 'lambda_r': 12.65, 'lambda_a': 6.0,
     'BibTeXKey': 'Lafitte2013'},
    {'name': 'ethane', 'm': 1.4373, 'sigma_m': 3.7257e-10,
     'epsilon_over_k': 206.12, 'lambda_r': 12.4, 'lambda_a': 6.0,
     'BibTeXKey': 'Lafitte2013'}
]
model = teqp.make_model({'kind': 'SAFT-VR-Mie', 'model': {'coeffs': coeffs}})

T = 250.0
rho = 14481.0  # mol/m3

print("T=250K, rho=14481 mol/m3")
print(f"{'x_CH4':>8s} {'alpha_r':>14s} {'Ar01':>12s} {'P_bar':>10s}")

xvals = [0.0001, 0.05, 0.10, 0.20, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90, 0.9999]
for x1 in xvals:
    z = np.array([x1, 1.0 - x1])
    alpha_r = model.get_Ar00(T, rho, z)
    Ar01 = model.get_Ar01(T, rho, z)
    P = rho * 8.314462618 * T * (1.0 + Ar01)
    print(f"{x1:8.4f} {alpha_r:14.8f} {Ar01:12.6f} {P / 1e5:10.4f}")

# Also compute dFdN via numerical perturbation
print("\n=== dFdN at x=[0.3, 0.7] via direct perturbation ===")
x1_base = 0.30
z_base = np.array([x1_base, 1.0 - x1_base])
n = 1.0  # 1 mole total
V = n / rho  # total volume in m3

h = 1e-8
F_base = n * model.get_Ar00(T, rho, z_base)

# dFdN for CH4
n_plus = np.array([0.3 + h, 0.7])
rho_plus = sum(n_plus) / V
z_plus = n_plus / sum(n_plus)
F_plus = sum(n_plus) * model.get_Ar00(T, rho_plus, z_plus)

n_minus = np.array([0.3 - h, 0.7])
rho_minus = sum(n_minus) / V
z_minus = n_minus / sum(n_minus)
F_minus = sum(n_minus) * model.get_Ar00(T, rho_minus, z_minus)

dFdN0 = (F_plus - F_minus) / (2 * h)
print(f"F(base) = {F_base:.10f}")
print(f"dFdN_CH4 = {dFdN0:.10f}")

# dFdN for C2H6
n_plus1 = np.array([0.3, 0.7 + h])
rho_plus1 = sum(n_plus1) / V
z_plus1 = n_plus1 / sum(n_plus1)
F_plus1 = sum(n_plus1) * model.get_Ar00(T, rho_plus1, z_plus1)

n_minus1 = np.array([0.3, 0.7 - h])
rho_minus1 = sum(n_minus1) / V
z_minus1 = n_minus1 / sum(n_minus1)
F_minus1 = sum(n_minus1) * model.get_Ar00(T, rho_minus1, z_minus1)

dFdN1 = (F_plus1 - F_minus1) / (2 * h)
print(f"dFdN_C2H6 = {dFdN1:.10f}")

# Summary comparison
print("\n=== Comparison at x=[0.3, 0.7] ===")
print(f"NeqSim F/n = -1.72384324  | teqp alpha_r = {model.get_Ar00(T, rho, z_base):.8f}")
print(f"NeqSim dFdN_CH4 = -0.4455 | teqp dFdN_CH4 = {dFdN0:.6f}")
print(f"NeqSim dFdN_C2H6 = -2.025 | teqp dFdN_C2H6 = {dFdN1:.6f}")
