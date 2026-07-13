package neqsim.process.automation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.automation.SimulationVariable.VariableType;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorTrain;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.ejector.Ejector;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.heatexchanger.HeatExchanger;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.pipeline.Pipeline;
import neqsim.process.equipment.pipeline.WaterHammerPipe;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.reactor.GibbsReactor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.splitter.ComponentSplitter;
import neqsim.process.equipment.splitter.Splitter;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.tank.Tank;
import neqsim.process.equipment.util.Adjuster;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Provides a stable, string-addressable automation API for interacting with a running NeqSim {@link ProcessSystem} or
 * {@link ProcessModel}. Variables in the simulation are reachable through stable dot-notation paths such as
 * {@code "separator-1.gasOutStream.temperature"}, removing the need to navigate Java objects directly.
 *
 * <p>
 * When backed by a {@link ProcessModel} (multi-area plant), addresses use area-qualified syntax:
 * {@code "AreaName::UnitName.property"} or {@code "AreaName::UnitName.port.property"}.
 * </p>
 *
 * <p>
 * The core operations are:
 * </p>
 * <ul>
 * <li>{@link #getUnitList()} &mdash; list all unit operation names</li>
 * <li>{@link #getAreaList()} &mdash; list all process area names (ProcessModel only)</li>
 * <li>{@link #getUnitList(String)} &mdash; list units in a specific area</li>
 * <li>{@link #getVariableList(String)} &mdash; list all variables for a unit</li>
 * <li>{@link #getVariableValue(String, String)} &mdash; read a variable by address</li>
 * <li>{@link #setVariableValue(String, double, String)} &mdash; write an input variable</li>
 * </ul>
 *
 * <p>
 * <strong>Address format:</strong> {@code unitName.property} or {@code unitName.streamPort.property}
 * </p>
 *
 * <p>
 * <strong>Example usage:</strong>
 * </p>
 *
 * <pre>
 * // Single ProcessSystem
 * ProcessAutomation auto = new ProcessAutomation(process);
 *
 * // List all units
 * List&lt;String&gt; units = auto.getUnitList();
 *
 * // List variables for a separator
 * List&lt;SimulationVariable&gt; vars = auto.getVariableList("HP Sep");
 *
 * // Read a value
 * double temp = auto.getVariableValue("HP Sep.gasOutStream.temperature", "C");
 *
 * // Set an input
 * auto.setVariableValue("Compressor.outletPressure", 120.0, "bara");
 *
 * // Multi-area ProcessModel
 * ProcessAutomation plantAuto = new ProcessAutomation(plantModel);
 * List&lt;String&gt; areas = plantAuto.getAreaList();
 * List&lt;String&gt; areaUnits = plantAuto.getUnitList("Separation");
 * double p = plantAuto.getVariableValue("Separation::HP Sep.pressure", "bara");
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ProcessAutomation {

  /** Separator between area name and unit address in multi-area mode. */
  public static final String AREA_SEPARATOR = "::";

  /**
   * Stable schema version for JSON responses produced by this facade. Increment the minor for additive changes, the
   * major for breaking changes. Agents and MCP clients should branch on this value when parsing responses.
   */
  public static final String SCHEMA_VERSION = "1.0";

  private final ProcessSystem processSystem;
  private final ProcessModel processModel;
  private final AutomationDiagnostics diagnostics;
  /**
   * Registry of typed write validators consulted by {@link #setVariableValueValidated(String, double, String)} and
   * {@link #setValuesTransactional(Map, String)}. Defaults to {@link WriteValidatorRegistry#createDefault()}.
   */
  private WriteValidatorRegistry validatorRegistry = WriteValidatorRegistry.createDefault();
  /**
   * Dirty flag: true when one or more inputs have been changed via {@link #setVariableValue} since the last successful
   * run. Used by {@link #isDirty()}, {@link #runIfDirty()}, and the {@code stale} warning emitted by
   * {@link #getVariableValueSafe}.
   */
  private boolean dirty = false;

  /**
   * Creates an automation facade for a single process system.
   *
   * @param processSystem the process system to automate
   */
  public ProcessAutomation(ProcessSystem processSystem) {
    if (processSystem == null) {
      throw new IllegalArgumentException("processSystem must not be null");
    }
    this.processSystem = processSystem;
    this.processModel = null;
    this.diagnostics = new AutomationDiagnostics();
  }

  /**
   * Creates an automation facade for a multi-area process model.
   *
   * <p>
   * In this mode, addresses use area-qualified syntax: {@code "AreaName::UnitName.property"}.
   * </p>
   *
   * @param processModel the process model to automate
   */
  public ProcessAutomation(ProcessModel processModel) {
    if (processModel == null) {
      throw new IllegalArgumentException("processModel must not be null");
    }
    this.processModel = processModel;
    this.processSystem = null;
    this.diagnostics = new AutomationDiagnostics();
  }

  /**
   * Returns whether this automation facade is backed by a {@link ProcessModel} (multi-area).
   *
   * @return true if backed by a ProcessModel, false if backed by a single ProcessSystem
   */
  public boolean isMultiArea() {
    return processModel != null;
  }

  /**
   * Returns the diagnostics instance for this automation facade. The diagnostics provide fuzzy name matching,
   * auto-correction, physical value validation, and operation history tracking.
   *
   * @return the automation diagnostics
   */
  public AutomationDiagnostics getDiagnostics() {
    return diagnostics;
  }

  /**
   * Reads a variable value with self-healing: if the exact address fails, attempts auto-correction via fuzzy matching
   * against known unit names and variable addresses. Returns a JSON string with the value on success, or a diagnostic
   * result with suggestions on failure.
   *
   * @param address the dot-notation address, e.g. "separator-1.gasOutStream.temperature"
   * @param unitOfMeasure the desired unit
   * @return JSON result string with either value or diagnostic information
   */
  public String getVariableValueSafe(String address, String unitOfMeasure) {
    try {
      double value = getVariableValue(address, unitOfMeasure);
      diagnostics.recordSuccess("get", address);
      return buildSuccessJson(address, value, unitOfMeasure);
    } catch (IllegalArgumentException e) {
      AutomationDiagnostics.DiagnosticResult diag = diagnoseAndAttemptRecovery(address, e);
      if (diag.hasAutoCorrection()) {
        try {
          double value = getVariableValue(diag.getAutoCorrection(), unitOfMeasure);
          diagnostics.recordFailure("get", address, diag.getCategory(), diag.getAutoCorrection());
          return buildAutoCorrectedJson(address, diag.getAutoCorrection(), value, unitOfMeasure, diag);
        } catch (Exception retryEx) {
          // Auto-correction also failed
        }
      }
      diagnostics.recordFailure("get", address, diag.getCategory(), null);
      return diag.toJson();
    }
  }

  /**
   * Sets a variable value with self-healing: if the exact address fails, attempts auto-correction via fuzzy matching.
   * Also validates the value against physical bounds before setting.
   *
   * @param address the dot-notation address
   * @param value the value to set
   * @param unitOfMeasure the unit of the value
   * @return JSON result string with either success or diagnostic information
   */
  public String setVariableValueSafe(String address, double value, String unitOfMeasure) {
    // Pre-validate physical bounds
    String propertyName = extractPropertyName(address);
    AutomationDiagnostics.DiagnosticResult boundsCheck = diagnostics.validatePhysicalBounds(propertyName, value,
        unitOfMeasure);
    if (boundsCheck != null && boundsCheck.getCategory() == AutomationDiagnostics.ErrorCategory.VALUE_OUT_OF_BOUNDS
        && boundsCheck.getContext().containsKey("severity")
        && !"WARNING".equals(boundsCheck.getContext().get("severity"))) {
      diagnostics.recordFailure("set", address, AutomationDiagnostics.ErrorCategory.VALUE_OUT_OF_BOUNDS, null);
      return boundsCheck.toJson();
    }

    try {
      setVariableValue(address, value, unitOfMeasure);
      diagnostics.recordSuccess("set", address);
      String warningJson = boundsCheck != null ? boundsCheck.toJson() : null;
      return buildSetSuccessJson(address, value, unitOfMeasure, warningJson);
    } catch (IllegalArgumentException e) {
      AutomationDiagnostics.DiagnosticResult diag = diagnoseAndAttemptRecovery(address, e);
      if (diag.hasAutoCorrection()) {
        try {
          setVariableValue(diag.getAutoCorrection(), value, unitOfMeasure);
          diagnostics.recordFailure("set", address, diag.getCategory(), diag.getAutoCorrection());
          return buildAutoCorrectedSetJson(address, diag.getAutoCorrection(), value, unitOfMeasure, diag);
        } catch (Exception retryEx) {
          // Auto-correction also failed
        }
      }
      diagnostics.recordFailure("set", address, diag.getCategory(), null);
      return diag.toJson();
    }
  }

  /**
   * Returns the names of all process areas. Only available when backed by a {@link ProcessModel}.
   *
   * @return unmodifiable list of area names in insertion order
   * @throws IllegalStateException if backed by a single ProcessSystem
   */
  public List<String> getAreaList() {
    if (processModel == null) {
      throw new IllegalStateException("getAreaList() is only available when backed by a ProcessModel");
    }
    return Collections.unmodifiableList(processModel.getProcessSystemNames());
  }

  /**
   * Returns the names of all unit operations. When backed by a {@link ProcessModel}, returns area-qualified names in
   * the format {@code "AreaName::UnitName"}.
   *
   * @return unmodifiable list of unit operation names
   */
  public List<String> getUnitList() {
    if (processModel != null) {
      List<String> names = new ArrayList<String>();
      for (String areaName : processModel.getProcessSystemNames()) {
        ProcessSystem area = processModel.get(areaName);
        for (ProcessEquipmentInterface unit : area.getUnitOperations()) {
          names.add(areaName + AREA_SEPARATOR + unit.getName());
        }
      }
      return Collections.unmodifiableList(names);
    }

    List<ProcessEquipmentInterface> units = processSystem.getUnitOperations();
    List<String> names = new ArrayList<String>(units.size());
    for (ProcessEquipmentInterface unit : units) {
      names.add(unit.getName());
    }
    return Collections.unmodifiableList(names);
  }

  /**
   * Returns the names of unit operations in a specific process area. Only available when backed by a
   * {@link ProcessModel}.
   *
   * @param areaName the name of the process area
   * @return unmodifiable list of unit operation names (without area prefix)
   * @throws IllegalStateException if backed by a single ProcessSystem
   * @throws IllegalArgumentException if the area is not found
   */
  public List<String> getUnitList(String areaName) {
    if (processModel == null) {
      throw new IllegalStateException("getUnitList(areaName) is only available when backed by a ProcessModel");
    }
    ProcessSystem area = processModel.get(areaName);
    if (area == null) {
      throw new IllegalArgumentException("Area not found: " + areaName);
    }
    List<ProcessEquipmentInterface> units = area.getUnitOperations();
    List<String> names = new ArrayList<String>(units.size());
    for (ProcessEquipmentInterface unit : units) {
      names.add(unit.getName());
    }
    return Collections.unmodifiableList(names);
  }

  /**
   * Returns all available variables for the named unit operation.
   *
   * @param unitName the name of the unit operation
   * @return list of variable descriptors
   * @throws IllegalArgumentException if the unit is not found
   */
  public List<SimulationVariable> getVariableList(String unitName) {
    return getVariableList(unitName, null);
  }

  /**
   * Returns variables for the named unit, filtered by type.
   *
   * <p>
   * When backed by a {@link ProcessModel}, the {@code unitName} may be area-qualified: {@code "AreaName::UnitName"}.
   * </p>
   *
   * @param unitName the name of the unit operation, optionally area-qualified
   * @param type the variable type filter, or null for all variables
   * @return list of variable descriptors matching the filter
   * @throws IllegalArgumentException if the unit is not found
   */
  public List<SimulationVariable> getVariableList(String unitName, VariableType type) {
    ResolvedUnit resolved = resolveUnit(unitName);

    List<SimulationVariable> all = buildVariableList(resolved.addressPrefix, resolved.unit);
    if (type == null) {
      return Collections.unmodifiableList(all);
    }

    List<SimulationVariable> filtered = new ArrayList<SimulationVariable>();
    for (SimulationVariable v : all) {
      if (v.getType() == type) {
        filtered.add(v);
      }
    }
    return Collections.unmodifiableList(filtered);
  }

  /**
   * Convenience: returns only the settable {@link VariableType#INPUT INPUT} variables for a unit. These are the
   * addresses that can be driven with {@link #setVariableValue(String, double, String)} (e.g. a cooler set-point is
   * {@code "<Cooler>.outletTemperature"}, an INPUT — not {@code "<Cooler>.outletStream.temperature"}, a computed
   * OUTPUT).
   *
   * @param unitName the unit name, optionally area-qualified
   * @return the INPUT-type variables for the unit
   * @throws IllegalArgumentException if the unit is not found
   */
  public List<SimulationVariable> getInputVariables(String unitName) {
    return getVariableList(unitName, VariableType.INPUT);
  }

  /**
   * Convenience: returns only the read-only {@link VariableType#OUTPUT OUTPUT} variables for a unit. Useful as
   * benchmark / read-back addresses for model-vs-plant comparison.
   *
   * @param unitName the unit name, optionally area-qualified
   * @return the OUTPUT-type variables for the unit
   * @throws IllegalArgumentException if the unit is not found
   */
  public List<SimulationVariable> getOutputVariables(String unitName) {
    return getVariableList(unitName, VariableType.OUTPUT);
  }

  /**
   * Pre-flight check (never throws) for whether an address is a settable {@link VariableType#INPUT INPUT} variable.
   * Writing to a computed OUTPUT address (for example {@code "<Cooler>.outletStream.temperature"}) is silently
   * overwritten by the next {@code run()}; use this to catch that before it happens.
   *
   * @param address the dot-notation address, optionally area-qualified
   * @return true when the address resolves to a known INPUT variable, false otherwise (unknown or OUTPUT)
   */
  public boolean isWritableAddress(String address) {
    if (address == null || address.trim().isEmpty()) {
      return false;
    }
    String unitName = address;
    int areaSepIdx = address.indexOf(AREA_SEPARATOR);
    String prefix = "";
    String local = address;
    if (areaSepIdx >= 0) {
      prefix = address.substring(0, areaSepIdx + AREA_SEPARATOR.length());
      local = address.substring(areaSepIdx + AREA_SEPARATOR.length());
    }
    int dotIdx = local.indexOf('.');
    if (dotIdx < 0) {
      return false;
    }
    unitName = prefix + local.substring(0, dotIdx);
    try {
      for (SimulationVariable v : getVariableList(unitName, VariableType.INPUT)) {
        if (address.equals(v.getAddress())) {
          return true;
        }
      }
    } catch (RuntimeException ex) {
      return false;
    }
    return false;
  }

  // ------------------------- Adjustable parameters -------------------------

  /**
   * Sentinel threshold above which an adjuster bound is treated as unbounded. Adjusters default to +/-1e10, so any
   * magnitude at or beyond 1e9 is reported as {@code null} (no bound).
   */
  private static final double UNBOUNDED_THRESHOLD = 1.0e9;

  /**
   * Returns the registry of adjustable parameters (degrees of freedom) for the underlying process.
   *
   * <p>
   * The registry combines two sources:
   * </p>
   * <ul>
   * <li><strong>Writable INPUT variables</strong> &mdash; every {@link SimulationVariable} of type
   * {@link VariableType#INPUT} that is writable, with its existing bounds and default unit.</li>
   * <li><strong>Adjuster unit operations</strong> &mdash; each {@link neqsim.process.equipment.util.Adjuster} is
   * reported with the unit operation and property it actually drives ({@link AdjustableParameter#getTargetUnitName()}
   * and {@link AdjustableParameter#getTargetProperty()}). This removes the ambiguity that arises when an adjuster's
   * name does not match the variable it controls.</li>
   * </ul>
   *
   * @return an unmodifiable list of adjustable parameter descriptors
   */
  public List<AdjustableParameter> getAdjustableParameters() {
    List<AdjustableParameter> params = new ArrayList<AdjustableParameter>();
    for (String unitName : getUnitList()) {
      ProcessEquipmentInterface unit;
      try {
        unit = resolveUnit(unitName).unit;
      } catch (RuntimeException e) {
        continue;
      }
      if (unit instanceof Adjuster) {
        Adjuster adj = (Adjuster) unit;
        ProcessEquipmentInterface adjusted = adj.getAdjustedEquipment();
        String targetUnitName = adjusted == null ? null : adjusted.getName();
        String adjustedVar = emptyToNull(adj.getAdjustedVariable());
        String unitStr = adj.getAdjustedVariableUnit();
        Double lo = sanitizeBound(adj.getMinAdjustedValue());
        Double hi = sanitizeBound(adj.getMaxAdjustedValue());
        String address;
        if (targetUnitName != null && adjustedVar != null) {
          address = targetUnitName + "." + adjustedVar;
        } else {
          address = unitName;
        }
        params.add(new AdjustableParameter(unitName, address, unitStr, lo, hi, targetUnitName, adjustedVar,
            AdjustableParameter.Source.ADJUSTER));
      } else {
        List<SimulationVariable> inputs;
        try {
          inputs = getVariableList(unitName, VariableType.INPUT);
        } catch (RuntimeException e) {
          continue;
        }
        for (SimulationVariable v : inputs) {
          if (!v.isWritable()) {
            continue;
          }
          params.add(new AdjustableParameter(v.getName(), v.getAddress(), v.getDefaultUnit(), v.getMinimumValue(),
              v.getMaximumValue(), unitName, v.getName(), AdjustableParameter.Source.INPUT_VARIABLE));
        }
      }
    }
    return Collections.unmodifiableList(params);
  }

  /**
   * Returns the registry of adjustable parameters as a JSON string.
   *
   * @return JSON string {@code {schemaVersion, count, parameters:[{name, address, unit, lowerBound, upperBound,
   * targetUnitName, targetProperty, source}]}}
   */
  public String getAdjustableParametersJson() {
    List<AdjustableParameter> params = getAdjustableParameters();
    com.google.gson.JsonObject root = new com.google.gson.JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);
    root.addProperty("count", params.size());
    com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
    for (AdjustableParameter p : params) {
      arr.add(p.toJsonObject());
    }
    root.add("parameters", arr);
    return root.toString();
  }

  /**
   * Creates a new {@link AgenticProcessOptimizer} bound to this automation facade. This is the recommended entry point
   * for closed-loop, ML- and agent-driven optimization over the underlying process: the optimizer drives
   * {@link #evaluate(Map, String, java.util.List)} for every trial, speaks schema-versioned JSON, and never throws.
   *
   * @return a fresh optimizer wrapping this facade
   */
  public AgenticProcessOptimizer newOptimizer() {
    return new AgenticProcessOptimizer(this);
  }

  /**
   * Converts an adjuster bound to a nullable {@link Double}, mapping sentinel "unbounded" values (magnitude at or
   * beyond {@link #UNBOUNDED_THRESHOLD}) and non-finite values to {@code null}.
   *
   * @param value the raw bound value
   * @return the bound, or {@code null} if effectively unbounded
   */
  private static Double sanitizeBound(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value) || Math.abs(value) >= UNBOUNDED_THRESHOLD) {
      return null;
    }
    return Double.valueOf(value);
  }

  /**
   * Returns {@code null} for a null or empty string, otherwise the original string.
   *
   * @param s the string to check
   * @return {@code null} if empty, otherwise {@code s}
   */
  private static String emptyToNull(String s) {
    if (s == null || s.trim().isEmpty()) {
      return null;
    }
    return s;
  }

  /**
   * Returns the simple class name (equipment type) of a unit operation. Useful for discovering what kind of equipment a
   * unit is, e.g. "Compressor", "Separator", "PipeBeggsAndBrills".
   *
   * @param unitName the name of the unit operation, optionally area-qualified
   * @return the simple class name of the equipment
   * @throws IllegalArgumentException if the unit is not found
   */
  public String getEquipmentType(String unitName) {
    ResolvedUnit resolved = resolveUnit(unitName);
    return resolved.unit.getClass().getSimpleName();
  }

  /**
   * Reads the current value of a simulation variable.
   *
   * <p>
   * When backed by a {@link ProcessModel}, the address must be area-qualified: {@code "AreaName::unitName.property"} or
   * {@code "AreaName::unitName.streamPort.property"}.
   * </p>
   *
   * @param address the dot-notation address, e.g. "separator-1.gasOutStream.temperature" or
   * "Separation::separator-1.gasOutStream.temperature"
   * @param unitOfMeasure the desired unit, e.g. "C", "bara", "kg/hr". Pass null or empty for default units
   * @return the variable value in the requested unit
   * @throws IllegalArgumentException if the address cannot be resolved
   */
  public double getVariableValue(String address, String unitOfMeasure) {
    if (address == null || address.trim().isEmpty()) {
      throw new IllegalArgumentException("Address must not be null or empty");
    }

    // Separate area prefix if present
    String localAddress = address;
    String areaName = null;
    int areaSepIdx = address.indexOf(AREA_SEPARATOR);
    if (areaSepIdx >= 0) {
      areaName = address.substring(0, areaSepIdx);
      localAddress = address.substring(areaSepIdx + AREA_SEPARATOR.length());
    }

    String[] parts = localAddress.split("\\.", 3);
    String unitName = parts[0];

    ProcessEquipmentInterface unit = findUnit(areaName, unitName);

    if (parts.length == 2) {
      return getEquipmentProperty(unit, parts[1], unitOfMeasure);
    } else if (parts.length == 3) {
      StreamInterface stream = resolveStreamPort(unit, parts[1]);
      if (stream == null) {
        throw new IllegalArgumentException("Stream port not found: " + parts[1] + " on unit " + unitName);
      }
      return getStreamProperty(stream, parts[2], unitOfMeasure);
    } else {
      throw new IllegalArgumentException(
          "Invalid address format: " + address + ". Expected 'unitName.property' or 'unitName.port.property'");
    }
  }

  /**
   * Sets the value of a simulation input variable. Only variables with {@link VariableType#INPUT INPUT} type can be
   * set.
   *
   * <p>
   * When backed by a {@link ProcessModel}, the address must be area-qualified:
   * {@code "AreaName::Compressor.outletPressure"}.
   * </p>
   *
   * @param address the dot-notation address, e.g. "Compressor.outletPressure" or
   * "Compression::Compressor.outletPressure"
   * @param value the value to set
   * @param unitOfMeasure the unit of the provided value, e.g. "bara", "C". Pass null or empty for default units
   * @throws IllegalArgumentException if the address cannot be resolved or the variable is read-only
   */
  public void setVariableValue(String address, double value, String unitOfMeasure) {
    if (address == null || address.trim().isEmpty()) {
      throw new IllegalArgumentException("Address must not be null or empty");
    }

    // Separate area prefix if present
    String localAddress = address;
    String areaName = null;
    int areaSepIdx = address.indexOf(AREA_SEPARATOR);
    if (areaSepIdx >= 0) {
      areaName = address.substring(0, areaSepIdx);
      localAddress = address.substring(areaSepIdx + AREA_SEPARATOR.length());
    }

    String[] parts = localAddress.split("\\.", 3);
    String unitName = parts[0];

    ProcessEquipmentInterface unit = findUnit(areaName, unitName);

    if (parts.length == 2) {
      setEquipmentProperty(unit, parts[1], value, unitOfMeasure);
    } else if (parts.length == 3) {
      StreamInterface stream = resolveStreamPort(unit, parts[1]);
      if (stream == null) {
        throw new IllegalArgumentException("Stream port not found: " + parts[1] + " on unit " + unitName);
      }
      setStreamProperty(stream, parts[2], value, unitOfMeasure);
    } else {
      throw new IllegalArgumentException(
          "Invalid address format: " + address + ". Expected 'unitName.property' or 'unitName.port.property'");
    }
    this.dirty = true;
  }

  // ========================== Private helpers ==========================

  /**
   * Holds a resolved unit and its address prefix for variable naming.
   */
  private static class ResolvedUnit {
    final ProcessEquipmentInterface unit;
    final String addressPrefix;

    ResolvedUnit(ProcessEquipmentInterface unit, String addressPrefix) {
      this.unit = unit;
      this.addressPrefix = addressPrefix;
    }
  }

  /**
   * Resolves a unit name (optionally area-qualified) to the equipment and its address prefix.
   *
   * @param unitName the unit name, optionally with area prefix "AreaName::UnitName"
   * @return the resolved unit and address prefix
   * @throws IllegalArgumentException if the unit is not found
   */
  private ResolvedUnit resolveUnit(String unitName) {
    int areaSepIdx = unitName.indexOf(AREA_SEPARATOR);
    if (areaSepIdx >= 0) {
      String areaName = unitName.substring(0, areaSepIdx);
      String localName = unitName.substring(areaSepIdx + AREA_SEPARATOR.length());
      ProcessEquipmentInterface unit = findUnit(areaName, localName);
      return new ResolvedUnit(unit, areaName + AREA_SEPARATOR + localName);
    }

    ProcessEquipmentInterface unit = findUnit(null, unitName);
    return new ResolvedUnit(unit, unitName);
  }

  /**
   * Finds a unit by name, optionally scoped to a specific area.
   *
   * @param areaName the area name, or null to search the single ProcessSystem (or all areas)
   * @param unitName the unit name
   * @return the equipment
   * @throws IllegalArgumentException if the unit is not found
   */
  private ProcessEquipmentInterface findUnit(String areaName, String unitName) {
    // Check if the address is an IEC 81346 reference designation (starts with = or -)
    if (unitName != null && (unitName.startsWith("=") || unitName.startsWith("-"))) {
      ProcessEquipmentInterface found = findByReferenceDesignation(areaName, unitName);
      if (found != null) {
        return found;
      }
    }

    if (processModel != null) {
      if (areaName != null) {
        ProcessSystem area = processModel.get(areaName);
        if (area == null) {
          // Try fuzzy area matching
          List<String> areaNames = processModel.getProcessSystemNames();
          String corrected = diagnostics.autoCorrectName(areaName, areaNames);
          if (corrected != null) {
            area = processModel.get(corrected);
          }
          if (area == null) {
            List<String> suggestions = diagnostics.findClosestNames(areaName, areaNames, 3);
            throw new IllegalArgumentException(
                "Area not found: " + areaName + (suggestions.isEmpty() ? "" : ". Did you mean: " + suggestions + "?"));
          }
        }
        ProcessEquipmentInterface unit = area.getUnit(unitName);
        if (unit == null) {
          // Try fuzzy unit matching within the area
          List<String> unitNames = getPlainUnitNames(area);
          String corrected = diagnostics.autoCorrectName(unitName, unitNames);
          if (corrected != null) {
            unit = area.getUnit(corrected);
          }
          if (unit == null) {
            List<String> suggestions = diagnostics.findClosestNames(unitName, unitNames, 3);
            throw new IllegalArgumentException("Unit not found: " + unitName + " in area " + areaName
                + (suggestions.isEmpty() ? "" : ". Did you mean: " + suggestions + "?"));
          }
        }
        return unit;
      }
      // Search all areas
      for (String name : processModel.getProcessSystemNames()) {
        ProcessSystem area = processModel.get(name);
        ProcessEquipmentInterface unit = area.getUnit(unitName);
        if (unit != null) {
          return unit;
        }
      }
      // Fuzzy search across all areas
      List<String> allNames = new ArrayList<String>();
      for (String name : processModel.getProcessSystemNames()) {
        allNames.addAll(getPlainUnitNames(processModel.get(name)));
      }
      String corrected = diagnostics.autoCorrectName(unitName, allNames);
      if (corrected != null) {
        for (String name : processModel.getProcessSystemNames()) {
          ProcessEquipmentInterface u = processModel.get(name).getUnit(corrected);
          if (u != null) {
            return u;
          }
        }
      }
      List<String> suggestions = diagnostics.findClosestNames(unitName, allNames, 3);
      throw new IllegalArgumentException("Unit not found in any area: " + unitName
          + (suggestions.isEmpty() ? "" : ". Did you mean: " + suggestions + "?"));
    }

    // Single ProcessSystem mode
    ProcessEquipmentInterface unit = processSystem.getUnit(unitName);
    if (unit == null) {
      // Try fuzzy matching
      List<String> unitNames = getPlainUnitNames(processSystem);
      String corrected = diagnostics.autoCorrectName(unitName, unitNames);
      if (corrected != null) {
        unit = processSystem.getUnit(corrected);
      }
      if (unit == null) {
        List<String> suggestions = diagnostics.findClosestNames(unitName, unitNames, 3);
        throw new IllegalArgumentException(
            "Unit not found: " + unitName + (suggestions.isEmpty() ? "" : ". Did you mean: " + suggestions + "?"));
      }
    }
    return unit;
  }

  /**
   * Gets plain (non-area-qualified) unit names from a ProcessSystem.
   *
   * @param ps the process system
   * @return list of unit names
   */
  private List<String> getPlainUnitNames(ProcessSystem ps) {
    List<ProcessEquipmentInterface> units = ps.getUnitOperations();
    List<String> names = new ArrayList<String>(units.size());
    for (ProcessEquipmentInterface u : units) {
      names.add(u.getName());
    }
    return names;
  }

  /**
   * Finds a unit by its IEC 81346 reference designation string.
   *
   * <p>
   * Searches all equipment in the relevant process system(s) for a matching reference designation. This enables
   * addressing equipment by their IEC 81346 codes, e.g. "=A1-B1" or "-K2".
   * </p>
   *
   * @param areaName the area name to search within (null to search all)
   * @param refDesString the reference designation string to match
   * @return the matching equipment, or null if not found
   */
  private ProcessEquipmentInterface findByReferenceDesignation(String areaName, String refDesString) {
    if (processModel != null) {
      if (areaName != null) {
        ProcessSystem area = processModel.get(areaName);
        if (area != null) {
          return searchByRefDes(area, refDesString);
        }
      }
      for (String name : processModel.getProcessSystemNames()) {
        ProcessEquipmentInterface found = searchByRefDes(processModel.get(name), refDesString);
        if (found != null) {
          return found;
        }
      }
      return null;
    }
    return searchByRefDes(processSystem, refDesString);
  }

  /**
   * Searches a process system for equipment matching a reference designation string.
   *
   * @param ps the process system to search
   * @param refDesString the reference designation to match
   * @return the matching equipment, or null if not found
   */
  private ProcessEquipmentInterface searchByRefDes(ProcessSystem ps, String refDesString) {
    for (ProcessEquipmentInterface unit : ps.getUnitOperations()) {
      String unitRefDes = unit.getReferenceDesignationString();
      if (unitRefDes != null && !unitRefDes.isEmpty() && unitRefDes.equals(refDesString)) {
        return unit;
      }
    }
    return null;
  }

  /**
   * Builds the list of variables exposed by a unit operation.
   *
   * @param unitName the unit name (used as address prefix)
   * @param unit the unit operation
   * @return list of variables
   */
  private List<SimulationVariable> buildVariableList(String unitName, ProcessEquipmentInterface unit) {
    List<SimulationVariable> vars = new ArrayList<SimulationVariable>();
    boolean handledOutlets = false;

    // Universal equipment-level outputs
    vars.add(new SimulationVariable(unitName + ".temperature", "temperature", VariableType.OUTPUT, "K",
        "Equipment temperature"));
    vars.add(
        new SimulationVariable(unitName + ".pressure", "pressure", VariableType.OUTPUT, "bara", "Equipment pressure"));

    // Stream-specific variables
    if (unit instanceof StreamInterface) {
      addStreamVariables(vars, unitName, (StreamInterface) unit, true);
      handledOutlets = true;
    }

    // Separator family (ThreePhaseSeparator before Separator since it extends Separator)
    if (unit instanceof ThreePhaseSeparator) {
      addStreamOutputVariables(vars, unitName + ".gasOutStream", ((ThreePhaseSeparator) unit).getGasOutStream());
      addStreamOutputVariables(vars, unitName + ".oilOutStream", ((ThreePhaseSeparator) unit).getOilOutStream());
      addStreamOutputVariables(vars, unitName + ".waterOutStream", ((ThreePhaseSeparator) unit).getWaterOutStream());
      handledOutlets = true;
    } else if (unit instanceof Separator) {
      addStreamOutputVariables(vars, unitName + ".gasOutStream", ((Separator) unit).getGasOutStream());
      addStreamOutputVariables(vars, unitName + ".liquidOutStream", ((Separator) unit).getLiquidOutStream());
      handledOutlets = true;
    }

    // Tank (gas/liquid outlets like separator)
    if (unit instanceof Tank) {
      vars.add(new SimulationVariable(unitName + ".liquidLevel", "liquidLevel", VariableType.OUTPUT, "",
          "Tank liquid level"));
      vars.add(new SimulationVariable(unitName + ".volume", "volume", VariableType.INPUT, "m3", "Tank volume"));
      try {
        addStreamOutputVariables(vars, unitName + ".gasOutStream",
            (StreamInterface) unit.getClass().getMethod("getGasOutStream").invoke(unit));
        addStreamOutputVariables(vars, unitName + ".liquidOutStream",
            (StreamInterface) unit.getClass().getMethod("getLiquidOutStream").invoke(unit));
        handledOutlets = true;
      } catch (Exception e) {
        // Tank may not have gas/liquid split
      }
    }

    // Expander (extends Compressor, check before Compressor)
    if (unit instanceof Expander) {
      vars.add(new SimulationVariable(unitName + ".outletPressure", "outletPressure", VariableType.INPUT, "bara",
          "Expander outlet pressure"));
      vars.add(new SimulationVariable(unitName + ".isentropicEfficiency", "isentropicEfficiency", VariableType.INPUT,
          "", "Isentropic efficiency (fraction)"));
      vars.add(new SimulationVariable(unitName + ".polytropicEfficiency", "polytropicEfficiency", VariableType.INPUT,
          "", "Polytropic efficiency (fraction)"));
      vars.add(
          new SimulationVariable(unitName + ".power", "power", VariableType.OUTPUT, "kW", "Expander power output"));
      addOutletStreamVariables(vars, unitName, unit);
      handledOutlets = true;
    }

    // CompressorTrain (check before Compressor since it doesn't extend Compressor)
    if (unit instanceof CompressorTrain) {
      vars.add(new SimulationVariable(unitName + ".power", "power", VariableType.OUTPUT, "kW",
          "Compressor train total power"));
      vars.add(new SimulationVariable(unitName + ".polytropicEfficiency", "polytropicEfficiency", VariableType.OUTPUT,
          "", "Overall polytropic efficiency"));
      addOutletStreamVariables(vars, unitName, unit);
      handledOutlets = true;
    }

    // Compressor (not Expander)
    if (unit instanceof Compressor && !(unit instanceof Expander)) {
      vars.add(new SimulationVariable(unitName + ".outletPressure", "outletPressure", VariableType.INPUT, "bara",
          "Compressor outlet pressure"));
      vars.add(new SimulationVariable(unitName + ".polytropicEfficiency", "polytropicEfficiency", VariableType.INPUT,
          "", "Polytropic efficiency (fraction)"));
      vars.add(new SimulationVariable(unitName + ".isentropicEfficiency", "isentropicEfficiency", VariableType.OUTPUT,
          "", "Isentropic efficiency (fraction)"));
      vars.add(new SimulationVariable(unitName + ".power", "power", VariableType.OUTPUT, "kW",
          "Compressor power consumption"));
      vars.add(new SimulationVariable(unitName + ".speed", "speed", VariableType.INPUT, "rpm", "Compressor speed"));
      vars.add(new SimulationVariable(unitName + ".polytropicHead", "polytropicHead", VariableType.OUTPUT, "kJ/kg",
          "Polytropic head"));
      vars.add(new SimulationVariable(unitName + ".compressionRatio", "compressionRatio", VariableType.OUTPUT, "",
          "Compression ratio"));
      addOutletStreamVariables(vars, unitName, unit);
      handledOutlets = true;
    }

    // Pump
    if (unit instanceof Pump) {
      vars.add(new SimulationVariable(unitName + ".outletPressure", "outletPressure", VariableType.INPUT, "bara",
          "Pump outlet pressure"));
      vars.add(
          new SimulationVariable(unitName + ".power", "power", VariableType.OUTPUT, "kW", "Pump power consumption"));
      vars.add(new SimulationVariable(unitName + ".isentropicEfficiency", "isentropicEfficiency", VariableType.INPUT,
          "", "Isentropic efficiency (fraction)"));
      vars.add(new SimulationVariable(unitName + ".speed", "speed", VariableType.INPUT, "rpm", "Pump speed"));
      addOutletStreamVariables(vars, unitName, unit);
      handledOutlets = true;
    }

    // Heat exchanger (HeatExchanger extends Heater, so check BEFORE Heater)
    if (unit instanceof HeatExchanger && !(unit instanceof Cooler)) {
      vars.add(new SimulationVariable(unitName + ".UAvalue", "UAvalue", VariableType.INPUT, "W/K",
          "Overall heat transfer coefficient times area"));
      vars.add(new SimulationVariable(unitName + ".duty", "duty", VariableType.OUTPUT, "W", "Heat exchanger duty"));
      vars.add(new SimulationVariable(unitName + ".thermalEffectiveness", "thermalEffectiveness", VariableType.OUTPUT,
          "", "Thermal effectiveness"));
      handledOutlets = true;
    }

    // Cooler
    if (unit instanceof Cooler) {
      vars.add(new SimulationVariable(unitName + ".outletTemperature", "outletTemperature", VariableType.INPUT, "C",
          "Cooler outlet temperature"));
      vars.add(new SimulationVariable(unitName + ".duty", "duty", VariableType.OUTPUT, "W", "Cooler duty"));
      addOutletStreamVariables(vars, unitName, unit);
      handledOutlets = true;
    }

    // Heater (not Cooler and not HeatExchanger)
    if (unit instanceof Heater && !(unit instanceof Cooler) && !(unit instanceof HeatExchanger)) {
      vars.add(new SimulationVariable(unitName + ".outletTemperature", "outletTemperature", VariableType.INPUT, "C",
          "Heater outlet temperature"));
      vars.add(new SimulationVariable(unitName + ".duty", "duty", VariableType.OUTPUT, "W", "Heater duty"));
      addOutletStreamVariables(vars, unitName, unit);
      handledOutlets = true;
    }

    // Valve
    if (unit instanceof ThrottlingValve) {
      vars.add(new SimulationVariable(unitName + ".outletPressure", "outletPressure", VariableType.INPUT, "bara",
          "Valve outlet pressure"));
      vars.add(new SimulationVariable(unitName + ".Cv", "Cv", VariableType.INPUT, "", "Valve flow coefficient"));
      vars.add(new SimulationVariable(unitName + ".percentValveOpening", "percentValveOpening", VariableType.INPUT, "%",
          "Valve opening percentage"));
      addOutletStreamVariables(vars, unitName, unit);
      handledOutlets = true;
    }

    // Pipeline (AdiabaticPipe, PipeBeggsAndBrills, etc.)
    if (unit instanceof Pipeline) {
      vars.add(new SimulationVariable(unitName + ".length", "length", VariableType.INPUT, "m", "Pipe length"));
      vars.add(
          new SimulationVariable(unitName + ".diameter", "diameter", VariableType.INPUT, "m", "Pipe inner diameter"));
      vars.add(new SimulationVariable(unitName + ".pipeWallRoughness", "pipeWallRoughness", VariableType.INPUT, "m",
          "Pipe wall roughness"));
      vars.add(new SimulationVariable(unitName + ".wallThickness", "wallThickness", VariableType.INPUT, "m",
          "Pipe wall thickness"));
      vars.add(new SimulationVariable(unitName + ".elevation", "elevation", VariableType.INPUT, "m",
          "Pipe elevation change from inlet to outlet"));
      vars.add(new SimulationVariable(unitName + ".pressureDrop", "pressureDrop", VariableType.OUTPUT, "bara",
          "Pressure drop across pipe"));
      if (unit instanceof WaterHammerPipe) {
        vars.add(new SimulationVariable(unitName + ".valveOpening", "valveOpening", VariableType.INPUT, "",
            "Water-hammer valve opening fraction"));
        vars.add(new SimulationVariable(unitName + ".valveOpeningPercent", "valveOpeningPercent", VariableType.INPUT,
            "%", "Water-hammer valve opening percentage"));
        vars.add(new SimulationVariable(unitName + ".waveSpeed", "waveSpeed", VariableType.INPUT, "m/s",
            "Acoustic wave speed override or calculated value"));
        vars.add(new SimulationVariable(unitName + ".numberOfNodes", "numberOfNodes", VariableType.INPUT, "",
            "Water-hammer computational node count"));
        vars.add(new SimulationVariable(unitName + ".courantNumber", "courantNumber", VariableType.INPUT, "",
            "Courant number for stable transient time steps"));
        vars.add(new SimulationVariable(unitName + ".maxStableTimeStep", "maxStableTimeStep", VariableType.OUTPUT, "s",
            "Maximum stable time step from the Courant limit"));
        vars.add(new SimulationVariable(unitName + ".waveRoundTripTime", "waveRoundTripTime", VariableType.OUTPUT, "s",
            "Pipe acoustic wave round-trip time"));
        vars.add(new SimulationVariable(unitName + ".maxPressure", "maxPressure", VariableType.OUTPUT, "bara",
            "Maximum pressure envelope during transient"));
        vars.add(new SimulationVariable(unitName + ".minPressure", "minPressure", VariableType.OUTPUT, "bara",
            "Minimum pressure envelope during transient"));
      }
      addOutletStreamVariables(vars, unitName, unit);
      handledOutlets = true;
    }

    // Ejector
    if (unit instanceof Ejector) {
      vars.add(new SimulationVariable(unitName + ".dischargePressure", "dischargePressure", VariableType.INPUT, "bara",
          "Ejector discharge pressure"));
      vars.add(new SimulationVariable(unitName + ".entrainmentRatio", "entrainmentRatio", VariableType.OUTPUT, "",
          "Ejector entrainment ratio"));
      vars.add(new SimulationVariable(unitName + ".efficiencyIsentropic", "efficiencyIsentropic", VariableType.INPUT,
          "", "Isentropic efficiency"));
      handledOutlets = true;
    }

    // Gibbs reactor (and other TwoPortEquipment reactors)
    if (unit instanceof GibbsReactor) {
      vars.add(new SimulationVariable(unitName + ".power", "power", VariableType.OUTPUT, "kW",
          "Reactor power (heat of reaction)"));
      addOutletStreamVariables(vars, unitName, unit);
      handledOutlets = true;
    }

    // Distillation column
    if (unit instanceof DistillationColumn) {
      vars.add(new SimulationVariable(unitName + ".condenserRefluxRatio", "condenserRefluxRatio", VariableType.INPUT,
          "", "Condenser reflux ratio"));
      handledOutlets = true;
    }

    // Recycle
    if (unit instanceof Recycle) {
      vars.add(new SimulationVariable(unitName + ".errorTemperature", "errorTemperature", VariableType.OUTPUT, "",
          "Temperature convergence error"));
      vars.add(new SimulationVariable(unitName + ".errorFlow", "errorFlow", VariableType.OUTPUT, "",
          "Flow rate convergence error"));
      addOutletStreamVariables(vars, unitName, unit);
      handledOutlets = true;
    }

    // Component splitter
    if (unit instanceof ComponentSplitter) {
      addOutletStreamVariables(vars, unitName, unit);
      handledOutlets = true;
    }

    // Mixer — output only
    if (unit instanceof Mixer) {
      addOutletStreamVariables(vars, unitName, unit);
      handledOutlets = true;
    }

    // Splitter — routing split factors are settable INPUTs (bounded 0-1); outlet streams
    // are outputs. The split factors are the routing decision variables an optimizer can
    // adjust to redistribute flow between branches.
    if (unit instanceof Splitter && !(unit instanceof ComponentSplitter)) {
      List<StreamInterface> splitStreams = unit.getOutletStreams();
      for (int i = 0; i < splitStreams.size(); i++) {
        vars.add(new SimulationVariable(unitName + ".splitFactor_" + i, "splitFactor_" + i, VariableType.INPUT, "",
            "Fraction of feed routed to outlet " + i + " (0-1, renormalised across outlets)")
            .withBounds(Double.valueOf(0.0), Double.valueOf(1.0)));
        addStreamOutputVariables(vars, unitName + ".splitStream_" + i, splitStreams.get(i));
      }
      handledOutlets = true;
    }

    // Generic fallback for any TwoPortEquipment not yet handled
    if (!handledOutlets && unit instanceof TwoPortEquipment) {
      addOutletStreamVariables(vars, unitName, unit);
    }

    return enrichVariableMetadata(vars);
  }

  /**
   * Enriches sparse variable descriptors with metadata useful to agents.
   *
   * @param variables raw variables discovered from equipment
   * @return enriched variable descriptors
   */
  private List<SimulationVariable> enrichVariableMetadata(List<SimulationVariable> variables) {
    List<SimulationVariable> enrichedVariables = new ArrayList<SimulationVariable>();
    for (SimulationVariable variable : variables) {
      enrichedVariables.add(enrichVariableMetadata(variable));
    }
    return enrichedVariables;
  }

  /**
   * Enriches a single variable descriptor.
   *
   * @param variable raw variable descriptor
   * @return enriched variable descriptor
   */
  private SimulationVariable enrichVariableMetadata(SimulationVariable variable) {
    SimulationVariable enriched = variable.withCategory(inferVariableCategory(variable))
        .withWritableSafety(variable.getType() == VariableType.INPUT, variable.getType() == VariableType.INPUT)
        .withApplicability(inferApplicability(variable));
    String name = variable.getName();

    if ("temperature".equals(name) || "outletTemperature".equals(name)) {
      return enriched.withBounds(Double.valueOf(1.0), Double.valueOf(2000.0)).withUnitFamily("temperature");
    }
    if ("pressure".equals(name) || "outletPressure".equals(name) || "dischargePressure".equals(name)) {
      return enriched.withBounds(Double.valueOf(1.0e-6), Double.valueOf(10000.0)).withUnitFamily("pressure");
    }
    if ("flowRate".equals(name)) {
      return enriched.withBounds(Double.valueOf(0.0), null).withUnitFamily("flow");
    }
    if (name.toLowerCase().contains("efficiency")) {
      return enriched.withBounds(Double.valueOf(0.0), Double.valueOf(1.0)).withUnitFamily("fraction");
    }
    if ("percentValveOpening".equals(name)) {
      return enriched.withBounds(Double.valueOf(0.0), Double.valueOf(100.0)).withUnitFamily("fraction");
    }
    if ("Cv".equals(name) || "UAvalue".equals(name) || "speed".equals(name) || "length".equals(name)
        || "diameter".equals(name) || "pipeWallRoughness".equals(name) || "volume".equals(name)
        || "condenserRefluxRatio".equals(name)) {
      return enriched.withBounds(Double.valueOf(0.0), null);
    }
    return enriched;
  }

  /**
   * Infers a variable category from its address and property name.
   *
   * @param variable variable descriptor
   * @return category string
   */
  private String inferVariableCategory(SimulationVariable variable) {
    String address = variable.getAddress();
    String name = variable.getName();
    if (address.contains("Stream")) {
      return "stream";
    }
    if (name.toLowerCase().contains("efficiency") || "power".equals(name) || "duty".equals(name)) {
      return "performance";
    }
    if ("length".equals(name) || "diameter".equals(name) || "volume".equals(name) || "pipeWallRoughness".equals(name)) {
      return "geometry";
    }
    return variable.getType() == VariableType.INPUT ? "equipment_input" : "equipment_output";
  }

  /**
   * Infers an applicability note for a variable.
   *
   * @param variable variable descriptor
   * @return applicability note
   */
  private String inferApplicability(SimulationVariable variable) {
    if (variable.getAddress().contains("gasOutStream")) {
      return "Available for equipment with a gas outlet stream";
    }
    if (variable.getAddress().contains("liquidOutStream")) {
      return "Available for equipment with a liquid outlet stream";
    }
    if (variable.getAddress().contains("outletStream")) {
      return "Available for equipment with a single outlet stream";
    }
    if (variable.getType() == VariableType.INPUT) {
      return "Writable input; rerun the process after changing this value";
    }
    return "Read-only output from the latest process run";
  }

  /**
   * Adds stream variables (temperature, pressure, flowRate) with appropriate INPUT/OUTPUT type.
   *
   * @param vars the list to add to
   * @param prefix the address prefix
   * @param stream the stream
   * @param isInput whether the stream properties are settable
   */
  private void addStreamVariables(List<SimulationVariable> vars, String prefix, StreamInterface stream,
      boolean isInput) {
    VariableType inputType = isInput ? VariableType.INPUT : VariableType.OUTPUT;
    vars.add(new SimulationVariable(prefix + ".temperature", "temperature", inputType, "K", "Stream temperature"));
    vars.add(new SimulationVariable(prefix + ".pressure", "pressure", inputType, "bara", "Stream pressure"));
    vars.add(new SimulationVariable(prefix + ".flowRate", "flowRate", inputType, "kg/hr", "Stream mass flow rate"));
    vars.add(new SimulationVariable(prefix + ".density", "density", VariableType.OUTPUT, "kg/m3", "Stream density"));
    vars.add(
        new SimulationVariable(prefix + ".molarMass", "molarMass", VariableType.OUTPUT, "kg/mol", "Stream molar mass"));
  }

  /**
   * Adds read-only stream variables for an output stream.
   *
   * @param vars the list to add to
   * @param prefix the address prefix including port name
   * @param stream the stream
   */
  private void addStreamOutputVariables(List<SimulationVariable> vars, String prefix, StreamInterface stream) {
    if (stream == null) {
      return;
    }
    addStreamVariables(vars, prefix, stream, false);
  }

  /**
   * Adds outlet stream variables for single-outlet equipment.
   *
   * @param vars the list to add to
   * @param unitName the unit name
   * @param unit the equipment
   */
  private void addOutletStreamVariables(List<SimulationVariable> vars, String unitName,
      ProcessEquipmentInterface unit) {
    List<StreamInterface> outlets = unit.getOutletStreams();
    if (!outlets.isEmpty()) {
      addStreamOutputVariables(vars, unitName + ".outletStream", outlets.get(0));
    }
  }

  /**
   * Gets a property value directly from an equipment object.
   *
   * @param unit the equipment
   * @param property the property name
   * @param uom the desired unit of measure
   * @return the property value
   */
  private double getEquipmentProperty(ProcessEquipmentInterface unit, String property, String uom) {
    boolean hasUnit = uom != null && !uom.trim().isEmpty();

    if (property.startsWith("splitFactor_") && unit instanceof Splitter) {
      int idx = parseIndexSuffix(property, "splitFactor_");
      double[] sf = ((Splitter) unit).getSplitFactors();
      if (idx >= 0 && idx < sf.length) {
        return sf[idx];
      }
      throw new IllegalArgumentException("Split factor index out of range for " + unit.getName() + ": " + property);
    }

    switch (property) {
    case "temperature":
      if (unit.getFluid() == null) {
        return Double.NaN;
      }
      return hasUnit ? unit.getTemperature(uom) : unit.getTemperature();
    case "pressure":
      if (unit.getFluid() == null) {
        return Double.NaN;
      }
      return hasUnit ? unit.getPressure(uom) : unit.getPressure();
    case "outletPressure":
      if (unit instanceof Compressor) {
        return ((Compressor) unit).getOutletPressure();
      }
      if (unit instanceof Pump) {
        return ((Pump) unit).getOutletPressure();
      }
      if (unit instanceof ThrottlingValve) {
        return ((ThrottlingValve) unit).getOutletPressure();
      }
      break;
    case "power":
      if (unit instanceof Compressor) {
        return hasUnit ? ((Compressor) unit).getPower(uom) : ((Compressor) unit).getPower();
      }
      if (unit instanceof Pump) {
        return hasUnit ? ((Pump) unit).getPower(uom) : ((Pump) unit).getPower();
      }
      if (unit instanceof CompressorTrain) {
        return hasUnit ? ((CompressorTrain) unit).getPower(uom) : ((CompressorTrain) unit).getPower();
      }
      if (unit instanceof GibbsReactor) {
        return hasUnit ? ((GibbsReactor) unit).getPower(uom) : ((GibbsReactor) unit).getPower();
      }
      break;
    case "duty":
      // Heater is a superclass of HeatExchanger, so check HeatExchanger first
      if (unit instanceof HeatExchanger) {
        return ((HeatExchanger) unit).getDuty();
      }
      if (unit instanceof Heater) {
        return hasUnit ? ((Heater) unit).getDuty(uom) : ((Heater) unit).getDuty();
      }
      break;
    case "polytropicEfficiency":
      if (unit instanceof Compressor) {
        return ((Compressor) unit).getPolytropicEfficiency();
      }
      if (unit instanceof CompressorTrain) {
        return ((CompressorTrain) unit).getPolytropicEfficiency();
      }
      break;
    case "isentropicEfficiency":
      if (unit instanceof Compressor) {
        return ((Compressor) unit).getIsentropicEfficiency();
      }
      if (unit instanceof Pump) {
        return ((Pump) unit).getIsentropicEfficiency();
      }
      break;
    case "Cv":
      if (unit instanceof ThrottlingValve) {
        return hasUnit ? ((ThrottlingValve) unit).getCv(uom) : ((ThrottlingValve) unit).getCv();
      }
      break;
    case "percentValveOpening":
      if (unit instanceof ThrottlingValve) {
        return ((ThrottlingValve) unit).getPercentValveOpening();
      }
      if (unit instanceof WaterHammerPipe) {
        return ((WaterHammerPipe) unit).getValveOpeningPercent();
      }
      break;
    case "valveOpening":
      if (unit instanceof WaterHammerPipe) {
        return ((WaterHammerPipe) unit).getValveOpening();
      }
      break;
    case "valveOpeningPercent":
      if (unit instanceof WaterHammerPipe) {
        return ((WaterHammerPipe) unit).getValveOpeningPercent();
      }
      break;
    case "UAvalue":
      if (unit instanceof HeatExchanger) {
        return ((HeatExchanger) unit).getUAvalue();
      }
      break;
    case "thermalEffectiveness":
      if (unit instanceof HeatExchanger) {
        return ((HeatExchanger) unit).getThermalEffectiveness();
      }
      break;
    case "outletTemperature":
      return hasUnit ? unit.getOutletTemperature(uom) : unit.getOutletTemperature("K");
    case "flowRate":
      if (unit instanceof StreamInterface) {
        return hasUnit ? ((StreamInterface) unit).getFlowRate(uom) : ((StreamInterface) unit).getFlowRate("kg/hr");
      }
      break;
    case "density":
      if (unit instanceof StreamInterface) {
        StreamInterface s = (StreamInterface) unit;
        return s.getFluid() != null ? s.getFluid().getDensity("kg/m3") : Double.NaN;
      }
      break;
    case "molarMass":
      if (unit instanceof StreamInterface) {
        StreamInterface s = (StreamInterface) unit;
        return s.getFluid() != null ? s.getFluid().getMolarMass("kg/mol") : Double.NaN;
      }
      break;
    case "speed":
      if (unit instanceof Compressor) {
        return ((Compressor) unit).getSpeed();
      }
      if (unit instanceof Pump) {
        return ((Pump) unit).getSpeed();
      }
      break;
    case "polytropicHead":
      if (unit instanceof Compressor) {
        return hasUnit ? ((Compressor) unit).getPolytropicHead(uom) : ((Compressor) unit).getPolytropicHead();
      }
      break;
    case "compressionRatio":
      if (unit instanceof Compressor) {
        return ((Compressor) unit).getCompressionRatio();
      }
      break;

    case "condenserRefluxRatio":
      if (unit instanceof DistillationColumn) {
        return ((DistillationColumn) unit).getCondenser().getRefluxRatio();
      }
      break;
    case "length":
      if (unit instanceof Pipeline) {
        return ((Pipeline) unit).getLength();
      }
      break;
    case "diameter":
      if (unit instanceof Pipeline) {
        return ((Pipeline) unit).getDiameter();
      }
      break;
    case "pipeWallRoughness":
      if (unit instanceof Pipeline) {
        return ((Pipeline) unit).getPipeWallRoughness();
      }
      break;
    case "wallThickness":
      if (unit instanceof Pipeline) {
        return ((Pipeline) unit).getWallThickness();
      }
      break;
    case "elevation":
      if (unit instanceof Pipeline) {
        return ((Pipeline) unit).getElevation();
      }
      break;
    case "pressureDrop":
      if (unit instanceof Pipeline) {
        return ((Pipeline) unit).getPressureDrop();
      }
      break;
    case "numberOfNodes":
      if (unit instanceof WaterHammerPipe) {
        return ((WaterHammerPipe) unit).getNumberOfNodes();
      }
      break;
    case "courantNumber":
      if (unit instanceof WaterHammerPipe) {
        return ((WaterHammerPipe) unit).getCourantNumber();
      }
      break;
    case "waveSpeed":
      if (unit instanceof WaterHammerPipe) {
        return ((WaterHammerPipe) unit).getWaveSpeed();
      }
      break;
    case "maxStableTimeStep":
      if (unit instanceof WaterHammerPipe) {
        return ((WaterHammerPipe) unit).getMaxStableTimeStep();
      }
      break;
    case "waveRoundTripTime":
      if (unit instanceof WaterHammerPipe) {
        return ((WaterHammerPipe) unit).getWaveRoundTripTime();
      }
      break;
    case "maxPressure":
      if (unit instanceof WaterHammerPipe) {
        return ((WaterHammerPipe) unit).getMaxPressure(hasUnit ? uom : "bar");
      }
      break;
    case "minPressure":
      if (unit instanceof WaterHammerPipe) {
        return ((WaterHammerPipe) unit).getMinPressure(hasUnit ? uom : "bar");
      }
      break;
    case "dischargePressure":
      if (unit instanceof Ejector) {
        return ((Ejector) unit).getOutStream().getPressure();
      }
      break;
    case "entrainmentRatio":
      if (unit instanceof Ejector) {
        return ((Ejector) unit).getEntrainmentRatio();
      }
      break;
    case "efficiencyIsentropic":
      if (unit instanceof Ejector) {
        return ((Ejector) unit).getEfficiencyIsentropic();
      }
      break;
    case "liquidLevel":
      if (unit instanceof Tank) {
        return ((Tank) unit).getLiquidLevel();
      }
      break;
    case "volume":
      if (unit instanceof Tank) {
        return ((Tank) unit).getVolume();
      }
      break;
    case "errorTemperature":
      if (unit instanceof Recycle) {
        return ((Recycle) unit).getErrorTemperature();
      }
      break;
    case "errorFlow":
      if (unit instanceof Recycle) {
        return ((Recycle) unit).getErrorFlow();
      }
      break;
    default:
      break;
    }
    throw new IllegalArgumentException("Unknown property '" + property + "' for unit " + unit.getName() + " ("
        + unit.getClass().getSimpleName() + ")");
  }

  /**
   * Sets a property value directly on an equipment object.
   *
   * @param unit the equipment
   * @param property the property name
   * @param value the value to set
   * @param uom the unit of measure for the value
   */
  private void setEquipmentProperty(ProcessEquipmentInterface unit, String property, double value, String uom) {
    boolean hasUnit = uom != null && !uom.trim().isEmpty();

    if (property.startsWith("splitFactor_") && unit instanceof Splitter) {
      Splitter sp = (Splitter) unit;
      int idx = parseIndexSuffix(property, "splitFactor_");
      double[] sf = sp.getSplitFactors();
      if (idx < 0 || idx >= sf.length) {
        throw new IllegalArgumentException("Split factor index out of range for " + unit.getName() + ": " + property);
      }
      double[] updated = sf.clone();
      updated[idx] = value;
      // Splitter renormalises the factors so they sum to 1.
      sp.setSplitFactors(updated);
      return;
    }

    switch (property) {
    case "outletPressure":
      if (unit instanceof Compressor) {
        if (hasUnit) {
          ((Compressor) unit).setOutletPressure(value, uom);
        } else {
          ((Compressor) unit).setOutletPressure(value);
        }
        return;
      }
      if (unit instanceof Pump) {
        if (hasUnit) {
          ((Pump) unit).setOutletPressure(value, uom);
        } else {
          ((Pump) unit).setOutletPressure(value);
        }
        return;
      }
      if (unit instanceof ThrottlingValve) {
        if (hasUnit) {
          ((ThrottlingValve) unit).setOutletPressure(value, uom);
        } else {
          ((ThrottlingValve) unit).setOutletPressure(value);
        }
        return;
      }
      break;
    case "outletTemperature":
      if (unit instanceof Heater) {
        if (hasUnit) {
          ((Heater) unit).setOutletTemperature(value, uom);
        } else {
          ((Heater) unit).setOutletTemperature(value);
        }
        return;
      }
      break;
    case "polytropicEfficiency":
      if (unit instanceof Compressor) {
        ((Compressor) unit).setPolytropicEfficiency(value);
        return;
      }
      break;
    case "isentropicEfficiency":
      if (unit instanceof Compressor) {
        ((Compressor) unit).setIsentropicEfficiency(value);
        return;
      }
      if (unit instanceof Pump) {
        ((Pump) unit).setIsentropicEfficiency(value);
        return;
      }
      break;
    case "speed":
      if (unit instanceof Compressor) {
        ((Compressor) unit).setSpeed(value);
        return;
      }
      if (unit instanceof Pump) {
        ((Pump) unit).setSpeed(value);
        return;
      }
      break;
    case "Cv":
      if (unit instanceof ThrottlingValve) {
        if (hasUnit) {
          ((ThrottlingValve) unit).setCv(value, uom);
        } else {
          ((ThrottlingValve) unit).setCv(value);
        }
        return;
      }
      break;
    case "percentValveOpening":
      if (unit instanceof ThrottlingValve) {
        ((ThrottlingValve) unit).setPercentValveOpening(value);
        return;
      }
      if (unit instanceof WaterHammerPipe) {
        ((WaterHammerPipe) unit).setValveOpeningPercent(value);
        return;
      }
      break;
    case "UAvalue":
      if (unit instanceof HeatExchanger) {
        ((HeatExchanger) unit).setUAvalue(value);
        return;
      }
      break;
    case "temperature":
      if (unit instanceof StreamInterface) {
        if (hasUnit) {
          ((Stream) unit).setTemperature(value, uom);
        } else {
          unit.setTemperature(value);
        }
        return;
      }
      break;
    case "pressure":
      if (unit instanceof StreamInterface) {
        if (hasUnit) {
          ((Stream) unit).setPressure(value, uom);
        } else {
          unit.setPressure(value);
        }
        return;
      }
      break;
    case "flowRate":
      if (unit instanceof StreamInterface) {
        String flowUnit = hasUnit ? uom : "kg/hr";
        ((Stream) unit).setFlowRate(value, flowUnit);
        return;
      }
      break;
    case "length":
      if (unit instanceof Pipeline) {
        ((Pipeline) unit).setLength(value);
        return;
      }
      break;
    case "diameter":
      if (unit instanceof Pipeline) {
        ((Pipeline) unit).setDiameter(value);
        return;
      }
      break;
    case "pipeWallRoughness":
      if (unit instanceof Pipeline) {
        ((Pipeline) unit).setPipeWallRoughness(value);
        return;
      }
      break;
    case "wallThickness":
      if (unit instanceof Pipeline) {
        ((Pipeline) unit).setWallThickness(value);
        return;
      }
      break;
    case "elevation":
      if (unit instanceof Pipeline) {
        ((Pipeline) unit).setElevation(value);
        return;
      }
      break;
    case "valveOpening":
      if (unit instanceof WaterHammerPipe) {
        ((WaterHammerPipe) unit).setValveOpening(value);
        return;
      }
      break;
    case "valveOpeningPercent":
      if (unit instanceof WaterHammerPipe) {
        ((WaterHammerPipe) unit).setValveOpeningPercent(value);
        return;
      }
      break;
    case "numberOfNodes":
      if (unit instanceof WaterHammerPipe) {
        ((WaterHammerPipe) unit).setNumberOfNodes((int) Math.round(value));
        return;
      }
      break;
    case "courantNumber":
      if (unit instanceof WaterHammerPipe) {
        ((WaterHammerPipe) unit).setCourantNumber(value);
        return;
      }
      break;
    case "waveSpeed":
      if (unit instanceof WaterHammerPipe) {
        ((WaterHammerPipe) unit).setWaveSpeed(value);
        return;
      }
      break;
    case "dischargePressure":
      if (unit instanceof Ejector) {
        ((Ejector) unit).setDischargePressure(value);
        return;
      }
      break;
    case "efficiencyIsentropic":
      if (unit instanceof Ejector) {
        ((Ejector) unit).setEfficiencyIsentropic(value);
        return;
      }
      break;
    case "volume":
      if (unit instanceof Tank) {
        ((Tank) unit).setVolume(value);
        return;
      }
      break;
    case "condenserRefluxRatio":
      if (unit instanceof DistillationColumn) {
        ((DistillationColumn) unit).setCondenserRefluxRatio(value);
        return;
      }
      break;
    default:
      break;
    }
    throw new IllegalArgumentException("Cannot set property '" + property + "' on unit " + unit.getName() + " ("
        + unit.getClass().getSimpleName() + ")");
  }

  /**
   * Resolves a stream port name to the actual stream object.
   *
   * @param unit the equipment
   * @param portName the port name, e.g. "gasOutStream", "liquidOutStream", "outletStream"
   * @return the resolved stream, or null if not found
   */
  private StreamInterface resolveStreamPort(ProcessEquipmentInterface unit, String portName) {
    String normalizedPort = portName.toLowerCase();

    try {
      switch (normalizedPort) {
      case "gasoutstream":
      case "gasout":
        return (StreamInterface) unit.getClass().getMethod("getGasOutStream").invoke(unit);
      case "liquidoutstream":
      case "liquidout":
        return (StreamInterface) unit.getClass().getMethod("getLiquidOutStream").invoke(unit);
      case "oiloutstream":
      case "oilout":
        return (StreamInterface) unit.getClass().getMethod("getOilOutStream").invoke(unit);
      case "wateroutstream":
      case "waterout":
        return (StreamInterface) unit.getClass().getMethod("getWaterOutStream").invoke(unit);
      case "outletstream":
      case "outlet":
        return (StreamInterface) unit.getClass().getMethod("getOutletStream").invoke(unit);
      case "inletstream":
      case "inlet":
        List<StreamInterface> inlets = unit.getInletStreams();
        return inlets.isEmpty() ? null : inlets.get(0);
      default:
        break;
      }
    } catch (NoSuchMethodException e) {
      // Try fallback: some equipment uses getOutStream (e.g., Ejector) instead of getOutletStream
      if (normalizedPort.equals("outletstream") || normalizedPort.equals("outlet")) {
        try {
          return (StreamInterface) unit.getClass().getMethod("getOutStream").invoke(unit);
        } catch (Exception ex) {
          // Fall through to getOutletStreams fallback
        }
        List<StreamInterface> outlets = unit.getOutletStreams();
        return outlets.isEmpty() ? null : outlets.get(0);
      }
    } catch (Exception e) {
      // reflection failure
    }

    // Try indexed port: "outletStream0", "outletStream1", etc.
    if (normalizedPort.startsWith("outletstream") && normalizedPort.length() > 12) {
      try {
        int idx = Integer.parseInt(normalizedPort.substring(12));
        List<StreamInterface> outlets = unit.getOutletStreams();
        if (idx >= 0 && idx < outlets.size()) {
          return outlets.get(idx);
        }
      } catch (NumberFormatException e) {
        // ignore
      }
    }

    return null;
  }

  /**
   * Gets a property value from a stream.
   *
   * @param stream the stream
   * @param property the property name
   * @param uom the desired unit of measure
   * @return the property value
   */
  private double getStreamProperty(StreamInterface stream, String property, String uom) {
    boolean hasUnit = uom != null && !uom.trim().isEmpty();

    switch (property) {
    case "temperature":
      return hasUnit ? stream.getTemperature(uom) : stream.getTemperature();
    case "pressure":
      return hasUnit ? stream.getPressure(uom) : stream.getPressure();
    case "flowRate":
      return hasUnit ? stream.getFlowRate(uom) : stream.getFlowRate("kg/hr");
    case "density":
      return stream.getFluid() != null ? stream.getFluid().getDensity("kg/m3") : Double.NaN;
    case "molarMass":
      return stream.getFluid() != null ? stream.getFluid().getMolarMass("kg/mol") : Double.NaN;
    default:
      break;
    }
    throw new IllegalArgumentException("Unknown stream property: " + property);
  }

  /**
   * Sets a property value on a stream.
   *
   * @param stream the stream
   * @param property the property name
   * @param value the value to set
   * @param uom the unit of measure
   */
  private void setStreamProperty(StreamInterface stream, String property, double value, String uom) {
    boolean hasUnit = uom != null && !uom.trim().isEmpty();

    if (!(stream instanceof Stream)) {
      throw new IllegalArgumentException(
          "Cannot set properties on non-Stream type: " + stream.getClass().getSimpleName());
    }
    Stream s = (Stream) stream;

    switch (property) {
    case "temperature":
      if (hasUnit) {
        s.setTemperature(value, uom);
      } else {
        s.setTemperature(value);
      }
      return;
    case "pressure":
      if (hasUnit) {
        s.setPressure(value, uom);
      } else {
        s.setPressure(value);
      }
      return;
    case "flowRate":
      s.setFlowRate(value, hasUnit ? uom : "kg/hr");
      return;
    default:
      break;
    }
    throw new IllegalArgumentException("Cannot set stream property: " + property);
  }

  // ========================== Self-Healing Helpers ==========================

  /**
   * Diagnoses an address resolution failure and attempts recovery via fuzzy matching.
   *
   * @param address the failed address
   * @param error the caught exception
   * @return a diagnostic result with suggestions and possible auto-correction
   */
  private AutomationDiagnostics.DiagnosticResult diagnoseAndAttemptRecovery(String address,
      IllegalArgumentException error) {
    String msg = error.getMessage();
    if (msg == null) {
      msg = "";
    }

    // Parse address to extract components
    String localAddress = address;
    int areaSepIdx = address.indexOf(AREA_SEPARATOR);
    if (areaSepIdx >= 0) {
      localAddress = address.substring(areaSepIdx + AREA_SEPARATOR.length());
    }
    String[] parts = localAddress.split("\\.", 3);
    String unitName = parts.length > 0 ? parts[0] : "";

    // Unit not found
    if (msg.contains("Unit not found") || msg.contains("Area not found")) {
      List<String> validUnits = getUnitList();
      return diagnostics.diagnoseUnitNotFound(unitName, validUnits);
    }

    // Stream port not found
    if (msg.contains("Stream port not found") || msg.contains("port")) {
      List<String> validPorts = java.util.Arrays.asList("gasOutStream", "liquidOutStream", "oilOutStream",
          "waterOutStream", "outletStream", "inletStream");
      String portName = parts.length > 1 ? parts[1] : "";
      return diagnostics.diagnosePortNotFound(address, unitName, portName, validPorts);
    }

    // Property not found (Unknown property, Unknown stream property)
    if (msg.contains("Unknown property") || msg.contains("Unknown stream property")) {
      try {
        List<SimulationVariable> vars = getVariableList(unitName);
        String propertyName = parts.length > 1 ? parts[parts.length - 1] : "";
        return diagnostics.diagnosePropertyNotFound(address, unitName, propertyName, vars);
      } catch (Exception e) {
        // Can't get variable list - fall through
      }
    }

    // Read-only variable (set attempted on an OUTPUT-type property)
    if (msg.contains("Cannot set property") || msg.contains("Cannot set stream")
        || msg.toLowerCase(java.util.Locale.ROOT).contains("read-only")
        || msg.toLowerCase(java.util.Locale.ROOT).contains("read only")) {
      java.util.Map<String, Object> ctx = new java.util.LinkedHashMap<String, Object>();
      ctx.put("errorMessage", msg);
      return new AutomationDiagnostics.DiagnosticResult(AutomationDiagnostics.ErrorCategory.READ_ONLY_VARIABLE, address,
          msg, new ArrayList<String>(), null,
          "This variable is an OUTPUT (computed by the simulation) and cannot be set. "
              + "Use getVariableList() to discover INPUT-type variables that can be modified.",
          ctx);
    }

    // Unknown unit-of-measure
    if (msg.toLowerCase(java.util.Locale.ROOT).contains("unknown unit")
        || msg.toLowerCase(java.util.Locale.ROOT).contains("unit not supported")
        || msg.toLowerCase(java.util.Locale.ROOT).contains("invalid unit")) {
      java.util.Map<String, Object> ctx = new java.util.LinkedHashMap<String, Object>();
      ctx.put("errorMessage", msg);
      return new AutomationDiagnostics.DiagnosticResult(AutomationDiagnostics.ErrorCategory.UNKNOWN_UNIT, address, msg,
          new ArrayList<String>(), null, "Unsupported unit of measure. Call getAllowedUnits(address) for a list of "
              + "valid UOM strings, or pass null to use the variable's default unit.",
          ctx);
    }

    // Convergence failure during write+run
    String lower = msg.toLowerCase(java.util.Locale.ROOT);
    if (lower.contains("not converge") || lower.contains("did not converge") || lower.contains("convergence")
        || lower.contains("solver failed")) {
      java.util.Map<String, Object> ctx = new java.util.LinkedHashMap<String, Object>();
      ctx.put("errorMessage", msg);
      return new AutomationDiagnostics.DiagnosticResult(AutomationDiagnostics.ErrorCategory.CONVERGENCE_FAILURE,
          address, msg, new ArrayList<String>(), null,
          "Solver did not converge after the change. Try a smaller step, relax recycle "
              + "tolerance, or revert the change. Inspect the diagnostics log for residuals.",
          ctx);
    }

    // Invalid address format or generic error
    java.util.Map<String, Object> context = new java.util.LinkedHashMap<String, Object>();
    context.put("errorMessage", msg);
    context.put("addressFormat", "unitName.property or unitName.port.property");
    return new AutomationDiagnostics.DiagnosticResult(AutomationDiagnostics.ErrorCategory.INVALID_ADDRESS_FORMAT,
        address, msg, new java.util.ArrayList<String>(), null,
        "Check address format. Expected: 'unitName.property' or 'unitName.port.property'. "
            + "Use getUnitList() and getVariableList(unitName) to discover valid addresses.",
        context);
  }

  /**
   * Extracts the property name from an address (the last dot-separated segment).
   *
   * @param address the dot-notation address
   * @return the property name
   */
  private String extractPropertyName(String address) {
    if (address == null) {
      return "";
    }
    String localAddress = address;
    int areaSepIdx = address.indexOf(AREA_SEPARATOR);
    if (areaSepIdx >= 0) {
      localAddress = address.substring(areaSepIdx + AREA_SEPARATOR.length());
    }
    int lastDot = localAddress.lastIndexOf('.');
    return lastDot >= 0 ? localAddress.substring(lastDot + 1) : localAddress;
  }

  /**
   * Builds a success JSON response for a get operation.
   *
   * @param address the address
   * @param value the value
   * @param unit the unit of measure
   * @return JSON string
   */
  private String buildSuccessJson(String address, double value, String unit) {
    com.google.gson.JsonObject result = new com.google.gson.JsonObject();
    result.addProperty("status", "success");
    result.addProperty("address", address);
    result.addProperty("value", value);
    if (unit != null) {
      result.addProperty("unit", unit);
    }
    return result.toString();
  }

  /**
   * Builds a JSON response for a successful auto-corrected get operation.
   *
   * @param originalAddress the original address that failed
   * @param correctedAddress the corrected address that succeeded
   * @param value the value read
   * @param unit the unit of measure
   * @param diag the diagnostic result
   * @return JSON string
   */
  private String buildAutoCorrectedJson(String originalAddress, String correctedAddress, double value, String unit,
      AutomationDiagnostics.DiagnosticResult diag) {
    com.google.gson.JsonObject result = new com.google.gson.JsonObject();
    result.addProperty("status", "auto_corrected");
    result.addProperty("originalAddress", originalAddress);
    result.addProperty("correctedAddress", correctedAddress);
    result.addProperty("value", value);
    if (unit != null) {
      result.addProperty("unit", unit);
    }
    result.addProperty("remediation", "Address was auto-corrected from '" + originalAddress + "' to '"
        + correctedAddress + "'. Use the corrected address in future calls.");
    return result.toString();
  }

  /**
   * Builds a success JSON response for a set operation.
   *
   * @param address the address
   * @param value the value set
   * @param unit the unit of measure
   * @param warningJson JSON warning from bounds validation, or null
   * @return JSON string
   */
  private String buildSetSuccessJson(String address, double value, String unit, String warningJson) {
    com.google.gson.JsonObject result = new com.google.gson.JsonObject();
    result.addProperty("status", "success");
    result.addProperty("address", address);
    result.addProperty("value", value);
    if (unit != null) {
      result.addProperty("unit", unit);
    }
    if (warningJson != null) {
      result.add("warning", com.google.gson.JsonParser.parseString(warningJson).getAsJsonObject());
    }
    return result.toString();
  }

  /**
   * Builds a JSON response for a successful auto-corrected set operation.
   *
   * @param originalAddress the original address that failed
   * @param correctedAddress the corrected address that succeeded
   * @param value the value set
   * @param unit the unit of measure
   * @param diag the diagnostic result
   * @return JSON string
   */
  private String buildAutoCorrectedSetJson(String originalAddress, String correctedAddress, double value, String unit,
      AutomationDiagnostics.DiagnosticResult diag) {
    com.google.gson.JsonObject result = new com.google.gson.JsonObject();
    result.addProperty("status", "auto_corrected");
    result.addProperty("originalAddress", originalAddress);
    result.addProperty("correctedAddress", correctedAddress);
    result.addProperty("value", value);
    if (unit != null) {
      result.addProperty("unit", unit);
    }
    result.addProperty("remediation", "Address was auto-corrected from '" + originalAddress + "' to '"
        + correctedAddress + "'. Use the corrected address in future calls.");
    return result.toString();
  }

  // ===================================================================================
  // Agentic API extensions (P0–P2). All methods below are additive and non-breaking.
  // ===================================================================================

  /**
   * Returns the stable schema version of JSON responses produced by this facade. Agents and MCP clients can branch on
   * this value when parsing responses (see {@link #SCHEMA_VERSION}).
   *
   * @return the schema version string, e.g. "1.0"
   */
  public String getSchemaVersion() {
    return SCHEMA_VERSION;
  }

  // ----------------------------- Dirty / run-policy tracking ------------------------------

  /**
   * Returns whether any input has been set via {@link #setVariableValue} since the last successful {@link #run()} or
   * {@link #runIfDirty()}. When {@code true}, outputs returned by {@link #getVariableValue} may be stale and should be
   * refreshed by calling {@link #run()}.
   *
   * @return {@code true} if the underlying process needs to be re-run, {@code false} otherwise
   */
  public boolean isDirty() {
    return dirty;
  }

  /**
   * Manually marks this facade as dirty, e.g. after modifying the underlying process system directly through other Java
   * APIs.
   */
  public void markDirty() {
    this.dirty = true;
  }

  /**
   * Runs the underlying {@link ProcessSystem} or {@link ProcessModel} and clears the dirty flag on success.
   *
   * @throws RuntimeException if the underlying solver fails
   */
  public void run() {
    if (processModel != null) {
      processModel.run();
    } else {
      processSystem.run();
    }
    this.dirty = false;
  }

  /**
   * Runs the underlying process only if {@link #isDirty()} is true. Returns whether a run was actually performed.
   *
   * @return {@code true} if a run was triggered, {@code false} if the process was already clean
   */
  public boolean runIfDirty() {
    if (dirty) {
      run();
      return true;
    }
    return false;
  }

  /**
   * Sets an input variable then immediately runs the process. Equivalent to
   * {@link #setVariableValue(String, double, String)} followed by {@link #run()} but provided as a single atomic call
   * for agents that want the result of one specific change.
   *
   * @param address the dot-notation address
   * @param value the value to set
   * @param unitOfMeasure the unit of the provided value, or null for default units
   * @throws IllegalArgumentException if the address cannot be resolved
   * @throws RuntimeException if the subsequent run fails
   */
  public void setVariableValueAndRun(String address, double value, String unitOfMeasure) {
    setVariableValue(address, value, unitOfMeasure);
    run();
  }

  // ----------------------------- Agentic run gating & evaluation ------------------------------

  /**
   * Returns the structured outcome of the most recent run as a JSON string. Lets agents inspect whether the last run
   * succeeded and, if not, which unit failed, without catching and parsing a {@link RuntimeException}.
   *
   * @return schema-versioned JSON describing the last run outcome (see
   * {@link neqsim.process.processmodel.RunStatus#toJson()})
   */
  public String getRunStatusJson() {
    if (processModel != null) {
      return processModel.getRunStatusJson();
    }
    return processSystem.getRunStatusJson();
  }

  /**
   * Runs the underlying process and returns the structured {@link neqsim.process.processmodel.RunStatus RunStatus} as
   * JSON, <strong>never throwing</strong> on a solver failure. This is the run primitive recommended for agentic loops:
   * a diverging or failing trial is reported as {@code "success": false} with the offending unit named, instead of
   * crashing the agent's control loop with an exception.
   *
   * <p>
   * The dirty flag is cleared whether or not the run succeeded, so a subsequent {@link #runIfDirty()} will not re-run a
   * known-failing configuration.
   * </p>
   *
   * @return schema-versioned run-status JSON, with {@code success}, {@code failedUnitName} and {@code failedUnitError}
   * fields
   */
  public String runJson() {
    try {
      run();
    } catch (RuntimeException e) {
      // The run status is still recorded by the process; the dirty flag is cleared below so the
      // agent does not silently re-run a known-failing setpoint.
      this.dirty = false;
    }
    return getRunStatusJson();
  }

  /**
   * Runs the process until convergence (or an iteration limit) and returns a unified JSON report suitable for per-trial
   * feasibility gating in agentic optimization. Never throws on a solver failure.
   *
   * <p>
   * For a multi-area {@link ProcessModel}, this delegates to {@link ProcessModel#runUntilConverged(int, double)} and
   * merges {@link ProcessModel#getConvergenceReportJson()} with the run status. For a single {@link ProcessSystem}, a
   * normal {@link ProcessSystem#run()} already iterates internal recycles; the {@code converged} flag then reflects
   * whether the run completed without a failed unit (the {@link neqsim.process.processmodel.RunStatus RunStatus}
   * success flag).
   * </p>
   *
   * <p>
   * Top-level fields: {@code schemaVersion}, {@code converged}, {@code runSucceeded}, {@code failedUnitName},
   * {@code failedUnitError}, {@code iterations}, {@code maxIterations}, {@code maxError}, and (multi-area only) the
   * nested {@code convergence} report and {@code areas} array.
   * </p>
   *
   * @param maxIterations maximum outer iterations (multi-area only); must be at least 1
   * @param tolerance relative convergence tolerance (multi-area only); must be finite and positive
   * @return schema-versioned convergence/run JSON
   * @throws IllegalArgumentException if {@code maxIterations < 1} or {@code tolerance} is not a finite positive number
   */
  public String runUntilConvergedJson(int maxIterations, double tolerance) {
    if (maxIterations < 1) {
      throw new IllegalArgumentException("maxIterations must be at least 1, was " + maxIterations);
    }
    if (Double.isNaN(tolerance) || Double.isInfinite(tolerance) || tolerance <= 0.0) {
      throw new IllegalArgumentException("tolerance must be a finite positive number, was " + tolerance);
    }

    com.google.gson.JsonObject root = new com.google.gson.JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);
    root.addProperty("maxIterations", maxIterations);

    boolean runSucceeded = true;
    boolean converged;
    if (processModel != null) {
      try {
        converged = processModel.runUntilConverged(maxIterations, tolerance);
      } catch (RuntimeException e) {
        runSucceeded = false;
        converged = false;
      }
      root.addProperty("converged", converged);
      root.addProperty("iterations", processModel.getLastIterationCount());
      root.addProperty("maxError", processModel.getError());
      // Embed the full structured convergence report for area-level diagnostics.
      try {
        com.google.gson.JsonElement report = com.google.gson.JsonParser
            .parseString(processModel.getConvergenceReportJson());
        root.add("convergence", report);
        if (report.isJsonObject() && report.getAsJsonObject().has("areas")) {
          root.add("areas", report.getAsJsonObject().get("areas"));
        }
      } catch (RuntimeException e) {
        // convergence report unavailable; primary fields above are sufficient
      }
    } else {
      try {
        processSystem.run();
      } catch (RuntimeException e) {
        runSucceeded = false;
      }
      converged = runSucceeded && processSystem.getRunStatus().isSuccess();
      root.addProperty("converged", converged);
    }
    this.dirty = false;

    root.addProperty("runSucceeded", runSucceeded);
    mergeRunStatus(root);
    return root.toString();
  }

  /**
   * Atomic <em>evaluate</em> step for closed-loop agentic optimization: applies a batch of decision variables, runs the
   * process until convergence, gates the result on the run status, and reads back the requested objective / constraint
   * variables &mdash; all returned as a single JSON document. This method <strong>never throws</strong>; an infeasible
   * trial (rejected setpoint, diverged solver, or failed unit) is reported as {@code "feasible": false} so the
   * optimizer can penalise it instead of crashing.
   *
   * <p>
   * This is the recommended one-call primitive for SQP / particle-swarm / grid sweeps over a flowsheet. It removes the
   * boilerplate of set &rarr; run &rarr; try/catch &rarr; read and avoids the common jpype pitfall of mixing
   * {@code java.lang.String} JSON with Python parsing, because the entire trial outcome is returned as one
   * ready-to-parse string.
   * </p>
   *
   * <p>
   * Returned fields:
   * </p>
   * <ul>
   * <li>{@code schemaVersion} &mdash; stable schema version</li>
   * <li>{@code feasible} &mdash; {@code true} only when every setpoint was applied, the run did not fail, and the model
   * converged</li>
   * <li>{@code converged}, {@code runSucceeded}, {@code failedUnitName}, {@code failedUnitError}, {@code iterations},
   * {@code maxError}</li>
   * <li>{@code setpointsApplied} &mdash; map of address &rarr; value that were set</li>
   * <li>{@code setpointsRejected} &mdash; map of address &rarr; rejection reason</li>
   * <li>{@code readbacks} &mdash; map of address &rarr; value for successfully read outputs</li>
   * <li>{@code readbackErrors} &mdash; map of address &rarr; reason for unreadable outputs</li>
   * </ul>
   *
   * @param setpoints ordered map of decision-variable address &rarr; value to apply; may be null or empty to evaluate
   * the current configuration
   * @param setpointUnit unit applied to every setpoint, or null for default units
   * @param readbacks objective / constraint addresses to read after the run; may be null or empty
   * @param readbackUnit unit applied to every read-back, or null for default units
   * @param maxIterations maximum outer iterations for the convergence run; must be at least 1
   * @param tolerance relative convergence tolerance; must be finite and positive
   * @return schema-versioned JSON describing the trial outcome
   * @throws IllegalArgumentException if {@code maxIterations < 1} or {@code tolerance} is not a finite positive number
   */
  public String evaluate(Map<String, Double> setpoints, String setpointUnit, List<String> readbacks,
      String readbackUnit, int maxIterations, double tolerance) {
    if (maxIterations < 1) {
      throw new IllegalArgumentException("maxIterations must be at least 1, was " + maxIterations);
    }
    if (Double.isNaN(tolerance) || Double.isInfinite(tolerance) || tolerance <= 0.0) {
      throw new IllegalArgumentException("tolerance must be a finite positive number, was " + tolerance);
    }

    com.google.gson.JsonObject root = new com.google.gson.JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);

    // 1) Apply decision variables, capturing per-address rejections.
    com.google.gson.JsonObject applied = new com.google.gson.JsonObject();
    com.google.gson.JsonObject rejected = new com.google.gson.JsonObject();
    if (setpoints != null) {
      for (Map.Entry<String, Double> e : setpoints.entrySet()) {
        try {
          setVariableValue(e.getKey(), e.getValue(), setpointUnit);
          applied.addProperty(e.getKey(), e.getValue());
        } catch (RuntimeException ex) {
          rejected.addProperty(e.getKey(), ex.getMessage());
          diagnostics.recordFailure("set", e.getKey(), AutomationDiagnostics.ErrorCategory.INVALID_ADDRESS_FORMAT,
              null);
        }
      }
    }
    root.add("setpointsApplied", applied);
    root.add("setpointsRejected", rejected);

    // 2) Run until converged (never throws here).
    boolean runSucceeded = true;
    boolean converged;
    if (processModel != null) {
      try {
        converged = processModel.runUntilConverged(maxIterations, tolerance);
      } catch (RuntimeException ex) {
        runSucceeded = false;
        converged = false;
      }
      root.addProperty("iterations", processModel.getLastIterationCount());
      root.addProperty("maxError", processModel.getError());
    } else {
      try {
        processSystem.run();
      } catch (RuntimeException ex) {
        runSucceeded = false;
      }
      converged = runSucceeded && processSystem.getRunStatus().isSuccess();
    }
    this.dirty = false;
    root.addProperty("runSucceeded", runSucceeded);
    root.addProperty("converged", converged);
    mergeRunStatus(root);

    boolean runFailed = root.has("failedUnitName") && !root.get("failedUnitName").isJsonNull();
    boolean feasible = runSucceeded && converged && !runFailed && rejected.size() == 0;
    root.addProperty("feasible", feasible);

    // 3) Read back objectives / constraints, capturing per-address failures.
    com.google.gson.JsonObject reads = new com.google.gson.JsonObject();
    com.google.gson.JsonObject readErrors = new com.google.gson.JsonObject();
    if (readbacks != null) {
      for (String addr : readbacks) {
        try {
          reads.addProperty(addr, getVariableValue(addr, readbackUnit));
        } catch (RuntimeException ex) {
          readErrors.addProperty(addr, ex.getMessage());
        }
      }
    }
    root.add("readbacks", reads);
    root.add("readbackErrors", readErrors);

    return root.toString();
  }

  /**
   * Convenience overload of {@link #evaluate(Map, String, List, String, int, double)} that uses the same unit for
   * setpoints and read-backs and sensible default convergence settings (30 iterations, relative tolerance 5e-3, which
   * is robust for plants with near-zero-flow anti-surge recycles).
   *
   * @param setpoints decision-variable address &rarr; value map; may be null or empty
   * @param unitOfMeasure unit applied to setpoints and read-backs, or null for default units
   * @param readbacks objective / constraint addresses to read after the run; may be null or empty
   * @return schema-versioned JSON describing the trial outcome
   */
  public String evaluate(Map<String, Double> setpoints, String unitOfMeasure, List<String> readbacks) {
    return evaluate(setpoints, unitOfMeasure, readbacks, unitOfMeasure, 30, 5.0e-3);
  }

  /**
   * Evaluates a batch of candidate setpoint sets and returns one schema-versioned JSON object per candidate. This is
   * the parallel, ML/DoE-oriented counterpart of {@link #evaluate(Map, String, java.util.List)}: an external optimizer
   * (SciPy, BoTorch, a genetic algorithm, an agent) can score many candidates in one call.
   *
   * <p>
   * For a {@link ProcessSystem} with {@code maxParallel &gt; 1} and more than one candidate, each candidate is applied
   * to an independent {@link ProcessSystem#copy() copy} evaluated on its own thread, so the batch is genuinely parallel
   * and the live model is <b>left untouched</b>. For a multi-area {@link ProcessModel} (which has no {@code copy()}),
   * or when {@code maxParallel == 1}, candidates are evaluated sequentially on the live facade (the last candidate
   * remains applied). Each per-candidate result carries the full {@link #evaluate} payload &mdash; including the
   * {@code converged}, {@code iterations}, {@code maxError}, {@code failedUnitName} and {@code failedUnitError}
   * convergence-failure detail &mdash; plus its {@code index}.
   * </p>
   *
   * @param candidates the list of decision-variable maps to evaluate; must not be null
   * @param setpointUnit unit applied to all setpoints, or null for each variable's default unit
   * @param readbacks objective / constraint addresses read after each run; may be null or empty
   * @param readbackUnit unit applied to all read-backs, or null for default units
   * @param maxParallel maximum worker threads (only used for a {@link ProcessSystem}); values &lt; 1 are treated as 1
   * @param maxIterations convergence iteration cap per candidate; must be at least 1
   * @param tolerance convergence tolerance per candidate; must be finite and positive
   * @return JSON {@code {schemaVersion, count, parallel, feasibleCount, firstFeasibleIndex, results:[{index, ...}]}}
   */
  public String evaluateBatchJson(List<Map<String, Double>> candidates, String setpointUnit, List<String> readbacks,
      String readbackUnit, int maxParallel, int maxIterations, double tolerance) {
    if (candidates == null) {
      throw new IllegalArgumentException("candidates must not be null");
    }
    if (maxIterations < 1) {
      throw new IllegalArgumentException("maxIterations must be at least 1, was " + maxIterations);
    }
    if (Double.isNaN(tolerance) || Double.isInfinite(tolerance) || tolerance <= 0.0) {
      throw new IllegalArgumentException("tolerance must be a finite positive number, was " + tolerance);
    }

    int n = candidates.size();
    com.google.gson.JsonObject[] out = new com.google.gson.JsonObject[n];
    boolean parallel = processSystem != null && maxParallel > 1 && n > 1;

    if (parallel) {
      int threads = Math.min(maxParallel, n);
      java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
      try {
        List<java.util.concurrent.Future<com.google.gson.JsonObject>> futures = new ArrayList<java.util.concurrent.Future<com.google.gson.JsonObject>>();
        for (int i = 0; i < n; i++) {
          final int index = i;
          final Map<String, Double> candidate = candidates.get(i);
          futures.add(pool.submit(new java.util.concurrent.Callable<com.google.gson.JsonObject>() {
            @Override
            public com.google.gson.JsonObject call() {
              ProcessSystem copy = processSystem.copy();
              ProcessAutomation copyAuto = new ProcessAutomation(copy);
              String json = copyAuto.evaluate(candidate, setpointUnit, readbacks, readbackUnit, maxIterations,
                  tolerance);
              com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
              obj.addProperty("index", index);
              return obj;
            }
          }));
        }
        for (int i = 0; i < n; i++) {
          try {
            out[i] = futures.get(i).get();
          } catch (Exception ex) {
            com.google.gson.JsonObject err = new com.google.gson.JsonObject();
            err.addProperty("schemaVersion", SCHEMA_VERSION);
            err.addProperty("index", i);
            err.addProperty("feasible", false);
            err.addProperty("evaluationError", ex.getMessage() == null ? ex.toString() : ex.getMessage());
            out[i] = err;
          }
        }
      } finally {
        pool.shutdownNow();
      }
    } else {
      for (int i = 0; i < n; i++) {
        String json = evaluate(candidates.get(i), setpointUnit, readbacks, readbackUnit, maxIterations, tolerance);
        com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        obj.addProperty("index", i);
        out[i] = obj;
      }
    }

    com.google.gson.JsonArray results = new com.google.gson.JsonArray();
    int feasibleCount = 0;
    int firstFeasibleIndex = -1;
    for (int i = 0; i < n; i++) {
      results.add(out[i]);
      if (out[i].has("feasible") && out[i].get("feasible").getAsBoolean()) {
        feasibleCount++;
        if (firstFeasibleIndex < 0) {
          firstFeasibleIndex = i;
        }
      }
    }

    com.google.gson.JsonObject root = new com.google.gson.JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);
    root.addProperty("count", n);
    root.addProperty("parallel", parallel);
    root.addProperty("feasibleCount", feasibleCount);
    root.addProperty("firstFeasibleIndex", firstFeasibleIndex);
    root.add("results", results);
    return root.toString();
  }

  /**
   * Convenience overload of {@link #evaluateBatchJson(List, String, List, String, int, double)} using default
   * convergence settings (30 iterations, tolerance 5e-3) and the same unit for setpoints and read-backs.
   *
   * @param candidates the list of decision-variable maps to evaluate; must not be null
   * @param unitOfMeasure unit applied to setpoints and read-backs, or null for default units
   * @param readbacks objective / constraint addresses read after each run; may be null or empty
   * @param maxParallel maximum worker threads (only used for a {@link ProcessSystem})
   * @return schema-versioned JSON batch result
   */
  public String evaluateBatchJson(List<Map<String, Double>> candidates, String unitOfMeasure, List<String> readbacks,
      int maxParallel) {
    return evaluateBatchJson(candidates, unitOfMeasure, readbacks, unitOfMeasure, maxParallel, 30, 5.0e-3);
  }

  /**
   * Parses the trailing integer index of an indexed property such as {@code splitFactor_2}.
   *
   * @param property the full property name
   * @param prefix the prefix preceding the index
   * @return the parsed index, or -1 if it cannot be parsed
   */
  private static int parseIndexSuffix(String property, String prefix) {
    try {
      return Integer.parseInt(property.substring(prefix.length()));
    } catch (NumberFormatException | IndexOutOfBoundsException ex) {
      return -1;
    }
  }

  /**
   * Merges the most recent {@link neqsim.process.processmodel.RunStatus RunStatus} fields ({@code success},
   * {@code failedUnitName}, {@code failedUnitError}) into the supplied JSON object. Used by
   * {@link #runUntilConvergedJson(int, double)} and {@link #evaluate(Map, String, List, String, int, double)}.
   *
   * @param root the JSON object to enrich with run-status fields
   */
  private void mergeRunStatus(com.google.gson.JsonObject root) {
    neqsim.process.processmodel.RunStatus status = processModel != null ? processModel.getRunStatus()
        : processSystem.getRunStatus();
    if (status == null) {
      return;
    }
    if (status.getFailedUnitName() != null) {
      root.addProperty("failedUnitName", status.getFailedUnitName());
    } else {
      root.add("failedUnitName", com.google.gson.JsonNull.INSTANCE);
    }
    if (status.getFailedUnitError() != null) {
      root.addProperty("failedUnitError", status.getFailedUnitError());
    } else {
      root.add("failedUnitError", com.google.gson.JsonNull.INSTANCE);
    }
  }

  // ----------------------------- Batch get / set ------------------------------

  /**
   * Reads many variables in a single call. Reduces round-trip latency for MCP/HTTP agents that otherwise issue one call
   * per variable.
   *
   * <p>
   * Each value is read in the requested unit if {@code unitOfMeasure} is non-null, otherwise in the variable's default
   * unit. Addresses that fail to resolve are skipped and recorded in the diagnostics; the returned map only contains
   * successfully read entries. To detect failures, compare {@code addresses.size()} to the returned map size and
   * consult {@link #getDiagnostics()}.
   * </p>
   *
   * @param addresses dot-notation addresses to read
   * @param unitOfMeasure unit applied to every address, or null for defaults
   * @return ordered map from address to value (Linked) with only successfully read entries
   * @throws IllegalArgumentException if {@code addresses} is null
   */
  public Map<String, Double> getValues(List<String> addresses, String unitOfMeasure) {
    if (addresses == null) {
      throw new IllegalArgumentException("addresses must not be null");
    }
    Map<String, Double> out = new LinkedHashMap<String, Double>();
    for (String addr : addresses) {
      try {
        out.put(addr, getVariableValue(addr, unitOfMeasure));
      } catch (Exception e) {
        diagnostics.recordFailure("get", addr, AutomationDiagnostics.ErrorCategory.INVALID_ADDRESS_FORMAT, null);
      }
    }
    return out;
  }

  /**
   * Sets many input variables in a single call.
   *
   * @param updates ordered map from address to value
   * @param unitOfMeasure unit applied to every update, or null for defaults
   * @param runAfter when {@code true}, calls {@link #run()} once after all writes succeed; when {@code false}, leaves
   * the facade dirty
   * @return number of variables that were successfully set
   * @throws IllegalArgumentException if {@code updates} is null
   */
  public int setValues(Map<String, Double> updates, String unitOfMeasure, boolean runAfter) {
    if (updates == null) {
      throw new IllegalArgumentException("updates must not be null");
    }
    int ok = 0;
    for (Map.Entry<String, Double> e : updates.entrySet()) {
      try {
        setVariableValue(e.getKey(), e.getValue(), unitOfMeasure);
        ok++;
      } catch (Exception ex) {
        diagnostics.recordFailure("set", e.getKey(), AutomationDiagnostics.ErrorCategory.INVALID_ADDRESS_FORMAT, null);
      }
    }
    if (runAfter && ok > 0) {
      run();
    }
    return ok;
  }

  // ----------------------------- Typed validation ------------------------------

  /**
   * Returns the registry of typed {@link WriteValidator write validators} consulted by
   * {@link #setVariableValueValidated(String, double, String)} and {@link #setValuesTransactional(Map, String)}.
   * Defaults to {@link WriteValidatorRegistry#createDefault()}; replace it via
   * {@link #setWriteValidatorRegistry(WriteValidatorRegistry)} to disable validation or to add project-specific checks.
   *
   * @return the current registry; never null
   */
  public WriteValidatorRegistry getWriteValidatorRegistry() {
    return validatorRegistry;
  }

  /**
   * Replaces the registry of typed {@link WriteValidator write validators}.
   *
   * @param registry the new registry; must not be null
   */
  public void setWriteValidatorRegistry(WriteValidatorRegistry registry) {
    if (registry == null) {
      throw new IllegalArgumentException("registry must not be null");
    }
    this.validatorRegistry = registry;
  }

  /**
   * Resolves the {@link ProcessEquipmentInterface} addressed by an automation address (handling the optional area
   * prefix and stripping any port/property suffix). Returns {@code null} when the address cannot be resolved; never
   * throws.
   *
   * @param address the dot-notation address
   * @return the equipment, or null when unresolvable
   */
  private ProcessEquipmentInterface tryResolveEquipment(String address) {
    if (address == null || address.trim().isEmpty()) {
      return null;
    }
    String local = address;
    String areaName = null;
    int areaSepIdx = address.indexOf(AREA_SEPARATOR);
    if (areaSepIdx >= 0) {
      areaName = address.substring(0, areaSepIdx);
      local = address.substring(areaSepIdx + AREA_SEPARATOR.length());
    }
    String[] parts = local.split("\\.", 3);
    try {
      return findUnit(areaName, parts[0]);
    } catch (Exception ex) {
      return null;
    }
  }

  /**
   * Returns the property path that the typed validators see (the portion of the address after the unit name). For a
   * two-part address {@code "Compressor.outletPressure"} this is {@code "outletPressure"}; for a three-part address
   * {@code "Sep1.gasOut.pressure"} this is {@code "gasOut.pressure"}.
   *
   * @param address the dot-notation address
   * @return the property path, or null when the address has no property part
   */
  private String extractPropertyPath(String address) {
    if (address == null) {
      return null;
    }
    String local = address;
    int areaSepIdx = address.indexOf(AREA_SEPARATOR);
    if (areaSepIdx >= 0) {
      local = address.substring(areaSepIdx + AREA_SEPARATOR.length());
    }
    int dot = local.indexOf('.');
    if (dot < 0) {
      return null;
    }
    return local.substring(dot + 1);
  }

  /**
   * Reads the current value of an address without throwing. Returns {@code null} when the value is unreadable for any
   * reason (typically because the variable is INPUT-only and has not yet been set, or because the equipment has not
   * been run).
   *
   * @param address the dot-notation address
   * @param unitOfMeasure the unit of measure, or null for the default
   * @return the current value, or null when unreadable
   */
  private Double tryReadValue(String address, String unitOfMeasure) {
    try {
      return getVariableValue(address, unitOfMeasure);
    } catch (Exception ex) {
      return null;
    }
  }

  /**
   * Like {@link #setVariableValue(String, double, String)} but first runs the {@link WriteValidatorRegistry} against
   * the proposed write. When validation returns {@link WriteValidationResult.Severity#ERROR ERROR} the write is
   * rejected with an {@link IllegalArgumentException} and the simulation is left unchanged.
   *
   * @param address the dot-notation address
   * @param value the value to set
   * @param unitOfMeasure the unit of measure, or null for the default
   * @return the validation result (always non-null; {@link WriteValidationResult.Severity#OK OK} or
   * {@link WriteValidationResult.Severity#WARNING WARNING})
   * @throws IllegalArgumentException if validation fails or the address cannot be resolved
   */
  public WriteValidationResult setVariableValueValidated(String address, double value, String unitOfMeasure) {
    ProcessEquipmentInterface eq = tryResolveEquipment(address);
    WriteValidationResult vr = WriteValidationResult.ok();
    if (eq != null) {
      String propertyPath = extractPropertyPath(address);
      vr = validatorRegistry.validate(eq, propertyPath, value, unitOfMeasure);
      if (!vr.isAllowed()) {
        throw new IllegalArgumentException("Write rejected by validator [" + vr.getCode() + "]: " + vr.getMessage());
      }
    }
    setVariableValue(address, value, unitOfMeasure);
    return vr;
  }

  /**
   * Applies many input writes atomically: validates every write up-front, snapshots the current values, applies the
   * writes, runs the simulation, and rolls back to the snapshot when any phase fails.
   *
   * <p>
   * Rollback semantics:
   * </p>
   * <ul>
   * <li><strong>Validation failure</strong> — if any validator returns {@link WriteValidationResult.Severity#ERROR
   * ERROR}, the batch is rejected without touching the simulation and
   * {@link TransactionalBatchResult#getRollbackCategory()} returns
   * {@link TransactionalBatchResult.RollbackCategory#VALIDATION_FAILED VALIDATION_FAILED}.</li>
   * <li><strong>Apply failure</strong> — if a write throws when applied, the snapshot is restored and the simulation is
   * re-run to coherence; {@link TransactionalBatchResult.RollbackCategory#APPLY_FAILED APPLY_FAILED} is returned.</li>
   * <li><strong>Run failure</strong> — if the writes apply cleanly but {@link #run()} throws, the snapshot is restored
   * and re-run; {@link TransactionalBatchResult.RollbackCategory#RUN_FAILED RUN_FAILED} is returned.</li>
   * </ul>
   *
   * <p>
   * Previous values that cannot be read (typically because the variable is INPUT-only and was not previously set) are
   * recorded as {@code null} in the per-write outcome and skipped during rollback. For best results, ensure inputs have
   * been set at least once before relying on transactional rollback.
   * </p>
   *
   * @param updates ordered map from address to value
   * @param unitOfMeasure unit applied to every update, or null for defaults
   * @return the {@link TransactionalBatchResult}; never null
   * @throws IllegalArgumentException if {@code updates} is null
   */
  public TransactionalBatchResult setValuesTransactional(Map<String, Double> updates, String unitOfMeasure) {
    if (updates == null) {
      throw new IllegalArgumentException("updates must not be null");
    }

    // Phase 0 — snapshot + validate
    List<TransactionalBatchResult.WriteOutcome> outcomes = new ArrayList<TransactionalBatchResult.WriteOutcome>(
        updates.size());
    boolean anyValidationError = false;
    String firstValidationFailure = null;
    for (Map.Entry<String, Double> e : updates.entrySet()) {
      String address = e.getKey();
      double value = e.getValue();
      Double previous = tryReadValue(address, unitOfMeasure);
      ProcessEquipmentInterface eq = tryResolveEquipment(address);
      WriteValidationResult vr = WriteValidationResult.ok();
      if (eq != null) {
        vr = validatorRegistry.validate(eq, extractPropertyPath(address), value, unitOfMeasure);
      }
      outcomes.add(new TransactionalBatchResult.WriteOutcome(address, value, unitOfMeasure, previous, vr, false, null));
      if (!vr.isAllowed() && !anyValidationError) {
        anyValidationError = true;
        firstValidationFailure = address + " — " + vr.getCode() + ": " + vr.getMessage();
      }
    }
    if (anyValidationError) {
      return TransactionalBatchResult.rolledBack(TransactionalBatchResult.RollbackCategory.VALIDATION_FAILED,
          "Validation failed: " + firstValidationFailure, outcomes);
    }

    // Phase 1 — apply
    List<TransactionalBatchResult.WriteOutcome> applied = new ArrayList<TransactionalBatchResult.WriteOutcome>(
        outcomes.size());
    int idx = 0;
    String applyError = null;
    String applyErrorAddress = null;
    for (Map.Entry<String, Double> e : updates.entrySet()) {
      String address = e.getKey();
      double value = e.getValue();
      TransactionalBatchResult.WriteOutcome existing = outcomes.get(idx++);
      try {
        setVariableValue(address, value, unitOfMeasure);
        applied.add(new TransactionalBatchResult.WriteOutcome(address, value, unitOfMeasure,
            existing.getPreviousValue(), existing.getValidation(), true, null));
      } catch (Exception ex) {
        applyError = ex.getMessage();
        applyErrorAddress = address;
        applied.add(new TransactionalBatchResult.WriteOutcome(address, value, unitOfMeasure,
            existing.getPreviousValue(), existing.getValidation(), false, ex.getMessage()));
        break;
      }
    }
    // Carry over any not-yet-attempted outcomes so the result lists every requested write
    while (applied.size() < outcomes.size()) {
      applied.add(outcomes.get(applied.size()));
    }

    if (applyError != null) {
      rollbackSnapshot(applied, unitOfMeasure);
      return TransactionalBatchResult.rolledBack(TransactionalBatchResult.RollbackCategory.APPLY_FAILED,
          "Apply failed at " + applyErrorAddress + ": " + applyError, applied);
    }

    // Phase 2 — run
    try {
      run();
    } catch (Exception ex) {
      String runError = ex.getMessage();
      rollbackSnapshot(applied, unitOfMeasure);
      return TransactionalBatchResult.rolledBack(TransactionalBatchResult.RollbackCategory.RUN_FAILED,
          "Run failed after applying writes: " + runError, applied);
    }

    return TransactionalBatchResult.committed(applied);
  }

  /**
   * Restores values from a snapshot stored in {@link TransactionalBatchResult.WriteOutcome} entries and re-runs the
   * simulation to leave it in a coherent state. Values whose snapshot is {@code null} are skipped. Restore-time errors
   * are logged through the diagnostics but never thrown.
   *
   * @param snapshot the snapshot entries from the failed batch
   * @param unitOfMeasure the unit of measure used for the original writes
   */
  private void rollbackSnapshot(List<TransactionalBatchResult.WriteOutcome> snapshot, String unitOfMeasure) {
    for (TransactionalBatchResult.WriteOutcome wo : snapshot) {
      if (!wo.isApplied()) {
        continue;
      }
      Double prev = wo.getPreviousValue();
      if (prev == null) {
        continue;
      }
      try {
        setVariableValue(wo.getAddress(), prev.doubleValue(), unitOfMeasure);
      } catch (Exception ex) {
        diagnostics.recordFailure("rollback", wo.getAddress(),
            AutomationDiagnostics.ErrorCategory.INVALID_ADDRESS_FORMAT, null);
      }
    }
    try {
      run();
    } catch (Exception ex) {
      diagnostics.recordFailure("rollback-run", "*", AutomationDiagnostics.ErrorCategory.CONVERGENCE_FAILURE, null);
    }
  }

  // ----------------------------- Snapshot ------------------------------

  /**
   * Returns a JSON snapshot of all variables for a unit, an area, or the entire process. Useful for agent observation
   * and for building model-vs-plant comparisons.
   *
   * @param scope unit name (e.g. {@code "HP Sep"}), area-qualified unit name (e.g. {@code "Separation::HP Sep"}), area
   * name (e.g. {@code "Separation"}), or {@code "*"} / {@code null} for the whole process
   * @return JSON string {@code {schemaVersion, scope, units:[{name, area, type, variables:{...}}]}}
   */
  public String snapshot(String scope) {
    com.google.gson.JsonObject root = new com.google.gson.JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);
    root.addProperty("scope", scope == null ? "*" : scope);
    root.addProperty("dirty", dirty);
    com.google.gson.JsonArray unitsArr = new com.google.gson.JsonArray();

    List<String> targetUnits = resolveScope(scope);
    for (String unitAddr : targetUnits) {
      com.google.gson.JsonObject u = new com.google.gson.JsonObject();
      u.addProperty("name", unitAddr);
      try {
        u.addProperty("type", getEquipmentType(unitAddr));
      } catch (Exception e) {
        // skip type if unresolvable
      }
      com.google.gson.JsonObject vars = new com.google.gson.JsonObject();
      try {
        List<SimulationVariable> vlist = getVariableList(unitAddr);
        for (SimulationVariable v : vlist) {
          try {
            double val = getVariableValue(v.getAddress(), v.getDefaultUnit());
            com.google.gson.JsonObject vobj = new com.google.gson.JsonObject();
            vobj.addProperty("value", val);
            if (v.getDefaultUnit() != null) {
              vobj.addProperty("unit", v.getDefaultUnit());
            }
            vobj.addProperty("type", v.getType().name());
            vars.add(stripUnitPrefix(v.getAddress(), unitAddr), vobj);
          } catch (Exception e) {
            // skip individual variable read failures
          }
        }
      } catch (Exception e) {
        // skip unit
      }
      u.add("variables", vars);
      unitsArr.add(u);
    }
    root.add("units", unitsArr);
    return root.toString();
  }

  /**
   * Returns a stable, side-effect-free JSON utilization snapshot of every unit in the flowsheet.
   *
   * <p>
   * This is the recommended observation endpoint for machine-learning / reinforcement-learning optimization loops.
   * Whereas {@link #snapshot(String)} reports raw process variables, this method reports capacity <i>utilization</i>
   * &mdash; for every unit the maximum constraint utilization, the limiting constraint, a per-constraint breakdown,
   * feasibility, and (for compressors and pumps) shaft power, plus the plant-wide {@code bottleneck} and
   * {@code anyOverloaded} flags. Pair it with
   * {@link #evaluate(java.util.Map, String, java.util.List, String, int, double) evaluate(...)} (action + reward) to
   * close an optimization loop: {@code evaluate} applies setpoints and runs the model; {@code getUtilizationSnapshot}
   * reads back the resulting capacity observation.
   * </p>
   *
   * <p>
   * The method does <b>not</b> run the model; call {@link #evaluate} or {@link #run()} first so the reported
   * utilization reflects the latest setpoints. For a multi-area model each unit entry carries an {@code "area"}
   * property.
   * </p>
   *
   * @return JSON string {@code {schemaVersion, units:[...], bottleneck:{...}, anyOverloaded, anyHardLimitExceeded}}
   */
  public String getUtilizationSnapshot() {
    if (processModel != null) {
      return processModel.getUtilizationSnapshotJson();
    }
    return processSystem.getUtilizationSnapshotJson();
  }

  // ---------------- Agentic optimization: prepare / validate / rank / quality ----------------

  /**
   * Enables the gas-load capacity constraint on every separator (these are disabled by default for backward
   * compatibility) so that {@link #getUtilizationSnapshot()} reports separator gas-load utilisation. Works for both a
   * {@link ProcessSystem} and a multi-area {@link ProcessModel}.
   *
   * @return the number of separators whose gas-load capacity constraint was enabled
   */
  public int enableCapacityConstraints() {
    int separators = 0;
    for (String addr : getUnitList()) {
      ProcessEquipmentInterface u;
      try {
        u = resolveUnit(addr).unit;
      } catch (RuntimeException ex) {
        continue;
      }
      try {
        if (u instanceof Separator) {
          // Creates + enables the Souders-Brown gas-load constraint, which is not part of
          // the generic default constraint set.
          ((Separator) u).useGasCapacityConstraints();
          separators++;
        } else if (u instanceof Compressor) {
          // Recreate constraints so surge/speed constraints stay DISABLED for chartless
          // compressors (they would otherwise pin utilisation at a degenerate 100 %), while
          // the power constraint stays enabled when a driver / design power is set.
          ((Compressor) u).reinitializeCapacityConstraints();
          ((CapacityConstrainedEquipment) u).setCapacityAnalysisEnabled(true);
        } else if (u instanceof CapacityConstrainedEquipment) {
          // Pumps, valves, pipelines, heaters/coolers, heat exchangers, manifolds, ... have
          // their constraints disabled by default for backward compatibility. Enable them so
          // every equipment type can bind in a capacity / optimization study.
          CapacityConstrainedEquipment eq = (CapacityConstrainedEquipment) u;
          eq.setCapacityAnalysisEnabled(true);
          eq.enableAllConstraints();
        }
      } catch (RuntimeException ex) {
        // skip units that cannot be configured
      }
    }
    return separators;
  }

  /**
   * Enables capacity constraints and returns a readiness report for a capacity / optimization study. Works for both a
   * {@link ProcessSystem} and a {@link ProcessModel}.
   *
   * @return JSON {@code {schemaVersion, separatorsEnabled, ready, issueCount, issues:[{unit,type,issue,severity,
   * remedy}]}}
   */
  public String prepareForCapacityStudyJson() {
    int enabled = enableCapacityConstraints();
    com.google.gson.JsonArray issues = collectOptimizationIssues();
    com.google.gson.JsonObject root = new com.google.gson.JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);
    root.addProperty("separatorsEnabled", enabled);
    root.addProperty("issueCount", issues.size());
    root.addProperty("ready", issues.size() == 0);
    root.add("issues", issues);
    return root.toString();
  }

  /**
   * Validates the model for an agentic optimization loop <b>without changing it</b>. Flags conditions that make an
   * optimizer misbehave: separators without geometry (capacity cannot be evaluated), compressors without a performance
   * chart (surge / power constraints undefined), and adjustable parameters with unbounded limits. Works for both a
   * {@link ProcessSystem} and a {@link ProcessModel}.
   *
   * @return JSON {@code {schemaVersion, ok, issueCount, issues:[{unit,type,issue,severity,remedy}]}}
   */
  public String validateForOptimizationJson() {
    com.google.gson.JsonArray issues = collectOptimizationIssues();
    com.google.gson.JsonObject root = new com.google.gson.JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);
    root.addProperty("issueCount", issues.size());
    root.addProperty("ok", issues.size() == 0);
    root.add("issues", issues);
    return root.toString();
  }

  /**
   * Collects optimization-readiness issues across all units (both process types).
   *
   * @return a JSON array of issue objects
   */
  private com.google.gson.JsonArray collectOptimizationIssues() {
    com.google.gson.JsonArray issues = new com.google.gson.JsonArray();
    for (String addr : getUnitList()) {
      ProcessEquipmentInterface u;
      try {
        u = resolveUnit(addr).unit;
      } catch (RuntimeException ex) {
        continue;
      }
      if (u instanceof Separator) {
        boolean hasGeom;
        try {
          hasGeom = ((Separator) u).hasGeometry();
        } catch (RuntimeException ex) {
          hasGeom = true;
        }
        if (!hasGeom) {
          addIssue(issues, addr, "Separator", "No internal diameter set; gas-load capacity cannot be evaluated.",
              "warning", "Call setInternalDiameter(...) (and setOrientation for scrubbers).");
        }
      } else if (u instanceof Compressor) {
        boolean hasChart;
        try {
          hasChart = ((Compressor) u).getCompressorChart() != null
              && ((Compressor) u).getCompressorChart().isUseCompressorChart();
        } catch (RuntimeException ex) {
          hasChart = false;
        }
        if (!hasChart) {
          addIssue(issues, addr, "Compressor", "No performance chart in use; surge / speed constraints are undefined.",
              "info", "Attach a chart or set getMechanicalDesign().setMaxDesignPower(kW) for a power constraint.");
        }
      }
    }
    // Unbounded adjustable parameters.
    for (AdjustableParameter p : getAdjustableParameters()) {
      Double lo = p.getLowerBound();
      Double hi = p.getUpperBound();
      boolean unbounded = lo == null || hi == null || Math.abs(lo) >= UNBOUNDED_THRESHOLD
          || Math.abs(hi) >= UNBOUNDED_THRESHOLD;
      if (unbounded) {
        addIssue(issues, p.getName(), "AdjustableParameter",
            "Decision variable has unbounded limits; supply physically meaningful bounds before optimizing.", "warning",
            "Set a finite [lower, upper] for " + p.getAddress() + ".");
      }
    }
    return issues;
  }

  /**
   * Appends an issue object to the issues array.
   *
   * @param issues the array to append to
   * @param unit the unit or parameter name/address
   * @param type the equipment or parameter type
   * @param issue the human-readable issue description
   * @param severity {@code "warning"} or {@code "info"}
   * @param remedy the suggested remedy
   */
  private void addIssue(com.google.gson.JsonArray issues, String unit, String type, String issue, String severity,
      String remedy) {
    com.google.gson.JsonObject o = new com.google.gson.JsonObject();
    o.addProperty("unit", unit);
    o.addProperty("type", type);
    o.addProperty("issue", issue);
    o.addProperty("severity", severity);
    o.addProperty("remedy", remedy);
    issues.add(o);
  }

  /**
   * Returns the top-{@code n} units ranked by capacity utilisation, i.e. a debottlenecking order, parsed from
   * {@link #getUtilizationSnapshot()}. Works for both a {@link ProcessSystem} and a {@link ProcessModel}.
   *
   * @param topN the maximum number of units to return (values &lt; 1 default to 1)
   * @return JSON {@code {schemaVersion, count, ranking:[{rank,name,area,type,utilizationPercent,limitingConstraint,
   * feasible}]}}
   */
  public String getBottleneckRankingJson(int topN) {
    int limit = topN < 1 ? 1 : topN;
    com.google.gson.JsonObject root = new com.google.gson.JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);
    com.google.gson.JsonArray ranking = new com.google.gson.JsonArray();
    try {
      com.google.gson.JsonObject snap = com.google.gson.JsonParser.parseString(getUtilizationSnapshot())
          .getAsJsonObject();
      List<com.google.gson.JsonObject> units = new ArrayList<com.google.gson.JsonObject>();
      if (snap.has("units") && snap.get("units").isJsonArray()) {
        for (com.google.gson.JsonElement el : snap.getAsJsonArray("units")) {
          if (el.isJsonObject() && el.getAsJsonObject().has("maxUtilizationPercent")) {
            units.add(el.getAsJsonObject());
          }
        }
      }
      units.sort(
          (a, b) -> Double.compare(readDouble(b, "maxUtilizationPercent"), readDouble(a, "maxUtilizationPercent")));
      int rank = 1;
      for (com.google.gson.JsonObject uo : units) {
        if (rank > limit) {
          break;
        }
        com.google.gson.JsonObject r = new com.google.gson.JsonObject();
        r.addProperty("rank", rank);
        r.addProperty("name", uo.has("name") ? uo.get("name").getAsString() : "");
        if (uo.has("area")) {
          r.addProperty("area", uo.get("area").getAsString());
        }
        if (uo.has("type")) {
          r.addProperty("type", uo.get("type").getAsString());
        }
        r.addProperty("utilizationPercent", readDouble(uo, "maxUtilizationPercent"));
        if (uo.has("limitingConstraint") && !uo.get("limitingConstraint").isJsonNull()) {
          r.addProperty("limitingConstraint", uo.get("limitingConstraint").getAsString());
        }
        if (uo.has("feasible")) {
          r.addProperty("feasible", uo.get("feasible").getAsBoolean());
        }
        ranking.add(r);
        rank++;
      }
    } catch (RuntimeException ex) {
      root.addProperty("error", ex.getMessage());
    }
    root.addProperty("count", ranking.size());
    root.add("ranking", ranking);
    return root.toString();
  }

  /**
   * Reads a numeric property from a JSON object, returning 0.0 when absent or non-numeric.
   *
   * @param obj JSON object
   * @param key property key
   * @return the value, or 0.0
   */
  private static double readDouble(com.google.gson.JsonObject obj, String key) {
    try {
      return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsDouble() : 0.0;
    } catch (RuntimeException ex) {
      return 0.0;
    }
  }

  /**
   * Resolves a stream from an address: a stream unit name, an area-qualified stream name, or a {@code unit.port}
   * reference; a bare unit name falls back to that unit's first outlet stream.
   *
   * @param address the stream or unit address
   * @return the resolved stream
   * @throws IllegalArgumentException if the address cannot be resolved to a stream
   */
  private StreamInterface resolveStreamFromAddress(String address) {
    if (address == null || address.trim().isEmpty()) {
      throw new IllegalArgumentException("address must not be null or empty");
    }
    String areaPrefix = "";
    String local = address;
    int areaIdx = address.indexOf(AREA_SEPARATOR);
    if (areaIdx >= 0) {
      areaPrefix = address.substring(0, areaIdx + AREA_SEPARATOR.length());
      local = address.substring(areaIdx + AREA_SEPARATOR.length());
    }
    int dot = local.indexOf('.');
    if (dot < 0) {
      ProcessEquipmentInterface u = resolveUnit(address).unit;
      if (u instanceof StreamInterface) {
        return (StreamInterface) u;
      }
      List<StreamInterface> outs = u.getOutletStreams();
      if (outs != null && !outs.isEmpty()) {
        return outs.get(0);
      }
      throw new IllegalArgumentException("No outlet stream available for unit: " + address);
    }
    ProcessEquipmentInterface u = resolveUnit(areaPrefix + local.substring(0, dot)).unit;
    StreamInterface s = resolveStreamPort(u, local.substring(dot + 1));
    if (s == null) {
      throw new IllegalArgumentException("Stream port not found: " + local.substring(dot + 1));
    }
    return s;
  }

  /**
   * Computes product-quality specs for the fluid at a stream address, at the default ASTM reference temperature of 37.8
   * degC. See {@link #getProductQualityJson(String, double)}.
   *
   * @param address a stream address (stream unit name, area-qualified name, or {@code unit.port})
   * @return JSON with RVP, TVP, cricondenbar and cricondentherm
   */
  public String getProductQualityJson(String address) {
    return getProductQualityJson(address, 37.8);
  }

  /**
   * Computes string-addressable product-quality observables for the fluid at a stream address: Reid vapour pressure
   * (ASTM D6377), true vapour pressure, and the phase-envelope cricondenbar / cricondentherm. Failures in any one
   * metric are reported as an {@code *Error} field rather than throwing, so an agent can use the result as a constraint
   * read. Works for both a {@link ProcessSystem} and a {@link ProcessModel}.
   *
   * @param address a stream address (stream unit name, area-qualified name, or {@code unit.port})
   * @param referenceTemperatureC the ASTM reference temperature in degrees Celsius (typically 37.8)
   * @return JSON {@code {schemaVersion, address, rvp_bara, tvp_bara, referenceTemperatureC, cricondenbar_bara,
   * cricondentherm_K}}
   */
  public String getProductQualityJson(String address, double referenceTemperatureC) {
    com.google.gson.JsonObject root = new com.google.gson.JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);
    root.addProperty("address", address);
    neqsim.thermo.system.SystemInterface fluid;
    try {
      fluid = resolveStreamFromAddress(address).getThermoSystem();
    } catch (RuntimeException ex) {
      root.addProperty("error", ex.getMessage());
      return root.toString();
    }
    // RVP + TVP (ASTM D6377). calculate() mutates the system, so clone first.
    try {
      neqsim.thermo.system.SystemInterface f1 = fluid.clone();
      neqsim.standards.oilquality.Standard_ASTM_D6377 std = new neqsim.standards.oilquality.Standard_ASTM_D6377(f1);
      std.setReferenceTemperature(referenceTemperatureC, "C");
      std.setMethodRVP("RVP_ASTM_D6377");
      std.calculate();
      root.addProperty("rvp_bara", std.getValue("RVP", "bara"));
      root.addProperty("tvp_bara", std.getValue("TVP", "bara"));
      root.addProperty("referenceTemperatureC", referenceTemperatureC);
    } catch (Exception ex) {
      root.addProperty("rvpError", ex.getMessage());
    }
    // Cricondenbar / cricondentherm from the PT phase envelope (water removed).
    try {
      neqsim.thermo.system.SystemInterface g = fluid.clone();
      if (g.hasComponent("water")) {
        g.removeComponent("water");
      }
      g.setMixingRule("classic");
      g.setMultiPhaseCheck(false);
      neqsim.thermodynamicoperations.ThermodynamicOperations ops = new neqsim.thermodynamicoperations.ThermodynamicOperations(
          g);
      ops.calcPTphaseEnvelope();
      double[] ccb = ops.getOperation().get("cricondenbar");
      double[] cct = ops.getOperation().get("cricondentherm");
      if (ccb != null && ccb.length > 1) {
        root.addProperty("cricondenbar_bara", ccb[1]);
      }
      if (cct != null && cct.length > 0) {
        root.addProperty("cricondentherm_K", cct[0]);
      }
    } catch (Exception ex) {
      root.addProperty("envelopeError", ex.getMessage());
    }
    return root.toString();
  }

  /**
   * Finds the maximum total feed throughput at which no unit exceeds its capacity limit, by scaling the named feed
   * streams proportionally to their base rates and reading the capacity {@link #getUtilizationSnapshot() snapshot}.
   * This is the native, both-process-type form of a max-throughput debottlenecking study. Capacity constraints are
   * enabled first via {@link #enableCapacityConstraints()}. The search is a bisection assuming utilisation rises with
   * throughput. Works for both a {@link ProcessSystem} and a {@link ProcessModel}.
   *
   * @param feedAddresses feed stream addresses (stream unit names or area-qualified names) to scale together
   * @param minRate lower bound of the TOTAL feed rate search, in {@code rateUnit}
   * @param maxRate upper bound of the TOTAL feed rate search, in {@code rateUnit}
   * @param rateUnit the flow-rate unit (e.g. {@code "kg/hr"}); null or empty defaults to {@code "kg/hr"}
   * @param utilizationLimit the maximum allowed unit utilisation as a fraction (e.g. 1.0 for 100 %); values &le; 0
   * default to 1.0
   * @return JSON {@code {schemaVersion, maxRate, rateUnit, feasibleAtMin, bindingUnit, bindingConstraint,
   * bindingUtilizationPercent}}; the model is left scaled to the returned maxRate
   */
  public String findMaxThroughputJson(List<String> feedAddresses, double minRate, double maxRate, String rateUnit,
      double utilizationLimit) {
    com.google.gson.JsonObject root = new com.google.gson.JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);
    if (feedAddresses == null || feedAddresses.isEmpty()) {
      throw new IllegalArgumentException("At least one feed address is required");
    }
    if (maxRate <= minRate) {
      throw new IllegalArgumentException("maxRate must be greater than minRate");
    }
    final String unit = rateUnit == null || rateUnit.trim().isEmpty() ? "kg/hr" : rateUnit;
    final double limit = utilizationLimit <= 0.0 ? 1.0 : utilizationLimit;

    final List<StreamInterface> feeds = new ArrayList<StreamInterface>();
    for (String a : feedAddresses) {
      ProcessEquipmentInterface u = resolveUnit(a).unit;
      if (!(u instanceof StreamInterface)) {
        throw new IllegalArgumentException("Not a feed stream: " + a);
      }
      feeds.add((StreamInterface) u);
    }
    final double[] baseRates = new double[feeds.size()];
    double baseTotalTmp = 0.0;
    for (int i = 0; i < feeds.size(); i++) {
      baseRates[i] = feeds.get(i).getFlowRate(unit);
      baseTotalTmp += baseRates[i];
    }
    final double baseTotal = baseTotalTmp;
    if (baseTotal <= 0.0) {
      throw new IllegalArgumentException("Total base feed rate must be positive to scale feeds proportionally");
    }

    enableCapacityConstraints();

    // Bisection: find the highest total rate with max utilisation <= limit.
    double lo = minRate;
    double hi = maxRate;
    boolean feasibleAtMin = evaluateThroughputFeasible(feeds, baseRates, baseTotal, unit, minRate, limit);
    if (!feasibleAtMin) {
      // Already over capacity even at the minimum rate.
      applyTotalRate(feeds, baseRates, baseTotal, unit, minRate);
      run();
      com.google.gson.JsonObject b = bottleneckFromSnapshot();
      root.addProperty("maxRate", minRate);
      root.addProperty("rateUnit", unit);
      root.addProperty("feasibleAtMin", false);
      mergeBottleneck(root, b);
      return root.toString();
    }
    if (evaluateThroughputFeasible(feeds, baseRates, baseTotal, unit, maxRate, limit)) {
      lo = maxRate; // whole range feasible
    } else {
      for (int iter = 0; iter < 20 && (hi - lo) / Math.max(baseTotal, 1.0) > 1.0e-3; iter++) {
        double mid = 0.5 * (lo + hi);
        if (evaluateThroughputFeasible(feeds, baseRates, baseTotal, unit, mid, limit)) {
          lo = mid;
        } else {
          hi = mid;
        }
      }
    }
    // Leave the model scaled to the feasible maximum and report the binding unit there.
    applyTotalRate(feeds, baseRates, baseTotal, unit, lo);
    run();
    com.google.gson.JsonObject b = bottleneckFromSnapshot();
    root.addProperty("maxRate", lo);
    root.addProperty("rateUnit", unit);
    root.addProperty("feasibleAtMin", true);
    mergeBottleneck(root, b);
    return root.toString();
  }

  /**
   * Scales all feeds to a target total rate and returns whether the converged model stays within the utilisation limit.
   *
   * @param feeds the feed streams
   * @param baseRates each feed's base rate
   * @param baseTotal the base total rate
   * @param unit the flow-rate unit
   * @param targetTotal the target total rate
   * @param limit the utilisation limit (fraction)
   * @return true when the run converged and the maximum unit utilisation is within the limit
   */
  private boolean evaluateThroughputFeasible(List<StreamInterface> feeds, double[] baseRates, double baseTotal,
      String unit, double targetTotal, double limit) {
    applyTotalRate(feeds, baseRates, baseTotal, unit, targetTotal);
    try {
      run();
    } catch (RuntimeException ex) {
      return false;
    }
    try {
      com.google.gson.JsonObject snap = com.google.gson.JsonParser.parseString(getUtilizationSnapshot())
          .getAsJsonObject();
      double maxUtil = 0.0;
      if (snap.has("units") && snap.get("units").isJsonArray()) {
        for (com.google.gson.JsonElement el : snap.getAsJsonArray("units")) {
          if (el.isJsonObject()) {
            double u = readDouble(el.getAsJsonObject(), "maxUtilization");
            if (u > maxUtil) {
              maxUtil = u;
            }
          }
        }
      }
      return maxUtil <= limit;
    } catch (RuntimeException ex) {
      return false;
    }
  }

  /**
   * Sets each feed to its base rate scaled by {@code targetTotal / baseTotal}.
   *
   * @param feeds the feed streams
   * @param baseRates each feed's base rate
   * @param baseTotal the base total rate
   * @param unit the flow-rate unit
   * @param targetTotal the target total rate
   */
  private void applyTotalRate(List<StreamInterface> feeds, double[] baseRates, double baseTotal, String unit,
      double targetTotal) {
    double factor = targetTotal / baseTotal;
    for (int i = 0; i < feeds.size(); i++) {
      feeds.get(i).setFlowRate(baseRates[i] * factor, unit);
    }
  }

  /**
   * Extracts the bottleneck unit object from the current utilisation snapshot.
   *
   * @return the bottleneck JSON object, or null when none
   */
  private com.google.gson.JsonObject bottleneckFromSnapshot() {
    try {
      com.google.gson.JsonObject snap = com.google.gson.JsonParser.parseString(getUtilizationSnapshot())
          .getAsJsonObject();
      if (snap.has("bottleneck") && snap.get("bottleneck").isJsonObject()) {
        return snap.getAsJsonObject("bottleneck");
      }
    } catch (RuntimeException ex) {
      // ignore
    }
    return null;
  }

  /**
   * Copies bottleneck name / constraint / utilisation from a bottleneck object into the result root.
   *
   * @param root the result object to populate
   * @param bottleneck the bottleneck object, or null
   */
  private void mergeBottleneck(com.google.gson.JsonObject root, com.google.gson.JsonObject bottleneck) {
    if (bottleneck == null) {
      return;
    }
    if (bottleneck.has("name")) {
      root.addProperty("bindingUnit", bottleneck.get("name").getAsString());
    }
    if (bottleneck.has("limitingConstraint") && !bottleneck.get("limitingConstraint").isJsonNull()) {
      root.addProperty("bindingConstraint", bottleneck.get("limitingConstraint").getAsString());
    }
    if (bottleneck.has("utilizationPercent")) {
      root.addProperty("bindingUtilizationPercent", readDouble(bottleneck, "utilizationPercent"));
    }
  }

  /**
   * Resolves a snapshot scope string to a list of (possibly area-qualified) unit names.
   *
   * @param scope unit, area, area::unit, "*", or null
   * @return list of unit addresses to include
   */
  private List<String> resolveScope(String scope) {
    if (scope == null || "*".equals(scope) || scope.trim().isEmpty()) {
      return getUnitList();
    }
    // Try as area name (multi-area only)
    if (processModel != null && !scope.contains(AREA_SEPARATOR) && !scope.contains(".")) {
      List<String> areas = processModel.getProcessSystemNames();
      if (areas.contains(scope)) {
        return getUnitList(scope);
      }
    }
    // Treat as a single unit address
    List<String> single = new ArrayList<String>(1);
    single.add(scope);
    return single;
  }

  /**
   * Removes a unit-name prefix from a variable address, leaving the property path.
   *
   * @param address full variable address
   * @param unitAddr unit prefix (possibly area-qualified)
   * @return the property portion of the address
   */
  private String stripUnitPrefix(String address, String unitAddr) {
    String localAddr = address;
    if (localAddr.startsWith(unitAddr + ".")) {
      return localAddr.substring(unitAddr.length() + 1);
    }
    return localAddr;
  }

  // ----------------------------- Describe / discovery ------------------------------

  /**
   * Returns a single JSON manifest describing the entire flowsheet schema: areas (if any), units with type and
   * variables, and stable schema version. This is the recommended single-tool-call discovery endpoint for LLM agents.
   *
   * @return JSON string with schema, areas, units, and variable descriptors
   */
  public String describe() {
    com.google.gson.JsonObject root = new com.google.gson.JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);
    root.addProperty("multiArea", isMultiArea());
    root.addProperty("dirty", dirty);

    if (isMultiArea()) {
      com.google.gson.JsonArray areasArr = new com.google.gson.JsonArray();
      for (String a : getAreaList()) {
        areasArr.add(a);
      }
      root.add("areas", areasArr);
    }

    com.google.gson.JsonArray unitsArr = new com.google.gson.JsonArray();
    for (String unitAddr : getUnitList()) {
      com.google.gson.JsonObject u = new com.google.gson.JsonObject();
      u.addProperty("name", unitAddr);
      try {
        u.addProperty("type", getEquipmentType(unitAddr));
      } catch (Exception e) {
        // ignore
      }
      com.google.gson.JsonArray varsArr = new com.google.gson.JsonArray();
      try {
        for (SimulationVariable v : getVariableList(unitAddr)) {
          com.google.gson.JsonObject vo = new com.google.gson.JsonObject();
          vo.addProperty("address", v.getAddress());
          vo.addProperty("name", v.getName());
          vo.addProperty("type", v.getType().name());
          if (v.getDefaultUnit() != null) {
            vo.addProperty("unit", v.getDefaultUnit());
          }
          if (v.getDescription() != null) {
            vo.addProperty("description", v.getDescription());
          }
          if (v.getUnitFamily() != null) {
            vo.addProperty("unitFamily", v.getUnitFamily());
          }
          if (v.getCategory() != null) {
            vo.addProperty("category", v.getCategory());
          }
          varsArr.add(vo);
        }
      } catch (Exception e) {
        // ignore unit
      }
      u.add("variables", varsArr);
      unitsArr.add(u);
    }
    root.add("units", unitsArr);
    return root.toString();
  }

  // ----------------------------- Topology / connections ------------------------------

  /**
   * Returns a JSON description of the flowsheet topology: equipment with their declared inlet and outlet streams, and
   * explicit {@link neqsim.process.processmodel.ProcessConnection ProcessConnection} edges when available.
   *
   * @return JSON string {@code {schemaVersion, equipment:[{name, type, inlets, outlets}], connections:[{source, target,
   * type, label}]}}
   */
  public String getTopology() {
    com.google.gson.JsonObject root = new com.google.gson.JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);
    com.google.gson.JsonArray equipArr = new com.google.gson.JsonArray();

    List<ProcessSystem> systems = new ArrayList<ProcessSystem>();
    if (processModel != null) {
      for (String a : processModel.getProcessSystemNames()) {
        systems.add(processModel.get(a));
      }
    } else {
      systems.add(processSystem);
    }

    for (ProcessSystem sys : systems) {
      for (ProcessEquipmentInterface unit : sys.getUnitOperations()) {
        com.google.gson.JsonObject u = new com.google.gson.JsonObject();
        u.addProperty("name", unit.getName());
        u.addProperty("type", unit.getClass().getSimpleName());
        com.google.gson.JsonArray inlets = new com.google.gson.JsonArray();
        com.google.gson.JsonArray outlets = new com.google.gson.JsonArray();
        try {
          for (StreamInterface s : unit.getInletStreams()) {
            if (s != null) {
              inlets.add(s.getName());
            }
          }
        } catch (Exception e) {
          // ignore - not all equipment exposes inlets
        }
        try {
          for (StreamInterface s : unit.getOutletStreams()) {
            if (s != null) {
              outlets.add(s.getName());
            }
          }
        } catch (Exception e) {
          // ignore
        }
        u.add("inlets", inlets);
        u.add("outlets", outlets);
        equipArr.add(u);
      }
    }
    root.add("equipment", equipArr);

    com.google.gson.JsonArray connsArr = new com.google.gson.JsonArray();
    try {
      List<neqsim.process.processmodel.ProcessConnection> conns = (processSystem != null)
          ? processSystem.getConnections()
          : null;
      if (conns != null) {
        for (neqsim.process.processmodel.ProcessConnection c : conns) {
          com.google.gson.JsonObject co = new com.google.gson.JsonObject();
          co.addProperty("source", c.getSourceEquipment());
          co.addProperty("target", c.getTargetEquipment());
          if (c.getType() != null) {
            co.addProperty("type", c.getType().name());
          }
          if (c.getSourcePort() != null) {
            co.addProperty("sourcePort", c.getSourcePort());
          }
          if (c.getTargetPort() != null) {
            co.addProperty("targetPort", c.getTargetPort());
          }
          connsArr.add(co);
        }
      }
    } catch (Exception e) {
      // ignore - connections are optional metadata
    }
    root.add("connections", connsArr);

    return root.toString();
  }

  /**
   * Returns the upstream and downstream neighbors of a unit, derived from its inlet and outlet streams. Useful for
   * multi-hop agent reasoning ("what feeds the HP separator?").
   *
   * @param unitName unit name (or area-qualified unit name in multi-area mode)
   * @return JSON string {@code {unit, upstream:[...], downstream:[...]}}
   * @throws IllegalArgumentException if the unit cannot be resolved
   */
  public String getNeighbors(String unitName) {
    String areaName = null;
    String localName = unitName;
    int areaSepIdx = unitName.indexOf(AREA_SEPARATOR);
    if (areaSepIdx >= 0) {
      areaName = unitName.substring(0, areaSepIdx);
      localName = unitName.substring(areaSepIdx + AREA_SEPARATOR.length());
    }
    ProcessEquipmentInterface target = findUnit(areaName, localName);

    java.util.Set<String> inletStreamNames = new java.util.LinkedHashSet<String>();
    java.util.Set<String> outletStreamNames = new java.util.LinkedHashSet<String>();
    try {
      for (StreamInterface s : target.getInletStreams()) {
        if (s != null) {
          inletStreamNames.add(s.getName());
        }
      }
    } catch (Exception e) {
      // ignore
    }
    try {
      for (StreamInterface s : target.getOutletStreams()) {
        if (s != null) {
          outletStreamNames.add(s.getName());
        }
      }
    } catch (Exception e) {
      // ignore
    }

    com.google.gson.JsonObject root = new com.google.gson.JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);
    root.addProperty("unit", unitName);
    com.google.gson.JsonArray up = new com.google.gson.JsonArray();
    com.google.gson.JsonArray down = new com.google.gson.JsonArray();

    List<ProcessSystem> systems = new ArrayList<ProcessSystem>();
    if (processModel != null) {
      for (String a : processModel.getProcessSystemNames()) {
        systems.add(processModel.get(a));
      }
    } else {
      systems.add(processSystem);
    }

    for (ProcessSystem sys : systems) {
      for (ProcessEquipmentInterface other : sys.getUnitOperations()) {
        if (other == target) {
          continue;
        }
        try {
          for (StreamInterface s : other.getOutletStreams()) {
            if (s != null && inletStreamNames.contains(s.getName())) {
              up.add(other.getName());
              break;
            }
          }
        } catch (Exception e) {
          // ignore
        }
        try {
          for (StreamInterface s : other.getInletStreams()) {
            if (s != null && outletStreamNames.contains(s.getName())) {
              down.add(other.getName());
              break;
            }
          }
        } catch (Exception e) {
          // ignore
        }
      }
    }
    root.add("upstream", up);
    root.add("downstream", down);
    return root.toString();
  }

  // ----------------------------- Structured / composition / vector access ------------------------

  /**
   * Returns a structured JSON element for a variable address. Unlike {@link #getVariableValue(String, String)} which is
   * scalar-only, this method supports vector and object-valued variables such as stream compositions, per-phase
   * properties, and K-values.
   *
   * <p>
   * <strong>Supported address suffixes</strong> (case-insensitive):
   * </p>
   * <ul>
   * <li>{@code <unit>.<stream>.composition} → object {component: moleFraction}</li>
   * <li>{@code <unit>.<stream>.molarComposition} → same as composition</li>
   * <li>{@code <unit>.<stream>.massComposition} → object {component: massFraction}</li>
   * <li>{@code <unit>.<stream>.components} → array of component names</li>
   * <li>{@code <unit>.<stream>.phaseFractions} → object {gas, oil, aqueous} mole fractions</li>
   * <li>{@code <unit>.<stream>.kvalues} → object {component: K}</li>
   * </ul>
   *
   * <p>
   * Any address not matching one of these patterns is delegated to {@link #getVariableValue(String, String)} and
   * wrapped as a JSON number.
   * </p>
   *
   * @param address the dot-notation address
   * @return Gson {@code JsonElement} (object, array, or primitive number)
   * @throws IllegalArgumentException if the address cannot be resolved
   */
  public com.google.gson.JsonElement getStructured(String address) {
    if (address == null || address.trim().isEmpty()) {
      throw new IllegalArgumentException("Address must not be null or empty");
    }
    String localAddress = address;
    String areaName = null;
    int areaSepIdx = address.indexOf(AREA_SEPARATOR);
    if (areaSepIdx >= 0) {
      areaName = address.substring(0, areaSepIdx);
      localAddress = address.substring(areaSepIdx + AREA_SEPARATOR.length());
    }
    String[] parts = localAddress.split("\\.", 3);
    if (parts.length == 3) {
      String last = parts[2].toLowerCase(java.util.Locale.ROOT);
      ProcessEquipmentInterface unit = findUnit(areaName, parts[0]);
      StreamInterface stream = resolveStreamPort(unit, parts[1]);
      if (stream == null) {
        throw new IllegalArgumentException("Stream port not found: " + parts[1] + " on unit " + parts[0]);
      }
      neqsim.thermo.system.SystemInterface fluid = stream.getFluid();
      if ("composition".equals(last) || "molarcomposition".equals(last)) {
        return compositionJson(fluid, false);
      }
      if ("masscomposition".equals(last)) {
        return compositionJson(fluid, true);
      }
      if ("components".equals(last)) {
        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
        for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
          arr.add(fluid.getComponent(i).getComponentName());
        }
        return arr;
      }
      if ("phasefractions".equals(last)) {
        com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
        for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
          obj.addProperty(fluid.getPhase(i).getPhaseTypeName(), fluid.getBeta(i));
        }
        return obj;
      }
      if ("kvalues".equals(last)) {
        com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
        if (fluid.getNumberOfPhases() >= 2) {
          for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
            double yi = fluid.getPhase(0).getComponent(i).getx();
            double xi = fluid.getPhase(1).getComponent(i).getx();
            obj.addProperty(fluid.getComponent(i).getComponentName(), xi > 0.0 ? yi / xi : Double.NaN);
          }
        }
        return obj;
      }
    }
    // Fallback to scalar
    double v = getVariableValue(address, null);
    return new com.google.gson.JsonPrimitive(v);
  }

  /**
   * Builds a {component → fraction} JSON object for a fluid.
   *
   * @param fluid the thermo system
   * @param mass if {@code true} returns mass fractions, otherwise mole fractions (overall)
   * @return JSON object
   */
  private com.google.gson.JsonObject compositionJson(neqsim.thermo.system.SystemInterface fluid, boolean mass) {
    com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
    double totalMoles = fluid.getTotalNumberOfMoles();
    double totalMass = 0.0;
    if (mass) {
      for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
        totalMass += fluid.getComponent(i).getNumberOfmoles() * fluid.getComponent(i).getMolarMass();
      }
    }
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      double frac;
      if (mass) {
        double m = fluid.getComponent(i).getNumberOfmoles() * fluid.getComponent(i).getMolarMass();
        frac = totalMass > 0.0 ? m / totalMass : 0.0;
      } else {
        frac = totalMoles > 0.0 ? fluid.getComponent(i).getNumberOfmoles() / totalMoles : 0.0;
      }
      obj.addProperty(fluid.getComponent(i).getComponentName(), frac);
    }
    return obj;
  }

  // ----------------------------- Address validation ------------------------------

  /**
   * Validates an address without throwing. Returns {@code null} if the address resolves, or a diagnostic describing why
   * it does not.
   *
   * @param address the dot-notation address
   * @return diagnostic on failure, {@code null} on success
   */
  public AutomationDiagnostics.DiagnosticResult validateAddress(String address) {
    if (address == null || address.trim().isEmpty()) {
      return new AutomationDiagnostics.DiagnosticResult(AutomationDiagnostics.ErrorCategory.INVALID_ADDRESS_FORMAT,
          address == null ? "" : address, "Address must not be null or empty", new ArrayList<String>(), null,
          "Pass a non-empty address of the form 'unit.property' or 'unit.port.property'.",
          new LinkedHashMap<String, Object>());
    }
    try {
      // Resolve unit (and stream port if present) without touching property
      String localAddress = address;
      String areaName = null;
      int areaSepIdx = address.indexOf(AREA_SEPARATOR);
      if (areaSepIdx >= 0) {
        areaName = address.substring(0, areaSepIdx);
        localAddress = address.substring(areaSepIdx + AREA_SEPARATOR.length());
      }
      String[] parts = localAddress.split("\\.", 3);
      ProcessEquipmentInterface unit = findUnit(areaName, parts[0]);
      if (parts.length == 3) {
        StreamInterface s = resolveStreamPort(unit, parts[1]);
        if (s == null) {
          return diagnoseAndAttemptRecovery(address,
              new IllegalArgumentException("Stream port not found: " + parts[1]));
        }
      }
      return null;
    } catch (IllegalArgumentException e) {
      return diagnoseAndAttemptRecovery(address, e);
    }
  }

  /**
   * Returns a list of unit-of-measure strings that are valid for a given address. For now this uses the variable's
   * {@link SimulationVariable#getUnitFamily() unit family} to suggest typical UOMs; agents can also pass {@code null}
   * to use the variable's default unit.
   *
   * @param address dot-notation address
   * @return ordered list of suggested UOM strings (may be empty if the unit family is unknown)
   */
  public List<String> getAllowedUnits(String address) {
    List<String> out = new ArrayList<String>();
    try {
      String localAddress = address;
      String areaName = null;
      int areaSepIdx = address.indexOf(AREA_SEPARATOR);
      if (areaSepIdx >= 0) {
        areaName = address.substring(0, areaSepIdx);
        localAddress = address.substring(areaSepIdx + AREA_SEPARATOR.length());
      }
      String unitName = localAddress.split("\\.", 2)[0];
      String prefix = (areaName != null ? areaName + AREA_SEPARATOR : "") + unitName;
      for (SimulationVariable v : getVariableList(prefix)) {
        if (v.getAddress().equals(address)) {
          String family = v.getUnitFamily();
          if (family == null) {
            return out;
          }
          if ("temperature".equalsIgnoreCase(family)) {
            out.add("K");
            out.add("C");
            out.add("F");
          } else if ("pressure".equalsIgnoreCase(family)) {
            out.add("bara");
            out.add("Pa");
            out.add("psi");
            out.add("barg");
          } else if ("massFlow".equalsIgnoreCase(family)) {
            out.add("kg/sec");
            out.add("kg/hr");
            out.add("tonnes/hr");
          } else if ("molarFlow".equalsIgnoreCase(family)) {
            out.add("mole/sec");
          } else if ("density".equalsIgnoreCase(family)) {
            out.add("kg/m3");
          } else if ("power".equalsIgnoreCase(family)) {
            out.add("W");
            out.add("kW");
            out.add("MW");
          } else if ("length".equalsIgnoreCase(family)) {
            out.add("m");
          } else if ("volume".equalsIgnoreCase(family)) {
            out.add("m3");
          } else if ("rotationalSpeed".equalsIgnoreCase(family)) {
            out.add("rpm");
          }
          return out;
        }
      }
    } catch (Exception e) {
      // fall through, return empty
    }
    return out;
  }
}
