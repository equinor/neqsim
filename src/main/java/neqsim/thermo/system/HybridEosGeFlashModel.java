package neqsim.thermo.system;

/**
 * Opt-in contract for a multiphase flash with separate equation-of-state gas and oil phases and an excess-Gibbs-energy
 * aqueous phase.
 *
 * <p>
 * The hybrid path is deliberately separate from the two-phase direct gamma-phi K-value loop. A model prepares its
 * creation-order phase roles, the dedicated multiphase strategy solves phase fractions and compositions using each
 * phase's own fugacity model, and the model then validates and finalises the result. Ordinary EOS systems never
 * implement this interface and therefore do not enter the hybrid dispatch.
 * </p>
 *
 * @author NeqSim
 */
public interface HybridEosGeFlashModel extends EosGeFlashModel {
  /**
   * Configure and enable the fixed-role EOS-gas / EOS-oil / GE-aqueous flash.
   *
   * <p>
   * Implementations that expose a reusable GE liquid phase can promote that phase to the aqueous role and clone their
   * EOS gas phase for the oil role. The default implementation rejects the request so a model cannot silently claim a
   * topology that it has not configured.
   * </p>
   */
  default void enableHybridEosGeFlash() {
    throw new UnsupportedOperationException("This model does not expose configurable hybrid EOS-GE phase roles.");
  }

  /**
   * Check whether fixed gas, oil and GE-aqueous creation-order roles have been configured.
   *
   * @return {@code true} when the model owns all three reusable fluid-role objects
   */
  default boolean isHybridEosGeTopologyConfigured() {
    return false;
  }

  /**
   * Select the dedicated hybrid multiphase strategy.
   *
   * @return {@code true} when the current system state requires hybrid EOS-GE multiphase solving
   */
  default boolean requiresHybridEosGeFlash() {
    return false;
  }

  /**
   * Restore creation-order gas, oil and aqueous roles and provide a finite initial phase split.
   */
  default void prepareHybridEosGeFlash() {
    // Ordinary systems require no hybrid phase-role preparation.
  }

  /**
   * Restore creation-order role mappings without changing current phase compositions or fractions.
   */
  default void restoreHybridEosGePhaseRoles() {
    // Ordinary systems require no hybrid phase-role restoration.
  }

  /**
   * Restore role types for the current active mapping without adding a missing role.
   */
  default void restoreHybridEosGeActivePhaseTypes() {
    // Ordinary systems require no hybrid phase-role restoration.
  }

  /**
   * Synchronize reaction-adjusted overall species amounts across all model-owned phase roles.
   *
   * <p>
   * Chemical equilibrium changes species amounts while conserving elements. A coupled reactive flash must therefore
   * replace the feed-species {@code z} values before its next phase-fraction solve; otherwise generated species are
   * discarded or rescaled to the original total mole count.
   * </p>
   *
   * @param componentMoles reaction-adjusted overall moles by component index
   * @param totalMoles sum of the reaction-adjusted component amounts
   */
  default void synchronizeHybridEosGeOverallComposition(double[] componentMoles, double totalMoles) {
    // Ordinary systems do not own hybrid phase roles.
  }

  /**
   * Check whether an active phase number maps to the configured GE aqueous role.
   *
   * <p>
   * This must use the model-owned creation-order mapping rather than a phase object's mutable type label. GE phase
   * initialization may temporarily classify a hydrocarbon-rich trial composition as oil while the multiphase solver is
   * still converging.
   * </p>
   *
   * @param phaseNumber active phase number
   * @return {@code true} when the active phase is the configured GE aqueous object
   */
  default boolean isHybridEosGeAqueousPhase(int phaseNumber) {
    return false;
  }

  /**
   * Validate and finalise a hybrid multiphase result.
   *
   * @param phaseFractionMinimumLimit minimum active phase fraction used by the solver
   * @return {@code true} when the result is finite, balanced and consistent with the configured roles
   */
  default boolean finishHybridEosGeFlash(double phaseFractionMinimumLimit) {
    return false;
  }

  /**
   * Return model-specific diagnostics for a rejected hybrid result.
   *
   * @param phaseFractionMinimumLimit minimum active phase fraction used by the solver
   * @return diagnostic text
   */
  default String getHybridEosGeFlashDiagnostics(double phaseFractionMinimumLimit) {
    return "No model-specific hybrid EOS-GE diagnostics are available.";
  }
}
