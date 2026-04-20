package neqsim.process.envelope;

import java.io.Serializable;

/**
 * Represents a single operating margin measurement for process equipment.
 *
 * <p>
 * An operating margin captures the relationship between the current value of a process variable and
 * its operating limit. It tracks how close the process is to a constraint boundary, expressed as
 * both an absolute distance and a percentage.
 * </p>
 *
 * <p>
 * Margins can be of two directions:
 * </p>
 * <ul>
 * <li>{@link Direction#HIGH} — the limit is an upper bound (e.g., max pressure, max temperature,
 * max speed)</li>
 * <li>{@link Direction#LOW} — the limit is a lower bound (e.g., min level, min flow, surge
 * line)</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * OperatingMargin margin = new OperatingMargin("HP Separator", "pressure",
 *     OperatingMargin.MarginType.PRESSURE, OperatingMargin.Direction.HIGH, 72.0, 85.0, "bara");
 * double pct = margin.getMarginPercent(); // 15.3%
 * OperatingMargin.Status status = margin.getStatus(); // NORMAL
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class OperatingMargin implements Serializable, Comparable<OperatingMargin> {
  private static final long serialVersionUID = 1L;

  /**
   * Type of operating margin being tracked.
   */
  public enum MarginType {
    /** Pressure margin (e.g., design pressure, relief set pressure). */
    PRESSURE,
    /** Temperature margin (e.g., max discharge temperature, MDMT). */
    TEMPERATURE,
    /** Level margin (e.g., separator liquid level HH/LL). */
    LEVEL,
    /** Flow margin (e.g., minimum stable flow, maximum pipeline velocity). */
    FLOW,
    /** Compressor surge margin (distance to surge line). */
    SURGE,
    /** Compressor stonewall margin (distance to choke). */
    STONEWALL,
    /** Speed margin (e.g., compressor max/min speed). */
    SPEED,
    /** Power margin (e.g., driver power limit). */
    POWER,
    /** Composition margin (e.g., max CO2, max H2S, min methane). */
    COMPOSITION,
    /** Hydrate subcooling margin (temperature above hydrate formation). */
    HYDRATE,
    /** Hydrocarbon dew point margin. */
    HC_DEW_POINT,
    /** Water dew point margin. */
    WATER_DEW_POINT,
    /** Valve opening margin (max Cv capacity). */
    VALVE_OPENING,
    /** Heat exchanger duty margin. */
    DUTY,
    /** Pipeline erosion velocity margin. */
    EROSION_VELOCITY,
    /** Generic custom margin type. */
    CUSTOM
  }

  /**
   * Direction of the constraint.
   */
  public enum Direction {
    /** Upper bound limit (current value must stay below). */
    HIGH,
    /** Lower bound limit (current value must stay above). */
    LOW
  }

  /**
   * Status classification based on margin percentage.
   */
  public enum Status {
    /** Margin greater than 20% — safe operating region. */
    NORMAL,
    /** Margin between 10% and 20% — monitor closely. */
    ADVISORY,
    /** Margin between 5% and 10% — take preventive action. */
    WARNING,
    /** Margin between 0% and 5% — immediate action required. */
    CRITICAL,
    /** Margin is zero or negative — limit violated. */
    VIOLATED
  }

  /** Threshold for ADVISORY status (fraction, not percent). */
  private static final double ADVISORY_THRESHOLD = 0.20;
  /** Threshold for WARNING status (fraction). */
  private static final double WARNING_THRESHOLD = 0.10;
  /** Threshold for CRITICAL status (fraction). */
  private static final double CRITICAL_THRESHOLD = 0.05;

  private final String equipmentName;
  private final String variableName;
  private final MarginType marginType;
  private final Direction direction;
  private double currentValue;
  private double limitValue;
  private final String unit;
  private double nominalValue;
  private boolean nominalValueSet;

  /**
   * Creates an operating margin measurement.
   *
   * @param equipmentName name of the equipment (e.g., "HP Separator", "Export Compressor")
   * @param variableName name of the variable being tracked (e.g., "pressure", "level")
   * @param marginType type classification for this margin
   * @param direction whether the limit is an upper or lower bound
   * @param currentValue current measured or simulated value
   * @param limitValue the operating limit value
   * @param unit engineering unit string (e.g., "bara", "C", "m3/hr")
   */
  public OperatingMargin(String equipmentName, String variableName, MarginType marginType,
      Direction direction, double currentValue, double limitValue, String unit) {
    this.equipmentName = equipmentName;
    this.variableName = variableName;
    this.marginType = marginType;
    this.direction = direction;
    this.currentValue = currentValue;
    this.limitValue = limitValue;
    this.unit = unit;
    this.nominalValue = Double.NaN;
    this.nominalValueSet = false;
  }

  /**
   * Returns the absolute distance between the current value and the limit.
   *
   * <p>
   * For HIGH direction, this is (limit - current). For LOW direction, this is (current - limit).
   * Positive values mean the limit is not violated.
   * </p>
   *
   * @return absolute margin distance in engineering units
   */
  public double getAbsoluteMargin() {
    if (direction == Direction.HIGH) {
      return limitValue - currentValue;
    } else {
      return currentValue - limitValue;
    }
  }

  /**
   * Returns the margin as a fraction of the limit value (0.0 to 1.0+).
   *
   * <p>
   * A value of 0.15 means the current operating point is 15% away from the limit. Negative values
   * indicate the limit has been violated.
   * </p>
   *
   * @return margin as a fraction (not percentage)
   */
  public double getMarginFraction() {
    if (Math.abs(limitValue) < 1e-15) {
      return (Math.abs(getAbsoluteMargin()) < 1e-15) ? 1.0 : 0.0;
    }
    return getAbsoluteMargin() / Math.abs(limitValue);
  }

  /**
   * Returns the margin as a percentage (0.0 to 100.0+).
   *
   * @return margin percentage
   */
  public double getMarginPercent() {
    return getMarginFraction() * 100.0;
  }

  /**
   * Classifies the current margin into a status category.
   *
   * <p>
   * Thresholds (configurable via static methods):
   * </p>
   * <ul>
   * <li>NORMAL: margin &gt; 20%</li>
   * <li>ADVISORY: 10% &lt; margin &lt;= 20%</li>
   * <li>WARNING: 5% &lt; margin &lt;= 10%</li>
   * <li>CRITICAL: 0% &lt; margin &lt;= 5%</li>
   * <li>VIOLATED: margin &lt;= 0%</li>
   * </ul>
   *
   * @return the status classification
   */
  public Status getStatus() {
    double fraction = getMarginFraction();
    if (fraction <= 0.0) {
      return Status.VIOLATED;
    } else if (fraction <= CRITICAL_THRESHOLD) {
      return Status.CRITICAL;
    } else if (fraction <= WARNING_THRESHOLD) {
      return Status.WARNING;
    } else if (fraction <= ADVISORY_THRESHOLD) {
      return Status.ADVISORY;
    } else {
      return Status.NORMAL;
    }
  }

  /**
   * Returns a severity score from 0 (safe) to 100 (violated) for sorting.
   *
   * @return integer severity score
   */
  public int getSeverityScore() {
    double fraction = getMarginFraction();
    if (fraction <= 0.0) {
      return 100;
    } else if (fraction >= 1.0) {
      return 0;
    }
    return (int) Math.round((1.0 - fraction) * 100.0);
  }

  /**
   * Updates the current value of this margin.
   *
   * @param newValue the new measured or simulated value
   */
  public void updateCurrentValue(double newValue) {
    this.currentValue = newValue;
  }

  /**
   * Updates the limit value of this margin.
   *
   * @param newLimit the new operating limit
   */
  public void updateLimitValue(double newLimit) {
    this.limitValue = newLimit;
  }

  /**
   * Sets the nominal (design/normal) value for reference.
   *
   * @param nominal the nominal operating value
   */
  public void setNominalValue(double nominal) {
    this.nominalValue = nominal;
    this.nominalValueSet = true;
  }

  /**
   * Returns the nominal operating value, if set.
   *
   * @return nominal value, or NaN if not set
   */
  public double getNominalValue() {
    return nominalValue;
  }

  /**
   * Returns whether a nominal value has been set.
   *
   * @return true if nominal value is configured
   */
  public boolean hasNominalValue() {
    return nominalValueSet;
  }

  /**
   * Returns the equipment name.
   *
   * @return equipment name string
   */
  public String getEquipmentName() {
    return equipmentName;
  }

  /**
   * Returns the variable name.
   *
   * @return variable name string
   */
  public String getVariableName() {
    return variableName;
  }

  /**
   * Returns the margin type classification.
   *
   * @return margin type enum value
   */
  public MarginType getMarginType() {
    return marginType;
  }

  /**
   * Returns the constraint direction.
   *
   * @return direction enum value
   */
  public Direction getDirection() {
    return direction;
  }

  /**
   * Returns the current measured or simulated value.
   *
   * @return current value in engineering units
   */
  public double getCurrentValue() {
    return currentValue;
  }

  /**
   * Returns the operating limit value.
   *
   * @return limit value in engineering units
   */
  public double getLimitValue() {
    return limitValue;
  }

  /**
   * Returns the engineering unit string.
   *
   * @return unit string (e.g., "bara", "C", "kg/hr")
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Returns a unique key identifying this margin (equipment + variable + direction).
   *
   * @return unique margin key string
   */
  public String getKey() {
    return equipmentName + "." + variableName + "." + direction.name();
  }

  /**
   * Compares margins by severity (most critical first).
   *
   * @param other the other margin to compare against
   * @return negative if this margin is more critical, positive if less critical
   */
  @Override
  public int compareTo(OperatingMargin other) {
    return Integer.compare(other.getSeverityScore(), this.getSeverityScore());
  }

  /**
   * Returns a human-readable summary string.
   *
   * @return formatted margin description
   */
  @Override
  public String toString() {
    return String.format("[%s] %s.%s: %.2f / %.2f %s (margin: %.1f%%, status: %s)", getStatus(),
        equipmentName, variableName, currentValue, limitValue, unit, getMarginPercent(),
        getStatus());
  }
}
