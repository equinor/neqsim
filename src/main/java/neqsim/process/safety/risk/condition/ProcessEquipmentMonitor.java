package neqsim.process.safety.risk.condition;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.equipment.capacity.CapacityConstraint;

/**
 * Physics-based equipment monitor that directly integrates with NeqSim process equipment.
 *
 * <p>
 * This class connects reliability monitoring directly to NeqSim's process simulation physics,
 * automatically extracting condition indicators (temperature, pressure, capacity utilization) from
 * the equipment's actual process state.
 * </p>
 *
 * <p>
 * Key features:
 * </p>
 * <ul>
 * <li>Automatic temperature/pressure monitoring from equipment</li>
 * <li>Capacity utilization from CapacityConstrainedEquipment interface</li>
 * <li>Health index calculation based on physics deviations</li>
 * <li>Failure rate adjustment based on operating conditions</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * ProcessEquipmentMonitor monitor = new ProcessEquipmentMonitor(separator);
 * monitor.setDesignTemperatureRange(273.15, 373.15); // Design T range in K
 * monitor.setDesignPressureRange(1.0, 50.0); // Design P range in bara
 * monitor.setBaseFailureRate(0.0001); // per hour
 * 
 * // After process simulation runs:
 * monitor.update(); // Reads T, P, capacity from equipment
 * double failureRate = monitor.getAdjustedFailureRate();
 * double health = monitor.getHealthIndex();
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ProcessEquipmentMonitor implements Serializable {
  private static final long serialVersionUID = 1000L;

  private ProcessEquipmentInterface equipment;
  private String equipmentName;

  // Design limits
  private double minDesignTemperature = Double.NaN;
  private double maxDesignTemperature = Double.NaN;
  private double minDesignPressure = Double.NaN;
  private double maxDesignPressure = Double.NaN;
  private double maxCapacityUtilization = 1.0;

  // Monitoring weights
  private double temperatureWeight = 1.0;
  private double pressureWeight = 1.0;
  private double capacityWeight = 1.5;

  // Reliability parameters
  private double baseFailureRate = 0.0001;
  private double adjustedFailureRate = 0.0001;
  private double healthIndex = 1.0;

  // Current readings
  private double currentTemperature = Double.NaN;
  private double currentPressure = Double.NaN;
  private double currentCapacityUtilization = 0.0;
  private String bottleneckConstraint = "";

  // History
  private List<MonitorReading> history;
  private int maxHistorySize = 1000;
  private Instant lastUpdated;

  /**
   * A recorded reading from the monitor.
   */
  public static class MonitorReading implements Serializable {
    private static final long serialVersionUID = 1L;

    private Instant timestamp;
    private double temperature;
    private double pressure;
    private double capacityUtilization;
    private double healthIndex;
    private double adjustedFailureRate;

    /**
     * Creates a monitor reading.
     *
     * @param timestamp reading time
     * @param temperature temperature in K
     * @param pressure pressure in bara
     * @param capacityUtilization capacity utilization 0-1
     * @param healthIndex calculated health index 0-1
     * @param failureRate adjusted failure rate per hour
     */
    public MonitorReading(Instant timestamp, double temperature, double pressure,
        double capacityUtilization, double healthIndex, double failureRate) {
      this.timestamp = timestamp;
      this.temperature = temperature;
      this.pressure = pressure;
      this.capacityUtilization = capacityUtilization;
      this.healthIndex = healthIndex;
      this.adjustedFailureRate = failureRate;
    }

    public Instant getTimestamp() {
      return timestamp;
    }

    public double getTemperature() {
      return temperature;
    }

    public double getPressure() {
      return pressure;
    }

    public double getCapacityUtilization() {
      return capacityUtilization;
    }

    public double getHealthIndex() {
      return healthIndex;
    }

    public double getAdjustedFailureRate() {
      return adjustedFailureRate;
    }

    /**
     * Converts reading to map for JSON serialization.
     *
     * @return map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("timestamp", timestamp.toString());
      map.put("temperature_K", temperature);
      map.put("pressure_bara", pressure);
      map.put("capacityUtilization", capacityUtilization);
      map.put("healthIndex", healthIndex);
      map.put("adjustedFailureRate", adjustedFailureRate);
      return map;
    }
  }

  /**
   * Creates a monitor for the specified process equipment.
   *
   * @param equipment NeqSim process equipment to monitor
   */
  public ProcessEquipmentMonitor(ProcessEquipmentInterface equipment) {
    this.equipment = equipment;
    this.equipmentName = equipment != null ? equipment.getName() : "Unknown";
    this.history = new ArrayList<>();
    this.lastUpdated = Instant.now();
  }

  /**
   * Creates a monitor with name only (for testing without equipment).
   *
   * @param equipmentName equipment name
   */
  public ProcessEquipmentMonitor(String equipmentName) {
    this.equipment = null;
    this.equipmentName = equipmentName;
    this.history = new ArrayList<>();
    this.lastUpdated = Instant.now();
  }

  /**
   * Updates monitor by reading current values from equipment.
   *
   * <p>
   * This method reads temperature, pressure, and capacity utilization directly from the NeqSim
   * equipment and recalculates health index and failure rate.
   * </p>
   */
  public void update() {
    if (equipment == null) {
      return;
    }

    // Read physics from equipment
    currentTemperature = equipment.getTemperature();
    currentPressure = equipment.getPressure();

    // Read capacity utilization if equipment supports it
    if (equipment instanceof CapacityConstrainedEquipment) {
      CapacityConstrainedEquipment constrained = (CapacityConstrainedEquipment) equipment;
      currentCapacityUtilization = constrained.getMaxUtilization();

      CapacityConstraint bottleneck = constrained.getBottleneckConstraint();
      if (bottleneck != null) {
        bottleneckConstraint = bottleneck.getName();
      }
    }

    // Recalculate health
    recalculateHealth();

    // Record history
    lastUpdated = Instant.now();
    history.add(new MonitorReading(lastUpdated, currentTemperature, currentPressure,
        currentCapacityUtilization, healthIndex, adjustedFailureRate));

    // Limit history size
    while (history.size() > maxHistorySize) {
      history.remove(0);
    }
  }

  /**
   * Manually sets current values (for testing or external data integration).
   *
   * @param temperature temperature in Kelvin
   * @param pressure pressure in bara
   * @param capacityUtilization capacity utilization 0-1
   */
  public void setCurrentValues(double temperature, double pressure, double capacityUtilization) {
    this.currentTemperature = temperature;
    this.currentPressure = pressure;
    this.currentCapacityUtilization = capacityUtilization;
    recalculateHealth();

    lastUpdated = Instant.now();
    history.add(new MonitorReading(lastUpdated, currentTemperature, currentPressure,
        currentCapacityUtilization, healthIndex, adjustedFailureRate));

    while (history.size() > maxHistorySize) {
      history.remove(0);
    }
  }

  /**
   * Recalculates health index and adjusted failure rate based on current readings.
   */
  private void recalculateHealth() {
    double tempHealth = calculateTemperatureHealth();
    double pressHealth = calculatePressureHealth();
    double capHealth = calculateCapacityHealth();

    // Weighted average of health contributions
    double totalWeight = temperatureWeight + pressureWeight + capacityWeight;
    healthIndex =
        (tempHealth * temperatureWeight + pressHealth * pressureWeight + capHealth * capacityWeight)
            / totalWeight;

    // Adjust failure rate based on health
    // Lower health = higher failure rate (exponential relationship)
    double multiplier = Math.exp((1.0 - healthIndex) * 3.0);
    adjustedFailureRate = baseFailureRate * multiplier;
  }

  /**
   * Calculates health contribution from temperature.
   *
   * @return health value 0-1 (1 = optimal)
   */
  private double calculateTemperatureHealth() {
    if (Double.isNaN(currentTemperature)) {
      return 1.0;
    }
    if (Double.isNaN(minDesignTemperature) || Double.isNaN(maxDesignTemperature)) {
      return 1.0;
    }

    double range = maxDesignTemperature - minDesignTemperature;
    if (range <= 0) {
      return 1.0;
    }

    double midpoint = (minDesignTemperature + maxDesignTemperature) / 2;
    double normalizedDeviation = Math.abs(currentTemperature - midpoint) / (range / 2);

    // Health decreases as we approach design limits
    if (currentTemperature < minDesignTemperature || currentTemperature > maxDesignTemperature) {
      // Outside design range - health degrades rapidly
      double exceedance = 0;
      if (currentTemperature < minDesignTemperature) {
        exceedance = (minDesignTemperature - currentTemperature) / range;
      } else {
        exceedance = (currentTemperature - maxDesignTemperature) / range;
      }
      return Math.max(0, 0.3 - exceedance);
    }

    // Inside design range
    return Math.max(0.5, 1.0 - normalizedDeviation * 0.5);
  }

  /**
   * Calculates health contribution from pressure.
   *
   * @return health value 0-1 (1 = optimal)
   */
  private double calculatePressureHealth() {
    if (Double.isNaN(currentPressure)) {
      return 1.0;
    }
    if (Double.isNaN(minDesignPressure) || Double.isNaN(maxDesignPressure)) {
      return 1.0;
    }

    double range = maxDesignPressure - minDesignPressure;
    if (range <= 0) {
      return 1.0;
    }

    // Operating near max design pressure reduces health
    double normalizedPressure = (currentPressure - minDesignPressure) / range;

    if (currentPressure < minDesignPressure || currentPressure > maxDesignPressure) {
      // Outside design range
      double exceedance = 0;
      if (currentPressure > maxDesignPressure) {
        exceedance = (currentPressure - maxDesignPressure) / range;
      } else {
        exceedance = (minDesignPressure - currentPressure) / range;
      }
      return Math.max(0, 0.3 - exceedance * 2);
    }

    // Health degrades as pressure approaches max
    if (normalizedPressure > 0.8) {
      return 1.0 - (normalizedPressure - 0.8) * 2.5;
    }

    return 1.0;
  }

  /**
   * Calculates health contribution from capacity utilization.
   *
   * @return health value 0-1 (1 = optimal)
   */
  private double calculateCapacityHealth() {
    if (Double.isNaN(currentCapacityUtilization)) {
      return 1.0;
    }

    // Operating above max utilization reduces health
    if (currentCapacityUtilization > maxCapacityUtilization) {
      double exceedance = currentCapacityUtilization - maxCapacityUtilization;
      return Math.max(0, 0.5 - exceedance);
    }

    // Health degrades as utilization approaches 100%
    if (currentCapacityUtilization > 0.8) {
      return 1.0 - (currentCapacityUtilization - 0.8) * 1.5;
    }

    return 1.0;
  }

  /**
   * Gets probability of failure within specified time period.
   *
   * @param hours time period in hours
   * @return probability of failure (0-1)
   */
  public double getFailureProbability(double hours) {
    return 1.0 - Math.exp(-adjustedFailureRate * hours);
  }

  /**
   * Gets estimated remaining useful life based on health trend.
   *
   * @return remaining useful life in hours, or Double.POSITIVE_INFINITY if not degrading
   */
  public double getRemainingUsefulLife() {
    if (history.size() < 10) {
      return Double.NaN;
    }

    // Simple linear extrapolation of health trend
    int n = Math.min(50, history.size());
    List<MonitorReading> recent = history.subList(history.size() - n, history.size());

    double sumX = 0;
    double sumY = 0;
    double sumXY = 0;
    double sumX2 = 0;

    for (int i = 0; i < n; i++) {
      sumX += i;
      sumY += recent.get(i).getHealthIndex();
      sumXY += i * recent.get(i).getHealthIndex();
      sumX2 += i * i;
    }

    double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);

    if (slope >= 0) {
      return Double.POSITIVE_INFINITY;
    }

    double currentHealth = recent.get(n - 1).getHealthIndex();
    double criticalHealth = 0.3;
    double hoursPerRecord = 1.0;

    return Math.max(0, (currentHealth - criticalHealth) / (-slope) * hoursPerRecord);
  }

  // ================= Getters and Setters =================

  /**
   * Gets the monitored equipment.
   *
   * @return process equipment
   */
  public ProcessEquipmentInterface getEquipment() {
    return equipment;
  }

  /**
   * Sets the monitored equipment.
   *
   * @param equipment process equipment to monitor
   */
  public void setEquipment(ProcessEquipmentInterface equipment) {
    this.equipment = equipment;
    this.equipmentName = equipment != null ? equipment.getName() : this.equipmentName;
  }

  /**
   * Gets equipment name.
   *
   * @return equipment name
   */
  public String getEquipmentName() {
    return equipmentName;
  }

  /**
   * Sets design temperature range.
   *
   * @param min minimum design temperature in Kelvin
   * @param max maximum design temperature in Kelvin
   */
  public void setDesignTemperatureRange(double min, double max) {
    this.minDesignTemperature = min;
    this.maxDesignTemperature = max;
  }

  /**
   * Sets design pressure range.
   *
   * @param min minimum design pressure in bara
   * @param max maximum design pressure in bara
   */
  public void setDesignPressureRange(double min, double max) {
    this.minDesignPressure = min;
    this.maxDesignPressure = max;
  }

  /**
   * Sets maximum capacity utilization threshold.
   *
   * @param maxUtilization maximum utilization (1.0 = 100%)
   */
  public void setMaxCapacityUtilization(double maxUtilization) {
    this.maxCapacityUtilization = maxUtilization;
  }

  /**
   * Sets base failure rate.
   *
   * @param rate base failure rate in failures per hour
   */
  public void setBaseFailureRate(double rate) {
    this.baseFailureRate = rate;
    this.adjustedFailureRate = rate;
  }

  /**
   * Gets base failure rate.
   *
   * @return base failure rate in failures per hour
   */
  public double getBaseFailureRate() {
    return baseFailureRate;
  }

  /**
   * Gets adjusted failure rate (based on current conditions).
   *
   * @return adjusted failure rate in failures per hour
   */
  public double getAdjustedFailureRate() {
    return adjustedFailureRate;
  }

  /**
   * Gets current health index.
   *
   * @return health index 0-1 (1 = optimal)
   */
  public double getHealthIndex() {
    return healthIndex;
  }

  /**
   * Gets current temperature reading.
   *
   * @return temperature in Kelvin
   */
  public double getCurrentTemperature() {
    return currentTemperature;
  }

  /**
   * Gets current pressure reading.
   *
   * @return pressure in bara
   */
  public double getCurrentPressure() {
    return currentPressure;
  }

  /**
   * Gets current capacity utilization.
   *
   * @return capacity utilization 0-1
   */
  public double getCurrentCapacityUtilization() {
    return currentCapacityUtilization;
  }

  /**
   * Gets the bottleneck constraint name.
   *
   * @return bottleneck constraint name or empty string
   */
  public String getBottleneckConstraint() {
    return bottleneckConstraint;
  }

  /**
   * Gets last update time.
   *
   * @return last update instant
   */
  public Instant getLastUpdated() {
    return lastUpdated;
  }

  /**
   * Gets monitoring history.
   *
   * @return list of monitor readings
   */
  public List<MonitorReading> getHistory() {
    return new ArrayList<>(history);
  }

  /**
   * Sets indicator weights for health calculation.
   *
   * @param tempWeight temperature weight
   * @param pressWeight pressure weight
   * @param capWeight capacity utilization weight
   */
  public void setWeights(double tempWeight, double pressWeight, double capWeight) {
    this.temperatureWeight = tempWeight;
    this.pressureWeight = pressWeight;
    this.capacityWeight = capWeight;
  }

  /**
   * Converts current state to map for JSON serialization.
   *
   * @return map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("equipmentName", equipmentName);
    map.put("currentTemperature_K", currentTemperature);
    map.put("currentPressure_bara", currentPressure);
    map.put("currentCapacityUtilization", currentCapacityUtilization);
    map.put("bottleneckConstraint", bottleneckConstraint);
    map.put("healthIndex", healthIndex);
    map.put("baseFailureRate", baseFailureRate);
    map.put("adjustedFailureRate", adjustedFailureRate);
    map.put("failureProbability_24h", getFailureProbability(24));
    map.put("remainingUsefulLife_hours", getRemainingUsefulLife());
    map.put("lastUpdated", lastUpdated.toString());

    Map<String, Double> designLimits = new HashMap<>();
    designLimits.put("minTemperature_K", minDesignTemperature);
    designLimits.put("maxTemperature_K", maxDesignTemperature);
    designLimits.put("minPressure_bara", minDesignPressure);
    designLimits.put("maxPressure_bara", maxDesignPressure);
    designLimits.put("maxCapacityUtilization", maxCapacityUtilization);
    map.put("designLimits", designLimits);

    return map;
  }
}
