"""Reference SAFT-VR Mie calculations using teqp (NIST) for methane."""
import teqp
import numpy as np

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
model = teqp.make_model(spec)

z = np.array([1.0])
R = 8.314462618

# Critical point
Tc, rhoc = model.solve_pure_critical(200.0, 10000.0)
Pc = (1 + model.get_Ar01(Tc, rhoc, z)) * rhoc * R * Tc
print(f"Critical point: Tc={Tc:.2f} K, rhoc={rhoc:.1f} mol/m3, Pc={Pc/1e5:.2f} bar")
print(f"NIST:           Tc=190.56 K, Pc=45.99 bar")
print()

# Saturation curve
nist_data = {120: 19.12, 130: 36.87, 140: 64.12, 150: 103.5, 160: 159.4, 170: 236.3, 180: 341.3}
print("Saturation curve:")
print(f"  T(K)    Psat_teqp(bar)  Psat_NIST(bar)   ratio")
for T in [120.0, 130.0, 140.0, 150.0, 160.0, 170.0, 180.0]:
    try:
        rhoL, rhoV = model.pure_VLE_T(T, 1e4, 0.1, 10)
        Ar01_L = model.get_Ar01(T, rhoL, z)
        Psat = (1 + Ar01_L) * rhoL * R * T
        nist = nist_data.get(int(T), 0)
        ratio = nist / (Psat/1e5) if Psat > 0 else 0
        print(f"  {T:5.0f}   {Psat/1e5:14.4f}  {nist:14.2f}   {ratio:6.2f}")
    except Exception as e:
        print(f"  {T:5.0f}   FAILED: {e}")
