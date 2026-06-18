package neqsim.process.maintenance;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.GsonBuilder;

/**
 * Defines the temporary operating constraints (envelope) during a maintenance deferral period. This
 * specifies the limits within which equipment can safely operate while degraded.
 *
 * <p>
 * Constraints include maximum/minimum values for flow, pressure, temperature, speed, and power that
 * ensure the equipment stays within acceptable margins during deferred maintenance.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class TemporaryOperatingEnvelope implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /**
   * A single operating constraint within the envelope.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static class Constraint implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1001L;

    private final String parameter;
    private final String unit;
    private final double minValue;
    private final double maxValue;
    private final double normalValue;
    private final String rationale;

    /**
     * Constructs a constraint.
     *
     * @param parameter parameter name (e.g., "flow_rate", "discharge_pressure")
     * @param unit unit of measurement
     * @param minValue minimum allowed value
     * @param maxValue maximum allowed value
     * @param normalValue normal operating value
     * @param rationale reason for constraint
     */
    public Constraint(String parameter, String unit, double minValue, double maxValue,
        double normalValue, String rationale) {
      this.parameter = parameter;
      this.unit = unit;
      this.minValue = minValue;
      this.maxValue = maxValue;
      this.normalValue = normalValue;
      this.rationale = rationale;
    }

    /**
     * Gets the parameter name.
     *
     * @return parameter name
     */
    public String getParameter() {
      return parameter;
    }

    /**
     * Gets the unit.
     *
     * @return unit string
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Gets the minimum allowed value.
     *
     * @return minimum value
     */
    public double getMinValue() {
      return minValue;
    }

    /**
     * Gets the maximum allowed value.
     *
     * @return maximum value
     */
    public double getMaxValue() {
      return maxValue;
    }

    /**
     * Gets the normal operating value.
     *
     * @return normal value
     */
    public double getNormalValue() {
      return normalValue;
    }

    /**
     * Gets the rationale for this constraint.
     *
     * @return rationale text
     */
    public String getRationale() {
      return rationale;
    }

    /**
     * Checks if a given value is within this constraint.
     *
     * @param value value to check
     * @return true if within bounds
     */
    public boolean isWithinBounds(double value) {
      return value >= minValue && value <= maxValue;
    }

    /**
     * Gets the margin from the nearest limit as a fraction of the constraint range.
     *
     * @param value current value
     * @return margin fraction (0 = at limit, 1 = at center)
     */
    public double getMarginFraction(double value) {
      if (maxValue <= minValue) {
        return 0.0;
      }
      double range = maxValue - minValue;
      double distFromMin = value - minValue;
      double distFromMax = maxValue - value;
      double minDist = Math.min(distFromMin, distFromMax);
      return Math.max(0.0, minDist / (range / 2.0));
    }
  }

  private String equipmentName = "";
  private double validityPeriodHours = 0.0;
  private final List<Constraint> constraints = new ArrayList<Constraint>();
  private String monitoringRequirements = "";
  private String escalationCriteria = "";

  /**
   * Constructs a temporary operating envelope.
   *
   * @param equipmentName name of the equipment
   * @param validityPeriodHours how long this envelope is valid
   */
  public TemporaryOperatingEnvelope(String equipmentName, double validityPeriodHours) {
    this.equipmentName = equipmentName;
    this.validityPeriodHours = validityPeriodHours;
  }

  /**
   * Adds a constraint to the envelope.
   *
   * @param constraint the constraint to add
   */
  public void addConstraint(Constraint constraint) {
    constraints.add(constraint);
  }

  /**
   * Adds a constraint with all parameters.
   *
   * @param parameter parameter name
   * @param unit unit of measurement
   * @param minValue minimum allowed value
   * @param maxValue maximum allowed value
   * @param normalValue normal operating value
   * @param rationale reason for constraint
   */
  public void addConstraint(String parameter, String unit, double minValue, double maxValue,
      double normalValue, String rationale) {
    constraints.add(new Constraint(parameter, unit, minValue, maxValue, normalValue, rationale));
  }

  /**
   * Gets all constraints.
   *
   * @return unmodifiable list of constraints
   */
  public List<Constraint> getConstraints() {
    return Collections.unmodifiableList(constraints);
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
   * Gets the validity period in hours.
   *
   * @return validity period
   */
  public double getValidityPeriodHours() {
    return validityPeriodHours;
  }

  /**
   * Sets the monitoring requirements text.
   *
   * @param requirements monitoring requirements description
   */
  public void setMonitoringRequirements(String requirements) {
    this.monitoringRequirements = requirements;
  }

  /**
   * Gets the monitoring requirements.
   *
   * @return monitoring requirements text
   */
  public String getMonitoringRequirements() {
    return monitoringRequirements;
  }

  /**
   * Sets the escalation criteria.
   *
   * @param criteria escalation criteria description
   */
  public void setEscalationCriteria(String criteria) {
    this.escalationCriteria = criteria;
  }

  /**
   * Gets the escalation criteria.
   *
   * @return escalation criteria text
   */
  public String getEscalationCriteria() {
    return escalationCriteria;
  }

  /**
   * Checks if all current operating values are within the envelope.
   *
   * @param parameterNames array of parameter names
   * @param values array of current values (same order as parameter names)
   * @return true if all values are within bounds
   */
  public boolean isWithinEnvelope(String[] parameterNames, double[] values) {
    for (int i = 0; i < parameterNames.length; i++) {
      for (Constraint c : constraints) {
        if (c.getParameter().equals(parameterNames[i])) {
          if (!c.isWithinBounds(values[i])) {
            return false;
          }
        }
      }
    }
    return true;
  }

  /**
   * Generates a JSON representation.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().create().toJson(this);
  }
}
