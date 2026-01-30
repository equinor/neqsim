package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.failure.EquipmentFailureMode;

/**
 * Result of a production impact analysis for equipment failure scenarios.
 *
 * <p>
 * This class contains comprehensive information about the impact of equipment failure on
 * production, including:
 * </p>
 * <ul>
 * <li>Baseline (normal) production rates</li>
 * <li>Production with failed equipment</li>
 * <li>Production loss (absolute and percentage)</li>
 * <li>Comparison to full plant shutdown</li>
 * <li>Economic impact estimates</li>
 * <li>Recommended operating mode</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProductionImpactResult implements Serializable {

  private static final long serialVersionUID = 1000L;

  /**
   * Recommended operating mode after equipment failure.
   */
  public enum RecommendedAction {
    /**
     * Continue at full rate - failure has minimal impact.
     */
    CONTINUE_NORMAL,

    /**
     * Reduce throughput to match new constraints.
     */
    REDUCE_THROUGHPUT,

    /**
     * Shutdown affected train/section only.
     */
    PARTIAL_SHUTDOWN,

    /**
     * Full plant shutdown required.
     */
    FULL_SHUTDOWN,

    /**
     * Reroute flow to bypass failed equipment.
     */
    BYPASS_EQUIPMENT,

    /**
     * Switch to standby/spare equipment.
     */
    USE_STANDBY
  }

  // Equipment identification
  private String equipmentName;
  private String equipmentType;
  private EquipmentFailureMode failureMode;

  // Production rates (in kg/hr)
  private double baselineProductionRate;
  private double productionWithFailure;
  private double optimizedProductionWithFailure;
  private double fullShutdownProduction;

  // Production loss metrics
  private double absoluteLoss;
  private double percentLoss;
  private double lossVsFullShutdown;

  // Economic impact (optional)
  private double economicLossPerHour;
  private double economicLossPerDay;
  private double productPricePerKg;

  // Bottleneck analysis
  private String newBottleneck;
  private double newBottleneckUtilization;
  private String originalBottleneck;
  private double originalBottleneckUtilization;

  // Operational recommendations
  private RecommendedAction recommendedAction;
  private String recommendationReason;
  private Map<String, Double> optimizedSetpoints;
  private List<String> affectedEquipment;

  // Power and efficiency
  private double baselinePower;
  private double powerWithFailure;
  private double powerSavings;
  private double specificEnergyBaseline;
  private double specificEnergyWithFailure;

  // Time estimates
  private double estimatedRecoveryTime;
  private double timeToImplementChanges;

  // Analysis metadata
  private long analysisTimestamp;
  private double analysisComputeTime;
  private boolean converged;
  private String analysisNotes;

  /**
   * Creates a new production impact result.
   */
  public ProductionImpactResult() {
    this.optimizedSetpoints = new HashMap<String, Double>();
    this.affectedEquipment = new ArrayList<String>();
    this.analysisTimestamp = System.currentTimeMillis();
    this.converged = true;
    this.recommendedAction = RecommendedAction.REDUCE_THROUGHPUT;
  }

  /**
   * Creates a production impact result for a specific equipment failure.
   *
   * @param equipmentName name of the failed equipment
   * @param failureMode the failure mode
   */
  public ProductionImpactResult(String equipmentName, EquipmentFailureMode failureMode) {
    this();
    this.equipmentName = equipmentName;
    this.failureMode = failureMode;
  }

  // Calculation methods

  /**
   * Calculates derived metrics from the set production values.
   */
  public void calculateDerivedMetrics() {
    // Absolute loss
    this.absoluteLoss = baselineProductionRate - productionWithFailure;

    // Percent loss
    if (baselineProductionRate > 0) {
      this.percentLoss = (absoluteLoss / baselineProductionRate) * 100.0;
    }

    // Loss vs full shutdown (negative means degraded operation is better than shutdown)
    this.lossVsFullShutdown = productionWithFailure - fullShutdownProduction;

    // Economic calculations
    if (productPricePerKg > 0) {
      this.economicLossPerHour = absoluteLoss * productPricePerKg;
      this.economicLossPerDay = economicLossPerHour * 24.0;
    }

    // Power savings/increase
    this.powerSavings = baselinePower - powerWithFailure;

    // Specific energy
    if (baselineProductionRate > 0) {
      this.specificEnergyBaseline = baselinePower / baselineProductionRate;
    }
    if (productionWithFailure > 0) {
      this.specificEnergyWithFailure = powerWithFailure / productionWithFailure;
    }

    // Determine recommended action
    determineRecommendedAction();
  }

  private void determineRecommendedAction() {
    // Decision logic for recommended action
    if (percentLoss < 1.0) {
      recommendedAction = RecommendedAction.CONTINUE_NORMAL;
      recommendationReason = "Production impact is minimal (<1% loss)";
    } else if (productionWithFailure <= 0) {
      recommendedAction = RecommendedAction.FULL_SHUTDOWN;
      recommendationReason = "No production possible with failed equipment";
    } else if (percentLoss < 20.0 && lossVsFullShutdown > 0) {
      recommendedAction = RecommendedAction.REDUCE_THROUGHPUT;
      recommendationReason = String
          .format("Degraded operation produces %.1f kg/hr more than shutdown", lossVsFullShutdown);
    } else if (percentLoss >= 50.0 && lossVsFullShutdown < absoluteLoss * 0.1) {
      recommendedAction = RecommendedAction.FULL_SHUTDOWN;
      recommendationReason = "Large production loss with marginal benefit vs shutdown";
    } else {
      recommendedAction = RecommendedAction.REDUCE_THROUGHPUT;
      recommendationReason =
          String.format("Reduce throughput to %.1f%% of normal", 100.0 - percentLoss);
    }
  }

  // Getters and setters

  /**
   * Gets the equipment name.
   *
   * @return the equipment name
   */
  public String getEquipmentName() {
    return equipmentName;
  }

  /**
   * Sets the equipment name.
   *
   * @param equipmentName the equipment name
   */
  public void setEquipmentName(String equipmentName) {
    this.equipmentName = equipmentName;
  }

  /**
   * Gets the equipment type.
   *
   * @return the equipment type
   */
  public String getEquipmentType() {
    return equipmentType;
  }

  /**
   * Sets the equipment type.
   *
   * @param equipmentType the equipment type
   */
  public void setEquipmentType(String equipmentType) {
    this.equipmentType = equipmentType;
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
   * @param failureMode the failure mode
   */
  public void setFailureMode(EquipmentFailureMode failureMode) {
    this.failureMode = failureMode;
  }

  /**
   * Gets the baseline production rate in kg/hr.
   *
   * @return the baseline rate
   */
  public double getBaselineProductionRate() {
    return baselineProductionRate;
  }

  /**
   * Sets the baseline production rate.
   *
   * @param rate the rate in kg/hr
   */
  public void setBaselineProductionRate(double rate) {
    this.baselineProductionRate = rate;
  }

  /**
   * Gets the production rate with failure in kg/hr.
   *
   * @return the production with failure
   */
  public double getProductionWithFailure() {
    return productionWithFailure;
  }

  /**
   * Sets the production rate with failure.
   *
   * @param rate the rate in kg/hr
   */
  public void setProductionWithFailure(double rate) {
    this.productionWithFailure = rate;
  }

  /**
   * Gets the optimized production with failure in kg/hr.
   *
   * @return the optimized production
   */
  public double getOptimizedProductionWithFailure() {
    return optimizedProductionWithFailure;
  }

  /**
   * Sets the optimized production with failure.
   *
   * @param rate the rate in kg/hr
   */
  public void setOptimizedProductionWithFailure(double rate) {
    this.optimizedProductionWithFailure = rate;
  }

  /**
   * Gets the full shutdown production (usually 0).
   *
   * @return the shutdown production
   */
  public double getFullShutdownProduction() {
    return fullShutdownProduction;
  }

  /**
   * Sets the full shutdown production.
   *
   * @param rate the rate in kg/hr
   */
  public void setFullShutdownProduction(double rate) {
    this.fullShutdownProduction = rate;
  }

  /**
   * Gets the absolute production loss in kg/hr.
   *
   * @return the absolute loss
   */
  public double getAbsoluteLoss() {
    return absoluteLoss;
  }

  /**
   * Gets the production loss percentage.
   *
   * @return the percent loss
   */
  public double getPercentLoss() {
    return percentLoss;
  }

  /**
   * Gets the production advantage over full shutdown.
   *
   * @return positive if degraded operation is better than shutdown
   */
  public double getLossVsFullShutdown() {
    return lossVsFullShutdown;
  }

  /**
   * Gets the economic loss per hour.
   *
   * @return the economic loss per hour
   */
  public double getEconomicLossPerHour() {
    return economicLossPerHour;
  }

  /**
   * Gets the economic loss per day.
   *
   * @return the economic loss per day
   */
  public double getEconomicLossPerDay() {
    return economicLossPerDay;
  }

  /**
   * Sets the product price for economic calculations.
   *
   * @param pricePerKg price per kg of product
   */
  public void setProductPricePerKg(double pricePerKg) {
    this.productPricePerKg = pricePerKg;
  }

  /**
   * Gets the new bottleneck equipment name.
   *
   * @return the new bottleneck
   */
  public String getNewBottleneck() {
    return newBottleneck;
  }

  /**
   * Sets the new bottleneck equipment name.
   *
   * @param bottleneck the new bottleneck
   */
  public void setNewBottleneck(String bottleneck) {
    this.newBottleneck = bottleneck;
  }

  /**
   * Gets the new bottleneck utilization.
   *
   * @return the utilization fraction
   */
  public double getNewBottleneckUtilization() {
    return newBottleneckUtilization;
  }

  /**
   * Sets the new bottleneck utilization.
   *
   * @param utilization the utilization fraction
   */
  public void setNewBottleneckUtilization(double utilization) {
    this.newBottleneckUtilization = utilization;
  }

  /**
   * Gets the original bottleneck equipment name.
   *
   * @return the original bottleneck
   */
  public String getOriginalBottleneck() {
    return originalBottleneck;
  }

  /**
   * Sets the original bottleneck equipment name.
   *
   * @param bottleneck the original bottleneck
   */
  public void setOriginalBottleneck(String bottleneck) {
    this.originalBottleneck = bottleneck;
  }

  /**
   * Gets the original bottleneck utilization.
   *
   * @return the utilization fraction
   */
  public double getOriginalBottleneckUtilization() {
    return originalBottleneckUtilization;
  }

  /**
   * Sets the original bottleneck utilization.
   *
   * @param utilization the utilization fraction
   */
  public void setOriginalBottleneckUtilization(double utilization) {
    this.originalBottleneckUtilization = utilization;
  }

  /**
   * Gets the recommended action.
   *
   * @return the recommended action
   */
  public RecommendedAction getRecommendedAction() {
    return recommendedAction;
  }

  /**
   * Sets the recommended action.
   *
   * @param action the recommended action
   */
  public void setRecommendedAction(RecommendedAction action) {
    this.recommendedAction = action;
  }

  /**
   * Gets the recommendation reason.
   *
   * @return the reason
   */
  public String getRecommendationReason() {
    return recommendationReason;
  }

  /**
   * Sets the recommendation reason.
   *
   * @param reason the reason
   */
  public void setRecommendationReason(String reason) {
    this.recommendationReason = reason;
  }

  /**
   * Gets the optimized setpoints map.
   *
   * @return map of equipment name to recommended setpoint
   */
  public Map<String, Double> getOptimizedSetpoints() {
    return Collections.unmodifiableMap(optimizedSetpoints);
  }

  /**
   * Adds an optimized setpoint.
   *
   * @param equipmentName the equipment name
   * @param setpoint the recommended setpoint
   */
  public void addOptimizedSetpoint(String equipmentName, double setpoint) {
    this.optimizedSetpoints.put(equipmentName, setpoint);
  }

  /**
   * Gets the list of affected equipment.
   *
   * @return list of affected equipment names
   */
  public List<String> getAffectedEquipment() {
    return Collections.unmodifiableList(affectedEquipment);
  }

  /**
   * Adds affected equipment.
   *
   * @param equipmentName the equipment name
   */
  public void addAffectedEquipment(String equipmentName) {
    if (!affectedEquipment.contains(equipmentName)) {
      this.affectedEquipment.add(equipmentName);
    }
  }

  /**
   * Gets the baseline power consumption in kW.
   *
   * @return the baseline power
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
   * Gets the power consumption with failure in kW.
   *
   * @return the power with failure
   */
  public double getPowerWithFailure() {
    return powerWithFailure;
  }

  /**
   * Sets the power consumption with failure.
   *
   * @param power the power in kW
   */
  public void setPowerWithFailure(double power) {
    this.powerWithFailure = power;
  }

  /**
   * Gets the power savings (positive if less power used).
   *
   * @return the power savings in kW
   */
  public double getPowerSavings() {
    return powerSavings;
  }

  /**
   * Gets the estimated recovery time in hours.
   *
   * @return the recovery time
   */
  public double getEstimatedRecoveryTime() {
    return estimatedRecoveryTime;
  }

  /**
   * Sets the estimated recovery time.
   *
   * @param hours the recovery time in hours
   */
  public void setEstimatedRecoveryTime(double hours) {
    this.estimatedRecoveryTime = hours;
  }

  /**
   * Gets the time to implement operational changes in hours.
   *
   * @return the time to implement
   */
  public double getTimeToImplementChanges() {
    return timeToImplementChanges;
  }

  /**
   * Sets the time to implement changes.
   *
   * @param hours the time in hours
   */
  public void setTimeToImplementChanges(double hours) {
    this.timeToImplementChanges = hours;
  }

  /**
   * Checks if the analysis converged.
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
   * Gets the analysis notes.
   *
   * @return the notes
   */
  public String getAnalysisNotes() {
    return analysisNotes;
  }

  /**
   * Sets the analysis notes.
   *
   * @param notes the notes
   */
  public void setAnalysisNotes(String notes) {
    this.analysisNotes = notes;
  }

  /**
   * Sets the analysis compute time.
   *
   * @param timeMs the time in milliseconds
   */
  public void setAnalysisComputeTime(double timeMs) {
    this.analysisComputeTime = timeMs;
  }

  /**
   * Gets the analysis compute time.
   *
   * @return the time in milliseconds
   */
  public double getAnalysisComputeTime() {
    return analysisComputeTime;
  }

  /**
   * Converts the result to a Map for easy access.
   *
   * @return map of result properties
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<String, Object>();

    // Equipment info
    map.put("equipmentName", equipmentName);
    map.put("equipmentType", equipmentType);
    map.put("failureMode", failureMode != null ? failureMode.getName() : null);

    // Production rates
    map.put("baselineProductionRate_kg_hr", baselineProductionRate);
    map.put("productionWithFailure_kg_hr", productionWithFailure);
    map.put("optimizedProductionWithFailure_kg_hr", optimizedProductionWithFailure);
    map.put("fullShutdownProduction_kg_hr", fullShutdownProduction);

    // Loss metrics
    map.put("absoluteLoss_kg_hr", absoluteLoss);
    map.put("percentLoss", percentLoss);
    map.put("lossVsFullShutdown_kg_hr", lossVsFullShutdown);

    // Economic
    map.put("economicLossPerHour", economicLossPerHour);
    map.put("economicLossPerDay", economicLossPerDay);

    // Bottleneck
    map.put("originalBottleneck", originalBottleneck);
    map.put("originalBottleneckUtilization", originalBottleneckUtilization);
    map.put("newBottleneck", newBottleneck);
    map.put("newBottleneckUtilization", newBottleneckUtilization);

    // Recommendations
    map.put("recommendedAction", recommendedAction.name());
    map.put("recommendationReason", recommendationReason);
    map.put("optimizedSetpoints", optimizedSetpoints);
    map.put("affectedEquipment", affectedEquipment);

    // Power
    map.put("baselinePower_kW", baselinePower);
    map.put("powerWithFailure_kW", powerWithFailure);
    map.put("powerSavings_kW", powerSavings);

    // Time estimates
    map.put("estimatedRecoveryTime_hours", estimatedRecoveryTime);
    map.put("timeToImplementChanges_hours", timeToImplementChanges);

    // Metadata
    map.put("converged", converged);
    map.put("analysisNotes", analysisNotes);

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
    sb.append("=== Production Impact Analysis ===\n");
    sb.append(String.format("Equipment: %s (%s)\n", equipmentName,
        failureMode != null ? failureMode.getName() : "Unknown"));
    sb.append(String.format("Baseline Production: %.1f kg/hr\n", baselineProductionRate));
    sb.append(String.format("Production with Failure: %.1f kg/hr\n", productionWithFailure));
    sb.append(String.format("Production Loss: %.1f kg/hr (%.1f%%)\n", absoluteLoss, percentLoss));
    sb.append(String.format("vs Full Shutdown: %+.1f kg/hr\n", lossVsFullShutdown));
    sb.append(String.format("Recommended Action: %s\n", recommendedAction));
    sb.append(String.format("Reason: %s\n", recommendationReason));
    if (newBottleneck != null) {
      sb.append(String.format("New Bottleneck: %s (%.1f%% utilized)\n", newBottleneck,
          newBottleneckUtilization * 100));
    }
    return sb.toString();
  }
}
