package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable multivariate process-data window used for evidence-grounded root-cause analysis.
 *
 * <p>
 * A window contains raw engineering values rather than pre-standardized values. A {@link RcaNormalOperationModel} is
 * responsible for matching the window to a normal operating regime and standardizing its deviations. Signal arrays and
 * operating-condition values are defensively copied.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class RcaProcessWindow implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  private final String regimeId;
  private final double sampleIntervalSeconds;
  private final Map<String, Double> operatingConditions;
  private final Map<String, double[]> signals;
  private final int sampleCount;

  private RcaProcessWindow(Builder builder) {
    if (builder.regimeId == null || builder.regimeId.trim().isEmpty()) {
      throw new IllegalArgumentException("regimeId must not be blank");
    }
    if (!Double.isFinite(builder.sampleIntervalSeconds) || builder.sampleIntervalSeconds <= 0.0) {
      throw new IllegalArgumentException("sampleIntervalSeconds must be finite and > 0");
    }
    if (builder.signals.isEmpty()) {
      throw new IllegalArgumentException("at least one signal is required");
    }

    int expectedSamples = -1;
    Map<String, double[]> copiedSignals = new LinkedHashMap<String, double[]>();
    for (Map.Entry<String, double[]> entry : builder.signals.entrySet()) {
      String name = validateName(entry.getKey(), "signal");
      double[] values = entry.getValue();
      if (values == null || values.length < 3) {
        throw new IllegalArgumentException("signal " + name + " must contain at least three samples");
      }
      if (expectedSamples < 0) {
        expectedSamples = values.length;
      } else if (values.length != expectedSamples) {
        throw new IllegalArgumentException("all signals must contain the same number of samples");
      }
      for (int i = 0; i < values.length; i++) {
        if (!Double.isFinite(values[i])) {
          throw new IllegalArgumentException("signal " + name + " contains a non-finite value at index " + i);
        }
      }
      if (copiedSignals.containsKey(name)) {
        throw new IllegalArgumentException("duplicate signal name after normalization: " + name);
      }
      copiedSignals.put(name, values);
    }

    Map<String, Double> copiedConditions = new LinkedHashMap<String, Double>();
    for (Map.Entry<String, Double> entry : builder.operatingConditions.entrySet()) {
      String name = validateName(entry.getKey(), "operating condition");
      Double value = entry.getValue();
      if (value == null || !Double.isFinite(value.doubleValue())) {
        throw new IllegalArgumentException("operating condition " + name + " must be finite");
      }
      if (copiedConditions.containsKey(name)) {
        throw new IllegalArgumentException("duplicate operating-condition name after normalization: " + name);
      }
      copiedConditions.put(name, value);
    }

    this.regimeId = builder.regimeId.trim();
    this.sampleIntervalSeconds = builder.sampleIntervalSeconds;
    this.operatingConditions = Collections.unmodifiableMap(copiedConditions);
    this.signals = Collections.unmodifiableMap(copiedSignals);
    this.sampleCount = expectedSamples;
  }

  private static String validateName(String name, String type) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException(type + " name must not be blank");
    }
    return name.trim();
  }

  /**
   * Returns the identifier attached to this window.
   *
   * <p>
   * For normal training windows this is the known operating-regime identifier. A test window may use an arbitrary
   * identifier because regime matching is performed from its operating-condition values.
   * </p>
   *
   * @return window or regime identifier
   */
  public String getRegimeId() {
    return regimeId;
  }

  /**
   * Returns the sample interval.
   *
   * @return sample interval in seconds
   */
  public double getSampleIntervalSeconds() {
    return sampleIntervalSeconds;
  }

  /**
   * Returns the number of samples in every signal.
   *
   * @return sample count
   */
  public int getSampleCount() {
    return sampleCount;
  }

  /**
   * Returns the signal names in insertion order.
   *
   * @return unmodifiable signal-name list
   */
  public List<String> getSignalNames() {
    return Collections.unmodifiableList(new ArrayList<String>(signals.keySet()));
  }

  /**
   * Returns a defensive copy of one signal.
   *
   * @param signalName signal name
   * @return signal samples
   */
  public double[] getSignal(String signalName) {
    double[] values = signals.get(signalName);
    if (values == null) {
      throw new IllegalArgumentException("unknown signal: " + signalName);
    }
    return values.clone();
  }

  double[] getSignalInternal(String signalName) {
    return signals.get(signalName);
  }

  /**
   * Returns the operating conditions used for regime matching.
   *
   * @return unmodifiable condition map
   */
  public Map<String, Double> getOperatingConditions() {
    return operatingConditions;
  }

  /**
   * Creates a window builder.
   *
   * @param regimeId window or regime identifier
   * @param sampleIntervalSeconds sample interval in seconds
   * @return builder
   */
  public static Builder builder(String regimeId, double sampleIntervalSeconds) {
    return new Builder(regimeId, sampleIntervalSeconds);
  }

  /**
   * Builder for immutable process windows.
   */
  public static final class Builder {
    private final String regimeId;
    private final double sampleIntervalSeconds;
    private final Map<String, Double> operatingConditions = new LinkedHashMap<String, Double>();
    private final Map<String, double[]> signals = new LinkedHashMap<String, double[]>();

    private Builder(String regimeId, double sampleIntervalSeconds) {
      this.regimeId = regimeId;
      this.sampleIntervalSeconds = sampleIntervalSeconds;
    }

    /**
     * Adds an operating-condition coordinate.
     *
     * @param name condition name, for example {@code gasFlowSetPoint}
     * @param value condition value
     * @return this builder
     */
    public Builder operatingCondition(String name, double value) {
      operatingConditions.put(name, Double.valueOf(value));
      return this;
    }

    /**
     * Adds a process signal.
     *
     * @param name signal name
     * @param values signal values
     * @return this builder
     */
    public Builder signal(String name, double[] values) {
      if (values == null) {
        throw new IllegalArgumentException("signal values must not be null");
      }
      signals.put(name, values.clone());
      return this;
    }

    /**
     * Builds the immutable window.
     *
     * @return process window
     */
    public RcaProcessWindow build() {
      return new RcaProcessWindow(this);
    }
  }
}
