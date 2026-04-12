"""Detailed comparison of P-V isotherm with teqp at T=120K."""
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
T = 120.0

# Fine-grained P-V isotherm in the liquid spinodal region
print("=== Fine P-V isotherm near spinodal ===")
print(f"  Vm(m3/mol)    rho(mol/m3)    Z           P(bar)      Ar00")
for Vm in np.concatenate([
    np.linspace(3.0e-5, 4.0e-5, 20),
    np.linspace(4.0e-5, 1.0e-4, 10),
    np.array([5.0e-4, 1.0e-3, 5.0e-3, 1.0e-2])
]):
    rho = 1.0/Vm
    Ar01 = model.get_Ar01(T, rho, z)
    Ar00 = model.get_Ar00(T, rho, z)
    Z_val = 1 + Ar01
    P_val = Z_val * rho * R * T
    print(f"  {Vm:.5e}  {rho:12.1f}  {Z_val:12.6f}  {P_val/1e5:12.4f}  {Ar00:12.6f}")

# VLE with better initial guesses
print("\n=== VLE at various temperatures ===")
for T_vle in [120.0, 130.0, 140.0, 150.0, 160.0, 170.0, 180.0, 185.0, 190.0]:
    try:
        # Use ancillaries or manual guesses for initial densities
        rhoL, rhoV = model.pure_VLE_T(T_vle, 25000.0, 100.0, 50)
        Ar01_L = model.get_Ar01(T_vle, rhoL, z)
        Psat = (1 + Ar01_L) * rhoL * R * T_vle

        VmL = 1.0/rhoL
        VmV = 1.0/rhoV

        # Check gas-side pressure
        Ar01_V = model.get_Ar01(T_vle, rhoV, z)
        Psat_V = (1 + Ar01_V) * rhoV * R * T_vle

        print(f"  T={T_vle:.0f}K: Psat_L={Psat/1e5:.4f} bar, Psat_V={Psat_V/1e5:.4f} bar, "
              f"VmL={VmL:.3e}, VmV={VmV:.3e}, rhoL={rhoL:.1f}, rhoV={rhoV:.2f}")
    except Exception as e:
        print(f"  T={T_vle:.0f}K: FAILED - {e}")
