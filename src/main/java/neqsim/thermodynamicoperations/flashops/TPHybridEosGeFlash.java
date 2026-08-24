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

  /** Largest aqueous phase-fraction ratio allowed in one projected beta correction. */
  private static final double HYBRID_AQUEOUS_FRACTION_STEP_LIMIT = 2.0;

  /** Maximum common Newton beta-step scale for a hybrid flash carrying ions. */
  private static final double HYBRID_IONIC_BETA_STEP_SCALE = 0.1;


  /** Reaction-adjusted overall component fractions used by the coupled phase solve. */
  private transient double[] coupledOverallFractions;

  /** Exact reaction-adjusted overall component mole inventory. */
  private transient double[] coupledOverallMoles;

  /** Settled aqueous phase fraction captured immediately before a beta Newton correction. */
  private transient double previousAqueousFractionBeforeBetaCorrection = Double.NaN;

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

  /**
   * Damp the complete beta correction while an ionic inventory is confined to the aqueous role.
   *
   * <p>
   * Scaling every phase correction together preserves the Newton direction and avoids the oscillation produced by
   * independently clipping one phase after the step. Non-ionic hybrid and ordinary multiphase flashes retain the
   * general solver's iteration-dependent scale.
   * </p>
   *
   * @param proposedScale iteration-dependent scale selected by the general solver
   * @return damped common scale for ionic hybrid flashes
   */
  @Override
  protected double limitBetaStepScale(double proposedScale) {
    return hasPositiveHybridIonicInventory() ? Math.min(proposedScale, HYBRID_IONIC_BETA_STEP_SCALE) : proposedScale;
  }

  /**
   * Check whether the current coupled inventory contains an ion confined to the aqueous role.
   *
   * @return {@code true} when a positive ionic overall fraction is present
   */
  private boolean hasPositiveHybridIonicInventory() {
    for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
      ComponentInterface component = system.getPhase(0).getComponent(componentIndex);
      if ((component.getIonicCharge() != 0 || component.isIsIon()) && getCoupledOverallFraction(componentIndex) > 0.0) {
        return true;
      }
    }
    return false;
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
    int aqueousPhaseIndex = getHybridAqueousPhaseNumber();
    previousAqueousFractionBeforeBetaCorrection = aqueousPhaseIndex >= 0 ? system.getPhase(aqueousPhaseIndex).getBeta()
        : Double.NaN;
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
    if (enforceAqueousFractionBounds()) {
      // The beta Newton step was projected back into the physically admissible region. Rebuild
      // E with the projected fractions before calculating component compositions.
      calcE();
    }

    int lineSearchAqueousPhase = -1;
    double[] previousNeutralShares = null;
    double[] proposedNeutralShares = null;
    double lineSearchNeutralTotal = Double.NaN;
    boolean ionicInventory = hasPositiveHybridIonicInventory();
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      boolean aqueous = hybridModel.isHybridEosGeAqueousPhase(phaseIndex);
      double phaseFraction = Math.max(system.getPhase(phaseIndex).getBeta(), phaseFractionMinimumLimit);
      int numberOfComponents = system.getPhase(0).getNumberOfComponents();
      double previousNeutralFractionSum = 0.0;
      if (aqueous && ionicInventory) {
        lineSearchAqueousPhase = phaseIndex;
        previousNeutralShares = new double[numberOfComponents];
        proposedNeutralShares = new double[numberOfComponents];
        for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
          ComponentInterface component = system.getPhase(phaseIndex).getComponent(componentIndex);
          if (component.getIonicCharge() == 0 && !component.isIsIon()) {
            previousNeutralShares[componentIndex] = Math.max(component.getx(), 0.0);
            previousNeutralFractionSum += previousNeutralShares[componentIndex];
          }
        }
        if (previousNeutralFractionSum > 0.0) {
          for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
            previousNeutralShares[componentIndex] /= previousNeutralFractionSum;
          }
        }
      }

      double ionFractionSum = 0.0;
      double neutralFractionSum = 0.0;
      for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
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
        double neutralTotal = 1.0 - ionFractionSum;
        for (int componentIndex = 0; componentIndex < numberOfComponents; componentIndex++) {
          ComponentInterface referenceComponent = system.getPhase(0).getComponent(componentIndex);
          if (referenceComponent.getIonicCharge() == 0 && !referenceComponent.isIsIon()) {
            ComponentInterface aqueousComponent = system.getPhase(phaseIndex).getComponent(componentIndex);
            double proposedNeutralShare = aqueousComponent.getx() / neutralFractionSum;
            aqueousComponent.setx(neutralTotal * proposedNeutralShare);
            if (phaseIndex == lineSearchAqueousPhase) {
              proposedNeutralShares[componentIndex] = proposedNeutralShare;
              lineSearchNeutralTotal = neutralTotal;
            }
          }
        }
      } else {
        system.getPhase(phaseIndex).normalize();
      }
    }

    if (lineSearchAqueousPhase >= 0 && previousNeutralShares != null && proposedNeutralShares != null) {
      system.init(1);
      if (hasFiniteAqueousNeutralFugacities(lineSearchAqueousPhase)) {
        return;
      }
      double lineSearchFraction = 0.5;
      while (lineSearchFraction >= 1.0e-6) {
        PhaseInterface aqueousPhase = system.getPhase(lineSearchAqueousPhase);
        for (int componentIndex = 0; componentIndex < aqueousPhase.getNumberOfComponents(); componentIndex++) {
          ComponentInterface component = aqueousPhase.getComponent(componentIndex);
          if (component.getIonicCharge() == 0 && !component.isIsIon()) {
            double neutralShare = previousNeutralShares[componentIndex] + lineSearchFraction
                * (proposedNeutralShares[componentIndex] - previousNeutralShares[componentIndex]);
            component.setx(lineSearchNeutralTotal * neutralShare);
          }
        }
        system.init(1);
        if (hasFiniteAqueousNeutralFugacities(lineSearchAqueousPhase)) {
          return;
        }
        lineSearchFraction *= 0.5;
      }
      throw new IllegalStateException(
          "Hybrid EOS-GE aqueous neutral-composition line search could not retain finite fugacity coefficients.");
    }
  }

  /**
   * Check finite positive fugacity coefficients for material neutral components in the aqueous role.
   *
   * @param aqueousPhaseIndex active aqueous phase index
   * @return {@code true} when every material neutral component has a finite positive coefficient
   */
  private boolean hasFiniteAqueousNeutralFugacities(int aqueousPhaseIndex) {
    PhaseInterface aqueousPhase = system.getPhase(aqueousPhaseIndex);
    for (int componentIndex = 0; componentIndex < aqueousPhase.getNumberOfComponents(); componentIndex++) {
      ComponentInterface component = aqueousPhase.getComponent(componentIndex);
      if (getCoupledOverallFraction(componentIndex) <= 1.0e-30 || component.getIonicCharge() != 0
          || component.isIsIon()) {
        continue;
      }
      if (!(component.getx() > 0.0) || !Double.isFinite(component.getx())
          || !(component.getFugacityCoefficient() > 0.0) || !Double.isFinite(component.getFugacityCoefficient())) {
        return false;
      }
    }
    return true;
  }

  /**
   * Keep the fixed-topology beta iterate large enough to contain the complete ionic inventory.
   *
   * <p>
   * Ions are excluded from EOS gas and oil roles, so their aqueous mole fractions sum to
   * {@code sum(zIon) / betaAqueous}. An unconstrained Newton correction can temporarily move {@code betaAqueous} below
   * {@code sum(zIon)} even when the final equilibrium is feasible. A correction in the other direction can likewise
   * displace nearly all aqueous solvent before the phase model can update its fugacities. This method projects the
   * aqueous fraction onto the ionic-capacity boundary and a bidirectional step-ratio trust region, transferring the
   * correction proportionally to or from the other active phases. Feasible iterates and the final acceptance tolerances
   * are unchanged.
   * </p>
   *
   * @return {@code true} when the phase fractions were projected
   */
  private boolean enforceAqueousFractionBounds() {
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
    for (int componentIndex = 0; componentIndex < system.getPhase(0).getNumberOfComponents(); componentIndex++) {
      ComponentInterface component = system.getPhase(0).getComponent(componentIndex);
      if (component.getIonicCharge() != 0 || component.isIsIon()) {
        ionOverallFraction += Math.max(getCoupledOverallFraction(componentIndex), 0.0);
      }
    }
    if (!(ionOverallFraction > 0.0) || !Double.isFinite(ionOverallFraction)) {
      return false;
    }

    double maximumAqueousFraction = 1.0 - (system.getNumberOfPhases() - 1.0) * phaseFractionMinimumLimit;
    // calcQ captures the settled beta before the Newton proposal is written and initialized.
    // Limiting beta reduction bounds the corresponding ionic-concentration increase, while
    // limiting beta growth prevents one proposal from displacing nearly all aqueous solvent.
    double previousAqueousFraction = previousAqueousFractionBeforeBetaCorrection;
    double stepLimitedMinimumAqueousFraction = Double.isFinite(previousAqueousFraction) && previousAqueousFraction > 0.0
        ? previousAqueousFraction / HYBRID_AQUEOUS_FRACTION_STEP_LIMIT
        : ionOverallFraction + HYBRID_ION_CAPACITY_MARGIN;
    double minimumAqueousFraction = Math.min(maximumAqueousFraction,
        Math.max(ionOverallFraction + HYBRID_ION_CAPACITY_MARGIN, stepLimitedMinimumAqueousFraction));
    double stepLimitedMaximumAqueousFraction = Double.isFinite(previousAqueousFraction) && previousAqueousFraction > 0.0
        ? Math.min(maximumAqueousFraction, previousAqueousFraction * HYBRID_AQUEOUS_FRACTION_STEP_LIMIT)
        : maximumAqueousFraction;
    double currentAqueousFraction = system.getBeta(aqueousPhaseIndex);
    if (currentAqueousFraction >= minimumAqueousFraction
        && currentAqueousFraction <= stepLimitedMaximumAqueousFraction) {
      return false;
    }

    if (currentAqueousFraction > stepLimitedMaximumAqueousFraction) {
      double excessAqueousFraction = currentAqueousFraction - stepLimitedMaximumAqueousFraction;
      double nonAqueousFraction = 0.0;
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        if (phaseIndex != aqueousPhaseIndex) {
          nonAqueousFraction += system.getBeta(phaseIndex);
        }
      }
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        if (phaseIndex != aqueousPhaseIndex) {
          double share = nonAqueousFraction > 0.0 ? system.getBeta(phaseIndex) / nonAqueousFraction
              : 1.0 / (system.getNumberOfPhases() - 1.0);
          system.setBeta(phaseIndex, system.getBeta(phaseIndex) + excessAqueousFraction * share);
        }
      }
      system.setBeta(aqueousPhaseIndex, stepLimitedMaximumAqueousFraction);
      synchronizePhaseBetaMirrors();
      return true;
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
          + ", requiredAqueousBeta=" + minimumAqueousFraction + ", previousAqueousBeta=" + previousAqueousFraction
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
    synchronizePhaseBetaMirrors();
    return true;
  }

  /** Synchronize the mapped system beta values into their active phase objects. */
  private void synchronizePhaseBetaMirrors() {
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      system.getPhase(phaseIndex).setBeta(system.getBeta(phaseIndex));
    }
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
