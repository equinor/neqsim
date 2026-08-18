package neqsim.process.equipment.pipeline;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Reusable, scale-aware comparison metrics for two-fluid benchmark and acceptance suites. */
public final class TwoFluidBenchmarkMetrics {
  private TwoFluidBenchmarkMetrics() {
  }

  /** Fit the least-squares log-log exponent in {@code response = constant * rate^n}. */
  public static double fitRateExponent(double[] rates, double[] responses) {
    requireSameLength(rates, responses, 2);
    double meanX = 0.0;
    double meanY = 0.0;
    for (int index = 0; index < rates.length; index++) {
      requirePositiveFinite(rates[index], "Rate");
      requirePositiveFinite(responses[index], "Response");
      meanX += Math.log(rates[index]);
      meanY += Math.log(responses[index]);
    }
    meanX /= rates.length;
    meanY /= rates.length;
    double covariance = 0.0;
    double variance = 0.0;
    for (int index = 0; index < rates.length; index++) {
      double dx = Math.log(rates[index]) - meanX;
      covariance += dx * (Math.log(responses[index]) - meanY);
      variance += dx * dx;
    }
    if (!(variance > 0.0)) {
      throw new IllegalArgumentException("Rate sweep must contain at least two distinct rates");
    }
    return covariance / variance;
  }

  /** Return maximum value divided by the sample median of a positive profile. */
  public static double maximumToMedianRatio(double[] profile) {
    requireFiniteValues(profile, 1, "Profile");
    double maximum = -Double.MAX_VALUE;
    for (double value : profile) {
      if (value < 0.0) {
        throw new IllegalArgumentException("Profile values must be non-negative");
      }
      maximum = Math.max(maximum, value);
    }
    double median = percentile(profile, 0.5);
    if (!(median > 0.0)) {
      throw new IllegalArgumentException("Profile median must be positive");
    }
    return maximum / median;
  }

  /** Symmetric relative spread using the larger absolute result as scale. */
  public static double relativeMeshSpread(double first, double second) {
    if (!Double.isFinite(first) || !Double.isFinite(second)) {
      throw new IllegalArgumentException("Mesh results must be finite");
    }
    double scale = Math.max(Math.abs(first), Math.abs(second));
    return scale == 0.0 ? 0.0 : Math.abs(first - second) / scale;
  }

  /**
   * Analyze a settled signal with robust P10-P90 amplitude and completed median-upcrossing cycles.
   *
   * @param timeSeconds strictly increasing sample times
   * @param signal sampled pressure, flow, or holdup signal
   * @param settledWindowStartSeconds first time included in the settled window
   * @return immutable limit-cycle metrics
   */
  public static LimitCycleMetrics analyzeLimitCycle(double[] timeSeconds, double[] signal,
      double settledWindowStartSeconds) {
    requireSameLength(timeSeconds, signal, 3);
    if (!Double.isFinite(settledWindowStartSeconds)) {
      throw new IllegalArgumentException("Settled-window start must be finite");
    }
    List<Double> selectedTimes = new ArrayList<Double>();
    List<Double> selectedSignal = new ArrayList<Double>();
    double previousTime = -Double.MAX_VALUE;
    for (int index = 0; index < timeSeconds.length; index++) {
      double time = timeSeconds[index];
      double value = signal[index];
      if (!Double.isFinite(time) || !Double.isFinite(value) || time <= previousTime) {
        throw new IllegalArgumentException("Times must be finite and strictly increasing; signal must be finite");
      }
      previousTime = time;
      if (time >= settledWindowStartSeconds) {
        selectedTimes.add(time);
        selectedSignal.add(value);
      }
    }
    if (selectedTimes.size() < 3) {
      throw new IllegalArgumentException("Settled window must contain at least three samples");
    }

    double[] values = new double[selectedSignal.size()];
    for (int index = 0; index < values.length; index++) {
      values[index] = selectedSignal.get(index);
    }
    double p10 = percentile(values, 0.10);
    double median = percentile(values, 0.50);
    double p90 = percentile(values, 0.90);
    double robustBand = p90 - p10;

    List<Double> crossingTimes = new ArrayList<Double>();
    if (robustBand > Math.ulp(Math.max(1.0, Math.abs(median)))) {
      for (int index = 1; index < values.length; index++) {
        double before = values[index - 1] - median;
        double after = values[index] - median;
        if (before < 0.0 && after >= 0.0 && after > before) {
          double fraction = -before / (after - before);
          double crossing = selectedTimes.get(index - 1)
              + fraction * (selectedTimes.get(index) - selectedTimes.get(index - 1));
          crossingTimes.add(crossing);
        }
      }
    }
    int completedCycles = Math.max(0, crossingTimes.size() - 1);
    double period = Double.NaN;
    if (completedCycles > 0) {
      double[] intervals = new double[completedCycles];
      for (int index = 0; index < intervals.length; index++) {
        intervals[index] = crossingTimes.get(index + 1) - crossingTimes.get(index);
      }
      period = percentile(intervals, 0.5);
    }
    return new LimitCycleMetrics(p10, median, p90, period, completedCycles, values.length, selectedTimes.get(0),
        selectedTimes.get(selectedTimes.size() - 1));
  }

  private static double percentile(double[] values, double fraction) {
    double[] sorted = Arrays.copyOf(values, values.length);
    Arrays.sort(sorted);
    double position = fraction * (sorted.length - 1);
    int lower = (int) Math.floor(position);
    int upper = (int) Math.ceil(position);
    if (lower == upper) {
      return sorted[lower];
    }
    double weight = position - lower;
    return sorted[lower] * (1.0 - weight) + sorted[upper] * weight;
  }

  private static void requireSameLength(double[] first, double[] second, int minimumLength) {
    requireFiniteValues(first, minimumLength, "First array");
    requireFiniteValues(second, minimumLength, "Second array");
    if (first.length != second.length) {
      throw new IllegalArgumentException("Arrays must have the same length");
    }
  }

  private static void requireFiniteValues(double[] values, int minimumLength, String label) {
    if (values == null || values.length < minimumLength) {
      throw new IllegalArgumentException(label + " must contain at least " + minimumLength + " values");
    }
    for (double value : values) {
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException(label + " values must be finite");
      }
    }
  }

  private static void requirePositiveFinite(double value, String label) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(label + " must be positive and finite");
    }
  }

  /** Immutable transient limit-cycle summary. */
  public static final class LimitCycleMetrics implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final double p10;
    private final double median;
    private final double p90;
    private final double periodSeconds;
    private final int completedCycleCount;
    private final int sampleCount;
    private final double windowStartSeconds;
    private final double windowEndSeconds;

    private LimitCycleMetrics(double p10, double median, double p90, double periodSeconds, int completedCycleCount,
        int sampleCount, double windowStartSeconds, double windowEndSeconds) {
      this.p10 = p10;
      this.median = median;
      this.p90 = p90;
      this.periodSeconds = periodSeconds;
      this.completedCycleCount = completedCycleCount;
      this.sampleCount = sampleCount;
      this.windowStartSeconds = windowStartSeconds;
      this.windowEndSeconds = windowEndSeconds;
    }

    public double getP10() {
      return p10;
    }

    public double getMedian() {
      return median;
    }

    public double getP90() {
      return p90;
    }

    public double getP10ToP90Band() {
      return p90 - p10;
    }

    public double getPeriodSeconds() {
      return periodSeconds;
    }

    public int getCompletedCycleCount() {
      return completedCycleCount;
    }

    public int getSampleCount() {
      return sampleCount;
    }

    public double getWindowStartSeconds() {
      return windowStartSeconds;
    }

    public double getWindowEndSeconds() {
      return windowEndSeconds;
    }

    public boolean hasRepeatedCycle() {
      return completedCycleCount > 0 && Double.isFinite(periodSeconds);
    }
  }
}
