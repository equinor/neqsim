package neqsim.mcp.runners;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import neqsim.process.automation.AutomationDiagnostics;
import neqsim.process.automation.ProcessAutomation;
import neqsim.process.automation.SimulationVariable;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.SimulationResult;
import neqsim.process.processmodel.lifecycle.ProcessModelState;
import neqsim.process.processmodel.lifecycle.ProcessSystemState;
import java.util.Map;

/**
 * Stateless automation and lifecycle runner for MCP integration.
 *
 * <p>
 * Provides string-addressable variable access via {@link ProcessAutomation} and lifecycle state
 * management via {@link ProcessSystemState}. Designed for agentic workflows where LLMs need to
 * discover, read, and write simulation variables without navigating Java class hierarchies.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class AutomationRunner {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor — all methods are static.
   */
  private AutomationRunner() {}

  /**
   * Runs a process from JSON and returns the list of equipment unit names.
   *
   * <p>
   * Builds and runs the process, then queries {@link ProcessAutomation#getUnitList()} to discover
   * all addressable equipment.
   * </p>
   *
   * @param processJson the JSON process definition
   * @return JSON string with list of unit names and their types
   */
  public static String listUnits(String processJson) {
    if (processJson == null || processJson.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty");
    }
    try {
      SimulationResult simResult = ProcessSystem.fromJsonAndRun(processJson);
      if (simResult.isError()) {
        return errorJson("SIMULATION_ERROR", "Process failed: " + simResult.getErrors().toString());
      }
      ProcessSystem process = simResult.getProcessSystem();
      ProcessAutomation auto = process.getAutomation();
      List<String> units = auto.getUnitList();

      JsonObject result = new JsonObject();
      result.addProperty("status", "success");
      JsonArray unitsArray = new JsonArray();
      for (String unit : units) {
        JsonObject unitObj = new JsonObject();
        unitObj.addProperty("name", unit);
        unitObj.addProperty("type", auto.getEquipmentType(unit));
        unitsArray.add(unitObj);
      }
      result.add("units", unitsArray);
      result.addProperty("count", units.size());
      return GSON.toJson(result);
    } catch (Exception e) {
      return errorJson("ERROR", "Failed to list units: " + e.getMessage());
    }
  }

  /**
   * Runs a process from JSON and returns all variables for a specific equipment unit with
   * self-healing. If the unit name is incorrect, attempts fuzzy matching and returns suggestions.
   *
   * @param processJson the JSON process definition
   * @param unitName the equipment unit name to query variables for
   * @return JSON string with list of variables including address, type, unit, and description
   */
  public static String listVariables(String processJson, String unitName) {
    if (processJson == null || processJson.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty");
    }
    if (unitName == null || unitName.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "Unit name is null or empty");
    }
    try {
      SimulationResult simResult = ProcessSystem.fromJsonAndRun(processJson);
      if (simResult.isError()) {
        return errorJson("SIMULATION_ERROR", "Process failed: " + simResult.getErrors().toString());
      }
      ProcessSystem process = simResult.getProcessSystem();
      ProcessAutomation auto = process.getAutomation();

      // Try direct access first; if it fails, use diagnostics
      List<SimulationVariable> vars;
      String resolvedName = unitName;
      String autoCorrection = null;
      try {
        vars = auto.getVariableList(unitName);
      } catch (IllegalArgumentException e) {
        // Fuzzy match the unit name
        List<String> validUnits = auto.getUnitList();
        AutomationDiagnostics diag = auto.getDiagnostics();
        String corrected = diag.autoCorrectName(unitName, validUnits);
        if (corrected != null) {
          vars = auto.getVariableList(corrected);
          resolvedName = corrected;
          autoCorrection = corrected;
          diag.recordFailure("list", unitName,
              AutomationDiagnostics.ErrorCategory.UNIT_NOT_FOUND, corrected);
        } else {
          // Return diagnostic with suggestions
          AutomationDiagnostics.DiagnosticResult diagResult =
              diag.diagnoseUnitNotFound(unitName, validUnits);
          diag.recordFailure("list", unitName,
              AutomationDiagnostics.ErrorCategory.UNIT_NOT_FOUND, null);
          return diagResult.toJson();
        }
      }

      JsonObject result = new JsonObject();
      result.addProperty("status", autoCorrection != null ? "auto_corrected" : "success");
      result.addProperty("unitName", resolvedName);
      if (autoCorrection != null) {
        result.addProperty("originalName", unitName);
        result.addProperty("autoCorrection", autoCorrection);
        result.addProperty("remediation",
            "Unit name auto-corrected from '" + unitName + "' to '" + autoCorrection
                + "'. Use the corrected name in future calls.");
      }
      result.addProperty("equipmentType", auto.getEquipmentType(resolvedName));
      JsonArray varsArray = new JsonArray();
      for (SimulationVariable v : vars) {
        JsonObject varObj = new JsonObject();
        varObj.addProperty("address", v.getAddress());
        varObj.addProperty("name", v.getName());
        varObj.addProperty("type", v.getType().name());
        varObj.addProperty("defaultUnit", v.getDefaultUnit());
        varObj.addProperty("description", v.getDescription());
        varsArray.add(varObj);
      }
      result.add("variables", varsArray);
      result.addProperty("count", vars.size());
      return GSON.toJson(result);
    } catch (Exception e) {
      return errorJson("ERROR", "Failed to list variables: " + e.getMessage());
    }
  }

  /**
   * Runs a process from JSON and reads a specific variable value with self-healing. If the address
   * is incorrect, attempts fuzzy matching and auto-correction, returning suggestions in the
   * response.
   *
   * @param processJson the JSON process definition
   * @param address the dot-notation variable address (e.g., "HP Sep.gasOutStream.temperature")
   * @param unit the desired unit of measurement (e.g., "C", "bara", "kg/hr")
   * @return JSON string with the variable value, auto-correction info, or diagnostic suggestions
   */
  public static String getVariable(String processJson, String address, String unit) {
    if (processJson == null || processJson.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty");
    }
    if (address == null || address.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "Variable address is null or empty");
    }
    try {
      SimulationResult simResult = ProcessSystem.fromJsonAndRun(processJson);
      if (simResult.isError()) {
        return errorJson("SIMULATION_ERROR", "Process failed: " + simResult.getErrors().toString());
      }
      ProcessSystem process = simResult.getProcessSystem();
      ProcessAutomation auto = process.getAutomation();

      // Use safe accessor with self-healing
      return auto.getVariableValueSafe(address, unit);
    } catch (Exception e) {
      return errorJson("ERROR", "Failed to get variable: " + e.getMessage());
    }
  }

  /**
   * Runs a process from JSON, modifies a variable with self-healing, re-runs, and returns the
   * updated results. If the address is incorrect, attempts fuzzy matching and auto-correction. Also
   * validates values against physical bounds before setting.
   *
   * @param processJson the JSON process definition
   * @param address the dot-notation variable address to modify
   * @param value the new value to set
   * @param unit the unit of the value
   * @return JSON string with the updated simulation results or diagnostic info
   */
  public static String setVariableAndRun(String processJson, String address, double value,
      String unit) {
    if (processJson == null || processJson.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty");
    }
    if (address == null || address.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "Variable address is null or empty");
    }
    try {
      SimulationResult simResult = ProcessSystem.fromJsonAndRun(processJson);
      if (simResult.isError()) {
        return errorJson("SIMULATION_ERROR",
            "Initial run failed: " + simResult.getErrors().toString());
      }
      ProcessSystem process = simResult.getProcessSystem();
      ProcessAutomation auto = process.getAutomation();

      // Use safe accessor with self-healing and bounds validation
      String safeResult = auto.setVariableValueSafe(address, value, unit);
      JsonObject safeObj = JsonParser.parseString(safeResult).getAsJsonObject();
      String status = safeObj.has("status") ? safeObj.get("status").getAsString() : "";

      // If set failed (diagnostic returned), return the diagnostic
      if (!"success".equals(status) && !"auto_corrected".equals(status)) {
        return safeResult;
      }

      // Re-run the process after modification
      process.run();

      // Augment result with simulation report
      String report = process.getReport_json();
      if (report != null && !report.isEmpty()) {
        safeObj.add("simulationReport", JsonParser.parseString(report));
      }

      return GSON.toJson(safeObj);
    } catch (Exception e) {
      return errorJson("ERROR", "Failed to set variable: " + e.getMessage());
    }
  }

  /**
   * Runs a process from JSON and saves its state as a lifecycle snapshot.
   *
   * @param processJson the JSON process definition
   * @param stateName name for the snapshot
   * @param stateVersion version string for the snapshot
   * @return JSON string with the serialized process state
   */
  public static String saveState(String processJson, String stateName, String stateVersion) {
    if (processJson == null || processJson.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty");
    }
    try {
      SimulationResult simResult = ProcessSystem.fromJsonAndRun(processJson);
      if (simResult.isError()) {
        return errorJson("SIMULATION_ERROR", "Process failed: " + simResult.getErrors().toString());
      }
      ProcessSystem process = simResult.getProcessSystem();
      ProcessSystemState state = ProcessSystemState.fromProcessSystem(process);
      if (stateName != null && !stateName.trim().isEmpty()) {
        state.setName(stateName);
      }
      if (stateVersion != null && !stateVersion.trim().isEmpty()) {
        state.setVersion(stateVersion);
      }

      String stateJson = state.toJson();

      JsonObject result = new JsonObject();
      result.addProperty("status", "success");
      result.addProperty("stateName", state.getName());
      result.addProperty("stateVersion", state.getVersion());
      result.add("state", JsonParser.parseString(stateJson));
      return GSON.toJson(result);
    } catch (Exception e) {
      return errorJson("ERROR", "Failed to save state: " + e.getMessage());
    }
  }

  /**
   * Compares two process states and returns the differences.
   *
   * @param stateJson1 the first process state as JSON
   * @param stateJson2 the second process state as JSON
   * @return JSON string with the differences between the two states
   */
  public static String compareStates(String stateJson1, String stateJson2) {
    if (stateJson1 == null || stateJson1.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "First state JSON is null or empty");
    }
    if (stateJson2 == null || stateJson2.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "Second state JSON is null or empty");
    }
    try {
      ProcessModelState state1 = ProcessModelState.fromJson(stateJson1);
      ProcessModelState state2 = ProcessModelState.fromJson(stateJson2);
      ProcessModelState.ModelDiff diff = ProcessModelState.compare(state1, state2);

      JsonObject result = new JsonObject();
      result.addProperty("status", "success");
      result.addProperty("hasChanges", diff.hasChanges());
      JsonObject diffObj = new JsonObject();
      JsonArray added = new JsonArray();
      for (String name : diff.getAddedEquipment()) {
        added.add(name);
      }
      diffObj.add("addedEquipment", added);
      JsonArray removed = new JsonArray();
      for (String name : diff.getRemovedEquipment()) {
        removed.add(name);
      }
      diffObj.add("removedEquipment", removed);
      JsonObject modified = new JsonObject();
      for (Map.Entry<String, String> entry : diff.getModifiedParameters().entrySet()) {
        modified.addProperty(entry.getKey(), entry.getValue());
      }
      diffObj.add("modifiedParameters", modified);
      result.add("diff", diffObj);
      return GSON.toJson(result);
    } catch (Exception e) {
      return errorJson("ERROR", "Failed to compare states: " + e.getMessage());
    }
  }

  /**
   * Diagnoses a failed automation operation and returns suggestions for fixing it. This is the
   * entry point agents should call when an automation operation returns an error to get actionable
   * remediation hints.
   *
   * @param processJson the JSON process definition
   * @param failedAddress the address that failed (e.g., "HP separator.gasOut.temp")
   * @param operation the operation that failed ("get", "set", or "list")
   * @return JSON string with diagnostic information including suggestions and auto-corrections
   */
  public static String diagnose(String processJson, String failedAddress, String operation) {
    if (processJson == null || processJson.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty");
    }
    if (failedAddress == null || failedAddress.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "Failed address is null or empty");
    }
    try {
      SimulationResult simResult = ProcessSystem.fromJsonAndRun(processJson);
      if (simResult.isError()) {
        return errorJson("SIMULATION_ERROR", "Process failed: " + simResult.getErrors().toString());
      }
      ProcessSystem process = simResult.getProcessSystem();
      ProcessAutomation auto = process.getAutomation();
      AutomationDiagnostics diag = auto.getDiagnostics();

      // Parse the address to determine what kind of resolution failed
      String localAddress = failedAddress;
      int areaSepIdx = failedAddress.indexOf(ProcessAutomation.AREA_SEPARATOR);
      if (areaSepIdx >= 0) {
        localAddress =
            failedAddress.substring(areaSepIdx + ProcessAutomation.AREA_SEPARATOR.length());
      }
      String[] parts = localAddress.split("\\.", 3);
      String unitName = parts.length > 0 ? parts[0] : "";

      // Try to resolve the unit name
      List<String> validUnits = auto.getUnitList();
      String correctedUnit = diag.autoCorrectName(unitName, validUnits);
      boolean unitResolved = validUnits.contains(unitName) || correctedUnit != null;

      JsonObject result = new JsonObject();
      result.addProperty("status", "diagnostic");
      result.addProperty("failedAddress", failedAddress);
      result.addProperty("operation", operation != null ? operation : "unknown");

      if (!unitResolved) {
        // Unit-level failure
        AutomationDiagnostics.DiagnosticResult unitDiag =
            diag.diagnoseUnitNotFound(unitName, validUnits);
        result.add("diagnosis", JsonParser.parseString(unitDiag.toJson()));
      } else {
        // Unit is fine, check property/port level
        String resolvedUnit = correctedUnit != null ? correctedUnit : unitName;
        List<SimulationVariable> vars = auto.getVariableList(resolvedUnit);

        if (parts.length >= 2) {
          String property = parts[parts.length - 1];
          AutomationDiagnostics.DiagnosticResult propDiag =
              diag.diagnosePropertyNotFound(failedAddress, resolvedUnit, property, vars);
          result.add("diagnosis", JsonParser.parseString(propDiag.toJson()));
        } else {
          // Just provide the list of valid variables as guidance
          JsonArray varsArray = new JsonArray();
          for (SimulationVariable v : vars) {
            JsonObject varObj = new JsonObject();
            varObj.addProperty("address", v.getAddress());
            varObj.addProperty("name", v.getName());
            varObj.addProperty("type", v.getType().name());
            varsArray.add(varObj);
          }
          result.add("availableVariables", varsArray);
          result.addProperty("remediation",
              "Use one of the available variable addresses for unit '" + resolvedUnit + "'.");
        }

        if (correctedUnit != null) {
          result.addProperty("unitAutoCorrection", correctedUnit);
          result.addProperty("unitRemediaton",
              "Unit name '" + unitName + "' was auto-corrected to '" + correctedUnit + "'.");
        }
      }

      // Include the learning report
      result.addProperty("learningReport", diag.getLearningReport());

      return GSON.toJson(result);
    } catch (Exception e) {
      return errorJson("ERROR", "Failed to diagnose: " + e.getMessage());
    }
  }

  /**
   * Returns the learning report from operation history, summarizing success rates, error patterns,
   * learned corrections, and recommendations.
   *
   * @param processJson the JSON process definition (used to initialize the automation)
   * @return JSON string with learning statistics and recommendations
   */
  public static String getLearningReport(String processJson) {
    if (processJson == null || processJson.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty");
    }
    try {
      SimulationResult simResult = ProcessSystem.fromJsonAndRun(processJson);
      if (simResult.isError()) {
        return errorJson("SIMULATION_ERROR", "Process failed: " + simResult.getErrors().toString());
      }
      ProcessSystem process = simResult.getProcessSystem();
      ProcessAutomation auto = process.getAutomation();
      return auto.getDiagnostics().getLearningReport();
    } catch (Exception e) {
      return errorJson("ERROR", "Failed to get learning report: " + e.getMessage());
    }
  }

  /**
   * Builds a standard error JSON response.
   *
   * @param code the error code
   * @param message the error message
   * @return JSON error string
   */
  private static String errorJson(String code, String message) {
    JsonObject error = new JsonObject();
    error.addProperty("status", "error");
    error.addProperty("code", code);
    error.addProperty("message", message);
    return GSON.toJson(error);
  }
}
