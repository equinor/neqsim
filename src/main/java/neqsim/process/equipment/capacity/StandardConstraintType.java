package neqsim.process.equipment.capacity;

/**
 * Standard constraint types commonly used across process equipment.
 *
 * <p>
 * This enum provides predefined constraint definitions with standard names and units. Use these
 * constants when creating CapacityConstraint objects for common constraint types to ensure
 * consistency across the codebase.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>
 * CapacityConstraint speedConstraint = StandardConstraintType.COMPRESSOR_SPEED.createConstraint()
 *     .setDesignValue(10000.0).setMaxValue(11000.0).setValueSupplier(() -> compressor.getSpeed());
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public enum StandardConstraintType {

  // ==================== Separator Constraints ====================

  /**
   * Gas load factor (K-factor) for separators. Represents the ratio of actual gas velocity to
   * design terminal velocity.
   */
  SEPARATOR_GAS_LOAD_FACTOR("gasLoadFactor", "m/s", CapacityConstraint.ConstraintType.SOFT,
      "Gas load factor (K-factor) - ratio of gas velocity to design terminal velocity"),

  /**
   * Liquid load factor for separators. Represents liquid throughput relative to design capacity.
   */
  SEPARATOR_LIQUID_LOAD_FACTOR("liquidLoadFactor", "m³/m²/hr",
      CapacityConstraint.ConstraintType.SOFT,
      "Liquid load factor - liquid throughput relative to design"),

  /**
   * Liquid residence time in separators. Minimum time required for phase separation.
   */
  SEPARATOR_RESIDENCE_TIME("residenceTime", "min", CapacityConstraint.ConstraintType.SOFT,
      "Liquid residence time - minimum time for adequate phase separation"),

  // ==================== Compressor Constraints ====================

  /**
   * Compressor rotational speed.
   */
  COMPRESSOR_SPEED("speed", "RPM", CapacityConstraint.ConstraintType.HARD,
      "Compressor rotational speed - limited by mechanical design"),

  /**
   * Compressor shaft power.
   */
  COMPRESSOR_POWER("power", "kW", CapacityConstraint.ConstraintType.HARD,
      "Compressor shaft power - limited by driver capacity"),

  /**
   * Compressor surge margin. Distance from surge line as percentage of flow.
   */
  COMPRESSOR_SURGE_MARGIN("surgeMargin", "%", CapacityConstraint.ConstraintType.HARD,
      "Surge margin - distance from surge line (minimum ~10% required)"),

  /**
   * Compressor stonewall margin. Distance from stonewall as percentage of flow.
   */
  COMPRESSOR_STONEWALL_MARGIN("stonewallMargin", "%", CapacityConstraint.ConstraintType.SOFT,
      "Stonewall margin - distance from choke condition"),

  /**
   * Compressor discharge temperature.
   */
  COMPRESSOR_DISCHARGE_TEMP("dischargeTemp", "°C", CapacityConstraint.ConstraintType.HARD,
      "Discharge temperature - limited by material and seal ratings"),

  // ==================== Pump Constraints ====================

  /**
   * Pump NPSH margin. Available NPSH minus required NPSH.
   */
  PUMP_NPSH_MARGIN("npshMargin", "m", CapacityConstraint.ConstraintType.HARD,
      "NPSH margin - prevents cavitation damage"),

  /**
   * Pump flow rate capacity.
   */
  PUMP_FLOW_RATE("flowRate", "m³/hr", CapacityConstraint.ConstraintType.SOFT,
      "Pump flow rate relative to design capacity"),

  /**
   * Pump power consumption.
   */
  PUMP_POWER("power", "kW", CapacityConstraint.ConstraintType.HARD,
      "Pump power - limited by motor rating"),

  // ==================== Heat Exchanger Constraints ====================

  /**
   * Heat exchanger duty.
   */
  HEAT_EXCHANGER_DUTY("duty", "kW", CapacityConstraint.ConstraintType.SOFT,
      "Heat transfer duty relative to design capacity"),

  /**
   * Heat exchanger approach temperature. Minimum temperature difference between streams.
   */
  HEAT_EXCHANGER_APPROACH_TEMP("approachTemp", "°C", CapacityConstraint.ConstraintType.SOFT,
      "Approach temperature - minimum feasible is ~3-5°C"),

  /**
   * Heat exchanger pressure drop.
   */
  HEAT_EXCHANGER_PRESSURE_DROP("pressureDrop", "bar", CapacityConstraint.ConstraintType.SOFT,
      "Pressure drop across heat exchanger"),

  // ==================== Valve Constraints ====================

  /**
   * Valve Cv utilization. Ratio of required Cv to installed Cv.
   */
  VALVE_CV_UTILIZATION("cvUtilization", "%", CapacityConstraint.ConstraintType.SOFT,
      "Cv utilization - ratio of required to installed Cv"),

  /**
   * Valve pressure drop.
   */
  VALVE_PRESSURE_DROP("pressureDrop", "bar", CapacityConstraint.ConstraintType.SOFT,
      "Pressure drop across valve"),

  /**
   * Valve opening position.
   */
  VALVE_OPENING("opening", "%", CapacityConstraint.ConstraintType.SOFT,
      "Valve opening percentage - optimal range 20-80%"),

  // ==================== Pipe Constraints ====================

  /**
   * Pipe fluid velocity.
   */
  PIPE_VELOCITY("velocity", "m/s", CapacityConstraint.ConstraintType.SOFT,
      "Fluid velocity in pipe"),

  /**
   * Pipe erosional velocity ratio. Ratio of actual to erosional velocity.
   */
  PIPE_EROSIONAL_VELOCITY("erosionalVelocityRatio", "%", CapacityConstraint.ConstraintType.HARD,
      "Ratio of actual to erosional velocity - must stay below 100%"),

  /**
   * Pipe pressure drop per unit length.
   */
  PIPE_PRESSURE_DROP("pressureDropPerLength", "bar/km", CapacityConstraint.ConstraintType.SOFT,
      "Pressure drop per unit length");

  /** Constraint name. */
  private final String name;

  /** Unit of measurement. */
  private final String unit;

  /** Constraint type. */
  private final CapacityConstraint.ConstraintType type;

  /** Description of the constraint. */
  private final String description;

  /**
   * Creates a standard constraint type.
   *
   * @param name the constraint name
   * @param unit the unit of measurement
   * @param type the constraint type
   * @param description the description
   */
  StandardConstraintType(String name, String unit, CapacityConstraint.ConstraintType type,
      String description) {
    this.name = name;
    this.unit = unit;
    this.type = type;
    this.description = description;
  }

  /**
   * Creates a new CapacityConstraint with this type's predefined settings.
   *
   * <p>
   * The returned constraint has the name, unit, type, and description preset. You still need to set
   * the design value, max value, and value supplier.
   * </p>
   *
   * @return a new CapacityConstraint with this type's settings
   */
  public CapacityConstraint createConstraint() {
    return new CapacityConstraint(name, unit, type).setDescription(description);
  }

  /**
   * Gets the constraint name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the unit of measurement.
   *
   * @return the unit
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Gets the constraint type.
   *
   * @return the type
   */
  public CapacityConstraint.ConstraintType getType() {
    return type;
  }

  /**
   * Gets the description.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }
}
