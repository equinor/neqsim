package neqsim.process.safety.rupture;

import java.io.Serializable;
import neqsim.process.safety.inventory.TrappedInventoryCalculator.InventoryResult;
import neqsim.process.util.fire.TransientWallHeatTransfer;
import neqsim.process.util.fire.VesselRuptureCalculator;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.unit.PressureUnit;

/**
 * Transient trapped-liquid fire rupture screening study for blocked-in piping segments.
 *
 * <p>
 * The study combines an evidence-linked {@link InventoryResult}, fire heat exposure, transient wall
 * heating, thermal expansion pressure rise, temperature-reduced material strength, and optional
 * flange/relief checks. It is a structured screening model intended to identify segments that need
 * pressure relief, passive fire protection, operational safeguards, or detailed specialist analysis.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class TrappedLiquidFireRuptureStudy implements Serializable {
  private static final long serialVersionUID = 1L;

  private static final double PA_PER_BARA = 1.0e5;
  private static final double DEFAULT_STEEL_DENSITY = 7850.0;
  private static final double DEFAULT_STEEL_CP = 500.0;
  private static final double DEFAULT_STEEL_K = 45.0;
  private static final double DEFAULT_LIQUID_CP = 2200.0;
  private static final double DEFAULT_LIQUID_EXPANSION = 7.0e-4;
  private static final double DEFAULT_LIQUID_BULK_MODULUS = 1.2e9;
  private static final double DEFAULT_INNER_HTC = 500.0;
  private static final double DEFAULT_OUTER_HTC = 35.0;
  private static final double DEFAULT_TENSILE_FACTOR = 0.75;

  private final Builder builder;

  /**
   * Creates a trapped-liquid fire rupture study.
   *
   * @param builder populated builder
   */
  private TrappedLiquidFireRuptureStudy(Builder builder) {
    this.builder = builder;
  }

  /**
   * Creates a builder.
   *
   * @return new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Runs the transient rupture screening calculation.
   *
   * @return rupture study result
   */
  public TrappedLiquidFireRuptureResult run() {
    builder.validate();
    double initialTemperatureK = builder.inventory.getTemperatureK();
    double initialPressurePa = builder.inventory.getPressureBara() * PA_PER_BARA;
    double liquidMassKg = Math.max(builder.inventory.getTotalLiquidMassKg(),
        builder.inventory.getTotalMassKg());
    double liquidTemperatureK = initialTemperatureK;
    double pressurePa = initialPressurePa;
    double exposedAreaM2 = builder.exposedAreaM2();
    double pipeInnerRadiusM = builder.pipeInternalDiameterM / 2.0;
    double flangeRatingAmbientPa = flangeClassRatingPa(builder.flangeClass);

    TransientWallHeatTransfer wall = new TransientWallHeatTransfer(builder.wallThicknessM,
        builder.wallThermalConductivityWPerMK, builder.wallDensityKgPerM3,
        builder.wallHeatCapacityJPerKgK, initialTemperatureK, builder.wallNodes);
    TrappedLiquidFireRuptureResult.Builder result = TrappedLiquidFireRuptureResult.builder(
        builder.segmentId).geometry(builder.pipeInternalDiameterM,
            builder.inventory.getTotalVolumeM3());
    addStandards(result);
    addInputWarnings(result, liquidMassKg);

    int steps = (int) Math.ceil(builder.maxTimeSeconds / builder.timeStepSeconds);
    for (int i = 0; i <= steps; i++) {
      double timeS = Math.min(i * builder.timeStepSeconds, builder.maxTimeSeconds);
      double outerWallTemperatureK = wall.getOuterWallTemperature();
      double innerWallTemperatureK = wall.getInnerWallTemperature();
      double allowableStressPa = builder.material.allowableRuptureStressAt(outerWallTemperatureK,
          builder.tensileStrengthFactor);
      double vonMisesStressPa = VesselRuptureCalculator.vonMisesStress(pressurePa,
          pipeInnerRadiusM, builder.wallThicknessM);
      double flangeRatingPa = temperatureReducedFlangeRating(flangeRatingAmbientPa,
          outerWallTemperatureK);

      result.addPoint(timeS, pressurePa / PA_PER_BARA, liquidTemperatureK, innerWallTemperatureK,
          outerWallTemperatureK, vonMisesStressPa / 1.0e6, allowableStressPa / 1.0e6,
          flangeRatingPa / PA_PER_BARA);
      recordEvents(result, timeS, pressurePa, vonMisesStressPa, allowableStressPa, flangeRatingPa,
          liquidTemperatureK);
      if (vonMisesStressPa >= allowableStressPa || pressurePa >= flangeRatingPa) {
        break;
      }
      if (i == steps) {
        break;
      }

      double previousMeanWallTemperatureK = wall.getMeanWallTemperature();
      double incidentFluxWPerM2 = builder.fireScenario.incidentHeatFlux(outerWallTemperatureK);
      wall.advanceTimeStep(builder.timeStepSeconds, liquidTemperatureK,
          builder.innerHeatTransferCoefficientWPerM2K, builder.fireScenario.getAmbientTemperatureK(),
          builder.outerHeatTransferCoefficientWPerM2K, incidentFluxWPerM2);
      double heatAbsorbedByWallJ = wall.getHeatAbsorbed(exposedAreaM2,
          previousMeanWallTemperatureK);
      double incidentHeatJ = incidentFluxWPerM2 * exposedAreaM2 * builder.timeStepSeconds;
      double heatToLiquidJ = Math.max(0.0, incidentHeatJ - Math.max(0.0, heatAbsorbedByWallJ));
      liquidTemperatureK += heatToLiquidJ / (liquidMassKg * builder.liquidHeatCapacityJPerKgK);
      pressurePa = pressureFromThermalExpansion(initialPressurePa, initialTemperatureK,
          liquidTemperatureK);
      if (builder.reliefSetPressurePa > 0.0 && pressurePa >= builder.reliefSetPressurePa
          && builder.reliefLimitsPressure) {
        pressurePa = builder.reliefSetPressurePa;
      }
    }
    addRecommendations(result);
    return result.build();
  }

  /**
   * Records event times for the current transient state.
   *
   * @param result result builder
   * @param timeS current time in s
   * @param pressurePa current pressure in Pa
   * @param vonMisesStressPa current von Mises stress in Pa
   * @param allowableStressPa current allowable stress in Pa
   * @param flangeRatingPa current temperature-reduced flange rating in Pa
   * @param liquidTemperatureK current liquid temperature in K
   */
  private void recordEvents(TrappedLiquidFireRuptureResult.Builder result, double timeS,
      double pressurePa, double vonMisesStressPa, double allowableStressPa, double flangeRatingPa,
      double liquidTemperatureK) {
    if (builder.reliefSetPressurePa > 0.0 && pressurePa >= builder.reliefSetPressurePa) {
      result.recordReliefSet(timeS);
    }
    if (builder.vaporPocketDetectionEnabled && vaporPhaseLikely(pressurePa, liquidTemperatureK)) {
      result.recordVaporPocket(timeS);
    }
    if (vonMisesStressPa >= allowableStressPa) {
      result.recordPipeRupture(timeS);
    }
    if (pressurePa >= flangeRatingPa) {
      result.recordFlangeFailure(timeS);
    }
  }

  /**
   * Adds applied standard references to the result.
   *
   * @param result result builder
   */
  private void addStandards(TrappedLiquidFireRuptureResult.Builder result) {
    result.addStandard("API 521 / ISO 23251 fire exposure and relief screening basis");
    result.addStandard("ASME B31.3/B31.4 pressure piping stress screening");
    result.addStandard("ASME B16.5 flange class pressure-temperature screening");
    result.addStandard("CCPS/TNO consequence handoff basis for post-rupture source terms");
  }

  /**
   * Adds setup warnings to the result.
   *
   * @param result result builder
   * @param liquidMassKg effective liquid mass in kg
   */
  private void addInputWarnings(TrappedLiquidFireRuptureResult.Builder result,
      double liquidMassKg) {
    if (builder.inventory.getTotalLiquidMassKg() <= 0.0) {
      result.addWarning("No liquid inventory was reported; total mass was used for thermal capacity.");
    }
    if (builder.inventory.getWarnings() != null) {
      for (String warning : builder.inventory.getWarnings()) {
        result.addWarning("Inventory warning: " + warning);
      }
    }
    if (liquidMassKg <= 0.0) {
      result.addWarning("Effective liquid mass is non-positive; result should not be used.");
    }
  }

  /**
   * Adds engineering recommendations after the run.
   *
   * @param result result builder
   */
  private void addRecommendations(TrappedLiquidFireRuptureResult.Builder result) {
    result.addRecommendation("Verify trapped inventory and isolation boundaries from current P&IDs, line lists, and valve positions.");
    result.addRecommendation("Use certified material, flange, bolt, and gasket pressure-temperature data for final design decisions.");
    result.addRecommendation("Evaluate PSV, thermal relief, drain/vent, or procedural safeguards for blocked-in liquid segments exposed to fire.");
    result.addRecommendation("Use the source-term handoff for consequence screening if pipe or flange failure is predicted.");
  }

  /**
   * Calculates pressure rise from blocked-liquid thermal expansion.
   *
   * @param initialPressurePa initial pressure in Pa
   * @param initialTemperatureK initial temperature in K
   * @param liquidTemperatureK current liquid temperature in K
   * @return pressure in Pa
   */
  private double pressureFromThermalExpansion(double initialPressurePa, double initialTemperatureK,
      double liquidTemperatureK) {
    double deltaTemperature = Math.max(0.0, liquidTemperatureK - initialTemperatureK);
    double pressureRisePa = builder.liquidBulkModulusPa * builder.liquidThermalExpansionPerK
        * deltaTemperature;
    return initialPressurePa + pressureRisePa;
  }

  /**
   * Checks whether a vapor phase appears at the current state.
   *
   * @param pressurePa pressure in Pa
   * @param temperatureK temperature in K
   * @return true if a gas phase is detected in the NeqSim flash
   */
  private boolean vaporPhaseLikely(double pressurePa, double temperatureK) {
    if (builder.fluid == null) {
      return false;
    }
    try {
      SystemInterface state = builder.fluid.clone();
      state.setPressure(pressurePa / PA_PER_BARA, "bara");
      state.setTemperature(temperatureK);
      ThermodynamicOperations operations = new ThermodynamicOperations(state);
      operations.TPflash();
      state.initProperties();
      return state.hasPhaseType("gas") && state.getPhase("gas").getNumberOfMolesInPhase() > 1.0e-10;
    } catch (RuntimeException ex) {
      return false;
    }
  }

  /**
   * Gets ambient flange class rating in Pa.
   *
   * @param flangeClass ASME B16.5 pressure class, or zero when not configured
   * @return ambient pressure rating in Pa, or positive infinity when no flange is configured
   */
  private static double flangeClassRatingPa(int flangeClass) {
    switch (flangeClass) {
      case 150:
        return 19.6 * PA_PER_BARA;
      case 300:
        return 51.0 * PA_PER_BARA;
      case 600:
        return 102.0 * PA_PER_BARA;
      case 900:
        return 153.0 * PA_PER_BARA;
      case 1500:
        return 255.0 * PA_PER_BARA;
      case 2500:
        return 425.0 * PA_PER_BARA;
      default:
        return Double.POSITIVE_INFINITY;
    }
  }

  /**
   * Applies a generic high-temperature derating to an ambient flange pressure class.
   *
   * @param ambientRatingPa ambient flange rating in Pa
   * @param metalTemperatureK metal temperature in K
   * @return temperature-reduced rating in Pa
   */
  private static double temperatureReducedFlangeRating(double ambientRatingPa,
      double metalTemperatureK) {
    if (!Double.isFinite(ambientRatingPa)) {
      return ambientRatingPa;
    }
    double temperatureC = metalTemperatureK - 273.15;
    double factor;
    if (temperatureC <= 100.0) {
      factor = 1.0;
    } else if (temperatureC <= 200.0) {
      factor = 0.90;
    } else if (temperatureC <= 300.0) {
      factor = 0.75;
    } else if (temperatureC <= 400.0) {
      factor = 0.55;
    } else if (temperatureC <= 500.0) {
      factor = 0.35;
    } else if (temperatureC <= 600.0) {
      factor = 0.20;
    } else {
      factor = 0.10;
    }
    return ambientRatingPa * factor;
  }

  /** Builder for {@link TrappedLiquidFireRuptureStudy}. */
  public static final class Builder {
    private String segmentId = "trapped-segment";
    private InventoryResult inventory;
    private SystemInterface fluid;
    private FireExposureScenario fireScenario;
    private MaterialStrengthCurve material = MaterialStrengthCurve.forApi5LPipeGrade("B");
    private double pipeInternalDiameterM = Double.NaN;
    private double pipeOuterDiameterM = Double.NaN;
    private double wallThicknessM = Double.NaN;
    private double exposedLengthM = Double.NaN;
    private int flangeClass;
    private double reliefSetPressurePa = Double.NaN;
    private boolean reliefLimitsPressure;
    private double maxTimeSeconds = 1800.0;
    private double timeStepSeconds = 2.0;
    private double wallDensityKgPerM3 = DEFAULT_STEEL_DENSITY;
    private double wallHeatCapacityJPerKgK = DEFAULT_STEEL_CP;
    private double wallThermalConductivityWPerMK = DEFAULT_STEEL_K;
    private double liquidHeatCapacityJPerKgK = DEFAULT_LIQUID_CP;
    private double liquidThermalExpansionPerK = DEFAULT_LIQUID_EXPANSION;
    private double liquidBulkModulusPa = DEFAULT_LIQUID_BULK_MODULUS;
    private double innerHeatTransferCoefficientWPerM2K = DEFAULT_INNER_HTC;
    private double outerHeatTransferCoefficientWPerM2K = DEFAULT_OUTER_HTC;
    private double tensileStrengthFactor = DEFAULT_TENSILE_FACTOR;
    private int wallNodes = 15;
    private boolean vaporPocketDetectionEnabled = true;

    /**
     * Sets segment identifier.
     *
     * @param segmentId segment identifier
     * @return this builder
     */
    public Builder segmentId(String segmentId) {
      if (segmentId != null && !segmentId.trim().isEmpty()) {
        this.segmentId = segmentId.trim();
      }
      return this;
    }

    /**
     * Sets trapped inventory result.
     *
     * @param inventory trapped inventory result
     * @return this builder
     */
    public Builder inventory(InventoryResult inventory) {
      this.inventory = inventory;
      return this;
    }

    /**
     * Sets representative fluid for vapor-pocket and source-term handoff calculations.
     *
     * @param fluid configured thermodynamic fluid
     * @return this builder
     */
    public Builder fluid(SystemInterface fluid) {
      this.fluid = fluid;
      return this;
    }

    /**
     * Sets fire exposure scenario.
     *
     * @param fireScenario fire exposure scenario
     * @return this builder
     */
    public Builder fireScenario(FireExposureScenario fireScenario) {
      this.fireScenario = fireScenario;
      return this;
    }

    /**
     * Sets material strength curve.
     *
     * @param material material strength curve
     * @return this builder
     */
    public Builder material(MaterialStrengthCurve material) {
      this.material = material;
      return this;
    }

    /**
     * Sets material from API 5L grade.
     *
     * @param grade API 5L grade
     * @return this builder
     */
    public Builder api5lMaterial(String grade) {
      this.material = MaterialStrengthCurve.forApi5LPipeGrade(grade);
      return this;
    }

    /**
     * Sets pipe geometry in SI units.
     *
     * @param pipeInternalDiameterM pipe internal diameter in m
     * @param wallThicknessM pipe wall thickness in m
     * @param exposedLengthM exposed pipe length in m
     * @return this builder
     */
    public Builder pipeGeometry(double pipeInternalDiameterM, double wallThicknessM,
        double exposedLengthM) {
      this.pipeInternalDiameterM = pipeInternalDiameterM;
      this.wallThicknessM = wallThicknessM;
      this.pipeOuterDiameterM = pipeInternalDiameterM + 2.0 * wallThicknessM;
      this.exposedLengthM = exposedLengthM;
      return this;
    }

    /**
     * Sets pipe geometry with unit conversion.
     *
     * @param pipeInternalDiameter pipe internal diameter value
     * @param diameterUnit diameter unit, one of m, mm, in, or ft
     * @param wallThickness wall thickness value
     * @param thicknessUnit thickness unit, one of m, mm, in, or ft
     * @param exposedLength exposed length value
     * @param lengthUnit length unit, one of m, mm, in, or ft
     * @return this builder
     */
    public Builder pipeGeometry(double pipeInternalDiameter, String diameterUnit,
        double wallThickness, String thicknessUnit, double exposedLength, String lengthUnit) {
      return pipeGeometry(toMeters(pipeInternalDiameter, diameterUnit), toMeters(wallThickness,
          thicknessUnit), toMeters(exposedLength, lengthUnit));
    }

    /**
     * Sets ASME B16.5 flange class for flange screening.
     *
     * @param flangeClass pressure class, for example 150, 300, 600, 900, 1500, or 2500
     * @return this builder
     */
    public Builder flangeClass(int flangeClass) {
      this.flangeClass = flangeClass;
      return this;
    }

    /**
     * Sets relief set pressure.
     *
     * @param pressure pressure value
     * @param unit pressure unit supported by {@link PressureUnit}
     * @param limitsPressure true if the relief device is assumed to limit pressure at set pressure
     * @return this builder
     */
    public Builder reliefSetPressure(double pressure, String unit, boolean limitsPressure) {
      this.reliefSetPressurePa = new PressureUnit(pressure, unit).getValue("bara") * PA_PER_BARA;
      this.reliefLimitsPressure = limitsPressure;
      return this;
    }

    /**
     * Sets simulation time controls.
     *
     * @param maxTimeSeconds maximum simulation time in s
     * @param timeStepSeconds time step in s
     * @return this builder
     */
    public Builder timeControls(double maxTimeSeconds, double timeStepSeconds) {
      this.maxTimeSeconds = maxTimeSeconds;
      this.timeStepSeconds = timeStepSeconds;
      return this;
    }

    /**
     * Sets liquid thermal properties used for pressure rise.
     *
     * @param heatCapacityJPerKgK liquid heat capacity in J/(kg K)
     * @param thermalExpansionPerK liquid volumetric expansion coefficient in 1/K
     * @param bulkModulusPa liquid bulk modulus in Pa
     * @return this builder
     */
    public Builder liquidThermalProperties(double heatCapacityJPerKgK,
        double thermalExpansionPerK, double bulkModulusPa) {
      this.liquidHeatCapacityJPerKgK = heatCapacityJPerKgK;
      this.liquidThermalExpansionPerK = thermalExpansionPerK;
      this.liquidBulkModulusPa = bulkModulusPa;
      return this;
    }

    /**
     * Sets wall thermal properties.
     *
     * @param densityKgPerM3 wall density in kg/m3
     * @param heatCapacityJPerKgK wall heat capacity in J/(kg K)
     * @param thermalConductivityWPerMK wall thermal conductivity in W/(m K)
     * @return this builder
     */
    public Builder wallThermalProperties(double densityKgPerM3, double heatCapacityJPerKgK,
        double thermalConductivityWPerMK) {
      this.wallDensityKgPerM3 = densityKgPerM3;
      this.wallHeatCapacityJPerKgK = heatCapacityJPerKgK;
      this.wallThermalConductivityWPerMK = thermalConductivityWPerMK;
      return this;
    }

    /**
     * Sets heat transfer coefficients.
     *
     * @param innerHeatTransferCoefficientWPerM2K inside wall coefficient in W/(m2 K)
     * @param outerHeatTransferCoefficientWPerM2K outside coefficient in W/(m2 K)
     * @return this builder
     */
    public Builder heatTransferCoefficients(double innerHeatTransferCoefficientWPerM2K,
        double outerHeatTransferCoefficientWPerM2K) {
      this.innerHeatTransferCoefficientWPerM2K = innerHeatTransferCoefficientWPerM2K;
      this.outerHeatTransferCoefficientWPerM2K = outerHeatTransferCoefficientWPerM2K;
      return this;
    }

    /**
     * Sets rupture tensile-strength utilization factor.
     *
     * @param tensileStrengthFactor fraction of temperature-reduced tensile strength; must be
     *        positive
     * @return this builder
     */
    public Builder tensileStrengthFactor(double tensileStrengthFactor) {
      this.tensileStrengthFactor = tensileStrengthFactor;
      return this;
    }

    /**
     * Enables or disables vapor-pocket detection by TP flash.
     *
     * @param enabled true to check for vapor phase during the transient
     * @return this builder
     */
    public Builder vaporPocketDetectionEnabled(boolean enabled) {
      this.vaporPocketDetectionEnabled = enabled;
      return this;
    }

    /**
     * Builds the study.
     *
     * @return trapped-liquid fire rupture study
     */
    public TrappedLiquidFireRuptureStudy build() {
      validate();
      return new TrappedLiquidFireRuptureStudy(this);
    }

    /**
     * Calculates exposed pipe area.
     *
     * @return exposed area in m2
     */
    private double exposedAreaM2() {
      return Math.PI * pipeOuterDiameterM * exposedLengthM;
    }

    /**
     * Validates builder setup.
     *
     * @throws IllegalStateException if required input is missing
     * @throws IllegalArgumentException if a numeric value is invalid
     */
    private void validate() {
      if (inventory == null) {
        throw new IllegalStateException("inventory must be set");
      }
      if (material == null) {
        throw new IllegalStateException("material must be set");
      }
      validatePositive(pipeInternalDiameterM, "pipeInternalDiameterM");
      validatePositive(pipeOuterDiameterM, "pipeOuterDiameterM");
      validatePositive(wallThicknessM, "wallThicknessM");
      validatePositive(exposedLengthM, "exposedLengthM");
      if (fireScenario == null) {
        fireScenario = FireExposureScenario.api521PoolFire(exposedAreaM2(), 1.0);
      }
      validatePositive(maxTimeSeconds, "maxTimeSeconds");
      validatePositive(timeStepSeconds, "timeStepSeconds");
      validatePositive(wallDensityKgPerM3, "wallDensityKgPerM3");
      validatePositive(wallHeatCapacityJPerKgK, "wallHeatCapacityJPerKgK");
      validatePositive(wallThermalConductivityWPerMK, "wallThermalConductivityWPerMK");
      validatePositive(liquidHeatCapacityJPerKgK, "liquidHeatCapacityJPerKgK");
      validatePositive(liquidThermalExpansionPerK, "liquidThermalExpansionPerK");
      validatePositive(liquidBulkModulusPa, "liquidBulkModulusPa");
      validatePositive(innerHeatTransferCoefficientWPerM2K,
          "innerHeatTransferCoefficientWPerM2K");
      validatePositive(outerHeatTransferCoefficientWPerM2K,
          "outerHeatTransferCoefficientWPerM2K");
      validatePositive(tensileStrengthFactor, "tensileStrengthFactor");
      if (wallNodes < 3) {
        throw new IllegalArgumentException("wallNodes must be at least 3");
      }
    }
  }

  /**
   * Converts a length value to meters.
   *
   * @param value length value
   * @param unit length unit
   * @return length in m
   */
  private static double toMeters(double value, String unit) {
    validatePositive(value, "length");
    if (unit == null) {
      return value;
    }
    String normalized = unit.trim().toLowerCase();
    if ("mm".equals(normalized)) {
      return value / 1000.0;
    }
    if ("cm".equals(normalized)) {
      return value / 100.0;
    }
    if ("in".equals(normalized) || "inch".equals(normalized)) {
      return value * 0.0254;
    }
    if ("ft".equals(normalized) || "foot".equals(normalized) || "feet".equals(normalized)) {
      return value * 0.3048;
    }
    return value;
  }

  /**
   * Validates that a numeric value is positive and finite.
   *
   * @param value value to validate
   * @param name parameter name used in exception messages
   * @throws IllegalArgumentException if value is invalid
   */
  private static void validatePositive(double value, String name) {
    if (value <= 0.0 || Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(name + " must be positive and finite");
    }
  }
}
