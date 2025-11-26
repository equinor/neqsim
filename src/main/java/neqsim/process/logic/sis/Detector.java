package neqsim.process.logic.sis;

/**
 * Represents a fire or gas detector in a Safety Instrumented System.
 * 
 * <p>
 * Detectors monitor process conditions and can trigger safety actions when thresholds are exceeded.
 * Each detector has:
 * <ul>
 * <li>Alarm level (L, H, HH) indicating severity</li>
 * <li>Trip status (normal or tripped)</li>
 * <li>Measured value and setpoint</li>
 * <li>Bypass capability for maintenance</li>
 * <li>Fault detection</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public class Detector {
  private final String name;
  private final DetectorType type;
  private final AlarmLevel alarmLevel;
  private double setpoint;
  private String unit;

  private boolean isTripped = false;
  private boolean isBypassed = false;
  private boolean isFaulty = false;
  private double measuredValue = 0.0;
  private long tripTime = 0;

  /**
   * Creates a detector.
   *
   * @param name detector tag/name
   * @param type detector type (FIRE, GAS, PRESSURE, etc.)
   * @param alarmLevel alarm level (L, H, HH)
   * @param setpoint trip setpoint
   * @param unit engineering unit
   */
  public Detector(String name, DetectorType type, AlarmLevel alarmLevel, double setpoint,
      String unit) {
    this.name = name;
    this.type = type;
    this.alarmLevel = alarmLevel;
    this.setpoint = setpoint;
    this.unit = unit;
  }

  /**
   * Updates the detector with a new measured value and evaluates trip condition.
   *
   * @param measuredValue current measured value
   */
  public void update(double measuredValue) {
    this.measuredValue = measuredValue;

    if (isBypassed || isFaulty) {
      return; // Don't evaluate if bypassed or faulty
    }

    // Check trip condition based on alarm level
    boolean shouldTrip = false;
    switch (alarmLevel) {
      case LOW:
      case LOW_LOW:
        shouldTrip = measuredValue < setpoint;
        break;
      case HIGH:
      case HIGH_HIGH:
        shouldTrip = measuredValue > setpoint;
        break;
    }

    if (shouldTrip && !isTripped) {
      isTripped = true;
      tripTime = System.currentTimeMillis();
    } else if (!shouldTrip && isTripped) {
      // Auto-reset if condition clears (depends on detector configuration)
      // In real SIS, manual reset is often required
      // isTripped = false;
    }
  }

  /**
   * Manually trips the detector (for testing).
   */
  public void trip() {
    if (!isBypassed && !isFaulty) {
      isTripped = true;
      tripTime = System.currentTimeMillis();
    }
  }

  /**
   * Resets the detector after acknowledgment.
   * 
   * <p>
   * In safety systems, reset typically requires operator action and may have permissive conditions.
   * </p>
   */
  public void reset() {
    if (!isBypassed && measuredValue < setpoint) { // Only reset if condition cleared
      isTripped = false;
      tripTime = 0;
    }
  }

  /**
   * Bypasses the detector for maintenance.
   *
   * @param bypass true to bypass, false to restore
   */
  public void setBypass(boolean bypass) {
    this.isBypassed = bypass;
    if (bypass) {
      isTripped = false; // Clear trip when bypassed
    }
  }

  /**
   * Sets the fault status of the detector.
   *
   * @param faulty true if detector has a fault
   */
  public void setFaulty(boolean faulty) {
    this.isFaulty = faulty;
  }

  /**
   * Gets the detector name.
   *
   * @return detector name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the detector type.
   *
   * @return detector type
   */
  public DetectorType getType() {
    return type;
  }

  /**
   * Gets the alarm level.
   *
   * @return alarm level
   */
  public AlarmLevel getAlarmLevel() {
    return alarmLevel;
  }

  /**
   * Checks if detector is tripped.
   *
   * @return true if tripped
   */
  public boolean isTripped() {
    return isTripped && !isBypassed && !isFaulty;
  }

  /**
   * Checks if detector is bypassed.
   *
   * @return true if bypassed
   */
  public boolean isBypassed() {
    return isBypassed;
  }

  /**
   * Checks if detector is faulty.
   *
   * @return true if faulty
   */
  public boolean isFaulty() {
    return isFaulty;
  }

  /**
   * Gets the measured value.
   *
   * @return measured value
   */
  public double getMeasuredValue() {
    return measuredValue;
  }

  /**
   * Gets the setpoint.
   *
   * @return setpoint value
   */
  public double getSetpoint() {
    return setpoint;
  }

  /**
   * Sets a new setpoint.
   *
   * @param setpoint new setpoint value
   */
  public void setSetpoint(double setpoint) {
    this.setpoint = setpoint;
  }

  /**
   * Gets the time when detector tripped (milliseconds since epoch).
   *
   * @return trip time, or 0 if not tripped
   */
  public long getTripTime() {
    return tripTime;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(name).append(" [").append(type).append(" ").append(alarmLevel).append("] - ");
    sb.append("Setpoint: ").append(setpoint).append(" ").append(unit);
    sb.append(", Value: ").append(String.format("%.2f", measuredValue)).append(" ").append(unit);
    sb.append(", Status: ");
    if (isBypassed) {
      sb.append("BYPASSED");
    } else if (isFaulty) {
      sb.append("FAULTY");
    } else if (isTripped) {
      sb.append("TRIPPED");
    } else {
      sb.append("NORMAL");
    }
    return sb.toString();
  }

  /**
   * Detector type enumeration.
   */
  public enum DetectorType {
    FIRE("Fire Detector"), GAS("Gas Detector"), PRESSURE("Pressure Transmitter"), TEMPERATURE(
        "Temperature Transmitter"), LEVEL("Level Transmitter"), FLOW("Flow Transmitter");

    private final String description;

    DetectorType(String description) {
      this.description = description;
    }

    public String getDescription() {
      return description;
    }
  }

  /**
   * Alarm level enumeration.
   */
  public enum AlarmLevel {
    LOW_LOW("LL"), LOW("L"), HIGH("H"), HIGH_HIGH("HH");

    private final String notation;

    AlarmLevel(String notation) {
      this.notation = notation;
    }

    public String getNotation() {
      return notation;
    }
  }
}
