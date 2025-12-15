package neqsim.process.mpc;

import java.io.Serializable;
import neqsim.process.equipment.ProcessEquipmentInterface;

/**
 * Base class for MPC variables (manipulated, controlled, or disturbance).
 *
 * <p>
 * An MPCVariable binds a process equipment property to the MPC formulation. It defines how to read
 * the current value from the equipment and, for manipulated variables, how to write new setpoints.
 * This abstraction allows the MPC to work with any NeqSim process equipment without hard-coding
 * specific property accessors.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * {@code
 * // Create a manipulated variable for valve opening
 * ManipulatedVariable mv = new ManipulatedVariable("ValveOpening", valve, "opening")
 *     .setBounds(0.0, 1.0).setRateLimit(-0.1, 0.1);
 *
 * // Create a controlled variable for separator pressure
 * ControlledVariable cv = new ControlledVariable("Pressure", separator, "pressure", "bara")
 *     .setSetpoint(50.0).setSoftConstraints(45.0, 55.0);
 * }
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 * @since 3.0
 */
public abstract class MPCVariable implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Unique identifier for this variable. */
  protected final String name;

  /** Human-readable description of this variable. */
  protected String description;

  /** The process equipment this variable is bound to. */
  protected ProcessEquipmentInterface equipment;

  /** The property name to read/write on the equipment. */
  protected String propertyName;

  /** The unit for the property value. */
  protected String unit;

  /** Current value of the variable. */
  protected double currentValue = Double.NaN;

  /** Minimum allowed value for this variable. */
  protected double minValue = Double.NEGATIVE_INFINITY;

  /** Maximum allowed value for this variable. */
  protected double maxValue = Double.POSITIVE_INFINITY;

  /**
   * Construct an MPC variable with a name.
   *
   * @param name unique identifier for this variable
   */
  protected MPCVariable(String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Variable name must be provided");
    }
    this.name = name;
  }

  /**
   * Construct an MPC variable bound to equipment.
   *
   * @param name unique identifier for this variable
   * @param equipment the process equipment to bind to
   * @param propertyName the property to read/write
   */
  protected MPCVariable(String name, ProcessEquipmentInterface equipment, String propertyName) {
    this(name);
    this.equipment = equipment;
    this.propertyName = propertyName;
  }

  /**
   * Construct an MPC variable bound to equipment with unit specification.
   *
   * @param name unique identifier for this variable
   * @param equipment the process equipment to bind to
   * @param propertyName the property to read/write
   * @param unit the unit for the property value
   */
  protected MPCVariable(String name, ProcessEquipmentInterface equipment, String propertyName,
      String unit) {
    this(name, equipment, propertyName);
    this.unit = unit;
  }

  /**
   * Get the variable name.
   *
   * @return the unique identifier
   */
  public String getName() {
    return name;
  }

  /**
   * Get the bound equipment.
   *
   * @return the process equipment, or null if not bound
   */
  public ProcessEquipmentInterface getEquipment() {
    return equipment;
  }

  /**
   * Set the bound equipment.
   *
   * @param equipment the process equipment to bind to
   * @return this variable for method chaining
   */
  public MPCVariable setEquipment(ProcessEquipmentInterface equipment) {
    this.equipment = equipment;
    return this;
  }

  /**
   * Get the property name.
   *
   * @return the property name being read/written
   */
  public String getPropertyName() {
    return propertyName;
  }

  /**
   * Set the property name to read/write.
   *
   * @param propertyName the property name
   * @return this variable for method chaining
   */
  public MPCVariable setPropertyName(String propertyName) {
    this.propertyName = propertyName;
    return this;
  }

  /**
   * Get the unit for this variable.
   *
   * @return the unit string, or null if not specified
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Set the unit for this variable.
   *
   * @param unit the unit string
   * @return this variable for method chaining
   */
  public MPCVariable setUnit(String unit) {
    this.unit = unit;
    return this;
  }

  /**
   * Get the description for this variable.
   *
   * @return the description, or null if not set
   */
  public String getDescription() {
    return description;
  }

  /**
   * Set the description for this variable.
   *
   * @param description the human-readable description
   * @return this variable for method chaining
   */
  public MPCVariable setDescription(String description) {
    this.description = description;
    return this;
  }

  /**
   * Get the minimum allowed value.
   *
   * @return the minimum bound
   */
  public double getMinValue() {
    return minValue;
  }

  /**
   * Get the maximum allowed value.
   *
   * @return the maximum bound
   */
  public double getMaxValue() {
    return maxValue;
  }

  /**
   * Set bounds for this variable.
   *
   * @param min minimum allowed value
   * @param max maximum allowed value
   * @return this variable for method chaining
   */
  public MPCVariable setBounds(double min, double max) {
    if (min > max) {
      throw new IllegalArgumentException("Minimum bound must not exceed maximum bound");
    }
    this.minValue = min;
    this.maxValue = max;
    return this;
  }

  /**
   * Get the current value of this variable.
   *
   * @return the current value
   */
  public double getCurrentValue() {
    return currentValue;
  }

  /**
   * Set the current value (used for caching/tracking).
   *
   * @param value the current value
   */
  public void setCurrentValue(double value) {
    this.currentValue = value;
  }

  /**
   * Read the current value from the bound equipment.
   *
   * <p>
   * This method uses reflection or equipment-specific accessors to read the property value. The
   * implementation varies by variable type and property.
   * </p>
   *
   * @return the current value read from equipment
   */
  public abstract double readValue();

  /**
   * Get the type of this MPC variable.
   *
   * @return the variable type (MV, CV, or DV)
   */
  public abstract MPCVariableType getType();

  /**
   * Enumeration of MPC variable types.
   */
  public enum MPCVariableType {
    /** Manipulated Variable - can be adjusted by the controller. */
    MANIPULATED,
    /** Controlled Variable - target to be controlled to setpoint. */
    CONTROLLED,
    /** Disturbance Variable - measured but not controlled (feedforward). */
    DISTURBANCE,
    /** State Variable - internal model state for nonlinear MPC. */
    STATE
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(getType()).append(" '").append(name).append("'");
    if (equipment != null) {
      sb.append(" on ").append(equipment.getName());
    }
    if (propertyName != null) {
      sb.append(".").append(propertyName);
    }
    if (unit != null) {
      sb.append(" [").append(unit).append("]");
    }
    return sb.toString();
  }
}
