"""Quick check: teqp fugacity coefficients vs numerical for pure methane."""
import teqp
import numpy as np

# Pure methane 1-component model
coeffs_pure = [{"name": "Methane", "m": 1.0, "sigma_Angstrom": 3.7412, "epsilon_over_k": 153.36,
     "lambda_r": 12.65, "lambda_a": 6.0, "BibTeXKey": "Lafitte-2013"}]

model_pure = teqp.make_model({'kind': 'SAFT-VR-Mie', 'model': {'coeffs': coeffs_pure}})

T = 250.0
rho = 1804.0
z = np.array([1.0])

alpha_r = model_pure.get_Ar00(T, rho, z)
Ar01 = model_pure.get_Ar01(T, rho, z)
Z = 1 + Ar01

# Analytical: pure component lnphi = alpha_r + (Z-1) - ln(Z)
lnphi_formula = alpha_r + (Z - 1) - np.log(Z)

# From teqp
lnphi_teqp = np.array(model_pure.get_fugacity_coefficients(T, np.array([rho])))[0]

# Numerical dFdN
h = 1e-7
V = 1.0/rho
F_base = 1.0 * alpha_r
# F(n+h): rho_new = (1+h)/V = rho*(1+h)
alpha_plus = model_pure.get_Ar00(T, rho*(1+h), z)
F_plus = (1+h) * alpha_plus
alpha_minus = model_pure.get_Ar00(T, rho*(1-h), z)
F_minus = (1-h) * alpha_minus
dFdN_num = (F_plus - F_minus) / (2*h)
lnphi_num = dFdN_num - np.log(Z)

print("=== Pure Methane at T=250K, rho=1804 mol/m3 ===")
print(f"  alpha_r = {alpha_r:.10f}")
print(f"  Ar01 = {Ar01:.10f}, Z = {Z:.10f}")
print(f"  lnphi (formula: a+Z-1-ln(Z)) = {lnphi_formula:.10f}")
print(f"  lnphi (teqp API)              = {lnphi_teqp:.10f}")
print(f"  lnphi (numerical dFdN-ln(Z))  = {lnphi_num:.10f}")
print(f"  dFdN_numerical = {dFdN_num:.10f}")

# Now test 2-component model with near-pure methane
print("\n=== 2-component model, near-pure methane ===")
coeffs_binary = [
    {"name": "Methane", "m": 1.0, "sigma_Angstrom": 3.7412, "epsilon_over_k": 153.36,
     "lambda_r": 12.65, "lambda_a": 6.0, "BibTeXKey": "Lafitte-2013"},
    {"name": "Ethane", "m": 1.4373, "sigma_Angstrom": 3.7257, "epsilon_over_k": 206.12,
     "lambda_r": 12.4, "lambda_a": 6.0, "BibTeXKey": "Lafitte-2013"},
]
model_bin = teqp.make_model({'kind': 'SAFT-VR-Mie', 'model': {'coeffs': coeffs_binary}})

x_CH4 = 0.999
z_bin = np.array([x_CH4, 1-x_CH4])
rhovec = rho * z_bin

alpha_r_bin = model_bin.get_Ar00(T, rho, z_bin)
Ar01_bin = model_bin.get_Ar01(T, rho, z_bin)
Z_bin = 1 + Ar01_bin
lnphi_bin = np.array(model_bin.get_fugacity_coefficients(T, rhovec))

print(f"  alpha_r = {alpha_r_bin:.10f} (pure: {alpha_r:.10f})")
print(f"  Z = {Z_bin:.10f} (pure: {Z:.10f})")
print(f"  lnphi_CH4(teqp API) = {lnphi_bin[0]:.10f}")
print(f"  lnphi_CH4(pure formula) = {lnphi_formula:.10f}")
print(f"  DIFFERENCE = {lnphi_bin[0] - lnphi_formula:.6e}")

# Try numerical dFdN for 2-component model
n = 1.0
V_total = n / rho
h2 = 1e-7
n_new = n + h2
rho_new = n_new / V_total
z_new = np.array([(x_CH4*n + h2)/n_new, ((1-x_CH4)*n)/n_new])
alpha_new = model_bin.get_Ar00(T, rho_new, z_new)
F_plus2 = n_new * alpha_new

n_new2 = n - h2
rho_new2 = n_new2 / V_total
z_new2 = np.array([(x_CH4*n - h2)/n_new2, ((1-x_CH4)*n)/n_new2])
alpha_new2 = model_bin.get_Ar00(T, rho_new2, z_new2)
F_minus2 = n_new2 * alpha_new2

dFdN_bin_num = (F_plus2 - F_minus2) / (2*h2)
lnphi_bin_num = dFdN_bin_num - np.log(Z_bin)
print(f"  lnphi_CH4(numerical) = {lnphi_bin_num:.10f}")
print(f"  dFdN_CH4(numerical)  = {dFdN_bin_num:.10f}")
