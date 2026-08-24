package neqsim.thermodynamicoperations.flashops;

import static neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
import org.ejml.simple.SimpleMatrix;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.HybridEosGeFlashModel;
import neqsim.thermo.system.SystemInterface;

/**
 * Dedicated multiphase strategy for EOS-gas, EOS-oil and GE-aqueous phase topologies.
 *
 * <p>
 * The strategy reuses the general multiphase fugacity and phase-fraction equations, but starts from model-owned fixed
 * creation-order roles and disables phase-discovery logic that can replace those roles with clones of the wrong phase
 * implementation. Phase disappearance only changes the active {@code phaseIndex} mapping; a later flash restores the
 * original gas, oil and aqueous objects through {@link HybridEosGeFlashModel#prepareHybridEosGeFlash()}.
 * </p>
 *
 * @author NeqSim
 */
public class TPHybridEosGeFlash extends TPmultiflash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Model that owns phase roles and final acceptance. */
  private final HybridEosGeFlashModel hybridModel;

  /** Maximum fixed-topology outer iterations. */
  private static final int MAXIMUM_HYBRID_ITERATIONS = 50;

  /** Fugacity-objective residual used to terminate the fixed-topology solve. */
  private static final double HYBRID_SOLVER_TOLERANCE = 1.0e-10;

  /** Maximum aqueous-chemistry / phase-equilibrium outer iterations. */
  private static final int MAXIMUM_REACTIVE_ITERATIONS = 100;

  /** Minimum outer iterations used to certify a coupled reactive state. */
  private static final int MINIMUM_REACTIVE_ITERATIONS = 3;

  /** Sum of aqueous mole-fraction changes accepted for chemical-equilibrium convergence. */
  private static final double REACTIVE_COMPOSITION_TOLERANCE = 1.0e-10;

  /** Positive floor for trace species after conservative reaction-delta projection. */
  private static final double MINIMUM_COUPLED_COMPONENT_MOLES = 1.0e-45;

  /** Extra aqueous phase-fraction room retained above the fixed ionic inventory. */
  private static final double HYBRID_ION_CAPACITY_MARGIN = 100.0 * phaseFractionMinimumLimit;

  /** Largest ionic-concentration increase allowed in one projected beta correction. */
  private static final double HYBRID_ION_CONCENTRATION_STEP_LIMIT = 2.0;

  /** Reaction-adjusted overall component fractions used by the coupled phase solve. */
  private transient double[] coupledOverallFractions;

  /** Exact reaction-adjusted overall component mole inventory. */
  private transient double[] coupledOverallMoles;

  /**
   * Create a hybrid multiphase flash.
   *
   * @param system thermodynamic system to solve
   * @param hybridModel model owning the creation-order role contract
   */
  public TPHybridEosGeFlash(SystemInterface system, HybridEosGeFlashModel hybridModel) {
    super(system, false);
    this.hybridModel = hybridModel;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    if (system.doSolidPhaseCheck() || system.isMultiphaseWaxCheck()) {
      throw new UnsupportedOperationException(
          "Hybrid EOS-GE TPflash currently supports fluid phases only; disable solid and wax checks.");
    }

    hybridModel.prepareHybridEosGeFlash();
    system.init(1);
    double residual = Double.POSITIVE_INFINITY;
    double chemicalDeviation = system.isChemicalSystem() ? Double.POSITIVE_INFINITY : 0.0;
    boolean coupledConverged = !system.isChemicalSystem();
    int maximumOuterIterations = system.isChemicalSystem() ? MAXIMUM_REACTIVE_ITERATIONS : 1;

    if (system.isChemicalSystem()) {
      // Establish a feed-balanced phase split before chemistry sees the role-specific seed compositions.
      residual = solveFixedTopologyPhaseEquilibrium();
    }
    for (int outerIteration = 0; outerIteration < maximumOuterIterations; outerIteration++) {
      if (system.isChemicalSystem()) {
        chemicalDeviation = solveAqueousChemicalEquilibrium(outerIteration == 0);
      }
      residual = solveFixedTopologyPhaseEquilibrium();
      if (!system.isChemicalSystem()) {
        coupledConverged = true;
        break;
      }
      coupledConverged = outerIteration + 1 >= MINIMUM_REACTIVE_ITERATIONS
          && chemicalDeviation <= REACTIVE_COMPOSITION_TOLERANCE && Double.isFinite(residual)
          && residual <= HYBRID_SOLVER_TOLERANCE;
      if (coupledConverged) {
        break;
      }
    }

    if (!coupledConverged) {
      throw new IllegalStateException("Reactive hybrid EOS-GE TPflash did not converge: chemicalDeviation="
          + chemicalDeviation + ", solverResidual=" + residual + ", "
          + hybridModel.getHybridEosGeFlashDiagnostics(phaseFractionMinimumLimit));
    }

    if (!hybridModel.finishHybridEosGeFlash(phaseFractionMinimumLimit)) {
      throw new IllegalStateException("Hybrid EOS-GE TPflash did not produce an acceptable state: "
          + hybridModel.getHybridEosGeFlashDiagnostics(phaseFractionMinimumLimit) + ", solverResidual=" + residual);
    }
  }

  /**
   * Solve the fixed gas-oil-aqueous phase topology at the current chemical composition.
   *
   * @return final fugacity-objective residual
   */
  private double solveFixedTopologyPhaseEquilibrium() {
    double residual = Double.POSITIVE_INFINITY;
    for (int iteration = 0; iteration < MAXIMUM_HYBRID_ITERATIONS; iteration++) {
      setDoubleArrays();
      checkOneRemove = false;
      removePhase = false;
      residual = solveBeta();
      hybridModel.restoreHybridEosGeActivePhaseTypes();
      system.init(1);
      if (Double.isFinite(residual) && residual <= HYBRID_SOLVER_TOLERANCE && iteration >= 4) {
        break;
      }
    }
    return residual;
  }

  /**
   * Re-equilibrate reactions in the model-owned aqueous phase and measure the composition update.
   *
   * @param initialise whether to request the linear-programming initial estimate before Newton refinement
   * @return sum of absolute aqueous mole-fraction changes
   */
  private double solveAqueousChemicalEquilibrium(boolean initialise) {
    int aqueousPhaseNumber = getHybridAqueousPhaseNumber();
    if (aqueousPhaseNumber < 0) {
      throw new IllegalStateException("Reactive hybrid EOS-GE flash requires an active aqueous phase.");
    }

    system.init(1);
    int numberOfComponents = system.getPhase(aqueousPhaseNumber).getNumberOfComponents();
    double[] oldComposition = new double[numberOfComponents];
    double[] oldAqueousMoles = new double[numberOfComponents];
    for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
      oldComposition[componentIndex] = system.getPhase(aqueousPhaseNumber).getComponent(componentIndex).getx();
      oldAqueousMoles[componentIndex] = system.getPhase(aqueousPhaseNumber).getComponent(componentIndex)
          .getNumberOfMolesInPhase();
    }
    if (initialise) {
      system.getChemicalReactionOperations().solveChemEq(aqueousPhaseNumber, 0);
    }
    system.getChemicalReactionOperations().solveChemEq(aqueousPhaseNumber, 1);
    hybridModel.restoreHybridEosGeActivePhaseTypes();
    updateCoupledOverallComposition(aqueousPhaseNumber, oldAqueousMoles);
    system.init(1);

    double chemicalDeviation = 0.0;
    for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
      double moleFraction = system.getPhase(aqueousPhaseNumber).getComponent(componentIndex).getx();
      if (!Double.isFinite(moleFraction) || moleFraction < 0.0) {
        return Double.POSITIVE_INFINITY;
      }
      chemicalDeviation += Math.abs(oldComposition[componentIndex] - moleFraction);
    }
    return chemicalDeviation;
  }

  /**
   * Find the active phase number mapped to the model-owned GE aqueous object.
   *
   * @return active aqueous phase number, or {@code -1} if no aqueous role is active
   */
  private int getHybridAqueousPhaseNumber() {
    for (int phaseNumber = 0; phaseNumber < system.getNumberOfPhases(); phaseNumber++) {
      if (hybridModel.isHybridEosGeAqueousPhase(phaseNumber)) {
        return phaseNumber;
      }
    }
    return -1;
  }

  /**
   * Capture the reaction-adjusted species inventory before the next interphase-equilibrium solve.
   *
   * <p>
   * Chemical reactions conserve elements but change species amounts. The ordinary overall {@code z} values retain the
   * feed species inventory, so using them here would remove bicarbonate, carbonate and other generated species during
   * the next beta/composition update. The coupled inventory starts from the exact overall species amounts and applies
   * only the mole changes produced by aqueous chemistry. It deliberately does not reconstruct the inventory from the
   * latest phase split, because repeating that operation would accumulate the phase solver's finite material-balance
   * residual. Synchronizing the exact inventory and its total preserves elements and charge while allowing reactions to
   * change the total number of species moles.
   * </p>
   *
   * @param aqueousPhaseNumber active aqueous phase number
   * @param oldAqueousMoles aqueous component amounts before chemical equilibrium
   */
  private void updateCoupledOverallComposition(int aqueousPhaseNumber, double[] oldAqueousMoles) {
    int numberOfComponents = system.getPhase(0).getNumberOfComponents();
    if (coupledOverallMoles == null || coupledOverallMoles.length != numberOfComponents) {
      coupledOverallMoles = new double[numberOfComponents];
      for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
        coupledOverallMoles[componentIndex] = system.getPhase(0).getComponent(componentIndex).getNumberOfmoles();
      }
    }

    double[] reactionDeltas = getConservativeReactionDeltas(aqueousPhaseNumber, oldAqueousMoles);
    coupledOverallFractions = new double[numberOfComponents];
    double totalMoles = 0.0;
    for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
      coupledOverallMoles[componentIndex] += reactionDeltas[componentIndex];
      if (!Double.isFinite(coupledOverallMoles[componentIndex]) || coupledOverallMoles[componentIndex] < -1.0e-9) {
        coupledOverallFractions = null;
        throw new IllegalStateException("Aqueous chemical equilibrium produced an invalid overall amount for component "
            + system.getPhase(0).getComponent(componentIndex).getComponentName() + ": "
            + coupledOverallMoles[componentIndex]);
      }
      coupledOverallMoles[componentIndex] = Math.max(MINIMUM_COUPLED_COMPONENT_MOLES,
          coupledOverallMoles[componentIndex]);
      totalMoles += coupledOverallMoles[componentIndex];
    }

    if (!(totalMoles > 0.0) || !Double.isFinite(totalMoles)) {
      coupledOverallFractions = null;
      return;
    }
    for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
      coupledOverallFractions[componentIndex] = coupledOverallMoles[componentIndex] / totalMoles;
    }
    hybridModel.synchronizeHybridEosGeOverallComposition(coupledOverallMoles, totalMoles);
    system.initBeta();
    system.normalizeBeta();
  }

  /**
   * Project the aqueous reaction update onto the stoichiometric conservation null space.
   *
   * <p>
   * The chemical solver is iterative, so its raw mole update can contain a small component normal to the element and
   * charge constraints. Reusing that update in an outer phase/chemistry loop would accumulate the residual. The
   * projection {@code delta - A+ A delta} retains only reaction directions satisfying {@code A delta = 0}.
   * </p>
   *
   * @param aqueousPhaseNumber active aqueous phase number
   * @param oldAqueousMoles aqueous component amounts before chemical equilibrium
   * @return conservative overall component mole changes
   */
  private double[] getConservativeReactionDeltas(int aqueousPhaseNumber, double[] oldAqueousMoles) {
    ComponentInterface[] reactiveComponents = system.getChemicalReactionOperations().getComponents();
    double[][] conservationArray = system.getChemicalReactionOperations().getAmatrix();
    double[] reactionDeltas = new double[system.getPhase(0).getNumberOfComponents()];
    if (reactiveComponents == null || reactiveComponents.length == 0 || conservationArray == null
        || conservationArray.length == 0) {
      return reactionDeltas;
    }

    SimpleMatrix rawDelta = new SimpleMatrix(reactiveComponents.length, 1);
    for (int reactiveIndex = 0; reactiveIndex < reactiveComponents.length; reactiveIndex++) {
      int componentIndex = reactiveComponents[reactiveIndex].getComponentNumber();
      double newMoles = system.getPhase(aqueousPhaseNumber).getComponent(componentIndex).getNumberOfMolesInPhase();
      rawDelta.set(reactiveIndex, 0, newMoles - oldAqueousMoles[componentIndex]);
    }
    SimpleMatrix conservationMatrix = new SimpleMatrix(conservationArray);
    SimpleMatrix conservativeDelta = rawDelta
        .minus(conservationMatrix.pseudoInverse().mult(conservationMatrix).mult(rawDelta));
    for (int reactiveIndex = 0; reactiveIndex < reactiveComponents.length; reactiveIndex++) {
      reactionDeltas[reactiveComponents[reactiveIndex].getComponentNumber()] = conservativeDelta.get(reactiveIndex, 0);
    }
    return reactionDeltas;
  }

  /**
   * Return the current overall species fraction used by the phase-equilibrium equations.
   *
   * @param componentIndex component index
   * @return reaction-adjusted fraction for reactive systems, otherwise the feed fraction
   */
  private double getCoupledOverallFraction(int componentIndex) {
    if (coupledOverallFractions != null && componentIndex >= 0 && componentIndex < coupledOverallFractions.length) {
      return coupledOverallFractions[componentIndex];
    }
    return system.getPhase(0).getComponent(componentIndex).getz();
  }

  /** {@inheritDoc} */
  @Override
  public void calcE() {
    for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
      Erow[componentIndex] = 0.0;
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        Erow[componentIndex] += system.getPhase(phaseIndex).getBeta()
            * inverseFugacityCoefficient(phaseIndex, componentIndex);
      }
      if (!Double.isFinite(Erow[componentIndex]) || Erow[componentIndex] < 1.0e-100) {
        Erow[componentIndex] = 1.0e-100;
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public double calcQ() {
    calcE();
    for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
      double overallFraction = getCoupledOverallFraction(componentIndex);
      multTerm[componentIndex] = overallFraction / Erow[componentIndex];
      multTerm2[componentIndex] = overallFraction / (Erow[componentIndex] * Erow[componentIndex]);
    }

    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      dQdbeta[phaseIndex][0] = 1.0;
      for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
        dQdbeta[phaseIndex][0] -= multTerm[componentIndex] * inverseFugacityCoefficient(phaseIndex, componentIndex);
      }
    }

    for (int rowPhase = 0; rowPhase < system.getNumberOfPhases(); rowPhase++) {
      for (int columnPhase = 0; columnPhase < system.getNumberOfPhases(); columnPhase++) {
        Qmatrix[rowPhase][columnPhase] = 0.0;
        for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
          Qmatrix[rowPhase][columnPhase] += multTerm2[componentIndex]
              * inverseFugacityCoefficient(rowPhase, componentIndex)
              * inverseFugacityCoefficient(columnPhase, componentIndex);
        }
        if (rowPhase == columnPhase) {
          Qmatrix[rowPhase][columnPhase] += 1.0e-10;
        }
      }
    }
    return Q;
  }

  /** {@inheritDoc} */
  @Override
  public void setXY() {
    if (enforceAqueousIonCapacityBound()) {
      // The beta Newton step was projected back into the physically admissible region. Rebuild
      // E with the projected fractions before calculating component compositions.
      calcE();
    }
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      boolean aqueous = hybridModel.isHybridEosGeAqueousPhase(phaseIndex);
      double phaseFraction = Math.max(system.getPhase(phaseIndex).getBeta(), phaseFractionMinimumLimit);
      double ionFractionSum = 0.0;
      double neutralFractionSum = 0.0;
      for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
        ComponentInterface referenceComponent = system.getPhase(0).getComponent(componentIndex);
        double feedFraction = getCoupledOverallFraction(componentIndex);
        double newMoleFraction;
        boolean ion = referenceComponent.getIonicCharge() != 0 || referenceComponent.isIsIon();
        if (ion) {
          newMoleFraction = aqueous ? feedFraction / phaseFraction : 1.0e-50;
        } else {
          newMoleFraction = feedFraction / Erow[componentIndex]
              * inverseFugacityCoefficient(phaseIndex, componentIndex);
        }
        if (!Double.isFinite(newMoleFraction) || newMoleFraction <= 0.0) {
          newMoleFraction = 1.0e-50;
        }
        system.getPhase(phaseIndex).getComponent(componentIndex).setx(newMoleFraction);
        if (ion) {
          ionFractionSum += newMoleFraction;
        } else {
          neutralFractionSum += newMoleFraction;
        }
      }
      if (aqueous) {
        if (!(ionFractionSum < 1.0) || !(neutralFractionSum > 0.0)) {
          throw new IllegalStateException(
              "Hybrid EOS-GE aqueous composition cannot accommodate overall ion amount: " + "ionFractionSum="
                  + ionFractionSum + ", neutralFractionSum=" + neutralFractionSum + ", phaseFraction=" + phaseFraction);
        }
        double neutralScale = (1.0 - ionFractionSum) / neutralFractionSum;
        for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
          ComponentInterface referenceComponent = system.getPhase(0).getComponent(componentIndex);
          if (referenceComponent.getIonicCharge() == 0 && !referenceComponent.isIsIon()) {
            ComponentInterface aqueousComponent = system.getPhase(phaseIndex).getComponent(componentIndex);
            aqueousComponent.setx(aqueousComponent.getx() * neutralScale);
          }
        }
      } else {
        system.getPhase(phaseIndex).normalize();
      }
    }
  }

  /**
   * Keep the fixed-topology beta iterate large enough to contain the complete ionic inventory.
   *
   * <p>
   * Ions are excluded from EOS gas and oil roles, so their aqueous mole fractions sum to
   * {@code sum(zIon) / betaAqueous}. An unconstrained Newton correction can temporarily move {@code betaAqueous} below
   * {@code sum(zIon)} even when the final equilibrium is feasible. That iterate has no normalized aqueous composition
   * and formerly failed before the next correction could recover. This method projects only such infeasible iterates
   * onto the ionic-capacity boundary, taking the required fraction proportionally from the adjustable part of the other
   * active phases. Feasible iterates and the final acceptance tolerances are unchanged.
   * </p>
   *
   * @return {@code true} when the phase fractions were projected
   */
  private boolean enforceAqueousIonCapacityBound() {
    int aqueousPhaseIndex = -1;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      if (hybridModel.isHybridEosGeAqueousPhase(phaseIndex)) {
        aqueousPhaseIndex = phaseIndex;
        break;
      }
    }
    if (aqueousPhaseIndex < 0 || system.getNumberOfPhases() < 2) {
      return false;
    }

    double ionOverallFraction = 0.0;
    double currentAqueousIonFraction = 0.0;
    for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
      ComponentInterface component = system.getPhase(0).getComponent(componentIndex);
      if (component.getIonicCharge() != 0 || component.isIsIon()) {
        ionOverallFraction += Math.max(getCoupledOverallFraction(componentIndex), 0.0);
        currentAqueousIonFraction +=
            Math.max(system.getPhase(aqueousPhaseIndex).getComponent(componentIndex).getx(), 0.0);
      }
    }
    if (!(ionOverallFraction > 0.0) || !Double.isFinite(ionOverallFraction)) {
      return false;
    }

    double maximumAqueousFraction = 1.0 - (system.getNumberOfPhases() - 1.0) * phaseFractionMinimumLimit;
    double maximumProjectedIonFraction =
        Double.isFinite(currentAqueousIonFraction) && currentAqueousIonFraction > HYBRID_ION_CAPACITY_MARGIN
            ? Math.min(1.0 - HYBRID_ION_CAPACITY_MARGIN,
                HYBRID_ION_CONCENTRATION_STEP_LIMIT * currentAqueousIonFraction)
            : 1.0 / HYBRID_ION_CONCENTRATION_STEP_LIMIT;
    double concentrationLimitedAqueousFraction = ionOverallFraction / maximumProjectedIonFraction;
    double minimumAqueousFraction = Math.min(maximumAqueousFraction,
        Math.max(ionOverallFraction + HYBRID_ION_CAPACITY_MARGIN, concentrationLimitedAqueousFraction));
    double currentAqueousFraction = system.getBeta(aqueousPhaseIndex);
    if (currentAqueousFraction >= minimumAqueousFraction) {
      return false;
    }

    double requiredTransfer = minimumAqueousFraction - currentAqueousFraction;
    double adjustableFraction = 0.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      if (phaseIndex != aqueousPhaseIndex) {
        adjustableFraction += Math.max(system.getBeta(phaseIndex) - phaseFractionMinimumLimit, 0.0);
      }
    }
    if (adjustableFraction + phaseFractionMinimumLimit < requiredTransfer) {
      throw new IllegalStateException("Hybrid EOS-GE phase fractions cannot accommodate the ionic inventory: "
          + "ionOverallFraction=" + ionOverallFraction + ", aqueousBeta=" + currentAqueousFraction
          + ", requiredAqueousBeta=" + minimumAqueousFraction + ", previousAqueousIonFraction="
          + currentAqueousIonFraction + ", maximumProjectedIonFraction=" + maximumProjectedIonFraction
          + ", adjustableFraction=" + adjustableFraction);
    }

    double remainingTransfer = requiredTransfer;
    int lastAdjustablePhase = -1;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      if (phaseIndex == aqueousPhaseIndex) {
        continue;
      }
      double available = Math.max(system.getBeta(phaseIndex) - phaseFractionMinimumLimit, 0.0);
      if (available <= 0.0) {
        continue;
      }
      lastAdjustablePhase = phaseIndex;
      double transfer = requiredTransfer * available / adjustableFraction;
      transfer = Math.min(transfer, remainingTransfer);
      system.setBeta(phaseIndex, system.getBeta(phaseIndex) - transfer);
      remainingTransfer -= transfer;
    }
    if (remainingTransfer > 0.0 && lastAdjustablePhase >= 0) {
      system.setBeta(lastAdjustablePhase, system.getBeta(lastAdjustablePhase) - remainingTransfer);
    }

    double nonAqueousFraction = 0.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      if (phaseIndex != aqueousPhaseIndex) {
        nonAqueousFraction += system.getBeta(phaseIndex);
      }
    }
    system.setBeta(aqueousPhaseIndex, 1.0 - nonAqueousFraction);
    // SystemThermo keeps beta in both the mapped system array and each phase object. The
    // composition kernel reads the phase copy immediately, before the next system init.
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      system.getPhase(phaseIndex).setBeta(system.getBeta(phaseIndex));
    }
    return true;
  }

  /**
   * Return an allowed phase's inverse fugacity coefficient. Ions are excluded from EOS gas and oil phases exactly
   * instead of relying on a large finite penalty.
   *
   * @param phaseIndex active phase index
   * @param componentIndex component index
   * @return inverse fugacity coefficient, or zero when the component is excluded from the phase
   */
  private double inverseFugacityCoefficient(int phaseIndex, int componentIndex) {
    ComponentInterface referenceComponent = system.getPhase(0).getComponent(componentIndex);
    if ((referenceComponent.getIonicCharge() != 0 || referenceComponent.isIsIon())
        && !hybridModel.isHybridEosGeAqueousPhase(phaseIndex)) {
      return 0.0;
    }
    double fugacityCoefficient = system.getPhase(phaseIndex).getComponent(componentIndex).getFugacityCoefficient();
    if (!(fugacityCoefficient > 0.0) || !Double.isFinite(fugacityCoefficient)) {
      return 0.0;
    }
    return 1.0 / fugacityCoefficient;
  }

}
