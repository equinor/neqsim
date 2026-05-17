package neqsim.process.safety.risk;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Result of an operational risk Monte Carlo simulation.
 *
 * <p>
 * Contains production forecasts with uncertainty quantification:
 * <ul>
 * <li>Mean, P10, P50, P90 production values</li>
 * <li>Availability statistics</li>
 * <li>Failure event counts and durations</li>
 * <li>Equipment-specific availability data</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class OperationalRiskResult implements Serializable {

  private static final long serialVersionUID = 1000L;

  private int iterations;
  private double timeHorizonDays;

  // Baseline values
  private double baselineProductionRate; // kg/hr
  private double maxPossibleProduction; // kg

  // Production statistics
  private double meanProduction;
  private double p10Production;
  private double p50Production;
  private double p90Production;
  private double minProduction;
  private double maxProduction;
  private double stdDevProduction;

  // Availability statistics
  private double meanAvailability;
  private double minAvailability;
  private double maxAvailability;

  // Failure statistics
  private double meanFailureCount;
  private double meanDowntimeHours;
  private double maxDowntimeHours;

  // Equipment-specific data
  private Map<String, Double> equipmentAvailability;

  /**
   * Default constructor.
   */
  public OperationalRiskResult() {
    this.equipmentAvailability = new HashMap<String, Double>();
  }

  // Statistics calculation

  /**
   * Calculates statistics from Monte Carlo results.
   *
   * @param productions array of total production values
   * @param availabilities array of availability fractions
   * @param failureCounts array of failure counts
   * @param downtimes array of downtime hours
   */
  public void calculateStatistics(double[] productions, double[] availabilities,
      int[] failureCounts, double[] downtimes) {

    // Sort for percentile calculation
    double[] sortedProductions = productions.clone();
    Arrays.sort(sortedProductions);

    double[] sortedAvailabilities = availabilities.clone();
    Arrays.sort(sortedAvailabilities);

    int n = productions.length;

    // Production statistics
    this.meanProduction = calculateMean(productions);
    this.stdDevProduction = calculateStdDev(productions, meanProduction);
    this.minProduction = sortedProductions[0];
    this.maxProduction = sortedProductions[n - 1];
    this.p10Production = getPercentile(sortedProductions, 10);
    this.p50Production = getPercentile(sortedProductions, 50);
    this.p90Production = getPercentile(sortedProductions, 90);

    // Availability statistics
    this.meanAvailability = calculateMean(availabilities) * 100; // Convert to percentage
    this.minAvailability = sortedAvailabilities[0] * 100;
    this.maxAvailability = sortedAvailabilities[n - 1] * 100;

    // Failure statistics
    double[] failureDoubles = new double[failureCounts.length];
    for (int i = 0; i < failureCounts.length; i++) {
      failureDoubles[i] = failureCounts[i];
    }
    this.meanFailureCount = calculateMean(failureDoubles);
    this.meanDowntimeHours = calculateMean(downtimes);

    double maxDown = 0;
    for (double d : downtimes) {
      if (d > maxDown) {
        maxDown = d;
      }
    }
    this.maxDowntimeHours = maxDown;
  }

  private double calculateMean(double[] values) {
    double sum = 0;
    for (double v : values) {
      sum += v;
    }
    return sum / values.length;
  }

  private double calculateStdDev(double[] values, double mean) {
    double sumSq = 0;
    for (double v : values) {
      sumSq += (v - mean) * (v - mean);
    }
    return Math.sqrt(sumSq / values.length);
  }

  private double getPercentile(double[] sorted, int percentile) {
    int index = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
    index = Math.max(0, Math.min(index, sorted.length - 1));
    return sorted[index];
  }

  // Setters

  public void setIterations(int iterations) {
    this.iterations = iterations;
  }

  public void setTimeHorizonDays(double days) {
    this.timeHorizonDays = days;
  }

  public void setBaselineProductionRate(double rate) {
    this.baselineProductionRate = rate;
  }

  public void setMaxPossibleProduction(double max) {
    this.maxPossibleProduction = max;
  }

  public void addEquipmentAvailability(String name, double availability) {
    this.equipmentAvailability.put(name, availability * 100);
  }

  // Getters

  /**
   * Gets the number of Monte Carlo iterations.
   *
   * @return iterations
   */
  public int getIterations() {
    return iterations;
  }

  /**
   * Gets the simulation time horizon in days.
   *
   * @return time horizon days
   */
  public double getTimeHorizonDays() {
    return timeHorizonDays;
  }

  /**
   * Gets the baseline production rate.
   *
   * @return production rate in kg/hr
   */
  public double getBaselineProductionRate() {
    return baselineProductionRate;
  }

  /**
   * Gets the maximum possible production (100% availability).
   *
   * @return max production in kg
   */
  public double getMaxPossibleProduction() {
    return maxPossibleProduction;
  }

  /**
   * Gets the mean production.
   *
   * @return mean production in kg
   */
  public double getMeanProduction() {
    return meanProduction;
  }

  /**
   * Gets the P10 production (optimistic).
   *
   * @return P10 production in kg
   */
  public double getP10Production() {
    return p10Production;
  }

  /**
   * Gets the P50 production (median).
   *
   * @return P50 production in kg
   */
  public double getP50Production() {
    return p50Production;
  }

  /**
   * Gets the P90 production (conservative).
   *
   * @return P90 production in kg
   */
  public double getP90Production() {
    return p90Production;
  }

  /**
   * Gets the minimum production across all iterations.
   *
   * @return minimum production in kg
   */
  public double getMinProduction() {
    return minProduction;
  }

  /**
   * Gets the maximum production across all iterations.
   *
   * @return maximum production in kg
   */
  public double getMaxProduction() {
    return maxProduction;
  }

  /**
   * Gets the standard deviation of production.
   *
   * @return standard deviation in kg
   */
  public double getStdDevProduction() {
    return stdDevProduction;
  }

  /**
   * Gets the mean availability percentage.
   *
   * @return availability percentage (0-100)
   */
  public double getAvailability() {
    return meanAvailability;
  }

  /**
   * Gets the mean availability percentage.
   *
   * @return availability percentage (0-100)
   */
  public double getMeanAvailability() {
    return meanAvailability;
  }

  /**
   * Gets the minimum availability percentage.
   *
   * @return min availability percentage
   */
  public double getMinAvailability() {
    return minAvailability;
  }

  /**
   * Gets the maximum availability percentage.
   *
   * @return max availability percentage
   */
  public double getMaxAvailability() {
    return maxAvailability;
  }

  /**
   * Gets the mean number of failure events.
   *
   * @return mean failure count
   */
  public double getMeanFailureCount() {
    return meanFailureCount;
  }

  /**
   * Gets the mean downtime in hours.
   *
   * @return mean downtime hours
   */
  public double getMeanDowntimeHours() {
    return meanDowntimeHours;
  }

  /**
   * Gets the maximum downtime in hours.
   *
   * @return max downtime hours
   */
  public double getMaxDowntimeHours() {
    return maxDowntimeHours;
  }

  /**
   * Gets equipment-specific availability data.
   *
   * @return map of equipment name to availability percentage
   */
  public Map<String, Double> getEquipmentAvailability() {
    return new HashMap<String, Double>(equipmentAvailability);
  }

  /**
   * Calculates production efficiency (actual vs possible).
   *
   * @return efficiency percentage (0-100)
   */
  public double getProductionEfficiency() {
    if (maxPossibleProduction > 0) {
      return (meanProduction / maxPossibleProduction) * 100;
    }
    return 0;
  }

  /**
   * Calculates expected production loss.
   *
   * @return production loss in kg
   */
  public double getExpectedProductionLoss() {
    return maxPossibleProduction - meanProduction;
  }

  /**
   * Calculates coefficient of variation (uncertainty measure).
   *
   * @return CV as percentage
   */
  public double getCoefficientOfVariation() {
    if (meanProduction > 0) {
      return (stdDevProduction / meanProduction) * 100;
    }
    return 0;
  }

  /**
   * Generates a summary map.
   *
   * @return map of key metrics
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<String, Object>();

    // Simulation parameters
    map.put("iterations", iterations);
    map.put("timeHorizonDays", timeHorizonDays);
    map.put("baselineProductionRate_kg_hr", baselineProductionRate);
    map.put("maxPossibleProduction_kg", maxPossibleProduction);

    // Production statistics
    Map<String, Object> production = new HashMap<String, Object>();
    production.put("mean_kg", meanProduction);
    production.put("p10_kg", p10Production);
    production.put("p50_kg", p50Production);
    production.put("p90_kg", p90Production);
    production.put("min_kg", minProduction);
    production.put("max_kg", maxProduction);
    production.put("stdDev_kg", stdDevProduction);
    production.put("efficiency_percent", getProductionEfficiency());
    production.put("expectedLoss_kg", getExpectedProductionLoss());
    production.put("coefficientOfVariation_percent", getCoefficientOfVariation());
    map.put("productionStatistics", production);

    // Availability statistics
    Map<String, Object> availability = new HashMap<String, Object>();
    availability.put("mean_percent", meanAvailability);
    availability.put("min_percent", minAvailability);
    availability.put("max_percent", maxAvailability);
    map.put("availabilityStatistics", availability);

    // Failure statistics
    Map<String, Object> failures = new HashMap<String, Object>();
    failures.put("meanFailureCount", meanFailureCount);
    failures.put("meanDowntimeHours", meanDowntimeHours);
    failures.put("maxDowntimeHours", maxDowntimeHours);
    map.put("failureStatistics", failures);

    // Equipment availability
    map.put("equipmentAvailability", equipmentAvailability);

    return map;
  }

  /**
   * Serializes the result to JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Operational Risk Simulation Results ===\n");
    sb.append(
        String.format("Iterations: %d, Time Horizon: %.0f days%n", iterations, timeHorizonDays));
    sb.append(String.format("%nProduction Statistics:%n"));
    sb.append(String.format("  Baseline Rate: %.2f kg/hr%n", baselineProductionRate));
    sb.append(String.format("  Max Possible:  %.2f kg%n", maxPossibleProduction));
    sb.append(String.format("  Mean:          %.2f kg%n", meanProduction));
    sb.append(String.format("  P10/P50/P90:   %.2f / %.2f / %.2f kg%n", p10Production,
        p50Production, p90Production));
    sb.append(String.format("  Efficiency:    %.1f%%%n", getProductionEfficiency()));
    sb.append(String.format("%nAvailability:%n"));
    sb.append(String.format("  Mean: %.2f%%, Range: %.2f%% - %.2f%%%n", meanAvailability,
        minAvailability, maxAvailability));
    sb.append(String.format("%nFailure Events:%n"));
    sb.append(String.format("  Mean Count:    %.1f%n", meanFailureCount));
    sb.append(String.format("  Mean Downtime: %.1f hours%n", meanDowntimeHours));

    if (!equipmentAvailability.isEmpty()) {
      sb.append(String.format("%nEquipment Availability:%n"));
      for (Map.Entry<String, Double> entry : equipmentAvailability.entrySet()) {
        sb.append(String.format("  %s: %.2f%%%n", entry.getKey(), entry.getValue()));
      }
    }

    return sb.toString();
  }
}
