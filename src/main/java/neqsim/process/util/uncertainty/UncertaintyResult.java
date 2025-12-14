package neqsim.process.util.uncertainty;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import neqsim.process.measurementdevice.vfm.UncertaintyBounds;

/**
 * Result of an uncertainty propagation analysis for a process simulation.
 *
 * <p>
 * Contains uncertainty bounds for all outputs along with the sensitivity matrix used for the
 * calculation.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class UncertaintyResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final Map<String, UncertaintyBounds> outputUncertainties;
  private final SensitivityMatrix sensitivityMatrix;
  private final int monteCarloSamples;
  private final double convergenceMetric;

  /**
   * Creates an uncertainty result from analytical propagation.
   *
   * @param outputUncertainties map of output names to uncertainty bounds
   * @param sensitivityMatrix the sensitivity matrix used
   */
  public UncertaintyResult(Map<String, UncertaintyBounds> outputUncertainties,
      SensitivityMatrix sensitivityMatrix) {
    this.outputUncertainties = new HashMap<>(outputUncertainties);
    this.sensitivityMatrix = sensitivityMatrix;
    this.monteCarloSamples = 0;
    this.convergenceMetric = 0.0;
  }

  /**
   * Creates an uncertainty result from Monte Carlo analysis.
   *
   * @param outputUncertainties map of output names to uncertainty bounds
   * @param monteCarloSamples number of Monte Carlo samples used
   * @param convergenceMetric convergence metric (e.g., coefficient of variation of the mean)
   */
  public UncertaintyResult(Map<String, UncertaintyBounds> outputUncertainties,
      int monteCarloSamples, double convergenceMetric) {
    this.outputUncertainties = new HashMap<>(outputUncertainties);
    this.sensitivityMatrix = null;
    this.monteCarloSamples = monteCarloSamples;
    this.convergenceMetric = convergenceMetric;
  }

  /**
   * Gets uncertainty bounds for a specific output variable.
   *
   * @param outputName the output variable name
   * @return uncertainty bounds or null if not found
   */
  public UncertaintyBounds getUncertainty(String outputName) {
    return outputUncertainties.get(outputName);
  }

  /**
   * Gets all output uncertainties.
   *
   * @return map of output names to uncertainty bounds
   */
  public Map<String, UncertaintyBounds> getAllUncertainties() {
    return new HashMap<>(outputUncertainties);
  }

  /**
   * Gets the sensitivity matrix (if available).
   *
   * @return sensitivity matrix or null for Monte Carlo results
   */
  public SensitivityMatrix getSensitivityMatrix() {
    return sensitivityMatrix;
  }

  /**
   * Checks if the result is from Monte Carlo analysis.
   *
   * @return true if Monte Carlo was used
   */
  public boolean isMonteCarloResult() {
    return monteCarloSamples > 0;
  }

  /**
   * Gets the number of Monte Carlo samples used.
   *
   * @return number of samples, or 0 for analytical results
   */
  public int getMonteCarloSamples() {
    return monteCarloSamples;
  }

  /**
   * Gets the convergence metric for Monte Carlo results.
   *
   * @return convergence metric, or 0 for analytical results
   */
  public double getConvergenceMetric() {
    return convergenceMetric;
  }

  /**
   * Gets the output with the highest relative uncertainty.
   *
   * @return output name with highest uncertainty
   */
  public String getMostUncertainOutput() {
    String mostUncertain = null;
    double maxRelUncert = 0.0;

    for (Map.Entry<String, UncertaintyBounds> entry : outputUncertainties.entrySet()) {
      double relUncert = entry.getValue().getCoefficientOfVariation();
      if (!Double.isNaN(relUncert) && relUncert > maxRelUncert) {
        maxRelUncert = relUncert;
        mostUncertain = entry.getKey();
      }
    }

    return mostUncertain;
  }

  /**
   * Checks if all outputs meet a specified relative uncertainty threshold.
   *
   * @param maxRelativeUncertainty maximum acceptable relative uncertainty (e.g., 0.05 for 5%)
   * @return true if all outputs meet the threshold
   */
  public boolean meetsUncertaintyThreshold(double maxRelativeUncertainty) {
    for (UncertaintyBounds bounds : outputUncertainties.values()) {
      if (bounds.getCoefficientOfVariation() > maxRelativeUncertainty) {
        return false;
      }
    }
    return true;
  }

  /**
   * Gets outputs that exceed a relative uncertainty threshold.
   *
   * @param threshold maximum acceptable relative uncertainty
   * @return map of output names to their uncertainty bounds that exceed the threshold
   */
  public Map<String, UncertaintyBounds> getOutputsExceedingThreshold(double threshold) {
    Map<String, UncertaintyBounds> exceeding = new HashMap<>();

    for (Map.Entry<String, UncertaintyBounds> entry : outputUncertainties.entrySet()) {
      if (entry.getValue().getCoefficientOfVariation() > threshold) {
        exceeding.put(entry.getKey(), entry.getValue());
      }
    }

    return exceeding;
  }

  /**
   * Generates a summary string of the uncertainty analysis.
   *
   * @return formatted summary
   */
  public String getSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("Uncertainty Analysis Results\n");
    sb.append("============================\n");

    if (isMonteCarloResult()) {
      sb.append(String.format("Method: Monte Carlo (%d samples)\n", monteCarloSamples));
      sb.append(String.format("Convergence: %.4f\n", convergenceMetric));
    } else {
      sb.append("Method: Analytical (linear propagation)\n");
    }

    sb.append("\nOutput Uncertainties:\n");
    for (Map.Entry<String, UncertaintyBounds> entry : outputUncertainties.entrySet()) {
      UncertaintyBounds bounds = entry.getValue();
      sb.append(String.format("  %s: %.4f ± %.4f %s (%.1f%%)\n", entry.getKey(), bounds.getMean(),
          bounds.getStandardDeviation(), bounds.getUnit(), bounds.getRelativeUncertaintyPercent()));
    }

    return sb.toString();
  }

  @Override
  public String toString() {
    return getSummary();
  }
}
