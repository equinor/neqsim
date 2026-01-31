package neqsim.process.safety.risk.condition;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Condition-Based Reliability Model.
 *
 * <p>
 * Provides dynamic reliability estimation based on real-time equipment condition data. Integrates
 * with process monitoring to update failure probabilities based on actual operating conditions
 * rather than generic OREDA data.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ConditionBasedReliability implements Serializable {

  private static final long serialVersionUID = 1000L;

  /** Equipment identifier. */
  private String equipmentId;

  /** Equipment name. */
  private String equipmentName;

  /** Base failure rate (failures/hour). */
  private double baseFailureRate;

  /** Current adjusted failure rate. */
  private double adjustedFailureRate;

  /** Health index (0-1, 1 = perfect health). */
  private double healthIndex = 1.0;

  /** Condition indicators. */
  private List<ConditionIndicator> indicators;

  /** Degradation model type. */
  private DegradationModel degradationModel = DegradationModel.LINEAR;

  /** Remaining useful life estimate (hours). */
  private double remainingUsefulLife;

  /** Confidence in RUL estimate (0-1). */
  private double rulConfidence;

  /** History of health indices. */
  private List<HealthRecord> healthHistory;

  /** Last update timestamp. */
  private Instant lastUpdated;

  /**
   * Degradation model types.
   */
  public enum DegradationModel {
    LINEAR, EXPONENTIAL, WEIBULL, MACHINE_LEARNING
  }

  /**
   * Condition indicator for equipment health monitoring.
   */
  public static class ConditionIndicator implements Serializable {
    private static final long serialVersionUID = 1L;

    private String indicatorId;
    private String name;
    private IndicatorType type;
    private double currentValue;
    private double normalValue;
    private double warningThreshold;
    private double criticalThreshold;
    private double weight; // Weight for overall health calculation
    private boolean alarming;
    private boolean critical;

    public enum IndicatorType {
      VIBRATION, TEMPERATURE, PRESSURE, FLOW, CURRENT, WEAR, CORROSION, EFFICIENCY, ACOUSTIC, OIL_ANALYSIS, CUSTOM
    }

    public ConditionIndicator(String id, String name, IndicatorType type) {
      this.indicatorId = id;
      this.name = name;
      this.type = type;
      this.weight = 1.0;
    }

    public void setThresholds(double normal, double warning, double critical) {
      this.normalValue = normal;
      this.warningThreshold = warning;
      this.criticalThreshold = critical;
    }

    public void updateValue(double value) {
      this.currentValue = value;
      this.alarming = (criticalThreshold > normalValue) ? (value > warningThreshold)
          : (value < warningThreshold);
      this.critical = (criticalThreshold > normalValue) ? (value > criticalThreshold)
          : (value < criticalThreshold);
    }

    /**
     * Gets health contribution (0-1).
     *
     * @return health contribution
     */
    public double getHealthContribution() {
      if (criticalThreshold == normalValue) {
        return 1.0;
      }

      double deviation;
      if (criticalThreshold > normalValue) {
        // Higher is worse (e.g., temperature, vibration)
        if (currentValue <= normalValue) {
          return 1.0;
        }
        if (currentValue >= criticalThreshold) {
          return 0.0;
        }
        deviation = (currentValue - normalValue) / (criticalThreshold - normalValue);
      } else {
        // Lower is worse (e.g., efficiency)
        if (currentValue >= normalValue) {
          return 1.0;
        }
        if (currentValue <= criticalThreshold) {
          return 0.0;
        }
        deviation = (normalValue - currentValue) / (normalValue - criticalThreshold);
      }

      return Math.max(0, 1.0 - deviation);
    }

    // Getters
    public String getIndicatorId() {
      return indicatorId;
    }

    public String getName() {
      return name;
    }

    public IndicatorType getType() {
      return type;
    }

    public double getCurrentValue() {
      return currentValue;
    }

    public double getNormalValue() {
      return normalValue;
    }

    public double getWarningThreshold() {
      return warningThreshold;
    }

    public double getCriticalThreshold() {
      return criticalThreshold;
    }

    public double getWeight() {
      return weight;
    }

    public void setWeight(double weight) {
      this.weight = weight;
    }

    public boolean isAlarming() {
      return alarming;
    }

    public boolean isCritical() {
      return critical;
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("indicatorId", indicatorId);
      map.put("name", name);
      map.put("type", type.name());
      map.put("currentValue", currentValue);
      map.put("normalValue", normalValue);
      map.put("warningThreshold", warningThreshold);
      map.put("criticalThreshold", criticalThreshold);
      map.put("weight", weight);
      map.put("healthContribution", getHealthContribution());
      map.put("alarming", alarming);
      map.put("critical", critical);
      return map;
    }
  }

  /**
   * Health record for historical tracking.
   */
  public static class HealthRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private Instant timestamp;
    private double healthIndex;
    private double adjustedFailureRate;

    public HealthRecord(Instant timestamp, double health, double failureRate) {
      this.timestamp = timestamp;
      this.healthIndex = health;
      this.adjustedFailureRate = failureRate;
    }

    public Instant getTimestamp() {
      return timestamp;
    }

    public double getHealthIndex() {
      return healthIndex;
    }

    public double getAdjustedFailureRate() {
      return adjustedFailureRate;
    }
  }

  /**
   * Creates a condition-based reliability model.
   *
   * @param equipmentId equipment identifier
   * @param equipmentName equipment name
   * @param baseFailureRate base failure rate (failures/hour)
   */
  public ConditionBasedReliability(String equipmentId, String equipmentName,
      double baseFailureRate) {
    this.equipmentId = equipmentId;
    this.equipmentName = equipmentName;
    this.baseFailureRate = baseFailureRate;
    this.adjustedFailureRate = baseFailureRate;
    this.indicators = new ArrayList<>();
    this.healthHistory = new ArrayList<>();
    this.lastUpdated = Instant.now();
  }

  /**
   * Adds a condition indicator.
   *
   * @param indicator condition indicator
   */
  public void addIndicator(ConditionIndicator indicator) {
    indicators.add(indicator);
  }

  /**
   * Creates and adds a vibration indicator.
   *
   * @param id indicator ID
   * @param name indicator name
   * @param normal normal value (mm/s RMS)
   * @param warning warning threshold
   * @param critical critical threshold
   * @return created indicator
   */
  public ConditionIndicator addVibrationIndicator(String id, String name, double normal,
      double warning, double critical) {
    ConditionIndicator indicator =
        new ConditionIndicator(id, name, ConditionIndicator.IndicatorType.VIBRATION);
    indicator.setThresholds(normal, warning, critical);
    indicators.add(indicator);
    return indicator;
  }

  /**
   * Creates and adds a temperature indicator.
   *
   * @param id indicator ID
   * @param name indicator name
   * @param normal normal temperature (C)
   * @param warning warning threshold
   * @param critical critical threshold
   * @return created indicator
   */
  public ConditionIndicator addTemperatureIndicator(String id, String name, double normal,
      double warning, double critical) {
    ConditionIndicator indicator =
        new ConditionIndicator(id, name, ConditionIndicator.IndicatorType.TEMPERATURE);
    indicator.setThresholds(normal, warning, critical);
    indicators.add(indicator);
    return indicator;
  }

  /**
   * Updates indicator value and recalculates health.
   *
   * @param indicatorId indicator ID
   * @param value new value
   */
  public void updateIndicator(String indicatorId, double value) {
    for (ConditionIndicator indicator : indicators) {
      if (indicator.getIndicatorId().equals(indicatorId)) {
        indicator.updateValue(value);
        break;
      }
    }
    recalculateHealth();
  }

  /**
   * Updates all indicators from a map.
   *
   * @param values map of indicator ID to value
   */
  public void updateIndicators(Map<String, Double> values) {
    for (Map.Entry<String, Double> entry : values.entrySet()) {
      for (ConditionIndicator indicator : indicators) {
        if (indicator.getIndicatorId().equals(entry.getKey())) {
          indicator.updateValue(entry.getValue());
          break;
        }
      }
    }
    recalculateHealth();
  }

  /**
   * Recalculates health index and adjusted failure rate.
   */
  public void recalculateHealth() {
    if (indicators.isEmpty()) {
      healthIndex = 1.0;
      adjustedFailureRate = baseFailureRate;
      return;
    }

    // Weighted average of health contributions
    double weightedSum = 0;
    double totalWeight = 0;
    int criticalCount = 0;

    for (ConditionIndicator indicator : indicators) {
      weightedSum += indicator.getHealthContribution() * indicator.getWeight();
      totalWeight += indicator.getWeight();
      if (indicator.isCritical()) {
        criticalCount++;
      }
    }

    healthIndex = totalWeight > 0 ? weightedSum / totalWeight : 1.0;

    // Apply degradation model to get failure rate multiplier
    double multiplier = calculateFailureRateMultiplier(healthIndex);

    // Critical indicators significantly increase failure rate
    if (criticalCount > 0) {
      multiplier *= (1 + criticalCount);
    }

    adjustedFailureRate = baseFailureRate * multiplier;

    // Estimate remaining useful life
    estimateRUL();

    // Record history
    lastUpdated = Instant.now();
    healthHistory.add(new HealthRecord(lastUpdated, healthIndex, adjustedFailureRate));

    // Keep history manageable
    while (healthHistory.size() > 1000) {
      healthHistory.remove(0);
    }
  }

  private double calculateFailureRateMultiplier(double health) {
    switch (degradationModel) {
      case LINEAR:
        // Linear increase as health decreases
        return 1.0 + (1.0 - health) * 4.0; // 1x at health=1, 5x at health=0
      case EXPONENTIAL:
        // Exponential increase at low health
        return Math.exp((1.0 - health) * 2.0);
      case WEIBULL:
        // Weibull-like behavior (bathtub curve)
        double beta = 2.0; // Shape parameter
        return Math.pow(1.0 / Math.max(health, 0.01), beta);
      case MACHINE_LEARNING:
        // Placeholder - would use ML model in production
        return 1.0 + (1.0 - health) * 3.0;
      default:
        return 1.0;
    }
  }

  private void estimateRUL() {
    if (healthHistory.size() < 10) {
      remainingUsefulLife = Double.NaN;
      rulConfidence = 0;
      return;
    }

    // Simple linear extrapolation of health trend
    int n = Math.min(100, healthHistory.size());
    List<HealthRecord> recent =
        healthHistory.subList(healthHistory.size() - n, healthHistory.size());

    double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
    for (int i = 0; i < n; i++) {
      sumX += i;
      sumY += recent.get(i).getHealthIndex();
      sumXY += i * recent.get(i).getHealthIndex();
      sumX2 += i * i;
    }

    double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);

    if (slope >= 0) {
      // Health not degrading
      remainingUsefulLife = Double.POSITIVE_INFINITY;
      rulConfidence = 0.5;
    } else {
      // Estimate time to reach critical health (0.2)
      double currentHealth = recent.get(n - 1).getHealthIndex();
      double criticalHealth = 0.2;
      double hoursPerRecord = 1.0; // Assume hourly records

      remainingUsefulLife = (currentHealth - criticalHealth) / (-slope) * hoursPerRecord;
      remainingUsefulLife = Math.max(0, remainingUsefulLife);

      // Confidence based on fit quality and data amount
      rulConfidence = Math.min(0.9, 0.5 + n / 200.0);
    }
  }

  /**
   * Gets probability of failure before specified time.
   *
   * @param hours time horizon in hours
   * @return probability of failure
   */
  public double getProbabilityOfFailure(double hours) {
    // Exponential distribution with adjusted rate
    return 1.0 - Math.exp(-adjustedFailureRate * hours);
  }

  /**
   * Gets mean time to failure (MTTF).
   *
   * @return MTTF in hours
   */
  public double getMTTF() {
    return 1.0 / adjustedFailureRate;
  }

  /**
   * Gets all alarming indicators.
   *
   * @return list of alarming indicators
   */
  public List<ConditionIndicator> getAlarmingIndicators() {
    List<ConditionIndicator> alarming = new ArrayList<>();
    for (ConditionIndicator indicator : indicators) {
      if (indicator.isAlarming()) {
        alarming.add(indicator);
      }
    }
    return alarming;
  }

  /**
   * Gets all critical indicators.
   *
   * @return list of critical indicators
   */
  public List<ConditionIndicator> getCriticalIndicators() {
    List<ConditionIndicator> critical = new ArrayList<>();
    for (ConditionIndicator indicator : indicators) {
      if (indicator.isCritical()) {
        critical.add(indicator);
      }
    }
    return critical;
  }

  // Getters and setters

  public String getEquipmentId() {
    return equipmentId;
  }

  public String getEquipmentName() {
    return equipmentName;
  }

  public double getBaseFailureRate() {
    return baseFailureRate;
  }

  public void setBaseFailureRate(double rate) {
    this.baseFailureRate = rate;
    recalculateHealth();
  }

  public double getAdjustedFailureRate() {
    return adjustedFailureRate;
  }

  public double getHealthIndex() {
    return healthIndex;
  }

  public List<ConditionIndicator> getIndicators() {
    return new ArrayList<>(indicators);
  }

  public DegradationModel getDegradationModel() {
    return degradationModel;
  }

  public void setDegradationModel(DegradationModel model) {
    this.degradationModel = model;
    recalculateHealth();
  }

  public double getRemainingUsefulLife() {
    return remainingUsefulLife;
  }

  public double getRULConfidence() {
    return rulConfidence;
  }

  public Instant getLastUpdated() {
    return lastUpdated;
  }

  public double getFailureRateMultiplier() {
    return adjustedFailureRate / baseFailureRate;
  }

  /**
   * Converts to map for JSON serialization.
   *
   * @return map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("equipmentId", equipmentId);
    map.put("equipmentName", equipmentName);
    map.put("lastUpdated", lastUpdated.toString());

    // Health summary
    Map<String, Object> health = new HashMap<>();
    health.put("healthIndex", healthIndex);
    health.put("status", healthIndex > 0.8 ? "GOOD"
        : healthIndex > 0.5 ? "FAIR" : healthIndex > 0.2 ? "POOR" : "CRITICAL");
    map.put("health", health);

    // Reliability metrics
    Map<String, Object> reliability = new HashMap<>();
    reliability.put("baseFailureRate", baseFailureRate);
    reliability.put("adjustedFailureRate", adjustedFailureRate);
    reliability.put("failureRateMultiplier", getFailureRateMultiplier());
    reliability.put("mttf", getMTTF());
    reliability.put("pof24h", getProbabilityOfFailure(24));
    reliability.put("pof720h", getProbabilityOfFailure(720)); // 30 days
    map.put("reliability", reliability);

    // RUL
    if (!Double.isNaN(remainingUsefulLife) && !Double.isInfinite(remainingUsefulLife)) {
      Map<String, Object> rul = new HashMap<>();
      rul.put("hours", remainingUsefulLife);
      rul.put("days", remainingUsefulLife / 24);
      rul.put("confidence", rulConfidence);
      map.put("remainingUsefulLife", rul);
    }

    // Indicators
    List<Map<String, Object>> indicatorList = new ArrayList<>();
    for (ConditionIndicator indicator : indicators) {
      indicatorList.add(indicator.toMap());
    }
    map.put("indicators", indicatorList);

    // Alarms
    map.put("alarmingCount", getAlarmingIndicators().size());
    map.put("criticalCount", getCriticalIndicators().size());

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

  /**
   * Generates status report.
   *
   * @return status report
   */
  public String toReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("CONDITION-BASED RELIABILITY REPORT\n");
    sb.append("═".repeat(60)).append("\n\n");

    sb.append("Equipment: ").append(equipmentName).append(" (").append(equipmentId).append(")\n");
    sb.append("Last Updated: ").append(lastUpdated).append("\n\n");

    // Health
    String status = healthIndex > 0.8 ? "GOOD ✓"
        : healthIndex > 0.5 ? "FAIR !" : healthIndex > 0.2 ? "POOR ⚠" : "CRITICAL ✗";
    sb.append(String.format("Health Index: %.1f%% [%s]%n", healthIndex * 100, status));
    sb.append("\n");

    // Reliability
    sb.append("RELIABILITY METRICS\n");
    sb.append("─".repeat(40)).append("\n");
    sb.append(String.format("  Base Failure Rate:     %.2e /hour%n", baseFailureRate));
    sb.append(String.format("  Adjusted Failure Rate: %.2e /hour (%.1fx)%n", adjustedFailureRate,
        getFailureRateMultiplier()));
    sb.append(String.format("  MTTF:                  %.0f hours (%.1f days)%n", getMTTF(),
        getMTTF() / 24));
    sb.append(
        String.format("  P(fail in 24h):        %.2f%%%n", getProbabilityOfFailure(24) * 100));
    sb.append(
        String.format("  P(fail in 30d):        %.2f%%%n", getProbabilityOfFailure(720) * 100));
    sb.append("\n");

    // RUL
    if (!Double.isNaN(remainingUsefulLife) && !Double.isInfinite(remainingUsefulLife)) {
      sb.append(String.format("Remaining Useful Life: %.0f hours (%.0f days) [%.0f%% confidence]%n",
          remainingUsefulLife, remainingUsefulLife / 24, rulConfidence * 100));
      sb.append("\n");
    }

    // Indicators
    sb.append("CONDITION INDICATORS\n");
    sb.append("─".repeat(60)).append("\n");
    sb.append(String.format("%-20s %10s %10s %10s %8s%n", "Indicator", "Current", "Normal",
        "Critical", "Health"));
    sb.append("─".repeat(60)).append("\n");

    for (ConditionIndicator ind : indicators) {
      String flag = ind.isCritical() ? "✗" : ind.isAlarming() ? "!" : "✓";
      sb.append(String.format("%-20s %10.2f %10.2f %10.2f %7.0f%% %s%n", ind.getName(),
          ind.getCurrentValue(), ind.getNormalValue(), ind.getCriticalThreshold(),
          ind.getHealthContribution() * 100, flag));
    }

    return sb.toString();
  }

  @Override
  public String toString() {
    return String.format("ConditionBasedReliability[%s: health=%.0f%%, λ=%.2e]", equipmentName,
        healthIndex * 100, adjustedFailureRate);
  }
}
