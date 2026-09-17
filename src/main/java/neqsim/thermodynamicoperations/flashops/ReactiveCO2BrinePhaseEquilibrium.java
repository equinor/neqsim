package neqsim.thermodynamicoperations.flashops;

import java.util.concurrent.CancellationException;
import neqsim.chemicalreactions.ChemicalReactionOperations;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.flashops.reactiveflash.FormulaMatrix;

/**
 * Bounded phase/reaction coupling for water-rich electrolyte-CPA CO2/brine hydrate calculations.
 *
 * <p>
 * Each phase solve holds species inventories fixed and confines ions to water. Chemistry is solved on an isolated
 * aqueous phase, where the chemical solver can synchronize reaction-adjusted species totals. Only its conservative
 * species changes are transferred back to the full fluid. This avoids feeding stale overall species amounts and
 * ion-stripped phase trials into the generic coupled multiphase iteration. A result requires simultaneous molecular
 * fugacity equality, reaction equilibrium, elemental conservation and electroneutrality. The caller is updated only
 * after all checks succeed; no EOS, hydrate or reaction parameters are changed.
 * </p>
 */
public final class ReactiveCO2BrinePhaseEquilibrium {
  private static final int MAXIMUM_ITERATIONS = 30;
  private static final double REACTION_TOLERANCE = 2.0e-6;
  private final SystemInterface system;
  private double minimumTrialDistance = Double.NaN;
  private int iterations;

  /**
   * Creates a coupled calculation without modifying the supplied fluid.
   *
   * @param system reactive water-rich electrolyte-CPA CO2/water/ion fluid
   */
  public ReactiveCO2BrinePhaseEquilibrium(SystemInterface system) {
    this.system = system;
  }

  /**
   * Checks the model, composition and reaction scope.
   *
   * @param fluid system to inspect
   * @return true for reactive fluids within the constrained CO2/water/ion phase solver's scope
   */
  public static boolean isApplicable(SystemInterface fluid) {
    return fluid.isChemicalSystem() && CO2BrinePhaseEquilibrium.hasSupportedComposition(fluid);
  }

  /**
   * Solves the phase and chemical equilibrium transactionally.
   *
   * @throws IllegalArgumentException for unsupported fluids
   * @throws IllegalStateException when chemistry, phase equilibrium or conservation cannot be qualified
   * @throws CancellationException when the calling thread is interrupted
   */
  public void run() {
    iterations = 0;
    minimumTrialDistance = Double.NaN;
    checkInterrupted();
    if (!isApplicable(system)) {
      throw new IllegalArgumentException(
          "Reactive CO2/brine equilibrium requires water-rich electrolyte-CPA CO2/water");
    }
    FormulaMatrix formula = new FormulaMatrix(system);
    double[] elements = formula.computeElementVector(overallMoles(system));
    SystemInterface work = system.clone();
    double reactionResidual = Double.NaN;
    for (int iteration = 0; iteration < MAXIMUM_ITERATIONS; iteration++) {
      iterations = iteration + 1;
      checkInterrupted();
      work.isChemicalSystem(false);
      CO2BrinePhaseEquilibrium phaseFlash = new CO2BrinePhaseEquilibrium(work);
      try {
        phaseFlash.run();
      } catch (CancellationException ex) {
        throw ex;
      } catch (IllegalStateException ex) {
        throw new IllegalStateException("Reactive CO2/brine phase equilibrium failed: " + ex.getMessage(), ex);
      }
      work.isChemicalSystem(true);
      reactionResidual = work.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual();
      verifyElements(formula, elements, overallMoles(work));
      if (Double.isFinite(reactionResidual) && reactionResidual <= REACTION_TOLERANCE) {
        double charge = work.getChemicalReactionOperations().getReactivePhaseChargeMoles();
        if (!Double.isFinite(charge) || Math.abs(charge) > 1.0e-8) {
          throw new IllegalStateException("Reactive CO2/brine equilibrium failed aqueous electroneutrality");
        }
        checkInterrupted();
        copyResult(work, phaseFlash.getAcceptedRoot());
        minimumTrialDistance = phaseFlash.getMinimumTrialDistance();
        return;
      }

      SystemInterface aqueous = work.phaseToSystem("aqueous");
      // phaseToSystem replaces both the material basis and the reactive phase index. Rebuild the chemical
      // initialization on that isolated basis rather than retaining its parent's phase-bound helpers.
      aqueous.chemicalReactionInit();
      aqueous.setNumberOfPhases(1);
      aqueous.setPhaseType(0, PhaseType.AQUEOUS);
      aqueous.setBeta(0, 1.0);
      aqueous.init(1);
      double[] before = overallMoles(aqueous);
      ChemicalReactionOperations chemistry = aqueous.getChemicalReactionOperations();
      if (!chemistry.solveChemEq(0, 1)) {
        throw new IllegalStateException("Reactive CO2/brine aqueous chemistry did not converge: reaction residual="
            + chemistry.getMaximumAbsoluteReactionLogResidual() + ", element residual="
            + chemistry.getMaximumAbsoluteElementBalanceResidual() + ", charge="
            + chemistry.getReactivePhaseChargeMoles());
      }
      double[] after = overallMoles(aqueous);
      double[] updated = overallMoles(work);
      for (int component = 0; component < updated.length; component++) {
        // Keep every non-aqueous component amount; only aqueous reactions change the full species inventory.
        updated[component] += after[component] - before[component];
        if (!Double.isFinite(updated[component]) || updated[component] < -1.0e-12) {
          throw new IllegalStateException("Reactive CO2/brine equilibrium produced an invalid species inventory");
        }
        updated[component] = Math.max(0.0, updated[component]);
      }
      verifyElements(formula, elements, updated);
      work.setMolarFlowRates(updated);
    }
    throw new IllegalStateException("Reactive CO2/brine equilibrium exceeded " + MAXIMUM_ITERATIONS
        + " coupling iterations: reaction residual=" + reactionResidual);
  }

  /** @return number of phase/reaction coupling iterations in the most recent calculation */
  public int getIterations() {
    return iterations;
  }

  /** @return minimum CO2 trial distance at the accepted speciation, or NaN if no result was accepted */
  public double getMinimumTrialDistance() {
    return minimumTrialDistance;
  }

  /** Reject cancellation before constructing trials or committing a result. */
  private static void checkInterrupted() {
    if (Thread.currentThread().isInterrupted()) {
      throw new CancellationException("Reactive CO2/brine equilibrium interrupted");
    }
  }

  /**
   * Reads the current species inventory on the full-fluid basis.
   *
   * @param fluid source fluid
   * @return component amounts in moles, in the original component order
   */
  private static double[] overallMoles(SystemInterface fluid) {
    double[] moles = new double[fluid.getNumberOfComponents()];
    for (int component = 0; component < moles.length; component++) {
      moles[component] = fluid.getPhase(0).getComponent(component).getNumberOfmoles();
    }
    return moles;
  }

  /**
   * Checks elements and charge against the original feed, not the previous coupling iterate.
   *
   * @param formula input formula matrix, including spectator ions and charge
   * @param expected input elemental amounts
   * @param moles proposed species amounts
   */
  private static void verifyElements(FormulaMatrix formula, double[] expected, double[] moles) {
    double[] actual = formula.computeElementVector(moles);
    for (int element = 0; element < expected.length; element++) {
      double tolerance = Math.max(1.0e-9, Math.abs(expected[element]) * 1.0e-10);
      if (!Double.isFinite(actual[element]) || Math.abs(actual[element] - expected[element]) > tolerance) {
        throw new IllegalStateException("Reactive CO2/brine equilibrium failed elemental conservation for "
            + formula.getElementNames()[element] + ": residual=" + (actual[element] - expected[element]));
      }
    }
  }

  /**
   * Commits a qualified species inventory and its material phases while preserving the caller's component order.
   *
   * @param result qualified full-fluid state
   * @param firstRoot requested EOS root of its first phase
   */
  private void copyResult(SystemInterface result, PhaseType firstRoot) {
    system.setMolarFlowRates(overallMoles(result));
    system.setNumberOfPhases(result.getNumberOfPhases());
    for (int phase = 0; phase < result.getNumberOfPhases(); phase++) {
      system.setPhaseIndex(phase, phase);
      system.setPhase(result.getPhase(phase).clone(), phase);
      // The density-based display label can differ from the root used to establish fugacity equality.
      system.setPhaseType(phase, phase == 0 ? firstRoot : PhaseType.AQUEOUS);
      system.setBeta(phase, result.getBeta(phase));
    }
    // Phase objects and the chemical balance basis changed even when their array indices stayed unchanged.
    system.chemicalReactionInit();
  }
}
