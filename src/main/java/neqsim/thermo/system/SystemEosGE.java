package neqsim.thermo.system;

import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseType;

/**
 * Base class for gamma-phi systems combining equation-of-state and excess-Gibbs-energy phases.
 *
 * <p>
 * The default topology shared by Wilson, NRTL, UNIFAC and specialised activity models has creation-order slot
 * {@code phaseArray[0]} as the EOS vapour phase and subsequent fluid slots as independent clones of the GE liquid
 * phase. Active phases can later be reordered through {@code phaseIndex}, so {@code getPhase(0)} is not guaranteed to
 * return the EOS slot. Concrete systems remain responsible for selecting the EOS, activity model, mixing rules and
 * parameter sources. Any concrete EOS-GE system can opt into separate EOS gas and oil slots plus a GE aqueous slot by
 * calling {@link #enableHybridEosGeFlash()}. Electrolyte systems may configure those roles directly through
 * {@link #configureHybridEosGePhases(double, double, PhaseInterface, PhaseInterface, PhaseInterface)}. The dedicated
 * multiphase strategy restores these creation-order roles before every flash, activates only feed-supported roles and
 * removes disappearing roles from the active mapping without replacing their phase objects. Reactive coupling and
 * scale-potential accuracy remain governed by the selected aqueous model's species, activity formulation and parameter
 * coverage; the topology does not turn a non-electrolyte GE model into an electrolyte model.
 * </p>
 *
 * @author NeqSim
 */
public abstract class SystemEosGE extends SystemEos implements HybridEosGeFlashModel {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Initial minimum phase fraction used when restoring a missing hybrid role. */
  private static final double HYBRID_INITIAL_PHASE_FRACTION = 1.0e-5;

  /** Maximum component material-balance residual accepted after a non-reactive hybrid flash. */
  private static final double HYBRID_MATERIAL_BALANCE_TOLERANCE = 1.0e-7;

  /** Maximum phase-composition normalization residual accepted after a hybrid flash. */
  private static final double HYBRID_NORMALIZATION_TOLERANCE = 1.0e-8;

  /** Maximum cross-phase logarithmic fugacity residual accepted after a hybrid flash. */
  private static final double HYBRID_LOG_FUGACITY_TOLERANCE = 1.0e-5;

  /** Minimum practical phase fraction retained as an active hybrid phase. */
  private static final double HYBRID_ACTIVE_PHASE_FRACTION = 1.0e-10;

  /** Creation-order slot containing the EOS gas object. */
  private int eosGasPhaseSlot = 0;

  /** Creation-order slot containing the EOS oil object, or {@code -1} for two-phase topology. */
  private int eosOilPhaseSlot = -1;

  /** Creation-order slot containing the GE liquid or aqueous object. */
  private int geLiquidPhaseSlot = 1;

  /** Whether separate EOS gas, EOS oil and GE aqueous roles have been configured. */
  private boolean hybridEosGeTopologyConfigured = false;

  /** Last component material-balance residual calculated by hybrid acceptance. */
  private transient double lastHybridMaterialBalanceResidual = Double.NaN;

  /** Last phase-composition normalization residual calculated by hybrid acceptance. */
  private transient double lastHybridNormalizationResidual = Double.NaN;

  /** Last cross-phase logarithmic fugacity residual calculated by hybrid acceptance. */
  private transient double lastHybridLogFugacityResidual = Double.NaN;

  /** Component associated with the last maximum hybrid fugacity residual. */
  private transient String lastHybridWorstFugacityComponent = null;

  /** Component associated with the last maximum hybrid material-balance residual. */
  private transient String lastHybridWorstMaterialBalanceComponent = null;

  /**
   * Constructor for an EOS-GE system.
   *
   * @param temperature temperature in kelvin
   * @param pressure pressure in bara
   * @param checkForSolids whether a pure-component solid phase should be available
   */
  protected SystemEosGE(double temperature, double pressure, boolean checkForSolids) {
    super(temperature, pressure, checkForSolids);
  }

  /**
   * Configure the common EOS-GE phase topology.
   *
   * @param temperature temperature in kelvin
   * @param pressure pressure in bara
   * @param eosPhase equation-of-state vapour phase
   * @param gePhase excess-Gibbs-energy liquid phase
   */
  protected final void configureEosGePhases(double temperature, double pressure, PhaseInterface eosPhase,
      PhaseInterface gePhase) {
    hybridEosGeTopologyConfigured = false;
    eosGasPhaseSlot = 0;
    eosOilPhaseSlot = -1;
    geLiquidPhaseSlot = 1;
    phaseArray[0] = eosPhase;
    initialisePhase(0, temperature, pressure, PhaseType.GAS);
    phaseArray[1] = gePhase;
    initialisePhase(1, temperature, pressure, PhaseType.LIQUID);

    if (solidPhaseCheck) {
      setNumberOfPhases(4);
      phaseArray[2] = gePhase.clone();
      initialisePhase(2, temperature, pressure, PhaseType.LIQUID);
      phaseArray[3] = new PhasePureComponentSolid();
      initialisePhase(3, temperature, pressure, PhaseType.SOLID);
      phaseArray[3].setRefPhase(phaseArray[1].getRefPhase());
    }
  }

  /**
   * Configure reusable creation-order roles for a hybrid gas-oil-aqueous system.
   *
   * <p>
   * The slot layout deliberately preserves the historic electrolyte-GE convention: {@code phaseArray[0]} is EOS gas,
   * {@code phaseArray[1]} is GE aqueous and {@code phaseArray[2]} is EOS oil. Active phases may be reordered or
   * collapsed through {@code phaseIndex}; these three creation-order objects are never replaced by the hybrid flash.
   * </p>
   *
   * @param temperature temperature in kelvin
   * @param pressure pressure in bara
   * @param eosGasPhase equation-of-state gas phase
   * @param geAqueousPhase excess-Gibbs-energy aqueous phase
   * @param eosOilPhase equation-of-state oil phase
   */
  protected final void configureHybridEosGePhases(double temperature, double pressure, PhaseInterface eosGasPhase,
      PhaseInterface geAqueousPhase, PhaseInterface eosOilPhase) {
    hybridEosGeTopologyConfigured = true;
    eosGasPhaseSlot = 0;
    geLiquidPhaseSlot = 1;
    eosOilPhaseSlot = 2;

    setMaxNumberOfPhases(solidPhaseCheck ? 4 : 3);
    phaseArray[eosGasPhaseSlot] = eosGasPhase;
    initialisePhase(eosGasPhaseSlot, temperature, pressure, PhaseType.GAS);
    phaseArray[geLiquidPhaseSlot] = geAqueousPhase;
    initialisePhase(geLiquidPhaseSlot, temperature, pressure, PhaseType.AQUEOUS);
    phaseArray[eosOilPhaseSlot] = eosOilPhase;
    initialisePhase(eosOilPhaseSlot, temperature, pressure, PhaseType.OIL);

    if (solidPhaseCheck) {
      phaseArray[3] = new PhasePureComponentSolid();
      initialisePhase(3, temperature, pressure, PhaseType.SOLID);
      phaseArray[3].setRefPhase(phaseArray[geLiquidPhaseSlot].getRefPhase());
      setNumberOfPhases(4);
      setPhaseIndex(0, eosGasPhaseSlot);
      setPhaseIndex(1, geLiquidPhaseSlot);
      setPhaseIndex(2, eosOilPhaseSlot);
      setPhaseIndex(3, 3);
    } else {
      setNumberOfPhases(2);
      setPhaseIndex(0, eosGasPhaseSlot);
      setPhaseIndex(1, geLiquidPhaseSlot);
    }
  }

  /**
   * Enable the reusable hybrid gas-oil-aqueous topology for this EOS-GE system.
   *
   * <p>
   * The existing creation-order EOS vapour and GE liquid objects are retained. A clone of the EOS object becomes the
   * oil role, the GE object becomes the aqueous role, and multiphase checking is enabled. This makes the topology
   * available to Wilson, NRTL, UNIFAC and specialised electrolyte-GE subclasses without placing model names in the
   * flash solver. The selected GE phase must still implement meaningful aqueous fugacity and activity coefficients for
   * the requested components and reactions.
   * </p>
   */
  @Override
  public final void enableHybridEosGeFlash() {
    if (!hybridEosGeTopologyConfigured) {
      PhaseInterface eosGasPhase = phaseArray[eosGasPhaseSlot];
      PhaseInterface geAqueousPhase = phaseArray[geLiquidPhaseSlot];
      if (eosGasPhase == null || geAqueousPhase == null
          || !(geAqueousPhase instanceof neqsim.thermo.phase.PhaseGEInterface)) {
        throw new IllegalStateException(
            "The system must provide EOS gas and GE liquid phases before enabling the hybrid EOS-GE flash.");
      }
      configureHybridEosGePhases(getTemperature(), getPressure(), eosGasPhase, geAqueousPhase, eosGasPhase.clone());
    }
    setMultiPhaseCheck(true);
  }

  /** {@inheritDoc} */
  @Override
  public final boolean isHybridEosGeTopologyConfigured() {
    return hybridEosGeTopologyConfigured;
  }

  /**
   * Restore the default two-phase direct gamma-phi active-phase contract after density ordering or a prior single-phase
   * collapse.
   *
   * <p>
   * Specialized hybrid systems may override this method to restore a different phase-role topology. Their flash solver
   * must use the same phase-role contract.
   * </p>
   */
  @Override
  public void prepareGammaPhiFlash() {
    setNumberOfPhases(2);
    setPhaseIndex(0, 0);
    setPhaseIndex(1, 1);
  }

  /** {@inheritDoc} */
  @Override
  public boolean requiresHybridEosGeFlash() {
    return hybridEosGeTopologyConfigured && doMultiPhaseCheck();
  }

  /** {@inheritDoc} */
  @Override
  public void prepareHybridEosGeFlash() {
    if (!hybridEosGeTopologyConfigured) {
      throw new IllegalStateException("Hybrid EOS-GE phase roles have not been configured.");
    }

    restoreHybridEosGePhaseRoles();
    seedHybridPhaseCompositions();
    seedHybridPhaseFractions();
    activateSupportedHybridPhaseRoles();
  }

  /** {@inheritDoc} */
  @Override
  public void restoreHybridEosGePhaseRoles() {
    if (!hybridEosGeTopologyConfigured) {
      throw new IllegalStateException("Hybrid EOS-GE phase roles have not been configured.");
    }
    setNumberOfPhases(3);
    setPhaseIndex(0, eosGasPhaseSlot);
    setPhaseIndex(1, eosOilPhaseSlot);
    setPhaseIndex(2, geLiquidPhaseSlot);
    restoreHybridPhaseObject(eosGasPhaseSlot, PhaseType.GAS);
    restoreHybridPhaseObject(eosOilPhaseSlot, PhaseType.OIL);
    restoreHybridPhaseObject(geLiquidPhaseSlot, PhaseType.AQUEOUS);
    restoreHybridEosGeActivePhaseTypes();
  }

  /** {@inheritDoc} */
  @Override
  public void synchronizeHybridEosGeOverallComposition(double[] componentMoles, double totalMoles) {
    if (!hybridEosGeTopologyConfigured) {
      throw new IllegalStateException("Hybrid EOS-GE phase roles have not been configured.");
    }
    if (componentMoles == null || componentMoles.length != getNumberOfComponents()) {
      throw new IllegalArgumentException("Reaction-adjusted component amounts must match the system component count.");
    }
    if (!(totalMoles > 0.0) || !Double.isFinite(totalMoles)) {
      throw new IllegalArgumentException("Reaction-adjusted total moles must be finite and positive.");
    }

    setTotalNumberOfMoles(totalMoles);
    int[] roleSlots = new int[] { eosGasPhaseSlot, eosOilPhaseSlot, geLiquidPhaseSlot };
    for (int roleSlot : roleSlots) {
      if (roleSlot < 0) {
        continue;
      }
      PhaseInterface role = phaseArray[roleSlot];
      for (int componentIndex = 0; componentIndex < componentMoles.length; componentIndex++) {
        double moles = componentMoles[componentIndex];
        role.getComponent(componentIndex).setNumberOfmoles(moles);
        role.getComponent(componentIndex).setz(moles / totalMoles);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean isHybridEosGeAqueousPhase(int phaseNumber) {
    return hybridEosGeTopologyConfigured && phaseNumber >= 0 && phaseNumber < getNumberOfPhases()
        && getPhaseIndex(phaseNumber) == geLiquidPhaseSlot;
  }

  /**
   * Seed role-specific compositions from the overall feed before multiphase fugacity iteration.
   */
  private void seedHybridPhaseCompositions() {
    PhaseInterface gas = phaseArray[eosGasPhaseSlot];
    PhaseInterface oil = phaseArray[eosOilPhaseSlot];
    PhaseInterface aqueous = phaseArray[geLiquidPhaseSlot];

    for (int componentIndex = 0; componentIndex < gas.getNumberOfComponents(); componentIndex++) {
      neqsim.thermo.component.ComponentInterface component = gas.getComponent(componentIndex);
      double feedFraction = Math.max(component.getz(), 1.0e-50);
      boolean ion = component.getIonicCharge() != 0 || component.isIsIon();
      boolean aqueousComponent = isAqueousComponent(component);
      boolean heavyHydrocarbon = component.isHydrocarbon() && component.getMolarMass() > 0.045;

      double gasFraction;
      double oilFraction;
      double aqueousFraction;
      if (ion) {
        gasFraction = 1.0e-50;
        oilFraction = 1.0e-50;
        aqueousFraction = feedFraction;
      } else if (aqueousComponent) {
        gasFraction = Math.min(feedFraction * 1.0e-3, 1.0e-8);
        oilFraction = Math.min(feedFraction * 1.0e-4, 1.0e-10);
        aqueousFraction = feedFraction;
      } else if (component.isHydrocarbon()) {
        gasFraction = heavyHydrocarbon ? Math.max(feedFraction * 1.0e-2, 1.0e-16) : feedFraction;
        oilFraction = heavyHydrocarbon ? feedFraction : Math.max(feedFraction * 5.0e-2, 1.0e-16);
        aqueousFraction = Math.max(feedFraction * 1.0e-8, 1.0e-30);
      } else {
        gasFraction = feedFraction;
        oilFraction = Math.max(feedFraction * 1.0e-1, 1.0e-16);
        aqueousFraction = Math.max(feedFraction * 1.0e-2, 1.0e-20);
      }

      gas.getComponent(componentIndex).setx(gasFraction);
      oil.getComponent(componentIndex).setx(oilFraction);
      aqueous.getComponent(componentIndex).setx(aqueousFraction);
    }
    gas.normalize();
    oil.normalize();
    aqueous.normalize();
  }

  /**
   * Seed finite gas, oil and aqueous fractions from component affinities in the overall feed.
   */
  private void seedHybridPhaseFractions() {
    PhaseInterface gas = phaseArray[eosGasPhaseSlot];
    double aqueousFeed = 0.0;
    double oilFeed = 0.0;
    double gasFeed = 0.0;

    for (int componentIndex = 0; componentIndex < gas.getNumberOfComponents(); componentIndex++) {
      neqsim.thermo.component.ComponentInterface component = gas.getComponent(componentIndex);
      double feedFraction = Math.max(component.getz(), 0.0);
      if (isAqueousComponent(component)) {
        aqueousFeed += feedFraction;
      } else if (component.isHydrocarbon() && component.getMolarMass() > 0.045) {
        oilFeed += feedFraction;
      } else {
        gasFeed += feedFraction;
      }
    }

    gasFeed = Math.max(gasFeed, HYBRID_INITIAL_PHASE_FRACTION);
    oilFeed = Math.max(oilFeed, HYBRID_INITIAL_PHASE_FRACTION);
    aqueousFeed = Math.max(aqueousFeed, HYBRID_INITIAL_PHASE_FRACTION);
    double total = gasFeed + oilFeed + aqueousFeed;
    setBeta(0, gasFeed / total);
    setBeta(1, oilFeed / total);
    setBeta(2, aqueousFeed / total);
    normalizeBeta();
  }

  /**
   * Identify ions, water and common polar production solvents as aqueous-feed components.
   *
   * @param component component to classify
   * @return {@code true} for an aqueous-feed component
   */
  private boolean isAqueousComponent(neqsim.thermo.component.ComponentInterface component) {
    if (component.getIonicCharge() != 0 || component.isIsIon()) {
      return true;
    }
    String name = component.getComponentName().toLowerCase();
    return "water".equals(name) || "meg".equals(name) || "teg".equals(name) || "deg".equals(name)
        || "methanol".equals(name) || "ethanol".equals(name);
  }

  /**
   * Check whether the feed contains a hydrocarbon capable of supporting an EOS liquid at the current temperature.
   *
   * <p>
   * This prevents a small metastable EOS-liquid root made only from supercritical methane and water from entering a
   * gas-aqueous solve. The creation-order oil object is retained and is reconsidered from the current feed before every
   * later flash.
   * </p>
   *
   * @return {@code true} when an oil-forming feed component is present
   */
  private boolean hasHybridOilCandidate() {
    PhaseInterface referencePhase = phaseArray[eosGasPhaseSlot];
    for (int componentIndex = 0; componentIndex < referencePhase.getNumberOfComponents(); componentIndex++) {
      neqsim.thermo.component.ComponentInterface component = referencePhase.getComponent(componentIndex);
      if (component.getz() > 1.0e-12 && (component.isHydrocarbon() || component.isIsTBPfraction())
          && component.getTC() > getTemperature()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Check whether the feed contains a component with a Wilson estimate favouring an EOS gas root.
   *
   * @return {@code true} when a gas-forming feed component is present
   */
  private boolean hasHybridGasCandidate() {
    PhaseInterface referencePhase = phaseArray[eosGasPhaseSlot];
    for (int componentIndex = 0; componentIndex < referencePhase.getNumberOfComponents(); componentIndex++) {
      neqsim.thermo.component.ComponentInterface component = referencePhase.getComponent(componentIndex);
      if (component.getz() <= 1.0e-12 || component.getIonicCharge() != 0 || component.isIsIon()) {
        continue;
      }
      double criticalTemperature = component.getTC();
      double criticalPressure = component.getPC();
      if (!(criticalTemperature > 0.0) || !(criticalPressure > 0.0) || !(getPressure() > 0.0)) {
        continue;
      }
      double wilsonK = criticalPressure / getPressure()
          * Math.exp(5.373 * (1.0 + component.getAcentricFactor()) * (1.0 - criticalTemperature / getTemperature()));
      if (Double.isFinite(wilsonK) && wilsonK > 1.0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Check whether the feed contains water, ions or a supported polar aqueous solvent.
   *
   * @return {@code true} when an aqueous-forming feed component is present
   */
  private boolean hasHybridAqueousCandidate() {
    PhaseInterface referencePhase = phaseArray[eosGasPhaseSlot];
    for (int componentIndex = 0; componentIndex < referencePhase.getNumberOfComponents(); componentIndex++) {
      neqsim.thermo.component.ComponentInterface component = referencePhase.getComponent(componentIndex);
      if (component.getz() > 1.0e-12 && isAqueousComponent(component)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Activate only feed-supported hybrid roles while retaining a complete inactive creation-order mapping.
   */
  private void activateSupportedHybridPhaseRoles() {
    int[] activeSlots = new int[3];
    int activeCount = 0;
    if (hasHybridGasCandidate()) {
      activeSlots[activeCount++] = eosGasPhaseSlot;
    }
    if (hasHybridOilCandidate()) {
      activeSlots[activeCount++] = eosOilPhaseSlot;
    }
    if (hasHybridAqueousCandidate()) {
      activeSlots[activeCount++] = geLiquidPhaseSlot;
    }
    if (activeCount == 0) {
      activeSlots[activeCount++] = eosGasPhaseSlot;
    }

    setNumberOfPhases(activeCount);
    for (int phaseNumber = 0; phaseNumber < activeCount; phaseNumber++) {
      setPhaseIndex(phaseNumber, activeSlots[phaseNumber]);
    }
    completeInactiveHybridPhaseMapping(activeSlots, activeCount);
    normalizeBeta();
    restoreHybridEosGeActivePhaseTypes();
  }

  /** {@inheritDoc} */
  @Override
  public boolean finishHybridEosGeFlash(double phaseFractionMinimumLimit) {
    if (!hybridEosGeTopologyConfigured || getNumberOfPhases() < 1) {
      return false;
    }

    restoreHybridEosGeActivePhaseTypes();
    init(1);

    lastHybridNormalizationResidual = 0.0;
    lastHybridMaterialBalanceResidual = 0.0;
    lastHybridLogFugacityResidual = 0.0;
    lastHybridWorstFugacityComponent = null;
    lastHybridWorstMaterialBalanceComponent = null;
    double betaSum = 0.0;
    for (int phaseIndex = 0; phaseIndex < getNumberOfPhases(); phaseIndex++) {
      if (!isHybridRoleSlot(getPhaseIndex(phaseIndex))) {
        return false;
      }
      betaSum += getBeta(phaseIndex);
      double compositionSum = 0.0;
      for (int componentIndex = 0; componentIndex < getPhase(phaseIndex).getNumberOfComponents(); componentIndex++) {
        compositionSum += getPhase(phaseIndex).getComponent(componentIndex).getx();
      }
      lastHybridNormalizationResidual = Math.max(lastHybridNormalizationResidual, Math.abs(1.0 - compositionSum));
    }
    lastHybridNormalizationResidual = Math.max(lastHybridNormalizationResidual, Math.abs(1.0 - betaSum));

    if (!isChemicalSystem()) {
      for (int componentIndex = 0; componentIndex < getPhase(0).getNumberOfComponents(); componentIndex++) {
        double calculatedFeedFraction = 0.0;
        for (int phaseIndex = 0; phaseIndex < getNumberOfPhases(); phaseIndex++) {
          calculatedFeedFraction += getBeta(phaseIndex) * getPhase(phaseIndex).getComponent(componentIndex).getx();
        }
        double residual = Math.abs(getPhase(0).getComponent(componentIndex).getz() - calculatedFeedFraction);
        if (residual > lastHybridMaterialBalanceResidual) {
          lastHybridMaterialBalanceResidual = residual;
          lastHybridWorstMaterialBalanceComponent = getPhase(0).getComponent(componentIndex).getComponentName();
        }
      }
    }

    for (int componentIndex = 0; componentIndex < getPhase(0).getNumberOfComponents(); componentIndex++) {
      neqsim.thermo.component.ComponentInterface referenceComponent = getPhase(0).getComponent(componentIndex);
      if (referenceComponent.getIonicCharge() != 0 || referenceComponent.isIsIon()) {
        continue;
      }
      double referenceLogFugacity = Double.NaN;
      for (int phaseIndex = 0; phaseIndex < getNumberOfPhases(); phaseIndex++) {
        if (getBeta(phaseIndex) <= 100.0 * phaseFractionMinimumLimit) {
          continue;
        }
        neqsim.thermo.component.ComponentInterface phaseComponent = getPhase(phaseIndex).getComponent(componentIndex);
        double moleFraction = phaseComponent.getx();
        double fugacityCoefficient = phaseComponent.getFugacityCoefficient();
        if (!Double.isFinite(moleFraction) || moleFraction < 0.0) {
          lastHybridLogFugacityResidual = Double.POSITIVE_INFINITY;
          lastHybridWorstFugacityComponent = phaseComponent.getComponentName();
          continue;
        }
        if (moleFraction <= 1.0e-30) {
          continue;
        }
        if (!(fugacityCoefficient > 0.0) || !Double.isFinite(fugacityCoefficient)) {
          lastHybridLogFugacityResidual = Double.POSITIVE_INFINITY;
          lastHybridWorstFugacityComponent = phaseComponent.getComponentName();
          continue;
        }
        double logFugacity = Math.log(moleFraction * fugacityCoefficient * getPhase(phaseIndex).getPressure());
        if (Double.isNaN(referenceLogFugacity)) {
          referenceLogFugacity = logFugacity;
        } else {
          double residual = Math.abs(logFugacity - referenceLogFugacity);
          if (residual > lastHybridLogFugacityResidual) {
            lastHybridLogFugacityResidual = residual;
            lastHybridWorstFugacityComponent = phaseComponent.getComponentName();
          }
        }
      }
    }

    boolean accepted = lastHybridNormalizationResidual <= HYBRID_NORMALIZATION_TOLERANCE
        && (isChemicalSystem() || lastHybridMaterialBalanceResidual <= HYBRID_MATERIAL_BALANCE_TOLERANCE)
        && lastHybridLogFugacityResidual <= HYBRID_LOG_FUGACITY_TOLERANCE;
    if (accepted) {
      collapseTraceHybridPhases(Math.max(HYBRID_ACTIVE_PHASE_FRACTION, 100.0 * phaseFractionMinimumLimit));
      normalizeBeta();
      orderByDensity();
      init(1);
      // Phase implementations may classify any non-gas EOS root as a generic liquid during init. Reassert the
      // model-owned semantic roles after the final property initialization so callers consistently observe GAS, OIL
      // and AQUEOUS rather than implementation-specific temporary labels.
      restoreHybridEosGeActivePhaseTypes();
    }
    return accepted;
  }

  /**
   * Check whether a creation-order slot is one of the configured hybrid fluid roles.
   *
   * @param creationOrderSlot creation-order phase-array slot
   * @return {@code true} for the EOS gas, EOS oil or GE aqueous slot
   */
  private boolean isHybridRoleSlot(int creationOrderSlot) {
    return creationOrderSlot == eosGasPhaseSlot || creationOrderSlot == eosOilPhaseSlot
        || creationOrderSlot == geLiquidPhaseSlot;
  }

  /**
   * Restore temperature, pressure and type directly on a creation-order phase object.
   *
   * <p>
   * System phase-type metadata is restored separately through active phase numbers because
   * {@link #setPhaseType(int, PhaseType)} follows {@code phaseIndex}.
   * </p>
   *
   * @param creationOrderSlot phase-array slot
   * @param phaseType role type
   */
  private void restoreHybridPhaseObject(int creationOrderSlot, PhaseType phaseType) {
    PhaseInterface phase = phaseArray[creationOrderSlot];
    phase.setTemperature(getTemperature());
    phase.setPressure(getPressure());
    phase.setType(phaseType);
  }

  /**
   * Restore role types for the currently active creation-order mapping.
   */
  @Override
  public void restoreHybridEosGeActivePhaseTypes() {
    for (int phaseNumber = 0; phaseNumber < getNumberOfPhases(); phaseNumber++) {
      int creationOrderSlot = getPhaseIndex(phaseNumber);
      PhaseType roleType;
      if (creationOrderSlot == eosGasPhaseSlot) {
        roleType = PhaseType.GAS;
      } else if (creationOrderSlot == eosOilPhaseSlot) {
        roleType = PhaseType.OIL;
      } else if (creationOrderSlot == geLiquidPhaseSlot) {
        roleType = PhaseType.AQUEOUS;
      } else {
        continue;
      }
      // Chemical-equilibrium operations can temporarily disable phase shifting while they force an aqueous phase.
      // Write creation-order metadata directly so the role contract is restored even when setPhaseType would be a
      // guarded no-op. A later init then receives the same role type as the phase object.
      phaseType[creationOrderSlot] = roleType;
      restoreHybridPhaseObject(creationOrderSlot, roleType);
    }
  }

  /**
   * Remove trace roles from the active mapping without deleting or replacing their creation-order objects.
   *
   * @param threshold largest phase fraction considered absent
   */
  private void collapseTraceHybridPhases(double threshold) {
    int[] survivingSlots = new int[3];
    int survivorCount = 0;
    int largestPhaseNumber = 0;
    for (int phaseNumber = 0; phaseNumber < getNumberOfPhases(); phaseNumber++) {
      if (getBeta(phaseNumber) > getBeta(largestPhaseNumber)) {
        largestPhaseNumber = phaseNumber;
      }
      if (getBeta(phaseNumber) > threshold) {
        survivingSlots[survivorCount++] = getPhaseIndex(phaseNumber);
      }
    }
    if (survivorCount == 0) {
      survivingSlots[survivorCount++] = getPhaseIndex(largestPhaseNumber);
    }

    setNumberOfPhases(survivorCount);
    for (int phaseNumber = 0; phaseNumber < survivorCount; phaseNumber++) {
      setPhaseIndex(phaseNumber, survivingSlots[phaseNumber]);
    }
    completeInactiveHybridPhaseMapping(survivingSlots, survivorCount);
    normalizeBeta();
    restoreHybridEosGeActivePhaseTypes();
  }

  /**
   * Keep inactive {@code phaseIndex} entries as a permutation of the creation-order roles.
   *
   * <p>
   * System component addition iterates to {@code maxNumberOfPhases} through {@link #getPhase(int)} even when fewer
   * phases are active. Duplicate inactive mappings would therefore add a new component twice to one phase object.
   * </p>
   *
   * @param activeSlots active creation-order slots
   * @param activeCount number of active slots
   */
  private void completeInactiveHybridPhaseMapping(int[] activeSlots, int activeCount) {
    int[] roleSlots = new int[] { eosGasPhaseSlot, eosOilPhaseSlot, geLiquidPhaseSlot };
    int mappingIndex = activeCount;
    for (int roleSlot : roleSlots) {
      boolean active = false;
      for (int activeIndex = 0; activeIndex < activeCount; activeIndex++) {
        if (activeSlots[activeIndex] == roleSlot) {
          active = true;
          break;
        }
      }
      if (!active) {
        setPhaseIndex(mappingIndex++, roleSlot);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getHybridEosGeFlashDiagnostics(double phaseFractionMinimumLimit) {
    return "phaseFractionMinimumLimit=" + phaseFractionMinimumLimit + ", materialBalanceResidual="
        + lastHybridMaterialBalanceResidual + ", normalizationResidual=" + lastHybridNormalizationResidual
        + ", worstMaterialBalanceComponent=" + lastHybridWorstMaterialBalanceComponent + ", logFugacityResidual="
        + lastHybridLogFugacityResidual + ", worstFugacityComponent=" + lastHybridWorstFugacityComponent
        + ", activePhases=" + getNumberOfPhases() + ", phaseBetas=" + hybridPhaseBetaDiagnostics()
        + ", componentPhases=" + hybridComponentDiagnostics();
  }

  /**
   * Format active phase roles and fractions for rejected-flash diagnostics.
   *
   * @return compact active phase diagnostics
   */
  private String hybridPhaseBetaDiagnostics() {
    StringBuilder diagnostics = new StringBuilder("[");
    for (int phaseIndex = 0; phaseIndex < getNumberOfPhases(); phaseIndex++) {
      if (phaseIndex > 0) {
        diagnostics.append(", ");
      }
      diagnostics.append(getPhase(phaseIndex).getType()).append('=').append(getBeta(phaseIndex));
    }
    return diagnostics.append(']').toString();
  }

  /**
   * Format mole fractions and fugacity coefficients for all components in a rejected state.
   *
   * @return compact component diagnostics
   */
  private String hybridComponentDiagnostics() {
    StringBuilder diagnostics = new StringBuilder("[");
    for (int componentIndex = 0; componentIndex < getPhase(0).getNumberOfComponents(); componentIndex++) {
      if (componentIndex > 0) {
        diagnostics.append("; ");
      }
      diagnostics.append(getPhase(0).getComponent(componentIndex).getComponentName()).append('=');
      for (int phaseIndex = 0; phaseIndex < getNumberOfPhases(); phaseIndex++) {
        if (phaseIndex > 0) {
          diagnostics.append(',');
        }
        neqsim.thermo.component.ComponentInterface component = getPhase(phaseIndex).getComponent(componentIndex);
        diagnostics.append(getPhase(phaseIndex).getType()).append("(x=").append(component.getx()).append(",phi=")
            .append(component.getFugacityCoefficient()).append(')');
      }
    }
    return diagnostics.append(']').toString();
  }

  /**
   * Get the equation-of-state phase from its creation-order slot.
   *
   * <p>
   * This deliberately bypasses active-phase ordering: after density ordering, {@code getPhase(0)} may identify a
   * different phase through {@code phaseIndex}.
   * </p>
   *
   * @return EOS phase
   */
  public final PhaseInterface getEquationOfStatePhase() {
    return phaseArray[eosGasPhaseSlot];
  }

  /**
   * Get the EOS gas creation-order slot.
   *
   * @return EOS gas slot
   */
  public final int getEosGasPhaseSlot() {
    return eosGasPhaseSlot;
  }

  /**
   * Get the EOS oil creation-order slot.
   *
   * @return EOS oil slot, or {@code -1} when no hybrid oil role is configured
   */
  public final int getEosOilPhaseSlot() {
    return eosOilPhaseSlot;
  }

  /**
   * Get the GE liquid or aqueous creation-order slot.
   *
   * @return GE liquid slot
   */
  public final int getGeLiquidPhaseSlot() {
    return geLiquidPhaseSlot;
  }

  /**
   * Get the EOS oil phase from its creation-order slot.
   *
   * @return EOS oil phase, or {@code null} when no hybrid oil role is configured
   */
  public final PhaseInterface getEosOilPhase() {
    return eosOilPhaseSlot < 0 ? null : phaseArray[eosOilPhaseSlot];
  }

  /**
   * Get the GE liquid or aqueous phase from its creation-order slot.
   *
   * @return GE liquid or aqueous phase
   */
  public final PhaseInterface getGeLiquidPhase() {
    return phaseArray[geLiquidPhaseSlot];
  }

  /**
   * Check whether a phase index identifies an excess-Gibbs-energy phase.
   *
   * @param phaseNumber active phase number
   * @return {@code true} when the phase is a GE phase
   */
  public final boolean isExcessGibbsEnergyPhase(int phaseNumber) {
    return getPhase(phaseNumber) instanceof neqsim.thermo.phase.PhaseGEInterface;
  }

  /**
   * Initialise one phase and its system phase-type metadata.
   *
   * @param phaseNumber phase-array index to initialise
   * @param temperature temperature in kelvin
   * @param pressure pressure in bara
   * @param phaseType phase type
   */
  private void initialisePhase(int phaseNumber, double temperature, double pressure, PhaseType phaseType) {
    PhaseInterface phase = phaseArray[phaseNumber];
    phase.setTemperature(temperature);
    phase.setPressure(pressure);
    phase.setType(phaseType);
    setPhaseType(phaseNumber, phaseType);
  }
}
