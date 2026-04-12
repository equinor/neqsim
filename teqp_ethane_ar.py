"""Compare Helmholtz residual for ethane: teqp vs diagnostic values."""
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

# Test at T=260K, various molar volumes
T = 260.0
print(f"=== Ethane Ar00 comparison at T={T} K ===")
print(f"{'Vm (m3/mol)':<16} {'rhoN (1/m3)':<16} {'Ar00_teqp':<16} {'P_teqp (bar)':<16}")

for Vm in [5e-5, 6e-5, 7e-5, 1e-4, 2e-4, 5e-4, 1e-3, 5e-3, 1e-2]:
    rhoN = 1.0 / (Vm * 6.022e23)  # wrong: this gives mol density...
    # teqp uses molar density in mol/m3
    rho_mol = 1.0 / Vm
    Ar00 = model.get_Ar00(T, rho_mol, z)
    Ar01 = model.get_Ar01(T, rho_mol, z)
    P = (1 + Ar01) * rho_mol * R * T / 1e5  # bar
    print(f"{Vm:<16.4e} {rho_mol:<16.2f} {Ar00:<16.6f} {P:<16.4f}")

# Also get VLE solution
print(f"\n=== Ethane VLE at T={T} K ===")
rhoL, rhoV = model.pure_VLE_T(T, 18000.0, 50.0, 50)
VmL = 1.0 / rhoL
VmV = 1.0 / rhoV
Psat = (1 + model.get_Ar01(T, rhoL, z)) * rhoL * R * T / 1e5
print(f"  Liquid: rhoL={rhoL:.2f} mol/m3, VmL={VmL:.6e} m3/mol")
print(f"  Vapor:  rhoV={rhoV:.2f} mol/m3, VmV={VmV:.6e} m3/mol")
print(f"  Psat={Psat:.4f} bar")
print(f"  Ar00_L={model.get_Ar00(T, rhoL, z):.6f}")
print(f"  Ar00_V={model.get_Ar00(T, rhoV, z):.6f}")

# Compare different T values at VLE
print(f"\n=== Ethane VLE at multiple temperatures ===")
for T in [200.0, 220.0, 240.0, 260.0, 280.0]:
    try:
        rhoL, rhoV = model.pure_VLE_T(T, 18000.0, 50.0, 50)
        Psat = (1 + model.get_Ar01(T, rhoL, z)) * rhoL * R * T / 1e5
        VmL = 1.0 / rhoL
        VmV = 1.0 / rhoV
        etaL = rhoL * np.pi / 6.0 * (1.4373 * (3.7257e-10)**3)  # approximate packing
        print(f"  T={T:.0f}K: Psat={Psat:.4f} bar  VmL={VmL:.4e}  VmV={VmV:.4e}  Ar00_L={model.get_Ar00(T, rhoL, z):.6f}")
    except Exception as e:
        print(f"  T={T:.0f}K: FAILED - {e}")

# P-V isotherm at T=260K for comparison
print(f"\n=== P-V isotherm at T=260K ===")
for Vm in [5e-5, 6e-5, 7e-5, 8e-5, 1e-4, 1.5e-4, 2e-4, 3e-4, 5e-4, 1e-3, 5e-3]:
    rho_mol = 1.0 / Vm
    Ar01 = model.get_Ar01(T, rho_mol, z)
    P = (1 + Ar01) * rho_mol * R * T / 1e5
    print(f"  Vm={Vm:.4e}  P={P:.4f} bar")
