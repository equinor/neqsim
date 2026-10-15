package neqsim.thermodynamicoperations.flashops;

import java.util.Arrays;
import java.util.concurrent.CancellationException;
import neqsim.chemicalreactions.ChemicalReactionOperations;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.EosGeFlashModel;
import neqsim.thermodynamicoperations.flashops.reactiveflash.FormulaMatrix;

/**
 * Bounded coupling of general fluid equilibrium and aqueous chemical reactions.
 *
 * <p>
 * Each phase solve holds species inventories fixed and confines ions to water. Chemistry is solved on an isolated
 * aqueous phase, where the chemical solver can synchronize reaction-adjusted species totals. Only its conservative
 * species changes are transferred back to the full fluid. This avoids feeding stale overall species amounts and
 * ion-stripped phase trials into the generic coupled multiphase iteration. A result requires simultaneous molecular
 * fugacity equality, reaction equilibrium where an aqueous material phase exists, elemental conservation and
 * electroneutrality. An ion-free separated stream can retain registered reactive species without requiring an aqueous
 * phase; reaction coupling resumes if an aqueous phase appears at a later state. The caller is updated only after all
 * checks succeed; no EOS, hydrate or reaction parameters are changed.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
final class AqueousReactionEquilibrium {
  private static final int MAXIMUM_ITERATIONS = 30;
  private static final double REACTION_TOLERANCE = 2.0e-6;
  private final SystemInterface system;
  private double minimumTrialDistance = Double.NaN;
  private int iterations;
  /** Acceleration state and diagnostics for the most recent coupled solve. */
  private InventoryAcceleration acceleration;

  /**
   * Creates a coupled calculation without modifying the supplied fluid.
   *
   * @param system reactive fluid with an aqueous ionic inventory
   */
  public AqueousReactionEquilibrium(SystemInterface system) {
    this.system = system;
  }

  /**
   * Checks the model, composition and reaction scope.
   *
   * @param fluid system to inspect
   * @return true for reactive ionic EOS fluids without model-owned EOS-GE, forced, solid or wax phase roles
   */
  public static boolean isApplicable(SystemInterface fluid) {
    return !(fluid instanceof EosGeFlashModel) && fluid.isChemicalSystem() && fluid.hasIons()
        && fluid.getChemicalReactionOperations() != null && !fluid.doSolidPhaseCheck() && !fluid.isForcePhaseTypes()
        && !fluid.isMultiphaseWaxCheck();
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
          "Aqueous reaction coupling requires a reactive ionic fluid with freely selected fluid phases");
    }
    FormulaMatrix formula = new FormulaMatrix(system);
    double[] elements = formula.computeElementVector(overallMoles(system));
    SystemInterface work = system.clone();
    double reactionResidual = Double.NaN;
    acceleration = new InventoryAcceleration();
    for (int iteration = 0; iteration < MAXIMUM_ITERATIONS; iteration++) {
      iterations = iteration + 1;
      checkInterrupted();
      work.isChemicalSystem(false);
      double[] phaseInventory = FluidEquilibriumChecks.inventory(work);
      TPflash phaseFlash = new TPflash(work);
      try {
        phaseFlash.run();
        FluidEquilibriumChecks.validate(work, phaseInventory);
        verifyElements(formula, elements, overallMoles(work));
      } catch (CancellationException ex) {
        throw ex;
      } catch (IllegalStateException ex) {
        if (acceleration.restoreUnaccelerated(work)) {
          continue;
        }
        throw new IllegalStateException("Aqueous reaction phase equilibrium failed: " + ex.getMessage(), ex);
      }
      work.isChemicalSystem(true);
      int aqueousCount = 0;
      for (int phase = 0; phase < work.getNumberOfPhases(); phase++) {
        if (work.getPhase(phase).getType() == PhaseType.AQUEOUS) {
          aqueousCount++;
        }
      }
      if (aqueousCount == 0 && !FlashPhaseConstraints.hasIonicInventory(work)) {
        // Separated gas/oil streams retain the registered reaction species but can have no aqueous inventory.
        // The frozen-species phase solve is already qualified; no aqueous reaction residual is defined here.
        checkInterrupted();
        copyResult(work);
        minimumTrialDistance = minimumTrialDistance(phaseFlash);
        return;
      }
      if (aqueousCount != 1) {
        if (acceleration.restoreUnaccelerated(work)) {
          continue;
        }
        throw new IllegalStateException("Aqueous reaction coupling requires exactly one aqueous material phase");
      }
      reactionResidual = work.getChemicalReactionOperations().getMaximumAbsoluteReactionLogResidual();
      if (acceleration.residualWorsened(reactionResidual) && acceleration.restoreUnaccelerated(work)) {
        continue;
      }
      if (Double.isFinite(reactionResidual) && reactionResidual <= REACTION_TOLERANCE) {
        double charge = work.getChemicalReactionOperations().getReactivePhaseChargeMoles();
        if (!Double.isFinite(charge) || Math.abs(charge) > 1.0e-8) {
          throw new IllegalStateException("Aqueous reaction equilibrium failed aqueous electroneutrality");
        }
        checkInterrupted();
        copyResult(work);
        minimumTrialDistance = minimumTrialDistance(phaseFlash);
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
        if (acceleration.restoreUnaccelerated(work)) {
          continue;
        }
        throw new IllegalStateException("Aqueous reaction aqueous chemistry did not converge: reaction residual="
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
          throw new IllegalStateException("Aqueous reaction equilibrium produced an invalid species inventory");
        }
        updated[component] = Math.max(0.0, updated[component]);
      }
      verifyElements(formula, elements, updated);
      double[] candidate = acceleration.nextInventory(work, overallMoles(work), updated, reactionResidual);
      try {
        verifyElements(formula, elements, candidate);
        work.setMolarFlowRates(candidate);
      } catch (IllegalStateException ex) {
        if (!acceleration.restoreUnaccelerated(work)) {
          throw ex;
        }
      }
    }
    throw new IllegalStateException("Aqueous reaction equilibrium exceeded " + MAXIMUM_ITERATIONS
        + " coupling iterations: reaction residual=" + reactionResidual);
  }

  /**
   * Safeguarded vector secant acceleration for the conservative reaction-inventory fixed point.
   *
   * <p>
   * The chemical solver supplies a stoichiometric inventory increment. Scaling the complete increment preserves its
   * element and charge balances. A scalar inverse-Jacobian estimate uses consecutive increments and the actual prior
   * step, with bounded extrapolation and a fraction-to-boundary safeguard. Every extrapolation remains a trial: the
   * next phase/reaction residual must improve and all physical acceptance checks remain unchanged. A failed trial
   * restores the unaccelerated chemical update and discards its secant history. Phase-topology changes also discard the
   * history. The vector Aitken approach follows Irons and Tuck, International Journal for Numerical Methods in
   * Engineering 1 (1969), 275-277, doi:10.1002/nme.1620010306; positivity and residual rollback are safeguards here.
   * </p>
   */
  private static final class InventoryAcceleration {
    /** Largest extrapolation factor allowed for the scalar secant estimate. */
    private static final double MAXIMUM_FACTOR = 10.0;
    /** Previous unaccelerated reaction-inventory increment. */
    private double[] previousDelta;
    /** Previous actual inventory step, including any acceleration. */
    private double[] previousStep;
    /** Conservative unaccelerated inventory retained while an extrapolation is evaluated. */
    private double[] fallback;
    /** Reaction residual before the pending extrapolation. */
    private double previousResidual;
    /** Number of extrapolated inventory trials. */
    private int attemptedTrials;
    /** Number of extrapolated trials restored to the unaccelerated update. */
    private int rejectedTrials;
    /** Material and requested phase roles associated with the secant history. */
    private PhaseType[] previousTopology;

    /**
     * Tests the residual safeguard for a pending extrapolation.
     *
     * @param residual maximum reaction log residual after the next phase solve
     * @return whether the extrapolation worsened the residual or made it nonfinite
     */
    private boolean residualWorsened(double residual) {
      return fallback != null && (!Double.isFinite(residual) || residual > previousResidual);
    }

    /**
     * Restores a failed extrapolation to its conservative unaccelerated chemical update.
     *
     * @param fluid isolated coupling work system
     * @return whether an extrapolation was pending and was restored
     */
    private boolean restoreUnaccelerated(SystemInterface fluid) {
      if (fallback == null) {
        return false;
      }
      fluid.setMolarFlowRates(fallback);
      rejectedTrials++;
      fallback = null;
      previousDelta = null;
      previousStep = null;
      previousTopology = null;
      return true;
    }

    /**
     * Constructs the next conservative inventory, with bounded secant acceleration when history permits.
     *
     * @param fluid initialized fluid defining the current component basis and phase topology
     * @param current inventory before the aqueous chemical solve
     * @param corrected inventory after transferring the converged aqueous chemical changes
     * @param residual reaction residual before the aqueous chemical solve
     * @return trial inventory; a failed extrapolation can be restored through {@link #restoreUnaccelerated}
     */
    private double[] nextInventory(SystemInterface fluid, double[] current, double[] corrected, double residual) {
      PhaseType[] topology = new PhaseType[2 * fluid.getNumberOfPhases()];
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        topology[2 * phase] = fluid.getPhase(phase).getType();
        topology[2 * phase + 1] = fluid.getPhaseType(phase);
      }
      if (!Arrays.equals(topology, previousTopology)
          || previousDelta != null && previousDelta.length != current.length) {
        previousDelta = null;
        previousStep = null;
      }
      previousTopology = topology;
      double[] delta = new double[current.length];
      for (int component = 0; component < delta.length; component++) {
        delta[component] = corrected[component] - current[component];
      }
      double factor = 1.0;
      if (previousDelta != null) {
        double numerator = 0.0;
        double denominator = 0.0;
        for (int component = 0; component < delta.length; component++) {
          double difference = delta[component] - previousDelta[component];
          numerator -= previousStep[component] * difference;
          denominator += difference * difference;
        }
        if (denominator > 0.0) {
          factor = Math.max(1.0, Math.min(MAXIMUM_FACTOR, numerator / denominator));
        }
        for (int component = 0; component < delta.length; component++) {
          if (delta[component] < 0.0) {
            factor = Math.min(factor, 0.99 * current[component] / -delta[component]);
          }
        }
        if (!Double.isFinite(factor) || factor < 1.0) {
          factor = 1.0;
        }
      }
      previousStep = delta.clone();
      previousDelta = delta;
      fallback = null;
      if (factor == 1.0) {
        return corrected;
      }
      attemptedTrials++;
      fallback = corrected.clone();
      previousResidual = residual;
      double[] candidate = new double[current.length];
      for (int component = 0; component < candidate.length; component++) {
        previousStep[component] *= factor;
        candidate[component] = current[component] + previousStep[component];
      }
      return candidate;
    }
  }

  /** @return number of phase/reaction coupling iterations in the most recent calculation */
  public int getIterations() {
    return iterations;
  }

  /** @return number of extrapolated inventory trials in the most recent calculation */
  int getAcceleratedTrialCount() {
    return acceleration == null ? 0 : acceleration.attemptedTrials;
  }

  /** @return number of extrapolations rejected and restored during the most recent calculation */
  int getRejectedAcceleratedTrialCount() {
    return acceleration == null ? 0 : acceleration.rejectedTrials;
  }

  /** @return minimum fluid trial distance at the accepted speciation, or NaN if no result was accepted */
  public double getMinimumTrialDistance() {
    return minimumTrialDistance;
  }

  /**
   * Reads the normalized stability evidence from the same general phase solve.
   *
   * @param flash completed fluid flash
   * @return minimum finite trial distance, or NaN if no stability trial was performed
   */
  private static double minimumTrialDistance(Flash flash) {
    double minimum = Double.POSITIVE_INFINITY;
    for (double distance : flash.getLastTangentPlaneDistances()) {
      if (Double.isFinite(distance)) {
        minimum = Math.min(minimum, distance);
      }
    }
    return minimum == Double.POSITIVE_INFINITY ? Double.NaN : minimum;
  }

  /** Reject cancellation before constructing trials or committing a result. */
  private static void checkInterrupted() {
    if (Thread.currentThread().isInterrupted()) {
      throw new CancellationException("Aqueous reaction equilibrium interrupted");
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
        throw new IllegalStateException("Aqueous reaction equilibrium failed elemental conservation for "
            + formula.getElementNames()[element] + ": residual=" + (actual[element] - expected[element]));
      }
    }
  }

  /**
   * Commits a qualified species inventory and its material phases while preserving the caller's component order.
   *
   * @param result qualified full-fluid state
   */
  private void copyResult(SystemInterface result) {
    system.setMolarFlowRates(overallMoles(result));
    system.setNumberOfPhases(result.getNumberOfPhases());
    for (int phase = 0; phase < result.getNumberOfPhases(); phase++) {
      system.setPhaseIndex(phase, phase);
      system.setPhase(result.getPhase(phase).clone(), phase);
      // The density-based display label can differ from the root used to establish fugacity equality.
      system.setPhaseType(phase, result.getPhaseType(phase));
      system.setBeta(phase, result.getBeta(phase));
    }
    // Phase objects and the chemical balance basis changed even when their array indices stayed unchanged.
    system.chemicalReactionInit();
  }
}
