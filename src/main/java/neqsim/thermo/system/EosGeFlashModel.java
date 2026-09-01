package neqsim.thermo.system;

import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;

/**
 * Optional thermodynamic hooks for systems that combine an equation-of-state vapour phase with an excess-Gibbs-energy
 * liquid phase.
 *
 * <p>
 * The ordinary NeqSim TP flash remains the default. A specialised gamma-phi model can opt into direct K-value iteration
 * and provide only the model-specific reference-state constraints needed by that iteration. This keeps activity-model
 * details out of the generic flash solver and gives future EOS-GE systems the same extension point.
 * </p>
 *
 * @author NeqSim
 */
public interface EosGeFlashModel {
  /**
   * Select direct gamma-phi K-value iteration instead of generic single-phase stability screening.
   *
   * @return {@code true} when the EOS-GE model requires the direct path
   */
  default boolean requiresDirectGammaPhiFlash() {
    return false;
  }

  /**
   * Prepare model phase roles before direct gamma-phi initialization and iteration.
   *
   * <p>
   * Implementations can restore creation-order phase slots after an earlier operation reordered active phases.
   * </p>
   */
  default void prepareGammaPhiFlash() {
    // Ordinary systems require no phase-role preparation.
  }

  /**
   * Return the vapour-side fugacity coefficient used in the gamma-phi K-value ratio.
   *
   * @param component vapour-phase component
   * @param vapourPhase equation-of-state phase
   * @return vapour-side fugacity coefficient
   */
  default double getGammaPhiVapourFugacityCoefficient(ComponentInterface component, PhaseInterface vapourPhase) {
    component.fugcoef(vapourPhase);
    return component.getFugacityCoefficient();
  }

  /**
   * Apply any model validity constraint to a newly calculated K value.
   *
   * @param component component whose K value is being updated
   * @param targetK unconstrained gamma-phi K value
   * @return constrained target K value
   */
  default double constrainGammaPhiKValue(ComponentInterface component, double targetK) {
    return targetK;
  }

  /**
   * Relax a gamma-phi K-value update when the model needs damped successive substitution.
   *
   * @param previousK previous K value
   * @param targetK constrained target K value
   * @return K value to use for the next iteration
   */
  default double relaxGammaPhiKValue(double previousK, double targetK) {
    return targetK;
  }

  /**
   * Validate and finalise a direct gamma-phi TP-flash result.
   *
   * @param deviation final logarithmic K-value deviation
   * @param phaseFractionMinimumLimit minimum active phase fraction used by the solver
   * @return {@code true} when the result was accepted or reduced to a valid single phase
   */
  default boolean finishGammaPhiFlash(double deviation, double phaseFractionMinimumLimit) {
    return false;
  }

  /**
   * Return model-specific convergence diagnostics for a rejected direct gamma-phi flash.
   *
   * @param deviation final logarithmic K-value deviation
   * @param phaseFractionMinimumLimit minimum active phase fraction used by the solver
   * @return diagnostic text
   */
  default String getGammaPhiFlashDiagnostics(double deviation, double phaseFractionMinimumLimit) {
    return "No model-specific gamma-phi diagnostics are available.";
  }
}
