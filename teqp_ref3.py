"""Check teqp's built-in methane parameters and compare."""
import teqp
import numpy as np

# Try built-in methane
model_builtin = teqp.make_model({'kind': 'SAFT-VR-Mie', 'model': {'names': ['Methane']}})
z = np.array([1.0])
R = 8.314462618

# Get critical point for built-in
Tc_bi, rhoc_bi = model_builtin.solve_pure_critical(200.0, 10000.0)
Pc_bi = (1 + model_builtin.get_Ar01(Tc_bi, rhoc_bi, z)) * rhoc_bi * R * Tc_bi
print(f"Built-in methane: Tc={Tc_bi:.2f} K, Pc={Pc_bi/1e5:.2f} bar")

# VLE at several temperatures
print("Built-in model saturation:")
for T_vle in [120.0, 130.0, 140.0, 150.0, 160.0, 170.0, 180.0]:
    try:
        rhoL, rhoV = model_builtin.pure_VLE_T(T_vle, 25000.0, 100.0, 50)
        Psat = (1 + model_builtin.get_Ar01(T_vle, rhoL, z)) * rhoL * R * T_vle
        print(f"  T={T_vle:.0f}K: Psat={Psat/1e5:.4f} bar")
    except Exception as e:
        print(f"  T={T_vle:.0f}K: FAILED - {e}")

# Now try with our custom parameters
spec = {
    'kind': 'SAFT-VR-Mie',
    'model': {
        'coeffs': [{
            'name': 'methane',
            'm': 1.0,
            'sigma_Angstrom': 3.7412,
            'epsilon_over_k': 153.36,
            'lambda_r': 12.65,
            'lambda_a': 6.0,
            'BibTeXKey': 'Lafitte2013'
        }]
    }
}
model_custom = teqp.make_model(spec)
Tc_cu, rhoc_cu = model_custom.solve_pure_critical(200.0, 10000.0)
Pc_cu = (1 + model_custom.get_Ar01(Tc_cu, rhoc_cu, z)) * rhoc_cu * R * Tc_cu
print(f"\nCustom methane: Tc={Tc_cu:.2f} K, Pc={Pc_cu/1e5:.2f} bar")

print("\nCustom model saturation:")
for T_vle in [120.0, 130.0, 140.0, 150.0, 160.0, 170.0, 180.0]:
    try:
        rhoL, rhoV = model_custom.pure_VLE_T(T_vle, 25000.0, 100.0, 50)
        Psat = (1 + model_custom.get_Ar01(T_vle, rhoL, z)) * rhoL * R * T_vle
        print(f"  T={T_vle:.0f}K: Psat={Psat/1e5:.4f} bar")
    except Exception as e:
        print(f"  T={T_vle:.0f}K: FAILED - {e}")

# Also print built-in model's JSON to see exact params
print("\nDone.")
