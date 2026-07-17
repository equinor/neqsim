/*
 * SystemVanLaarActivitySRK.java
 */

package neqsim.thermo.system;

import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.phase.PhaseGEVanLaarAcid;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.phase.PhasePureComponentSolid;
import neqsim.thermo.phase.PhaseSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * This class defines a gamma-phi thermodynamic system for the
 * water-nitric-acid-sulfuric-acid
 * mixture. The vapour phase is described by the SRK equation of state, while
 * every liquid phase uses
 * the Van Laar excess-Gibbs-energy model of Taleb, Ponche and Mirabel (1996)
 * ({@link neqsim.thermo.phase.PhaseGEVanLaarAcid}).
 *
 * <p>
 * The system reproduces the equilibrium identity
 * {@code fugacity_i = gamma_i * x_i * P0_i} for the
 * three modelled acids, where {@code gamma_i} is the Van Laar activity
 * coefficient and {@code P0_i}
 * the pure-component saturation vapour pressure from
 * {@link neqsim.thermo.util.empiric.NitricSulfuricAcidVaporPressure}. Because
 * the liquid-phase
 * fugacity coefficient is built directly from the activity model, at low system
 * pressure (where the
 * vapour-phase fugacity coefficient tends to one) the component fugacities
 * equal the partial
 * pressures of the reference paper.
 * </p>
 *
 * @author NeqSim
 * @version $Id: $Id
 */
public class SystemVanLaarActivitySRK extends SystemEos {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** HNO3 tuned SRK critical temperature in kelvin. */
  private static final double HNO3_TUNED_TC_K = 578.433819;

  /** HNO3 tuned SRK critical pressure in bara. */
  private static final double HNO3_TUNED_PC_BAR = 107.435001;

  /** HNO3 tuned SRK acentric factor. */
  private static final double HNO3_TUNED_ACENTRIC_FACTOR = 0.849356;

  /** CO2-HNO3 fitted temperature-independent kij base. */
  private static final double CO2_HNO3_KIJ_BASE = 0.15148833;

  /** CO2-HNO3 fitted kij slope versus degrees Celsius. */
  private static final double CO2_HNO3_KIJ_SLOPE = -0.00028980;

  /** CO2-HNO3 low-temperature Gaussian kij bump amplitude. */
  private static final double CO2_HNO3_KIJ_LOW_TEMP_AMPLITUDE = 0.07478942;

  /** CO2-HNO3 low-temperature Gaussian centre temperature in degrees Celsius. */
  private static final double CO2_HNO3_KIJ_LOW_TEMP_CENTER_C = -25.0;

  /** CO2-HNO3 low-temperature Gaussian width in degrees Celsius. */
  private static final double CO2_HNO3_KIJ_LOW_TEMP_WIDTH_C = 10.0;

  /** CO2-HNO3 mid-temperature Gaussian kij dip amplitude. */
  private static final double CO2_HNO3_KIJ_MID_TEMP_DIP_AMPLITUDE = 0.08044221;

  /**
   * CO2-HNO3 mid-temperature Gaussian dip centre temperature in degrees Celsius.
   */
  private static final double CO2_HNO3_KIJ_MID_TEMP_DIP_CENTER_C = 40.0;

  /** CO2-HNO3 mid-temperature Gaussian dip width in degrees Celsius. */
  private static final double CO2_HNO3_KIJ_MID_TEMP_DIP_WIDTH_C = 6.0;

  /** CO2-HNO3 high-temperature Gaussian kij bump amplitude. */
  private static final double CO2_HNO3_KIJ_HIGH_TEMP_AMPLITUDE = 0.12485075;

  /**
   * CO2-HNO3 high-temperature Gaussian bump centre temperature in degrees
   * Celsius.
   */
  private static final double CO2_HNO3_KIJ_HIGH_TEMP_CENTER_C = 51.0;

  /** CO2-HNO3 high-temperature Gaussian bump width in degrees Celsius. */
  private static final double CO2_HNO3_KIJ_HIGH_TEMP_WIDTH_C = 5.0;

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
   * Initial flash K-value for sulfuric acid, strongly favouring the Van Laar
   * liquid phase.
   */
  private static final double INITIAL_K_SULFURIC_ACID = 1.0e-30;

  /** Water molar mass in gram per mole. */
  private static final double WATER_MOLAR_MASS_G_PER_MOL = 18.01528;

  /** Nitric acid molar mass in gram per mole. */
  private static final double NITRIC_ACID_MOLAR_MASS_G_PER_MOL = 63.01284;

  /** Sulfuric acid molar mass in gram per mole. */
  private static final double SULFURIC_ACID_MOLAR_MASS_G_PER_MOL = 98.07848;

  /**
   * Trace carrier-phase water amount in moles used for gamma-phi acid solubility
   * checks.
   */
  private static final double TRACE_WATER_MOLES = 1.0e-8;

  /**
   * Trace carrier-phase acid amount in moles used for gamma-phi acid solubility
   * checks.
   */
  private static final double TRACE_ACID_MOLES = 1.0e-10;

  /** Numerically present but negligible acid amount in moles. */
  private static final double NEGLIGIBLE_ACID_MOLES = 1.0e-30;

  /**
   * Minimum water mass fraction for a phase to be treated as aqueous acid liquid.
   */
  private static final double MINIMUM_AQUEOUS_WATER_MASS_FRACTION = 0.01;

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
   * @param T              The temperature in unit Kelvin
   * @param P              The pressure in unit bara (absolute pressure)
   * @param checkForSolids Set true to do solid phase check and calculations
   */
  public SystemVanLaarActivitySRK(double T, double P, boolean checkForSolids) {
    super(T, P, checkForSolids);
    modelName = "VanLaar-acid-GE-model";
    attractiveTermNumber = 0;
    setImplementedCompositionDeriativesofFugacity(false);

    phaseArray[0] = new PhaseSrkEos();
    phaseArray[0].setTemperature(T);
    phaseArray[0].setPressure(P);
    for (int i = 1; i < numberOfPhases; i++) {
      phaseArray[i] = new PhaseGEVanLaarAcid();
      phaseArray[i].setTemperature(T);
      phaseArray[i].setPressure(P);
      phaseArray[i].setType(PhaseType.AQUEOUS);
      setPhaseType(i, PhaseType.AQUEOUS);
    }

    if (solidPhaseCheck) {
      setNumberOfPhases(4);
      phaseArray[numberOfPhases - 1] = new PhasePureComponentSolid();
      phaseArray[numberOfPhases - 1].setTemperature(T);
      phaseArray[numberOfPhases - 1].setPressure(P);
      phaseArray[numberOfPhases - 1].setRefPhase(phaseArray[1].getRefPhase());
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Applies acid-specific SRK vapour tuning immediately after adding components
   * so users can build
   * this model with the regular NeqSim component API.
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
   * Refreshes temperature-dependent acid binary interaction parameters before the
   * analytic or
   * numeric initialization evaluates SRK fugacity coefficients.
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
   * Refreshes temperature-dependent acid binary interaction parameters before
   * phase-specific
   * initialization.
   * </p>
   */
  @Override
  public void init(int type, int phaseNum) {
    applyAcidVapourTuning();
    super.init(type, phaseNum);
  }

  /**
   * Apply fitted SRK vapour-side acid parameters to all phases that contain the
   * corresponding
   * components.
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
   * @param kValue        initial K-value
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
   * Apply fitted HNO3 SRK pure-component parameters to all phase component
   * objects.
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

  /** Apply fitted CO2-acid binary interaction parameters to EoS phases. */
  private void applyAcidBinaryInteractionTuning() {
    for (int phaseIndex = 0; phaseIndex < getMaxNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = getPhase(phaseIndex);
      if (phase instanceof PhaseEos) {
        applyAcidBinaryInteractionTuning((PhaseEos) phase);
      }
    }
  }

  /**
   * Apply fitted CO2-acid binary interaction parameters to a single EoS phase.
   *
   * @param phase EoS phase whose mixing rule should receive the acid interaction
   *              parameters
   */
  private void applyAcidBinaryInteractionTuning(PhaseEos phase) {
    EosMixingRulesInterface mixingRule = phase.getEosMixingRule();
    if (mixingRule == null || mixingRule.getBinaryInteractionParameters() == null
        || !phase.hasComponent("CO2")) {
      return;
    }
    int co2Index = phase.getComponent("CO2").getComponentNumber();
    double temperatureC = phase.getTemperature() - 273.15;

    if (phase.hasComponent("nitric acid")) {
      int hno3Index = phase.getComponent("nitric acid").getComponentNumber();
      mixingRule.setBinaryInteractionParameter(co2Index, hno3Index,
          carbonDioxideNitricAcidKij(temperatureC));
    }
    if (phase.hasComponent("sulfuric acid")) {
      int h2so4Index = phase.getComponent("sulfuric acid").getComponentNumber();
      mixingRule.setBinaryInteractionParameter(co2Index, h2so4Index,
          carbonDioxideSulfuricAcidKij(temperatureC));
    }
  }

  /**
   * Fitted SRK binary interaction parameter for CO2-HNO3 in the Van Laar acid
   * gamma-phi model.
   *
   * @param temperatureC temperature in degrees Celsius
   * @return fitted CO2-HNO3 SRK binary interaction parameter
   */
  public static double carbonDioxideNitricAcidKij(double temperatureC) {
    double lowTemperatureReduced = (temperatureC - CO2_HNO3_KIJ_LOW_TEMP_CENTER_C)
        / CO2_HNO3_KIJ_LOW_TEMP_WIDTH_C;
    double midTemperatureReduced = (temperatureC - CO2_HNO3_KIJ_MID_TEMP_DIP_CENTER_C)
        / CO2_HNO3_KIJ_MID_TEMP_DIP_WIDTH_C;
    double highTemperatureReduced = (temperatureC - CO2_HNO3_KIJ_HIGH_TEMP_CENTER_C)
        / CO2_HNO3_KIJ_HIGH_TEMP_WIDTH_C;
    return CO2_HNO3_KIJ_BASE + CO2_HNO3_KIJ_SLOPE * temperatureC
        + CO2_HNO3_KIJ_LOW_TEMP_AMPLITUDE
            * Math.exp(-0.5 * lowTemperatureReduced * lowTemperatureReduced)
        - CO2_HNO3_KIJ_MID_TEMP_DIP_AMPLITUDE
            * Math.exp(-0.5 * midTemperatureReduced * midTemperatureReduced)
        + CO2_HNO3_KIJ_HIGH_TEMP_AMPLITUDE
            * Math.exp(-0.5 * highTemperatureReduced * highTemperatureReduced);
  }

  /**
   * Fitted SRK binary interaction parameter for CO2-H2SO4 in the Van Laar acid
   * gamma-phi model.
   *
   * @param temperatureC temperature in degrees Celsius
   * @return fitted CO2-H2SO4 SRK binary interaction parameter
   */
  public static double carbonDioxideSulfuricAcidKij(double temperatureC) {
    return CO2_H2SO4_KIJ_AT_47_5_C + CO2_H2SO4_KIJ_SLOPE * (temperatureC - 47.5);
  }

  /**
   * Estimate acid solubility in high-pressure CO2 with the Van Laar liquid-source
   * fugacity and the
   * tuned SRK CO2 carrier-phase fugacity coefficient embedded in this model.
   *
   * @param acidName           acid name; supported values are
   *                           {@code "nitric acid"}, {@code "HNO3"},
   *                           {@code "sulfuric acid"}, {@code "sulphuric acid"},
   *                           and {@code "H2SO4"}
   * @param acidWeightPercent  acid concentration in the source liquid, in weight
   *                           percent
   * @param waterWeightPercent water concentration in the source liquid, in weight
   *                           percent
   * @param temperatureC       temperature in degrees Celsius
   * @param pressureBar        CO2 pressure in bara
   * @return acid mole fraction in the CO2 carrier phase, expressed as ppm mol
   * @throws IllegalArgumentException if the acid name, source composition,
   *                                  pressure, or temperature
   *                                  is outside the supported range
   */
  public static double acidSolubilityInCarbonDioxidePpm(String acidName, double acidWeightPercent,
      double waterWeightPercent, double temperatureC, double pressureBar) {
    return componentSolubilityInCarbonDioxidePpm(acidName, acidName, acidWeightPercent,
        waterWeightPercent, temperatureC, pressureBar);
  }

  /**
   * Estimate a water or acid component solubility in high-pressure CO2 for a
   * specified acid-water
   * source, using the Van Laar liquid-source fugacity and the tuned SRK CO2
   * carrier-phase fugacity
   * coefficient embedded in this model.
   *
   * @param componentName      component to report; supported values are
   *                           {@code "water"},
   *                           {@code "H2O"}, {@code "nitric acid"},
   *                           {@code "HNO3"}, {@code "sulfuric acid"},
   *                           {@code "sulphuric acid"}, and {@code "H2SO4"}
   * @param sourceAcidName     acid in the source liquid; supported acid aliases
   *                           are the same as in
   *                           {@link #acidSolubilityInCarbonDioxidePpm(String, double, double, double, double)}
   * @param acidWeightPercent  acid concentration in the source liquid, in weight
   *                           percent
   * @param waterWeightPercent water concentration in the source liquid, in weight
   *                           percent
   * @param temperatureC       temperature in degrees Celsius
   * @param pressureBar        CO2 pressure in bara
   * @return component mole fraction in the CO2 carrier phase, expressed as ppm
   *         mol
   * @throws IllegalArgumentException if the component name, source acid, source
   *                                  composition,
   *                                  pressure, or temperature is outside the
   *                                  supported range
   */
  public static double componentSolubilityInCarbonDioxidePpm(String componentName,
      String sourceAcidName, double acidWeightPercent, double waterWeightPercent,
      double temperatureC, double pressureBar) {
    if (acidWeightPercent <= 0.0 || waterWeightPercent < 0.0) {
      throw new IllegalArgumentException("Acid weight percent must be positive and water non-negative");
    }
    if (pressureBar <= 0.0) {
      throw new IllegalArgumentException("Pressure must be positive");
    }
    String normalizedComponentName = normalizeComponentName(componentName);
    String normalizedAcidName = normalizeAcidName(sourceAcidName);
    double liquidFugacityBar = componentSourceFugacityBar(normalizedComponentName,
        normalizedAcidName, acidWeightPercent, waterWeightPercent, temperatureC);
    double carrierFugacityCoefficient = carbonDioxideCarrierComponentFugacityCoefficient(
        normalizedComponentName, normalizedAcidName, temperatureC, pressureBar);
    return liquidFugacityBar / (carrierFugacityCoefficient * pressureBar) * 1.0e6;
  }

  /**
   * Calculate liquid-source fugacity from a Van Laar acid-water source system.
   *
   * @param componentName      component whose source fugacity should be
   *                           calculated
   * @param acidName           acid name
   * @param acidWeightPercent  acid concentration in weight percent
   * @param waterWeightPercent water concentration in weight percent
   * @param temperatureC       temperature in degrees Celsius
   * @return component source fugacity in bar
   */
  private static double componentSourceFugacityBar(String componentName, String acidName,
      double acidWeightPercent, double waterWeightPercent, double temperatureC) {
    double acidMoles = acidWeightPercent / acidMolarMass(acidName);
    double waterMoles = waterWeightPercent / WATER_MOLAR_MASS_G_PER_MOL;

    SystemVanLaarActivitySRK liquidSource = new SystemVanLaarActivitySRK(temperatureC + 273.15, 1.0);
    liquidSource.addComponent("water", waterMoles);
    liquidSource.addComponent("nitric acid",
        "nitric acid".equals(acidName) ? acidMoles : NEGLIGIBLE_ACID_MOLES);
    liquidSource.addComponent("sulfuric acid",
        "sulfuric acid".equals(acidName) ? acidMoles : NEGLIGIBLE_ACID_MOLES);
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
   * Calculate the tuned SRK fugacity coefficient of a trace source component in a
   * CO2 carrier phase.
   *
   * @param componentName component whose fugacity coefficient should be
   *                      calculated
   * @param acidName      acid in the source liquid
   * @param temperatureC  temperature in degrees Celsius
   * @param pressureBar   pressure in bara
   * @return component fugacity coefficient in the CO2-rich SRK phase
   */
  private static double carbonDioxideCarrierComponentFugacityCoefficient(String componentName,
      String acidName, double temperatureC, double pressureBar) {
    SystemSrkEos carrier = new SystemSrkEos(temperatureC + 273.15, pressureBar);
    carrier.addComponent("CO2", 0.999999);
    carrier.addComponent("water", TRACE_WATER_MOLES);
    carrier.addComponent("nitric acid",
        "nitric acid".equals(acidName) ? TRACE_ACID_MOLES : NEGLIGIBLE_ACID_MOLES);
    carrier.addComponent("sulfuric acid",
        "sulfuric acid".equals(acidName) ? TRACE_ACID_MOLES : NEGLIGIBLE_ACID_MOLES);
    carrier.createDatabase(true);
    applyNitricAcidPureComponentTuning(carrier);
    carrier.setMixingRule("classic");
    carrier.setBinaryInteractionParameter("CO2", acidName,
        acidCarbonDioxideKij(acidName, temperatureC));
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
   * @param acidName     normalized acid name
   * @param temperatureC temperature in degrees Celsius
   * @return acid-CO2 binary interaction parameter
   */
  private static double acidCarbonDioxideKij(String acidName, double temperatureC) {
    if ("nitric acid".equals(acidName)) {
      return carbonDioxideNitricAcidKij(temperatureC);
    }
    return carbonDioxideSulfuricAcidKij(temperatureC);
  }

  /**
   * CO2-water SRK binary interaction parameter used for trace-water carrier
   * calculations.
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
    if ("sulfuric acid".equals(normalized) || "sulphuric acid".equals(normalized)
        || "h2so4".equals(normalized)) {
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

    return clonedSystem;
  }
}
