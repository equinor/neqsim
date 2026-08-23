package neqsim.mcp.runners;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.model.ApiEnvelope;
import neqsim.mcp.model.ProcessResult;
import neqsim.mcp.model.ResultProvenance;
import neqsim.process.design.AutoSizeable;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorAntiSurgeApplication;
import neqsim.process.equipment.compressor.CompressorAntiSurgeApplication.CommissioningCheck;
import neqsim.process.equipment.compressor.CompressorAntiSurgeApplication.CommissioningReport;
import neqsim.process.equipment.compressor.CompressorAntiSurgeApplication.StageApplication;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.mixer.Mixer;
import neqsim.process.equipment.util.Recycle;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.JsonProcessBuilder;
import neqsim.process.processmodel.JsonProcessExporter;
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.SimulationResult;

/**
 * Stateless process simulation runner for MCP integration.
 *
 * <p>
 * Accepts a JSON process definition, optionally pre-validates it using {@link Validator}, then builds and runs either a
 * {@link ProcessSystem} using {@link ProcessSystem#fromJsonAndRun(String)} or a multi-area {@link ProcessModel} when
 * the JSON contains a top-level {@code areas} object. Returns the simulation result as a JSON string in the standard
 * envelope format.
 * </p>
 *
 * <h2>Input JSON Format:</h2>
 *
 * <pre>{@code { "fluid": { "model": "SRK", "temperature": 298.15, "pressure": 50.0, "mixingRule":
 * "classic", "components": {"methane": 0.85, "ethane": 0.10, "propane": 0.05} }, "process": [
 * {"type": "Stream", "name": "feed", "properties": {"flowRate": [50000.0, "kg/hr"]}}, {"type":
 * "Separator", "name": "HP Sep", "inlet": "feed"}, {"type": "Compressor", "name": "Comp", "inlet":
 * "HP Sep.gasOut", "properties": {"outletPressure": [80.0, "bara"]}} ] } }</pre>
 *
 * @author Even Solbraa @version 1.0
 */
public class ProcessRunner {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Private constructor — all methods are static.
   */
  private ProcessRunner() {
  }

  /**
   * Runs a process simulation from a JSON definition string.
   *
   * <p>
   * Delegates to {@link ProcessSystem#fromJsonAndRun(String)} and returns the result in the standard JSON envelope
   * format with status, report, and any warnings or errors.
   * </p>
   *
   * @param json the JSON process definition
   * @return a JSON string with the simulation result
   */
  public static String run(String json) {
    return validateAndRun(json);
  }

  /**
   * Validates and then runs a process simulation.
   *
   * <p>
   * First performs pre-flight validation using {@link Validator}. If validation finds errors, returns them without
   * running the simulation. If only warnings are found, proceeds with the simulation and includes the validation
   * warnings in the response.
   * </p>
   *
   * @param json the JSON process definition
   * @return a JSON string with validation issues and/or simulation results
   */
  public static String validateAndRun(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("INPUT_ERROR", "JSON input is null or empty",
          "Provide a valid JSON process definition with 'fluid' and 'process' blocks");
    }

    String resolved = resolveJsonInput(json);
    String resolvedTrim = resolved == null ? "" : resolved.trim();
    if (resolvedTrim.isEmpty() || (resolvedTrim.charAt(0) != '{' && resolvedTrim.charAt(0) != '[')) {
      return errorJson("INPUT_ERROR", "Process input is neither valid JSON nor a readable .json file",
          "Pass inline process JSON, or an absolute path to an existing UTF-8 .json file "
              + "(<= 25 MB, name ending in .json) containing the process definition.");
    }

    long startTime = System.currentTimeMillis();
    String normalizedJson = normalizeProcessJson(resolved);

    // Pre-validate
    String validationJson = Validator.validate(normalizedJson);
    JsonObject validation = JsonParser.parseString(validationJson).getAsJsonObject();

    if (!validation.get("valid").getAsBoolean()) {
      // Return validation errors without running
      JsonObject result = new JsonObject();
      result.addProperty("status", "error");
      result.addProperty("phase", "validation");
      result.add("validation", validation);
      addProcessDefinition(result, normalizedJson);
      ApiEnvelope.applyStandardFields(result, "runProcess", null, validation,
          ApiEnvelope.qualityGate("failed", "Pre-flight validation failed", true));
      return GSON.toJson(result);
    }

    // Run simulation
    try {
      JsonArray valIssues = validation.getAsJsonArray("issues");
      if (isProcessModelJson(normalizedJson)) {
        return runProcessModel(normalizedJson, startTime, true, valIssues);
      }
      return runProcessSystem(normalizedJson, startTime, true, valIssues);
    } catch (Exception e) {
      return errorJson("SIMULATION_ERROR", "Process simulation failed: " + e.getMessage(),
          "Retrieve getSchema(run_process,input) and a matching process example, then check plural 'inlets', "
              + "unit.port references, forward recycle references, named fluidRef values, and area/interAreaLinks wiring. "
              + "After repair, validateInput and rerun; inspect convergence warnings and balance evidence.");
    }
  }

  /**
   * Runs a process simulation and returns a typed result.
   *
   * <p>
   * This is the typed counterpart to {@link #run(String)}. It accepts a JSON string (same format) but returns a typed
   * {@link ApiEnvelope} with a {@link ProcessResult} payload for direct Java consumers.
   * </p>
   *
   * @param json the JSON process definition
   * @return an ApiEnvelope containing the ProcessResult on success, or errors on failure
   */
  public static ApiEnvelope<ProcessResult> runTyped(String json) {
    if (json == null || json.trim().isEmpty()) {
      return typedError("INPUT_ERROR", "JSON input is null or empty",
          "Provide a valid JSON process definition with 'fluid' and 'process' blocks");
    }

    try {
      String normalizedJson = normalizeProcessJson(resolveJsonInput(json));
      if (isProcessModelJson(normalizedJson)) {
        return runTypedProcessModel(normalizedJson);
      }

      SimulationResult simResult = ProcessSystem.fromJsonAndRun(normalizedJson);

      if (simResult.isError()) {
        java.util.List<neqsim.mcp.model.DiagnosticIssue> issues = new java.util.ArrayList<neqsim.mcp.model.DiagnosticIssue>();
        for (SimulationResult.ErrorDetail err : simResult.getErrors()) {
          issues.add(neqsim.mcp.model.DiagnosticIssue.error(err.getCode(), err.getMessage(), err.getRemediation()));
        }
        return ApiEnvelope.<ProcessResult>errors(issues).withTool("runProcess");
      }

      ProcessSystem process = simResult.getProcessSystem();
      String name = process != null ? process.getName() : "unknown";
      String reportJson = simResult.getReportJson();

      ProcessResult result = new ProcessResult(name, process, reportJson);
      ResultProvenance provenance = ResultProvenance.forProcess(extractModel(normalizedJson),
          extractMixingRule(normalizedJson), extractEquipmentCount(normalizedJson));
      provenance.setBenchmarkTrustLevel(BenchmarkTrust.getMaturityLevel("runProcess"));
      provenance.addValidationPassed("Process simulation completed");

      ApiEnvelope<ProcessResult> envelope = ApiEnvelope.success(result).withProvenance(provenance)
          .withTool("runProcess")
          .withValidation(ApiEnvelope.validationStatus(true, "simulation", "Typed process execution completed"))
          .withQualityGate(ApiEnvelope.qualityGate("passed", "Process simulation completed", true));

      for (String warning : simResult.getWarnings()) {
        envelope.addWarning(warning);
      }

      return envelope;
    } catch (Exception e) {
      return typedError("SIMULATION_ERROR", "Process simulation failed: " + e.getMessage(),
          "Use Validator.validate() first, then compare the definition with getSchema(run_process,input) and "
              + "the mixer-splitter-recycle example. Check inlet ports, forward references, named fluids, and area links.");
    }
  }

  /**
   * Creates a typed process error envelope with tool metadata.
   *
   * @param code the diagnostic code
   * @param message the diagnostic message
   * @param remediation the remediation hint
   * @return typed process error envelope
   */
  private static ApiEnvelope<ProcessResult> typedError(String code, String message, String remediation) {
    return ApiEnvelope.<ProcessResult>error(code, message, remediation).withTool("runProcess");
  }

  /**
   * Runs a normalized single-area process-system JSON definition.
   *
   * @param normalizedJson the normalized JSON process definition
   * @param startTime the wall-clock start time in milliseconds
   * @param preValidationPassed true if {@link Validator} was already run successfully
   * @param validationIssues optional validation issues to include in the response
   * @return JSON response containing simulation status, report, warnings, and provenance
   */
  private static String runProcessSystem(String normalizedJson, long startTime, boolean preValidationPassed,
      JsonArray validationIssues) {
    SimulationResult result = ProcessSystem.fromJsonAndRun(normalizedJson);
    String simJson = result.toJson();

    String model = extractModel(normalizedJson);
    String mixingRule = extractMixingRule(normalizedJson);
    int equipCount = extractEquipmentCount(normalizedJson);
    ResultProvenance provenance = ResultProvenance.forProcess(model, mixingRule, equipCount);
    provenance.setBenchmarkTrustLevel(BenchmarkTrust.getMaturityLevel("runProcess"));
    provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
    provenance.setConverged(!result.isError());

    if (preValidationPassed) {
      provenance.addValidationPassed("Pre-flight validation passed");
    }
    if (!result.isError()) {
      provenance.addValidationPassed("Process simulation completed");
    }
    for (String warning : result.getWarnings()) {
      provenance.addLimitation("Warning: " + warning);
    }
    addValidationIssueLimitations(provenance, validationIssues);

    JsonObject simObj = JsonParser.parseString(simJson).getAsJsonObject();
    String responseProcessJson = normalizedJson;
    if (!result.isError() && result.getProcessSystem() != null) {
      ProcessSystem process = result.getProcessSystem();
      JsonObject autoSizing = applyAutoSizing(process, normalizedJson);
      if (autoSizing != null) {
        simObj.add("autoSizing", autoSizing);
        if (autoSizing.get("sizedCount").getAsInt() > 0) {
          process.run();
          simObj.add("report", parseJsonOrString(process.getReport_json()));
        }
      }
      JsonObject antiSurgeSystems = applyAntiSurgeSystems(process, normalizedJson, null);
      if (antiSurgeSystems != null) {
        simObj.add("antiSurgeSystems", antiSurgeSystems);
        if (antiSurgeSystems.get("generatedScreeningMapCount").getAsInt() > 0) {
          process.run();
          simObj.add("report", parseJsonOrString(process.getReport_json()));
        }
      }
      attachProcessDesignData(simObj, process);
      responseProcessJson = exportCanonicalProcess(process, normalizedJson);
    }
    addValidationIssues(simObj, validationIssues);
    simObj.add("provenance", GSON.toJsonTree(provenance));
    addProcessDefinition(simObj, responseProcessJson);
    ensureProcessDataBlock(simObj);
    ApiEnvelope.applyStandardFields(simObj, "runProcess", provenance,
        buildProcessValidationBlock(preValidationPassed, validationIssues),
        ApiEnvelope.qualityGate(result.isError() ? "failed" : "passed",
            result.isError() ? "Process simulation returned errors" : "Process simulation completed", true));
    return GSON.toJson(simObj);
  }

  /**
   * Attaches design, capacity-utilization, and bottleneck reports for a completed process run.
   *
   * @param response process response object to mutate
   * @param process live process system after its final run
   */
  private static void attachProcessDesignData(JsonObject response, ProcessSystem process) {
    response.add("designReport", parseJsonOrString(process.getDesignReportJson()));
    process.applyMechanicalDesignCapacityConstraints();
    process.getAutomation().enableCapacityConstraints();
    response.add("utilizationSnapshot", parseJsonOrString(process.getAutomation().getUtilizationSnapshot()));
    response.add("bottleneckRanking",
        parseJsonOrString(process.getAutomation().getBottleneckRankingJson(process.getUnitOperations().size())));
  }

  /**
   * Exports the live process and preserves request-level execution options that are not part of the flowsheet model.
   *
   * @param process live process system to export
   * @param requestJson normalized request JSON
   * @return canonical, replayable process JSON
   */
  private static String exportCanonicalProcess(ProcessSystem process, String requestJson) {
    JsonObject exported = JsonParser.parseString(new JsonProcessExporter().toJson(process)).getAsJsonObject();
    JsonObject request = JsonParser.parseString(requestJson).getAsJsonObject();
    if (request.has("autoSizing")) {
      exported.add("autoSizing", request.get("autoSizing"));
    }
    if (request.has("antiSurgeSystems")) {
      exported.add("antiSurgeSystems", request.get("antiSurgeSystems"));
    }
    return GSON.toJson(exported);
  }

  /**
   * Binds root anti-surge system definitions to physical units in one process area.
   *
   * @param process live process system containing the referenced topology
   * @param requestJson normalized root or area request JSON
   * @param areaName optional area name used for filtering and reporting
   * @return anti-surge configuration report, or {@code null} when no systems are requested for this area
   */
  private static JsonObject applyAntiSurgeSystems(ProcessSystem process, String requestJson, String areaName) {
    JsonObject root = JsonParser.parseString(requestJson).getAsJsonObject();
    if (!root.has("antiSurgeSystems") || !root.get("antiSurgeSystems").isJsonArray()) {
      return null;
    }
    JsonArray requestedSystems = root.getAsJsonArray("antiSurgeSystems");
    JsonArray systemReports = new JsonArray();
    int configuredCount = 0;
    int failedCount = 0;
    int generatedScreeningMapCount = 0;
    for (int systemIndex = 0; systemIndex < requestedSystems.size(); systemIndex++) {
      if (!requestedSystems.get(systemIndex).isJsonObject()) {
        failedCount++;
        systemReports.add(
            antiSurgeError("anti-surge system " + systemIndex, "System definition must be a JSON object", areaName));
        continue;
      }
      JsonObject systemDefinition = requestedSystems.get(systemIndex).getAsJsonObject();
      String configuredArea = getOptionalString(systemDefinition, "area", null);
      if (areaName != null) {
        if (configuredArea == null) {
          continue;
        }
        if (!areaName.equals(configuredArea)) {
          continue;
        }
      }
      String systemName = getOptionalString(systemDefinition, "name", "anti-surge system " + (systemIndex + 1));
      JsonObject systemReport = new JsonObject();
      systemReport.addProperty("name", systemName);
      if (areaName != null) {
        systemReport.addProperty("area", areaName);
      }
      systemReport.addProperty("certificationStatus", "NOT_CERTIFIED_FOR_PROTECTION");
      CompressorAntiSurgeApplication application = new CompressorAntiSurgeApplication(systemName);
      JsonArray stageReports = new JsonArray();
      int systemFailures = 0;
      if (!systemDefinition.has("stages") || !systemDefinition.get("stages").isJsonArray()
          || systemDefinition.getAsJsonArray("stages").size() == 0) {
        systemFailures++;
        stageReports.add(antiSurgeError(systemName, "A non-empty 'stages' array is required", areaName));
      } else {
        JsonArray stages = systemDefinition.getAsJsonArray("stages");
        for (int stageIndex = 0; stageIndex < stages.size(); stageIndex++) {
          JsonObject stageReport;
          try {
            JsonObject stageDefinition = stages.get(stageIndex).getAsJsonObject();
            stageReport = bindAntiSurgeStage(process, application, stageDefinition, areaName,
                hasSubmittedActiveSurgeMap(root, getOptionalString(stageDefinition, "compressor", null)));
            if (stageReport.get("screeningGradeMap").getAsBoolean()) {
              generatedScreeningMapCount++;
            }
          } catch (RuntimeException exception) {
            systemFailures++;
            stageReport = antiSurgeError(systemName + " stage " + (stageIndex + 1), exception.getMessage(), areaName);
          }
          stageReports.add(stageReport);
        }
      }
      systemReport.add("stages", stageReports);
      CommissioningReport commissioning = application.runCommissioningChecks();
      systemReport.add("commissioning", commissioningToJson(commissioning));
      systemReport.addProperty("certificationStatement", commissioning.getCertificationStatement());
      if (systemFailures == 0) {
        systemReport.addProperty("status", "configured");
        configuredCount++;
      } else {
        systemReport.addProperty("status", "failed");
        failedCount++;
      }
      systemReports.add(systemReport);
    }
    if (systemReports.size() == 0) {
      return null;
    }
    JsonObject report = new JsonObject();
    report.addProperty("configuredCount", configuredCount);
    report.addProperty("failedCount", failedCount);
    report.addProperty("generatedScreeningMapCount", generatedScreeningMapCount);
    report.addProperty("certificationStatus", "NOT_CERTIFIED_FOR_PROTECTION");
    report.add("systems", systemReports);
    return report;
  }

  /**
   * Resolves and binds one anti-surge stage to a complete physical recycle path.
   *
   * @param process process system containing the named units
   * @param application anti-surge application receiving the stage
   * @param definition stage definition
   * @param areaName optional area label
   * @param submittedMap true when the request explicitly supplied the compressor map
   * @return stage binding report
   */
  private static JsonObject bindAntiSurgeStage(ProcessSystem process, CompressorAntiSurgeApplication application,
      JsonObject definition, String areaName, boolean submittedMap) {
    String compressorName = requireString(definition, "compressor");
    Compressor compressor = requireUnit(process, compressorName, Compressor.class);
    boolean generateScreeningMap = readBoolean(definition, "generateScreeningMap", false);
    boolean generatedMap = false;
    if (!hasActiveSurgeMap(compressor)) {
      if (!generateScreeningMap) {
        throw new IllegalArgumentException("Compressor '" + compressorName
            + "' requires an active compressor chart and surge curve; supply a vendor map, enable autoSizing, "
            + "or set generateScreeningMap=true");
      }
      double safetyFactor = definition.has("screeningMapSafetyFactor")
          ? definition.get("screeningMapSafetyFactor").getAsDouble()
          : 1.2;
      compressor.autoSize(safetyFactor);
      generatedMap = true;
      if (!hasActiveSurgeMap(compressor)) {
        throw new IllegalArgumentException(
            "Screening map generation did not create an active surge curve for '" + compressorName + "'");
      }
    }

    String hotValveName = getOptionalString(definition, "hotRecycleValve", null);
    String coldValveName = getOptionalString(definition, "coldRecycleValve", null);
    String hotRecycleName = getOptionalString(definition, "hotRecycle", null);
    String coldRecycleName = getOptionalString(definition, "coldRecycle", null);
    if ((hotValveName == null || hotRecycleName == null) && (coldValveName == null || coldRecycleName == null)) {
      throw new IllegalArgumentException("Stage '" + compressorName
          + "' requires a complete hot or cold recycle path with both valve and Recycle unit names");
    }
    ThrottlingValve hotValve = optionalUnit(process, hotValveName, ThrottlingValve.class);
    ThrottlingValve coldValve = optionalUnit(process, coldValveName, ThrottlingValve.class);
    Recycle hotRecycle = optionalUnit(process, hotRecycleName, Recycle.class);
    Recycle coldRecycle = optionalUnit(process, coldRecycleName, Recycle.class);
    Mixer suctionMixer = requireUnit(process, requireString(definition, "suctionMixer"), Mixer.class);
    Cooler aftercooler = optionalUnit(process, getOptionalString(definition, "aftercooler", null), Cooler.class);

    String stageName = getOptionalString(definition, "name", compressorName);
    StageApplication stage = application.addStage(stageName, compressor);
    stage.bindTopology(process, compressor, hotValve, coldValve, aftercooler, suctionMixer, hotRecycle, coldRecycle);
    configureAntiSurgeStage(stage, definition);

    JsonObject report = new JsonObject();
    report.addProperty("name", stageName);
    if (areaName != null) {
      report.addProperty("area", areaName);
    }
    report.addProperty("status", "bound");
    report.addProperty("physicalTopologyBound", true);
    report.addProperty("compressor", compressorName);
    report.addProperty("suctionMixer", suctionMixer.getName());
    addOptionalProperty(report, "hotRecycleValve", hotValveName);
    addOptionalProperty(report, "coldRecycleValve", coldValveName);
    addOptionalProperty(report, "aftercooler", aftercooler == null ? null : aftercooler.getName());
    addOptionalProperty(report, "hotRecycle", hotRecycleName);
    addOptionalProperty(report, "coldRecycle", coldRecycleName);
    report.addProperty("generatedScreeningMap", generatedMap);
    boolean screeningGradeMap = generatedMap || !submittedMap;
    report.addProperty("screeningGradeMap", screeningGradeMap);
    report.addProperty("mapProvenance",
        generatedMap ? "stage_generated_screening" : submittedMap ? "submitted" : "auto_sized_screening");
    report.addProperty("speedControlEnabled", stage.getTopologyBinding().isSpeedControlEnabled());
    if (screeningGradeMap) {
      report.addProperty("warning", "Generated compressor map is a screening estimate, not vendor-certified.");
    }
    return report;
  }

  /**
   * Checks whether a compressor map was explicitly submitted in a process definition.
   *
   * @param root request or area JSON object
   * @param compressorName compressor unit name
   * @return true when the compressor properties contain a compressor chart
   */
  private static boolean hasSubmittedActiveSurgeMap(JsonObject root, String compressorName) {
    if (compressorName == null || !root.has("process") || !root.get("process").isJsonArray()) {
      return false;
    }
    for (com.google.gson.JsonElement element : root.getAsJsonArray("process")) {
      if (!element.isJsonObject()) {
        continue;
      }
      JsonObject unit = element.getAsJsonObject();
      if (!compressorName.equals(getOptionalString(unit, "name", null)) || !unit.has("properties")
          || !unit.get("properties").isJsonObject()) {
        continue;
      }
      JsonObject properties = unit.getAsJsonObject("properties");
      if (!properties.has("compressorChart") || !properties.get("compressorChart").isJsonObject()) {
        return false;
      }
      JsonObject chart = properties.getAsJsonObject("compressorChart");
      if (chart.has("useCompressorChart") && !chart.get("useCompressorChart").getAsBoolean()) {
        return false;
      }
      return chart.has("surgeCurve") && chart.get("surgeCurve").isJsonObject()
          && readBoolean(chart.getAsJsonObject("surgeCurve"), "active", false);
    }
    return false;
  }

  /**
   * Applies optional design and response settings to an anti-surge stage.
   *
   * @param stage stage to configure
   * @param definition stage JSON definition
   */
  private static void configureAntiSurgeStage(StageApplication stage, JsonObject definition) {
    if (definition.has("designBasis") && definition.get("designBasis").isJsonObject()) {
      JsonObject design = definition.getAsJsonObject("designBasis");
      stage.setDesignBasis(requireDouble(design, "inletFlow"), requireDouble(design, "surgeFlow"),
          requireDouble(design, "suctionDensity"));
    }
    if (definition.has("recycleDesign") && definition.get("recycleDesign").isJsonObject()) {
      JsonObject recycle = definition.getAsJsonObject("recycleDesign");
      stage.setRecycleDesign(requireDouble(recycle, "controlMargin"), requireDouble(recycle, "valvePressureDrop"),
          requireDouble(recycle, "pipingVolume"), requireDouble(recycle, "requiredResponseTime"));
    }
    if (definition.has("valveStrokeTimes") && definition.get("valveStrokeTimes").isJsonObject()) {
      JsonObject strokes = definition.getAsJsonObject("valveStrokeTimes");
      stage.setValveStrokeTimes(requireDouble(strokes, "hot"), requireDouble(strokes, "cold"));
    }
    if (definition.has("speedControl") && definition.get("speedControl").isJsonObject()) {
      JsonObject speedControl = definition.getAsJsonObject("speedControl");
      if (!readBoolean(speedControl, "enabled", true)) {
        stage.getTopologyBinding().disableSpeedControl();
        return;
      }
      Compressor compressor = stage.getCompressor();
      double currentSpeed = compressor.getSpeed();
      double minimumSpeed = readFiniteDouble(speedControl, "minimumSpeed", Math.max(0.0, currentSpeed * 0.5));
      double maximumSpeed = readFiniteDouble(speedControl, "maximumSpeed",
          Math.max(currentSpeed * 1.5, minimumSpeed + 1.0));
      if (minimumSpeed < 0.0 || maximumSpeed < minimumSpeed) {
        throw new IllegalArgumentException("speedControl requires 0 <= minimumSpeed <= maximumSpeed");
      }
      stage.getTopologyBinding().enableSpeedControl(requireDouble(speedControl, "dischargePressureSetPoint"),
          readFiniteDouble(speedControl, "speedGain", 50.0), minimumSpeed, maximumSpeed,
          readFiniteDouble(speedControl, "recycleRunbackRate", 100.0));
    }
  }

  /**
   * Checks that a compressor has an enabled chart with an active surge boundary.
   *
   * @param compressor compressor to inspect
   * @return true when the map can support anti-surge calculations
   */
  private static boolean hasActiveSurgeMap(Compressor compressor) {
    return compressor.getCompressorChart() != null && compressor.getCompressorChart().isUseCompressorChart()
        && compressor.getCompressorChart().getSurgeCurve() != null
        && compressor.getCompressorChart().getSurgeCurve().isActive();
  }

  /**
   * Resolves a required named unit and checks its concrete type.
   *
   * @param process process system to search
   * @param name required unit name
   * @param type required concrete type
   * @param <T> process equipment type
   * @return resolved typed unit
   */
  private static <T extends ProcessEquipmentInterface> T requireUnit(ProcessSystem process, String name,
      Class<T> type) {
    ProcessEquipmentInterface unit = process.getUnit(name);
    if (unit == null) {
      throw new IllegalArgumentException("Required unit '" + name + "' was not found");
    }
    if (!type.isInstance(unit)) {
      throw new IllegalArgumentException(
          "Unit '" + name + "' must be " + type.getSimpleName() + " but is " + unit.getClass().getSimpleName());
    }
    return type.cast(unit);
  }

  /**
   * Resolves an optional named unit and checks its concrete type.
   *
   * @param process process system to search
   * @param name optional unit name
   * @param type required concrete type when named
   * @param <T> process equipment type
   * @return resolved unit, or {@code null} when no name was supplied
   */
  private static <T extends ProcessEquipmentInterface> T optionalUnit(ProcessSystem process, String name,
      Class<T> type) {
    return name == null ? null : requireUnit(process, name, type);
  }

  /**
   * Converts an anti-surge commissioning report to stable response JSON.
   *
   * @param commissioning commissioning report
   * @return JSON commissioning evidence
   */
  private static JsonObject commissioningToJson(CommissioningReport commissioning) {
    JsonObject result = new JsonObject();
    result.addProperty("allPassed", commissioning.allPassed());
    result.addProperty("certificationStatus", commissioning.getCertificationStatus().name());
    result.addProperty("certificationStatement", commissioning.getCertificationStatement());
    JsonArray checks = new JsonArray();
    for (CommissioningCheck check : commissioning.getChecks()) {
      JsonObject item = new JsonObject();
      item.addProperty("name", check.getName());
      item.addProperty("status", check.getStatus().name());
      item.addProperty("evidence", check.getEvidence());
      item.addProperty("recommendation", check.getRecommendation());
      checks.add(item);
    }
    result.add("checks", checks);
    return result;
  }

  /**
   * Creates a bounded anti-surge configuration error.
   *
   * @param name system or stage name
   * @param message actionable error message
   * @param areaName optional area label
   * @return error report
   */
  private static JsonObject antiSurgeError(String name, String message, String areaName) {
    JsonObject error = new JsonObject();
    error.addProperty("name", name);
    error.addProperty("status", "failed");
    error.addProperty("error", message == null ? "Unknown anti-surge configuration error" : message);
    if (areaName != null) {
      error.addProperty("area", areaName);
    }
    return error;
  }

  /**
   * Reads a required string property.
   *
   * @param object JSON object
   * @param name property name
   * @return non-empty string value
   */
  private static String requireString(JsonObject object, String name) {
    String value = getOptionalString(object, name, null);
    if (value == null) {
      throw new IllegalArgumentException("Required string property '" + name + "' is missing");
    }
    return value;
  }

  /**
   * Reads an optional string property.
   *
   * @param object JSON object
   * @param name property name
   * @param defaultValue fallback value
   * @return property value or fallback
   */
  private static String getOptionalString(JsonObject object, String name, String defaultValue) {
    if (!object.has(name) || object.get(name).isJsonNull()) {
      return defaultValue;
    }
    String value = object.get(name).getAsString().trim();
    return value.isEmpty() ? defaultValue : value;
  }

  /**
   * Reads a required finite double property.
   *
   * @param object JSON object
   * @param name property name
   * @return finite property value
   */
  private static double requireDouble(JsonObject object, String name) {
    if (!object.has(name)) {
      throw new IllegalArgumentException("Required numeric property '" + name + "' is missing");
    }
    double value = object.get(name).getAsDouble();
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("Numeric property '" + name + "' must be finite");
    }
    return value;
  }

  /**
   * Reads an optional finite double property.
   *
   * @param object JSON object
   * @param name property name
   * @param defaultValue fallback value
   * @return finite property value or fallback
   */
  private static double readFiniteDouble(JsonObject object, String name, double defaultValue) {
    if (!object.has(name)) {
      return defaultValue;
    }
    return requireDouble(object, name);
  }

  /**
   * Adds a non-null string property.
   *
   * @param object JSON object to mutate
   * @param name property name
   * @param value optional value
   */
  private static void addOptionalProperty(JsonObject object, String name, String value) {
    if (value != null) {
      object.addProperty(name, value);
    }
  }

  /**
   * Parses JSON text, falling back to a JSON string value when the text is not valid JSON.
   *
   * @param value JSON text or plain text
   * @return parsed JSON element represented as an object-compatible value
   */
  private static com.google.gson.JsonElement parseJsonOrString(String value) {
    try {
      return JsonParser.parseString(value);
    } catch (RuntimeException exception) {
      return GSON.toJsonTree(value);
    }
  }

  /**
   * Applies optional selective equipment autosizing to a successfully run process.
   *
   * <p>
   * Explicit compressor maps and valve {@code Cv} values are preserved by default. Set
   * {@code autoSizing.overwriteExplicit=true} (or {@code preserveExplicit=false}) to replace them. Generated compressor
   * maps are marked as screening estimates and are not vendor-certified.
   * </p>
   *
   * @param process live process system after its initial successful run
   * @param normalizedJson canonical request JSON containing the optional {@code autoSizing} object
   * @return sizing result JSON, or {@code null} when autosizing is not requested
   */
  private static JsonObject applyAutoSizing(ProcessSystem process, String normalizedJson) {
    JsonObject root = JsonParser.parseString(normalizedJson).getAsJsonObject();
    if (!root.has("autoSizing") || !root.get("autoSizing").isJsonObject()) {
      return null;
    }
    JsonObject options = root.getAsJsonObject("autoSizing");
    if (!readBoolean(options, "enabled", false)) {
      return null;
    }

    double safetyFactor = options.has("safetyFactor") ? options.get("safetyFactor").getAsDouble() : 1.2;
    if (!Double.isFinite(safetyFactor) || safetyFactor <= 0.0) {
      safetyFactor = 1.2;
    }
    boolean overwriteExplicit = readBoolean(options, "overwriteExplicit", false)
        || !readBoolean(options, "preserveExplicit", true);

    JsonObject report = new JsonObject();
    JsonArray equipment = new JsonArray();
    int sizedCount = 0;
    int preservedCount = 0;
    int failedCount = 0;
    for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
      if (!(unit instanceof AutoSizeable)) {
        continue;
      }
      JsonObject item = new JsonObject();
      item.addProperty("name", unit.getName());
      item.addProperty("type", unit.getClass().getSimpleName());
      String explicitEvidence = getExplicitSizingEvidence(unit, root);
      if (!overwriteExplicit && explicitEvidence != null) {
        item.addProperty("status", "preserved");
        item.addProperty("provenance", "supplied");
        item.addProperty("evidence", explicitEvidence);
        preservedCount++;
      } else {
        try {
          ((AutoSizeable) unit).autoSize(safetyFactor);
          item.addProperty("status", "sized");
          item.addProperty("provenance", "generated_screening");
          if (unit instanceof Compressor) {
            item.addProperty("warning", "Generated compressor map is a screening estimate, not vendor-certified.");
          }
          sizedCount++;
        } catch (RuntimeException exception) {
          item.addProperty("status", "failed");
          item.addProperty("error", exception.getClass().getSimpleName() + ": " + exception.getMessage());
          failedCount++;
        }
      }
      equipment.add(item);
    }

    report.addProperty("enabled", true);
    report.addProperty("safetyFactor", safetyFactor);
    report.addProperty("preserveExplicit", !overwriteExplicit);
    report.addProperty("sizedCount", sizedCount);
    report.addProperty("preservedCount", preservedCount);
    report.addProperty("failedCount", failedCount);
    report.add("equipment", equipment);
    return report;
  }

  /**
   * Returns recognized explicit sizing evidence that should not be overwritten by default.
   *
   * @param unit process equipment to inspect
   * @param requestRoot normalized request containing the submitted process units
   * @return evidence label, or {@code null} when no recognized explicit sizing evidence exists
   */
  private static String getExplicitSizingEvidence(ProcessEquipmentInterface unit, JsonObject requestRoot) {
    if (unit instanceof Compressor) {
      Compressor compressor = (Compressor) unit;
      if (compressor.getCompressorChart() != null && compressor.getCompressorChart().isUseCompressorChart()) {
        return "compressorChart";
      }
    }
    if (unit instanceof ThrottlingValve && ((ThrottlingValve) unit).isValveKvSet()) {
      return "cv";
    }
    JsonObject properties = findSubmittedProperties(requestRoot, unit.getName());
    if (properties == null) {
      return null;
    }
    if (properties.has("mechanicalDesign")) {
      return "mechanicalDesign";
    }
    String[] designProperties = { "compressorChart", "cv", "internalDiameter", "separatorLength", "diameter",
        "pipeWallThickness", "maxDesignDuty", "designDuty", "maxDesignPower", "maxDesignVolumeFlow" };
    for (String designProperty : designProperties) {
      if (properties.has(designProperty)) {
        return designProperty;
      }
    }
    return null;
  }

  /**
   * Finds the submitted property object for a named single-area process unit.
   *
   * @param requestRoot normalized request JSON
   * @param unitName process unit name
   * @return submitted properties, or {@code null} when the unit or properties are absent
   */
  private static JsonObject findSubmittedProperties(JsonObject requestRoot, String unitName) {
    if (!requestRoot.has("process") || !requestRoot.get("process").isJsonArray()) {
      return null;
    }
    JsonArray units = requestRoot.getAsJsonArray("process");
    for (int i = 0; i < units.size(); i++) {
      if (!units.get(i).isJsonObject()) {
        continue;
      }
      JsonObject submittedUnit = units.get(i).getAsJsonObject();
      if (submittedUnit.has("name") && unitName.equals(submittedUnit.get("name").getAsString())
          && submittedUnit.has("properties") && submittedUnit.get("properties").isJsonObject()) {
        return submittedUnit.getAsJsonObject("properties");
      }
    }
    return null;
  }

  /**
   * Reads an optional Boolean property.
   *
   * @param object JSON object containing the property
   * @param name property name
   * @param defaultValue value returned when the property is absent
   * @return parsed property or the supplied default
   */
  private static boolean readBoolean(JsonObject object, String name, boolean defaultValue) {
    return object.has(name) ? object.get(name).getAsBoolean() : defaultValue;
  }

  /**
   * Runs a normalized multi-area process-model JSON definition.
   *
   * @param normalizedJson the normalized JSON containing a top-level {@code areas} object
   * @param startTime the wall-clock start time in milliseconds
   * @param preValidationPassed true if {@link Validator} was already run successfully
   * @param validationIssues optional validation issues to include in the response
   * @return JSON response containing model status, area metadata, report, warnings, and provenance
   */
  private static String runProcessModel(String normalizedJson, long startTime, boolean preValidationPassed,
      JsonArray validationIssues) {
    ProcessModelBuildResult buildResult = buildProcessModel(normalizedJson);
    if (!buildResult.errors.isEmpty()) {
      return errorJson(buildResult.errors, buildResult.warnings);
    }

    try {
      buildResult.model.run();
    } catch (Exception e) {
      return errorJson("SIMULATION_ERROR", "Process model simulation failed: " + e.getMessage(),
          "Check area wiring, recycle settings, and equipment parameters in the 'areas' object.");
    }

    JsonObject autoSizing = applyProcessModelAutoSizing(buildResult.model, normalizedJson);
    if (autoSizing != null && autoSizing.get("sizedCount").getAsInt() > 0) {
      try {
        buildResult.model.run();
      } catch (Exception e) {
        return errorJson("SIMULATION_ERROR", "Post-sizing process model simulation failed: " + e.getMessage(),
            "Review the generated screening sizes or disable autoSizing for supplied equipment design.");
      }
    }

    JsonObject antiSurgeSystems = applyProcessModelAntiSurgeSystems(buildResult.model, normalizedJson);
    if (antiSurgeSystems != null && antiSurgeSystems.get("generatedScreeningMapCount").getAsInt() > 0) {
      try {
        buildResult.model.run();
      } catch (Exception e) {
        return errorJson("SIMULATION_ERROR", "Post anti-surge map process model simulation failed: " + e.getMessage(),
            "Review generated screening maps or provide validated vendor compressor maps.");
      }
    }

    JsonObject result = new JsonObject();
    result.addProperty("status", "success");
    result.addProperty("processModelName", "json-process-model");
    result.addProperty("areaCount", buildResult.model.size());
    result.add("areas", toJsonArray(buildResult.model.getProcessSystemNames()));

    if (!buildResult.warnings.isEmpty()) {
      JsonArray warnings = new JsonArray();
      for (String warning : buildResult.warnings) {
        warnings.add(warning);
      }
      result.add("warnings", warnings);
    }

    String reportJson = null;
    try {
      reportJson = buildResult.model.getReport_json();
    } catch (Exception e) {
      buildResult.warnings.add("ProcessModel report generation failed: " + e.getMessage());
    }
    if (reportJson != null) {
      try {
        result.add("report", JsonParser.parseString(reportJson));
      } catch (Exception e) {
        result.addProperty("report", reportJson);
      }
    }
    result.addProperty("convergenceSummary", buildResult.model.getConvergenceSummary());
    result.add("convergenceReport", JsonParser.parseString(buildResult.model.getConvergenceReportJson()));
    if (autoSizing != null) {
      result.add("autoSizing", autoSizing);
    }
    if (antiSurgeSystems != null) {
      result.add("antiSurgeSystems", antiSurgeSystems);
    }
    attachProcessModelDesignData(result, buildResult.model);

    ResultProvenance provenance = ResultProvenance.forProcess(extractModel(normalizedJson),
        extractMixingRule(normalizedJson), extractEquipmentCount(normalizedJson));
    provenance.setBenchmarkTrustLevel(BenchmarkTrust.getMaturityLevel("runProcess"));
    provenance.setComputationTimeMs(System.currentTimeMillis() - startTime);
    provenance.setConverged(buildResult.model.isModelConverged() || buildResult.model.size() <= 1);
    provenance.addAssumption("Multi-area ProcessModel executed from top-level JSON areas");
    provenance.addLimitation("ProcessModel contains " + buildResult.model.size()
        + " areas - verify inter-area stream references and convergence summary");
    if (preValidationPassed) {
      provenance.addValidationPassed("Pre-flight validation passed");
    }
    provenance.addValidationPassed("ProcessModel simulation completed");
    for (String warning : buildResult.warnings) {
      provenance.addLimitation("Warning: " + warning);
    }
    addValidationIssueLimitations(provenance, validationIssues);
    addValidationIssues(result, validationIssues);
    result.add("provenance", GSON.toJsonTree(provenance));
    addProcessDefinition(result, exportCanonicalProcessModel(buildResult.model, normalizedJson));
    ensureProcessDataBlock(result);
    ApiEnvelope.applyStandardFields(result, "runProcess", provenance,
        buildProcessValidationBlock(preValidationPassed, validationIssues),
        ApiEnvelope.qualityGate("passed", "ProcessModel simulation completed", true));

    return GSON.toJson(result);
  }

  /**
   * Applies the root autosizing policy independently to every process-model area.
   *
   * @param model live multi-area process model
   * @param normalizedJson normalized root request JSON
   * @return aggregate sizing report, or {@code null} when autosizing is not enabled
   */
  private static JsonObject applyProcessModelAutoSizing(ProcessModel model, String normalizedJson) {
    JsonObject root = JsonParser.parseString(normalizedJson).getAsJsonObject();
    if (!root.has("autoSizing") || !root.get("autoSizing").isJsonObject()
        || !readBoolean(root.getAsJsonObject("autoSizing"), "enabled", false)) {
      return null;
    }
    JsonObject report = new JsonObject();
    JsonArray equipment = new JsonArray();
    int sizedCount = 0;
    int preservedCount = 0;
    int failedCount = 0;
    for (String areaName : model.getProcessSystemNames()) {
      JsonObject areaRequest = root.getAsJsonObject("areas").getAsJsonObject(areaName).deepCopy();
      areaRequest.add("autoSizing", root.get("autoSizing").deepCopy());
      JsonObject areaReport = applyAutoSizing(model.get(areaName), GSON.toJson(areaRequest));
      sizedCount += areaReport.get("sizedCount").getAsInt();
      preservedCount += areaReport.get("preservedCount").getAsInt();
      failedCount += areaReport.get("failedCount").getAsInt();
      for (com.google.gson.JsonElement element : areaReport.getAsJsonArray("equipment")) {
        JsonObject item = element.getAsJsonObject();
        item.addProperty("area", areaName);
        equipment.add(item);
      }
    }
    JsonObject options = root.getAsJsonObject("autoSizing");
    boolean overwriteExplicit = readBoolean(options, "overwriteExplicit", false)
        || !readBoolean(options, "preserveExplicit", true);
    double safetyFactor = options.has("safetyFactor") ? options.get("safetyFactor").getAsDouble() : 1.2;
    report.addProperty("enabled", true);
    report.addProperty("safetyFactor", safetyFactor);
    report.addProperty("preserveExplicit", !overwriteExplicit);
    report.addProperty("sizedCount", sizedCount);
    report.addProperty("preservedCount", preservedCount);
    report.addProperty("failedCount", failedCount);
    report.add("equipment", equipment);
    return report;
  }

  /**
   * Applies area-qualified anti-surge systems across a multi-area process model.
   *
   * @param model live process model
   * @param normalizedJson normalized root request JSON
   * @return aggregate anti-surge report, or {@code null} when none are configured
   */
  private static JsonObject applyProcessModelAntiSurgeSystems(ProcessModel model, String normalizedJson) {
    JsonObject root = JsonParser.parseString(normalizedJson).getAsJsonObject();
    if (!root.has("antiSurgeSystems") || !root.get("antiSurgeSystems").isJsonArray()) {
      return null;
    }
    JsonArray systems = new JsonArray();
    int configuredCount = 0;
    int failedCount = 0;
    int generatedScreeningMapCount = 0;
    List<String> areaNames = model.getProcessSystemNames();
    for (com.google.gson.JsonElement element : root.getAsJsonArray("antiSurgeSystems")) {
      if (!element.isJsonObject()) {
        JsonObject failure = antiSurgeAreaFailure(null, "Each antiSurgeSystems entry must be an object");
        systems.add(failure);
        failedCount++;
        continue;
      }
      JsonObject definition = element.getAsJsonObject();
      String configuredArea = getOptionalString(definition, "area", null);
      if (configuredArea == null) {
        systems.add(antiSurgeAreaFailure(definition,
            "Multi-area anti-surge system requires an explicit 'area' matching a process-model area"));
        failedCount++;
      } else if (!areaNames.contains(configuredArea)) {
        systems.add(antiSurgeAreaFailure(definition,
            "Anti-surge system area '" + configuredArea + "' does not exist in the process model"));
        failedCount++;
      }
    }
    for (String areaName : areaNames) {
      JsonObject areaRequest = new JsonObject();
      areaRequest.add("antiSurgeSystems", root.get("antiSurgeSystems").deepCopy());
      JsonObject areaDefinition = root.getAsJsonObject("areas").getAsJsonObject(areaName);
      if (areaDefinition.has("process")) {
        areaRequest.add("process", areaDefinition.get("process").deepCopy());
      }
      JsonObject areaReport = applyAntiSurgeSystems(model.get(areaName), GSON.toJson(areaRequest), areaName);
      if (areaReport == null) {
        continue;
      }
      configuredCount += areaReport.get("configuredCount").getAsInt();
      failedCount += areaReport.get("failedCount").getAsInt();
      generatedScreeningMapCount += areaReport.get("generatedScreeningMapCount").getAsInt();
      for (com.google.gson.JsonElement system : areaReport.getAsJsonArray("systems")) {
        systems.add(system);
      }
    }
    JsonObject report = new JsonObject();
    report.addProperty("configuredCount", configuredCount);
    report.addProperty("failedCount", failedCount);
    report.addProperty("generatedScreeningMapCount", generatedScreeningMapCount);
    report.addProperty("certificationStatus", "NOT_CERTIFIED_FOR_PROTECTION");
    report.add("systems", systems);
    return report;
  }

  /**
   * Creates a bounded multi-area anti-surge routing failure.
   *
   * @param definition optional anti-surge system definition
   * @param message failure description
   * @return failed system report
   */
  private static JsonObject antiSurgeAreaFailure(JsonObject definition, String message) {
    JsonObject failure = new JsonObject();
    failure.addProperty("name",
        definition == null ? "anti-surge system" : getOptionalString(definition, "name", "anti-surge system"));
    if (definition != null) {
      addOptionalProperty(failure, "area", getOptionalString(definition, "area", null));
    }
    failure.addProperty("status", "failed");
    failure.addProperty("error", message);
    failure.addProperty("certificationStatus", "NOT_CERTIFIED_FOR_PROTECTION");
    return failure;
  }

  /**
   * Attaches area design reports and plant-wide capacity reports after the final model run.
   *
   * @param response process-model response object to mutate
   * @param model live process model after its final run
   */
  private static void attachProcessModelDesignData(JsonObject response, ProcessModel model) {
    JsonObject designReport = new JsonObject();
    JsonObject areas = new JsonObject();
    int unitCount = 0;
    for (String areaName : model.getProcessSystemNames()) {
      ProcessSystem area = model.get(areaName);
      areas.add(areaName, parseJsonOrString(area.getDesignReportJson()));
      unitCount += area.getUnitOperations().size();
    }
    designReport.add("areas", areas);
    response.add("designReport", designReport);
    model.applyMechanicalDesignCapacityConstraints();
    model.getAutomation().enableCapacityConstraints();
    response.add("utilizationSnapshot", parseJsonOrString(model.getAutomation().getUtilizationSnapshot()));
    response.add("bottleneckRanking", parseJsonOrString(model.getAutomation().getBottleneckRankingJson(unitCount)));
  }

  /**
   * Exports every live area while retaining model-level execution controls and inter-area links.
   *
   * @param model live process model to export
   * @param requestJson normalized request JSON
   * @return canonical replayable multi-area JSON
   */
  private static String exportCanonicalProcessModel(ProcessModel model, String requestJson) {
    JsonObject exported = JsonParser.parseString(requestJson).getAsJsonObject().deepCopy();
    JsonObject areas = new JsonObject();
    JsonProcessExporter exporter = new JsonProcessExporter();
    for (String areaName : model.getProcessSystemNames()) {
      areas.add(areaName, exporter.toJsonObject(model.get(areaName)));
    }
    exported.add("areas", areas);
    return GSON.toJson(exported);
  }

  /**
   * Runs a normalized process-model JSON definition and returns a typed MCP envelope.
   *
   * @param normalizedJson the normalized JSON containing a top-level {@code areas} object
   * @return typed process result containing the built {@link ProcessModel}, or errors on failure
   */
  private static ApiEnvelope<ProcessResult> runTypedProcessModel(String normalizedJson) {
    ProcessModelBuildResult buildResult = buildProcessModel(normalizedJson);
    if (!buildResult.errors.isEmpty()) {
      java.util.List<neqsim.mcp.model.DiagnosticIssue> issues = new java.util.ArrayList<neqsim.mcp.model.DiagnosticIssue>();
      for (SimulationResult.ErrorDetail err : buildResult.errors) {
        issues.add(neqsim.mcp.model.DiagnosticIssue.error(err.getCode(), err.getMessage(), err.getRemediation()));
      }
      return ApiEnvelope.<ProcessResult>errors(issues).withTool("runProcess");
    }

    try {
      buildResult.model.run();
      ProcessResult result = new ProcessResult("json-process-model", buildResult.model,
          buildResult.model.getReport_json(), buildResult.model.getProcessSystemNames());
      ApiEnvelope<ProcessResult> envelope = ApiEnvelope.success(result).withTool("runProcess");
      ResultProvenance provenance = ResultProvenance.forProcess(extractModel(normalizedJson),
          extractMixingRule(normalizedJson), extractEquipmentCount(normalizedJson));
      provenance.setBenchmarkTrustLevel(BenchmarkTrust.getMaturityLevel("runProcess"));
      provenance.addAssumption("Multi-area ProcessModel executed from top-level JSON areas");
      envelope.withProvenance(provenance)
          .withValidation(ApiEnvelope.validationStatus(true, "simulation", "Typed ProcessModel execution completed"))
          .withQualityGate(ApiEnvelope.qualityGate("passed", "ProcessModel simulation completed", true));
      for (String warning : buildResult.warnings) {
        envelope.addWarning(warning);
      }
      return envelope;
    } catch (Exception e) {
      return typedError("SIMULATION_ERROR", "Process model simulation failed: " + e.getMessage(),
          "Check area wiring, recycle settings, and equipment parameters in the 'areas' object.");
    }
  }

  /**
   * Builds a {@link ProcessModel} from top-level {@code areas} JSON while preserving area-level build errors for MCP
   * responses.
   *
   * @param normalizedJson normalized JSON containing named process areas
   * @return build result containing the model, errors, and warnings
   */
  private static ProcessModelBuildResult buildProcessModel(String normalizedJson) {
    ProcessModelBuildResult result = new ProcessModelBuildResult();
    try {
      JsonObject root = JsonParser.parseString(normalizedJson).getAsJsonObject();
      if (!root.has("areas") || !root.get("areas").isJsonObject()) {
        result.errors
            .add(new SimulationResult.ErrorDetail("MISSING_AREAS", "ProcessModel JSON must contain an 'areas' object",
                null, "Use {\"areas\": {\"areaName\": {\"fluid\": {...}, \"process\": [...]}}}"));
        return result;
      }

      JsonObject areas = root.getAsJsonObject("areas");
      if (areas.entrySet().isEmpty()) {
        result.errors.add(new SimulationResult.ErrorDetail("EMPTY_AREAS", "ProcessModel JSON contains no process areas",
            null, "Add at least one named area under the 'areas' object"));
        return result;
      }

      applyProcessModelExecutionSettings(root, result.model);

      for (Map.Entry<String, com.google.gson.JsonElement> entry : areas.entrySet()) {
        String areaName = entry.getKey();
        if (!entry.getValue().isJsonObject()) {
          result.errors
              .add(new SimulationResult.ErrorDetail("INVALID_AREA", "Area '" + areaName + "' must be a JSON object",
                  areaName, "Provide each area as a standard ProcessSystem JSON object"));
          continue;
        }
        SimulationResult areaResult = new JsonProcessBuilder().build(entry.getValue().toString());
        if (areaResult.isSuccess() && areaResult.getProcessSystem() != null) {
          result.model.add(areaName, areaResult.getProcessSystem());
          for (String warning : areaResult.getWarnings()) {
            result.warnings.add("Area '" + areaName + "': " + warning);
          }
        } else {
          for (SimulationResult.ErrorDetail error : areaResult.getErrors()) {
            result.errors.add(new SimulationResult.ErrorDetail(error.getCode(),
                "Area '" + areaName + "': " + error.getMessage(), areaName, error.getRemediation()));
          }
          for (String warning : areaResult.getWarnings()) {
            result.warnings.add("Area '" + areaName + "': " + warning);
          }
        }
      }
      if (result.errors.isEmpty() && root.has("interAreaLinks") && root.get("interAreaLinks").isJsonArray()) {
        result.warnings.addAll(result.model.applyInterAreaLinks(root.getAsJsonArray("interAreaLinks")));
      }
    } catch (Exception e) {
      result.errors.add(new SimulationResult.ErrorDetail("PROCESS_MODEL_PARSE_ERROR",
          "Failed to parse ProcessModel JSON: " + e.getMessage(), null,
          "Ensure the JSON has a top-level 'areas' object with valid area definitions"));
    }
    return result;
  }

  /**
   * Applies top-level execution controls from ProcessModel JSON.
   *
   * @param root root JSON object containing optional execution settings
   * @param model process model to configure before running
   */
  private static void applyProcessModelExecutionSettings(JsonObject root, ProcessModel model) {
    if (root.has("runStep")) {
      model.setRunStep(root.get("runStep").getAsBoolean());
    }
    if (root.has("maxIterations")) {
      model.setMaxIterations(root.get("maxIterations").getAsInt());
    }
    if (root.has("flowTolerance")) {
      model.setFlowTolerance(root.get("flowTolerance").getAsDouble());
    }
    if (root.has("temperatureTolerance")) {
      model.setTemperatureTolerance(root.get("temperatureTolerance").getAsDouble());
    }
    if (root.has("pressureTolerance")) {
      model.setPressureTolerance(root.get("pressureTolerance").getAsDouble());
    }
  }

  /**
   * Extracts the EOS model name from the input JSON.
   *
   * @param json the input JSON string
   * @return the model name, or "SRK" as default
   */
  private static String extractModel(String json) {
    try {
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      if (root.has("areas") && root.get("areas").isJsonObject()) {
        return extractAreaSummary(root.getAsJsonObject("areas"), "model", "SRK");
      }
      if (root.has("fluid") && root.getAsJsonObject("fluid").has("model")) {
        return root.getAsJsonObject("fluid").get("model").getAsString();
      }
    } catch (Exception ignored) {
    }
    return "SRK";
  }

  /**
   * Extracts the mixing rule from the input JSON.
   *
   * @param json the input JSON string
   * @return the mixing rule, or "classic" as default
   */
  private static String extractMixingRule(String json) {
    try {
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      if (root.has("areas") && root.get("areas").isJsonObject()) {
        return extractAreaSummary(root.getAsJsonObject("areas"), "mixingRule", "classic");
      }
      if (root.has("fluid") && root.getAsJsonObject("fluid").has("mixingRule")) {
        return root.getAsJsonObject("fluid").get("mixingRule").getAsString();
      }
    } catch (Exception ignored) {
    }
    return "classic";
  }

  /**
   * Counts the number of equipment entries in the process definition.
   *
   * @param json the input JSON string
   * @return the equipment count, or 0 if not parseable
   */
  private static int extractEquipmentCount(String json) {
    try {
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      if (root.has("areas") && root.get("areas").isJsonObject()) {
        int count = 0;
        for (Map.Entry<String, com.google.gson.JsonElement> entry : root.getAsJsonObject("areas").entrySet()) {
          count += extractEquipmentCount(entry.getValue().toString());
        }
        return count;
      }
      if (root.has("process") && root.get("process").isJsonArray()) {
        return root.getAsJsonArray("process").size();
      }
      if (root.has("equipment") && root.get("equipment").isJsonArray()) {
        return root.getAsJsonArray("equipment").size();
      }
    } catch (Exception ignored) {
    }
    return 0;
  }

  /**
   * Resolves the process-definition input, accepting either inline JSON or a path to a {@code .json} file.
   *
   * <p>
   * If the trimmed input begins with <code>{</code> or <code>[</code> it is treated as inline JSON and returned
   * unchanged. Otherwise, if it names an existing, readable, regular file whose name ends in {@code .json} (path length
   * &lt;= 4096, file size &gt; 0 and &lt;= 25 MB), the file contents are read as UTF-8 and returned. In all other cases
   * the original input is returned unchanged so the caller can report a clear error.
   * </p>
   *
   * @param input inline process JSON or a filesystem path to a {@code .json} file
   * @return the JSON content to parse, or the original input if it is not a resolvable file
   */
  static String resolveJsonInput(String input) {
    if (input == null) {
      return null;
    }
    String trimmed = input.trim();
    if (trimmed.isEmpty() || trimmed.charAt(0) == '{' || trimmed.charAt(0) == '[') {
      return input;
    }
    try {
      if (trimmed.length() <= 4096 && trimmed.toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
        java.nio.file.Path path = java.nio.file.Paths.get(trimmed);
        if (java.nio.file.Files.isRegularFile(path) && java.nio.file.Files.isReadable(path)) {
          long size = java.nio.file.Files.size(path);
          if (size > 0 && size <= 25L * 1024L * 1024L) {
            return new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
          }
        }
      }
    } catch (Exception ignored) {
      // Fall through and return the original input; downstream validation reports the error.
    }
    return input;
  }

  /**
   * Normalizes accepted process JSON variants to the canonical schema.
   *
   * <p>
   * Canonical schema expects {@code process} to be an array. Some legacy clients send {@code {"process": {"equipment":
   * [...]}}}. This method converts the legacy shape to the canonical one while preserving all other fields.
   * </p>
   *
   * @param json raw process JSON
   * @return canonical JSON string (or original input if not parseable)
   */
  static String normalizeProcessJson(String json) {
    try {
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();

      if (root.has("areas") && root.get("areas").isJsonObject()) {
        JsonObject areas = root.getAsJsonObject("areas");
        JsonObject normalizedAreas = new JsonObject();
        for (Map.Entry<String, com.google.gson.JsonElement> entry : areas.entrySet()) {
          if (entry.getValue().isJsonObject()) {
            normalizedAreas.add(entry.getKey(),
                JsonParser.parseString(normalizeProcessJson(entry.getValue().toString())).getAsJsonObject());
          } else {
            normalizedAreas.add(entry.getKey(), entry.getValue());
          }
        }
        root.add("areas", normalizedAreas);
        return GSON.toJson(root);
      }

      if (root.has("fluid") && root.get("fluid").isJsonObject()) {
        JsonObject fluid = root.getAsJsonObject("fluid");
        if (!fluid.has("temperature") && fluid.has("temperature_C")) {
          fluid.addProperty("temperature", fluid.get("temperature_C").getAsDouble() + 273.15);
        }
        if (!fluid.has("pressure") && fluid.has("pressure_bara")) {
          fluid.addProperty("pressure", fluid.get("pressure_bara").getAsDouble());
        }
      }

      if (root.has("process") && root.get("process").isJsonObject()) {
        JsonObject processObj = root.getAsJsonObject("process");
        if (processObj.has("equipment") && processObj.get("equipment").isJsonArray()) {
          root.add("process", processObj.getAsJsonArray("equipment"));
        }
      }

      if (root.has("process") && root.get("process").isJsonArray()) {
        JsonArray processArr = root.getAsJsonArray("process");
        for (int i = 0; i < processArr.size(); i++) {
          JsonObject unit = processArr.get(i).getAsJsonObject();
          JsonObject properties = unit.has("properties") && unit.get("properties").isJsonObject()
              ? unit.getAsJsonObject("properties")
              : new JsonObject();

          if (!properties.has("flowRate") && unit.has("flowRate")) {
            properties.add("flowRate", unit.get("flowRate"));
          }
          if (!properties.has("temperature") && unit.has("temperature")) {
            properties.add("temperature", unit.get("temperature"));
          }
          if (!properties.has("pressure") && unit.has("pressure")) {
            properties.add("pressure", unit.get("pressure"));
          }

          if (properties.size() > 0) {
            normalizeLegacyPropertyObjects(properties);
            unit.add("properties", properties);
          }
        }
      }

      return GSON.toJson(root);
    } catch (Exception ignored) {
    }
    return json;
  }

  /**
   * Checks whether a JSON string represents a multi-area ProcessModel.
   *
   * @param json the JSON string to inspect
   * @return true if the root object has a JSON object named {@code areas}
   */
  private static boolean isProcessModelJson(String json) {
    try {
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      return root.has("areas") && root.get("areas").isJsonObject();
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Extracts a comma-separated summary of a fluid field across all process-model areas.
   *
   * @param areas the areas object from a ProcessModel JSON document
   * @param fluidField the field to extract from each area's fluid block
   * @param defaultValue fallback value when an area omits the field
   * @return one value, or comma-separated values when areas differ
   */
  private static String extractAreaSummary(JsonObject areas, String fluidField, String defaultValue) {
    List<String> values = new ArrayList<String>();
    for (Map.Entry<String, com.google.gson.JsonElement> entry : areas.entrySet()) {
      if (!entry.getValue().isJsonObject()) {
        continue;
      }
      JsonObject area = entry.getValue().getAsJsonObject();
      String value = defaultValue;
      if (area.has("fluid") && area.get("fluid").isJsonObject() && area.getAsJsonObject("fluid").has(fluidField)) {
        value = area.getAsJsonObject("fluid").get(fluidField).getAsString();
      }
      if (!values.contains(value)) {
        values.add(value);
      }
    }
    if (values.isEmpty()) {
      return defaultValue;
    }
    return join(values, ", ");
  }

  /**
   * Adds validation warnings to a response object.
   *
   * @param response the response object to mutate
   * @param validationIssues validation issues returned by {@link Validator}
   */
  private static void addValidationIssues(JsonObject response, JsonArray validationIssues) {
    if (validationIssues != null && validationIssues.size() > 0) {
      response.add("validationIssues", validationIssues);
    }
  }

  /**
   * Builds a validation block for process runner responses.
   *
   * @param preValidationPassed true when pre-flight validation was executed and passed
   * @param validationIssues validation issues from {@link Validator}
   * @return validation JSON block
   */
  private static JsonObject buildProcessValidationBlock(boolean preValidationPassed, JsonArray validationIssues) {
    JsonObject validation = ApiEnvelope.validationStatus(preValidationPassed,
        preValidationPassed ? "preflight" : "not_run", preValidationPassed ? "Pre-flight validation passed"
            : "Pre-flight validation was not run on this internal path");
    validation.add("issues", validationIssues != null ? validationIssues : new JsonArray());
    return validation;
  }

  /**
   * Adds a strict data block while keeping legacy top-level fields.
   *
   * @param response process response object to mutate
   */
  private static void ensureProcessDataBlock(JsonObject response) {
    if (response.has("data")) {
      return;
    }
    JsonObject data = new JsonObject();
    String[] fields = { "processSystemName", "processModelName", "areaCount", "areas", "report",
        "convergenceSummary", "convergenceReport", "autoSizing", "designReport", "utilizationSnapshot",
        "bottleneckRanking", "processDefinition", "pythonScript" };
    for (String field : fields) {
      if (response.has(field)) {
        data.add(field, response.get(field));
      }
    }
    if (data.size() > 0) {
      response.add("data", data);
    }
  }

  /**
   * Adds the canonical process definition to a response so clients can inspect, edit, and submit it again.
   *
   * @param response process response object to mutate
   * @param normalizedJson normalized process JSON
   */
  private static void addProcessDefinition(JsonObject response, String normalizedJson) {
    try {
      response.add("processDefinition", JsonParser.parseString(normalizedJson));
      response.addProperty("pythonScript", renderPythonScript(normalizedJson));
    } catch (RuntimeException exception) {
      response.addProperty("processDefinition", normalizedJson);
    }
  }

  /**
   * Renders a standalone Python script that executes the canonical process JSON.
   *
   * <p>
   * Single-area definitions use {@link ProcessSystem#fromJsonAndRun(String)} directly and retain the live process in a
   * {@code process} variable. Multi-area definitions use this runner because it owns the JSON-to-{@link ProcessModel}
   * construction contract, including inter-area links and convergence settings.
   * </p>
   *
   * @param normalizedJson canonical process JSON to embed
   * @return deterministic Python source code
   */
  static String renderPythonScript(String normalizedJson) {
    String processLiteral = GSON.toJson(GSON.toJson(JsonParser.parseString(normalizedJson)));
    StringBuilder script = new StringBuilder();
    script.append("import json\n");
    script.append("from neqsim import jneqsim\n\n");
    script.append("PROCESS_JSON = ").append(processLiteral).append("\n\n");
    if (isProcessModelJson(normalizedJson)) {
      script.append("ProcessRunner = jneqsim.mcp.runners.ProcessRunner\n");
      script.append("response = json.loads(str(ProcessRunner.validateAndRun(PROCESS_JSON)))\n");
      script.append("if response.get(\"status\") != \"success\":\n");
      script.append("    raise RuntimeError(json.dumps(response, indent=2))\n");
      script.append("print(json.dumps(response, indent=2))\n");
    } else {
      script.append("ProcessSystem = jneqsim.process.processmodel.ProcessSystem\n");
      script.append("result = ProcessSystem.fromJsonAndRun(PROCESS_JSON)\n");
      script.append("if result.isError():\n");
      script.append("    raise RuntimeError(str(result.toJson()))\n");
      script.append("process = result.getProcessSystem()\n");
      script.append("print(str(result.toJson()))\n");
    }
    return script.toString();
  }

  /**
   * Adds validation warnings to provenance limitations.
   *
   * @param provenance the provenance object to mutate
   * @param validationIssues validation issues returned by {@link Validator}
   */
  private static void addValidationIssueLimitations(ResultProvenance provenance, JsonArray validationIssues) {
    if (validationIssues == null) {
      return;
    }
    for (int i = 0; i < validationIssues.size(); i++) {
      JsonObject issue = validationIssues.get(i).getAsJsonObject();
      if (issue.has("message")) {
        provenance.addLimitation("Validation warning: " + issue.get("message").getAsString());
      }
    }
  }

  /**
   * Converts a string list to a JSON array.
   *
   * @param values values to convert
   * @return JSON array containing the string values
   */
  private static JsonArray toJsonArray(List<String> values) {
    JsonArray array = new JsonArray();
    for (String value : values) {
      array.add(value);
    }
    return array;
  }

  /**
   * Joins string values with a delimiter.
   *
   * @param values values to join
   * @param delimiter delimiter between values
   * @return joined string
   */
  private static String join(List<String> values, String delimiter) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        builder.append(delimiter);
      }
      builder.append(values.get(i));
    }
    return builder.toString();
  }

  /**
   * Converts legacy {value, unit} property objects to [value, unit] arrays expected by the JsonProcessBuilder
   * reflection setter logic.
   *
   * @param properties mutable properties object
   */
  private static void normalizeLegacyPropertyObjects(JsonObject properties) {
    String[] unitAwareKeys = { "flowRate", "temperature", "pressure" };
    for (String key : unitAwareKeys) {
      if (properties.has(key) && properties.get(key).isJsonObject()) {
        JsonObject obj = properties.getAsJsonObject(key);
        if (obj.has("value") && obj.has("unit")) {
          JsonArray arr = new JsonArray();
          arr.add(obj.get("value"));
          arr.add(obj.get("unit"));
          properties.add(key, arr);
        }
      }
    }
  }

  /**
   * Creates a standard error JSON response.
   *
   * @param code the error code
   * @param message the error message
   * @param remediation the fix suggestion
   * @return JSON error string
   */
  private static String errorJson(String code, String message, String remediation) {
    JsonObject result = new JsonObject();
    result.addProperty("status", "error");

    JsonArray errors = new JsonArray();
    JsonObject err = new JsonObject();
    err.addProperty("code", code);
    err.addProperty("message", message);
    if (remediation != null) {
      err.addProperty("remediation", remediation);
    }
    errors.add(err);
    result.add("errors", errors);

    ApiEnvelope.applyStandardFields(result, "runProcess", null,
        ApiEnvelope.validationStatus(false, "input_or_simulation", message),
        ApiEnvelope.qualityGate("failed", message, true));

    return GSON.toJson(result);
  }

  /**
   * Creates a standard error JSON response from detailed simulation errors.
   *
   * @param errors simulation errors to expose
   * @param warnings non-fatal warnings to expose
   * @return JSON error string
   */
  private static String errorJson(List<SimulationResult.ErrorDetail> errors, List<String> warnings) {
    JsonObject result = new JsonObject();
    result.addProperty("status", "error");

    JsonArray errorArray = new JsonArray();
    for (SimulationResult.ErrorDetail error : errors) {
      errorArray.add(error.toJsonObject());
    }
    result.add("errors", errorArray);

    if (warnings != null && !warnings.isEmpty()) {
      JsonArray warningArray = new JsonArray();
      for (String warning : warnings) {
        warningArray.add(warning);
      }
      result.add("warnings", warningArray);
    }

    ApiEnvelope.applyStandardFields(result, "runProcess", null,
        ApiEnvelope.validationStatus(false, "build_or_simulation", "Process build or simulation returned errors"),
        ApiEnvelope.qualityGate("failed", "Process build or simulation returned errors", true));

    return GSON.toJson(result);
  }

  /**
   * Mutable container for ProcessModel build results.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class ProcessModelBuildResult {
    private final ProcessModel model = new ProcessModel();
    private final List<SimulationResult.ErrorDetail> errors = new ArrayList<SimulationResult.ErrorDetail>();
    private final List<String> warnings = new ArrayList<String>();
  }
}
