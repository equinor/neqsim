package neqsim.process.equipment.pipeline.twophasepipe.closure;

import java.io.Serializable;

/**
 * Configurable algebraic bubble-size closure for bubbly-flow momentum-transfer models.
 *
 * <p>
 * The closure estimates a characteristic spherical-bubble diameter from surface tension, continuous/dispersed-phase
 * density difference, and gravity, and limits the result by a configurable fraction of pipe diameter. It is
 * intentionally independent of a particular flow-regime model so callers can supply phase-property values from the
 * thermodynamic state.
 * </p>
 *
 * <p>
 * The default surface tension of {@code 0.02 N/m} and diameter cap of {@code D/5} reproduce the historical TwoFluidPipe
 * assumptions. Setting different values is explicit and does not imply validation outside approximately spherical,
 * liquid-continuous bubbly flow.
 * </p>
 */
public final class BubbleSizeClosure implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double DEFAULT_SURFACE_TENSION = 0.02;
  private static final double DEFAULT_PIPE_DIAMETER_FRACTION = 0.20;

  private double surfaceTension = DEFAULT_SURFACE_TENSION;
  private double maximumPipeDiameterFraction = DEFAULT_PIPE_DIAMETER_FRACTION;

  /** Creates a closure with the historical TwoFluidPipe assumptions. */
  public BubbleSizeClosure() {
  }

  /**
   * Creates a closure with explicit surface tension.
   *
   * @param surfaceTension surface tension in N/m
   */
  public BubbleSizeClosure(double surfaceTension) {
    setSurfaceTension(surfaceTension);
  }

  /**
   * Sets the gas-liquid surface tension used by the bubble-size estimate.
   *
   * @param surfaceTension surface tension in N/m, strictly positive and finite
   */
  public void setSurfaceTension(double surfaceTension) {
    if (!Double.isFinite(surfaceTension) || surfaceTension <= 0.0) {
      throw new IllegalArgumentException("surfaceTension must be finite and > 0 N/m");
    }
    this.surfaceTension = surfaceTension;
  }

  /** @return configured surface tension in N/m */
  public double getSurfaceTension() {
    return surfaceTension;
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
   * Estimates a characteristic spherical-bubble diameter.
   *
   * <p>
   * The uncapped estimate is {@code sqrt(sigma / (g * abs(deltaRho)))}. The returned value is capped at
   * {@code maximumPipeDiameterFraction * pipeDiameter}. This algebraic scale preserves the existing TwoFluidPipe form
   * while making all physical inputs and the geometry cap explicit.
   * </p>
   *
   * @param pipeDiameter internal pipe diameter in m
   * @param continuousPhaseDensity continuous-phase density in kg/m3
   * @param dispersedPhaseDensity dispersed-phase density in kg/m3
   * @param gravity gravitational acceleration magnitude in m/s2
   * @return characteristic bubble diameter in m
   */
  public double estimateDiameter(double pipeDiameter, double continuousPhaseDensity, double dispersedPhaseDensity,
      double gravity) {
    requirePositiveFinite(pipeDiameter, "pipeDiameter");
    requirePositiveFinite(continuousPhaseDensity, "continuousPhaseDensity");
    requirePositiveFinite(dispersedPhaseDensity, "dispersedPhaseDensity");
    requirePositiveFinite(gravity, "gravity");
    double densityDifference = Math.abs(continuousPhaseDensity - dispersedPhaseDensity);
    if (densityDifference == 0.0) {
      return maximumPipeDiameterFraction * pipeDiameter;
    }
    double unconstrained = Math.sqrt(surfaceTension / (gravity * densityDifference));
    return Math.min(unconstrained, maximumPipeDiameterFraction * pipeDiameter);
  }

  private static void requirePositiveFinite(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and > 0");
    }
  }
}
