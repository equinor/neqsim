package neqsim.process.safety.dispersion;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.safety.BoundaryConditions;
import neqsim.process.safety.release.SourceTermResult;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * High-level gas dispersion screening analyzer for NeqSim release scenarios.
 *
 * <p>
 * The analyzer connects NeqSim thermodynamics and process release rates to simple consequence
 * screening models. It can be built from a process stream, a dynamic {@link SourceTermResult}, or
 * an explicit release rate. The implementation selects a passive Gaussian plume or a dense-gas
 * screening model from the expanded gas density at ambient conditions.
 *
 * <p>
 * This class is intended for early safety screening, QRA input generation and agent workflows. It
 * does not replace detailed tools such as PHAST, FLACS, KFX or OpenFOAM for final layout decisions.
 *
 * @author ESOL
 * @version 1.0
 */
public class GasDispersionAnalyzer implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double GAS_CONSTANT = 8.314462618;
  private static final double AIR_GAS_CONSTANT = 287.05;
  private static final double DENSE_GAS_RATIO = 1.2;
  private static final double DEFAULT_RELEASE_HEIGHT_M = 0.0;
  private static final Map<String, Double> LOWER_FLAMMABLE_LIMITS = createLflLibrary();

  /** Dispersion model selection mode. */
  public enum ModelSelection {
    /** Choose Gaussian or dense-gas screening from expanded gas density. */
    AUTO,
    /** Force passive Gaussian plume screening. */
    GAUSSIAN_PLUME,
    /** Force dense-gas screening. */
    HEAVY_GAS
  }

  private final String scenarioName;
  private final SystemInterface fluid;
  private final double massReleaseRateKgPerS;
  private final BoundaryConditions boundaryConditions;
  private final double releaseHeightM;
  private final ModelSelection modelSelection;
  private final double manualLowerFlammableLimitVolumeFraction;
  private final String toxicComponentName;
  private final double toxicThresholdPpm;

  /**
   * Creates a gas dispersion analyzer from a builder.
   *
   * @param builder populated builder
   */
  private GasDispersionAnalyzer(Builder builder) {
    this.scenarioName = builder.scenarioName;
    this.fluid = builder.fluid == null ? null : builder.fluid.clone();
    this.massReleaseRateKgPerS = resolveMassReleaseRate(builder);
    this.boundaryConditions = builder.boundaryConditions;
    this.releaseHeightM = builder.releaseHeightM;
    this.modelSelection = builder.modelSelection;
    this.manualLowerFlammableLimitVolumeFraction = builder.lowerFlammableLimitVolumeFraction;
    this.toxicComponentName = builder.toxicComponentName;
    this.toxicThresholdPpm = builder.toxicThresholdPpm;
  }

  /**
   * Creates a new builder.
   *
   * @return new gas dispersion analyzer builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Analyze a process stream as a continuous release using the stream flow rate.
   *
   * @param scenarioName scenario identifier
   * @param stream process stream providing fluid and flow rate
   * @param boundaryConditions ambient boundary conditions
   * @return gas dispersion screening result
   */
  public static GasDispersionResult analyzeStream(String scenarioName, StreamInterface stream,
      BoundaryConditions boundaryConditions) {
    if (stream == null) {
      throw new IllegalArgumentException("stream cannot be null");
    }
    return builder().scenarioName(scenarioName).fluid(stream.getThermoSystem())
        .massReleaseRate(stream.getFlowRate("kg/sec")).boundaryConditions(boundaryConditions)
        .build().analyze();
  }

  /**
   * Gets the default lower flammable limit library.
   *
   * @return unmodifiable map from normalized component name to LFL volume fraction
   */
  public static Map<String, Double> getLowerFlammableLimits() {
    return LOWER_FLAMMABLE_LIMITS;
  }

  /**
   * Calculates the mixture lower flammable limit by Le Chatelier mixing.
   *
   * @param fluid thermodynamic fluid whose gas phase composition is evaluated
   * @return LFL as volume fraction in air, or NaN if no known flammable component exists
   */
  public static double lowerFlammableLimit(SystemInterface fluid) {
    if (fluid == null) {
      throw new IllegalArgumentException("fluid cannot be null");
    }
    SystemInterface system = prepareFluid(fluid);
    return calculateFuelData(system, Double.NaN).lowerFlammableLimitVolumeFraction;
  }

  /**
   * Runs the gas dispersion screening calculation.
   *
   * @return gas dispersion result
   */
  public GasDispersionResult analyze() {
    if (fluid == null) {
      throw new IllegalStateException("A NeqSim fluid must be specified");
    }
    if (!Double.isFinite(massReleaseRateKgPerS) || massReleaseRateKgPerS <= 0.0) {
      throw new IllegalStateException("Mass release rate must be positive");
    }

    SystemInterface ambientFluid = prepareExpandedGas(fluid, boundaryConditions);
    double sourceDensity = gasPhase(ambientFluid).getDensity("kg/m3");
    if (Double.isNaN(sourceDensity) || sourceDensity <= 0.0) {
      sourceDensity = estimateIdealGasDensity(ambientFluid);
    }
    double airDensity = airDensity(boundaryConditions);
    FuelData fuelData = calculateFuelData(ambientFluid, manualLowerFlammableLimitVolumeFraction);
    ModelSelection selectedModel = selectModel(sourceDensity, airDensity);

    FlammableEndpoint flammableEndpoint =
        calculateFlammableEndpoint(selectedModel, sourceDensity, airDensity, fuelData);
    ToxicEndpoint toxicEndpoint =
        calculateToxicEndpoint(selectedModel, sourceDensity, airDensity, ambientFluid);

    String basis = selectedModel == ModelSelection.HEAVY_GAS
        ? "Dense-gas screening using Britter-McQuaid continuous release scaling"
        : "Passive Gaussian plume screening using Briggs Pasquill-Gifford coefficients";

    return new GasDispersionResult(scenarioName, selectedModel.name(), massReleaseRateKgPerS,
        massReleaseRateKgPerS * fuelData.fuelMassFraction, sourceDensity, airDensity,
        fuelData.fuelMoleFraction, fuelData.fuelMassFraction,
        fuelData.lowerFlammableLimitVolumeFraction, flammableEndpoint.distanceToHalfLflM,
        flammableEndpoint.distanceToLflM, flammableEndpoint.cloudVolumeM3,
        toxicEndpoint.componentName, toxicEndpoint.thresholdPpm, toxicEndpoint.distanceM,
        boundaryConditions.getWindSpeed(), boundaryConditions.getPasquillStabilityClass(), basis);
  }

  /**
   * Resolve release rate from explicit builder input or source term peak rate.
   *
   * @param builder builder containing release rate inputs
   * @return release rate in kg/s
   */
  private static double resolveMassReleaseRate(Builder builder) {
    if (builder.massReleaseRateKgPerS > 0.0) {
      return builder.massReleaseRateKgPerS;
    }
    if (builder.sourceTerm != null) {
      return builder.sourceTerm.getPeakMassFlowRate();
    }
    return Double.NaN;
  }

  /**
   * Calculate flammable cloud endpoints for the selected model.
   *
   * @param selectedModel selected model
   * @param sourceDensity source gas density in kg/m3
   * @param airDensity air density in kg/m3
   * @param fuelData fuel mixture data
   * @return flammable endpoint data
   */
  private FlammableEndpoint calculateFlammableEndpoint(ModelSelection selectedModel,
      double sourceDensity, double airDensity, FuelData fuelData) {
    if (!fuelData.hasFuel()) {
      return new FlammableEndpoint(Double.NaN, Double.NaN, 0.0);
    }
    if (selectedModel == ModelSelection.HEAVY_GAS) {
      HeavyGasDispersion denseGas = createHeavyGasModel(sourceDensity, airDensity);
      double lflRatio = fuelData.lowerFlammableLimitVolumeFraction / fuelData.fuelMoleFraction;
      double halfLflRatio = 0.5 * lflRatio;
      double distanceLfl = distanceToDenseGasRatio(denseGas, lflRatio);
      double distanceHalfLfl = distanceToDenseGasRatio(denseGas, halfLflRatio);
      double cloudVolume = estimateHeavyGasCloudVolume(distanceLfl);
      return new FlammableEndpoint(distanceHalfLfl, distanceLfl, cloudVolume);
    }

    double fuelRate = massReleaseRateKgPerS * fuelData.fuelMassFraction;
    GaussianPlume plume = createGaussianPlume(fuelRate);
    double lflKgPerM3 = volumeFractionToKgPerM3(fuelData.lowerFlammableLimitVolumeFraction,
        fuelData.fuelMolarMassKgPerMol);
    double distanceLfl = plume.distanceToConcentration(lflKgPerM3);
    double distanceHalfLfl = plume.distanceToConcentration(0.5 * lflKgPerM3);
    double cloudVolume = estimateGaussianCloudVolume(plume, lflKgPerM3, distanceLfl);
    return new FlammableEndpoint(distanceHalfLfl, distanceLfl, cloudVolume);
  }

  /**
   * Calculate a toxic endpoint for the selected model.
   *
   * @param selectedModel selected model
   * @param sourceDensity source gas density in kg/m3
   * @param airDensity air density in kg/m3
   * @param ambientFluid expanded fluid at ambient conditions
   * @return toxic endpoint data
   */
  private ToxicEndpoint calculateToxicEndpoint(ModelSelection selectedModel, double sourceDensity,
      double airDensity, SystemInterface ambientFluid) {
    if (toxicComponentName == null || toxicComponentName.trim().isEmpty()
        || toxicThresholdPpm <= 0.0) {
      return new ToxicEndpoint("", Double.NaN, Double.NaN);
    }
    ComponentData componentData = componentData(ambientFluid, toxicComponentName);
    if (!componentData.exists()) {
      return new ToxicEndpoint(toxicComponentName, toxicThresholdPpm, Double.NaN);
    }

    if (selectedModel == ModelSelection.HEAVY_GAS) {
      HeavyGasDispersion denseGas = createHeavyGasModel(sourceDensity, airDensity);
      double thresholdVolumeFraction = toxicThresholdPpm / 1.0e6;
      double ratio = thresholdVolumeFraction / componentData.moleFraction;
      return new ToxicEndpoint(toxicComponentName, toxicThresholdPpm,
          distanceToDenseGasRatio(denseGas, ratio));
    }

    double toxicMassRate = massReleaseRateKgPerS * componentData.massFraction;
    GaussianPlume plume = createGaussianPlume(toxicMassRate);
    double thresholdKgPerM3 = ToxicLibrary.ppmToKgPerM3(toxicThresholdPpm,
        componentData.molarMassKgPerMol, boundaryConditions.getAmbientTemperature(),
        boundaryConditions.getAtmosphericPressureBar());
    return new ToxicEndpoint(toxicComponentName, toxicThresholdPpm,
        plume.distanceToConcentration(thresholdKgPerM3));
  }

  /**
   * Select the model to use from explicit configuration or gas density ratio.
   *
   * @param sourceDensity source gas density in kg/m3
   * @param airDensity air density in kg/m3
   * @return selected model
   */
  private ModelSelection selectModel(double sourceDensity, double airDensity) {
    if (modelSelection != ModelSelection.AUTO) {
      return modelSelection;
    }
    return sourceDensity / airDensity > DENSE_GAS_RATIO ? ModelSelection.HEAVY_GAS
        : ModelSelection.GAUSSIAN_PLUME;
  }

  /**
   * Create a Gaussian plume model from a mass emission rate.
   *
   * @param emissionRateKgPerS emission rate in kg/s
   * @return Gaussian plume model
   */
  private GaussianPlume createGaussianPlume(double emissionRateKgPerS) {
    return new GaussianPlume(Math.max(0.0, emissionRateKgPerS), releaseHeightM,
        boundaryConditions.getWindSpeed(), stability(boundaryConditions),
        terrain(boundaryConditions));
  }

  /**
   * Create a dense gas screening model.
   *
   * @param sourceDensity source gas density in kg/m3
   * @param airDensity air density in kg/m3
   * @return heavy gas dispersion model
   */
  private HeavyGasDispersion createHeavyGasModel(double sourceDensity, double airDensity) {
    double volumetricRate = massReleaseRateKgPerS / Math.max(sourceDensity, 1.0e-9);
    return new HeavyGasDispersion(volumetricRate, sourceDensity, airDensity,
        boundaryConditions.getWindSpeed());
  }

  /**
   * Calculate dense-gas distance to concentration ratio with input guards.
   *
   * @param denseGas dense-gas model
   * @param ratio target concentration ratio
   * @return distance in m, or NaN if the target is not meaningful
   */
  private static double distanceToDenseGasRatio(HeavyGasDispersion denseGas, double ratio) {
    if (Double.isNaN(ratio) || ratio <= 0.0 || ratio >= 1.0) {
      return Double.NaN;
    }
    return denseGas.distanceToConcentrationRatio(ratio);
  }

  /**
   * Estimate a Gaussian cloud volume above a concentration threshold.
   *
   * @param plume Gaussian plume model
   * @param thresholdKgPerM3 threshold concentration in kg/m3
   * @param distanceM maximum centerline distance to the threshold in m
   * @return estimated cloud volume in m3
   */
  private static double estimateGaussianCloudVolume(GaussianPlume plume, double thresholdKgPerM3,
      double distanceM) {
    if (!Double.isFinite(distanceM) || distanceM <= 1.0 || thresholdKgPerM3 <= 0.0) {
      return 0.0;
    }
    int steps = 200;
    double start = 1.0;
    double dx = (distanceM - start) / steps;
    double volume = 0.0;
    for (int i = 0; i < steps; i++) {
      double x = start + (i + 0.5) * dx;
      double centerline = plume.centerlineGroundConcentration(x);
      if (centerline <= thresholdKgPerM3) {
        continue;
      }
      double ellipseFactor = Math.sqrt(2.0 * Math.log(centerline / thresholdKgPerM3));
      double semiWidth = plume.sigmaY(x) * ellipseFactor;
      double semiHeight = plume.sigmaZ(x) * ellipseFactor;
      double halfEllipseArea = 0.5 * Math.PI * semiWidth * semiHeight;
      volume += halfEllipseArea * dx;
    }
    return volume;
  }

  /**
   * Estimate a shallow dense-gas flammable cloud volume.
   *
   * @param distanceM endpoint distance in m
   * @return estimated dense-gas cloud volume in m3
   */
  private static double estimateHeavyGasCloudVolume(double distanceM) {
    if (!Double.isFinite(distanceM) || distanceM <= 0.0) {
      return 0.0;
    }
    double width = Math.max(1.0, 0.25 * distanceM);
    double height = Math.max(1.0, Math.min(5.0, 0.05 * distanceM));
    return 0.5 * distanceM * width * height;
  }

  /**
   * Convert a gas volume fraction to mass concentration at ambient conditions.
   *
   * @param volumeFraction volume fraction in air
   * @param molarMassKgPerMol molar mass in kg/mol
   * @return concentration in kg/m3
   */
  private double volumeFractionToKgPerM3(double volumeFraction, double molarMassKgPerMol) {
    return volumeFraction * molarMassKgPerMol * boundaryConditions.getAtmosphericPressure()
        / (GAS_CONSTANT * boundaryConditions.getAmbientTemperature());
  }

  /**
   * Prepare a fluid at its current pressure and temperature.
   *
   * @param input input fluid
   * @return flashed and property-initialized fluid clone
   */
  private static SystemInterface prepareFluid(SystemInterface input) {
    SystemInterface system = input.clone();
    flashAndInit(system);
    return system;
  }

  /**
   * Prepare expanded release gas at ambient pressure and temperature.
   *
   * @param input input process fluid
   * @param boundaryConditions ambient boundary conditions
   * @return expanded fluid clone
   */
  private static SystemInterface prepareExpandedGas(SystemInterface input,
      BoundaryConditions boundaryConditions) {
    SystemInterface system = input.clone();
    system.setTemperature(boundaryConditions.getAmbientTemperature());
    system.setPressure(boundaryConditions.getAtmosphericPressureBar());
    flashAndInit(system);
    return system;
  }

  /**
   * Run TP flash and initialize thermodynamic and physical properties.
   *
   * @param system system to initialize
   */
  private static void flashAndInit(SystemInterface system) {
    ThermodynamicOperations operations = new ThermodynamicOperations(system);
    try {
      operations.TPflash();
    } catch (Exception ex) {
      system.init(3);
    }
    system.initProperties();
  }

  /**
   * Gets the gas phase or falls back to phase zero.
   *
   * @param system thermodynamic system
   * @return gas phase if present, otherwise phase zero
   */
  private static PhaseInterface gasPhase(SystemInterface system) {
    if (system.hasPhaseType("gas")) {
      return system.getPhase(system.getPhaseIndex("gas"));
    }
    return system.getPhase(0);
  }

  /**
   * Estimate gas density from ideal gas relation when physical properties are unavailable.
   *
   * @param system thermodynamic system
   * @return density in kg/m3
   */
  private static double estimateIdealGasDensity(SystemInterface system) {
    return system.getPressure() * 1.0e5 * system.getMolarMass()
        / (GAS_CONSTANT * system.getTemperature());
  }

  /**
   * Calculate ambient air density.
   *
   * @param conditions boundary conditions
   * @return air density in kg/m3
   */
  private static double airDensity(BoundaryConditions conditions) {
    return conditions.getAtmosphericPressure()
        / (AIR_GAS_CONSTANT * conditions.getAmbientTemperature());
  }

  /**
   * Convert NeqSim boundary stability class to Gaussian plume enum.
   *
   * @param conditions boundary conditions
   * @return Gaussian plume stability enum
   */
  private static GaussianPlume.Stability stability(BoundaryConditions conditions) {
    switch (conditions.getPasquillStabilityClass()) {
      case 'A':
        return GaussianPlume.Stability.A;
      case 'B':
        return GaussianPlume.Stability.B;
      case 'C':
        return GaussianPlume.Stability.C;
      case 'E':
        return GaussianPlume.Stability.E;
      case 'F':
        return GaussianPlume.Stability.F;
      case 'D':
      default:
        return GaussianPlume.Stability.D;
    }
  }

  /**
   * Convert boundary roughness to Gaussian plume terrain category.
   *
   * @param conditions boundary conditions
   * @return terrain category
   */
  private static GaussianPlume.Terrain terrain(BoundaryConditions conditions) {
    if (conditions.isOffshore() || conditions.getSurfaceRoughness() < 0.5) {
      return GaussianPlume.Terrain.RURAL;
    }
    return GaussianPlume.Terrain.URBAN;
  }

  /**
   * Calculate fuel mixture data from a gas phase.
   *
   * @param system thermodynamic system at release composition
   * @param manualLfl manual LFL override, or NaN to use component library
   * @return fuel data
   */
  private static FuelData calculateFuelData(SystemInterface system, double manualLfl) {
    PhaseInterface phase = gasPhase(system);
    if (Double.isFinite(manualLfl) && manualLfl > 0.0) {
      return new FuelData(1.0, 1.0, phase.getMolarMass(), manualLfl);
    }

    double totalMassBasis = 0.0;
    double fuelMoleFraction = 0.0;
    double fuelMassBasis = 0.0;
    double lflReciprocalBasis = 0.0;
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      ComponentInterface component = phase.getComponent(i);
      double moleFraction = Math.max(0.0, component.getx());
      double molarMass = component.getMolarMass();
      totalMassBasis += moleFraction * molarMass;
      Double lfl = LOWER_FLAMMABLE_LIMITS.get(normalizeComponentName(component.getComponentName()));
      if (lfl != null && moleFraction > 0.0) {
        fuelMoleFraction += moleFraction;
        fuelMassBasis += moleFraction * molarMass;
        lflReciprocalBasis += moleFraction / lfl.doubleValue();
      }
    }

    if (fuelMoleFraction <= 0.0 || lflReciprocalBasis <= 0.0 || totalMassBasis <= 0.0) {
      return FuelData.none();
    }
    double lflMix = fuelMoleFraction / lflReciprocalBasis;
    double fuelMolarMass = fuelMassBasis / fuelMoleFraction;
    double fuelMassFraction = fuelMassBasis / totalMassBasis;
    return new FuelData(fuelMoleFraction, fuelMassFraction, fuelMolarMass, lflMix);
  }

  /**
   * Gets component mole, mass and molar-mass data.
   *
   * @param system thermodynamic system
   * @param componentName component name to find
   * @return component data
   */
  private static ComponentData componentData(SystemInterface system, String componentName) {
    PhaseInterface phase = gasPhase(system);
    String normalizedTarget = normalizeComponentName(componentName);
    double totalMassBasis = 0.0;
    double componentMassBasis = 0.0;
    double componentMoleFraction = 0.0;
    double componentMolarMass = Double.NaN;

    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      ComponentInterface component = phase.getComponent(i);
      double moleFraction = Math.max(0.0, component.getx());
      double molarMass = component.getMolarMass();
      totalMassBasis += moleFraction * molarMass;
      if (normalizeComponentName(component.getComponentName()).equals(normalizedTarget)) {
        componentMoleFraction = moleFraction;
        componentMassBasis = moleFraction * molarMass;
        componentMolarMass = molarMass;
      }
    }

    if (componentMoleFraction <= 0.0 || totalMassBasis <= 0.0) {
      return ComponentData.none();
    }
    return new ComponentData(componentMoleFraction, componentMassBasis / totalMassBasis,
        componentMolarMass);
  }

  /**
   * Normalize component names for lookups.
   *
   * @param componentName raw component name
   * @return normalized component key
   */
  private static String normalizeComponentName(String componentName) {
    if (componentName == null) {
      return "";
    }
    return componentName.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ",
        "");
  }

  /**
   * Create lower flammable limit lookup table.
   *
   * @return unmodifiable LFL map
   */
  private static Map<String, Double> createLflLibrary() {
    Map<String, Double> lfl = new HashMap<>();
    putLfl(lfl, "methane", 0.044);
    putLfl(lfl, "ethane", 0.030);
    putLfl(lfl, "propane", 0.021);
    putLfl(lfl, "n-butane", 0.018);
    putLfl(lfl, "i-butane", 0.018);
    putLfl(lfl, "n-pentane", 0.014);
    putLfl(lfl, "i-pentane", 0.014);
    putLfl(lfl, "n-hexane", 0.012);
    putLfl(lfl, "hydrogen", 0.040);
    putLfl(lfl, "H2S", 0.040);
    putLfl(lfl, "CO", 0.125);
    putLfl(lfl, "methanol", 0.060);
    putLfl(lfl, "benzene", 0.012);
    return Collections.unmodifiableMap(lfl);
  }

  /**
   * Put an LFL value into the lookup table using normalized key.
   *
   * @param lfl target lookup table
   * @param componentName component name
   * @param volumeFraction LFL volume fraction in air
   */
  private static void putLfl(Map<String, Double> lfl, String componentName, double volumeFraction) {
    lfl.put(normalizeComponentName(componentName), volumeFraction);
  }

  /** Builder for {@link GasDispersionAnalyzer}. */
  public static final class Builder {
    private String scenarioName = "Gas dispersion scenario";
    private SystemInterface fluid;
    private double massReleaseRateKgPerS = Double.NaN;
    private SourceTermResult sourceTerm;
    private BoundaryConditions boundaryConditions = BoundaryConditions.defaultConditions();
    private double releaseHeightM = DEFAULT_RELEASE_HEIGHT_M;
    private ModelSelection modelSelection = ModelSelection.AUTO;
    private double lowerFlammableLimitVolumeFraction = Double.NaN;
    private String toxicComponentName;
    private double toxicThresholdPpm = Double.NaN;

    /**
     * Set the scenario name.
     *
     * @param scenarioName scenario name
     * @return this builder
     */
    public Builder scenarioName(String scenarioName) {
      this.scenarioName = scenarioName == null ? "Gas dispersion scenario" : scenarioName;
      return this;
    }

    /**
     * Set the NeqSim fluid.
     *
     * @param fluid thermodynamic fluid from a process or release scenario
     * @return this builder
     */
    public Builder fluid(SystemInterface fluid) {
      this.fluid = fluid;
      return this;
    }

    /**
     * Set a continuous release mass rate.
     *
     * @param massReleaseRateKgPerS mass release rate in kg/s
     * @return this builder
     */
    public Builder massReleaseRate(double massReleaseRateKgPerS) {
      this.massReleaseRateKgPerS = massReleaseRateKgPerS;
      return this;
    }

    /**
     * Set a dynamic source term. The peak mass rate is used for screening.
     *
     * @param sourceTerm release source-term result
     * @return this builder
     */
    public Builder sourceTerm(SourceTermResult sourceTerm) {
      this.sourceTerm = sourceTerm;
      return this;
    }

    /**
     * Set ambient boundary conditions.
     *
     * @param boundaryConditions weather and ambient conditions
     * @return this builder
     */
    public Builder boundaryConditions(BoundaryConditions boundaryConditions) {
      if (boundaryConditions != null) {
        this.boundaryConditions = boundaryConditions;
      }
      return this;
    }

    /**
     * Set effective release height.
     *
     * @param releaseHeightM release height in m
     * @return this builder
     */
    public Builder releaseHeight(double releaseHeightM) {
      this.releaseHeightM = Math.max(0.0, releaseHeightM);
      return this;
    }

    /**
     * Set model selection mode.
     *
     * @param modelSelection model selection mode
     * @return this builder
     */
    public Builder modelSelection(ModelSelection modelSelection) {
      if (modelSelection != null) {
        this.modelSelection = modelSelection;
      }
      return this;
    }

    /**
     * Override the lower flammable limit used for the released gas.
     *
     * @param lowerFlammableLimitVolumeFraction LFL volume fraction in air
     * @return this builder
     */
    public Builder lowerFlammableLimit(double lowerFlammableLimitVolumeFraction) {
      this.lowerFlammableLimitVolumeFraction = lowerFlammableLimitVolumeFraction;
      return this;
    }

    /**
     * Set a toxic endpoint to evaluate.
     *
     * @param componentName component name in the NeqSim fluid
     * @param thresholdPpm endpoint threshold in ppm
     * @return this builder
     */
    public Builder toxicEndpoint(String componentName, double thresholdPpm) {
      this.toxicComponentName = componentName;
      this.toxicThresholdPpm = thresholdPpm;
      return this;
    }

    /**
     * Build the analyzer.
     *
     * @return configured gas dispersion analyzer
     */
    public GasDispersionAnalyzer build() {
      return new GasDispersionAnalyzer(this);
    }
  }

  /** Fuel mixture data for flammable endpoint calculations. */
  private static final class FuelData implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double fuelMoleFraction;
    private final double fuelMassFraction;
    private final double fuelMolarMassKgPerMol;
    private final double lowerFlammableLimitVolumeFraction;

    /**
     * Creates fuel data.
     *
     * @param fuelMoleFraction fuel mole fraction
     * @param fuelMassFraction fuel mass fraction
     * @param fuelMolarMassKgPerMol fuel molar mass in kg/mol
     * @param lowerFlammableLimitVolumeFraction LFL volume fraction in air
     */
    private FuelData(double fuelMoleFraction, double fuelMassFraction, double fuelMolarMassKgPerMol,
        double lowerFlammableLimitVolumeFraction) {
      this.fuelMoleFraction = fuelMoleFraction;
      this.fuelMassFraction = fuelMassFraction;
      this.fuelMolarMassKgPerMol = fuelMolarMassKgPerMol;
      this.lowerFlammableLimitVolumeFraction = lowerFlammableLimitVolumeFraction;
    }

    /**
     * Create empty fuel data.
     *
     * @return empty fuel data
     */
    private static FuelData none() {
      return new FuelData(0.0, 0.0, Double.NaN, Double.NaN);
    }

    /**
     * Checks if known fuel is present.
     *
     * @return true if fuel fraction and LFL are available
     */
    private boolean hasFuel() {
      return fuelMoleFraction > 0.0 && fuelMassFraction > 0.0
          && Double.isFinite(lowerFlammableLimitVolumeFraction);
    }
  }

  /** Component data used for toxic endpoint calculations. */
  private static final class ComponentData implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double moleFraction;
    private final double massFraction;
    private final double molarMassKgPerMol;

    /**
     * Creates component data.
     *
     * @param moleFraction component mole fraction
     * @param massFraction component mass fraction
     * @param molarMassKgPerMol component molar mass in kg/mol
     */
    private ComponentData(double moleFraction, double massFraction, double molarMassKgPerMol) {
      this.moleFraction = moleFraction;
      this.massFraction = massFraction;
      this.molarMassKgPerMol = molarMassKgPerMol;
    }

    /**
     * Create empty component data.
     *
     * @return empty component data
     */
    private static ComponentData none() {
      return new ComponentData(0.0, 0.0, Double.NaN);
    }

    /**
     * Checks whether the component exists in the gas phase.
     *
     * @return true if the component exists
     */
    private boolean exists() {
      return moleFraction > 0.0 && massFraction > 0.0 && Double.isFinite(molarMassKgPerMol);
    }
  }

  /** Flammable endpoint holder. */
  private static final class FlammableEndpoint implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double distanceToHalfLflM;
    private final double distanceToLflM;
    private final double cloudVolumeM3;

    /**
     * Creates a flammable endpoint holder.
     *
     * @param distanceToHalfLflM distance to 50 percent LFL in m
     * @param distanceToLflM distance to 100 percent LFL in m
     * @param cloudVolumeM3 estimated cloud volume in m3
     */
    private FlammableEndpoint(double distanceToHalfLflM, double distanceToLflM,
        double cloudVolumeM3) {
      this.distanceToHalfLflM = distanceToHalfLflM;
      this.distanceToLflM = distanceToLflM;
      this.cloudVolumeM3 = cloudVolumeM3;
    }
  }

  /** Toxic endpoint holder. */
  private static final class ToxicEndpoint implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String componentName;
    private final double thresholdPpm;
    private final double distanceM;

    /**
     * Creates a toxic endpoint holder.
     *
     * @param componentName component name
     * @param thresholdPpm threshold in ppm
     * @param distanceM distance in m
     */
    private ToxicEndpoint(String componentName, double thresholdPpm, double distanceM) {
      this.componentName = componentName;
      this.thresholdPpm = thresholdPpm;
      this.distanceM = distanceM;
    }
  }
}
