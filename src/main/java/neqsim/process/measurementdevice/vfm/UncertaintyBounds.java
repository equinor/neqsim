package neqsim.process.measurementdevice.vfm;

import java.io.Serializable;

/**
 * Represents uncertainty bounds for a measured or calculated value.
 *
 * <p>
 * This class provides confidence intervals and standard deviation for values calculated by virtual
 * flow meters and soft sensors, enabling risk-aware decision making in production optimization.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class UncertaintyBounds implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final double mean;
  private final double standardDeviation;
  private final double lower95;
  private final double upper95;
  private final double lower99;
  private final double upper99;
  private final String unit;

  /**
   * Creates uncertainty bounds from mean and standard deviation assuming normal distribution.
   *
   * @param mean the mean value
   * @param standardDeviation the standard deviation
   * @param unit the engineering unit
   */
  public UncertaintyBounds(double mean, double standardDeviation, String unit) {
    this.mean = mean;
    this.standardDeviation = standardDeviation;
    this.unit = unit;
    // 95% confidence interval (1.96 * sigma)
    this.lower95 = mean - 1.96 * standardDeviation;
    this.upper95 = mean + 1.96 * standardDeviation;
    // 99% confidence interval (2.576 * sigma)
    this.lower99 = mean - 2.576 * standardDeviation;
    this.upper99 = mean + 2.576 * standardDeviation;
  }

  /**
   * Creates uncertainty bounds with explicit confidence intervals.
   *
   * @param mean the mean value
   * @param standardDeviation the standard deviation
   * @param lower95 lower bound of 95% CI
   * @param upper95 upper bound of 95% CI
   * @param lower99 lower bound of 99% CI
   * @param upper99 upper bound of 99% CI
   * @param unit the engineering unit
   */
  public UncertaintyBounds(double mean, double standardDeviation, double lower95, double upper95,
      double lower99, double upper99, String unit) {
    this.mean = mean;
    this.standardDeviation = standardDeviation;
    this.lower95 = lower95;
    this.upper95 = upper95;
    this.lower99 = lower99;
    this.upper99 = upper99;
    this.unit = unit;
  }

  /**
   * Gets the mean value.
   *
   * @return the mean
   */
  public double getMean() {
    return mean;
  }

  /**
   * Gets the standard deviation.
   *
   * @return the standard deviation
   */
  public double getStandardDeviation() {
    return standardDeviation;
  }

  /**
   * Gets the lower bound of the 95% confidence interval.
   *
   * @return lower 95% bound
   */
  public double getLower95() {
    return lower95;
  }

  /**
   * Gets the upper bound of the 95% confidence interval.
   *
   * @return upper 95% bound
   */
  public double getUpper95() {
    return upper95;
  }

  /**
   * Gets the lower bound of the 99% confidence interval.
   *
   * @return lower 99% bound
   */
  public double getLower99() {
    return lower99;
  }

  /**
   * Gets the upper bound of the 99% confidence interval.
   *
   * @return upper 99% bound
   */
  public double getUpper99() {
    return upper99;
  }

  /**
   * Gets the engineering unit.
   *
   * @return the unit
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Gets the coefficient of variation (relative uncertainty).
   *
   * @return CV as a ratio (not percentage)
   */
  public double getCoefficientOfVariation() {
    if (Math.abs(mean) < 1e-10) {
      return Double.NaN;
    }
    return standardDeviation / Math.abs(mean);
  }

  /**
   * Gets the relative uncertainty as a percentage.
   *
   * @return uncertainty percentage
   */
  public double getRelativeUncertaintyPercent() {
    return getCoefficientOfVariation() * 100.0;
  }

  /**
   * Checks if a value is within the 95% confidence interval.
   *
   * @param value the value to check
   * @return true if within 95% CI
   */
  public boolean isWithin95CI(double value) {
    return value >= lower95 && value <= upper95;
  }

  /**
   * Checks if a value is within the 99% confidence interval.
   *
   * @param value the value to check
   * @return true if within 99% CI
   */
  public boolean isWithin99CI(double value) {
    return value >= lower99 && value <= upper99;
  }

  /**
   * Combines two independent uncertainty bounds using error propagation for addition.
   *
   * @param other the other uncertainty bounds
   * @return combined uncertainty bounds
   */
  public UncertaintyBounds add(UncertaintyBounds other) {
    double newMean = this.mean + other.mean;
    double newStd = Math.sqrt(this.standardDeviation * this.standardDeviation
        + other.standardDeviation * other.standardDeviation);
    return new UncertaintyBounds(newMean, newStd, this.unit);
  }

  /**
   * Scales the uncertainty bounds by a constant factor.
   *
   * @param factor the scaling factor
   * @return scaled uncertainty bounds
   */
  public UncertaintyBounds scale(double factor) {
    return new UncertaintyBounds(mean * factor, standardDeviation * Math.abs(factor), unit);
  }

  @Override
  public String toString() {
    return String.format("%.4f ± %.4f %s (95%% CI: [%.4f, %.4f])", mean, standardDeviation, unit,
        lower95, upper95);
  }
}
