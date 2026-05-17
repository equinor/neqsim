package neqsim.process.util.reconciliation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Steady-State Detector (SSD) for process variables.
 *
 * <p>
 * Monitors a set of process variables over a sliding window and determines whether the process has
 * reached steady state. The primary criterion is the <b>R-statistic</b>: the ratio of filtered
 * variance (successive differences) to unfiltered variance (sample variance). At steady state, both
 * variances are similar and R approaches 1.0. During ramps, steps, or oscillations, R drops well
 * below 1.0.
 * </p>
 *
 * <p>
 * Two additional optional criteria can be enabled:
 * </p>
 * <ul>
 * <li><b>Slope test</b>: the absolute slope from linear regression through the window must be below
 * a threshold. This catches slow drifts that the R-test may miss.</li>
 * <li><b>Standard deviation test</b>: the standard deviation of the window must be below a
 * threshold. This catches excessive noise.</li>
 * </ul>
 *
 * <p>
 * The detector is designed for use in online optimization workflows where data is collected
 * externally (e.g., in Python from a DCS historian) and fed into the detector one sample at a time.
 * When all variables pass the SSD test, the process is at steady state and safe for data
 * reconciliation or model calibration.
 * </p>
 *
 * <p>
 * <b>Typical usage from Python:</b>
 * </p>
 *
 * <pre>
 * detector = SteadyStateDetector(30)  # 30-sample window
 * detector.addVariable(SteadyStateVariable("feed_flow", 30).setUnit("kg/hr"))
 * detector.addVariable(SteadyStateVariable("temperature", 30).setUnit("C"))
 *
 * # In a loop, push new readings:
 * detector.updateVariable("feed_flow", 1005.2)
 * detector.updateVariable("temperature", 82.1)
 * result = detector.evaluate()
 * if result.isAtSteadyState():
 *     # safe to reconcile
 *     ...
 * </pre>
 *
 * <p>
 * <b>References:</b>
 * </p>
 * <ul>
 * <li>Cao, S. and Rhinehart, R.R. (1995), "An efficient method for on-line identification of steady
 * state", Journal of Process Control, 5(6), 363-374.</li>
 * <li>Jiang, T., Chen, B. and He, X. (2003), "Industrial application of Wavelet-based steady state
 * detection", Computers and Chemical Engineering, 27, 569-578.</li>
 * </ul>
 *
 * @author Process Optimization Team
 * @version 1.0
 */
public class SteadyStateDetector implements java.io.Serializable {

  private static final long serialVersionUID = 1L;

  /** Logger for this class. */
  private static final Logger logger = LogManager.getLogger(SteadyStateDetector.class);

  /** Registered variables, keyed by name. */
  private final Map<String, SteadyStateVariable> variableMap;

  /** Ordered list of variables (preserves insertion order). */
  private final List<SteadyStateVariable> variableList;

  /** Default window size for new variables. */
  private int defaultWindowSize;

  /**
   * R-statistic threshold. A variable is at steady state if R &gt;= this threshold. Default 0.5
   * (Cao-Rhinehart recommended range 0.1-1.0; 0.5 is a good balance).
   */
  private double rThreshold = 0.5;

  /**
   * Maximum allowed slope (units per sample). If &gt; 0 and |slope| exceeds this, the variable is
   * not at steady state. Default 0 (disabled).
   */
  private double slopeThreshold = 0.0;

  /**
   * Maximum allowed standard deviation. If &gt; 0 and std.dev exceeds this, the variable is not at
   * steady state. Default 0 (disabled).
   */
  private double stdDevThreshold = 0.0;

  /**
   * Minimum fraction (0-1) of variables that must be at steady state for the overall verdict.
   * Default 1.0 (all variables must be steady).
   */
  private double requiredFraction = 1.0;

  /**
   * Whether a full window is required before declaring steady state. Default true.
   */
  private boolean requireFullWindow = true;

  /**
   * Creates a steady-state detector with the given default window size.
   *
   * @param defaultWindowSize the default number of samples in the sliding window (must be at least
   *        3)
   * @throws IllegalArgumentException if defaultWindowSize is less than 3
   */
  public SteadyStateDetector(int defaultWindowSize) {
    if (defaultWindowSize < 3) {
      throw new IllegalArgumentException(
          "Default window size must be at least 3, got: " + defaultWindowSize);
    }
    this.defaultWindowSize = defaultWindowSize;
    this.variableMap = new LinkedHashMap<String, SteadyStateVariable>();
    this.variableList = new ArrayList<SteadyStateVariable>();
  }

  /**
   * Creates a steady-state detector with a default window size of 30.
   */
  public SteadyStateDetector() {
    this(30);
  }

  // ==================== Variable management ====================

  /**
   * Adds a variable to the detector.
   *
   * @param variable the steady-state variable to monitor
   * @return this detector for chaining
   * @throws IllegalArgumentException if a variable with the same name already exists
   */
  public SteadyStateDetector addVariable(SteadyStateVariable variable) {
    if (variableMap.containsKey(variable.getName())) {
      throw new IllegalArgumentException("Variable already exists: " + variable.getName());
    }
    variableMap.put(variable.getName(), variable);
    variableList.add(variable);
    return this;
  }

  /**
   * Adds a variable by name using the default window size.
   *
   * @param name the tag name
   * @return the created variable (for further configuration)
   */
  public SteadyStateVariable addVariable(String name) {
    SteadyStateVariable v = new SteadyStateVariable(name, defaultWindowSize);
    addVariable(v);
    return v;
  }

  /**
   * Returns a variable by name.
   *
   * @param name the variable name
   * @return the variable, or null if not found
   */
  public SteadyStateVariable getVariable(String name) {
    return variableMap.get(name);
  }

  /**
   * Returns all registered variables.
   *
   * @return unmodifiable list of variables in insertion order
   */
  public List<SteadyStateVariable> getVariables() {
    return Collections.unmodifiableList(variableList);
  }

  /**
   * Returns the number of registered variables.
   *
   * @return variable count
   */
  public int getVariableCount() {
    return variableList.size();
  }

  /**
   * Removes a variable by name.
   *
   * @param name the variable name to remove
   * @return true if the variable was found and removed
   */
  public boolean removeVariable(String name) {
    SteadyStateVariable removed = variableMap.remove(name);
    if (removed != null) {
      variableList.remove(removed);
      return true;
    }
    return false;
  }

  /**
   * Clears all variables.
   */
  public void clear() {
    variableMap.clear();
    variableList.clear();
  }

  // ==================== Data update ====================

  /**
   * Updates a single variable with a new measurement value.
   *
   * @param name variable name (tag)
   * @param value new measurement reading
   * @throws IllegalArgumentException if the variable name is not found
   */
  public void updateVariable(String name, double value) {
    SteadyStateVariable v = variableMap.get(name);
    if (v == null) {
      throw new IllegalArgumentException("Variable not found: " + name);
    }
    v.addValue(value);
  }

  /**
   * Updates all variables at once from a map of name-value pairs.
   *
   * <p>
   * Convenient for pushing a batch of readings from a DCS scan.
   * </p>
   *
   * @param values map of variable name to measurement value
   * @throws IllegalArgumentException if any variable name is not found
   */
  public void updateAll(Map<String, Double> values) {
    for (Map.Entry<String, Double> entry : values.entrySet()) {
      updateVariable(entry.getKey(), entry.getValue());
    }
  }

  // ==================== SSD Evaluation ====================

  /**
   * Evaluates all variables against the SSD criteria and returns the result.
   *
   * <p>
   * A variable is at steady state if:
   * </p>
   * <ol>
   * <li>Its window is full (if {@code requireFullWindow} is true)</li>
   * <li>R-statistic &gt;= {@code rThreshold}</li>
   * <li>|slope| &lt;= {@code slopeThreshold} (if slopeThreshold &gt; 0)</li>
   * <li>std.dev &lt;= {@code stdDevThreshold} (if stdDevThreshold &gt; 0)</li>
   * </ol>
   *
   * <p>
   * The overall process is at steady state if the fraction of steady-state variables is at least
   * {@code requiredFraction}.
   * </p>
   *
   * @return the SSD result with per-variable diagnostics and overall verdict
   */
  public SteadyStateResult evaluate() {
    SteadyStateResult result = new SteadyStateResult(variableList, rThreshold, slopeThreshold);

    int steadyCount = 0;
    int transientCount = 0;

    for (SteadyStateVariable v : variableList) {
      boolean isSteady = evaluateVariable(v);
      v.setAtSteadyState(isSteady);

      if (isSteady) {
        steadyCount++;
      } else {
        transientCount++;
        result.addTransientVariable(v);
      }
    }

    result.setSteadyCount(steadyCount);
    result.setTransientCount(transientCount);

    // Overall verdict
    boolean overall;
    if (variableList.isEmpty()) {
      overall = false;
    } else {
      double fraction = (double) steadyCount / variableList.size();
      overall = fraction >= requiredFraction;
    }
    result.setAtSteadyState(overall);

    logger.debug("SSD evaluation: {} ({}/{} steady)", overall ? "STEADY" : "TRANSIENT", steadyCount,
        variableList.size());

    return result;
  }

  /**
   * Evaluates a single variable against the SSD criteria.
   *
   * @param v the variable to evaluate
   * @return true if the variable passes all active SSD tests
   */
  private boolean evaluateVariable(SteadyStateVariable v) {
    // Must have enough samples
    if (requireFullWindow && !v.isWindowFull()) {
      return false;
    }
    if (v.getCount() < 3) {
      return false;
    }

    // R-statistic test (primary)
    if (v.getRStatistic() < rThreshold) {
      return false;
    }

    // Slope test (optional)
    if (slopeThreshold > 0 && Math.abs(v.getSlope()) > slopeThreshold) {
      return false;
    }

    // Std.dev test (optional)
    if (stdDevThreshold > 0 && v.getStandardDeviation() > stdDevThreshold) {
      return false;
    }

    return true;
  }

  /**
   * Convenience method: updates all variables and evaluates in one call.
   *
   * @param values map of variable name to new measurement value
   * @return the SSD result
   */
  public SteadyStateResult updateAndEvaluate(Map<String, Double> values) {
    updateAll(values);
    return evaluate();
  }

  // ==================== Bridge to DataReconciliationEngine ====================

  /**
   * Creates a {@link DataReconciliationEngine} pre-populated with variables from this detector.
   *
   * <p>
   * Each steady-state variable that has a defined uncertainty is converted to a
   * {@link ReconciliationVariable} using the latest value as the measurement and the configured
   * uncertainty. Only variables currently at steady state are included (to avoid reconciling
   * transient data).
   * </p>
   *
   * @return a new engine with variables added, ready for constraints and reconciliation
   */
  public DataReconciliationEngine createReconciliationEngine() {
    DataReconciliationEngine engine = new DataReconciliationEngine();
    for (SteadyStateVariable v : variableList) {
      if (v.isAtSteadyState() && !Double.isNaN(v.getUncertainty())) {
        double value = v.getMean(); // use window mean for reconciliation
        double sigma = v.getUncertainty();
        ReconciliationVariable rv = new ReconciliationVariable(v.getName(), value, sigma);
        rv.setUnit(v.getUnit());
        engine.addVariable(rv);
      }
    }
    return engine;
  }

  // ==================== Configuration ====================

  /**
   * Returns the default window size.
   *
   * @return default window size for new variables
   */
  public int getDefaultWindowSize() {
    return defaultWindowSize;
  }

  /**
   * Sets the default window size for new variables.
   *
   * @param defaultWindowSize window size (must be at least 3)
   * @return this detector for chaining
   * @throws IllegalArgumentException if less than 3
   */
  public SteadyStateDetector setDefaultWindowSize(int defaultWindowSize) {
    if (defaultWindowSize < 3) {
      throw new IllegalArgumentException(
          "Default window size must be at least 3, got: " + defaultWindowSize);
    }
    this.defaultWindowSize = defaultWindowSize;
    return this;
  }

  /**
   * Returns the R-statistic threshold.
   *
   * @return threshold value (default 0.5)
   */
  public double getRThreshold() {
    return rThreshold;
  }

  /**
   * Sets the R-statistic threshold.
   *
   * <p>
   * A lower threshold (e.g., 0.3) is more lenient — allows more variability before declaring
   * transient. A higher threshold (e.g., 0.8) is stricter — requires very stable signals.
   * </p>
   *
   * <p>
   * Cao-Rhinehart recommended range: 0.1 to 1.0, with 0.5 as a good default.
   * </p>
   *
   * @param rThreshold R-statistic threshold in the range (0, 1]
   * @return this detector for chaining
   * @throws IllegalArgumentException if threshold is not in (0, 2]
   */
  public SteadyStateDetector setRThreshold(double rThreshold) {
    if (rThreshold <= 0 || rThreshold > 2.0) {
      throw new IllegalArgumentException("R-threshold must be in (0, 2], got: " + rThreshold);
    }
    this.rThreshold = rThreshold;
    return this;
  }

  /**
   * Returns the slope threshold.
   *
   * @return max allowed absolute slope (units per sample), or 0 if disabled
   */
  public double getSlopeThreshold() {
    return slopeThreshold;
  }

  /**
   * Sets the slope threshold.
   *
   * <p>
   * The slope is expressed in engineering-units per sample. Set to 0 to disable the slope test.
   * </p>
   *
   * @param slopeThreshold maximum allowed |slope|, or 0 to disable
   * @return this detector for chaining
   * @throws IllegalArgumentException if negative
   */
  public SteadyStateDetector setSlopeThreshold(double slopeThreshold) {
    if (slopeThreshold < 0) {
      throw new IllegalArgumentException(
          "Slope threshold must be non-negative, got: " + slopeThreshold);
    }
    this.slopeThreshold = slopeThreshold;
    return this;
  }

  /**
   * Returns the standard deviation threshold.
   *
   * @return max allowed standard deviation, or 0 if disabled
   */
  public double getStdDevThreshold() {
    return stdDevThreshold;
  }

  /**
   * Sets the standard deviation threshold.
   *
   * <p>
   * Set to 0 to disable the std.dev test. When enabled, a variable is only at steady state if its
   * window standard deviation is at or below this threshold.
   * </p>
   *
   * @param stdDevThreshold max standard deviation, or 0 to disable
   * @return this detector for chaining
   * @throws IllegalArgumentException if negative
   */
  public SteadyStateDetector setStdDevThreshold(double stdDevThreshold) {
    if (stdDevThreshold < 0) {
      throw new IllegalArgumentException(
          "Std.dev threshold must be non-negative, got: " + stdDevThreshold);
    }
    this.stdDevThreshold = stdDevThreshold;
    return this;
  }

  /**
   * Returns the required fraction of variables that must be steady for the overall verdict.
   *
   * @return fraction in [0, 1]
   */
  public double getRequiredFraction() {
    return requiredFraction;
  }

  /**
   * Sets the required fraction of variables that must be at steady state.
   *
   * <p>
   * Default is 1.0 (all variables must pass). Set to 0.8 to allow 20% of variables to be transient
   * and still declare overall steady state.
   * </p>
   *
   * @param requiredFraction fraction in [0, 1]
   * @return this detector for chaining
   * @throws IllegalArgumentException if not in [0, 1]
   */
  public SteadyStateDetector setRequiredFraction(double requiredFraction) {
    if (requiredFraction < 0 || requiredFraction > 1.0) {
      throw new IllegalArgumentException(
          "Required fraction must be in [0, 1], got: " + requiredFraction);
    }
    this.requiredFraction = requiredFraction;
    return this;
  }

  /**
   * Returns whether a full window is required before declaring steady state.
   *
   * @return true if full window is required (default)
   */
  public boolean isRequireFullWindow() {
    return requireFullWindow;
  }

  /**
   * Sets whether a full window is required before declaring steady state.
   *
   * @param requireFullWindow true to require full window
   * @return this detector for chaining
   */
  public SteadyStateDetector setRequireFullWindow(boolean requireFullWindow) {
    this.requireFullWindow = requireFullWindow;
    return this;
  }

  /**
   * Returns a summary string.
   *
   * @return brief summary of the detector configuration
   */
  @Override
  public String toString() {
    return String.format(
        "SteadyStateDetector[vars=%d, window=%d, R>=%.2f, |slope|<=%.2e, reqFrac=%.0f%%]",
        variableList.size(), defaultWindowSize, rThreshold, slopeThreshold, requiredFraction * 100);
  }
}
