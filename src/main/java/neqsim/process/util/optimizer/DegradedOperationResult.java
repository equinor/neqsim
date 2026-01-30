package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.failure.EquipmentFailureMode;
import neqsim.process.util.optimizer.DegradedOperationOptimizer.OperatingMode;

/**
 * Result of degraded operation optimization.
 *
 * <p>
 * Contains the optimal operating point, recommended setpoints, and metrics for running with
 * equipment failures or reduced capacity.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class DegradedOperationResult implements Serializable {

  private static final long serialVersionUID = 1000L;

  // Equipment info
  private String failedEquipment;
  private EquipmentFailureMode failureMode;

  // Baseline values
  private double baselineProduction;
  private double baselinePower;

  // Optimal values
  private double optimalFlowRate;
  private double optimalProduction;
  private double optimalPower;

  // Metrics
  private double productionLossPercent;
  private double powerSavingsPercent;
  private double capacityFactor;

  // Operating mode
  private OperatingMode operatingMode;

  // Setpoints
  private Map<String, Double> optimizedSetpoints;

  // Metadata
  private boolean converged;
  private String notes;
  private long computeTimeMs;

  /**
   * Creates a new degraded operation result.
   */
  public DegradedOperationResult() {
    this.optimizedSetpoints = new HashMap<String, Double>();
    this.converged = false;
    this.operatingMode = OperatingMode.REDUCED_CAPACITY;
  }

  /**
   * Calculates derived metrics from the set values.
   */
  public void calculateMetrics() {
    if (baselineProduction > 0) {
      productionLossPercent = ((baselineProduction - optimalProduction) / baselineProduction) * 100;
      capacityFactor = optimalProduction / baselineProduction;
    }
    if (baselinePower > 0) {
      powerSavingsPercent = ((baselinePower - optimalPower) / baselinePower) * 100;
    }
  }

  // Getters and setters

  /**
   * Gets the failed equipment name.
   *
   * @return the failed equipment name
   */
  public String getFailedEquipment() {
    return failedEquipment;
  }

  /**
   * Sets the failed equipment name.
   *
   * @param name the equipment name
   */
  public void setFailedEquipment(String name) {
    this.failedEquipment = name;
  }

  /**
   * Gets the failure mode.
   *
   * @return the failure mode
   */
  public EquipmentFailureMode getFailureMode() {
    return failureMode;
  }

  /**
   * Sets the failure mode.
   *
   * @param mode the failure mode
   */
  public void setFailureMode(EquipmentFailureMode mode) {
    this.failureMode = mode;
  }

  /**
   * Gets the baseline production rate.
   *
   * @return the baseline production in kg/hr
   */
  public double getBaselineProduction() {
    return baselineProduction;
  }

  /**
   * Sets the baseline production rate.
   *
   * @param rate the rate in kg/hr
   */
  public void setBaselineProduction(double rate) {
    this.baselineProduction = rate;
  }

  /**
   * Gets the baseline power consumption.
   *
   * @return the baseline power in kW
   */
  public double getBaselinePower() {
    return baselinePower;
  }

  /**
   * Sets the baseline power consumption.
   *
   * @param power the power in kW
   */
  public void setBaselinePower(double power) {
    this.baselinePower = power;
  }

  /**
   * Gets the optimal flow rate.
   *
   * @return the optimal flow rate in kg/hr
   */
  public double getOptimalFlowRate() {
    return optimalFlowRate;
  }

  /**
   * Sets the optimal flow rate.
   *
   * @param rate the rate in kg/hr
   */
  public void setOptimalFlowRate(double rate) {
    this.optimalFlowRate = rate;
  }

  /**
   * Gets the optimal production rate.
   *
   * @return the optimal production in kg/hr
   */
  public double getOptimalProduction() {
    return optimalProduction;
  }

  /**
   * Sets the optimal production rate.
   *
   * @param rate the rate in kg/hr
   */
  public void setOptimalProduction(double rate) {
    this.optimalProduction = rate;
  }

  /**
   * Gets the optimal power consumption.
   *
   * @return the optimal power in kW
   */
  public double getOptimalPower() {
    return optimalPower;
  }

  /**
   * Sets the optimal power consumption.
   *
   * @param power the power in kW
   */
  public void setOptimalPower(double power) {
    this.optimalPower = power;
  }

  /**
   * Gets the production loss percentage.
   *
   * @return the loss percentage
   */
  public double getProductionLossPercent() {
    return productionLossPercent;
  }

  /**
   * Gets the power savings percentage.
   *
   * @return the savings percentage
   */
  public double getPowerSavingsPercent() {
    return powerSavingsPercent;
  }

  /**
   * Gets the capacity factor.
   *
   * @return the capacity factor (0-1)
   */
  public double getCapacityFactor() {
    return capacityFactor;
  }

  /**
   * Gets the recommended operating mode.
   *
   * @return the operating mode
   */
  public OperatingMode getOperatingMode() {
    return operatingMode;
  }

  /**
   * Sets the operating mode.
   *
   * @param mode the operating mode
   */
  public void setOperatingMode(OperatingMode mode) {
    this.operatingMode = mode;
  }

  /**
   * Gets the optimized setpoints.
   *
   * @return map of equipment/parameter to recommended value
   */
  public Map<String, Double> getOptimizedSetpoints() {
    return new HashMap<String, Double>(optimizedSetpoints);
  }

  /**
   * Sets the optimized setpoints.
   *
   * @param setpoints the setpoints map
   */
  public void setOptimizedSetpoints(Map<String, Double> setpoints) {
    this.optimizedSetpoints = new HashMap<String, Double>(setpoints);
  }

  /**
   * Checks if optimization converged.
   *
   * @return true if converged
   */
  public boolean isConverged() {
    return converged;
  }

  /**
   * Sets the convergence status.
   *
   * @param converged true if converged
   */
  public void setConverged(boolean converged) {
    this.converged = converged;
  }

  /**
   * Gets the notes.
   *
   * @return the notes
   */
  public String getNotes() {
    return notes;
  }

  /**
   * Sets the notes.
   *
   * @param notes the notes
   */
  public void setNotes(String notes) {
    this.notes = notes;
  }

  /**
   * Gets the compute time.
   *
   * @return the compute time in milliseconds
   */
  public long getComputeTimeMs() {
    return computeTimeMs;
  }

  /**
   * Sets the compute time.
   *
   * @param timeMs the time in milliseconds
   */
  public void setComputeTimeMs(long timeMs) {
    this.computeTimeMs = timeMs;
  }

  /**
   * Converts to a Map representation.
   *
   * @return map of result properties
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<String, Object>();

    map.put("failedEquipment", failedEquipment);
    map.put("failureMode", failureMode != null ? failureMode.getName() : null);

    map.put("baselineProduction_kg_hr", baselineProduction);
    map.put("baselinePower_kW", baselinePower);

    map.put("optimalFlowRate_kg_hr", optimalFlowRate);
    map.put("optimalProduction_kg_hr", optimalProduction);
    map.put("optimalPower_kW", optimalPower);

    map.put("productionLossPercent", productionLossPercent);
    map.put("powerSavingsPercent", powerSavingsPercent);
    map.put("capacityFactor", capacityFactor);

    map.put("operatingMode", operatingMode.name());
    map.put("optimizedSetpoints", optimizedSetpoints);

    map.put("converged", converged);
    map.put("computeTimeMs", computeTimeMs);

    return map;
  }

  /**
   * Converts to JSON string.
   *
   * @return JSON representation
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== Degraded Operation Optimization ===\n");
    sb.append(String.format("Failed Equipment: %s%n", failedEquipment));
    sb.append(String.format("Operating Mode: %s%n", operatingMode));
    sb.append(
        String.format("%nBaseline: %.0f kg/hr, %.0f kW%n", baselineProduction, baselinePower));
    sb.append(String.format("Optimal:  %.0f kg/hr, %.0f kW%n", optimalProduction, optimalPower));
    sb.append(String.format("Loss: %.1f%%, Power Savings: %.1f%%%n", productionLossPercent,
        powerSavingsPercent));
    sb.append(String.format("Capacity Factor: %.1f%%%n", capacityFactor * 100));
    if (!optimizedSetpoints.isEmpty()) {
      sb.append("\nRecommended Setpoints:\n");
      for (Map.Entry<String, Double> entry : optimizedSetpoints.entrySet()) {
        sb.append(String.format("  %s: %.2f%n", entry.getKey(), entry.getValue()));
      }
    }
    return sb.toString();
  }
}
