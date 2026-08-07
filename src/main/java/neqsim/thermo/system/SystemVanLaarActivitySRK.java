/*
 * SystemVanLaarActivitySRK.java
 */

package neqsim.thermo.system;

import java.util.HashMap;
import java.util.Map;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.phase.PhaseGEVanLaarAcid;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.phase.PhaseSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * This class defines a gamma-phi thermodynamic system for the water-nitric-acid-sulfuric-acid mixture. The vapour phase
 * is described by the SRK equation of state, while every liquid phase uses the Van Laar excess-Gibbs-energy model of
 * Taleb, Ponche and Mirabel (1996) ({@link neqsim.thermo.phase.PhaseGEVanLaarAcid}).
 *
 * <p>
 * The system reproduces the equilibrium identity {@code fugacity_i = gamma_i * x_i * P0_i} for the three modelled
 * acids, where {@code gamma_i} is the Van Laar activity coefficient and {@code P0_i} the pure-component saturation
 * vapour pressure from {@link neqsim.thermo.util.empiric.NitricSulfuricAcidVaporPressure}. Because the liquid-phase
 * fugacity coefficient is built directly from the activity model, at low system pressure (where the vapour-phase
 * fugacity coefficient tends to one) the component fugacities equal the partial pressures of the reference paper.
 * </p>
 *
 * <p>
 * The Taleb activity correlation is recommended from 190 K through 298 K. The high-pressure CO2 carrier tuning in this
 * class is an empirical engineering fit. In a predominantly CO2 vapour phase, the direct gamma-phi flash uses the same
 * tuned trace-carrier SRK fugacity reference for water, nitric acid and sulfuric acid so that iteration and final
 * fugacity auditing use one internally consistent model. Results above 298 K are therefore extrapolations of the
 * activity model and are not validation against the low-temperature source paper.
 * </p>
 *
 * <p>
 * The direct EOS-GE TP-flash path currently supports fluid phases only. Enabling solid-phase or wax checks is rejected
 * explicitly because those post-processing paths are not yet integrated with the direct gamma-phi solver.
 * </p>
 *
 * @author NeqSim
 * @version $Id: $Id
 */
public class SystemVanLaarActivitySRK extends SystemEosGE {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** HNO3 tuned SRK critical temperature in kelvin. */
  private static final double HNO3_TUNED_TC_K = 578.433819;

  /** HNO3 tuned SRK critical pressure in bara. */
  private static final double HNO3_TUNED_PC_BAR = 107.435001;

  /** HNO3 tuned SRK acentric factor. */
  private static final double HNO3_TUNED_ACENTRIC_FACTOR = 0.849356;

  /** CO2-HNO3 density-fit quadratic coefficient for rho^2, rho in kg/m3. */
  private static final double CO2_HNO3_KIJ_DENSITY_A = 9.17377e-7;

  /** CO2-HNO3 density-fit quadratic coefficient for rho, rho in kg/m3. */
  private static final double CO2_HNO3_KIJ_DENSITY_B = -0.00132671;

  /** CO2-HNO3 density-fit quadratic intercept. */
  private static final double CO2_HNO3_KIJ_DENSITY_C = 0.614188;

  /** Lower calibrated pure CO2 density limit in kg/m3. */
  private static final double CO2_HNO3_KIJ_DENSITY_MIN_KG_M3 = 319.0;

  /** Upper calibrated pure CO2 density limit in kg/m3. */
  private static final double CO2_HNO3_KIJ_DENSITY_MAX_KG_M3 = 1054.0;

  /** CO2-H2SO4 fitted kij at 47.5 degrees Celsius. */
  private static final double CO2_H2SO4_KIJ_AT_47_5_C = 0.13751412;

  /** CO2-H2SO4 fitted kij slope versus degrees Celsius. */
  private static final double CO2_H2SO4_KIJ_SLOPE = -0.00181899;

  /** Initial flash K-value for CO2, strongly favouring the SRK vapour phase. */
  private static final double INITIAL_K_CO2 = 1.0e20;

  /** Initial flash K-value for water in the Van Laar acid liquid system. */
  private static final double INITIAL_K_WATER = 1.0e-5;

  /**
   * Initial flash K-value for nitric acid, favouring the Van Laar liquid phase.
   */
  private static final double INITIAL_K_NITRIC_ACID = 1.0e-10;

  /**
   * Initial flash K-value for sulfuric acid, strongly favouring the Van Laar liquid phase.
   */
  private static final double INITIAL_K_SULFURIC_ACID = 1.0e-30;

  /** Maximum carrier/liquid K value for material nitric acid. */
  private static final double MAXIMUM_NITRIC_ACID_CARRIER_K = 0.45;

  /** Logarithmic damping factor used by direct gamma-phi successive substitution. */
  private static final double GAMMA_PHI_K_VALUE_DAMPING_FACTOR = 0.25;

  /** Stronger damping used when either direct gamma-phi phase is close to disappearing. */
  private static final double PHASE_BOUNDARY_GAMMA_PHI_K_VALUE_DAMPING_FACTOR = 0.05;

  /** Minority phase fraction below which direct gamma-phi updates use stronger damping. */
  private static final double GAMMA_PHI_PHASE_BOUNDARY_FRACTION = 1.0e-4;

  /** Maximum phase fraction treated as a numerical trace. */
  private static final double TRACE_PHASE_BETA_LIMIT = 1.0e-5;

  /** Maximum acid-water moles in a numerical trace phase. */
  private static final double TRACE_PHASE_ACID_WATER_MOLE_LIMIT = 5.0;

  /** Minimum sulfuric acid moles that make a trace phase material. */
  private static final double MATERIAL_H2SO4_MOLE_LIMIT = 1.0e-5;

  /** Water molar mass in gram per mole. */
  private static final double WATER_MOLAR_MASS_G_PER_MOL = 18.01528;

  /** Nitric acid molar mass in gram per mole. */
  private static final double NITRIC_ACID_MOLAR_MASS_G_PER_MOL = 63.01284;

  /** Sulfuric acid molar mass in gram per mole. */
  private static final double SULFURIC_ACID_MOLAR_MASS_G_PER_MOL = 98.07848;

  /**
   * Trace carrier-phase water amount in moles used for gamma-phi acid solubility checks.
   */
  private static final double TRACE_WATER_MOLES = 1.0e-8;

  /**
   * Trace carrier-phase acid amount in moles used for gamma-phi acid solubility checks.
   */
  private static final double TRACE_ACID_MOLES = 1.0e-10;

  /** Numerically present but negligible acid amount in moles. */
  private static final double NEGLIGIBLE_ACID_MOLES = 1.0e-30;

  /** Minimum acid/water mass fraction for a Van Laar activity phase. */
  private static final double MINIMUM_ACID_WATER_PHASE_MASS_FRACTION = 0.50;

  /** Minimum CO2 mole fraction for a carrier phase. */
  private static final double MINIMUM_CARBON_DIOXIDE_CARRIER_MOLE_FRACTION = 0.80;

  /** Cached trace-carrier fugacity coefficients, keyed by component and dominant acid. */
  private transient Map<String, Double> carrierFugacityCoefficientCache = new HashMap<String, Double>();

  /** Temperature associated with the trace-carrier fugacity cache, in kelvin. */
  private transient double carrierFugacityCacheTemperature = Double.NaN;

  /** Pressure associated with the trace-carrier fugacity cache, in bara. */
  private transient double carrierFugacityCachePressure = Double.NaN;

  /** Dominant acid associated with the trace-carrier fugacity cache. */
  private transient String carrierFugacityCacheAcid = null;

  /**
   * <p>
   * Constructor for SystemVanLaarActivitySRK. Defaults to 298.15 K and 1.0 bara.
   * </p>
   */
  public SystemVanLaarActivitySRK() {
    this(298.15, 1.0, false);
  }

  /**
   * <p>
   * Constructor for SystemVanLaarActivitySRK.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   */
  public SystemVanLaarActivitySRK(double T, double P) {
    this(T, P, false);
  }

  /**
   * <p>
   * Constructor for SystemVanLaarActivitySRK.
   * </p>
   *
   * @param T The temperature in unit Kelvin
   * @param P The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemVanLaarActivitySRK(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "VanLaar-acid-GE-model";
    attractiveTermNumber = 0;
    setImplementedCompositionDeriativesofFugacity(false);

    configureEosGePhases(T, P, new PhaseSrkEos(), new PhaseGEVanLaarAcid());
    for (int phaseIndex = 1; phaseIndex < getMaxNumberOfPhases(); phaseIndex++) {
      if (getPhase(phaseIndex) instanceof PhaseGEVanLaarAcid) {
        getPhase(phaseIndex).setType(PhaseType.AQUEOUS);
        setPhaseType(phaseIndex, PhaseType.AQUEOUS);
      }
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Applies acid-specific SRK vapour tuning immediately after adding components so users can build this model with the
   * regular NeqSim component API.
   * </p>
   */
  @Override
  public void addComponent(String componentName, double moles) {
    super.addComponent(componentName, moles);
    applyNitricAcidPureComponentTuning();
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Refreshes acid binary interaction parameters before the analytic or numeric initialization evaluates SRK fugacity
   * coefficients.
   * </p>
   */
  @Override
  public void init(int initType) {
    applyAcidVapourTuning();
    super.init(initType);
    if (initType == 0) {
      applyVanLaarInitialFlashKValues();
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Refreshes acid binary interaction parameters before phase-specific initialization.
   * </p>
   */
  @Override
  public void init(int type, int phaseNum) {
    applyAcidVapourTuning();
    super.init(type, phaseNum);
  }

  /**
   * Apply fitted SRK vapour-side acid parameters to all phases that contain the corresponding components.
   */
  private void applyAcidVapourTuning() {
    applyNitricAcidPureComponentTuning();
    applyAcidBinaryInteractionTuning();
  }

  /**
   * Apply gamma-phi-appropriate initial K-values for the first TP flash estimate.
   */
  private void applyVanLaarInitialFlashKValues() {
    setInitialKValue("CO2", INITIAL_K_CO2);
    setInitialKValue("water", INITIAL_K_WATER);
    setInitialKValue("nitric acid", INITIAL_K_NITRIC_ACID);
    setInitialKValue("sulfuric acid", INITIAL_K_SULFURIC_ACID);
  }

  /**
   * Set an initial K-value for a component in every available phase.
   *
   * @param componentName component name
   * @param kValue initial K-value
   */
  private void setInitialKValue(String componentName, double kValue) {
    for (int phaseIndex = 0; phaseIndex < getMaxNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = getPhase(phaseIndex);
      if (phase != null && phase.hasComponent(componentName)) {
        phase.getComponent(componentName).setK(kValue);
      }
    }
  }

  /**
   * Checks whether a component belongs to the Van Laar acid activity model.
   *
   * @param componentName component name to check
   * @return true for water, nitric acid, and sulfuric acid components
   */
  public static boolean isVanLaarActivityComponent(String componentName) {
    if (componentName == null) {
      return false;
    }
    String normalizedName = componentName.trim().toLowerCase();
    return "water".equals(normalizedName) || "h2o".equals(normalizedName) || "nitric acid".equals(normalizedName)
        || "hno3".equals(normalizedName) || "sulfuric acid".equals(normalizedName)
        || "sulphuric acid".equals(normalizedName) || "h2so4".equals(normalizedName);
  }

  /**
   * Calculates the mass fraction of water, nitric acid, and sulfuric acid in a phase.
   *
   * @param phase phase to inspect
   * @return acid/water mass fraction in the phase, or 0.0 for an empty phase
   */
  public static double acidWaterMassFraction(PhaseInterface phase) {
    double acidWaterMass = 0.0;
    double totalMass = 0.0;
    for (int componentIndex = 0; componentIndex < phase.getNumberOfComponents(); componentIndex++) {
      ComponentInterface component = phase.getComponent(componentIndex);
      double componentMass = component.getx() * component.getMolarMass();
      totalMass += componentMass;
      if (isVanLaarActivityComponent(component.getComponentName())) {
        acidWaterMass += componentMass;
      }
    }
    if (totalMass <= 0.0) {
      return 0.0;
    }
    return acidWaterMass / totalMass;
  }

  /**
   * Checks whether a phase should be represented by the Van Laar activity model.
   *
   * @param phase phase to inspect
   * @return true if water and acids dominate the phase mass
   */
  public static boolean isPredominantlyAcidWaterPhase(PhaseInterface phase) {
    return acidWaterMassFraction(phase) >= MINIMUM_ACID_WATER_PHASE_MASS_FRACTION;
  }

  /**
   * Checks whether a phase is a CO2-rich SRK carrier phase.
   *
   * @param phase phase to inspect
   * @return true if CO2 is present and dominates the phase mole fraction
   */
  public static boolean isPredominantlyCarbonDioxidePhase(PhaseInterface phase) {
    if (!phase.hasComponent("CO2")) {
      return false;
    }
    return phase.getComponent("CO2").getx() >= MINIMUM_CARBON_DIOXIDE_CARRIER_MOLE_FRACTION;
  }

  /**
   * Calculates the tuned SRK fugacity coefficient for an activity-model component in the trace-component CO2 carrier
   * reference. The direct gamma-phi flash uses this same reference for water, nitric acid and sulfuric acid whenever
   * the actual vapour phase is predominantly CO2.
   *
   * @param componentName component whose carrier fugacity coefficient is needed
   * @return SRK fugacity coefficient in a trace-component CO2 carrier phase
   */
  public double carbonDioxideCarrierFugacityCoefficient(String componentName) {
    String normalizedComponentName = normalizeComponentName(componentName);
    String dominantAcid = dominantAcidName();
    refreshCarrierFugacityCache(dominantAcid);
    String cacheKey = dominantAcid + ":" + normalizedComponentName;
    Double cachedValue = carrierFugacityCoefficientCache.get(cacheKey);
    if (cachedValue != null) {
      return cachedValue.doubleValue();
    }
    double fugacityCoefficient = carbonDioxideCarrierComponentFugacityCoefficient(normalizedComponentName, dominantAcid,
        getTemperature() - 273.15, getPressure());
    carrierFugacityCoefficientCache.put(cacheKey, fugacityCoefficient);
    return fugacityCoefficient;
  }

  /**
   * Clear the carrier fugacity cache when its thermodynamic state changes.
   *
   * @param dominantAcid dominant acid used to construct the trace carrier
   */
  private void refreshCarrierFugacityCache(String dominantAcid) {
    if (carrierFugacityCoefficientCache == null) {
      carrierFugacityCoefficientCache = new HashMap<String, Double>();
    }
    if (Double.compare(carrierFugacityCacheTemperature, getTemperature()) != 0
        || Double.compare(carrierFugacityCachePressure, getPressure()) != 0 || carrierFugacityCacheAcid == null
        || !carrierFugacityCacheAcid.equals(dominantAcid)) {
      carrierFugacityCoefficientCache.clear();
      carrierFugacityCacheTemperature = getTemperature();
      carrierFugacityCachePressure = getPressure();
      carrierFugacityCacheAcid = dominantAcid;
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean requiresDirectGammaPhiFlash() {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public double getGammaPhiVapourFugacityCoefficient(ComponentInterface component, PhaseInterface vapourPhase) {
    component.fugcoef(vapourPhase);
    if (isPredominantlyCarbonDioxidePhase(vapourPhase) && isVanLaarActivityComponent(component.getComponentName())) {
      return carbonDioxideCarrierFugacityCoefficient(component.getComponentName());
    }
    return component.getFugacityCoefficient();
  }

  /** {@inheritDoc} */
  @Override
  public double constrainGammaPhiKValue(ComponentInterface component, double targetK) {
    if (isMaterialNitricAcidComponent(component)) {
      return Math.min(targetK, MAXIMUM_NITRIC_ACID_CARRIER_K);
    }
    return targetK;
  }

  /** {@inheritDoc} */
  @Override
  public double relaxGammaPhiKValue(double previousK, double targetK) {
    if (previousK <= 0.0 || targetK <= 0.0 || !Double.isFinite(previousK) || !Double.isFinite(targetK)) {
      return targetK;
    }
    double beta = getBeta();
    double minorityPhaseFraction = Double.isFinite(beta) ? Math.min(beta, 1.0 - beta) : 0.5;
    double dampingFactor = minorityPhaseFraction < GAMMA_PHI_PHASE_BOUNDARY_FRACTION
        ? PHASE_BOUNDARY_GAMMA_PHI_K_VALUE_DAMPING_FACTOR
        : GAMMA_PHI_K_VALUE_DAMPING_FACTOR;
    return Math.exp(Math.log(previousK) + dampingFactor * Math.log(targetK / previousK));
  }

  /**
   * Check whether a component is material nitric acid in the feed.
   *
   * @param component component to inspect
   * @return {@code true} for non-trace nitric acid
   */
  private boolean isMaterialNitricAcidComponent(ComponentInterface component) {
    String componentName = component.getComponentName();
    if (componentName == null) {
      return false;
    }
    String normalizedName = componentName.trim().toLowerCase();
    return ("nitric acid".equals(normalizedName) || "hno3".equals(normalizedName)) && component.getz() > 1.0e-12;
  }

  /** {@inheritDoc} */
  @Override
  public boolean finishGammaPhiFlash(double deviation, double phaseFractionMinimumLimit) {
    if (collapseTracePhase()) {
      return true;
    }
    if (!isGammaPhiResultAcceptable(deviation, phaseFractionMinimumLimit)) {
      return false;
    }
    acceptGammaPhiResult();
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public String getGammaPhiFlashDiagnostics(double deviation, double phaseFractionMinimumLimit) {
    String fugacityReport = getFugacityConsistencyReport(phaseFractionMinimumLimit);
    return "deviation=" + deviation + ", beta=" + getBeta() + ", fugacity="
        + (fugacityReport == null ? "consistent" : fugacityReport);
  }

  /**
   * Accept a finite direct gamma-phi result and preserve the model phase roles.
   *
   */
  private void acceptGammaPhiResult() {
    if (getNumberOfPhases() >= 2) {
      getPhase(0).setType(PhaseType.GAS);
      setPhaseType(0, PhaseType.GAS);
      getPhase(1).setType(PhaseType.AQUEOUS);
      setPhaseType(1, PhaseType.AQUEOUS);
    }
    normalizeActivePhaseFractions();
    init(1);
  }

  /**
   * Check whether a direct gamma-phi K-value result is finite and physically usable.
   *
   * @param deviation final logarithmic K-value deviation
   * @param phaseFractionMinimumLimit minimum active phase fraction
   * @return {@code true} when the state can be returned
   */
  private boolean isGammaPhiResultAcceptable(double deviation, double phaseFractionMinimumLimit) {
    if (!Double.isFinite(deviation) || !Double.isFinite(getBeta()) || getBeta() < 0.0 || getBeta() > 1.0) {
      return false;
    }
    for (int phaseIndex = 0; phaseIndex < getNumberOfPhases(); phaseIndex++) {
      double beta = getBeta(phaseIndex);
      if (!Double.isFinite(beta) || beta < 0.0 || beta > 1.0) {
        return false;
      }
      for (int componentIndex = 0; componentIndex < getPhase(phaseIndex).getNumberOfComponents(); componentIndex++) {
        ComponentInterface component = getPhase(phaseIndex).getComponent(componentIndex);
        if (!Double.isFinite(component.getx()) || component.getx() < 0.0 || !Double.isFinite(component.getK())
            || component.getK() <= 0.0) {
          return false;
        }
      }
    }
    return getFugacityConsistencyReport(phaseFractionMinimumLimit) == null
        && (getNumberOfPhases() < 2 || hasConstrainedPhaseRoles(phaseFractionMinimumLimit));
  }

  /**
   * Check the intended carrier/activity phase roles for a material two-phase split.
   *
   * @param phaseFractionMinimumLimit minimum active phase fraction
   * @return {@code true} when phase zero is CO2-rich and phase one is acid/water-rich
   */
  private boolean hasConstrainedPhaseRoles(double phaseFractionMinimumLimit) {
    if (getNumberOfPhases() < 2 || getBeta(0) <= 10.0 * phaseFractionMinimumLimit
        || getBeta(1) <= 10.0 * phaseFractionMinimumLimit) {
      return false;
    }
    return isPredominantlyCarbonDioxidePhase(getPhase(0)) && isPredominantlyAcidWaterPhase(getPhase(1));
  }

  /**
   * Collapse a non-material trace phase to the dominant feed-composition phase.
   *
   * @return {@code true} when a trace phase was collapsed
   */
  private boolean collapseTracePhase() {
    if (getNumberOfPhases() != 2) {
      return false;
    }
    int tracePhaseIndex = getBeta(0) <= getBeta(1) ? 0 : 1;
    int dominantPhaseIndex = tracePhaseIndex == 0 ? 1 : 0;
    double traceBeta = getBeta(tracePhaseIndex);
    if (!Double.isFinite(traceBeta) || traceBeta > TRACE_PHASE_BETA_LIMIT
        || acidWaterMolesInPhase(tracePhaseIndex) > TRACE_PHASE_ACID_WATER_MOLE_LIMIT
        || sulfuricAcidMolesInPhase(tracePhaseIndex) > MATERIAL_H2SO4_MOLE_LIMIT) {
      return false;
    }
    collapseToSinglePhase(dominantPhaseIndex);
    return true;
  }

  /**
   * Calculate total water and acid moles in one phase.
   *
   * @param phaseIndex phase index
   * @return total water, nitric-acid and sulfuric-acid moles
   */
  private double acidWaterMolesInPhase(int phaseIndex) {
    double moles = 0.0;
    PhaseInterface phase = getPhase(phaseIndex);
    for (int componentIndex = 0; componentIndex < phase.getNumberOfComponents(); componentIndex++) {
      ComponentInterface component = phase.getComponent(componentIndex);
      if (isVanLaarActivityComponent(component.getComponentName())) {
        moles += component.getNumberOfMolesInPhase();
      }
    }
    return moles;
  }

  /**
   * Calculate sulfuric-acid moles in one phase.
   *
   * @param phaseIndex phase index
   * @return sulfuric-acid moles, or zero if absent
   */
  private double sulfuricAcidMolesInPhase(int phaseIndex) {
    PhaseInterface phase = getPhase(phaseIndex);
    if (!phase.hasComponent("sulfuric acid")) {
      return 0.0;
    }
    return phase.getComponent("sulfuric acid").getNumberOfMolesInPhase();
  }

  /**
   * Compare component fugacities in all material active phases.
   *
   * @param phaseFractionMinimumLimit minimum active phase fraction
   * @return {@code null} when consistent, otherwise the first diagnostic
   */
  private String getFugacityConsistencyReport(double phaseFractionMinimumLimit) {
    if (getNumberOfPhases() < 2) {
      return null;
    }
    PhaseInterface firstPhase = getPhase(0);
    for (int componentIndex = 0; componentIndex < firstPhase.getNumberOfComponents(); componentIndex++) {
      ComponentInterface firstComponent = firstPhase.getComponent(componentIndex);
      String componentName = firstComponent.getComponentName();
      if (!isVanLaarActivityComponent(componentName)) {
        continue;
      }
      double totalMoles = firstComponent.getNumberOfmoles();
      if (totalMoles <= 1.0e-20) {
        continue;
      }
      double referenceFugacity = Double.NaN;
      int referencePhase = -1;
      for (int phaseIndex = 0; phaseIndex < getNumberOfPhases(); phaseIndex++) {
        if (getBeta(phaseIndex) <= 10.0 * phaseFractionMinimumLimit) {
          continue;
        }
        ComponentInterface component = getPhase(phaseIndex).getComponent(componentIndex);
        if (component.getNumberOfMolesInPhase() <= Math.max(1.0e-16, totalMoles * 1.0e-14)) {
          continue;
        }
        double fugacityCoefficient;
        if (phaseIndex == 0) {
          fugacityCoefficient = getGammaPhiVapourFugacityCoefficient(component, getPhase(phaseIndex));
        } else {
          component.fugcoef(getPhase(phaseIndex));
          fugacityCoefficient = component.getFugacityCoefficient();
        }
        double fugacity = component.getx() * fugacityCoefficient * getPhase(phaseIndex).getPressure();
        if (!Double.isFinite(fugacity) || fugacity < 0.0) {
          return "non-finite " + componentName + " fugacity in phase " + phaseIndex + ": " + fugacity;
        }
        if (Double.isNaN(referenceFugacity)) {
          referenceFugacity = fugacity;
          referencePhase = phaseIndex;
          continue;
        }
        double scale = Math.max(1.0e-30, Math.max(Math.abs(referenceFugacity), Math.abs(fugacity)));
        double relativeError = Math.abs(fugacity - referenceFugacity) / scale;
        if (relativeError > 1.0e-3) {
          return componentName + " phase " + referencePhase + " f=" + referenceFugacity + " bara, phase " + phaseIndex
              + " f=" + fugacity + " bara, relative error=" + relativeError;
        }
      }
    }
    return null;
  }

  /**
   * Collapse the active system to one selected phase at the overall feed composition.
   *
   * @param phaseIndex phase whose model and type should be retained
   */
  void collapseToSinglePhase(int phaseIndex) {
    int selectedCreationOrderIndex = getPhaseIndex(phaseIndex);
    PhaseType phaseType = getPhase(phaseIndex).getType();
    setNumberOfPhases(1);
    setPhaseIndex(0, selectedCreationOrderIndex);
    setBeta(0, 1.0);
    setPhaseType(0, phaseType);
    reset_x_y();
    init(1, 0);
  }

  /** Normalize active phase fractions after direct gamma-phi acceptance. */
  private void normalizeActivePhaseFractions() {
    double betaSum = 0.0;
    for (int phaseIndex = 0; phaseIndex < getNumberOfPhases(); phaseIndex++) {
      betaSum += getBeta(phaseIndex);
    }
    if (!Double.isFinite(betaSum) || betaSum <= 0.0) {
      return;
    }
    for (int phaseIndex = 0; phaseIndex < getNumberOfPhases(); phaseIndex++) {
      setBeta(phaseIndex, getBeta(phaseIndex) / betaSum);
    }
  }

  /**
   * Finds the dominant acid in the system for carrier fugacity reference setup.
   *
   * @return {@code "nitric acid"} or {@code "sulfuric acid"}
   */
  private String dominantAcidName() {
    double nitricAcidMoles = 0.0;
    double sulfuricAcidMoles = 0.0;
    if (getPhase(0).hasComponent("nitric acid")) {
      nitricAcidMoles = getPhase(0).getComponent("nitric acid").getNumberOfmoles();
    }
    if (getPhase(0).hasComponent("sulfuric acid")) {
      sulfuricAcidMoles = getPhase(0).getComponent("sulfuric acid").getNumberOfmoles();
    }
    return sulfuricAcidMoles > nitricAcidMoles ? "sulfuric acid" : "nitric acid";
  }

  /**
   * Apply fitted HNO3 SRK pure-component parameters to all phase component objects.
   */
  private void applyNitricAcidPureComponentTuning() {
    for (int phaseIndex = 0; phaseIndex < getMaxNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = getPhase(phaseIndex);
      if (phase != null && phase.hasComponent("nitric acid")) {
        ComponentInterface nitricAcid = phase.getComponent("nitric acid");
        nitricAcid.setTC(HNO3_TUNED_TC_K);
        nitricAcid.setPC(HNO3_TUNED_PC_BAR);
        nitricAcid.setAcentricFactor(HNO3_TUNED_ACENTRIC_FACTOR);
      }
    }
  }

  /** Apply fitted acid-carrier binary interaction parameters to EoS phases. */
  private void applyAcidBinaryInteractionTuning() {
    for (int phaseIndex = 0; phaseIndex < getMaxNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = getPhase(phaseIndex);
      if (phase instanceof PhaseEos) {
        applyAcidBinaryInteractionTuning((PhaseEos) phase);
      }
    }
  }

  /**
   * Apply fitted acid-carrier binary interaction parameters to a single EoS phase.
   *
   * @param phase EoS phase whose mixing rule should receive the acid interaction parameters
   */
  private void applyAcidBinaryInteractionTuning(PhaseEos phase) {
    EosMixingRulesInterface mixingRule = phase.getEosMixingRule();
    if (mixingRule == null || mixingRule.getBinaryInteractionParameters() == null || !phase.hasComponent("CO2")) {
      return;
    }
    int co2Index = phase.getComponent("CO2").getComponentNumber();
    double temperatureC = phase.getTemperature() - 273.15;
    int waterIndex = -1;

    if (phase.hasComponent("water")) {
      waterIndex = phase.getComponent("water").getComponentNumber();
      mixingRule.setBinaryInteractionParameter(co2Index, waterIndex, carbonDioxideWaterKij(temperatureC));
    }

    if (phase.hasComponent("nitric acid")) {
      int hno3Index = phase.getComponent("nitric acid").getComponentNumber();
      mixingRule.setBinaryInteractionParameter(co2Index, hno3Index,
          carbonDioxideNitricAcidKij(temperatureC, phase.getPressure()));
      if (waterIndex >= 0) {
        mixingRule.setBinaryInteractionParameter(hno3Index, waterIndex, 0.0);
      }
    }
    if (phase.hasComponent("sulfuric acid")) {
      int h2so4Index = phase.getComponent("sulfuric acid").getComponentNumber();
      mixingRule.setBinaryInteractionParameter(co2Index, h2so4Index, carbonDioxideSulfuricAcidKij(temperatureC));
      if (waterIndex >= 0) {
        mixingRule.setBinaryInteractionParameter(h2so4Index, waterIndex, 0.0);
      }
    }
  }

  /**
   * Fitted SRK binary interaction parameter for CO2-HNO3 as a function of pure CO2 density.
   *
   * @param carbonDioxideDensityKgM3 pure CO2 density in kg/m3
   * @return fitted CO2-HNO3 SRK binary interaction parameter
   */
  public static double carbonDioxideNitricAcidKijFromDensity(double carbonDioxideDensityKgM3) {
    double density = Math.max(CO2_HNO3_KIJ_DENSITY_MIN_KG_M3,
        Math.min(CO2_HNO3_KIJ_DENSITY_MAX_KG_M3, carbonDioxideDensityKgM3));
    return CO2_HNO3_KIJ_DENSITY_A * density * density + CO2_HNO3_KIJ_DENSITY_B * density + CO2_HNO3_KIJ_DENSITY_C;
  }

  /**
   * Calculates pure CO2 density from SRK at the specified condition.
   *
   * @param temperatureC temperature in degrees Celsius
   * @param pressureBar pressure in bara
   * @return pure CO2 density in kg/m3
   */
  public static double pureCarbonDioxideDensityKgM3(double temperatureC, double pressureBar) {
    SystemSrkEos carbonDioxide = new SystemSrkEos(temperatureC + 273.15, pressureBar);
    carbonDioxide.addComponent("CO2", 1.0);
    carbonDioxide.createDatabase(true);
    carbonDioxide.setMixingRule("classic");

    ThermodynamicOperations operations = new ThermodynamicOperations(carbonDioxide);
    operations.TPflash();
    carbonDioxide.initProperties();

    return carbonDioxide.getDensity("kg/m3");
  }

  /**
   * Fitted SRK binary interaction parameter for CO2-HNO3 using pure CO2 density.
   *
   * @param temperatureC temperature in degrees Celsius
   * @param pressureBar pressure in bara
   * @return fitted CO2-HNO3 SRK binary interaction parameter
   */
  public static double carbonDioxideNitricAcidKij(double temperatureC, double pressureBar) {
    double density = pureCarbonDioxideDensityKgM3(temperatureC, pressureBar);
    return carbonDioxideNitricAcidKijFromDensity(density);
  }

  /**
   * Fitted SRK binary interaction parameter for CO2-H2SO4 in the Van Laar acid gamma-phi model.
   *
   * @param temperatureC temperature in degrees Celsius
   * @return fitted CO2-H2SO4 SRK binary interaction parameter
   */
  public static double carbonDioxideSulfuricAcidKij(double temperatureC) {
    return CO2_H2SO4_KIJ_AT_47_5_C + CO2_H2SO4_KIJ_SLOPE * (temperatureC - 47.5);
  }

  /**
   * Estimate acid solubility in high-pressure CO2 with the Van Laar liquid-source fugacity and the tuned SRK CO2
   * carrier-phase fugacity coefficient embedded in this model.
   *
   * @param acidName acid name; supported values are {@code "nitric acid"}, {@code "HNO3"}, {@code "sulfuric acid"},
   * {@code "sulphuric acid"}, and {@code "H2SO4"}
   * @param acidWeightPercent acid concentration in the source liquid, in weight percent
   * @param waterWeightPercent water concentration in the source liquid, in weight percent
   * @param temperatureC temperature in degrees Celsius
   * @param pressureBar CO2 pressure in bara
   * @return acid mole fraction in the CO2 carrier phase, expressed as ppm mol
   * @throws IllegalArgumentException if the acid name, source composition, pressure, or temperature is outside the
   * supported range
   */
  public static double acidSolubilityInCarbonDioxidePpm(String acidName, double acidWeightPercent,
      double waterWeightPercent, double temperatureC, double pressureBar) {
    return componentSolubilityInCarbonDioxidePpm(acidName, acidName, acidWeightPercent, waterWeightPercent,
        temperatureC, pressureBar);
  }

  /**
   * Estimate a water or acid component solubility in high-pressure CO2 for a specified acid-water source, using the Van
   * Laar liquid-source fugacity and the tuned SRK CO2 carrier-phase fugacity coefficient embedded in this model.
   *
   * @param componentName component to report; supported values are {@code "water"}, {@code "H2O"},
   * {@code "nitric acid"}, {@code "HNO3"}, {@code "sulfuric acid"}, {@code "sulphuric acid"}, and {@code "H2SO4"}
   * @param sourceAcidName acid in the source liquid; supported acid aliases are the same as in
   * {@link #acidSolubilityInCarbonDioxidePpm(String, double, double, double, double)}
   * @param acidWeightPercent acid concentration in the source liquid, in weight percent
   * @param waterWeightPercent water concentration in the source liquid, in weight percent
   * @param temperatureC temperature in degrees Celsius
   * @param pressureBar CO2 pressure in bara
   * @return component mole fraction in the CO2 carrier phase, expressed as ppm mol
   * @throws IllegalArgumentException if the component name, source acid, source composition, pressure, or temperature
   * is outside the supported range
   */
  public static double componentSolubilityInCarbonDioxidePpm(String componentName, String sourceAcidName,
      double acidWeightPercent, double waterWeightPercent, double temperatureC, double pressureBar) {
    if (!Double.isFinite(acidWeightPercent) || !Double.isFinite(waterWeightPercent) || acidWeightPercent <= 0.0
        || waterWeightPercent < 0.0) {
      throw new IllegalArgumentException("Acid weight percent must be positive and water non-negative");
    }
    if (!Double.isFinite(pressureBar) || pressureBar <= 0.0) {
      throw new IllegalArgumentException("Pressure must be positive");
    }
    if (!Double.isFinite(temperatureC) || temperatureC + 273.15 <= 0.0) {
      throw new IllegalArgumentException("Temperature must be finite and above absolute zero");
    }
    String normalizedComponentName = normalizeComponentName(componentName);
    String normalizedAcidName = normalizeAcidName(sourceAcidName);
    double liquidFugacityBar = componentSourceFugacityBar(normalizedComponentName, normalizedAcidName,
        acidWeightPercent, waterWeightPercent, temperatureC);
    double carrierFugacityCoefficient = carbonDioxideCarrierComponentFugacityCoefficient(normalizedComponentName,
        normalizedAcidName, temperatureC, pressureBar);
    return liquidFugacityBar / (carrierFugacityCoefficient * pressureBar) * 1.0e6;
  }

  /**
   * Calculate liquid-source fugacity from a Van Laar acid-water source system.
   *
   * @param componentName component whose source fugacity should be calculated
   * @param acidName acid name
   * @param acidWeightPercent acid concentration in weight percent
   * @param waterWeightPercent water concentration in weight percent
   * @param temperatureC temperature in degrees Celsius
   * @return component source fugacity in bar
   */
  private static double componentSourceFugacityBar(String componentName, String acidName, double acidWeightPercent,
      double waterWeightPercent, double temperatureC) {
    double acidMoles = acidWeightPercent / acidMolarMass(acidName);
    double waterMoles = waterWeightPercent / WATER_MOLAR_MASS_G_PER_MOL;

    SystemVanLaarActivitySRK liquidSource = new SystemVanLaarActivitySRK(temperatureC + 273.15, 1.0);
    liquidSource.addComponent("water", waterMoles);
    liquidSource.addComponent("nitric acid", "nitric acid".equals(acidName) ? acidMoles : NEGLIGIBLE_ACID_MOLES);
    liquidSource.addComponent("sulfuric acid", "sulfuric acid".equals(acidName) ? acidMoles : NEGLIGIBLE_ACID_MOLES);
    liquidSource.createDatabase(true);
    liquidSource.setMixingRule("classic");
    liquidSource.init(0);
    liquidSource.init(1);

    PhaseInterface liquidPhase = findVanLaarLiquidPhase(liquidSource);
    ComponentInterface component = liquidPhase.getComponent(componentName);
    component.fugcoef(liquidPhase);
    return component.getx() * component.getFugacityCoefficient() * liquidPhase.getPressure();
  }

  /**
   * Calculate the tuned SRK fugacity coefficient of a trace source component in a CO2 carrier phase.
   *
   * @param componentName component whose fugacity coefficient should be calculated
   * @param acidName acid in the source liquid
   * @param temperatureC temperature in degrees Celsius
   * @param pressureBar pressure in bara
   * @return component fugacity coefficient in the CO2-rich SRK phase
   */
  private static double carbonDioxideCarrierComponentFugacityCoefficient(String componentName, String acidName,
      double temperatureC, double pressureBar) {
    SystemSrkEos carrier = new SystemSrkEos(temperatureC + 273.15, pressureBar);
    carrier.addComponent("CO2", 0.999999);
    carrier.addComponent("water", TRACE_WATER_MOLES);
    carrier.addComponent("nitric acid", "nitric acid".equals(acidName) ? TRACE_ACID_MOLES : NEGLIGIBLE_ACID_MOLES);
    carrier.addComponent("sulfuric acid", "sulfuric acid".equals(acidName) ? TRACE_ACID_MOLES : NEGLIGIBLE_ACID_MOLES);
    carrier.createDatabase(true);
    applyNitricAcidPureComponentTuning(carrier);
    carrier.setMixingRule("classic");
    carrier.setBinaryInteractionParameter("CO2", acidName, acidCarbonDioxideKij(acidName, temperatureC, pressureBar));
    carrier.setBinaryInteractionParameter("CO2", "water", carbonDioxideWaterKij(temperatureC));
    carrier.setBinaryInteractionParameter(acidName, "water", 0.0);

    ThermodynamicOperations operations = new ThermodynamicOperations(carrier);
    operations.TPflash();
    carrier.initProperties();

    PhaseInterface carrierPhase = findCarbonDioxideRichPhase(carrier);
    ComponentInterface component = carrierPhase.getComponent(componentName);
    component.fugcoef(carrierPhase);
    return component.getFugacityCoefficient();
  }

  /**
   * Locate the Van Laar liquid phase in a system.
   *
   * @param system system to search
   * @return Van Laar liquid phase
   * @throws IllegalArgumentException if no Van Laar liquid phase exists
   */
  private static PhaseInterface findVanLaarLiquidPhase(SystemInterface system) {
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = system.getPhase(phaseIndex);
      if (phase instanceof PhaseGEVanLaarAcid) {
        return phase;
      }
    }
    throw new IllegalArgumentException("No Van Laar acid liquid phase is available");
  }

  /**
   * Locate the CO2-rich phase in an SRK carrier system.
   *
   * @param system system to search
   * @return phase with the highest CO2 mole fraction
   */
  private static PhaseInterface findCarbonDioxideRichPhase(SystemInterface system) {
    PhaseInterface bestPhase = null;
    double bestCarbonDioxideFraction = -1.0;
    for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = system.getPhase(phaseIndex);
      if (phase.hasComponent("CO2")) {
        double carbonDioxideFraction = phase.getComponent("CO2").getx();
        if (carbonDioxideFraction > bestCarbonDioxideFraction) {
          bestCarbonDioxideFraction = carbonDioxideFraction;
          bestPhase = phase;
        }
      }
    }
    if (bestPhase == null) {
      throw new IllegalArgumentException("No CO2-rich SRK carrier phase is available");
    }
    return bestPhase;
  }

  /**
   * Apply fitted HNO3 SRK pure-component parameters to a system.
   *
   * @param system system whose nitric acid components should be updated
   */
  private static void applyNitricAcidPureComponentTuning(SystemInterface system) {
    for (int phaseIndex = 0; phaseIndex < system.getMaxNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = system.getPhase(phaseIndex);
      if (phase != null && phase.hasComponent("nitric acid")) {
        ComponentInterface nitricAcid = phase.getComponent("nitric acid");
        nitricAcid.setTC(HNO3_TUNED_TC_K);
        nitricAcid.setPC(HNO3_TUNED_PC_BAR);
        nitricAcid.setAcentricFactor(HNO3_TUNED_ACENTRIC_FACTOR);
      }
    }
  }

  /**
   * Get the model acid-CO2 binary interaction parameter.
   *
   * @param acidName normalized acid name
   * @param temperatureC temperature in degrees Celsius
   * @param pressureBar pressure in bara
   * @return acid-CO2 binary interaction parameter
   */
  private static double acidCarbonDioxideKij(String acidName, double temperatureC, double pressureBar) {
    if ("nitric acid".equals(acidName)) {
      return carbonDioxideNitricAcidKij(temperatureC, pressureBar);
    }
    return carbonDioxideSulfuricAcidKij(temperatureC);
  }

  /**
   * CO2-water SRK binary interaction parameter used for trace-water carrier calculations.
   *
   * @param temperatureC temperature in degrees Celsius
   * @return CO2-water binary interaction parameter
   */
  private static double carbonDioxideWaterKij(double temperatureC) {
    return 0.46851 - 98.73906 / (temperatureC + 273.15);
  }

  /**
   * Normalize supported acid aliases.
   *
   * @param acidName acid name or formula
   * @return normalized acid name
   * @throws IllegalArgumentException if the acid is not supported
   */
  private static String normalizeAcidName(String acidName) {
    if (acidName == null) {
      throw new IllegalArgumentException("Acid name cannot be null");
    }
    String normalized = acidName.trim().toLowerCase();
    if ("nitric acid".equals(normalized) || "hno3".equals(normalized)) {
      return "nitric acid";
    }
    if ("sulfuric acid".equals(normalized) || "sulphuric acid".equals(normalized) || "h2so4".equals(normalized)) {
      return "sulfuric acid";
    }
    throw new IllegalArgumentException("Unsupported acid: " + acidName);
  }

  /**
   * Normalize supported component aliases.
   *
   * @param componentName component name or formula
   * @return normalized component name
   * @throws IllegalArgumentException if the component is not supported
   */
  private static String normalizeComponentName(String componentName) {
    if (componentName == null) {
      throw new IllegalArgumentException("Component name cannot be null");
    }
    String normalized = componentName.trim().toLowerCase();
    if ("water".equals(normalized) || "h2o".equals(normalized)) {
      return "water";
    }
    return normalizeAcidName(componentName);
  }

  /**
   * Molar mass of a supported acid.
   *
   * @param acidName normalized acid name
   * @return acid molar mass in gram per mole
   */
  private static double acidMolarMass(String acidName) {
    if ("nitric acid".equals(acidName)) {
      return NITRIC_ACID_MOLAR_MASS_G_PER_MOL;
    }
    return SULFURIC_ACID_MOLAR_MASS_G_PER_MOL;
  }

  /** {@inheritDoc} */
  @Override
  public SystemVanLaarActivitySRK clone() {
    SystemVanLaarActivitySRK clonedSystem = null;
    try {
      clonedSystem = (SystemVanLaarActivitySRK) super.clone();
    } catch (Exception ex) {
      logger.error("Cloning failed.", ex);
    }

    if (clonedSystem != null) {
      clonedSystem.carrierFugacityCoefficientCache = new HashMap<String, Double>();
      clonedSystem.carrierFugacityCacheTemperature = Double.NaN;
      clonedSystem.carrierFugacityCachePressure = Double.NaN;
      clonedSystem.carrierFugacityCacheAcid = null;
    }

    return clonedSystem;
  }
}
