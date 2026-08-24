package neqsim.thermodynamicoperations.flashops.saturationops;

import java.io.Serializable;
import neqsim.util.unit.TemperatureUnit;

/**
 * Immutable outcome of a freezing-point temperature flash.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class FreezingPointResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private final boolean converged;
  private final double temperatureK;
  private final int iterations;
  private final double residual;
  private final String componentName;
  private final String failureReason;

  /**
   * Creates a freezing-point result.
   *
   * @param converged whether the calculation converged
   * @param temperatureK converged temperature in kelvin, or {@link Double#NaN} on failure
   * @param iterations total solver iterations
   * @param residual final dimensionless residual, or {@link Double#NaN} when unavailable
   * @param componentName component controlling the freezing point, or an empty string
   * @param failureReason failure explanation, or an empty string on success
   */
  private FreezingPointResult(boolean converged, double temperatureK, int iterations, double residual,
      String componentName, String failureReason) {
    this.converged = converged;
    this.temperatureK = temperatureK;
    this.iterations = iterations;
    this.residual = residual;
    this.componentName = componentName;
    this.failureReason = failureReason;
  }

  /**
   * Creates a converged result.
   *
   * @param temperatureK converged temperature in kelvin
   * @param iterations total solver iterations
   * @param residual final dimensionless residual
   * @param componentName component controlling the freezing point
   * @return converged result
   */
  public static FreezingPointResult converged(double temperatureK, int iterations, double residual,
      String componentName) {
    return new FreezingPointResult(true, temperatureK, iterations, residual, componentName, "");
  }

  /**
   * Creates a failed result.
   *
   * @param iterations total solver iterations
   * @param residual final dimensionless residual, or {@link Double#NaN} when unavailable
   * @param componentName component being evaluated, or an empty string
   * @param failureReason explanation of the failure
   * @return failed result
   */
  public static FreezingPointResult failed(int iterations, double residual, String componentName,
      String failureReason) {
    return new FreezingPointResult(false, Double.NaN, iterations, residual, componentName, failureReason);
  }

  /**
   * Returns whether the calculation converged.
   *
   * @return {@code true} for a converged physical result
   */
  public boolean isConverged() {
    return converged;
  }

  /**
   * Returns the converged freezing temperature.
   *
   * @param unit requested temperature unit, for example {@code K} or {@code C}
   * @return freezing temperature in the requested unit
   * @throws IllegalStateException if the calculation did not converge
   */
  public double getTemperature(String unit) {
    if (!converged) {
      throw new IllegalStateException("Freezing-point temperature is unavailable: " + failureReason);
    }
    return new TemperatureUnit(temperatureK, "K").getValue(unit);
  }

  /**
   * Returns the total number of solver iterations.
   *
   * @return iteration count
   */
  public int getIterations() {
    return iterations;
  }

  /**
   * Returns the final dimensionless equilibrium residual.
   *
   * @return residual, or {@link Double#NaN} when unavailable
   */
  public double getResidual() {
    return residual;
  }

  /**
   * Returns the component controlling the reported freezing point.
   *
   * @return component name, or an empty string when unavailable
   */
  public String getComponentName() {
    return componentName;
  }

  /**
   * Returns the failure explanation.
   *
   * @return failure reason, or an empty string on success
   */
  public String getFailureReason() {
    return failureReason;
  }
}