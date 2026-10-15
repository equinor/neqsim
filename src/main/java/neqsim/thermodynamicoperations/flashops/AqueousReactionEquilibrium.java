package neqsim.thermodynamicoperations.flashops;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CancellationException;
import Jama.Matrix;
import neqsim.chemicalreactions.ChemicalReactionOperations;
import neqsim.chemicalreactions.chemicalreaction.ChemicalReaction;
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
 * phase when conservative reactive aqueous stability trials qualify that neutral endpoint. A negative or unqualified
 * reactive trial rejects the endpoint; constructing a new reaction-stabilized split is outside that absence check.
 * Reaction coupling resumes if the molecular phase solve creates an aqueous phase. The caller is updated only after all
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
  /** Number of accepted conservative reaction-extent corrections. */
  private int reactionNewtonSteps;
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
    reactionNewtonSteps = 0;
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
    double previousCouplingResidual = Double.POSITIVE_INFINITY;
    boolean coupledNewtonActive = false;
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
        if (acceptNeutralBoundary(work, formula, elements)) {
          return;
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
        // Exact-zero ionic products can still form a reaction-stabilized aqueous phase. Qualify the molecular
        // endpoint against conservative reactive aqueous trials before accepting that boundary.
        boolean reactiveInventory = false;
        for (ChemicalReaction reaction : work.getChemicalReactionOperations().getReactionList()
            .getChemicalReactionList()) {
          for (String name : reaction.getNames()) {
            reactiveInventory |= work.getComponent(name).getNumberOfmoles() > 0.0;
          }
        }
        if (reactiveInventory) {
          if (acceptNeutralBoundary(work, formula, elements)) {
            return;
          }
          throw new IllegalStateException("Could not establish reactive aqueous stability of the neutral endpoint");
        }
        checkInterrupted();
        copyResult(work);
        minimumTrialDistance = minimumTrialDistance(phaseFlash);
        return;
      }
      if (aqueousCount != 1) {
        if (acceleration.restoreUnaccelerated(work)) {
          continue;
        }
        if (aqueousCount == 0 && acceptNeutralBoundary(work, formula, elements)) {
          return;
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

      // Keep the inexpensive fixed point for readily coupled fluids. Strong gas buffering can make that
      // iteration nearly stationary even when each isolated aqueous chemical solve is well converged.
      boolean attemptedNewton = coupledNewtonActive
          || iteration >= 6 && reactionResidual > 0.5 * previousCouplingResidual;
      previousCouplingResidual = reactionResidual;
      if (attemptedNewton) {
        SystemInterface corrected = reactionNewtonTrial(work);
        if (corrected != null) {
          verifyElements(formula, elements, overallMoles(corrected));
          work = corrected;
          acceleration.clearHistory();
          reactionNewtonSteps++;
          coupledNewtonActive = true;
          continue;
        }
      }
      SystemInterface aqueous = work.phaseToSystem("aqueous");
      // phaseToSystem replaces both the material basis and the reactive phase index. Rebuild the chemical
      // initialization on that isolated basis rather than retaining its parent's phase-bound helpers.
      aqueous.getChemicalReactionOperations().reinitialize();
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
        if (!attemptedNewton) {
          SystemInterface corrected = reactionNewtonTrial(work);
          if (corrected != null) {
            verifyElements(formula, elements, overallMoles(corrected));
            work = corrected;
            acceleration.clearHistory();
            reactionNewtonSteps++;
            coupledNewtonActive = true;
            continue;
          }
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
   * Commits a neutral boundary only after conservative recombination and reactive aqueous stability qualification.
   *
   * @param work owned trial at the failed or disappearing aqueous boundary
   * @param formula complete elemental and charge basis
   * @param elements original conserved inventory
   * @return whether a qualified endpoint was committed
   */
  private boolean acceptNeutralBoundary(SystemInterface work, FormulaMatrix formula, double[] elements) {
    ReactiveAqueousBoundary.Result endpoint = ReactiveAqueousBoundary.trySolve(work);
    if (endpoint == null) {
      return false;
    }
    verifyElements(formula, elements, overallMoles(endpoint.getFluid()));
    checkInterrupted();
    copyResult(endpoint.getFluid());
    minimumTrialDistance = endpoint.getNeutralDistance();
    return true;
  }

  /**
   * Attempts a conservative coupled correction without changing the current fluid.
   *
   * <p>
   * The unknowns are reaction extents, so every trial preserves all linear invariants of the registered reaction
   * network, including elemental amounts and spectator inventories. Reaction affinities are evaluated after molecular
   * phase equilibrium. Differentiating that reduced residual includes the response of the phase split to reaction,
   * which an isolated aqueous chemical Jacobian omits. Centered perturbations use local fixed-topology phase solves;
   * accepted steps use the complete phase flash and its physical checks. A fraction-to-boundary line search requires a
   * decrease of the actual squared reaction-log residual. Singular reaction bases, unavailable derivatives and failed
   * trials leave the original conservative fixed point available.
   * </p>
   *
   * @param current phase-equilibrated reactive fluid with one aqueous material phase
   * @return qualified lower-residual trial, or null if no correction can be qualified
   */
  private SystemInterface reactionNewtonTrial(SystemInterface current) {
    checkInterrupted();
    try {
      return computeReactionNewtonTrial(current);
    } catch (CancellationException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      // A derivative or linear solve is optional; the original chemical update remains available.
      return null;
    }
  }

  /**
   * Builds and safeguards the Newton correction in scaled reaction-extent coordinates.
   *
   * @param current phase-equilibrated fluid
   * @return accepted trial, or null when no merit-decreasing positive step exists
   */
  private SystemInterface computeReactionNewtonTrial(SystemInterface current) {
    ArrayList<ChemicalReaction> reactions = current.getChemicalReactionOperations().getReactionList()
        .getChemicalReactionList();
    int nr = reactions.size();
    if (nr == 0) {
      return null;
    }
    double[] inventory = overallMoles(current);
    double[][] stoichiometry = new double[nr][inventory.length];
    double[] scale = new double[nr];
    double[] residual = reactionResiduals(current, reactions);
    double[][] jacobian = new double[nr][nr];
    for (int reaction = 0; reaction < nr; reaction++) {
      checkInterrupted();
      String[] names = reactions.get(reaction).getNames();
      double[] coefficients = reactions.get(reaction).getStocCoefs();
      for (int term = 0; term < names.length; term++) {
        stoichiometry[reaction][current.getComponent(names[term]).getComponentNumber()] = coefficients[term];
      }
      double available = Double.POSITIVE_INFINITY;
      for (int component = 0; component < inventory.length; component++) {
        double coefficient = stoichiometry[reaction][component];
        if (coefficient != 0.0) {
          available = Math.min(available, inventory[component] / Math.abs(coefficient));
        }
      }
      if (!Double.isFinite(available) || available <= 0.0) {
        return null;
      }
      scale[reaction] = available;
      double[] plusAmounts = inventory.clone();
      double[] minusAmounts = inventory.clone();
      double fraction = 1.0e-3;
      for (int component = 0; component < inventory.length; component++) {
        double perturbation = fraction * available * stoichiometry[reaction][component];
        plusAmounts[component] += perturbation;
        minusAmounts[component] -= perturbation;
      }
      double[] plus = reactionResiduals(fixedPhaseReactionTrial(current, plusAmounts), reactions);
      double[] minus = reactionResiduals(fixedPhaseReactionTrial(current, minusAmounts), reactions);
      for (int row = 0; row < nr; row++) {
        jacobian[row][reaction] = (plus[row] - minus[row]) / (2.0 * fraction);
      }
    }
    double[] correction = new Matrix(jacobian).solve(new Matrix(residual, nr)).getColumnPackedCopy();
    double[] delta = new double[inventory.length];
    double alpha = 1.0;
    for (int component = 0; component < inventory.length; component++) {
      for (int reaction = 0; reaction < nr; reaction++) {
        delta[component] -= stoichiometry[reaction][component] * scale[reaction] * correction[reaction];
      }
      if (!Double.isFinite(delta[component])) {
        return null;
      }
      if (delta[component] < 0.0) {
        alpha = Math.min(alpha, 0.99 * inventory[component] / -delta[component]);
      }
    }
    double initialMerit = residualMerit(residual);
    if (!Double.isFinite(initialMerit) || !Double.isFinite(alpha) || alpha <= 0.0) {
      return null;
    }
    for (int backtrack = 0; backtrack < 10; backtrack++) {
      checkInterrupted();
      double[] candidate = inventory.clone();
      for (int component = 0; component < candidate.length; component++) {
        candidate[component] += alpha * delta[component];
      }
      try {
        SystemInterface trial = reactionTrial(current, candidate);
        double merit = residualMerit(reactionResiduals(trial, reactions));
        try {
          SystemInterface projected = chemicalProjection(trial);
          if (projected != null) {
            double projectedMerit = residualMerit(reactionResiduals(projected, reactions));
            if (Double.isFinite(projectedMerit) && projectedMerit < merit) {
              trial = projected;
              merit = projectedMerit;
            }
          }
        } catch (CancellationException ex) {
          throw ex;
        } catch (RuntimeException projectionFailure) {
          // The raw conservative Newton candidate remains available.
        }
        if (Double.isFinite(merit) && merit < initialMerit * (1.0 - 1.0e-4 * alpha)) {
          return trial;
        }
      } catch (CancellationException ex) {
        throw ex;
      } catch (RuntimeException ex) {
        // Reduce the same conservative extent step when a phase or chemical trial cannot be qualified.
      }
      alpha *= 0.5;
    }
    return null;
  }

  /**
   * Supplies an optional nonlinear chemical correction inside the coupled line search.
   *
   * @param current conservative phase-equilibrated Newton trial
   * @return phase-equilibrated trial after transferring the isolated aqueous chemical increment, or null
   */
  private SystemInterface chemicalProjection(SystemInterface current) {
    SystemInterface aqueous = current.phaseToSystem("aqueous");
    aqueous.getChemicalReactionOperations().reinitialize();
    aqueous.setNumberOfPhases(1);
    aqueous.setPhaseType(0, PhaseType.AQUEOUS);
    aqueous.setBeta(0, 1.0);
    aqueous.init(1);
    double[] before = overallMoles(aqueous);
    if (!aqueous.getChemicalReactionOperations().solveChemEq(0, 1)) {
      return null;
    }
    double[] after = overallMoles(aqueous);
    double[] updated = overallMoles(current);
    double[] increment = new double[updated.length];
    for (int component = 0; component < updated.length; component++) {
      increment[component] = after[component] - before[component];
    }
    double[] conservative = conservativeReactionIncrement(current, increment, before, after);
    for (int component = 0; component < updated.length; component++) {
      updated[component] += conservative[component];
    }
    return reactionTrial(current, updated);
  }

  /**
   * Removes qualified roundoff from a chemical increment using the complete registered reaction stoichiometric span.
   *
   * @param current reference fluid defining the active independent reaction basis
   * @param increment proposed full-fluid chemical inventory change
   * @param before aqueous species amounts before chemistry
   * @param after aqueous species amounts after chemistry
   * @return conservative reconstructed increment
   */
  private double[] conservativeReactionIncrement(SystemInterface current, double[] increment, double[] before,
      double[] after) {
    ArrayList<ChemicalReaction> reactions = current.getChemicalReactionOperations().getReactionList()
        .getChemicalReactionList();
    double[][] columns = new double[increment.length][reactions.size()];
    for (int reaction = 0; reaction < reactions.size(); reaction++) {
      String[] names = reactions.get(reaction).getNames();
      double[] coefficients = reactions.get(reaction).getStocCoefs();
      for (int term = 0; term < names.length; term++) {
        columns[current.getComponent(names[term]).getComponentNumber()][reaction] = coefficients[term];
      }
    }
    Matrix basis = new Matrix(columns);
    Matrix delta = new Matrix(increment, increment.length);
    double[] reconstructed = basis.times(basis.solve(delta)).getColumnPackedCopy();
    double arithmeticScale = 0.0;
    double incrementScale = 0.0;
    for (int component = 0; component < increment.length; component++) {
      arithmeticScale = Math.max(arithmeticScale, Math.max(Math.abs(before[component]), Math.abs(after[component])));
      incrementScale = Math.max(incrementScale, Math.abs(increment[component]));
    }
    double tolerance = Math.max(64.0 * Math.ulp(arithmeticScale), 1.0e-9 * incrementScale);
    for (int component = 0; component < increment.length; component++) {
      boolean spectator = true;
      for (int reaction = 0; reaction < reactions.size(); reaction++) {
        spectator &= columns[component][reaction] == 0.0;
      }
      if (spectator && increment[component] != 0.0) {
        throw new IllegalStateException("A chemical projection changed a spectator species");
      }
      if (!Double.isFinite(reconstructed[component])
          || Math.abs(reconstructed[component] - increment[component]) > tolerance) {
        throw new IllegalStateException("A chemical projection left the registered reaction stoichiometric span");
      }
    }
    return reconstructed;
  }

  /**
   * Equilibrates a small derivative perturbation while retaining the current phase topology.
   *
   * @param current phase-equilibrated fluid
   * @param amounts perturbed full-fluid species inventory
   * @return locally phase-equilibrated perturbed fluid
   */
  private SystemInterface fixedPhaseReactionTrial(SystemInterface current, double[] amounts) {
    SystemInterface trial = current.clone();
    int np = current.getNumberOfPhases();
    double[] original = overallMoles(current);
    double[][] phaseAmounts = new double[np][amounts.length];
    double[] phaseTotals = new double[np];
    double total = 0.0;
    for (int component = 0; component < amounts.length; component++) {
      total += amounts[component];
      for (int phase = 0; phase < np; phase++) {
        if (original[component] > 0.0) {
          phaseAmounts[phase][component] = current.getPhase(phase).getComponent(component).getNumberOfMolesInPhase()
              * amounts[component] / original[component];
        } else if (amounts[component] != 0.0) {
          throw new IllegalStateException("A derivative perturbation cannot introduce an absent species");
        }
        phaseTotals[phase] += phaseAmounts[phase][component];
      }
    }
    trial.setMolarFlowRates(amounts);
    trial.isChemicalSystem(false);
    trial.setNumberOfPhases(np);
    for (int phase = 0; phase < np; phase++) {
      trial.setBeta(phase, phaseTotals[phase] / total);
      for (int component = 0; component < amounts.length; component++) {
        trial.getPhase(phase).getComponent(component).setx(phaseAmounts[phase][component] / phaseTotals[phase]);
        trial.getPhase(phase).getComponent(component).setNumberOfMolesInPhase(phaseAmounts[phase][component]);
      }
    }
    trial.init(3);
    if (np > 1) {
      SysNewtonRhapsonTPflash solver = new SysNewtonRhapsonTPflash(trial, np, amounts.length);
      for (int correction = 0; correction < 8; correction++) {
        checkInterrupted();
        double error = solver.solve();
        if (error < 1e-10) {
          break;
        }
      }
    }
    FluidEquilibriumChecks.validate(trial, amounts);
    trial.isChemicalSystem(true);
    verifyReactionConfiguration(current, trial);
    return trial;
  }

  /**
   * Runs the complete frozen-species phase solve for a conservative reaction trial.
   *
   * @param current unchanged reference fluid
   * @param amounts proposed species inventory
   * @return physically checked trial retaining its aqueous reaction host
   */
  private SystemInterface reactionTrial(SystemInterface current, double[] amounts) {
    checkInterrupted();
    for (double amount : amounts) {
      if (!Double.isFinite(amount) || amount < 0.0) {
        throw new IllegalStateException("A reaction trial requires finite nonnegative species amounts");
      }
    }
    SystemInterface trial = current.clone();
    trial.setMolarFlowRates(amounts);
    trial.isChemicalSystem(false);
    TPflash flash = new TPflash(trial);
    flash.run();
    FluidEquilibriumChecks.validate(trial, amounts);
    trial.isChemicalSystem(true);
    verifyReactionConfiguration(current, trial);
    int aqueousCount = 0;
    for (int phase = 0; phase < trial.getNumberOfPhases(); phase++) {
      if (trial.getPhase(phase).getType() == PhaseType.AQUEOUS) {
        aqueousCount++;
      }
    }
    if (aqueousCount != 1) {
      throw new IllegalStateException("A reaction extent trial requires one aqueous material phase");
    }
    return trial;
  }

  /**
   * Ensures a phase trial cannot silently discover components or replace the caller's configured reactions.
   *
   * @param current unchanged reference fluid
   * @param trial candidate fluid
   */
  private void verifyReactionConfiguration(SystemInterface current, SystemInterface trial) {
    if (!Arrays.equals(current.getComponentNames(), trial.getComponentNames())
        || current.getChemicalReactionConcentrationBasis() != trial.getChemicalReactionConcentrationBasis()
        || current.getChemicalReactionDataSource() != trial.getChemicalReactionDataSource()) {
      throw new IllegalStateException("A reaction trial changed the configured component or reaction basis");
    }
    ArrayList<ChemicalReaction> expected = current.getChemicalReactionOperations().getReactionList()
        .getChemicalReactionList();
    ArrayList<ChemicalReaction> actual = trial.getChemicalReactionOperations().getReactionList()
        .getChemicalReactionList();
    if (expected.size() != actual.size()) {
      throw new IllegalStateException("A reaction trial changed the registered reaction set");
    }
    for (int reaction = 0; reaction < expected.size(); reaction++) {
      ChemicalReaction first = expected.get(reaction);
      ChemicalReaction second = actual.get(reaction);
      if (!first.getName().equals(second.getName()) || !Arrays.equals(first.getNames(), second.getNames())
          || !Arrays.equals(first.getStocCoefs(), second.getStocCoefs())
          || !Arrays.equals(first.getEquilibriumConstantCoefficients(), second.getEquilibriumConstantCoefficients())
          || first.getKineticRateLaw() != second.getKineticRateLaw() || first.getRateFactor() != second.getRateFactor()
          || first.getActivationEnergy() != second.getActivationEnergy()
          || first.getReferenceTemperature() != second.getReferenceTemperature()) {
        throw new IllegalStateException("A reaction trial changed configured reaction parameters");
      }
    }
  }

  /**
   * Reads finite reaction affinities in the fixed reaction-list order.
   *
   * @param fluid initialized reactive fluid
   * @param reactions ordered reaction basis
   * @return reaction log residuals
   */
  private double[] reactionResiduals(SystemInterface fluid, ArrayList<ChemicalReaction> reactions) {
    Map<String, Double> values = fluid.getChemicalReactionOperations().getReactionLogResiduals();
    double[] residual = new double[reactions.size()];
    for (int reaction = 0; reaction < residual.length; reaction++) {
      Double value = values.get(reactions.get(reaction).getName());
      if (value == null || !Double.isFinite(value)) {
        throw new IllegalStateException("A reaction trial requires finite affinities for every registered reaction");
      }
      residual[reaction] = value;
    }
    return residual;
  }

  /**
   * Evaluates the coupled line-search merit on dimensionless reaction residuals.
   *
   * @param residual reaction log residuals
   * @return squared Euclidean residual norm
   */
  private double residualMerit(double[] residual) {
    double merit = 0.0;
    for (double value : residual) {
      merit += value * value;
    }
    return merit;
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

    /** Discards secant history after a different conservative correction, retaining calculation diagnostics. */
    private void clearHistory() {
      previousDelta = null;
      previousStep = null;
      previousTopology = null;
      fallback = null;
    }

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

  /** @return number of accepted coupled reaction-extent Newton steps in the most recent calculation */
  int getReactionNewtonStepCount() {
    return reactionNewtonSteps;
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
    verifyReactionConfiguration(system, result);
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
    system.getChemicalReactionOperations().reinitialize();
  }
}
