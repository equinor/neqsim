package neqsim.process.envelope;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks an {@link OperatingMargin} over time and predicts when a limit will be breached.
 *
 * <p>
 * The MarginTracker maintains a circular buffer of historical margin readings and fits a linear
 * trend to estimate the rate of change. When the margin is decreasing, it calculates the estimated
 * time until the limit is breached (time-to-trip).
 * </p>
 *
 * <p>
 * Features:
 * </p>
 * <ul>
 * <li>Configurable history window size (default 60 samples)</li>
 * <li>Linear trend fitting via least-squares regression</li>
 * <li>Time-to-breach prediction with acceleration detection</li>
 * <li>Trend direction classification (IMPROVING, STABLE, DEGRADING, RAPIDLY_DEGRADING)</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * OperatingMargin margin = new OperatingMargin("Compressor", "surgeMargin", ...);
 * MarginTracker tracker = new MarginTracker(margin, 120); // 120-sample window
 *
 * // In a monitoring loop:
 * for (int t = 0; t &lt; 100; t++) {
 *   margin.updateCurrentValue(getCurrentSurgeMargin());
 *   tracker.recordSample(t * 60.0); // timestamp in seconds
 * }
 *
 * double timeToTrip = tracker.getTimeToBreachSeconds(); // seconds until limit hit
 * MarginTracker.TrendDirection trend = tracker.getTrendDirection();
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class MarginTracker implements Serializable {
  private static final long serialVersionUID = 1L;

  /**
   * Classification of the margin trend direction.
   */
  public enum TrendDirection {
    /** Margin is increasing (moving away from limit). */
    IMPROVING,
    /** Margin is approximately stable (rate near zero). */
    STABLE,
    /** Margin is decreasing (approaching limit). */
    DEGRADING,
    /** Margin is decreasing rapidly (high rate of change). */
    RAPIDLY_DEGRADING,
    /** Insufficient data to determine trend. */
    UNKNOWN
  }

  /** Default number of samples in the circular buffer. */
  private static final int DEFAULT_WINDOW_SIZE = 60;
  /** Rate threshold for STABLE classification (fraction per second). */
  private static final double STABLE_RATE_THRESHOLD = 1e-6;
  /** Multiplier for RAPIDLY_DEGRADING threshold. */
  private static final double RAPID_DEGRADATION_MULTIPLIER = 5.0;

  private final OperatingMargin margin;
  private final int windowSize;
  private final double[] marginHistory;
  private final double[] timestampHistory;
  private int historyCount;
  private int writeIndex;

  private double trendSlope;
  private double trendIntercept;
  private double trendRSquared;
  private boolean trendValid;

  /**
   * Creates a MarginTracker with default window size (60 samples).
   *
   * @param margin the operating margin to track
   */
  public MarginTracker(OperatingMargin margin) {
    this(margin, DEFAULT_WINDOW_SIZE);
  }

  /**
   * Creates a MarginTracker with specified window size.
   *
   * @param margin the operating margin to track
   * @param windowSize maximum number of samples to retain (must be at least 2)
   */
  public MarginTracker(OperatingMargin margin, int windowSize) {
    if (windowSize < 2) {
      throw new IllegalArgumentException("Window size must be at least 2, got: " + windowSize);
    }
    this.margin = margin;
    this.windowSize = windowSize;
    this.marginHistory = new double[windowSize];
    this.timestampHistory = new double[windowSize];
    this.historyCount = 0;
    this.writeIndex = 0;
    this.trendSlope = 0.0;
    this.trendIntercept = 0.0;
    this.trendRSquared = 0.0;
    this.trendValid = false;
  }

  /**
   * Records the current margin value at the given timestamp.
   *
   * <p>
   * After recording, the linear trend is automatically refit using all available samples in the
   * window.
   * </p>
   *
   * @param timestampSeconds time in seconds (monotonically increasing)
   */
  public void recordSample(double timestampSeconds) {
    marginHistory[writeIndex] = margin.getMarginFraction();
    timestampHistory[writeIndex] = timestampSeconds;
    writeIndex = (writeIndex + 1) % windowSize;
    if (historyCount < windowSize) {
      historyCount++;
    }
    fitTrend();
  }

  /**
   * Fits a linear regression to the margin history: margin(t) = slope * t + intercept.
   */
  private void fitTrend() {
    if (historyCount < 2) {
      trendValid = false;
      return;
    }

    double sumT = 0.0;
    double sumM = 0.0;
    double sumTT = 0.0;
    double sumTM = 0.0;
    int n = historyCount;

    for (int i = 0; i < n; i++) {
      int idx = (writeIndex - n + i + windowSize) % windowSize;
      double t = timestampHistory[idx];
      double m = marginHistory[idx];
      sumT += t;
      sumM += m;
      sumTT += t * t;
      sumTM += t * m;
    }

    double denominator = n * sumTT - sumT * sumT;
    if (Math.abs(denominator) < 1e-30) {
      trendValid = false;
      return;
    }

    trendSlope = (n * sumTM - sumT * sumM) / denominator;
    trendIntercept = (sumM - trendSlope * sumT) / n;
    trendValid = true;

    // Calculate R-squared
    double meanM = sumM / n;
    double ssTot = 0.0;
    double ssRes = 0.0;
    for (int i = 0; i < n; i++) {
      int idx = (writeIndex - n + i + windowSize) % windowSize;
      double predicted = trendSlope * timestampHistory[idx] + trendIntercept;
      ssRes += (marginHistory[idx] - predicted) * (marginHistory[idx] - predicted);
      ssTot += (marginHistory[idx] - meanM) * (marginHistory[idx] - meanM);
    }
    trendRSquared = (Math.abs(ssTot) < 1e-30) ? 1.0 : (1.0 - ssRes / ssTot);
  }

  /**
   * Returns the estimated time in seconds until the margin reaches zero (limit breach).
   *
   * <p>
   * Returns {@link Double#POSITIVE_INFINITY} if the trend is stable or improving, or if there is
   * insufficient data. Returns 0.0 if the limit is already violated.
   * </p>
   *
   * @return estimated seconds until breach, or POSITIVE_INFINITY if no breach predicted
   */
  public double getTimeToBreachSeconds() {
    if (!trendValid || historyCount < 2) {
      return Double.POSITIVE_INFINITY;
    }

    double currentMargin = margin.getMarginFraction();
    if (currentMargin <= 0.0) {
      return 0.0;
    }

    if (trendSlope >= -STABLE_RATE_THRESHOLD) {
      return Double.POSITIVE_INFINITY;
    }

    double latestTimestamp = getLatestTimestamp();
    // margin(t) = slope * t + intercept = 0 => t_breach = -intercept / slope
    double tBreach = -trendIntercept / trendSlope;
    double timeRemaining = tBreach - latestTimestamp;

    return Math.max(0.0, timeRemaining);
  }

  /**
   * Returns the estimated time to breach in minutes.
   *
   * @return estimated minutes until breach
   */
  public double getTimeToBreachMinutes() {
    return getTimeToBreachSeconds() / 60.0;
  }

  /**
   * Returns the trend direction classification.
   *
   * @return trend direction enum value
   */
  public TrendDirection getTrendDirection() {
    if (!trendValid || historyCount < 3) {
      return TrendDirection.UNKNOWN;
    }

    if (trendSlope > STABLE_RATE_THRESHOLD) {
      return TrendDirection.IMPROVING;
    } else if (trendSlope >= -STABLE_RATE_THRESHOLD) {
      return TrendDirection.STABLE;
    } else if (trendSlope >= -STABLE_RATE_THRESHOLD * RAPID_DEGRADATION_MULTIPLIER) {
      return TrendDirection.DEGRADING;
    } else {
      return TrendDirection.RAPIDLY_DEGRADING;
    }
  }

  /**
   * Returns the rate of margin change (fraction per second).
   *
   * <p>
   * Negative values indicate the margin is shrinking.
   * </p>
   *
   * @return slope of the linear trend (fraction/second)
   */
  public double getMarginRateOfChange() {
    return trendValid ? trendSlope : 0.0;
  }

  /**
   * Returns the R-squared value of the linear trend fit.
   *
   * <p>
   * Values close to 1.0 indicate a strong linear trend. Low values suggest noise or nonlinear
   * behavior.
   * </p>
   *
   * @return R-squared value (0.0 to 1.0)
   */
  public double getTrendRSquared() {
    return trendRSquared;
  }

  /**
   * Returns whether the trend calculation is valid (requires at least 2 samples).
   *
   * @return true if trend data is available
   */
  public boolean isTrendValid() {
    return trendValid;
  }

  /**
   * Returns the number of samples currently in the history buffer.
   *
   * @return sample count
   */
  public int getSampleCount() {
    return historyCount;
  }

  /**
   * Returns the underlying operating margin being tracked.
   *
   * @return the operating margin
   */
  public OperatingMargin getMargin() {
    return margin;
  }

  /**
   * Returns the configured window size.
   *
   * @return maximum number of samples in the circular buffer
   */
  public int getWindowSize() {
    return windowSize;
  }

  /**
   * Returns a copy of the margin fraction history (oldest first).
   *
   * @return list of historical margin fraction values
   */
  public List<Double> getMarginHistory() {
    List<Double> result = new ArrayList<Double>();
    int n = historyCount;
    for (int i = 0; i < n; i++) {
      int idx = (writeIndex - n + i + windowSize) % windowSize;
      result.add(marginHistory[idx]);
    }
    return result;
  }

  /**
   * Returns a copy of the timestamp history (oldest first).
   *
   * @return list of historical timestamp values in seconds
   */
  public List<Double> getTimestampHistory() {
    List<Double> result = new ArrayList<Double>();
    int n = historyCount;
    for (int i = 0; i < n; i++) {
      int idx = (writeIndex - n + i + windowSize) % windowSize;
      result.add(timestampHistory[idx]);
    }
    return result;
  }

  /**
   * Returns the most recent timestamp in the history.
   *
   * @return latest timestamp in seconds, or 0.0 if no samples recorded
   */
  private double getLatestTimestamp() {
    if (historyCount == 0) {
      return 0.0;
    }
    int latestIdx = (writeIndex - 1 + windowSize) % windowSize;
    return timestampHistory[latestIdx];
  }

  /**
   * Clears all recorded history and resets the trend calculation.
   */
  public void reset() {
    historyCount = 0;
    writeIndex = 0;
    trendSlope = 0.0;
    trendIntercept = 0.0;
    trendRSquared = 0.0;
    trendValid = false;
  }

  /**
   * Returns a summary string including the current margin, trend, and time-to-breach.
   *
   * @return formatted summary
   */
  @Override
  public String toString() {
    double ttb = getTimeToBreachMinutes();
    String ttbStr = Double.isInfinite(ttb) ? "N/A" : String.format("%.1f min", ttb);
    return String.format("MarginTracker[%s | trend: %s | rate: %.2e/s | TTB: %s | samples: %d]",
        margin.getKey(), getTrendDirection(), getMarginRateOfChange(), ttbStr, historyCount);
  }
}
