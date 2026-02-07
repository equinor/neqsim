package neqsim.process.util.reconciliation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A monitored process variable for steady-state detection.
 *
 * <p>
 * Maintains a sliding window of recent measurements and computes statistics used by
 * {@link SteadyStateDetector} to determine whether the variable is at steady state. The key
 * statistics are:
 * </p>
 * <ul>
 * <li>Mean and standard deviation of the window</li>
 * <li>R-statistic: ratio of filtered variance to unfiltered variance</li>
 * <li>Slope from linear regression over the window</li>
 * <li>Whether the variable is at steady state according to configurable thresholds</li>
 * </ul>
 *
 * <p>
 * Values are added one at a time via {@link #addValue(double)}. The window size and all thresholds
 * are set on the parent {@link SteadyStateDetector}.
 * </p>
 *
 * @author Process Optimization Team
 * @version 1.0
 */
public class SteadyStateVariable implements java.io.Serializable {

  private static final long serialVersionUID = 1L;

  /** Variable name (tag identifier). */
  private final String name;

  /** Engineering unit string (e.g., "kg/hr", "bara", "C"). */
  private String unit = "";

  /** Sliding window of recent values. */
  private final List<Double> window;

  /** Maximum window size. */
  private int windowSize;

  /** Current mean of the window. */
  private double mean;

  /** Current standard deviation of the window. */
  private double standardDeviation;

  /**
   * R-statistic: ratio of filtered variance to unfiltered variance. A value near 1.0 indicates
   * steady state; lower values indicate transient behaviour.
   */
  private double rStatistic;

  /**
   * Slope of linear regression through the window values. Near zero indicates steady state;
   * positive or negative values indicate a trend.
   */
  private double slope;

  /** Whether this variable is currently at steady state. */
  private boolean atSteadyState;

  /** Optional measurement uncertainty (sigma) for linking to reconciliation. */
  private double uncertainty = Double.NaN;

  /**
   * Creates a steady-state monitoring variable.
   *
   * @param name variable identifier (e.g., DCS tag name)
   * @param windowSize maximum number of recent values to retain
   */
  public SteadyStateVariable(String name, int windowSize) {
    if (windowSize < 3) {
      throw new IllegalArgumentException("Window size must be at least 3, got: " + windowSize);
    }
    this.name = name;
    this.windowSize = windowSize;
    this.window = new ArrayList<Double>(windowSize);
    this.atSteadyState = false;
  }

  /**
   * Adds a new measurement value to the sliding window.
   *
   * <p>
   * If the window exceeds its maximum size, the oldest value is removed. After adding, the mean,
   * standard deviation, R-statistic, and slope are recalculated.
   * </p>
   *
   * @param value the new measurement reading
   */
  public void addValue(double value) {
    window.add(value);
    while (window.size() > windowSize) {
      window.remove(0);
    }
    computeStatistics();
  }

  /**
   * Computes all statistics from the current window contents.
   *
   * <p>
   * Calculates: mean, standard deviation, R-statistic (filtered/unfiltered variance ratio), and
   * slope from linear regression.
   * </p>
   */
  private void computeStatistics() {
    int n = window.size();
    if (n < 2) {
      mean = n == 1 ? window.get(0) : 0.0;
      standardDeviation = 0.0;
      rStatistic = 1.0;
      slope = 0.0;
      return;
    }

    // Mean
    double sum = 0.0;
    for (Double v : window) {
      sum += v;
    }
    mean = sum / n;

    // Standard deviation
    double ssq = 0.0;
    for (Double v : window) {
      double d = v - mean;
      ssq += d * d;
    }
    standardDeviation = Math.sqrt(ssq / (n - 1));

    // R-statistic: ratio of filtered (successive difference) to unfiltered variance
    // Unfiltered variance = (1/(n-1)) * sum((xi - mean)^2)
    double unfilteredVar = ssq / (n - 1);

    // Filtered variance = (1/(2*(n-1))) * sum((x[i] - x[i-1])^2)
    double filteredSum = 0.0;
    for (int i = 1; i < n; i++) {
      double diff = window.get(i) - window.get(i - 1);
      filteredSum += diff * diff;
    }
    double filteredVar = filteredSum / (2.0 * (n - 1));

    // R = filtered / unfiltered; near 1.0 = steady state, << 1.0 = trend/ramp
    if (unfilteredVar > 1e-30) {
      rStatistic = filteredVar / unfilteredVar;
    } else {
      // All values effectively identical — perfectly steady
      rStatistic = 1.0;
    }

    // Slope via linear regression: y = a + b*x where x = 0,1,...,n-1
    // b = (n*sum(xi*yi) - sum(xi)*sum(yi)) / (n*sum(xi^2) - (sum(xi))^2)
    double sumX = 0.0;
    double sumXX = 0.0;
    double sumXY = 0.0;
    for (int i = 0; i < n; i++) {
      double x = i;
      double y = window.get(i);
      sumX += x;
      sumXX += x * x;
      sumXY += x * y;
    }
    double denominator = n * sumXX - sumX * sumX;
    if (Math.abs(denominator) > 1e-30) {
      slope = (n * sumXY - sumX * sum) / denominator;
    } else {
      slope = 0.0;
    }
  }

  /**
   * Returns the variable name.
   *
   * @return the tag name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the engineering unit string.
   *
   * @return unit string
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Sets the engineering unit string.
   *
   * @param unit the unit (e.g., "kg/hr")
   * @return this variable for chaining
   */
  public SteadyStateVariable setUnit(String unit) {
    this.unit = unit;
    return this;
  }

  /**
   * Returns the current number of values in the window.
   *
   * @return count of values in the sliding window
   */
  public int getCount() {
    return window.size();
  }

  /**
   * Returns the maximum window size.
   *
   * @return the configured window size
   */
  public int getWindowSize() {
    return windowSize;
  }

  /**
   * Sets the maximum window size.
   *
   * @param windowSize new window size (must be at least 3)
   * @throws IllegalArgumentException if windowSize is less than 3
   */
  public void setWindowSize(int windowSize) {
    if (windowSize < 3) {
      throw new IllegalArgumentException("Window size must be at least 3, got: " + windowSize);
    }
    this.windowSize = windowSize;
    while (window.size() > windowSize) {
      window.remove(0);
    }
    if (window.size() >= 2) {
      computeStatistics();
    }
  }

  /**
   * Returns whether the window is full.
   *
   * @return true if the number of values equals the window size
   */
  public boolean isWindowFull() {
    return window.size() >= windowSize;
  }

  /**
   * Returns the mean of the current window.
   *
   * @return arithmetic mean
   */
  public double getMean() {
    return mean;
  }

  /**
   * Returns the standard deviation of the current window.
   *
   * @return sample standard deviation
   */
  public double getStandardDeviation() {
    return standardDeviation;
  }

  /**
   * Returns the R-statistic (filtered/unfiltered variance ratio).
   *
   * <p>
   * The R-statistic compares the variance of successive differences (filtered variance) to the
   * overall sample variance (unfiltered variance). At steady state, both variances are similar and
   * R approaches 1.0. During ramps or trends, the unfiltered variance is much larger, so R drops
   * well below 1.0.
   * </p>
   *
   * @return R-statistic in the range [0, 1+] where ~1 = steady state
   */
  public double getRStatistic() {
    return rStatistic;
  }

  /**
   * Returns the slope from linear regression through the window.
   *
   * <p>
   * Expressed in engineering-units per sample. Divide by the sampling interval to get units per
   * second, per minute, etc.
   * </p>
   *
   * @return regression slope (units per sample)
   */
  public double getSlope() {
    return slope;
  }

  /**
   * Returns the most recent value in the window.
   *
   * @return the last added value, or NaN if empty
   */
  public double getLatestValue() {
    if (window.isEmpty()) {
      return Double.NaN;
    }
    return window.get(window.size() - 1);
  }

  /**
   * Returns the values in the current window.
   *
   * @return unmodifiable list of values
   */
  public List<Double> getWindowValues() {
    return Collections.unmodifiableList(window);
  }

  /**
   * Returns whether this variable is currently at steady state.
   *
   * @return true if at steady state
   */
  public boolean isAtSteadyState() {
    return atSteadyState;
  }

  /**
   * Sets the steady-state flag. Called by {@link SteadyStateDetector}.
   *
   * @param atSteadyState true if at steady state
   */
  void setAtSteadyState(boolean atSteadyState) {
    this.atSteadyState = atSteadyState;
  }

  /**
   * Returns the measurement uncertainty (sigma) for reconciliation.
   *
   * @return uncertainty, or NaN if not set
   */
  public double getUncertainty() {
    return uncertainty;
  }

  /**
   * Sets the measurement uncertainty for linking to the reconciliation engine.
   *
   * @param uncertainty standard deviation (sigma), must be positive
   * @return this variable for chaining
   * @throws IllegalArgumentException if uncertainty is not positive
   */
  public SteadyStateVariable setUncertainty(double uncertainty) {
    if (uncertainty <= 0.0) {
      throw new IllegalArgumentException("Uncertainty must be positive, got: " + uncertainty);
    }
    this.uncertainty = uncertainty;
    return this;
  }

  /**
   * Clears all values from the window and resets statistics.
   */
  public void clear() {
    window.clear();
    mean = 0.0;
    standardDeviation = 0.0;
    rStatistic = 1.0;
    slope = 0.0;
    atSteadyState = false;
  }

  /**
   * Returns a summary string.
   *
   * @return human-readable representation of this variable's SSD status
   */
  @Override
  public String toString() {
    String unitStr = unit.isEmpty() ? "" : " " + unit;
    String ssFlag = atSteadyState ? "SS" : "TRANSIENT";
    return String.format("%s: mean=%.4f%s, std=%.4f, R=%.4f, slope=%.4e [%s] (%d/%d samples)", name,
        mean, unitStr, standardDeviation, rStatistic, slope, ssFlag, window.size(), windowSize);
  }
}
