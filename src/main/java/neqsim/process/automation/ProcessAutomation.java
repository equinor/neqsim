package neqsim.process.automation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import neqsim.process.automation.SimulationVariable.VariableType;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortEquipment;
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
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Provides a stable, string-addressable automation API for interacting with a running NeqSim
 * {@link ProcessSystem} or {@link ProcessModel}. Variables in the simulation are reachable through
 * stable dot-notation paths such as {@code "separator-1.gasOutStream.temperature"}, removing the
 * need to navigate Java objects directly.
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
 * <strong>Address format:</strong> {@code unitName.property} or
 * {@code unitName.streamPort.property}
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

  private final ProcessSystem processSystem;
  private final ProcessModel processModel;
  private final AutomationDiagnostics diagnostics;

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
   * Returns the diagnostics instance for this automation facade. The diagnostics provide fuzzy name
   * matching, auto-correction, physical value validation, and operation history tracking.
   *
   * @return the automation diagnostics
   */
  public AutomationDiagnostics getDiagnostics() {
    return diagnostics;
  }

  /**
   * Reads a variable value with self-healing: if the exact address fails, attempts auto-correction
   * via fuzzy matching against known unit names and variable addresses. Returns a JSON string with
   * the value on success, or a diagnostic result with suggestions on failure.
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
          return buildAutoCorrectedJson(address, diag.getAutoCorrection(), value, unitOfMeasure,
              diag);
        } catch (Exception retryEx) {
          // Auto-correction also failed
        }
      }
      diagnostics.recordFailure("get", address, diag.getCategory(), null);
      return diag.toJson();
    }
  }

  /**
   * Sets a variable value with self-healing: if the exact address fails, attempts auto-correction
   * via fuzzy matching. Also validates the value against physical bounds before setting.
   *
   * @param address the dot-notation address
   * @param value the value to set
   * @param unitOfMeasure the unit of the value
   * @return JSON result string with either success or diagnostic information
   */
  public String setVariableValueSafe(String address, double value, String unitOfMeasure) {
    // Pre-validate physical bounds
    String propertyName = extractPropertyName(address);
    AutomationDiagnostics.DiagnosticResult boundsCheck =
        diagnostics.validatePhysicalBounds(propertyName, value, unitOfMeasure);
    if (boundsCheck != null
        && boundsCheck.getCategory() == AutomationDiagnostics.ErrorCategory.VALUE_OUT_OF_BOUNDS
        && boundsCheck.getContext().containsKey("severity")
        && !"WARNING".equals(boundsCheck.getContext().get("severity"))) {
      diagnostics.recordFailure("set", address,
          AutomationDiagnostics.ErrorCategory.VALUE_OUT_OF_BOUNDS, null);
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
          return buildAutoCorrectedSetJson(address, diag.getAutoCorrection(), value, unitOfMeasure,
              diag);
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
      throw new IllegalStateException(
          "getAreaList() is only available when backed by a ProcessModel");
    }
    return Collections.unmodifiableList(processModel.getProcessSystemNames());
  }

  /**
   * Returns the names of all unit operations. When backed by a {@link ProcessModel}, returns
   * area-qualified names in the format {@code "AreaName::UnitName"}.
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
   * Returns the names of unit operations in a specific process area. Only available when backed by
   * a {@link ProcessModel}.
   *
   * @param areaName the name of the process area
   * @return unmodifiable list of unit operation names (without area prefix)
   * @throws IllegalStateException if backed by a single ProcessSystem
   * @throws IllegalArgumentException if the area is not found
   */
  public List<String> getUnitList(String areaName) {
    if (processModel == null) {
      throw new IllegalStateException(
          "getUnitList(areaName) is only available when backed by a ProcessModel");
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
   * When backed by a {@link ProcessModel}, the {@code unitName} may be area-qualified:
   * {@code "AreaName::UnitName"}.
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
   * Returns the simple class name (equipment type) of a unit operation. Useful for discovering what
   * kind of equipment a unit is, e.g. "Compressor", "Separator", "PipeBeggsAndBrills".
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
   * When backed by a {@link ProcessModel}, the address must be area-qualified:
   * {@code "AreaName::unitName.property"} or {@code "AreaName::unitName.streamPort.property"}.
   * </p>
   *
   * @param address the dot-notation address, e.g. "separator-1.gasOutStream.temperature" or
   *        "Separation::separator-1.gasOutStream.temperature"
   * @param unitOfMeasure the desired unit, e.g. "C", "bara", "kg/hr". Pass null or empty for
   *        default units
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
        throw new IllegalArgumentException(
            "Stream port not found: " + parts[1] + " on unit " + unitName);
      }
      return getStreamProperty(stream, parts[2], unitOfMeasure);
    } else {
      throw new IllegalArgumentException("Invalid address format: " + address
          + ". Expected 'unitName.property' or 'unitName.port.property'");
    }
  }

  /**
   * Sets the value of a simulation input variable. Only variables with {@link VariableType#INPUT
   * INPUT} type can be set.
   *
   * <p>
   * When backed by a {@link ProcessModel}, the address must be area-qualified:
   * {@code "AreaName::Compressor.outletPressure"}.
   * </p>
   *
   * @param address the dot-notation address, e.g. "Compressor.outletPressure" or
   *        "Compression::Compressor.outletPressure"
   * @param value the value to set
   * @param unitOfMeasure the unit of the provided value, e.g. "bara", "C". Pass null or empty for
   *        default units
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
        throw new IllegalArgumentException(
            "Stream port not found: " + parts[1] + " on unit " + unitName);
      }
      setStreamProperty(stream, parts[2], value, unitOfMeasure);
    } else {
      throw new IllegalArgumentException("Invalid address format: " + address
          + ". Expected 'unitName.property' or 'unitName.port.property'");
    }
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
            throw new IllegalArgumentException("Area not found: " + areaName
                + (suggestions.isEmpty() ? "" : ". Did you mean: " + suggestions + "?"));
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
            throw new IllegalArgumentException("Unit not found: " + unitName + " in area "
                + areaName + (suggestions.isEmpty() ? "" : ". Did you mean: " + suggestions + "?"));
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
        throw new IllegalArgumentException("Unit not found: " + unitName
            + (suggestions.isEmpty() ? "" : ". Did you mean: " + suggestions + "?"));
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
   * Searches all equipment in the relevant process system(s) for a matching reference designation.
   * This enables addressing equipment by their IEC 81346 codes, e.g. "=A1-B1" or "-K2".
   * </p>
   *
   * @param areaName the area name to search within (null to search all)
   * @param refDesString the reference designation string to match
   * @return the matching equipment, or null if not found
   */
  private ProcessEquipmentInterface findByReferenceDesignation(String areaName,
      String refDesString) {
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
  private List<SimulationVariable> buildVariableList(String unitName,
      ProcessEquipmentInterface unit) {
    List<SimulationVariable> vars = new ArrayList<SimulationVariable>();
    boolean handledOutlets = false;

    // Universal equipment-level outputs
    vars.add(new SimulationVariable(unitName + ".temperature", "temperature", VariableType.OUTPUT,
        "K", "Equipment temperature"));
    vars.add(new SimulationVariable(unitName + ".pressure", "pressure", VariableType.OUTPUT, "bara",
        "Equipment pressure"));

    // Stream-specific variables
    if (unit instanceof StreamInterface) {
      addStreamVariables(vars, unitName, (StreamInterface) unit, true);
      handledOutlets = true;
    }

    // Separator family (ThreePhaseSeparator before Separator since it extends Separator)
    if (unit instanceof ThreePhaseSeparator) {
      addStreamOutputVariables(vars, unitName + ".gasOutStream",
          ((ThreePhaseSeparator) unit).getGasOutStream());
      addStreamOutputVariables(vars, unitName + ".oilOutStream",
          ((ThreePhaseSeparator) unit).getOilOutStream());
      addStreamOutputVariables(vars, unitName + ".waterOutStream",
          ((ThreePhaseSeparator) unit).getWaterOutStream());
      handledOutlets = true;
    } else if (unit instanceof Separator) {
      addStreamOutputVariables(vars, unitName + ".gasOutStream",
          ((Separator) unit).getGasOutStream());
      addStreamOutputVariables(vars, unitName + ".liquidOutStream",
          ((Separator) unit).getLiquidOutStream());
      handledOutlets = true;
    }

    // Tank (gas/liquid outlets like separator)
    if (unit instanceof Tank) {
      vars.add(new SimulationVariable(unitName + ".liquidLevel", "liquidLevel", VariableType.OUTPUT,
          "", "Tank liquid level"));
      vars.add(new SimulationVariable(unitName + ".volume", "volume", VariableType.INPUT, "m3",
          "Tank volume"));
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
      vars.add(new SimulationVariable(unitName + ".outletPressure", "outletPressure",
          VariableType.INPUT, "bara", "Expander outlet pressure"));
      vars.add(new SimulationVariable(unitName + ".isentropicEfficiency", "isentropicEfficiency",
          VariableType.INPUT, "", "Isentropic efficiency (fraction)"));
      vars.add(new SimulationVariable(unitName + ".polytropicEfficiency", "polytropicEfficiency",
          VariableType.INPUT, "", "Polytropic efficiency (fraction)"));
      vars.add(new SimulationVariable(unitName + ".power", "power", VariableType.OUTPUT, "kW",
          "Expander power output"));
      addOutletStreamVariables(vars, unitName, unit);
      handledOutlets = true;
    }

    // CompressorTrain (check before Compressor since it doesn't extend Compressor)
    if (unit instanceof CompressorTrain) {
      vars.add(new SimulationVariable(unitName + ".power", "power", VariableType.OUTPUT, "kW",
          "Compressor train total power"));
      vars.add(new SimulationVariable(unitName + ".polytropicEfficiency", "polytropicEfficiency",
          VariableType.OUTPUT, "", "Overall polytropic efficiency"));
      addOutletStreamVariables(vars, unitName, unit);
      handledOutlets = true;
    }

    // Compressor (not Expander)
    if (unit instanceof Compressor && !(unit instanceof Expander)) {
      vars.add(new SimulationVariable(unitName + ".outletPressure", "outletPressure",
          VariableType.INPUT, "bara", "Compressor outlet pressure"));
      vars.add(new SimulationVariable(unitName + ".polytropicEfficiency", "polytropicEfficiency",
          VariableType.INPUT, "", "Polytropic efficiency (fraction)"));
      vars.add(new SimulationVariable(unitName + ".isentropicEfficiency", "isentropicEfficiency",
          VariableType.OUTPUT, "", "Isentropic efficiency (fraction)"));
      vars.add(new SimulationVariable(unitName + ".power", "power", VariableType.OUTPUT, "kW",
          "Compressor power consumption"));
      vars.add(new SimulationVariable(unitName + ".speed", "speed", VariableType.INPUT, "rpm",
          "Compressor speed"));
      vars.add(new SimulationVariable(unitName + ".polytropicHead", "polytropicHead",
          VariableType.OUTPUT, "kJ/kg", "Polytropic head"));
      vars.add(new SimulationVariable(unitName + ".compressionRatio", "compressionRatio",
          VariableType.OUTPUT, "", "Compression ratio"));
      addOutletStreamVariables(vars, unitName, unit);
      handledOutlets = true;
    }

    // Pump
    if (unit instanceof Pump) {
      vars.add(new SimulationVariable(unitName + ".outletPressure", "outletPressure",
          VariableType.INPUT, "bara", "Pump outlet pressure"));
      vars.add(new SimulationVariable(unitName + ".power", "power", VariableType.OUTPUT, "kW",
          "Pump power consumption"));
      vars.add(new SimulationVariable(unitName + ".isentropicEfficiency", "isentropicEfficiency",
          VariableType.INPUT, "", "Isentropic efficiency (fraction)"));
      vars.add(new SimulationVariable(unitName + ".speed", "speed", VariableType.INPUT, "rpm",
          "Pump speed"));
      addOutletStreamVariables(vars, unitName, unit);
      handledOutlets = true;
    }

    // Heat exchanger (HeatExchanger extends Heater, so check BEFORE Heater)
    if (unit instanceof HeatExchanger && !(unit instanceof Cooler)) {
      vars.add(new SimulationVariable(unitName + ".UAvalue", "UAvalue", VariableType.INPUT, "W/K",
          "Overall heat transfer coefficient times area"));
      vars.add(new SimulationVariable(unitName + ".duty", "duty", VariableType.OUTPUT, "W",
          "Heat exchanger duty"));
      vars.add(new SimulationVariable(unitName + ".thermalEffectiveness", "thermalEffectiveness",
          VariableType.OUTPUT, "", "Thermal effectiveness"));
      handledOutlets = true;
    }

    // Cooler
    if (unit instanceof Cooler) {
      vars.add(new SimulationVariable(unitName + ".outletTemperature", "outletTemperature",
          VariableType.INPUT, "C", "Cooler outlet temperature"));
      vars.add(new SimulationVariable(unitName + ".duty", "duty", VariableType.OUTPUT, "W",
          "Cooler duty"));
      addOutletStreamVariables(vars, unitName, unit);
      handledOutlets = true;
    }

    // Heater (not Cooler and not HeatExchanger)
    if (unit instanceof Heater && !(unit instanceof Cooler) && !(unit instanceof HeatExchanger)) {
      vars.add(new SimulationVariable(unitName + ".outletTemperature", "outletTemperature",
          VariableType.INPUT, "C", "Heater outlet temperature"));
      vars.add(new SimulationVariable(unitName + ".duty", "duty", VariableType.OUTPUT, "W",
          "Heater duty"));
      addOutletStreamVariables(vars, unitName, unit);
      handledOutlets = true;
    }

    // Valve
    if (unit instanceof ThrottlingValve) {
      vars.add(new SimulationVariable(unitName + ".outletPressure", "outletPressure",
          VariableType.INPUT, "bara", "Valve outlet pressure"));
      vars.add(new SimulationVariable(unitName + ".Cv", "Cv", VariableType.INPUT, "",
          "Valve flow coefficient"));
      vars.add(new SimulationVariable(unitName + ".percentValveOpening", "percentValveOpening",
          VariableType.INPUT, "%", "Valve opening percentage"));
      addOutletStreamVariables(vars, unitName, unit);
      handledOutlets = true;
    }

    // Pipeline (AdiabaticPipe, PipeBeggsAndBrills, etc.)
    if (unit instanceof Pipeline) {
      vars.add(new SimulationVariable(unitName + ".length", "length", VariableType.INPUT, "m",
          "Pipe length"));
      vars.add(new SimulationVariable(unitName + ".diameter", "diameter", VariableType.INPUT, "m",
          "Pipe inner diameter"));
      vars.add(new SimulationVariable(unitName + ".pipeWallRoughness", "pipeWallRoughness",
          VariableType.INPUT, "m", "Pipe wall roughness"));
        vars.add(new SimulationVariable(unitName + ".wallThickness", "wallThickness",
          VariableType.INPUT, "m", "Pipe wall thickness"));
        vars.add(new SimulationVariable(unitName + ".elevation", "elevation", VariableType.INPUT,
          "m", "Pipe elevation change from inlet to outlet"));
      vars.add(new SimulationVariable(unitName + ".pressureDrop", "pressureDrop",
          VariableType.OUTPUT, "bara", "Pressure drop across pipe"));
        if (unit instanceof WaterHammerPipe) {
        vars.add(new SimulationVariable(unitName + ".valveOpening", "valveOpening",
          VariableType.INPUT, "", "Water-hammer valve opening fraction"));
        vars.add(new SimulationVariable(unitName + ".valveOpeningPercent",
          "valveOpeningPercent", VariableType.INPUT, "%",
          "Water-hammer valve opening percentage"));
        vars.add(new SimulationVariable(unitName + ".waveSpeed", "waveSpeed",
          VariableType.INPUT, "m/s", "Acoustic wave speed override or calculated value"));
        vars.add(new SimulationVariable(unitName + ".numberOfNodes", "numberOfNodes",
          VariableType.INPUT, "", "Water-hammer computational node count"));
        vars.add(new SimulationVariable(unitName + ".courantNumber", "courantNumber",
          VariableType.INPUT, "", "Courant number for stable transient time steps"));
        vars.add(new SimulationVariable(unitName + ".maxStableTimeStep", "maxStableTimeStep",
          VariableType.OUTPUT, "s", "Maximum stable time step from the Courant limit"));
        vars.add(new SimulationVariable(unitName + ".waveRoundTripTime", "waveRoundTripTime",
          VariableType.OUTPUT, "s", "Pipe acoustic wave round-trip time"));
        vars.add(new SimulationVariable(unitName + ".maxPressure", "maxPressure",
          VariableType.OUTPUT, "bara", "Maximum pressure envelope during transient"));
        vars.add(new SimulationVariable(unitName + ".minPressure", "minPressure",
          VariableType.OUTPUT, "bara", "Minimum pressure envelope during transient"));
        }
      addOutletStreamVariables(vars, unitName, unit);
      handledOutlets = true;
    }

    // Ejector
    if (unit instanceof Ejector) {
      vars.add(new SimulationVariable(unitName + ".dischargePressure", "dischargePressure",
          VariableType.INPUT, "bara", "Ejector discharge pressure"));
      vars.add(new SimulationVariable(unitName + ".entrainmentRatio", "entrainmentRatio",
          VariableType.OUTPUT, "", "Ejector entrainment ratio"));
      vars.add(new SimulationVariable(unitName + ".efficiencyIsentropic", "efficiencyIsentropic",
          VariableType.INPUT, "", "Isentropic efficiency"));
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
      vars.add(new SimulationVariable(unitName + ".condenserRefluxRatio", "condenserRefluxRatio",
          VariableType.INPUT, "", "Condenser reflux ratio"));
      handledOutlets = true;
    }

    // Recycle
    if (unit instanceof Recycle) {
      vars.add(new SimulationVariable(unitName + ".errorTemperature", "errorTemperature",
          VariableType.OUTPUT, "", "Temperature convergence error"));
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

    // Splitter — output only (not ComponentSplitter)
    if (unit instanceof Splitter && !(unit instanceof ComponentSplitter)) {
      List<StreamInterface> splitStreams = unit.getOutletStreams();
      for (int i = 0; i < splitStreams.size(); i++) {
        addStreamOutputVariables(vars, unitName + ".splitStream_" + i, splitStreams.get(i));
      }
      handledOutlets = true;
    }

    // Generic fallback for any TwoPortEquipment not yet handled
    if (!handledOutlets && unit instanceof TwoPortEquipment) {
      addOutletStreamVariables(vars, unitName, unit);
    }

    return vars;
  }

  /**
   * Adds stream variables (temperature, pressure, flowRate) with appropriate INPUT/OUTPUT type.
   *
   * @param vars the list to add to
   * @param prefix the address prefix
   * @param stream the stream
   * @param isInput whether the stream properties are settable
   */
  private void addStreamVariables(List<SimulationVariable> vars, String prefix,
      StreamInterface stream, boolean isInput) {
    VariableType inputType = isInput ? VariableType.INPUT : VariableType.OUTPUT;
    vars.add(new SimulationVariable(prefix + ".temperature", "temperature", inputType, "K",
        "Stream temperature"));
    vars.add(new SimulationVariable(prefix + ".pressure", "pressure", inputType, "bara",
        "Stream pressure"));
    vars.add(new SimulationVariable(prefix + ".flowRate", "flowRate", inputType, "kg/hr",
        "Stream mass flow rate"));
    vars.add(new SimulationVariable(prefix + ".density", "density", VariableType.OUTPUT, "kg/m3",
        "Stream density"));
    vars.add(new SimulationVariable(prefix + ".molarMass", "molarMass", VariableType.OUTPUT,
        "kg/mol", "Stream molar mass"));
  }

  /**
   * Adds read-only stream variables for an output stream.
   *
   * @param vars the list to add to
   * @param prefix the address prefix including port name
   * @param stream the stream
   */
  private void addStreamOutputVariables(List<SimulationVariable> vars, String prefix,
      StreamInterface stream) {
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
          return hasUnit ? ((CompressorTrain) unit).getPower(uom)
              : ((CompressorTrain) unit).getPower();
        }
        if (unit instanceof GibbsReactor) {
          return hasUnit ? ((GibbsReactor) unit).getPower(uom) : ((GibbsReactor) unit).getPower();
        }
        break;
      case "duty":
        if (unit instanceof Heater) {
          return hasUnit ? ((Heater) unit).getDuty(uom) : ((Heater) unit).getDuty();
        }
        if (unit instanceof HeatExchanger) {
          return ((HeatExchanger) unit).getDuty();
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
          return hasUnit ? ((StreamInterface) unit).getFlowRate(uom)
              : ((StreamInterface) unit).getFlowRate("kg/hr");
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
          return hasUnit ? ((Compressor) unit).getPolytropicHead(uom)
              : ((Compressor) unit).getPolytropicHead();
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
    throw new IllegalArgumentException("Unknown property '" + property + "' for unit "
        + unit.getName() + " (" + unit.getClass().getSimpleName() + ")");
  }

  /**
   * Sets a property value directly on an equipment object.
   *
   * @param unit the equipment
   * @param property the property name
   * @param value the value to set
   * @param uom the unit of measure for the value
   */
  private void setEquipmentProperty(ProcessEquipmentInterface unit, String property, double value,
      String uom) {
    boolean hasUnit = uom != null && !uom.trim().isEmpty();

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
    throw new IllegalArgumentException("Cannot set property '" + property + "' on unit "
        + unit.getName() + " (" + unit.getClass().getSimpleName() + ")");
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
  private void setStreamProperty(StreamInterface stream, String property, double value,
      String uom) {
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
      List<String> validPorts = java.util.Arrays.asList("gasOutStream", "liquidOutStream",
          "oilOutStream", "waterOutStream", "outletStream", "inletStream");
      String portName = parts.length > 1 ? parts[1] : "";
      return diagnostics.diagnosePortNotFound(address, unitName, portName, validPorts);
    }

    // Property not found (Unknown property, Cannot set property, Unknown stream property)
    if (msg.contains("Unknown property") || msg.contains("Cannot set property")
        || msg.contains("Unknown stream property") || msg.contains("Cannot set stream")) {
      try {
        List<SimulationVariable> vars = getVariableList(unitName);
        String propertyName = parts.length > 1 ? parts[parts.length - 1] : "";
        return diagnostics.diagnosePropertyNotFound(address, unitName, propertyName, vars);
      } catch (Exception e) {
        // Can't get variable list - fall through
      }
    }

    // Invalid address format or generic error
    java.util.Map<String, Object> context = new java.util.LinkedHashMap<String, Object>();
    context.put("errorMessage", msg);
    context.put("addressFormat", "unitName.property or unitName.port.property");
    return new AutomationDiagnostics.DiagnosticResult(
        AutomationDiagnostics.ErrorCategory.INVALID_ADDRESS_FORMAT, address, msg,
        new java.util.ArrayList<String>(), null,
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
  private String buildAutoCorrectedJson(String originalAddress, String correctedAddress,
      double value, String unit, AutomationDiagnostics.DiagnosticResult diag) {
    com.google.gson.JsonObject result = new com.google.gson.JsonObject();
    result.addProperty("status", "auto_corrected");
    result.addProperty("originalAddress", originalAddress);
    result.addProperty("correctedAddress", correctedAddress);
    result.addProperty("value", value);
    if (unit != null) {
      result.addProperty("unit", unit);
    }
    result.addProperty("remediation", "Address was auto-corrected from '" + originalAddress
        + "' to '" + correctedAddress + "'. Use the corrected address in future calls.");
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
  private String buildSetSuccessJson(String address, double value, String unit,
      String warningJson) {
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
  private String buildAutoCorrectedSetJson(String originalAddress, String correctedAddress,
      double value, String unit, AutomationDiagnostics.DiagnosticResult diag) {
    com.google.gson.JsonObject result = new com.google.gson.JsonObject();
    result.addProperty("status", "auto_corrected");
    result.addProperty("originalAddress", originalAddress);
    result.addProperty("correctedAddress", correctedAddress);
    result.addProperty("value", value);
    if (unit != null) {
      result.addProperty("unit", unit);
    }
    result.addProperty("remediation", "Address was auto-corrected from '" + originalAddress
        + "' to '" + correctedAddress + "'. Use the corrected address in future calls.");
    return result.toString();
  }
}
