package neqsim.mcp.runners;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.process.equipment.EquipmentFactory;

/**
 * Compact implementation traceability for the published NeqSim MCP surface.
 *
 * <p>
 * The full capability descriptors are intentionally detailed and can be removed by the response-size guard. This
 * inventory keeps the smaller tool-to-implementation bindings, canonical equipment-factory surface, and report paths
 * available for agents that need to understand how a request reaches the normal NeqSim model.
 * </p>
 *
 * <p>
 * This is metadata only. It does not introduce a second simulator, construct equipment, run a process, persist a
 * report, or bypass tool governance.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class McpImplementationInventory {

  private static final String RUNNER_PACKAGE = "neqsim.mcp.runners.";

  /** Exact implementation class used by each published MCP tool. */
  private static final Map<String, String> TOOL_IMPLEMENTATIONS = buildToolImplementations();

  /**
   * Private constructor — utility class.
   */
  private McpImplementationInventory() {
  }

  /**
   * Returns the exact implementation class for a published tool.
   *
   * @param toolName public MCP tool name
   * @return fully qualified implementation class, or null for an unknown tool
   */
  static String getImplementationClass(String toolName) {
    return TOOL_IMPLEMENTATIONS.get(toolName);
  }

  /**
   * Returns the immutable tool implementation registry for contract tests.
   *
   * @return tool-to-class bindings
   */
  static Map<String, String> getToolImplementations() {
    return TOOL_IMPLEMENTATIONS;
  }

  /**
   * Builds the machine-readable implementation inventory.
   *
   * @param toolCapabilities completed capability descriptors keyed by public tool name
   * @return compact implementation, equipment, and report-path manifest
   */
  static JsonObject build(JsonObject toolCapabilities) {
    JsonObject inventory = new JsonObject();
    JsonObject bindings = new JsonObject();
    JsonArray missing = new JsonArray();
    JsonArray mismatched = new JsonArray();

    List<String> publishedTools = new ArrayList<String>(IndustrialProfile.getAllKnownTools());
    Collections.sort(publishedTools);
    Set<String> implementationClasses = new TreeSet<String>();
    for (String toolName : publishedTools) {
      String expectedClass = TOOL_IMPLEMENTATIONS.get(toolName);
      if (expectedClass == null) {
        missing.add(toolName);
        continue;
      }
      bindings.addProperty(toolName, expectedClass);
      implementationClasses.add(expectedClass);

      if (!toolCapabilities.has(toolName)) {
        missing.add(toolName + " (capability descriptor)");
        continue;
      }
      JsonObject descriptor = toolCapabilities.getAsJsonObject(toolName);
      if (!descriptor.has("implementationClass")
          || !expectedClass.equals(descriptor.get("implementationClass").getAsString())) {
        mismatched.add(toolName);
      }
    }

    JsonArray undeclared = new JsonArray();
    for (String toolName : TOOL_IMPLEMENTATIONS.keySet()) {
      if (!IndustrialProfile.getAllKnownTools().contains(toolName)) {
        undeclared.add(toolName);
      }
    }

    inventory.addProperty("toolBindingCount", bindings.size());
    inventory.addProperty("implementationClassCount", implementationClasses.size());
    inventory.addProperty("complete", missing.size() == 0 && mismatched.size() == 0 && undeclared.size() == 0);
    inventory.add("toolImplementationBindings", bindings);
    inventory.add("implementationClasses", toJsonArray(implementationClasses));
    inventory.add("missingToolBindings", missing);
    inventory.add("mismatchedCapabilityBindings", mismatched);
    inventory.add("undeclaredToolBindings", undeclared);
    inventory.addProperty("bindingSource", "NeqSimTools delegates reconciled with IndustrialProfile.getAllKnownTools");

    List<String> equipmentTypes = EquipmentFactory.getSupportedEquipmentTypes();
    inventory.addProperty("equipmentTypeCount", equipmentTypes.size());
    inventory.add("supportedEquipmentTypes", toJsonArray(equipmentTypes));
    inventory.addProperty("equipmentFactory", "neqsim.process.equipment.EquipmentFactory");
    inventory.addProperty("processBuilder", "neqsim.process.processmodel.JsonProcessBuilder");
    inventory.addProperty("equipmentDiscovery",
        "Recursive runtime discovery plus specialized name-only EquipmentFactory construction");

    JsonArray reportPaths = new JsonArray();
    reportPaths
        .add(reportPath("engineering-report", "generateReport", "ReportRunner", "Standardized simulation-result JSON",
            new String[] { "markdown", "tables", "chartData", "validation", "summary" },
            "Returned in the bounded MCP response; no file or plant-system write"));
    reportPaths.add(
        reportPath("task-workflow-handoff", "bridgeTaskWorkflow", "TaskWorkflowBridge", "Standardized MCP tool output",
            new String[] { "resultsJson", "key_results", "validation", "approach", "conclusions" },
            "Returns a results.json-compatible handoff; external report rendering is a separate reviewed step"));
    inventory.addProperty("reportPathCount", reportPaths.size());
    inventory.add("reportPaths", reportPaths);
    inventory.addProperty("advisoryBoundary",
        "Inventory and reports are advisory metadata and outputs; they do not control or write to live plant systems");
    return inventory;
  }

  /**
   * Builds one report-path descriptor.
   *
   * @param id stable report path id
   * @param toolName public MCP tool name
   * @param implementationClass simple runner class name
   * @param inputContract input contract summary
   * @param outputs report outputs
   * @param persistence persistence and external-write boundary
   * @return report-path descriptor
   */
  private static JsonObject reportPath(String id, String toolName, String implementationClass, String inputContract,
      String[] outputs, String persistence) {
    JsonObject path = new JsonObject();
    path.addProperty("id", id);
    path.addProperty("tool", toolName);
    path.addProperty("implementationClass", qualify(implementationClass));
    path.addProperty("inputContract", inputContract);
    path.add("outputs", toJsonArray(java.util.Arrays.asList(outputs)));
    path.addProperty("persistenceBoundary", persistence);
    return path;
  }

  /**
   * Builds the exact tool implementation registry.
   *
   * @return immutable insertion-ordered bindings
   */
  private static Map<String, String> buildToolImplementations() {
    Map<String, String> implementations = new LinkedHashMap<String, String>();

    bind(implementations, "runFlash", "FlashRunner");
    bind(implementations, "runProcess", "ProcessRunner");
    bind(implementations, "validateInput", "Validator");
    bind(implementations, "searchComponents", "ComponentQuery");
    bind(implementations, "getExample", "neqsim.mcp.catalog.ExampleCatalog");
    bind(implementations, "getSchema", "neqsim.mcp.catalog.SchemaCatalog");
    bind(implementations, "getPropertyTable", "PropertyTableRunner");
    bind(implementations, "getPhaseEnvelope", "PhaseEnvelopeRunner");
    bind(implementations, "getCapabilities", "CapabilitiesRunner");
    bind(implementations, "runBatch", "BatchRunner");
    bind(implementations, "listSimulationUnits", "AutomationRunner");
    bind(implementations, "listUnitVariables", "AutomationRunner");
    bind(implementations, "getSimulationVariable", "AutomationRunner");
    bind(implementations, "setSimulationVariable", "AutomationRunner");
    bind(implementations, "saveSimulationState", "AutomationRunner");
    bind(implementations, "compareSimulationStates", "AutomationRunner");
    bind(implementations, "diagnoseAutomation", "AutomationRunner");
    bind(implementations, "getAutomationLearningReport", "AutomationRunner");
    bind(implementations, "manageIndustrialProfile", "IndustrialProfile");
    bind(implementations, "getBenchmarkTrust", "BenchmarkTrust");
    bind(implementations, "checkToolAccess", "IndustrialProfile");
    bind(implementations, "getAdjustableParameters", "AutomationRunner");
    bind(implementations, "manageModel", "ModelRegistry");
    bind(implementations, "inspectApi", "ApiKnowledgeRunner");

    bind(implementations, "crossValidateModels", "CrossValidationRunner");
    bind(implementations, "runParametricStudy", "ParametricStudyRunner");
    bind(implementations, "runPVT", "PVTRunner");
    bind(implementations, "runFlowAssurance", "FlowAssuranceRunner");
    bind(implementations, "calculateStandard", "StandardsRunner");
    bind(implementations, "runPipeline", "PipelineRunner");
    bind(implementations, "runChemistry", "ChemistryRunner");
    bind(implementations, "runMaterialsReview", "MaterialsReviewRunner");
    bind(implementations, "runOpenDrainReview", "OpenDrainReviewRunner");
    bind(implementations, "runNorsokS001Clause10Review", "NorsokS001Clause10ReviewRunner");
    bind(implementations, "runWaterHammer", "WaterHammerRunner");
    bind(implementations, "runAgenticEngineering", "AgenticEngineeringRunner");
    bind(implementations, "runReservoir", "ReservoirRunner");
    bind(implementations, "runFieldEconomics", "FieldDevelopmentRunner");
    bind(implementations, "runDynamic", "DynamicRunner");
    bind(implementations, "runBioprocess", "BioprocessRunner");
    bind(implementations, "sizeEquipment", "EquipmentSizingRunner");
    bind(implementations, "compareProcesses", "ProcessComparisonRunner");
    bind(implementations, "validateResults", "EngineeringValidator");
    bind(implementations, "runRelief", "ReliefRunner");
    bind(implementations, "runLOPA", "LOPARunner");
    bind(implementations, "runSIL", "SILRunner");
    bind(implementations, "runRiskMatrix", "RiskMatrixRunner");
    bind(implementations, "runFlareNetwork", "FlareRadiationRunner");
    bind(implementations, "runHAZOP", "HAZOPStudyRunner");
    bind(implementations, "runHazopScenario", "HazopScenarioRunner");
    bind(implementations, "runBarrierRegister", "BarrierRegisterRunner");
    bind(implementations, "runSafetySystemPerformance", "SafetySystemPerformanceRunner");
    bind(implementations, "runOperationalStudy", "OperationalStudyRunner");
    bind(implementations, "runRootCauseAnalysis", "RootCauseRunner");
    bind(implementations, "runProcessLoop", "AutomationRunner");
    bind(implementations, "designUtilities", "UtilityDesignRunner");

    bind(implementations, "manageSession", "SessionRunner");
    bind(implementations, "solveTask", "TaskSolverRunner");
    bind(implementations, "composeWorkflow", "TaskSolverRunner");
    bind(implementations, "generateReport", "ReportRunner");
    bind(implementations, "runPlugin", "PluginRegistry");
    bind(implementations, "getProgress", "ProgressTracker");
    bind(implementations, "streamSimulation", "StreamingRunner");
    bind(implementations, "generateVisualization", "VisualizationRunner");
    bind(implementations, "composeMultiServerWorkflow", "CompositionRunner");
    bind(implementations, "manageSecurity", "SecurityRunner");
    bind(implementations, "manageState", "StatePersistenceRunner");
    bind(implementations, "manageValidationProfile", "ValidationProfileRunner");
    bind(implementations, "queryDataCatalog", "DataCatalogRunner");
    bind(implementations, "bridgeTaskWorkflow", "TaskWorkflowBridge");
    bind(implementations, "runCapability", "GeneralCapabilityRunner");

    return Collections.unmodifiableMap(implementations);
  }

  /**
   * Adds one fully qualified implementation binding.
   *
   * @param implementations mutable registry
   * @param toolName public MCP tool name
   * @param implementationClass simple runner name or fully qualified class
   */
  private static void bind(Map<String, String> implementations, String toolName, String implementationClass) {
    implementations.put(toolName, qualify(implementationClass));
  }

  /**
   * Qualifies a core MCP implementation class name.
   *
   * @param implementationClass simple or fully qualified class name
   * @return fully qualified class name
   */
  private static String qualify(String implementationClass) {
    return implementationClass.indexOf('.') >= 0 ? implementationClass : RUNNER_PACKAGE + implementationClass;
  }

  /**
   * Converts strings to a JSON array.
   *
   * @param values string values
   * @return JSON array
   */
  private static JsonArray toJsonArray(Iterable<String> values) {
    JsonArray array = new JsonArray();
    for (String value : values) {
      array.add(value);
    }
    return array;
  }
}
