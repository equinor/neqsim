package neqsim.process.safety.rupture;

import java.io.Serializable;
import neqsim.process.safety.rupture.PipeFireRuptureResult.ReleaseEstimate;

/**
 * Blowdown pipe fire-rupture strain-rate study.
 *
 * <p>
 * The study implements the calculation structure used by legacy fire-rupture spreadsheets for pipes
 * exposed to fire while a blowdown pressure profile is supplied externally. It couples pipe
 * heat-up, pressure stress, temperature-dependent material properties, a Sellars-Tegart strain-rate
 * law, accumulated strain, and spreadsheet-style release estimates.
 * </p>
 *
 * <p>
 * This model is intended for screening and agent workflows where pipe data may be gathered from
 * source documents and piping specifications, then reviewed by an engineer before the calculation
 * is run. Formal safety studies should verify the material basis, pressure profile, segment
 * inventory, and release modelling.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class PipeFireRuptureStudy implements Serializable {
  private static final long serialVersionUID = 1L;

  private static final double PA_PER_BAR = 100000.0;
  private static final double UNIVERSAL_GAS_CONSTANT_J_PER_KMOLK = 8314.0;
  private static final double DEFAULT_MAX_TIME_SECONDS = 3600.0;
  private static final double DEFAULT_TIME_STEP_SECONDS = 5.0;
  private static final double GAS_RELEASE_COEFFICIENT = 0.00527;
  private static final double SHORT_PIPE_GAS_MULTIPLIER = 1.541;
  private static final double LIQUID_DISCHARGE_COEFFICIENT = 0.62;
  private static final double LIQUID_FLOW_CONSTANT = 11.78;
  private static final double GAS_LIQUID_DENSITY_SWITCH_KG_PER_M3 = 500.0;

  private final PipeFireRuptureInput input;
  private final PipeFireRuptureMaterial material;
  private final PipeFireRuptureScenario scenario;
  private final BlowdownPressureProfile pressureProfile;
  private final double timeStepSeconds;
  private final double maxTimeSeconds;
  private final boolean spreadsheetGasThermalMass;

  /**
   * Creates a pipe fire-rupture study.
   *
   * @param builder populated builder
   */
  private PipeFireRuptureStudy(Builder builder) {
    builder.validate();
    this.input = builder.input;
    this.material = builder.material;
    this.scenario = builder.scenario;
    this.pressureProfile = builder.pressureProfile;
    this.timeStepSeconds = builder.timeStepSeconds;
    this.maxTimeSeconds = builder.maxTimeSeconds;
    this.spreadsheetGasThermalMass = builder.spreadsheetGasThermalMass;
  }

  /**
   * Creates a builder for a pipe fire-rupture study.
   *
   * @param input pipe geometry and fluid input
   * @param material material property curve
   * @param scenario fire exposure scenario
   * @param pressureProfile blowdown pressure profile
   * @return new study builder
   */
  public static Builder builder(PipeFireRuptureInput input, PipeFireRuptureMaterial material,
      PipeFireRuptureScenario scenario, BlowdownPressureProfile pressureProfile) {
    return new Builder(input, material, scenario, pressureProfile);
  }

  /**
   * Runs the fire-rupture study.
   *
   * @return calculation result with time series and release estimate
   */
  public PipeFireRuptureResult run() {
    PipeFireRuptureResult.Builder result =
        PipeFireRuptureResult.builder(input, material, scenario, pressureProfile);
    addInputWarnings(result);

    double meanWallTemperatureC = input.getInitialTemperatureC();
    double outerSurfaceTemperatureC = input.getInitialTemperatureC();
    double heatFluxKWPerM2 = scenario.heatFluxKWPerM2(outerSurfaceTemperatureC);
    double accumulatedStrain = 0.0;
    int steps = (int) Math.ceil(maxTimeSeconds / timeStepSeconds);

    for (int step = 0; step <= steps; step++) {
      double timeSeconds = Math.min(step * timeStepSeconds, maxTimeSeconds);
      PipeState pipeState = pipeState(accumulatedStrain);
      if (step > 0) {
        double currentPressureBarg = Math.max(0.0, pressureProfile.pressureBargAt(timeSeconds));
        double previousMeanWallTemperatureC = meanWallTemperatureC;
        double previousHeatFluxKWPerM2 = heatFluxKWPerM2;
        double previousThermalConductivityWPerMK =
            material.thermalConductivityAt(previousMeanWallTemperatureC);
        double previousHeatCapacityJPerKgK = material.heatCapacityAt(previousMeanWallTemperatureC);
        double thermalMassJPerK = thermalMassJPerK(currentPressureBarg,
            previousMeanWallTemperatureC, previousHeatCapacityJPerKgK);
        meanWallTemperatureC += previousHeatFluxKWPerM2 * 1000.0 * input.getExposedAreaM2()
            * timeStepSeconds / thermalMassJPerK;
        outerSurfaceTemperatureC = meanWallTemperatureC + 0.5 * previousHeatFluxKWPerM2 * 1000.0
            * pipeState.wallThicknessM / previousThermalConductivityWPerMK;
        heatFluxKWPerM2 = scenario.heatFluxKWPerM2(outerSurfaceTemperatureC);
      }

      double pressureBarg = Math.max(0.0, pressureProfile.pressureBargAt(timeSeconds));
      double stressMPa = vonMisesStressMPa(pressureBarg, pipeState.outsideDiameterM,
          pipeState.insideDiameterM, input.getWeightStressMPa());
      double strainRatePerMinute = material.strainRatePerMinute(stressMPa, meanWallTemperatureC);
      if (step > 0) {
        accumulatedStrain =
            Math.min(2.0, accumulatedStrain + timeStepSeconds * strainRatePerMinute / 60.0);
      }
      double strainLimit = material.ruptureStrainLimitAt(meanWallTemperatureC);

      result.addPoint(timeSeconds, pressureBarg, meanWallTemperatureC, outerSurfaceTemperatureC,
          heatFluxKWPerM2, stressMPa, strainRatePerMinute, accumulatedStrain, strainLimit);
      if (accumulatedStrain > strainLimit) {
        result.recordRupture(timeSeconds, pressureBarg, meanWallTemperatureC,
            outerSurfaceTemperatureC, accumulatedStrain, strainLimit);
        result.releaseEstimate(releaseEstimate(pressureBarg));
        addRecommendations(result, true);
        return result.build();
      }
      if (step == steps) {
        break;
      }
    }

    result.releaseEstimate(null);
    addRecommendations(result, false);
    return result.build();
  }

  /**
   * Adds input warnings to the result.
   *
   * @param result result builder
   */
  private void addInputWarnings(PipeFireRuptureResult.Builder result) {
    if (input.getFluidDensityKgPerM3() < GAS_LIQUID_DENSITY_SWITCH_KG_PER_M3) {
      result.addWarning(
          "Fluid density below 500 kg/m3: gas heat capacity and gas release screening are used.");
    } else {
      result.addWarning(
          "Fluid density at or above 500 kg/m3: liquid heat capacity and liquid release screening are used.");
    }
    if (input.getExposedLengthM() <= 0.0) {
      result.addWarning("Exposed length is not positive; verify pipe geometry before use.");
    }
  }

  /**
   * Adds engineering recommendations to the result.
   *
   * @param result result builder
   * @param rupturePredicted true when rupture was predicted
   */
  private void addRecommendations(PipeFireRuptureResult.Builder result, boolean rupturePredicted) {
    result.addRecommendation(
        "Verify pipe class, OD, wall thickness, undertolerance, corrosion allowance, weld factor, and material against the applicable piping specification or certified project data.");
    result.addRecommendation(
        "Verify the blowdown pressure profile with a governed depressurization calculation before using the rupture time for design decisions.");
    result.addRecommendation(
        "Use the spreadsheet-style release estimates as screening values and hand off the rupture state to NeqSim source-term/consequence tools for formal reporting.");
    if (rupturePredicted) {
      result.addRecommendation(
          "Rupture is predicted for this fire scenario; review passive fire protection, segment isolation, depressurization time, and leak-rate consequences.");
    } else {
      result.addRecommendation(
          "No rupture is predicted within the simulated time; check whether the maximum time covers the relevant fire exposure duration.");
    }
  }

  /**
   * Calculates the thermal mass used for the next heat-up step.
   *
   * @param pressureBarg pressure for the next row in barg
   * @param meanWallTemperatureC current mean wall temperature in degrees Celsius
   * @param wallHeatCapacityJPerKgK wall heat capacity in J/kg-K
   * @return thermal mass in J/K
   */
  private double thermalMassJPerK(double pressureBarg, double meanWallTemperatureC,
      double wallHeatCapacityJPerKgK) {
    double metalMassKg = material.getDensityKgPerM3() * input.getExposedAreaM2()
        * input.getEffectiveWallThicknessM();
    double metalThermalMass = metalMassKg * wallHeatCapacityJPerKgK;
    if (input.getFluidDensityKgPerM3() >= GAS_LIQUID_DENSITY_SWITCH_KG_PER_M3) {
      return metalThermalMass + input.getInternalVolumeM3() * input.getFluidDensityKgPerM3()
          * input.getFluidHeatCapacityJPerKgK();
    }
    if (!spreadsheetGasThermalMass) {
      return metalThermalMass;
    }
    double absolutePressurePa = Math.max(0.0, pressureBarg) * PA_PER_BAR;
    double gasMassKg = absolutePressurePa * input.getGasMolecularWeightKgPerKmol()
        / (UNIVERSAL_GAS_CONSTANT_J_PER_KMOLK * (meanWallTemperatureC + 273.0))
        * input.getInternalVolumeM3();
    return metalThermalMass + gasMassKg * input.getFluidHeatCapacityJPerKgK();
  }

  /**
   * Calculates pipe dimensions after accumulated strain.
   *
   * @param accumulatedStrain accumulated effective strain
   * @return pipe state
   */
  private PipeState pipeState(double accumulatedStrain) {
    double strainFactor = 1.0 + Math.max(0.0, accumulatedStrain);
    double outsideDiameterM = input.getOutsideDiameterM() * strainFactor;
    double wallThicknessM = input.getEffectiveWallThicknessM() / strainFactor;
    double insideDiameterM = outsideDiameterM - 2.0 * wallThicknessM;
    return new PipeState(outsideDiameterM, insideDiameterM, wallThicknessM);
  }

  /**
   * Calculates von Mises stress using the spreadsheet thick-wall expressions.
   *
   * @param pressureBarg pipe internal gauge pressure in barg
   * @param outsideDiameterM current outside diameter in m
   * @param insideDiameterM current inside diameter in m
   * @param weightStressMPa axial weight stress in MPa
   * @return von Mises stress in MPa
   */
  private static double vonMisesStressMPa(double pressureBarg, double outsideDiameterM,
      double insideDiameterM, double weightStressMPa) {
    double pressureMPa = 0.1 * Math.max(0.0, pressureBarg);
    double denominator = outsideDiameterM * outsideDiameterM - insideDiameterM * insideDiameterM;
    double meanDiameterM = (outsideDiameterM + insideDiameterM) / 2.0;
    double innerOuterProduct = insideDiameterM * insideDiameterM * outsideDiameterM
        * outsideDiameterM / (meanDiameterM * meanDiameterM);
    double radialStressMPa =
        pressureMPa * (insideDiameterM * insideDiameterM - innerOuterProduct) / denominator;
    double hoopStressMPa =
        pressureMPa * (insideDiameterM * insideDiameterM + innerOuterProduct) / denominator;
    double longitudinalStressMPa =
        pressureMPa * insideDiameterM * insideDiameterM / denominator + weightStressMPa;
    return Math.sqrt(radialStressMPa * radialStressMPa + hoopStressMPa * hoopStressMPa
        + longitudinalStressMPa * longitudinalStressMPa - radialStressMPa * hoopStressMPa
        - radialStressMPa * longitudinalStressMPa - hoopStressMPa * longitudinalStressMPa);
  }

  /**
   * Calculates the spreadsheet-style release estimate at rupture.
   *
   * @param pressureBarg rupture pressure in barg, or null if no rupture was predicted
   * @return release estimate
   */
  private ReleaseEstimate releaseEstimate(Double pressureBarg) {
    if (pressureBarg == null || pressureBarg.doubleValue() <= 0.0) {
      return new ReleaseEstimate(0.0, 0.0, 0.0, 0.0, input.getInitialTemperatureC(),
          "No rupture predicted; release estimate set to zero.");
    }
    double insideAreaM2 = Math.PI / 4.0 * Math.pow(input.getInitialInsideDiameterM(), 2.0);
    double longPipeGasTwoSides = 0.0;
    double longPipeGasOneSide = 0.0;
    double shortPipeGasOneSide = 0.0;
    double liquidOneSide = 0.0;
    if (input.getFluidDensityKgPerM3() < GAS_LIQUID_DENSITY_SWITCH_KG_PER_M3) {
      longPipeGasTwoSides = GAS_RELEASE_COEFFICIENT * insideAreaM2 * pressureBarg.doubleValue()
          * PA_PER_BAR * Math.sqrt(input.getGasMolecularWeightKgPerKmol()
              / Math.max(1.0, input.getInitialTemperatureC() + 273.0));
      longPipeGasOneSide = longPipeGasTwoSides / 2.0;
      shortPipeGasOneSide = longPipeGasOneSide * SHORT_PIPE_GAS_MULTIPLIER;
    } else {
      double specificGravity = input.getFluidDensityKgPerM3() / 1000.0;
      liquidOneSide =
          ((insideAreaM2 * 1000000.0) * LIQUID_DISCHARGE_COEFFICIENT / LIQUID_FLOW_CONSTANT
              / Math.sqrt(specificGravity / (Math.max(pressureBarg.doubleValue(), 1.0) * 100.0)))
              / 60.0 * specificGravity;
    }
    return new ReleaseEstimate(longPipeGasTwoSides, longPipeGasOneSide, shortPipeGasOneSide,
        liquidOneSide, input.getInitialTemperatureC(),
        "Spreadsheet-style screening release estimate based on rupture pressure, pipe bore, gas MW or liquid density.");
  }

  /** Builder for {@link PipeFireRuptureStudy}. */
  public static final class Builder {
    private final PipeFireRuptureInput input;
    private final PipeFireRuptureMaterial material;
    private final PipeFireRuptureScenario scenario;
    private final BlowdownPressureProfile pressureProfile;
    private double timeStepSeconds = DEFAULT_TIME_STEP_SECONDS;
    private double maxTimeSeconds = DEFAULT_MAX_TIME_SECONDS;
    private boolean spreadsheetGasThermalMass = true;

    /**
     * Creates a builder.
     *
     * @param input pipe input object
     * @param material material curve
     * @param scenario fire scenario
     * @param pressureProfile pressure profile
     */
    private Builder(PipeFireRuptureInput input, PipeFireRuptureMaterial material,
        PipeFireRuptureScenario scenario, BlowdownPressureProfile pressureProfile) {
      this.input = input;
      this.material = material;
      this.scenario = scenario;
      this.pressureProfile = pressureProfile;
    }

    /**
     * Sets the calculation time step.
     *
     * @param timeStepSeconds time step in seconds; must be positive
     * @return this builder
     */
    public Builder timeStepSeconds(double timeStepSeconds) {
      validatePositive(timeStepSeconds, "timeStepSeconds");
      this.timeStepSeconds = timeStepSeconds;
      return this;
    }

    /**
     * Sets the maximum simulation time.
     *
     * @param maxTimeSeconds maximum time in seconds; must be positive
     * @return this builder
     */
    public Builder maxTimeSeconds(double maxTimeSeconds) {
      validatePositive(maxTimeSeconds, "maxTimeSeconds");
      this.maxTimeSeconds = maxTimeSeconds;
      return this;
    }

    /**
     * Selects whether gas thermal mass follows the legacy spreadsheet ideal-gas approximation.
     *
     * @param spreadsheetGasThermalMass true to include gas thermal mass for gas cases
     * @return this builder
     */
    public Builder spreadsheetGasThermalMass(boolean spreadsheetGasThermalMass) {
      this.spreadsheetGasThermalMass = spreadsheetGasThermalMass;
      return this;
    }

    /**
     * Builds the study.
     *
     * @return pipe fire-rupture study
     */
    public PipeFireRuptureStudy build() {
      return new PipeFireRuptureStudy(this);
    }

    /**
     * Validates the builder state.
     *
     * @throws IllegalArgumentException if the builder is invalid
     */
    private void validate() {
      if (input == null) {
        throw new IllegalArgumentException("input must not be null");
      }
      if (material == null) {
        throw new IllegalArgumentException("material must not be null");
      }
      if (scenario == null) {
        throw new IllegalArgumentException("scenario must not be null");
      }
      if (pressureProfile == null) {
        throw new IllegalArgumentException("pressureProfile must not be null");
      }
      validatePositive(timeStepSeconds, "timeStepSeconds");
      validatePositive(maxTimeSeconds, "maxTimeSeconds");
      if (maxTimeSeconds < timeStepSeconds) {
        throw new IllegalArgumentException("maxTimeSeconds must be at least timeStepSeconds");
      }
    }
  }

  /** Current pipe dimensions after strain deformation. */
  private static final class PipeState {
    private final double outsideDiameterM;
    private final double insideDiameterM;
    private final double wallThicknessM;

    /**
     * Creates a pipe state.
     *
     * @param outsideDiameterM outside diameter in m
     * @param insideDiameterM inside diameter in m
     * @param wallThicknessM wall thickness in m
     */
    private PipeState(double outsideDiameterM, double insideDiameterM, double wallThicknessM) {
      this.outsideDiameterM = outsideDiameterM;
      this.insideDiameterM = insideDiameterM;
      this.wallThicknessM = wallThicknessM;
    }
  }

  /**
   * Validates a positive finite value.
   *
   * @param value value to validate
   * @param name parameter name for messages
   * @throws IllegalArgumentException if the value is invalid
   */
  private static void validatePositive(double value, String name) {
    if (value <= 0.0 || Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException(name + " must be positive and finite");
    }
  }
}
