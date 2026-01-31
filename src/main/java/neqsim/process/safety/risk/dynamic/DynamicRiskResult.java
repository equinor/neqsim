package neqsim.process.safety.risk.dynamic;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.safety.risk.OperationalRiskResult;

/**
 * Result from dynamic risk simulation including transient effects.
 *
 * <p>
 * Extends standard risk results with transient-specific metrics:
 * </p>
 * <ul>
 * <li>Total transient losses from startup/shutdown</li>
 * <li>Transient loss as fraction of total loss</li>
 * <li>Detailed breakdown by failure phase</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class DynamicRiskResult extends OperationalRiskResult implements Serializable {

  private static final long serialVersionUID = 1000L;

  // Simulation parameters
  private boolean simulateTransients;
  private double rampUpTimeHours;
  private double timestepHours;

  // Transient-specific statistics
  private double meanTransientLoss;
  private double meanSteadyStateLoss;
  private double p10TransientLoss;
  private double p50TransientLoss;
  private double p90TransientLoss;
  private double transientLossFraction;

  // Transient event counts
  private double meanTransientCount;
  private int totalTransientEvents;

  // Raw arrays for analysis
  private double[] transientLosses;
  private double[] steadyStateLosses;

  /**
   * Creates a dynamic risk result.
   */
  public DynamicRiskResult() {
    super();
  }

  // Getters for simulation parameters

  /**
   * Returns whether transients were simulated.
   *
   * @return true if transients simulated
   */
  public boolean isSimulateTransients() {
    return simulateTransients;
  }

  /**
   * Gets the ramp-up time used in simulation.
   *
   * @return ramp-up time in hours
   */
  public double getRampUpTimeHours() {
    return rampUpTimeHours;
  }

  /**
   * Gets the timestep used in simulation.
   *
   * @return timestep in hours
   */
  public double getTimestepHours() {
    return timestepHours;
  }

  // Getters for transient statistics

  /**
   * Gets the mean transient loss.
   *
   * @return mean transient loss in kg
   */
  public double getMeanTransientLoss() {
    return meanTransientLoss;
  }

  /**
   * Gets the mean steady-state loss.
   *
   * @return mean steady-state loss in kg
   */
  public double getMeanSteadyStateLoss() {
    return meanSteadyStateLoss;
  }

  /**
   * Gets the P10 transient loss (10th percentile).
   *
   * @return P10 transient loss in kg
   */
  public double getP10TransientLoss() {
    return p10TransientLoss;
  }

  /**
   * Gets the P50 transient loss (median).
   *
   * @return P50 transient loss in kg
   */
  public double getP50TransientLoss() {
    return p50TransientLoss;
  }

  /**
   * Gets the P90 transient loss (90th percentile).
   *
   * @return P90 transient loss in kg
   */
  public double getP90TransientLoss() {
    return p90TransientLoss;
  }

  /**
   * Gets the fraction of total loss from transients.
   *
   * @return transient loss fraction (0-1)
   */
  public double getTransientLossFraction() {
    return transientLossFraction;
  }

  /**
   * Gets the fraction of total loss from transients as percentage.
   *
   * @return transient loss percentage (0-100)
   */
  public double getTransientLossPercent() {
    return transientLossFraction * 100.0;
  }

  /**
   * Gets the mean transient event count per iteration.
   *
   * @return mean transient count
   */
  public double getMeanTransientCount() {
    return meanTransientCount;
  }

  /**
   * Gets the total transient events across all iterations.
   *
   * @return total transient events
   */
  public int getTotalTransientEvents() {
    return totalTransientEvents;
  }

  /**
   * Gets the total mean loss (transient + steady-state).
   *
   * @return total mean loss in kg
   */
  public double getTotalMeanLoss() {
    return meanTransientLoss + meanSteadyStateLoss;
  }

  // Setters for simulation parameters

  /**
   * Sets whether transients were simulated.
   *
   * @param simulate true if transients simulated
   */
  public void setSimulateTransients(boolean simulate) {
    this.simulateTransients = simulate;
  }

  /**
   * Sets the ramp-up time.
   *
   * @param hours ramp-up time in hours
   */
  public void setRampUpTimeHours(double hours) {
    this.rampUpTimeHours = hours;
  }

  /**
   * Sets the timestep.
   *
   * @param hours timestep in hours
   */
  public void setTimestepHours(double hours) {
    this.timestepHours = hours;
  }

  /**
   * Calculates statistics from raw simulation data.
   *
   * @param totalProductions production totals per iteration
   * @param transientLosses transient losses per iteration
   * @param steadyStateLosses steady-state losses per iteration
   * @param availabilities availability per iteration
   * @param failureCounts failure counts per iteration
   * @param transientCounts transient event counts per iteration
   */
  public void calculateStatistics(double[] totalProductions, double[] transientLosses,
      double[] steadyStateLosses, double[] availabilities, int[] failureCounts,
      int[] transientCounts) {

    // Call parent to calculate base statistics
    super.calculateStatistics(totalProductions, availabilities, failureCounts,
        new double[totalProductions.length]); // downtime placeholder

    // Store raw data
    this.transientLosses = transientLosses.clone();
    this.steadyStateLosses = steadyStateLosses.clone();

    int n = transientLosses.length;

    // Calculate transient statistics
    meanTransientLoss = 0;
    meanSteadyStateLoss = 0;
    totalTransientEvents = 0;

    for (int i = 0; i < n; i++) {
      meanTransientLoss += transientLosses[i];
      meanSteadyStateLoss += steadyStateLosses[i];
      totalTransientEvents += transientCounts[i];
    }

    meanTransientLoss /= n;
    meanSteadyStateLoss /= n;
    meanTransientCount = (double) totalTransientEvents / n;

    // Calculate transient loss fraction
    double totalLoss = meanTransientLoss + meanSteadyStateLoss;
    if (totalLoss > 0) {
      transientLossFraction = meanTransientLoss / totalLoss;
    } else {
      transientLossFraction = 0;
    }

    // Calculate percentiles for transient loss
    double[] sortedTransient = transientLosses.clone();
    Arrays.sort(sortedTransient);

    p10TransientLoss = sortedTransient[(int) (n * 0.10)];
    p50TransientLoss = sortedTransient[(int) (n * 0.50)];
    p90TransientLoss = sortedTransient[(int) (n * 0.90)];
  }

  /**
   * Compares result with static (no transient) simulation.
   *
   * @param staticResult result from non-dynamic simulation
   * @return comparison map
   */
  public Map<String, Object> compareWithStatic(OperationalRiskResult staticResult) {
    Map<String, Object> comparison = new HashMap<>();

    double staticLoss = staticResult.getMaxPossibleProduction() - staticResult.getMeanProduction();
    double dynamicLoss = getTotalMeanLoss();

    comparison.put("staticMeanLoss_kg", staticLoss);
    comparison.put("dynamicMeanLoss_kg", dynamicLoss);
    comparison.put("additionalLoss_kg", dynamicLoss - staticLoss);
    comparison.put("additionalLossPercent",
        staticLoss > 0 ? (dynamicLoss - staticLoss) / staticLoss * 100 : 0);
    comparison.put("transientContribution_kg", meanTransientLoss);
    comparison.put("transientContributionPercent", transientLossFraction * 100);

    return comparison;
  }

  /**
   * Converts to map for JSON serialization.
   *
   * @return map representation
   */
  @Override
  public Map<String, Object> toMap() {
    Map<String, Object> map = super.toMap();

    // Add simulation parameters
    Map<String, Object> params = new HashMap<>();
    params.put("simulateTransients", simulateTransients);
    params.put("rampUpTimeHours", rampUpTimeHours);
    params.put("timestepHours", timestepHours);
    map.put("dynamicParameters", params);

    // Add transient statistics
    Map<String, Object> transient_ = new HashMap<>();
    transient_.put("meanLoss_kg", meanTransientLoss);
    transient_.put("p10Loss_kg", p10TransientLoss);
    transient_.put("p50Loss_kg", p50TransientLoss);
    transient_.put("p90Loss_kg", p90TransientLoss);
    transient_.put("lossFraction", transientLossFraction);
    transient_.put("lossPercent", transientLossFraction * 100);
    transient_.put("meanEventCount", meanTransientCount);
    transient_.put("totalEvents", totalTransientEvents);
    map.put("transientStatistics", transient_);

    // Add steady-state for comparison
    Map<String, Object> steadyState = new HashMap<>();
    steadyState.put("meanLoss_kg", meanSteadyStateLoss);
    steadyState.put("lossFraction", 1.0 - transientLossFraction);
    map.put("steadyStateStatistics", steadyState);

    // Summary
    Map<String, Object> summary = new HashMap<>();
    summary.put("totalMeanLoss_kg", getTotalMeanLoss());
    summary.put("transientContribution", String.format("%.1f%%", transientLossFraction * 100));
    map.put("lossSummary", summary);

    return map;
  }

  /**
   * Converts to JSON string.
   *
   * @return JSON representation
   */
  @Override
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  @Override
  public String toString() {
    return String.format(
        "DynamicRiskResult[availability=%.1f%%, totalLoss=%.0f kg, "
            + "transientFraction=%.1f%%, transients=%s]",
        getMeanAvailability(), getTotalMeanLoss(), transientLossFraction * 100, simulateTransients);
  }
}
