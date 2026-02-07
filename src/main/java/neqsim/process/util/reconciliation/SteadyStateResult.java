package neqsim.process.util.reconciliation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of a steady-state detection evaluation.
 *
 * <p>
 * Produced by {@link SteadyStateDetector#evaluate()} after checking all monitored variables against
 * R-statistic, slope, and standard-deviation thresholds. Contains an overall verdict and
 * per-variable diagnostics.
 * </p>
 *
 * @author Process Optimization Team
 * @version 1.0
 */
public class SteadyStateResult implements java.io.Serializable {

  private static final long serialVersionUID = 1L;

  /** All monitored variables with their current SSD status. */
  private final List<SteadyStateVariable> variables;

  /** Whether the overall process is at steady state (all variables pass). */
  private boolean atSteadyState;

  /** Number of variables at steady state. */
  private int steadyCount;

  /** Number of variables not at steady state. */
  private int transientCount;

  /** Variables that failed the SSD test. */
  private final List<SteadyStateVariable> transientVariables;

  /** Timestamp (epoch ms) of the evaluation. */
  private long timestamp;

  /** Minimum R-statistic threshold used. */
  private double rThreshold;

  /** Maximum slope threshold used. */
  private double slopeThreshold;

  /**
   * Creates a steady-state detection result.
   *
   * @param variables the list of all monitored variables
   * @param rThreshold the R-statistic threshold used
   * @param slopeThreshold the slope threshold used
   */
  public SteadyStateResult(List<SteadyStateVariable> variables, double rThreshold,
      double slopeThreshold) {
    this.variables = new ArrayList<SteadyStateVariable>(variables);
    this.transientVariables = new ArrayList<SteadyStateVariable>();
    this.rThreshold = rThreshold;
    this.slopeThreshold = slopeThreshold;
    this.timestamp = System.currentTimeMillis();
  }

  /**
   * Returns all monitored variables.
   *
   * @return unmodifiable list of variables
   */
  public List<SteadyStateVariable> getVariables() {
    return Collections.unmodifiableList(variables);
  }

  /**
   * Returns whether the overall process is at steady state.
   *
   * @return true if all variables are at steady state
   */
  public boolean isAtSteadyState() {
    return atSteadyState;
  }

  /**
   * Sets the overall steady-state verdict.
   *
   * @param atSteadyState true if all variables pass
   */
  void setAtSteadyState(boolean atSteadyState) {
    this.atSteadyState = atSteadyState;
  }

  /**
   * Returns the number of variables at steady state.
   *
   * @return count of steady-state variables
   */
  public int getSteadyCount() {
    return steadyCount;
  }

  /**
   * Sets the number of steady-state variables.
   *
   * @param steadyCount the count
   */
  void setSteadyCount(int steadyCount) {
    this.steadyCount = steadyCount;
  }

  /**
   * Returns the number of variables not at steady state.
   *
   * @return count of transient variables
   */
  public int getTransientCount() {
    return transientCount;
  }

  /**
   * Sets the number of transient variables.
   *
   * @param transientCount the count
   */
  void setTransientCount(int transientCount) {
    this.transientCount = transientCount;
  }

  /**
   * Returns the variables that failed the SSD test.
   *
   * @return unmodifiable list of transient variables
   */
  public List<SteadyStateVariable> getTransientVariables() {
    return Collections.unmodifiableList(transientVariables);
  }

  /**
   * Adds a variable to the transient list.
   *
   * @param variable the variable that failed the SSD test
   */
  void addTransientVariable(SteadyStateVariable variable) {
    transientVariables.add(variable);
  }

  /**
   * Returns the evaluation timestamp.
   *
   * @return epoch milliseconds when evaluate() was called
   */
  public long getTimestamp() {
    return timestamp;
  }

  /**
   * Sets the evaluation timestamp.
   *
   * @param timestamp epoch milliseconds
   */
  void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  /**
   * Returns the R-statistic threshold used for this evaluation.
   *
   * @return the R threshold
   */
  public double getRThreshold() {
    return rThreshold;
  }

  /**
   * Returns the slope threshold used for this evaluation.
   *
   * @return the slope threshold
   */
  public double getSlopeThreshold() {
    return slopeThreshold;
  }

  /**
   * Returns a human-readable summary report.
   *
   * @return formatted text report of the SSD evaluation
   */
  public String toReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Steady-State Detection Report ===\n");
    sb.append(String.format("Overall: %s\n", atSteadyState ? "STEADY STATE" : "TRANSIENT"));
    sb.append(String.format("Variables: %d steady, %d transient (of %d total)\n", steadyCount,
        transientCount, variables.size()));
    sb.append(
        String.format("Thresholds: R >= %.4f, |slope| <= %.4e\n\n", rThreshold, slopeThreshold));

    sb.append(String.format("%-20s %10s %10s %12s %8s\n", "Variable", "R-stat", "Slope", "Std.Dev",
        "Status"));
    sb.append(String.format("%-20s %10s %10s %12s %8s\n", "--------", "------", "-----", "-------",
        "------"));
    for (SteadyStateVariable v : variables) {
      sb.append(String.format("%-20s %10.4f %10.4e %12.4f %8s\n", v.getName(), v.getRStatistic(),
          v.getSlope(), v.getStandardDeviation(), v.isAtSteadyState() ? "SS" : "**TRANS**"));
    }

    if (!transientVariables.isEmpty()) {
      sb.append("\nTransient Variables (blocking steady state):\n");
      for (SteadyStateVariable v : transientVariables) {
        List<String> reasons = new ArrayList<String>();
        if (v.getRStatistic() < rThreshold) {
          reasons.add(String.format("R=%.4f < %.4f", v.getRStatistic(), rThreshold));
        }
        if (slopeThreshold > 0 && Math.abs(v.getSlope()) > slopeThreshold) {
          reasons.add(String.format("|slope|=%.4e > %.4e", Math.abs(v.getSlope()), slopeThreshold));
        }
        if (!v.isWindowFull()) {
          reasons.add(String.format("window not full (%d/%d)", v.getCount(), v.getWindowSize()));
        }
        sb.append(String.format("  %s: %s\n", v.getName(), String.join(", ", reasons)));
      }
    }
    return sb.toString();
  }

  /**
   * Returns a JSON representation of the result.
   *
   * @return JSON string with all SSD data
   */
  public String toJson() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"atSteadyState\": ").append(atSteadyState).append(",\n");
    sb.append("  \"steadyCount\": ").append(steadyCount).append(",\n");
    sb.append("  \"transientCount\": ").append(transientCount).append(",\n");
    sb.append("  \"rThreshold\": ").append(rThreshold).append(",\n");
    sb.append("  \"slopeThreshold\": ").append(slopeThreshold).append(",\n");
    sb.append("  \"timestamp\": ").append(timestamp).append(",\n");
    sb.append("  \"variables\": [\n");
    for (int i = 0; i < variables.size(); i++) {
      SteadyStateVariable v = variables.get(i);
      sb.append("    {");
      sb.append("\"name\": \"").append(v.getName()).append("\", ");
      sb.append("\"atSteadyState\": ").append(v.isAtSteadyState()).append(", ");
      sb.append("\"rStatistic\": ").append(v.getRStatistic()).append(", ");
      sb.append("\"slope\": ").append(v.getSlope()).append(", ");
      sb.append("\"mean\": ").append(v.getMean()).append(", ");
      sb.append("\"standardDeviation\": ").append(v.getStandardDeviation()).append(", ");
      sb.append("\"sampleCount\": ").append(v.getCount()).append(", ");
      sb.append("\"windowFull\": ").append(v.isWindowFull());
      if (!v.getUnit().isEmpty()) {
        sb.append(", \"unit\": \"").append(v.getUnit()).append("\"");
      }
      sb.append("}");
      if (i < variables.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append("  ]\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Returns a summary string.
   *
   * @return brief summary of the SSD evaluation
   */
  @Override
  public String toString() {
    return String.format("SteadyStateResult[%s, %d/%d steady]",
        atSteadyState ? "STEADY" : "TRANSIENT", steadyCount, variables.size());
  }
}
