"""
Optimize Born radii and SR2 (Wij) parameters for the CPA electrolyte model
with the dielectric decrement model enabled.

Target: NaCl mean ionic activity coefficients (Robinson & Stokes 1965)
at 25°C, 1 atm.

Parameters fitted:
  - Born radius for Na+ (lennardJonesMolecularDiameter, Angstrom)
  - Born radius for Cl- (lennardJonesMolecularDiameter, Angstrom)
  - Wij[Na+, water] (SR2 short-range interaction)
  - Wij[Na+, Cl-] (SR2 cation-anion interaction)
"""

import sys
import os
import numpy as np
from scipy.optimize import minimize, differential_evolution

# ── NeqSim setup ──────────────────────────────────────────────────────────────
try:
    from neqsim import jneqsim
except ImportError:
    sys.exit("ERROR: neqsim not installed. Run: pip install neqsim")

import jpype

# Java class shortcuts
SystemElectrolyteCPAstatoil = jneqsim.thermo.system.SystemElectrolyteCPAstatoil
ThermodynamicOperations = jneqsim.thermodynamicoperations.ThermodynamicOperations

# ── Robinson & Stokes NaCl data at 25°C ──────────────────────────────────────
RS_DATA = {
    0.001: 0.9649,
    0.005: 0.9275,
    0.01:  0.9024,
    0.05:  0.8216,
    0.1:   0.778,
    0.2:   0.735,
    0.5:   0.681,
    1.0:   0.657,
    2.0:   0.668,
    3.0:   0.714,
    4.0:   0.783,
    5.0:   0.874,
    6.0:   0.986,
}

# Use a focused range for fitting (m=0.1 to 6.0)
FIT_MOLALITIES = [0.1, 0.5, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0]
FIT_GAMMA = [RS_DATA[m] for m in FIT_MOLALITIES]


def compute_gamma_nacl(molality, born_Na, born_Cl, wij_Na_water, wij_Na_Cl,
                       use_decrement=True, verbose=False):
    """
    Compute mean ionic activity coefficient of NaCl at given molality.

    Parameters
    ----------
    molality : float
        NaCl molality (mol/kg water)
    born_Na : float
        Born radius for Na+ (LJ diameter in Angstrom)
    born_Cl : float
        Born radius for Cl- (LJ diameter in Angstrom)
    wij_Na_water : float
        SR2 interaction parameter Na+-water
    wij_Na_Cl : float
        SR2 interaction parameter Na+-Cl-
    use_decrement : bool
        Whether to enable dielectric decrement model
    verbose : bool
        Print debug info

    Returns
    -------
    float : mean ionic activity coefficient gamma_pm
    """
    try:
        # Create fresh system each time (avoids state contamination)
        T_K = 298.15
        P_bar = 1.01325
        fluid = SystemElectrolyteCPAstatoil(T_K, P_bar)

        # Add components: 1 kg water = 55.508 mol
        n_water = 55.508
        n_salt = molality  # mol NaCl per kg water

        fluid.addComponent("water", n_water)
        fluid.addComponent("Na+", n_salt)
        fluid.addComponent("Cl-", n_salt)

        fluid.setMixingRule(10)  # CPA electrolyte mixing rule
        fluid.setMultiPhaseCheck(False)

        # Enable decrement model on both phases
        if use_decrement:
            for phaseIdx in range(fluid.getNumberOfPhases()):
                phase = fluid.getPhase(phaseIdx)
                if hasattr(phase, 'setUseIonDielectricDecrement'):
                    phase.setUseIonDielectricDecrement(True)

        # Override Born radii (LJ diameters)
        fluid.getComponent("Na+").setLennardJonesMolecularDiameter(born_Na)
        fluid.getComponent("Cl-").setLennardJonesMolecularDiameter(born_Cl)

        # Override Wij parameters
        # Need component indices
        idx_water = fluid.getComponent("water").getComponentNumber()
        idx_Na = fluid.getComponent("Na+").getComponentNumber()
        idx_Cl = fluid.getComponent("Cl-").getComponentNumber()

        for phaseIdx in range(fluid.getNumberOfPhases()):
            phase = fluid.getPhase(phaseIdx)
            if hasattr(phase, 'getElectrolyteMixingRule'):
                mixRule = phase.getElectrolyteMixingRule()
                if mixRule is not None:
                    mixRule.setWijParameter(idx_Na, idx_water, wij_Na_water)
                    mixRule.setWijParameter(idx_Na, idx_Cl, wij_Na_Cl)

        # Run TP flash
        ops = ThermodynamicOperations(fluid)
        ops.TPflash()
        fluid.init(3)
        fluid.initPhysicalProperties()

        # Get activity coefficients
        gamma_Na = fluid.getPhase(0).getActivityCoefficient(idx_Na)
        gamma_Cl = fluid.getPhase(0).getActivityCoefficient(idx_Cl)

        if gamma_Na <= 0 or gamma_Cl <= 0:
            if verbose:
                print(f"  WARNING: negative gamma at m={molality}: Na={gamma_Na:.4f}, Cl={gamma_Cl:.4f}")
            return 1e6  # penalty

        gamma_pm = np.sqrt(gamma_Na * gamma_Cl)

        if verbose:
            print(f"  m={molality:.1f}: gamma_Na={gamma_Na:.4f}, gamma_Cl={gamma_Cl:.4f}, gamma_pm={gamma_pm:.4f}")

        return gamma_pm

    except Exception as e:
        if verbose:
            print(f"  ERROR at m={molality}: {e}")
        return 1e6  # penalty for failed calculations


def objective(params, use_decrement=True, verbose=False):
    """
    Objective function: sum of squared relative errors in gamma_pm.

    Parameters: [born_Na, born_Cl, wij_Na_water, wij_Na_Cl]
    """
    born_Na, born_Cl, wij_Na_water, wij_Na_Cl = params

    total_error = 0.0
    for m, gamma_exp in zip(FIT_MOLALITIES, FIT_GAMMA):
        gamma_calc = compute_gamma_nacl(m, born_Na, born_Cl, wij_Na_water, wij_Na_Cl,
                                         use_decrement=use_decrement, verbose=verbose)
        if gamma_calc > 1e5:
            return 1e10  # penalty for failed calculations

        rel_error = (gamma_calc - gamma_exp) / gamma_exp
        total_error += rel_error ** 2

    if verbose:
        rmse = np.sqrt(total_error / len(FIT_MOLALITIES)) * 100
        print(f"  RMSE: {rmse:.2f}%  params: born_Na={born_Na:.3f}, born_Cl={born_Cl:.3f}, "
              f"wij_Na_water={wij_Na_water:.6e}, wij_Na_Cl={wij_Na_Cl:.6e}")

    return total_error


def get_current_params():
    """Get the current (default) parameter values from NeqSim."""
    T_K = 298.15
    P_bar = 1.01325
    fluid = SystemElectrolyteCPAstatoil(T_K, P_bar)
    fluid.addComponent("water", 55.508)
    fluid.addComponent("Na+", 1.0)
    fluid.addComponent("Cl-", 1.0)
    fluid.setMixingRule(10)

    born_Na = fluid.getComponent("Na+").getLennardJonesMolecularDiameter()
    born_Cl = fluid.getComponent("Cl-").getLennardJonesMolecularDiameter()

    idx_water = fluid.getComponent("water").getComponentNumber()
    idx_Na = fluid.getComponent("Na+").getComponentNumber()
    idx_Cl = fluid.getComponent("Cl-").getComponentNumber()

    phase = fluid.getPhase(0)
    mixRule = phase.getElectrolyteMixingRule()
    wij_Na_water = mixRule.getWijParameter(idx_Na, idx_water)
    wij_Na_Cl = mixRule.getWijParameter(idx_Na, idx_Cl)

    # Also get Cl- LJ diameter
    print(f"\n=== Current Default Parameters ===")
    print(f"Born radius Na+ (LJ diameter): {born_Na:.4f} Å")
    print(f"Born radius Cl- (LJ diameter): {born_Cl:.4f} Å")
    print(f"Wij[Na+, water]:  {wij_Na_water:.6e}")
    print(f"Wij[Na+, Cl-]:    {wij_Na_Cl:.6e}")
    print(f"Component indices: water={idx_water}, Na+={idx_Na}, Cl-={idx_Cl}")

    return born_Na, born_Cl, wij_Na_water, wij_Na_Cl


def evaluate_model(params, label, use_decrement=True):
    """Evaluate gamma_pm at all molalities and print results."""
    born_Na, born_Cl, wij_Na_water, wij_Na_Cl = params

    print(f"\n=== {label} ===")
    print(f"Born Na+: {born_Na:.4f} Å, Born Cl-: {born_Cl:.4f} Å")
    print(f"Wij[Na+,H2O]: {wij_Na_water:.6e}, Wij[Na+,Cl-]: {wij_Na_Cl:.6e}")
    print(f"{'m':>6s}  {'γ±(calc)':>10s}  {'γ±(RS)':>8s}  {'Error%':>8s}")
    print("-" * 40)

    errors = []
    for m in sorted(RS_DATA.keys()):
        gamma_exp = RS_DATA[m]
        gamma_calc = compute_gamma_nacl(m, born_Na, born_Cl, wij_Na_water, wij_Na_Cl,
                                         use_decrement=use_decrement)
        err_pct = (gamma_calc - gamma_exp) / gamma_exp * 100
        errors.append(abs(err_pct))
        print(f"{m:>6.3f}  {gamma_calc:>10.4f}  {gamma_exp:>8.4f}  {err_pct:>+8.1f}%")

    print(f"\nMean absolute error: {np.mean(errors):.1f}%")
    print(f"Max absolute error:  {np.max(errors):.1f}%")


def main():
    print("=" * 60)
    print("Born Radius & SR2 Parameter Optimization")
    print("Target: NaCl γ± at 25°C with dielectric decrement model")
    print("=" * 60)

    # Step 1: Get current parameter values
    born_Na_0, born_Cl_0, wij_Na_water_0, wij_Na_Cl_0 = get_current_params()
    default_params = [born_Na_0, born_Cl_0, wij_Na_water_0, wij_Na_Cl_0]

    # Step 2: Evaluate current model with CM (original)
    print("\n--- Evaluating CM model (original, no decrement) ---")
    evaluate_model(default_params, "CM Model (Original)", use_decrement=False)

    # Step 3: Evaluate current model with decrement
    print("\n--- Evaluating Decrement model (current params) ---")
    evaluate_model(default_params, "Decrement Model (Default Params)", use_decrement=True)

    # Step 4: Optimization
    print("\n" + "=" * 60)
    print("Starting optimization with Nelder-Mead...")
    print("=" * 60)

    # Initial guess: start from current defaults but allow adjustment
    x0 = np.array(default_params, dtype=float)

    # Bounds for differential evolution
    # Born radii: 1.5-6.0 Å (physical range for ionic radii)
    # Wij: allow 10x variation around current values
    wij_scale = max(abs(wij_Na_water_0), 1e-6)
    wij_Cl_scale = max(abs(wij_Na_Cl_0), 1e-6)

    bounds = [
        (1.5, 6.0),      # born_Na
        (2.0, 7.0),      # born_Cl
        (-1e-3, 1e-3),   # wij_Na_water
        (-1e-3, 1e-3),   # wij_Na_Cl
    ]

    # First try Nelder-Mead from the default starting point
    result_nm = minimize(objective, x0, method='Nelder-Mead',
                         args=(True, False),
                         options={'maxiter': 500, 'xatol': 1e-4, 'fatol': 1e-6,
                                  'adaptive': True})

    print(f"\nNelder-Mead result: success={result_nm.success}")
    print(f"  Iterations: {result_nm.nit}, Function evals: {result_nm.nfev}")
    print(f"  Objective: {result_nm.fun:.6f}")

    # Step 5: Evaluate optimized model
    optimal_params = result_nm.x
    evaluate_model(optimal_params, "OPTIMIZED Decrement Model", use_decrement=True)

    # Also try differential evolution for global search
    print("\n" + "=" * 60)
    print("Starting differential evolution (global)...")
    print("=" * 60)

    result_de = differential_evolution(objective, bounds, args=(True, False),
                                        seed=42, maxiter=100, tol=1e-6,
                                        workers=1, polish=True,
                                        init='sobol', popsize=10)

    print(f"\nDifferential Evolution result: success={result_de.success}")
    print(f"  Iterations: {result_de.nit}, Function evals: {result_de.nfev}")
    print(f"  Objective: {result_de.fun:.6f}")

    de_params = result_de.x
    evaluate_model(de_params, "DE-OPTIMIZED Decrement Model", use_decrement=True)

    # Step 6: Print final recommendation
    best_params = de_params if result_de.fun < result_nm.fun else optimal_params
    best_obj = min(result_de.fun, result_nm.fun)

    print("\n" + "=" * 60)
    print("FINAL RECOMMENDATION")
    print("=" * 60)
    print(f"Best objective: {best_obj:.6f}")
    print(f"Born Na+ (LJ diameter): {best_params[0]:.4f} Å")
    print(f"Born Cl- (LJ diameter): {best_params[1]:.4f} Å")
    print(f"Wij[Na+, water]:        {best_params[2]:.8e}")
    print(f"Wij[Na+, Cl-]:          {best_params[3]:.8e}")

    evaluate_model(list(best_params), "BEST Model", use_decrement=True)


if __name__ == "__main__":
    main()
