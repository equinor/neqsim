package neqsim.process.equipment.pipeline.twophasepipe.closure;

import java.io.Serializable;

/**
 * Configurable algebraic bubble-size closure for liquid-continuous bubbly-flow models.
 *
 * <p>
 * The closure exposes the historical TwoFluidPipe buoyancy/capillary diameter scale as explicit configuration. By
 * default it uses a fixed {@code 0.02 N/m} surface tension and a {@code D/5} geometry cap, preserving the existing
 * bubble and dispersed-bubble calculations. Callers may opt into the local phase-property surface tension already
 * supplied by the pipe section.
 * </p>
 *
 * <p>
 * This is a single algebraic characteristic size. It does not model a bubble-size distribution, deformation,
 * coalescence, breakup, or turbulent-dissipation dependence.
 * </p>
 */
public final class BubbleSizeClosure implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double DEFAULT_SURFACE_TENSION = 0.02;
  private static final double DEFAULT_PIPE_DIAMETER_FRACTION = 0.20;
  private static final double HISTORICAL_SCALE_COEFFICIENT = 0.725;

  private double surfaceTension = DEFAULT_SURFACE_TENSION;
  private double maximumPipeDiameterFraction = DEFAULT_PIPE_DIAMETER_FRACTION;
  private boolean useLocalSurfaceTension;

  /** Creates a closure with the historical TwoFluidPipe assumptions. */
  public BubbleSizeClosure() {
  }

  /**
   * Creates a closure with explicit fixed surface tension.
   *
   * @param surfaceTension surface tension in N/m
   */
  public BubbleSizeClosure(double surfaceTension) {
    setSurfaceTension(surfaceTension);
  }

  /**
   * Sets the fixed gas-liquid surface tension used by the bubble-size estimate.
   *
   * @param surfaceTension surface tension in N/m, strictly positive and finite
   */
  public void setSurfaceTension(double surfaceTension) {
    requirePositiveFinite(surfaceTension, "surfaceTension");
    this.surfaceTension = surfaceTension;
  }

  /** @return configured fixed surface tension in N/m */
  public double getSurfaceTension() {
    return surfaceTension;
  }

  /**
   * Selects the local phase-property surface tension supplied for each pipe section.
   *
   * <p>
   * The default is {@code false}, which preserves the historical fixed {@code 0.02 N/m} calculation. When enabled, the
   * five-argument estimate validates and uses its local surface tension argument.
   * </p>
   *
   * @param useLocal true to use the caller-supplied local surface tension
   */
  public void setUseLocalSurfaceTension(boolean useLocal) {
    useLocalSurfaceTension = useLocal;
  }

  /** @return true when the caller-supplied local surface tension is selected */
  public boolean isUseLocalSurfaceTension() {
    return useLocalSurfaceTension;
  }

  /**
   * Sets the upper bubble-diameter limit as a fraction of pipe diameter.
   *
   * @param fraction fraction in the interval (0, 1]
   */
  public void setMaximumPipeDiameterFraction(double fraction) {
    if (!Double.isFinite(fraction) || fraction <= 0.0 || fraction > 1.0) {
      throw new IllegalArgumentException("fraction must be finite and in (0, 1]");
    }
    maximumPipeDiameterFraction = fraction;
  }

  /** @return configured maximum bubble diameter divided by pipe diameter */
  public double getMaximumPipeDiameterFraction() {
    return maximumPipeDiameterFraction;
  }

  /**
   * Estimates a characteristic bubble diameter using the configured fixed surface tension.
   *
   * @param pipeDiameter internal pipe diameter in m
   * @param continuousPhaseDensity continuous-phase density in kg/m3
   * @param dispersedPhaseDensity dispersed-phase density in kg/m3
   * @param gravity gravitational acceleration magnitude in m/s2
   * @return characteristic bubble diameter in m
   */
  public double estimateDiameter(double pipeDiameter, double continuousPhaseDensity, double dispersedPhaseDensity,
      double gravity) {
    return estimate(pipeDiameter, continuousPhaseDensity, dispersedPhaseDensity, gravity, surfaceTension);
  }

  /**
   * Estimates a characteristic bubble diameter with optional local phase-property surface tension.
   *
   * <p>
   * The uncapped historical scale is {@code 2 * sqrt(0.725 * sigma / (g * abs(deltaRho)))}. The returned value is
   * capped at {@code maximumPipeDiameterFraction * pipeDiameter}. The {@code localSurfaceTension} argument is used only
   * when {@link #isUseLocalSurfaceTension()} is true.
   * </p>
   *
   * @param pipeDiameter internal pipe diameter in m
   * @param continuousPhaseDensity continuous-phase density in kg/m3
   * @param dispersedPhaseDensity dispersed-phase density in kg/m3
   * @param gravity gravitational acceleration magnitude in m/s2
   * @param localSurfaceTension local phase-property surface tension in N/m
   * @return characteristic bubble diameter in m
   */
  public double estimateDiameter(double pipeDiameter, double continuousPhaseDensity, double dispersedPhaseDensity,
      double gravity, double localSurfaceTension) {
    double selectedSurfaceTension = surfaceTension;
    if (useLocalSurfaceTension) {
      requirePositiveFinite(localSurfaceTension, "localSurfaceTension");
      selectedSurfaceTension = localSurfaceTension;
    }
    return estimate(pipeDiameter, continuousPhaseDensity, dispersedPhaseDensity, gravity, selectedSurfaceTension);
  }

  private double estimate(double pipeDiameter, double continuousPhaseDensity, double dispersedPhaseDensity,
      double gravity, double selectedSurfaceTension) {
    requirePositiveFinite(pipeDiameter, "pipeDiameter");
    requirePositiveFinite(continuousPhaseDensity, "continuousPhaseDensity");
    requirePositiveFinite(dispersedPhaseDensity, "dispersedPhaseDensity");
    requirePositiveFinite(gravity, "gravity");
    double geometryBound = maximumPipeDiameterFraction * pipeDiameter;
    double densityDifference = Math.abs(continuousPhaseDensity - dispersedPhaseDensity);
    if (densityDifference == 0.0) {
      return geometryBound;
    }
    double unconstrained = 2.0
        * Math.pow(HISTORICAL_SCALE_COEFFICIENT * selectedSurfaceTension / (gravity * densityDifference), 0.5);
    return Math.min(unconstrained, geometryBound);
  }

  private static void requirePositiveFinite(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and > 0");
    }
  }
}
