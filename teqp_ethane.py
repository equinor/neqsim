"""Get teqp reference values for ethane SAFT-VR Mie saturation."""
import teqp
import numpy as np

spec = {
    'kind': 'SAFT-VR-Mie',
    'model': {
        'coeffs': [{
            'name': 'ethane',
            'm': 1.4373,
            'sigma_Angstrom': 3.7257,
            'epsilon_over_k': 206.12,
            'lambda_r': 12.4,
            'lambda_a': 6.0,
            'BibTeXKey': 'Lafitte2013'
        }]
    }
}
model = teqp.make_model(spec)
z = np.array([1.0])
R = 8.314462618

# Critical point
Tc, rhoc = model.solve_pure_critical(350.0, 8000.0)
Pc = (1 + model.get_Ar01(Tc, rhoc, z)) * rhoc * R * Tc
print(f"Ethane critical: Tc={Tc:.2f} K, Pc={Pc/1e5:.2f} bar")
print(f"NIST:            Tc=305.32 K, Pc=48.72 bar")
print()

# VLE
print("Ethane saturation (teqp reference):")
nist = {200: 3.50, 220: 7.69, 240: 14.82, 260: 25.64, 280: 40.85}
for T in [200.0, 220.0, 240.0, 260.0, 280.0]:
    try:
        rhoL, rhoV = model.pure_VLE_T(T, 18000.0, 50.0, 50)
        Psat = (1 + model.get_Ar01(T, rhoL, z)) * rhoL * R * T
        print(f"  T={T:.0f}K: Psat={Psat/1e5:.4f} bar (NIST={nist[int(T)]:.2f})")
    except Exception as e:
        print(f"  T={T:.0f}K: FAILED - {e}")
