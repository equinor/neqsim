package neqsim.process.equipment.reactor;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.PhysicalPropertyType;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Experimental isothermal plug-flow reactor for trace-impurity reactions in CO2 streams.
 *
 * <p>
 * The reactor integrates a concentration-based Arrhenius reaction network over the configured residence time. Every
 * reaction is applied through a balanced stoichiometric vector and each numerical extent is limited by the available
 * reactants. This preserves non-negative component inventories and elemental balances. The built-in kinetic parameters
 * are illustrative defaults; engineering use requires calibration against data representative of the fluid, wall
 * material, pressure, temperature and water content.
 * </p>
 *
 * <p>
 * Reaction R3b uses the same net stoichiometry as R1. H2S and NO2 enter its rate law as co-catalysts and are not
 * consumed by that reaction. Material selection affects only the R8 heterogeneous sulfur-formation rate.
 * </p>
 *
 * @author NeqSim Team
 * @version 1.0
 */
public class CO2ImpurityKineticReactor extends TwoPortEquipment {
  private static final long serialVersionUID = 1006L;
  private static final Logger logger = LogManager.getLogger(CO2ImpurityKineticReactor.class);
  private static final double GAS_CONSTANT = 8.314462618;
  private static final double MINIMUM_MOLES = 1.0e-30;
  private static final double REFERENCE_CONCENTRATION = 1.0;
  private static final double MAXIMUM_INTEGRATION_STEP_SECONDS = 60.0;
  private static final int MINIMUM_INTEGRATION_STEPS = 200;
  private static final int MAXIMUM_INTEGRATION_STEPS = 20000;

  private static final String[] SPECIES = { "H2S", "SO2", "NO2", "NO", "oxygen", "water", "H2SO4", "HNO3", "S8",
      "ammonia" };
  private static final String[] REACTION_IDS = { "R1", "R2", "R3A", "R3B", "R4", "R5", "R6", "R7" };

  /** Stoichiometry in the same order as {@link #SPECIES}. */
  private static final double[][] STOICHIOMETRY = { { 0.0, -1.0, 0.0, 0.0, -0.5, -1.0, 1.0, 0.0, 0.0, 0.0 },
      { -1.0, 1.0, -3.0, 3.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0 }, { 0.0, -1.0, -1.0, 1.0, 0.0, -1.0, 1.0, 0.0, 0.0, 0.0 },
      { 0.0, -1.0, 0.0, 0.0, -0.5, -1.0, 1.0, 0.0, 0.0, 0.0 }, { 0.0, 0.0, 2.0, -2.0, -1.0, 0.0, 0.0, 0.0, 0.0, 0.0 },
      { 0.0, 0.0, -3.0, 1.0, 0.0, -1.0, 0.0, 2.0, 0.0, 0.0 }, { -1.0, 1.0, 0.0, 0.0, -1.5, 1.0, 0.0, 0.0, 0.0, 0.0 },
      { -5.0, 5.0, 0.0, -6.0, 0.0, -4.0, 0.0, 0.0, 0.0, 6.0 },
      { -1.0, 0.0, 0.0, 0.0, -0.5, 1.0, 0.0, 0.0, 0.125, 0.0 } };

  private double reactorLength = 200000.0;
  private double fluidVelocity = 2.0;
  private double residenceTime = 100000.0;
  private String material = "carbon_steel";

  private double diameterCm = 6.50;
  private double volumeMl = 300.0;
  private double massFlowGPerHour = 50.0;
  private boolean useGeometryResidenceTime = false;

  private final double[] preExponentialFactors = { 1.0e4, 5.0e7, 1.4e6, 2.13e8, 1.0e5, 2.4e6, 2.0e3, 5.0e5 };
  private final double[] activationEnergies = { 45000.0, 28000.0, 26000.0, 15000.0, -4400.0, 28000.0, 65000.0,
      15000.0 };
  private double carbonSteelR8PreExponentialFactor = 1.5e4;
  private double carbonSteelR8ActivationEnergy = 42000.0;
  private double inertR8PreExponentialFactor = 2.0e3;
  private double inertR8ActivationEnergy = 65000.0;

  /**
   * Constructor for a reactor without an inlet stream.
   *
   * @param name reactor name
   */
  public CO2ImpurityKineticReactor(String name) {
    super(name);
  }

  /**
   * Constructor with an inlet stream.
   *
   * @param name reactor name
   * @param inlet inlet stream
   */
  public CO2ImpurityKineticReactor(String name, StreamInterface inlet) {
    super(name, inlet);
  }

  /**
   * Configure an Arrhenius parameter pair.
   *
   * <p>
   * Supported identifiers are R1, R2, R3A, R3B, R4-R7, R8CS and R8SS. R8 configures the parameter pair for the
   * currently selected material family.
   * </p>
   *
   * @param reactionId reaction identifier
   * @param preExponentialFactor pre-exponential factor, non-negative
   * @param activationEnergyKJPerMol activation energy [kJ/mol]
   */
  public void setReactionConstants(String reactionId, double preExponentialFactor, double activationEnergyKJPerMol) {
    validateFiniteNonNegative(preExponentialFactor, "pre-exponential factor");
    validateFinite(activationEnergyKJPerMol, "activation energy");
    if (reactionId == null) {
      throw new IllegalArgumentException("Reaction identifier cannot be null");
    }

    String normalizedId = reactionId.trim().toUpperCase(Locale.ROOT);
    for (int i = 0; i < REACTION_IDS.length; i++) {
      if (REACTION_IDS[i].equals(normalizedId)) {
        preExponentialFactors[i] = preExponentialFactor;
        activationEnergies[i] = activationEnergyKJPerMol * 1000.0;
        return;
      }
    }

    if ("R8".equals(normalizedId)) {
      normalizedId = isCatalyticMaterial() ? "R8CS" : "R8SS";
    }
    if ("R8CS".equals(normalizedId)) {
      carbonSteelR8PreExponentialFactor = preExponentialFactor;
      carbonSteelR8ActivationEnergy = activationEnergyKJPerMol * 1000.0;
      return;
    }
    if ("R8SS".equals(normalizedId)) {
      inertR8PreExponentialFactor = preExponentialFactor;
      inertR8ActivationEnergy = activationEnergyKJPerMol * 1000.0;
      return;
    }
    throw new IllegalArgumentException("Unsupported reaction identifier: " + reactionId);
  }

  /**
   * Configure cylindrical reactor geometry and mass flow.
   *
   * <p>
   * Calling this method makes the reactor calculate its residence time from the inlet-system density on each run.
   * </p>
   *
   * @param diameterCm internal diameter [cm]
   * @param volumeMl reactor volume [mL]
   * @param massFlowGPerHour mass flow [g/h]
   */
  public void setReactorGeometry(double diameterCm, double volumeMl, double massFlowGPerHour) {
    validateFinitePositive(diameterCm, "reactor diameter");
    validateFinitePositive(volumeMl, "reactor volume");
    validateFinitePositive(massFlowGPerHour, "mass flow");
    this.diameterCm = diameterCm;
    this.volumeMl = volumeMl;
    this.massFlowGPerHour = massFlowGPerHour;
    this.useGeometryResidenceTime = true;
  }

  /**
   * Generate a geometry and residence-time report at the inlet state.
   *
   * @return reactor report
   */
  public String generateReactorReport() {
    ensureInletAvailable();
    return generateReactorReport(getInletStream().getThermoSystem().getTemperature(),
        getInletStream().getThermoSystem().getPressure());
  }

  /**
   * Generate a geometry and residence-time report at a specified thermodynamic state.
   *
   * <p>
   * Density is calculated from a flashed clone of the inlet fluid; it is not selected from a pressure threshold or
   * another hard-coded phase assumption.
   * </p>
   *
   * @param temperatureKelvin temperature [K]
   * @param pressureBar pressure [bar absolute]
   * @return reactor report
   */
  public String generateReactorReport(double temperatureKelvin, double pressureBar) {
    double densityKgPerM3 = calculateDensity(temperatureKelvin, pressureBar);
    double areaCm2 = Math.PI * diameterCm * diameterCm / 4.0;
    double lengthCm = volumeMl / areaCm2;
    double residenceTimeSeconds = calculateGeometryResidenceTime(densityKgPerM3);

    StringBuilder report = new StringBuilder();
    report.append("Reactor geometry\n");
    report.append(String.format(Locale.ROOT, "Volume: %.3f mL%n", volumeMl));
    report.append(String.format(Locale.ROOT, "Internal diameter: %.3f cm%n", diameterCm));
    report.append(String.format(Locale.ROOT, "Calculated length: %.6f cm%n", lengthCm));
    report.append(String.format(Locale.ROOT, "Temperature: %.3f K%n", temperatureKelvin));
    report.append(String.format(Locale.ROOT, "Pressure: %.3f bar absolute%n", pressureBar));
    report.append(String.format(Locale.ROOT, "NeqSim fluid density: %.6f kg/m3%n", densityKgPerM3));
    report.append(String.format(Locale.ROOT, "Mass flow: %.6f g/h%n", massFlowGPerHour));
    report.append(String.format(Locale.ROOT, "Residence time: %.6f s%n", residenceTimeSeconds));
    return report.toString();
  }

  /**
   * Calculate geometry-derived residence time at a specified state.
   *
   * @param temperatureKelvin temperature [K]
   * @param pressureBar pressure [bar absolute]
   * @return residence time [s]
   */
  public double calculateGeometryResidenceTime(double temperatureKelvin, double pressureBar) {
    return calculateGeometryResidenceTime(calculateDensity(temperatureKelvin, pressureBar));
  }

  /**
   * Select wall material for heterogeneous R8 kinetics.
   *
   * @param materialName carbon_steel, magnetite, stainless_steel or inert
   */
  public void setMaterial(String materialName) {
    if (materialName == null) {
      throw new IllegalArgumentException("Material cannot be null");
    }
    String normalizedMaterial = materialName.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
    if (!Arrays.asList("carbon_steel", "magnetite", "stainless_steel", "inert").contains(normalizedMaterial)) {
      throw new IllegalArgumentException("Unsupported reactor material: " + materialName);
    }
    this.material = normalizedMaterial;
  }

  /** @return selected wall material. */
  public String getMaterial() {
    return material;
  }

  /**
   * Set reactor length and derive residence time from the current velocity.
   *
   * @param lengthInMeters length [m]
   */
  public void setReactorLength(double lengthInMeters) {
    validateFinitePositive(lengthInMeters, "reactor length");
    this.reactorLength = lengthInMeters;
    this.residenceTime = reactorLength / fluidVelocity;
    this.useGeometryResidenceTime = false;
  }

  /** @return reactor length [m]. */
  public double getReactorLength() {
    return reactorLength;
  }

  /**
   * Set fluid velocity and derive residence time from the current reactor length.
   *
   * @param velocityInMetersPerSec velocity [m/s]
   */
  public void setFluidVelocity(double velocityInMetersPerSec) {
    validateFinitePositive(velocityInMetersPerSec, "fluid velocity");
    this.fluidVelocity = velocityInMetersPerSec;
    this.residenceTime = reactorLength / fluidVelocity;
    this.useGeometryResidenceTime = false;
  }

  /** @return fluid velocity [m/s]. */
  public double getFluidVelocity() {
    return fluidVelocity;
  }

  /**
   * Set residence time directly.
   *
   * @param timeInSeconds residence time [s], non-negative
   */
  public void setResidenceTime(double timeInSeconds) {
    validateFiniteNonNegative(timeInSeconds, "residence time");
    this.residenceTime = timeInSeconds;
    this.useGeometryResidenceTime = false;
  }

  /** @return residence time [s]. */
  public double getResidenceTime() {
    return residenceTime;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    ensureInletAvailable();
    SystemInterface outletSystem = prepareSystem(getInletStream().getThermoSystem());
    outletSystem.init(3);
    outletSystem.initPhysicalProperties(PhysicalPropertyType.MASS_DENSITY);

    double temperatureKelvin = outletSystem.getTemperature();
    double densityKgPerM3 = outletSystem.getDensity("kg/m3");
    double molarDensityKmolPerM3 = densityKgPerM3 / (outletSystem.getMolarMass() * 1000.0);
    if (!Double.isFinite(molarDensityKmolPerM3) || molarDensityKmolPerM3 <= 0.0) {
      throw new IllegalStateException("Cannot integrate CO2 impurity kinetics with invalid molar density");
    }

    if (useGeometryResidenceTime) {
      residenceTime = calculateGeometryResidenceTime(densityKgPerM3);
    }

    double totalMoles = outletSystem.getNumberOfMoles();
    double parcelVolumeM3 = totalMoles / 1000.0 / molarDensityKmolPerM3;
    double[] concentrations = new double[SPECIES.length];
    for (int i = 0; i < SPECIES.length; i++) {
      concentrations[i] = getMoles(outletSystem, SPECIES[i]) / 1000.0 / parcelVolumeM3;
    }

    integrateConcentrations(concentrations, temperatureKelvin, residenceTime);
    for (int i = 0; i < SPECIES.length; i++) {
      setMoles(outletSystem, SPECIES[i], concentrations[i] * parcelVolumeM3 * 1000.0);
    }
    outletSystem.init(0);

    logger.info("Ran CO2 impurity reactor '{}' at {} K for {} s using material {}", getName(), temperatureKelvin,
        residenceTime, material);
    getOutletStream().setThermoSystem(outletSystem);
    getOutletStream().run(id);
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    run(UUID.randomUUID());
  }

  private SystemInterface prepareSystem(SystemInterface source) {
    SystemInterface system = source.clone();
    boolean componentAdded = false;
    for (String species : SPECIES) {
      if (!system.hasComponent(species)) {
        system.addComponent(species, MINIMUM_MOLES);
        componentAdded = true;
      }
    }
    if (componentAdded) {
      system.createDatabase(true);
    }
    system.init(0);
    return system;
  }

  private void integrateConcentrations(double[] concentrations, double temperatureKelvin,
      double integrationTimeSeconds) {
    if (integrationTimeSeconds <= 0.0) {
      return;
    }
    int integrationSteps = (int) Math.ceil(integrationTimeSeconds / MAXIMUM_INTEGRATION_STEP_SECONDS);
    integrationSteps = Math.max(integrationSteps, MINIMUM_INTEGRATION_STEPS);
    integrationSteps = Math.min(integrationSteps, MAXIMUM_INTEGRATION_STEPS);
    double timeStepSeconds = integrationTimeSeconds / integrationSteps;

    for (int step = 0; step < integrationSteps; step++) {
      double[] rates = calculateRates(concentrations, temperatureKelvin);
      for (int reaction = 0; reaction < STOICHIOMETRY.length; reaction++) {
        double proposedExtent = Math.max(0.0, rates[reaction] * timeStepSeconds);
        double boundedExtent = boundExtent(proposedExtent, concentrations, STOICHIOMETRY[reaction]);
        applyExtent(concentrations, STOICHIOMETRY[reaction], boundedExtent);
      }
    }
  }

  private double[] calculateRates(double[] concentration, double temperatureKelvin) {
    double[] rateConstants = new double[STOICHIOMETRY.length];
    for (int i = 0; i < REACTION_IDS.length; i++) {
      rateConstants[i] = calculateArrhenius(preExponentialFactors[i], activationEnergies[i], temperatureKelvin);
    }
    double r8PreExponential = isCatalyticMaterial() ? carbonSteelR8PreExponentialFactor : inertR8PreExponentialFactor;
    double r8ActivationEnergy = isCatalyticMaterial() ? carbonSteelR8ActivationEnergy : inertR8ActivationEnergy;
    rateConstants[8] = calculateArrhenius(r8PreExponential, r8ActivationEnergy, temperatureKelvin);

    double h2s = activity(concentration[0]);
    double so2 = activity(concentration[1]);
    double no2 = activity(concentration[2]);
    double no = activity(concentration[3]);
    double oxygen = activity(concentration[4]);
    double water = activity(concentration[5]);

    double[] rates = new double[STOICHIOMETRY.length];
    rates[0] = rateConstants[0] * REFERENCE_CONCENTRATION * so2 * Math.sqrt(oxygen) * water;
    rates[1] = rateConstants[1] * REFERENCE_CONCENTRATION * h2s * no2;
    rates[2] = rateConstants[2] * REFERENCE_CONCENTRATION * so2 * no2 * water;
    rates[3] = rateConstants[3] * REFERENCE_CONCENTRATION * so2 * Math.sqrt(oxygen) * Math.sqrt(h2s) * no2;
    rates[4] = rateConstants[4] * REFERENCE_CONCENTRATION * no * no * oxygen;
    rates[5] = rateConstants[5] * REFERENCE_CONCENTRATION * no2 * no2 * no2 * water;
    rates[6] = rateConstants[6] * REFERENCE_CONCENTRATION * h2s * Math.pow(oxygen, 1.5);
    rates[7] = rateConstants[7] * REFERENCE_CONCENTRATION * h2s * no * water;
    rates[8] = rateConstants[8] * REFERENCE_CONCENTRATION * h2s * Math.sqrt(oxygen);
    return rates;
  }

  private double activity(double concentrationKmolPerM3) {
    return Math.max(0.0, concentrationKmolPerM3) / REFERENCE_CONCENTRATION;
  }

  private double calculateArrhenius(double preExponentialFactor, double activationEnergy, double temperatureKelvin) {
    return preExponentialFactor * Math.exp(-activationEnergy / (GAS_CONSTANT * temperatureKelvin));
  }

  private double boundExtent(double proposedExtent, double[] concentration, double[] stoichiometry) {
    double boundedExtent = proposedExtent;
    for (int i = 0; i < stoichiometry.length; i++) {
      if (stoichiometry[i] < 0.0) {
        boundedExtent = Math.min(boundedExtent, concentration[i] / -stoichiometry[i]);
      }
    }
    return Math.max(0.0, boundedExtent);
  }

  private void applyExtent(double[] concentration, double[] stoichiometry, double extent) {
    for (int i = 0; i < concentration.length; i++) {
      concentration[i] = Math.max(0.0, concentration[i] + stoichiometry[i] * extent);
    }
  }

  private double calculateDensity(double temperatureKelvin, double pressureBar) {
    ensureInletAvailable();
    validateFinitePositive(temperatureKelvin, "temperature");
    validateFinitePositive(pressureBar, "pressure");
    SystemInterface reportSystem = getInletStream().getThermoSystem().clone();
    reportSystem.setTemperature(temperatureKelvin);
    reportSystem.setPressure(pressureBar);
    ThermodynamicOperations operations = new ThermodynamicOperations(reportSystem);
    operations.TPflash();
    reportSystem.initProperties();
    return reportSystem.getDensity("kg/m3");
  }

  private double calculateGeometryResidenceTime(double densityKgPerM3) {
    double inventoryG = volumeMl * 1.0e-6 * densityKgPerM3 * 1000.0;
    return inventoryG / massFlowGPerHour * 3600.0;
  }

  private boolean isCatalyticMaterial() {
    return "carbon_steel".equals(material) || "magnetite".equals(material);
  }

  private void ensureInletAvailable() {
    if (getInletStream() == null || getOutletStream() == null) {
      throw new IllegalStateException("CO2 impurity reactor requires connected inlet and outlet streams");
    }
  }

  private double getMoles(SystemInterface system, String component) {
    if (!system.hasComponent(component)) {
      return 0.0;
    }
    return Math.max(0.0, system.getComponent(component).getNumberOfmoles());
  }

  private void setMoles(SystemInterface system, String component, double targetMoles) {
    double boundedMoles = Math.max(targetMoles, MINIMUM_MOLES);
    system.addComponent(component, boundedMoles - getMoles(system, component));
  }

  private void validateFinitePositive(double value, String propertyName) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(propertyName + " must be finite and positive");
    }
  }

  private void validateFiniteNonNegative(double value, String propertyName) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(propertyName + " must be finite and non-negative");
    }
  }

  private void validateFinite(double value, String propertyName) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(propertyName + " must be finite");
    }
  }
}
