package neqsim.process.maintenance;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.GsonBuilder;

/**
 * Assesses the current health state of a process equipment item by combining degradation factors,
 * operating hours, and condition indicators into a unified health index.
 *
 * <p>
 * The health index ranges from 0.0 (failed) to 1.0 (as-new). It is computed from:
 * </p>
 * <ul>
 * <li>Performance degradation factor (efficiency/head loss)</li>
 * <li>Fouling factor</li>
 * <li>Operating hours since last overhaul</li>
 * <li>Condition indicator readings (vibration, temperature, etc.)</li>
 * </ul>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class EquipmentHealthAssessment implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /**
   * Severity classification for equipment health state.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public enum HealthSeverity {
    /** Equipment operating within normal parameters. */
    NORMAL,
    /** Minor degradation detected, monitoring recommended. */
    WATCH,
    /** Significant degradation, plan maintenance. */
    ALERT,
    /** Critical condition, immediate action recommended. */
    CRITICAL
  }

  /**
   * A single condition indicator contributing to health assessment.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static class ConditionIndicator implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1001L;

    private final String name;
    private final String unit;
    private final double currentValue;
    private final double normalValue;
    private final double alarmValue;
    private final double tripValue;

    /**
     * Constructs a condition indicator.
     *
     * @param name indicator name (e.g., "vibration", "bearing_temperature")
     * @param unit measurement unit
     * @param currentValue current measured value
     * @param normalValue baseline normal value
     * @param alarmValue alarm threshold
     * @param tripValue trip threshold
     */
    public ConditionIndicator(String name, String unit, double currentValue, double normalValue,
        double alarmValue, double tripValue) {
      this.name = name;
      this.unit = unit;
      this.currentValue = currentValue;
      this.normalValue = normalValue;
      this.alarmValue = alarmValue;
      this.tripValue = tripValue;
    }

    /**
     * Gets the indicator name.
     *
     * @return indicator name
     */
    public String getName() {
      return name;
    }

    /**
     * Gets the measurement unit.
     *
     * @return unit string
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Gets the current measured value.
     *
     * @return current value
     */
    public double getCurrentValue() {
      return currentValue;
    }

    /**
     * Gets the normal baseline value.
     *
     * @return normal value
     */
    public double getNormalValue() {
      return normalValue;
    }

    /**
     * Gets the alarm threshold.
     *
     * @return alarm value
     */
    public double getAlarmValue() {
      return alarmValue;
    }

    /**
     * Gets the trip threshold.
     *
     * @return trip value
     */
    public double getTripValue() {
      return tripValue;
    }

    /**
     * Calculates the health contribution of this indicator (1.0 = at normal, 0.0 = at trip).
     *
     * @return health fraction between 0.0 and 1.0
     */
    public double getHealthFraction() {
      if (tripValue <= normalValue) {
        return 1.0; // Invalid config, assume healthy
      }
      double fraction = 1.0 - (currentValue - normalValue) / (tripValue - normalValue);
      return Math.max(0.0, Math.min(1.0, fraction));
    }
  }

  // Equipment identification
  private String equipmentName = "";
  private String equipmentType = "";

  // Performance degradation inputs
  private double degradationFactor = 1.0;
  private double foulingFactor = 0.0;
  private double operatingHours = 0.0;
  private double hoursSinceOverhaul = 0.0;
  private double meanTimeBetweenOverhaul = 30000.0; // hours

  // Condition indicators
  private final List<ConditionIndicator> indicators = new ArrayList<ConditionIndicator>();

  // Computed results
  private double healthIndex = 1.0;
  private HealthSeverity severity = HealthSeverity.NORMAL;
  private double estimatedRemainingLife = Double.MAX_VALUE;

  /**
   * Constructs an equipment health assessment.
   *
   * @param equipmentName name of the equipment being assessed
   * @param equipmentType type of equipment (e.g., "compressor", "heat_exchanger")
   */
  public EquipmentHealthAssessment(String equipmentName, String equipmentType) {
    this.equipmentName = equipmentName;
    this.equipmentType = equipmentType;
  }

  /**
   * Sets the performance degradation factor.
   *
   * @param degradationFactor factor from 0 (failed) to 1 (as-new)
   */
  public void setDegradationFactor(double degradationFactor) {
    this.degradationFactor = degradationFactor;
  }

  /**
   * Sets the fouling factor.
   *
   * @param foulingFactor fouling from 0 (clean) to 1 (fully fouled)
   */
  public void setFoulingFactor(double foulingFactor) {
    this.foulingFactor = foulingFactor;
  }

  /**
   * Sets the total operating hours.
   *
   * @param operatingHours total hours in service
   */
  public void setOperatingHours(double operatingHours) {
    this.operatingHours = operatingHours;
  }

  /**
   * Sets the hours since last overhaul.
   *
   * @param hours hours since overhaul
   */
  public void setHoursSinceOverhaul(double hours) {
    this.hoursSinceOverhaul = hours;
  }

  /**
   * Sets the expected mean time between overhauls.
   *
   * @param mtbo mean time between overhauls in hours
   */
  public void setMeanTimeBetweenOverhaul(double mtbo) {
    this.meanTimeBetweenOverhaul = mtbo;
  }

  /**
   * Adds a condition indicator to the assessment.
   *
   * @param indicator condition indicator to add
   */
  public void addConditionIndicator(ConditionIndicator indicator) {
    indicators.add(indicator);
  }

  /**
   * Calculates the overall health index and severity classification. Call this after setting all
   * inputs.
   */
  public void calculate() {
    // Component 1: Performance degradation (weight 0.3)
    double perfHealth = degradationFactor * (1.0 - foulingFactor);

    // Component 2: Age/overhaul factor (weight 0.2)
    double ageHealth = 1.0;
    if (meanTimeBetweenOverhaul > 0) {
      double ageRatio = hoursSinceOverhaul / meanTimeBetweenOverhaul;
      ageHealth = Math.max(0.0, 1.0 - ageRatio);
    }

    // Component 3: Condition indicators (weight 0.5)
    double conditionHealth = 1.0;
    if (!indicators.isEmpty()) {
      double sum = 0.0;
      for (ConditionIndicator ci : indicators) {
        sum += ci.getHealthFraction();
      }
      conditionHealth = sum / indicators.size();
    }

    // Weighted combination
    healthIndex = 0.3 * perfHealth + 0.2 * ageHealth + 0.5 * conditionHealth;
    healthIndex = Math.max(0.0, Math.min(1.0, healthIndex));

    // Classify severity
    if (healthIndex >= 0.75) {
      severity = HealthSeverity.NORMAL;
    } else if (healthIndex >= 0.50) {
      severity = HealthSeverity.WATCH;
    } else if (healthIndex >= 0.25) {
      severity = HealthSeverity.ALERT;
    } else {
      severity = HealthSeverity.CRITICAL;
    }

    // Estimate remaining useful life (simple linear extrapolation)
    if (hoursSinceOverhaul > 0 && healthIndex < 1.0) {
      double degradationRate = (1.0 - healthIndex) / hoursSinceOverhaul;
      if (degradationRate > 0) {
        estimatedRemainingLife = healthIndex / degradationRate;
      }
    }
  }

  /**
   * Gets the computed health index.
   *
   * @return health index from 0.0 (failed) to 1.0 (as-new)
   */
  public double getHealthIndex() {
    return healthIndex;
  }

  /**
   * Gets the health severity classification.
   *
   * @return severity level
   */
  public HealthSeverity getSeverity() {
    return severity;
  }

  /**
   * Gets the estimated remaining useful life in hours.
   *
   * @return estimated hours until failure
   */
  public double getEstimatedRemainingLife() {
    return estimatedRemainingLife;
  }

  /**
   * Gets the equipment name.
   *
   * @return equipment name
   */
  public String getEquipmentName() {
    return equipmentName;
  }

  /**
   * Gets the equipment type.
   *
   * @return equipment type
   */
  public String getEquipmentType() {
    return equipmentType;
  }

  /**
   * Gets the condition indicators.
   *
   * @return unmodifiable list of condition indicators
   */
  public List<ConditionIndicator> getConditionIndicators() {
    return Collections.unmodifiableList(indicators);
  }

  /**
   * Generates a JSON representation of the health assessment.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
