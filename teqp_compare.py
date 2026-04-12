"""Compare teqp individual terms for ethane at specific conditions."""
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

# Key test: P-V isotherm at T=260K — compare with our Java code
T = 260.0
print(f"=== Ethane P-V isotherm at T={T} K (for comparison with NeqSim) ===")
print(f"{'Vm (m3/mol)':<16} {'P (bar)':<16} {'Ar00':<16} {'Ar01':<16}")

test_vms = [5e-5, 6e-5, 6.5e-5, 7e-5, 7.5e-5, 8e-5, 9e-5, 1e-4, 1.5e-4, 2e-4, 5e-4, 1e-3, 5e-3, 1e-2]
for Vm in test_vms:
    rho_mol = 1.0 / Vm
    Ar00 = model.get_Ar00(T, rho_mol, z)
    Ar01 = model.get_Ar01(T, rho_mol, z)
    P = (1 + Ar01) * rho_mol * R * T / 1e5  # bar
    print(f"{Vm:<16.4e} {P:<16.4f} {Ar00:<16.6f} {Ar01:<16.6f}")

# Also try methane at 120K for comparison (this should match our code exactly)
T2 = 120.0
print(f"\n=== Methane P-V isotherm at T={T2} K (validation) ===")
spec_ch4 = {
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
model_ch4 = teqp.make_model(spec_ch4)
print(f"{'Vm (m3/mol)':<16} {'P (bar)':<16} {'Ar00':<16} {'Ar01':<16}")
for Vm in [3.5e-5, 4e-5, 5e-5, 7e-5, 1e-4, 5e-4, 1e-3, 5e-3]:
    rho_mol = 1.0 / Vm
    Ar00 = model_ch4.get_Ar00(T2, rho_mol, z)
    Ar01 = model_ch4.get_Ar01(T2, rho_mol, z)
    P = (1 + Ar01) * rho_mol * R * T2 / 1e5
    print(f"{Vm:<16.4e} {P:<16.4f} {Ar00:<16.6f} {Ar01:<16.6f}")
