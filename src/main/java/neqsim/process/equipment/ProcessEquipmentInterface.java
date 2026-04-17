package neqsim.process.equipment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.ProcessElementInterface;
import neqsim.process.SimulationInterface;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.electricaldesign.ElectricalDesign;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.iec81346.ReferenceDesignation;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.instrumentdesign.InstrumentDesign;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.process.util.report.ReportConfig;
import neqsim.process.util.report.ReportConfig.DetailLevel;

/**
 * <p>
 * ProcessEquipmentInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ProcessEquipmentInterface extends ProcessElementInterface, SimulationInterface {
  /**
   * <p>
   * reportResults.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[][] reportResults();

  /**
   * <p>
   * Initialize a <code>initMechanicalDesign</code> for the equipment.
   * </p>
   */
  default void initMechanicalDesign() {}

  /**
   * <p>
   * Get a <code>mechanicalDesign</code> for the equipment.
   * </p>
   *
   * @return a {@link neqsim.process.mechanicaldesign.MechanicalDesign} object
   */
  public MechanicalDesign getMechanicalDesign();

  /**
   * <p>
   * Initialize an <code>electricalDesign</code> for the equipment.
   * </p>
   */
  default void initElectricalDesign() {}

  /**
   * <p>
   * Get an <code>electricalDesign</code> for the equipment.
   * </p>
   *
   * @return a {@link neqsim.process.electricaldesign.ElectricalDesign} object
   */
  default ElectricalDesign getElectricalDesign() {
    return new ElectricalDesign(this);
  }

  /**
   * Initialize an <code>instrumentDesign</code> for the equipment.
   */
  default void initInstrumentDesign() {}

  /**
   * Get an <code>instrumentDesign</code> for the equipment.
   *
   * @return a {@link neqsim.process.instrumentdesign.InstrumentDesign} object
   */
  default InstrumentDesign getInstrumentDesign() {
    return new InstrumentDesign(this);
  }

  /**
   * <p>
   * Check if process equipment needs recalculating.
   * </p>
   *
   * @return true or false
   */
  public default boolean needRecalculation() {
    return true;
  }

  /**
   * <p>
   * getSpecification.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getSpecification();

  /**
   * <p>
   * setSpecification.
   * </p>
   *
   * @param specification a {@link java.lang.String} object
   */
  public void setSpecification(String specification);

  /**
   * <p>
   * displayResult.
   * </p>
   */
  public void displayResult();

  /**
   * <p>
   * setRegulatorOutSignal.
   * </p>
   *
   * @param signal a double
   */
  public void setRegulatorOutSignal(double signal);

  /**
   * <p>
   * setController.
   * </p>
   *
   * @param controller a {@link neqsim.process.controllerdevice.ControllerDeviceInterface} object
   */
  public void setController(ControllerDeviceInterface controller);

  /**
   * <p>
   * getController.
   * </p>
   *
   * @return a {@link neqsim.process.controllerdevice.ControllerDeviceInterface} object
   */
  public ControllerDeviceInterface getController();

  /**
   * Adds a controller to this equipment with the given tag name.
   *
   * @param tag a unique tag identifying the controller (e.g. "PC-101", "LC-101")
   * @param controller a {@link neqsim.process.controllerdevice.ControllerDeviceInterface} object
   */
  public default void addController(String tag, ControllerDeviceInterface controller) {
    setController(controller);
  }

  /**
   * Gets a controller by tag name.
   *
   * @param tag the controller tag name
   * @return the controller, or null if not found
   */
  public default ControllerDeviceInterface getController(String tag) {
    return getController();
  }

  /**
   * Gets all controllers attached to this equipment.
   *
   * @return unmodifiable collection of controllers
   */
  public default Collection<ControllerDeviceInterface> getControllers() {
    ControllerDeviceInterface ctrl = getController();
    if (ctrl != null) {
      return Collections.singletonList(ctrl);
    }
    return Collections.emptyList();
  }

  /**
   * Returns all inlet streams connected to this equipment. Subclasses override to report their
   * specific inlets. Used by graph builders, DEXPI export, and auto-instrumentation to discover
   * topology without {@code instanceof} checks.
   *
   * @return unmodifiable list of inlet streams (empty by default)
   */
  public default List<StreamInterface> getInletStreams() {
    return Collections.emptyList();
  }

  /**
   * Returns all outlet streams produced by this equipment. Subclasses override to report their
   * specific outlets. Used by graph builders, DEXPI export, and auto-instrumentation to discover
   * topology without {@code instanceof} checks.
   *
   * @return unmodifiable list of outlet streams (empty by default)
   */
  public default List<StreamInterface> getOutletStreams() {
    return Collections.emptyList();
  }

  /**
   * <p>
   * getFluid.
   * </p>
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public default SystemInterface getFluid() {
    return getThermoSystem();
  }

  /**
   * <p>
   * getThermoSystem.
   * </p>
   *
   * @return a {@link neqsim.thermo.system.SystemInterface} object
   */
  public SystemInterface getThermoSystem();

  /**
   * <p>
   * getMassBalance in kg/sec.
   * </p>
   *
   * @return The mass balance of the process equipment in kg/sec.
   */
  public double getMassBalance();

  /**
   * <p>
   * Getter for the field <code>pressure</code>.
   * </p>
   *
   * @return Pressure in bara
   */
  public double getPressure();

  /**
   * <p>
   * Getter for the field <code>pressure</code> converted to specified unit.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getPressure(String unit);

  /**
   * <p>
   * Getter for the field <code>temperature</code> converted to specified unit.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getTemperature(String unit);

  /**
   * <p>
   * Getter for the field <code>temperature</code>.
   * </p>
   *
   * @return a double
   */
  public double getTemperature();

  /**
   * <p>
   * Setter for the field <code>pressure</code>.
   * </p>
   *
   * @param pressure a double
   */
  public void setPressure(double pressure);

  /**
   * <p>
   * Setter for the field <code>temperature</code>.
   * </p>
   *
   * @param temperature Temperature in Kelvin
   */
  public void setTemperature(double temperature);

  /**
   * <p>
   * runConditionAnalysis.
   * </p>
   *
   * @param refExchanger a {@link neqsim.process.equipment.ProcessEquipmentInterface} object
   */
  public void runConditionAnalysis(ProcessEquipmentInterface refExchanger);

  /**
   * <p>
   * getConditionAnalysisMessage.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getConditionAnalysisMessage();

  /**
   * Get exergy change production of the process equipment.
   *
   * @param unit Supported units are J and kJ
   * @param surroundingTemperature The surrounding temperature in Kelvin
   * @return change in exergy in specified unit
   */
  public double getExergyChange(String unit, double surroundingTemperature);

  /**
   * <p>
   * getResultTable.
   * </p>
   *
   * @return an array of {@link java.lang.String} objects
   */
  public String[][] getResultTable();

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o);

  /** {@inheritDoc} */
  @Override
  public int hashCode();

  /**
   * <p>
   * Serializes the Process Equipment along with its state to a JSON string.
   * </p>
   *
   * @return json string.
   */
  public String toJson();

  /**
   * Serializes the Process Equipment with configurable level of detail.
   *
   * @param cfg report configuration
   * @return json string
   */
  public default String toJson(ReportConfig cfg) {
    if (cfg != null && cfg.getDetailLevel(getName()) == DetailLevel.HIDE) {
      return null;
    }
    return toJson();
  }

  /** {@inheritDoc} */
  @Override
  public String getReport_json();

  /**
   * <p>
   * getEntropyProduction.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public default double getEntropyProduction(String unit) {
    return 0.0;
  }

  /**
   * <p>
   * getMassBalance.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public default double getMassBalance(String unit) {
    return 0.0;
  }

  /**
   * <p>
   * getExergyChange.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public default double getExergyChange(String unit) {
    return 0.0;
  }

  /**
   * <p>
   * getCapacityDuty.
   * </p>
   *
   * @return a double
   */
  public default double getCapacityDuty() {
    return 0.0;
  }

  /**
   * <p>
   * getCapacityMax.
   * </p>
   *
   * @return a double
   */
  public default double getCapacityMax() {
    return 0.0;
  }

  /**
   * <p>
   * getRestCapacity.
   * </p>
   *
   * @return a double
   */
  public default double getRestCapacity() {
    return getCapacityMax() - getCapacityDuty();
  }

  /**
   * Validate the process equipment before execution.
   *
   * <p>
   * Checks for common setup errors:
   * <ul>
   * <li>Equipment has a valid name</li>
   * <li>Input streams connected</li>
   * <li>Operating parameters in valid ranges</li>
   * </ul>
   *
   * @return validation result with errors and warnings
   */
  public default neqsim.util.validation.ValidationResult validateSetup() {
    neqsim.util.validation.ValidationResult result =
        new neqsim.util.validation.ValidationResult(getName());

    // Check: Equipment has a valid name
    if (getName() == null || getName().isEmpty()) {
      result.addError("equipment", "Equipment has no name", "Set equipment name in constructor");
    }

    return result;
  }

  /**
   * Checks if the current simulation result is physically valid.
   *
   * <p>
   * Returns false if calculated values are outside physically possible ranges. This method should
   * be overridden by specific equipment types to perform equipment-specific validation. For
   * example:
   * <ul>
   * <li>Compressor: power must be positive, head must be positive</li>
   * <li>Heat exchanger: duty direction must match temperature change</li>
   * <li>Separator: phase fractions must sum to 1.0</li>
   * </ul>
   *
   * @return true if simulation results are physically valid, false otherwise
   */
  public default boolean isSimulationValid() {
    // Default implementation - check if equipment has valid thermodynamic system
    // Equipment with no thermodynamic system (e.g., some utilities) are considered valid
    // Subclasses should override for equipment-specific validation
    SystemInterface thermo = getThermoSystem();
    if (thermo == null) {
      // No local thermo - assume equipment is valid unless it overrides this method
      // This handles heat exchangers, valves, etc. that may not store thermo locally
      return true;
    }
    // Check for NaN in basic properties
    if (Double.isNaN(thermo.getTemperature()) || Double.isNaN(thermo.getPressure())) {
      return false;
    }
    return true;
  }

  /**
   * Gets validation errors for the current simulation state.
   *
   * <p>
   * Returns a list of human-readable error messages describing why the simulation result is
   * invalid. Returns an empty list if the simulation is valid.
   * </p>
   *
   * @return list of validation error messages, empty if valid
   */
  public default List<String> getSimulationValidationErrors() {
    List<String> errors = new ArrayList<String>();
    SystemInterface thermo = getThermoSystem();

    if (thermo == null) {
      // No local thermo - not an error for default case
      // Equipment should override if it requires thermo validation
      return errors;
    }

    if (Double.isNaN(thermo.getTemperature())) {
      errors.add(getName() + ": Temperature is NaN");
    }
    if (Double.isNaN(thermo.getPressure())) {
      errors.add(getName() + ": Pressure is NaN");
    }

    return errors;
  }

  /**
   * Checks if the equipment is operating within its valid operating envelope.
   *
   * <p>
   * This is different from capacity utilization - it checks whether the equipment can physically
   * operate at the current conditions, not whether it's operating efficiently. For example:
   * <ul>
   * <li>Compressor: checks if between surge and stonewall</li>
   * <li>Pump: checks if above minimum flow (no cavitation)</li>
   * <li>Heat exchanger: checks if approach temperature is positive</li>
   * </ul>
   *
   * @return true if operating within valid envelope
   */
  public default boolean isWithinOperatingEnvelope() {
    return isSimulationValid();
  }

  /**
   * Gets the reason why equipment is outside its operating envelope.
   *
   * @return description of envelope violation, or null if within envelope
   */
  public default String getOperatingEnvelopeViolation() {
    if (isWithinOperatingEnvelope()) {
      return null;
    }
    List<String> errors = getSimulationValidationErrors();
    if (!errors.isEmpty()) {
      return errors.get(0);
    }
    return "Unknown operating envelope violation";
  }

  // ============================================================
  // Unified outlet property access
  // ============================================================

  /**
   * Returns the temperature of the primary outlet stream in the specified unit.
   *
   * <p>
   * Works uniformly across all equipment types by using {@link #getOutletStreams()}. For equipment
   * with multiple outlets (e.g., separators), returns the first outlet's temperature.
   * </p>
   *
   * @param unit temperature unit, e.g. "C", "K"
   * @return outlet temperature in specified unit, or {@link Double#NaN} if no outlet streams
   */
  public default double getOutletTemperature(String unit) {
    List<StreamInterface> outlets = getOutletStreams();
    if (outlets.isEmpty()) {
      return Double.NaN;
    }
    return outlets.get(0).getTemperature(unit);
  }

  /**
   * Returns the pressure of the primary outlet stream in the specified unit.
   *
   * <p>
   * Works uniformly across all equipment types by using {@link #getOutletStreams()}. For equipment
   * with multiple outlets, returns the first outlet's pressure.
   * </p>
   *
   * @param unit pressure unit, e.g. "bara", "barg", "Pa"
   * @return outlet pressure in specified unit, or {@link Double#NaN} if no outlet streams
   */
  public default double getOutletPressure(String unit) {
    List<StreamInterface> outlets = getOutletStreams();
    if (outlets.isEmpty()) {
      return Double.NaN;
    }
    return outlets.get(0).getPressure(unit);
  }

  /**
   * Returns the total flow rate across all outlet streams in the specified unit.
   *
   * <p>
   * For single-outlet equipment (compressor, heater, valve), returns the outlet flow rate. For
   * multi-outlet equipment (separator), returns the sum of all outlet flow rates.
   * </p>
   *
   * @param unit flow unit, e.g. "kg/hr", "Sm3/hr", "m3/hr"
   * @return total outlet flow rate in specified unit, or {@link Double#NaN} if no outlet streams
   */
  public default double getOutletFlowRate(String unit) {
    List<StreamInterface> outlets = getOutletStreams();
    if (outlets.isEmpty()) {
      return Double.NaN;
    }
    double total = 0.0;
    for (StreamInterface s : outlets) {
      total += s.getFlowRate(unit);
    }
    return total;
  }

  // ============================================================
  // Equipment state as Map (for Python/JSON integration)
  // ============================================================

  /**
   * Returns a map of key equipment properties with values and units.
   *
   * <p>
   * Provides a unified way to access equipment state without knowing the specific equipment type.
   * Each entry in the outer map has a property name (e.g. "temperature", "pressure"). Each inner
   * map contains "value" (Double) and "unit" (String).
   * </p>
   *
   * <p>
   * The default implementation uses {@link #getOutletStreams()} to report outlet conditions.
   * Subclasses override this to add equipment-specific properties (e.g., valve opening, compressor
   * power, separator liquid levels).
   * </p>
   *
   * @param temperatureUnit temperature unit (e.g. "C")
   * @param pressureUnit pressure unit (e.g. "bara")
   * @param flowUnit flow unit (e.g. "kg/hr")
   * @return map of property name to value/unit maps
   */
  public default Map<String, Map<String, Object>> getEquipmentState(String temperatureUnit,
      String pressureUnit, String flowUnit) {
    Map<String, Map<String, Object>> state = new LinkedHashMap<String, Map<String, Object>>();
    List<StreamInterface> outlets = getOutletStreams();
    if (!outlets.isEmpty()) {
      StreamInterface primary = outlets.get(0);
      state.put("temperature",
          createStateEntry(primary.getTemperature(temperatureUnit), temperatureUnit));
      state.put("pressure", createStateEntry(primary.getPressure(pressureUnit), pressureUnit));
      state.put("flow", createStateEntry(primary.getFlowRate(flowUnit), flowUnit));
    }
    return state;
  }

  /**
   * Helper to create a state entry map with value and unit.
   *
   * @param value the numeric value
   * @param unit the unit string
   * @return map with "value" and "unit" keys
   */
  static Map<String, Object> createStateEntry(double value, String unit) {
    Map<String, Object> entry = new LinkedHashMap<String, Object>();
    entry.put("value", value);
    entry.put("unit", unit);
    return entry;
  }

  // ============================================================
  // IEC 81346 Reference Designation Support
  // ============================================================

  /**
   * Returns the IEC 81346 reference designation for this equipment.
   *
   * <p>
   * The reference designation encodes three aspects per IEC 81346: function (what the system does),
   * product (what the equipment is), and location (where it is installed).
   * </p>
   *
   * @return the reference designation object, never null
   */
  public default ReferenceDesignation getReferenceDesignation() {
    return new ReferenceDesignation();
  }

  /**
   * Sets the IEC 81346 reference designation for this equipment.
   *
   * @param referenceDesignation the reference designation to set
   */
  public default void setReferenceDesignation(ReferenceDesignation referenceDesignation) {
    // Default no-op; overridden in ProcessEquipmentBaseClass
  }

  /**
   * Returns the full IEC 81346 reference designation string.
   *
   * <p>
   * Convenience method equivalent to
   * {@code getReferenceDesignation().toReferenceDesignationString()}.
   * </p>
   *
   * @return the formatted reference designation string, e.g. "=A1.K1-B1+P1.M1"
   */
  public default String getReferenceDesignationString() {
    return getReferenceDesignation().toReferenceDesignationString();
  }

  // ============================================================
  // Capacity Constraint Support (universal for all equipment)
  // ============================================================

  /**
   * Gets all capacity constraints defined for this equipment.
   *
   * <p>
   * Returns an unmodifiable map of constraint name to constraint object. Constraints are used by
   * the optimization framework to detect bottlenecks, enforce limits, and guide production
   * allocation. Equipment subclasses populate this map via
   * {@link #addCapacityConstraint(CapacityConstraint)}.
   * </p>
   *
   * @return unmodifiable map of constraint name to constraint, empty if none defined
   */
  public default Map<String, CapacityConstraint> getCapacityConstraints() {
    return Collections.emptyMap();
  }

  /**
   * Adds a capacity constraint to this equipment.
   *
   * <p>
   * Constraints can be added at any time. If a constraint with the same name already exists, it is
   * replaced. Use the fluent builder API on {@link CapacityConstraint} to configure the constraint
   * before adding.
   * </p>
   *
   * @param constraint the capacity constraint to add (ignored if null)
   */
  public default void addCapacityConstraint(CapacityConstraint constraint) {
    // No-op default; overridden in ProcessEquipmentBaseClass
  }

  /**
   * Gets the bottleneck (most limiting) constraint for this equipment.
   *
   * <p>
   * Returns the enabled constraint with the highest utilization ratio. If no constraints are
   * defined or enabled, returns null.
   * </p>
   *
   * @return the most limiting constraint, or null if none
   */
  public default CapacityConstraint getBottleneckConstraint() {
    Map<String, CapacityConstraint> constraints = getCapacityConstraints();
    if (constraints == null || constraints.isEmpty()) {
      return null;
    }
    CapacityConstraint bottleneck = null;
    double maxUtil = -1.0;
    for (CapacityConstraint c : constraints.values()) {
      if (c.isEnabled()) {
        double util = c.getUtilization();
        if (util > maxUtil) {
          maxUtil = util;
          bottleneck = c;
        }
      }
    }
    return bottleneck;
  }

  /**
   * Checks if any capacity constraint is violated (exceeds 100% utilization).
   *
   * <p>
   * A violated constraint means the equipment is operating beyond its design capacity. For HARD
   * constraints, this may indicate equipment trip or failure. For SOFT constraints, this indicates
   * reduced efficiency or accelerated wear.
   * </p>
   *
   * @return true if any enabled constraint utilization exceeds 1.0 (100%)
   */
  public default boolean isCapacityExceeded() {
    Map<String, CapacityConstraint> constraints = getCapacityConstraints();
    if (constraints == null) {
      return false;
    }
    for (CapacityConstraint c : constraints.values()) {
      if (c.isEnabled() && c.isViolated()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if any HARD constraint limit is exceeded.
   *
   * <p>
   * HARD limits represent absolute equipment limits (e.g., maximum speed) that cannot be exceeded
   * without equipment trip or damage. This is more severe than general capacity exceedance.
   * </p>
   *
   * @return true if any enabled HARD constraint's max value is exceeded
   */
  public default boolean isHardLimitExceeded() {
    Map<String, CapacityConstraint> constraints = getCapacityConstraints();
    if (constraints == null) {
      return false;
    }
    for (CapacityConstraint c : constraints.values()) {
      if (c.isEnabled() && c.isHardLimitExceeded()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Gets the maximum utilization across all enabled constraints.
   *
   * <p>
   * Returns the highest utilization ratio across all enabled constraints. Values above 1.0 indicate
   * the equipment is over capacity. Returns 0.0 if no constraints are defined or enabled.
   * </p>
   *
   * @return maximum utilization as fraction (1.0 = 100% of design capacity)
   */
  public default double getMaxUtilization() {
    Map<String, CapacityConstraint> constraints = getCapacityConstraints();
    if (constraints == null || constraints.isEmpty()) {
      return 0.0;
    }
    double maxUtil = 0.0;
    for (CapacityConstraint c : constraints.values()) {
      if (c.isEnabled()) {
        double util = c.getUtilization();
        if (util > maxUtil) {
          maxUtil = util;
        }
      }
    }
    return maxUtil;
  }

  /**
   * Gets the maximum utilization as a percentage.
   *
   * @return maximum utilization as percentage (100.0 = 100% of design capacity)
   */
  public default double getMaxUtilizationPercent() {
    return getMaxUtilization() * 100.0;
  }

  /**
   * Gets the available margin (headroom) on the most limiting constraint.
   *
   * <p>
   * Returns the remaining capacity before the bottleneck constraint reaches 100%. Negative values
   * indicate the equipment is already over capacity.
   * </p>
   *
   * @return available margin as fraction (0.2 = 20% headroom remaining)
   */
  public default double getAvailableMargin() {
    return 1.0 - getMaxUtilization();
  }

  /**
   * Gets the available margin as a percentage.
   *
   * @return available margin as percentage
   */
  public default double getAvailableMarginPercent() {
    return getAvailableMargin() * 100.0;
  }

  /**
   * Checks if any enabled constraint is near its limit (above warning threshold).
   *
   * <p>
   * The warning threshold is typically set at 90% of design capacity to provide early warning
   * before constraints are violated.
   * </p>
   *
   * @return true if any enabled constraint is above its warning threshold
   */
  public default boolean isNearCapacityLimit() {
    Map<String, CapacityConstraint> constraints = getCapacityConstraints();
    if (constraints == null) {
      return false;
    }
    for (CapacityConstraint c : constraints.values()) {
      if (c.isEnabled() && c.isNearLimit()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Gets a summary of all enabled constraint utilizations.
   *
   * <p>
   * Returns a map of constraint names to their current utilization percentages. Useful for
   * reporting and visualization.
   * </p>
   *
   * @return map of constraint name to utilization percentage
   */
  public default Map<String, Double> getUtilizationSummary() {
    Map<String, CapacityConstraint> constraints = getCapacityConstraints();
    Map<String, Double> summary = new LinkedHashMap<String, Double>();
    if (constraints == null) {
      return summary;
    }
    for (Map.Entry<String, CapacityConstraint> entry : constraints.entrySet()) {
      CapacityConstraint c = entry.getValue();
      if (c.isEnabled()) {
        summary.put(entry.getKey(), c.getUtilization() * 100.0);
      }
    }
    return summary;
  }
}
