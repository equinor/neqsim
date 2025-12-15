package neqsim.process.mpc;

import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Represents a controlled variable (CV) in an MPC formulation.
 *
 * <p>
 * A controlled variable is a process output that the MPC aims to keep at a setpoint or within
 * specified constraints. Common examples include pressures, temperatures, compositions, and quality
 * specifications.
 * </p>
 *
 * <p>
 * Features:
 * </p>
 * <ul>
 * <li>Setpoint tracking with configurable weight</li>
 * <li>Soft constraints (penalized violations)</li>
 * <li>Hard constraints (never violated)</li>
 * <li>Zone control (keep within range rather than at setpoint)</li>
 * <li>Priority weighting for multi-CV optimization</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * {@code
 * // Pressure CV with setpoint control
 * ControlledVariable pressureCV =
 *     new ControlledVariable("Pressure", separator, "pressure", "bara").setSetpoint(50.0) // Target
 *                                                                                         // pressure
 *         .setWeight(1.0) // High priority
 *         .setSoftConstraints(45.0, 55.0) // Comfortable range
 *         .setHardConstraints(35.0, 65.0); // Absolute limits
 *
 * // Temperature CV with zone control
 * ControlledVariable tempCV =
 *     new ControlledVariable("Temperature", outlet, "temperature", "C").setZone(20.0, 30.0) // Keep
 *                                                                                           // in
 *                                                                                           // this
 *                                                                                           // zone
 *         .setWeight(0.5); // Lower priority
 * }
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @since 3.0
 */
public class ControlledVariable extends MPCVariable {
  private static final long serialVersionUID = 1000L;

  /** Target setpoint for this CV. */
  private double setpoint = Double.NaN;

  /** Weight on setpoint tracking error in objective function. */
  private double weight = 1.0;

  /** Lower soft constraint (penalized if violated). */
  private double softMin = Double.NEGATIVE_INFINITY;

  /** Upper soft constraint (penalized if violated). */
  private double softMax = Double.POSITIVE_INFINITY;

  /** Lower hard constraint (never violated). */
  private double hardMin = Double.NEGATIVE_INFINITY;

  /** Upper hard constraint (never violated). */
  private double hardMax = Double.POSITIVE_INFINITY;

  /** Penalty weight for soft constraint violations. */
  private double softConstraintPenalty = 1000.0;

  /** Whether to use zone control instead of setpoint tracking. */
  private boolean zoneControl = false;

  /** Lower bound of zone for zone control mode. */
  private double zoneLower = Double.NEGATIVE_INFINITY;

  /** Upper bound of zone for zone control mode. */
  private double zoneUpper = Double.POSITIVE_INFINITY;

  /** Predicted value from MPC calculation. */
  private double predictedValue = Double.NaN;

  /**
   * Construct a controlled variable with a name.
   *
   * @param name unique identifier for this CV
   */
  public ControlledVariable(String name) {
    super(name);
  }

  /**
   * Construct a controlled variable bound to equipment.
   *
   * @param name unique identifier for this CV
   * @param equipment the process equipment to monitor
   * @param propertyName the property to control
   */
  public ControlledVariable(String name, ProcessEquipmentInterface equipment, String propertyName) {
    super(name, equipment, propertyName);
  }

  /**
   * Construct a controlled variable bound to equipment with unit.
   *
   * @param name unique identifier for this CV
   * @param equipment the process equipment to monitor
   * @param propertyName the property to control
   * @param unit the unit for the property value
   */
  public ControlledVariable(String name, ProcessEquipmentInterface equipment, String propertyName,
      String unit) {
    super(name, equipment, propertyName, unit);
  }

  @Override
  public MPCVariableType getType() {
    return MPCVariableType.CONTROLLED;
  }

  /**
   * Get the setpoint for this CV.
   *
   * @return the target setpoint
   */
  public double getSetpoint() {
    return setpoint;
  }

  /**
   * Set the setpoint for this CV.
   *
   * @param setpoint the target value
   * @return this variable for method chaining
   */
  public ControlledVariable setSetpoint(double setpoint) {
    if (!Double.isFinite(setpoint)) {
      throw new IllegalArgumentException("Setpoint must be finite");
    }
    this.setpoint = setpoint;
    this.zoneControl = false;
    return this;
  }

  /**
   * Get the tracking weight.
   *
   * @return the weight on setpoint error
   */
  public double getWeight() {
    return weight;
  }

  /**
   * Set the tracking weight for this CV.
   *
   * <p>
   * Higher weight means this CV is prioritized over others when the controller must make
   * trade-offs.
   * </p>
   *
   * @param weight the weight (non-negative)
   * @return this variable for method chaining
   */
  public ControlledVariable setWeight(double weight) {
    if (weight < 0) {
      throw new IllegalArgumentException("Weight must be non-negative");
    }
    this.weight = weight;
    return this;
  }

  /**
   * Get the lower soft constraint.
   *
   * @return the lower soft limit
   */
  public double getSoftMin() {
    return softMin;
  }

  /**
   * Get the upper soft constraint.
   *
   * @return the upper soft limit
   */
  public double getSoftMax() {
    return softMax;
  }

  /**
   * Set soft constraints for this CV.
   *
   * <p>
   * Soft constraints define the preferred operating range. The controller penalizes violations but
   * may exceed these limits if necessary to satisfy higher-priority objectives.
   * </p>
   *
   * @param min lower soft limit
   * @param max upper soft limit
   * @return this variable for method chaining
   */
  public ControlledVariable setSoftConstraints(double min, double max) {
    if (min > max) {
      throw new IllegalArgumentException("Soft min must not exceed soft max");
    }
    this.softMin = min;
    this.softMax = max;
    return this;
  }

  /**
   * Get the lower hard constraint.
   *
   * @return the lower hard limit
   */
  public double getHardMin() {
    return hardMin;
  }

  /**
   * Get the upper hard constraint.
   *
   * @return the upper hard limit
   */
  public double getHardMax() {
    return hardMax;
  }

  /**
   * Set hard constraints for this CV.
   *
   * <p>
   * Hard constraints are never violated. They represent physical or safety limits that the
   * controller must respect.
   * </p>
   *
   * @param min lower hard limit
   * @param max upper hard limit
   * @return this variable for method chaining
   */
  public ControlledVariable setHardConstraints(double min, double max) {
    if (min > max) {
      throw new IllegalArgumentException("Hard min must not exceed hard max");
    }
    this.hardMin = min;
    this.hardMax = max;
    this.minValue = min;
    this.maxValue = max;
    return this;
  }

  /**
   * Get the soft constraint penalty.
   *
   * @return the penalty weight for soft violations
   */
  public double getSoftConstraintPenalty() {
    return softConstraintPenalty;
  }

  /**
   * Set the penalty for soft constraint violations.
   *
   * @param penalty the penalty weight (positive)
   * @return this variable for method chaining
   */
  public ControlledVariable setSoftConstraintPenalty(double penalty) {
    if (penalty < 0) {
      throw new IllegalArgumentException("Penalty must be non-negative");
    }
    this.softConstraintPenalty = penalty;
    return this;
  }

  /**
   * Check if this CV uses zone control.
   *
   * @return true if zone control is enabled
   */
  public boolean isZoneControl() {
    return zoneControl;
  }

  /**
   * Get the lower zone bound.
   *
   * @return the lower zone limit
   */
  public double getZoneLower() {
    return zoneLower;
  }

  /**
   * Get the upper zone bound.
   *
   * @return the upper zone limit
   */
  public double getZoneUpper() {
    return zoneUpper;
  }

  /**
   * Set zone control for this CV.
   *
   * <p>
   * In zone control mode, the controller only acts when the CV leaves the specified zone. No
   * control action is taken as long as the CV remains within the zone. This reduces control effort
   * and wear on equipment.
   * </p>
   *
   * @param lower lower zone bound
   * @param upper upper zone bound
   * @return this variable for method chaining
   */
  public ControlledVariable setZone(double lower, double upper) {
    if (lower > upper) {
      throw new IllegalArgumentException("Zone lower must not exceed zone upper");
    }
    this.zoneLower = lower;
    this.zoneUpper = upper;
    this.zoneControl = true;
    // Set setpoint to zone center for compatibility
    this.setpoint = (lower + upper) / 2.0;
    return this;
  }

  /**
   * Get the predicted value from MPC calculation.
   *
   * @return the predicted CV value
   */
  public double getPredictedValue() {
    return predictedValue;
  }

  /**
   * Set the predicted value (used by MPC internals).
   *
   * @param value the predicted value
   */
  public void setPredictedValue(double value) {
    this.predictedValue = value;
  }

  @Override
  public ControlledVariable setBounds(double min, double max) {
    super.setBounds(min, max);
    return this;
  }

  @Override
  public ControlledVariable setEquipment(ProcessEquipmentInterface equipment) {
    super.setEquipment(equipment);
    return this;
  }

  @Override
  public ControlledVariable setPropertyName(String propertyName) {
    super.setPropertyName(propertyName);
    return this;
  }

  @Override
  public ControlledVariable setUnit(String unit) {
    super.setUnit(unit);
    return this;
  }

  @Override
  public double readValue() {
    if (equipment == null) {
      return currentValue;
    }

    // All process equipment implements getPressure and getTemperature via ProcessEquipmentInterface
    if ("pressure".equalsIgnoreCase(propertyName)) {
      if (unit != null) {
        currentValue = equipment.getPressure(unit);
      } else {
        currentValue = equipment.getPressure("bara");
      }
      return currentValue;
    }
    if ("temperature".equalsIgnoreCase(propertyName)) {
      if (unit != null) {
        currentValue = equipment.getTemperature(unit);
      } else {
        currentValue = equipment.getTemperature("C");
      }
      return currentValue;
    }

    // For streams, also support flowRate
    if (equipment instanceof StreamInterface) {
      StreamInterface stream = (StreamInterface) equipment;
      if ("flowRate".equalsIgnoreCase(propertyName)) {
        if (unit != null) {
          currentValue = stream.getFlowRate(unit);
        } else {
          currentValue = stream.getFlowRate("kg/hr");
        }
        return currentValue;
      }
    }

    return currentValue;
  }

  /**
   * Calculate the tracking error for this CV.
   *
   * @return the error (positive if above setpoint, negative if below)
   */
  public double getTrackingError() {
    if (!Double.isFinite(currentValue) || !Double.isFinite(setpoint)) {
      return 0.0;
    }
    return currentValue - setpoint;
  }

  /**
   * Calculate the soft constraint violation.
   *
   * @return the violation amount (0 if within soft limits)
   */
  public double getSoftViolation() {
    if (!Double.isFinite(currentValue)) {
      return 0.0;
    }
    if (currentValue < softMin) {
      return softMin - currentValue;
    }
    if (currentValue > softMax) {
      return currentValue - softMax;
    }
    return 0.0;
  }

  /**
   * Check if the current value is within zone.
   *
   * @return true if CV is within the control zone
   */
  public boolean isWithinZone() {
    if (!Double.isFinite(currentValue)) {
      return true;
    }
    return currentValue >= zoneLower && currentValue <= zoneUpper;
  }

  /**
   * Calculate the effective setpoint considering zone control.
   *
   * <p>
   * In zone control mode, returns the current value if within zone (no action needed), or the
   * nearest zone boundary if outside.
   * </p>
   *
   * @return the effective setpoint for control
   */
  public double getEffectiveSetpoint() {
    if (!zoneControl) {
      return setpoint;
    }
    if (!Double.isFinite(currentValue)) {
      return setpoint;
    }
    if (currentValue < zoneLower) {
      return zoneLower;
    }
    if (currentValue > zoneUpper) {
      return zoneUpper;
    }
    // Within zone - no control action needed
    return currentValue;
  }
}
