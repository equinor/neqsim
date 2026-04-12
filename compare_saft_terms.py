"""
Compare individual SAFT-VR Mie terms between teqp and NeqSim.

This script outputs reference values from teqp (well-validated C++ implementation)
that can be compared against NeqSim's Java implementation term-by-term.

teqp source: https://github.com/usnistgov/teqp
Clapeyron.jl: https://github.com/ClapeyronThermo/Clapeyron.jl

Key finding: For the association term, our NeqSim implementation uses simple g_HS(eta)
in the delta calculation, but Lafitte 2013 specifies g_Mie(sigma) (the full Mie RDF).
Clapeyron.jl uses the Dufal 2015 I(Tr, rhor) correlation instead. Both approaches
give different (and more accurate) results than our simple g_HS approach.
"""
import teqp
import numpy as np

# ===== Methane (non-associating, m=1, simplest case) =====
print("=" * 80)
print("METHANE - Non-associating, m=1.0 (Lafitte 2013 parameters)")
print("=" * 80)

coeffs_ch4 = [
    {'name': 'Methane', 'm': 1.0, 'sigma_Angstrom': 3.7412,
     'epsilon_over_k': 153.36, 'lambda_r': 12.65, 'lambda_a': 6.0,
     'BibTeXKey': 'Lafitte-2013'}
]
model_ch4 = teqp.make_model({'kind': 'SAFT-VR-Mie', 'model': {'coeffs': coeffs_ch4}})

# State point: T=200K, several densities
T = 200.0
x = np.array([1.0])

print(f"\nT = {T} K")
print(f"sigma = 3.7412 A, eps/k = 153.36 K, lambda_r = 12.65, lambda_a = 6.0, m = 1.0")

# Get core calcs at several densities
densities_mol_m3 = [100, 500, 1000, 5000, 10000, 15000, 20000]
print(f"\n{'rho':>10} {'alphar':>14} {'alphar_mono':>14} {'alphar_chain':>14} "
      f"{'a1kB':>14} {'a2kB2':>14} {'a3kB3':>14} {'Ar01':>10} {'Z':>8}")

for rho in densities_mol_m3:
    try:
        cc = model_ch4.get_core_calcs(T, float(rho), x)
        alphar = model_ch4.get_Ar00(T, float(rho), x)
        Ar01 = model_ch4.get_Ar01(T, float(rho), x)
        Z = 1.0 + Ar01

        print(f"{rho:10d} {alphar:14.8f} {cc['alphar_mono']:14.8f} "
              f"{cc['alphar_chain']:14.8f} "
              f"{cc['a1kB']:14.6f} {cc['a2kB2']:14.6f} "
              f"{cc['a3kB3']:14.8f} {Ar01:10.6f} {Z:8.6f}")
    except Exception as e:
        print(f"{rho:10d} Error: {e}")

# Intermediate values at a specific density (liquid-like)
rho_liq = 15000.0
cc = model_ch4.get_core_calcs(T, rho_liq, x)
print(f"\n--- Detailed intermediate values at rho={rho_liq} mol/m3 ---")
print(f"  d_ii (diameter):   {cc['dmat'][0][0]:.10f} Angstrom")
print(f"  rhos (seg density): {cc['rhos']:.10f} A^-3")
print(f"  rhoN (num density): {cc['rhoN']:.6e} m^-3")
print(f"  mbar:              {cc['mbar']:.6f}")
print(f"  xs[0]:             {cc['xs'][0]:.6f}")
print(f"  zeta[0]:           {cc['zeta'][0]:.10f}")
print(f"  zeta[1]:           {cc['zeta'][1]:.10f}")
print(f"  zeta[2]:           {cc['zeta'][2]:.10f}")
print(f"  zeta[3]:           {cc['zeta'][3]:.10f}")
print(f"  zeta_x:            {cc['zeta_x']:.10f}")
print(f"  zeta_x_bar:        {cc['zeta_x_bar']:.10f}")
print(f"  a1kB:              {cc['a1kB']:.10f}")
print(f"  a2kB2:             {cc['a2kB2']:.10f}")
print(f"  a3kB3:             {cc['a3kB3']:.12f}")
print(f"  alphar_mono:       {cc['alphar_mono']:.10f}")
print(f"  alphar_chain:      {cc['alphar_chain']:.10f}")

# NeqSim convention: V_neqsim = V_m3 * 1e5
# For 1 mol at rho=15000 mol/m3: V_m3 = 1/15000 = 6.667e-5 m3
# V_neqsim = 6.667 (cm3-like units)
V_m3 = 1.0 / rho_liq
V_neqsim = V_m3 * 1.0e5
print(f"\n  NeqSim units:")
print(f"    V_m3 = {V_m3:.6e} m3")
print(f"    V_neqsim = {V_neqsim:.6f}")
print(f"    eta (packing frac) = zeta_x = {cc['zeta_x']:.10f}")

# ===== Ethane (chain molecule, m=1.4373) =====
print("\n" + "=" * 80)
print("ETHANE - Chain molecule, m=1.4373 (Lafitte 2013)")
print("=" * 80)

coeffs_c2 = [
    {'name': 'Ethane', 'm': 1.4373, 'sigma_Angstrom': 3.7257,
     'epsilon_over_k': 206.12, 'lambda_r': 12.4, 'lambda_a': 6.0,
     'BibTeXKey': 'Lafitte-2013'}
]
model_c2 = teqp.make_model({'kind': 'SAFT-VR-Mie', 'model': {'coeffs': coeffs_c2}})

T = 250.0
print(f"\nT = {T} K")

print(f"\n{'rho':>10} {'alphar':>14} {'alphar_mono':>14} {'alphar_chain':>14} "
      f"{'a1kB':>14} {'a2kB2':>14} {'a3kB3':>14}")

for rho in [100, 500, 1000, 5000, 10000, 14000]:
    try:
        cc = model_c2.get_core_calcs(T, float(rho), np.array([1.0]))
        alphar = model_c2.get_Ar00(T, float(rho), np.array([1.0]))
        print(f"{rho:10d} {alphar:14.8f} {cc['alphar_mono']:14.8f} "
              f"{cc['alphar_chain']:14.8f} "
              f"{cc['a1kB']:14.6f} {cc['a2kB2']:14.6f} "
              f"{cc['a3kB3']:14.8f}")
    except Exception as e:
        print(f"{rho:10d} Error: {e}")

# ===== Water comparison (note: teqp does NOT have association) =====
# We can still compare non-associating terms (mono + chain) for water parameters
print("\n" + "=" * 80)
print("WATER (NON-ASSOCIATING TERMS ONLY) - Lafitte 2013 parameters")
print("Note: lambda_r=35.823 (Lafitte 2013), NOT 17.02 (Dufal 2015)")
print("=" * 80)

# Lafitte 2013 Table 11 water parameters
coeffs_water = [
    {'name': 'Water', 'm': 1.0, 'sigma_Angstrom': 3.0063,
     'epsilon_over_k': 266.68, 'lambda_r': 35.823, 'lambda_a': 6.0,
     'BibTeXKey': 'Lafitte-2013'}
]
model_water = teqp.make_model({'kind': 'SAFT-VR-Mie', 'model': {'coeffs': coeffs_water}})

T_water = 373.15
print(f"\nT = {T_water} K (boiling point)")

print(f"\n{'rho':>10} {'alphar':>14} {'alphar_mono':>14} {'alphar_chain':>14} "
      f"{'zeta_x':>12} {'d_ii':>12}")

for rho in [100, 1000, 5000, 20000, 30000, 40000, 50000, 55000]:
    try:
        cc = model_water.get_core_calcs(T_water, float(rho), np.array([1.0]))
        alphar = model_water.get_Ar00(T_water, float(rho), np.array([1.0]))
        print(f"{rho:10d} {alphar:14.8f} {cc['alphar_mono']:14.8f} "
              f"{cc['alphar_chain']:14.8f} "
              f"{cc['zeta_x']:12.8f} {cc['dmat'][0][0]:12.8f}")
    except Exception as e:
        print(f"{rho:10d} Error: {e}")

# Water with Dufal 2015 parameters (lambda_r=17.02)
print("\n" + "=" * 80)
print("WATER (NON-ASSOC, Dufal 2015 params: lambda_r=17.02)")
print("=" * 80)
coeffs_water2 = [
    {'name': 'Water', 'm': 1.0, 'sigma_Angstrom': 3.0063,
     'epsilon_over_k': 266.68, 'lambda_r': 17.02, 'lambda_a': 6.0,
     'BibTeXKey': 'Dufal-2015'}
]
model_water2 = teqp.make_model({'kind': 'SAFT-VR-Mie', 'model': {'coeffs': coeffs_water2}})

print(f"\nT = {T_water} K")
print(f"\n{'rho':>10} {'alphar_Dufal':>14} {'alphar_Laf':>14} {'diff_pct':>10}")
for rho in [100, 1000, 5000, 20000, 40000, 55000]:
    try:
        ar_dufal = model_water2.get_Ar00(T_water, float(rho), np.array([1.0]))
        ar_lafitte = model_water.get_Ar00(T_water, float(rho), np.array([1.0]))
        diff = (ar_dufal - ar_lafitte) / abs(ar_lafitte) * 100 if ar_lafitte != 0 else 0
        print(f"{rho:10d} {ar_dufal:14.8f} {ar_lafitte:14.8f} {diff:10.2f}%")
    except Exception as e:
        print(f"{rho:10d} Error: {e}")

# ===== Bubble point comparison for methane =====
print("\n" + "=" * 80)
print("METHANE VLE - teqp bubble point at T=150K")
print("=" * 80)
from scipy.optimize import brentq

T_vle = 150.0
# Scan pressures to find where fugacity equals
def methane_pressure(rho_mol_m3):
    return rho_mol_m3 * 8.314462618 * T_vle * (1.0 + model_ch4.get_Ar01(T_vle, rho_mol_m3, np.array([1.0])))

# Find liquid / gas densities at various pressures
print(f"T = {T_vle} K")
for P_bar in [5, 10, 15, 20, 25, 30, 35]:
    P_Pa = P_bar * 1e5
    try:
        rhoL = brentq(lambda rho: methane_pressure(rho) - P_Pa, 10000, 30000)
        rhoG = brentq(lambda rho: methane_pressure(rho) - P_Pa, 10, 5000)
        lnphi_L = model_ch4.get_ln_fugacity_coefficients(T_vle, rhoL, np.array([1.0]))[0]
        lnphi_G = model_ch4.get_ln_fugacity_coefficients(T_vle, rhoG, np.array([1.0]))[0]
        print(f"  P={P_bar:3d} bar: rhoL={rhoL:.1f}, rhoG={rhoG:.1f}, "
              f"lnphi_L={lnphi_L:.6f}, lnphi_G={lnphi_G:.6f}, "
              f"diff={abs(lnphi_L-lnphi_G):.2e}")
    except Exception as e:
        print(f"  P={P_bar:3d} bar: {e}")

print("\n" + "=" * 80)
print("KEY FINDING: Association Delta Formula Comparison")
print("=" * 80)
print("""
Our NeqSim implementation:
  delta = (exp(eps_HB/RT) - 1) * sigma^3 * N_A * kappa * g_HS(eta)
  g_HS(eta) = (1 - eta/2) / (1 - eta)^3   [simple hard-sphere RDF]

Lafitte 2013 paper (Section III.E):
  delta = K_ab * g_Mie(sigma; rho, T) * (exp(eps_HB/kT) - 1)
  g_Mie = g_HS(x0) * exp(tau*g1/g0 + tau^2*g2/g0)  [full Mie RDF with perturbation corrections]

Clapeyron.jl / Dufal 2015:
  delta = F * K * I(T/eps, rho*sigma^3)
  I = double polynomial in reduced T and rho (11x11 coefficient matrix)
  Uses DIFFERENT water parameters: lambda_r=17.02 (vs 35.823 in Lafitte 2013)

The simple g_HS(eta) misses the g1 and g2 perturbation corrections that are significant
for highly repulsive potentials like water (lambda_r=35.823 has very steep well).
These corrections can change g(sigma) by 50-100% at typical liquid densities.
""")
