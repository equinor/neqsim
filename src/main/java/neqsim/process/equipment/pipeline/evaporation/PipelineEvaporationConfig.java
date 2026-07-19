package neqsim.process.equipment.pipeline.evaporation;

/**
 * Numerical and geometric settings for {@link PipelineEvaporationStudy}.
 *
 * <p>
 * Velocities are actual phase velocities, not superficial velocities. SI units are used throughout.
 * </p>
 */
public class PipelineEvaporationConfig {
  private LiquidDistribution liquidDistribution = LiquidDistribution.DROPLETS;
  private double pipeLength = 100.0;
  private double pipeDiameter = 0.20;
  private double pipeRoughness = 1.0e-5;
  private double gasVelocity = 10.0;
  private double liquidVelocity = 0.5;
  private double initialDropletDiameter = 200.0e-6;
  private double initialFilmThickness = 0.50e-3;
  private double wettedPerimeterFraction = 1.0;
  private double ambientTemperature = 288.15;
  private double overallWallHeatTransferCoefficient = 0.0;
  private double initialStepLength = 0.25;
  private double minimumStepLength = 1.0e-12;
  private double maximumStepLength = 2.0;
  private double maximumDonorFractionPerStep = 0.05;
  private double maximumTemperatureChangePerStep = 2.0;
  private double completionFraction = 1.0e-4;
  private int maximumNumberOfSteps = 100000;
  private boolean useAbramzonSirignano = true;

  /** @return liquid geometry model */
  public LiquidDistribution getLiquidDistribution() {
    return liquidDistribution;
  }

  /** @param liquidDistribution liquid geometry model */
  public void setLiquidDistribution(LiquidDistribution liquidDistribution) {
    this.liquidDistribution = liquidDistribution;
  }

  /** @return pipe length in m */
  public double getPipeLength() {
    return pipeLength;
  }

  /** @param pipeLength pipe length in m */
  public void setPipeLength(double pipeLength) {
    this.pipeLength = pipeLength;
  }

  /** @return pipe inside diameter in m */
  public double getPipeDiameter() {
    return pipeDiameter;
  }

  /** @param pipeDiameter pipe inside diameter in m */
  public void setPipeDiameter(double pipeDiameter) {
    this.pipeDiameter = pipeDiameter;
  }

  /** @return pipe roughness in m */
  public double getPipeRoughness() {
    return pipeRoughness;
  }

  /** @param pipeRoughness pipe roughness in m */
  public void setPipeRoughness(double pipeRoughness) {
    this.pipeRoughness = pipeRoughness;
  }

  /** @return actual gas velocity in m/s */
  public double getGasVelocity() {
    return gasVelocity;
  }

  /** @param gasVelocity actual gas velocity in m/s */
  public void setGasVelocity(double gasVelocity) {
    this.gasVelocity = gasVelocity;
  }

  /** @return actual liquid velocity in m/s */
  public double getLiquidVelocity() {
    return liquidVelocity;
  }

  /** @param liquidVelocity actual liquid velocity in m/s */
  public void setLiquidVelocity(double liquidVelocity) {
    this.liquidVelocity = liquidVelocity;
  }

  /** @return initial droplet Sauter mean diameter in m */
  public double getInitialDropletDiameter() {
    return initialDropletDiameter;
  }

  /** @param initialDropletDiameter initial droplet Sauter mean diameter in m */
  public void setInitialDropletDiameter(double initialDropletDiameter) {
    this.initialDropletDiameter = initialDropletDiameter;
  }

  /** @return initial film thickness in m */
  public double getInitialFilmThickness() {
    return initialFilmThickness;
  }

  /** @param initialFilmThickness initial film thickness in m */
  public void setInitialFilmThickness(double initialFilmThickness) {
    this.initialFilmThickness = initialFilmThickness;
  }

  /** @return fraction of the pipe perimeter covered by film */
  public double getWettedPerimeterFraction() {
    return wettedPerimeterFraction;
  }

  /** @param wettedPerimeterFraction fraction of the pipe perimeter covered by film */
  public void setWettedPerimeterFraction(double wettedPerimeterFraction) {
    this.wettedPerimeterFraction = wettedPerimeterFraction;
  }

  /** @return ambient temperature in K */
  public double getAmbientTemperature() {
    return ambientTemperature;
  }

  /** @param ambientTemperature ambient temperature in K */
  public void setAmbientTemperature(double ambientTemperature) {
    this.ambientTemperature = ambientTemperature;
  }

  /** @return overall wall heat-transfer coefficient in W/(m2 K) */
  public double getOverallWallHeatTransferCoefficient() {
    return overallWallHeatTransferCoefficient;
  }

  /** @param coefficient overall wall heat-transfer coefficient in W/(m2 K) */
  public void setOverallWallHeatTransferCoefficient(double coefficient) {
    this.overallWallHeatTransferCoefficient = coefficient;
  }

  /** @return initial axial step in m */
  public double getInitialStepLength() {
    return initialStepLength;
  }

  /** @param initialStepLength initial axial step in m */
  public void setInitialStepLength(double initialStepLength) {
    this.initialStepLength = initialStepLength;
  }

  /** @return minimum axial step in m */
  public double getMinimumStepLength() {
    return minimumStepLength;
  }

  /** @param minimumStepLength minimum axial step in m */
  public void setMinimumStepLength(double minimumStepLength) {
    this.minimumStepLength = minimumStepLength;
  }

  /** @return maximum axial step in m */
  public double getMaximumStepLength() {
    return maximumStepLength;
  }

  /** @param maximumStepLength maximum axial step in m */
  public void setMaximumStepLength(double maximumStepLength) {
    this.maximumStepLength = maximumStepLength;
  }

  /** @return maximum fraction removed from any donor component in one step */
  public double getMaximumDonorFractionPerStep() {
    return maximumDonorFractionPerStep;
  }

  /** @param fraction maximum fraction removed from any donor component in one step */
  public void setMaximumDonorFractionPerStep(double fraction) {
    this.maximumDonorFractionPerStep = fraction;
  }

  /** @return maximum estimated phase temperature change in one step in K */
  public double getMaximumTemperatureChangePerStep() {
    return maximumTemperatureChangePerStep;
  }

  /** @param change maximum estimated phase temperature change in one step in K */
  public void setMaximumTemperatureChangePerStep(double change) {
    this.maximumTemperatureChangePerStep = change;
  }

  /** @return remaining injected-liquid fraction defining complete evaporation */
  public double getCompletionFraction() {
    return completionFraction;
  }

  /** @param completionFraction remaining injected-liquid fraction defining complete evaporation */
  public void setCompletionFraction(double completionFraction) {
    this.completionFraction = completionFraction;
  }

  /** @return maximum number of accepted axial steps */
  public int getMaximumNumberOfSteps() {
    return maximumNumberOfSteps;
  }

  /** @param maximumNumberOfSteps maximum number of accepted axial steps */
  public void setMaximumNumberOfSteps(int maximumNumberOfSteps) {
    this.maximumNumberOfSteps = maximumNumberOfSteps;
  }

  /** @return whether the droplet Stefan-flow correction is enabled */
  public boolean isUseAbramzonSirignano() {
    return useAbramzonSirignano;
  }

  /** @param useAbramzonSirignano whether the droplet Stefan-flow correction is enabled */
  public void setUseAbramzonSirignano(boolean useAbramzonSirignano) {
    this.useAbramzonSirignano = useAbramzonSirignano;
  }

  /** Validate all settings before a calculation. */
  public void validate() {
    require(liquidDistribution != null, "liquid distribution must be specified");
    requirePositive(pipeLength, "pipe length");
    requirePositive(pipeDiameter, "pipe diameter");
    requireNonNegative(pipeRoughness, "pipe roughness");
    requirePositive(gasVelocity, "gas velocity");
    requirePositive(liquidVelocity, "liquid velocity");
    requirePositive(initialDropletDiameter, "initial droplet diameter");
    requirePositive(initialFilmThickness, "initial film thickness");
    require(initialDropletDiameter < pipeDiameter, "initial droplet diameter must be smaller than the pipe diameter");
    require(2.0 * initialFilmThickness < pipeDiameter, "initial film thickness must be smaller than the pipe radius");
    require(wettedPerimeterFraction > 0.0 && wettedPerimeterFraction <= 1.0,
        "wetted perimeter fraction must be in (0, 1]");
    requirePositive(ambientTemperature, "ambient temperature");
    requireNonNegative(overallWallHeatTransferCoefficient, "wall heat-transfer coefficient");
    requirePositive(initialStepLength, "initial step length");
    requirePositive(minimumStepLength, "minimum step length");
    requirePositive(maximumStepLength, "maximum step length");
    require(minimumStepLength <= initialStepLength && initialStepLength <= maximumStepLength,
        "step lengths must satisfy minimum <= initial <= maximum");
    require(maximumDonorFractionPerStep > 0.0 && maximumDonorFractionPerStep < 1.0,
        "maximum donor fraction must be in (0, 1)");
    requirePositive(maximumTemperatureChangePerStep, "maximum temperature change");
    require(completionFraction > 0.0 && completionFraction < 1.0, "completion fraction must be in (0, 1)");
    require(maximumNumberOfSteps > 0, "maximum number of steps must be positive");
  }

  private static void requirePositive(double value, String name) {
    require(Double.isFinite(value) && value > 0.0, name + " must be finite and positive");
  }

  private static void requireNonNegative(double value, String name) {
    require(Double.isFinite(value) && value >= 0.0, name + " must be finite and non-negative");
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalArgumentException(message);
    }
  }
}
