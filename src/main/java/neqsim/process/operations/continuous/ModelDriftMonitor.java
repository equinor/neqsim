package neqsim.process.operations.continuous;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.JsonObject;

/**
 * EWMA and two-sided CUSUM drift monitor for named scalar signals with a frozen warm-up baseline.
 *
 * <p>
 * The first {@code warmup} values of each signal fix its baseline mean and standard deviation. After that, each value
 * updates an exponentially weighted moving average (started at the baseline mean) and a standardised two-sided CUSUM.
 * An alarm needs {@code confirm} consecutive out-of-control values. An engineering floor on the standard deviation
 * ({@code minSigma}) suppresses shifts that are statistically real but too small to be worth a review; without it a
 * short warm-up underestimates the noise and white noise alone raises false alarms within months.
 * </p>
 *
 * <p>
 * This is the Java counterpart of the {@code neqsim_continuous.drift.DriftMonitor} used by living tasks, so a Java or
 * MCP client gets identical alarms from identical data.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ModelDriftMonitor implements Serializable {
  private static final long serialVersionUID = 1L;

  private double lambda = 0.2;
  private double limitWidth = 3.5;
  private double cusumSlack = 0.5;
  private double cusumThreshold = 6.0;
  private int warmup = 30;
  private int confirm = 2;
  private final Map<String, Double> minSigma = new LinkedHashMap<String, Double>();
  private final Map<String, SignalState> signals = new LinkedHashMap<String, SignalState>();

  /**
   * State of one monitored signal.
   *
   * @author ESOL
   * @version 1.0
   */
  public static class SignalState implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Number of warm-up values seen. */
    int count;
    /** Baseline mean. */
    double mean;
    /** Sum of squared deviations (Welford). */
    double m2;
    /** EWMA statistic, NaN until the first monitored value. */
    double ewma = Double.NaN;
    /** Upper CUSUM. */
    double cusumPos;
    /** Lower CUSUM. */
    double cusumNeg;
    /** Consecutive out-of-control values. */
    int streak;
    /** Latched alarm. */
    boolean alarm;

    /**
     * Returns the baseline mean.
     *
     * @return baseline mean of the warm-up values
     */
    public double getMean() {
      return mean;
    }

    /**
     * Returns whether the alarm is latched.
     *
     * @return true after the first confirmed alarm until {@link ModelDriftMonitor#reset(String)}
     */
    public boolean isAlarm() {
      return alarm;
    }
  }

  /**
   * Creates a monitor with the default settings (lambda 0.2, L 3.5, k 0.5, h 6, warm-up 30, confirm 2).
   */
  public ModelDriftMonitor() {
  }

  /**
   * Sets the EWMA smoothing factor.
   *
   * @param lambda smoothing factor in (0, 1]
   * @return this monitor
   * @throws IllegalArgumentException if {@code lambda} is outside (0, 1]
   */
  public ModelDriftMonitor setLambda(double lambda) {
    if (!(lambda > 0.0 && lambda <= 1.0)) {
      throw new IllegalArgumentException("lambda must be in (0, 1], was " + lambda);
    }
    this.lambda = lambda;
    return this;
  }

  /**
   * Sets the EWMA control-limit width in standard deviations.
   *
   * @param width limit width L, positive
   * @return this monitor
   */
  public ModelDriftMonitor setLimitWidth(double width) {
    this.limitWidth = width;
    return this;
  }

  /**
   * Sets the CUSUM slack and decision threshold, both in standard deviations.
   *
   * @param slack slack k, non-negative
   * @param threshold decision threshold h, positive
   * @return this monitor
   */
  public ModelDriftMonitor setCusum(double slack, double threshold) {
    this.cusumSlack = slack;
    this.cusumThreshold = threshold;
    return this;
  }

  /**
   * Sets the warm-up length and the number of consecutive out-of-control values needed to alarm.
   *
   * @param warmupValues number of values that fix the baseline, at least 2
   * @param confirmValues consecutive out-of-control values required, at least 1
   * @return this monitor
   * @throws IllegalArgumentException if {@code warmupValues} is below 2 or {@code confirmValues} below 1
   */
  public ModelDriftMonitor setWarmup(int warmupValues, int confirmValues) {
    if (warmupValues < 2 || confirmValues < 1) {
      throw new IllegalArgumentException("warmup must be >= 2 and confirm >= 1");
    }
    this.warmup = warmupValues;
    this.confirm = confirmValues;
    return this;
  }

  /**
   * Sets an engineering floor on the standard deviation of one signal.
   *
   * @param signal signal name
   * @param sigma smallest standard deviation used for the signal, in the signal's unit
   * @return this monitor
   */
  public ModelDriftMonitor setMinSigma(String signal, double sigma) {
    minSigma.put(signal, sigma);
    return this;
  }

  /**
   * Feeds one value of a signal.
   *
   * @param signal signal name
   * @param value new value; NaN or infinite values are ignored
   * @return JSON status with phase, EWMA, CUSUM, {@code alarm} and {@code newAlarm}
   */
  public JsonObject update(String signal, double value) {
    JsonObject status = new JsonObject();
    status.addProperty("name", signal);
    status.addProperty("value", value);
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      status.addProperty("phase", "ignored");
      status.addProperty("alarm", false);
      status.addProperty("newAlarm", false);
      return status;
    }
    SignalState s = signals.get(signal);
    if (s == null) {
      s = new SignalState();
      signals.put(signal, s);
    }
    if (s.count < warmup) {
      s.count++;
      double delta = value - s.mean;
      s.mean += delta / s.count;
      s.m2 += delta * (value - s.mean);
      status.addProperty("phase", "warmup");
      status.addProperty("alarm", false);
      status.addProperty("newAlarm", false);
      return status;
    }
    double sigma = Math.sqrt(s.m2 / Math.max(s.count - 1, 1));
    Double floor = minSigma.get(signal);
    if (floor != null) {
      sigma = Math.max(sigma, floor);
    }
    if (sigma <= 0.0) {
      sigma = 1.0e-12;
    }
    double previous = Double.isNaN(s.ewma) ? s.mean : s.ewma;
    s.ewma = lambda * value + (1.0 - lambda) * previous;
    double limit = limitWidth * sigma * Math.sqrt(lambda / (2.0 - lambda));
    double standard = (value - s.mean) / sigma;
    s.cusumPos = Math.max(0.0, s.cusumPos + standard - cusumSlack);
    s.cusumNeg = Math.max(0.0, s.cusumNeg - standard - cusumSlack);
    boolean ewmaAlarm = Math.abs(s.ewma - s.mean) > limit;
    boolean cusumAlarm = Math.max(s.cusumPos, s.cusumNeg) > cusumThreshold;
    s.streak = (ewmaAlarm || cusumAlarm) ? s.streak + 1 : 0;
    boolean alarm = s.streak >= confirm;
    boolean newAlarm = alarm && !s.alarm;
    s.alarm = s.alarm || alarm;
    status.addProperty("phase", "monitor");
    status.addProperty("baselineMean", s.mean);
    status.addProperty("baselineStd", sigma);
    status.addProperty("ewma", s.ewma);
    status.addProperty("ewmaLimit", limit);
    status.addProperty("cusumPos", s.cusumPos);
    status.addProperty("cusumNeg", s.cusumNeg);
    status.addProperty("direction", value > s.mean ? "up" : "down");
    status.addProperty("alarm", alarm);
    status.addProperty("newAlarm", newAlarm);
    return status;
  }

  /**
   * Clears the alarm and the EWMA/CUSUM statistics of a signal, keeping its baseline.
   *
   * @param signal signal name
   */
  public void reset(String signal) {
    SignalState s = signals.get(signal);
    if (s != null) {
      s.ewma = Double.NaN;
      s.cusumPos = 0.0;
      s.cusumNeg = 0.0;
      s.streak = 0;
      s.alarm = false;
    }
  }

  /**
   * Returns the state of one signal.
   *
   * @param signal signal name
   * @return the state, or null if the signal has not been seen
   */
  public SignalState getState(String signal) {
    return signals.get(signal);
  }
}
