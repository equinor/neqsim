package neqsim.mcp.runners;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Industrial deployment profiles for the NeqSim MCP server.
 *
 * <p>
 * Provides opinionated deployment modes that control which tools are exposed, whether
 * human-approval gates are required for execution tools, and which validation level is enforced.
 * Each profile is designed for a specific industry use case:
 * </p>
 *
 * <ul>
 * <li><b>DESKTOP_ENGINEER</b> — Full access for a single engineer working on studies.</li>
 * <li><b>STUDY_TEAM</b> — Collaborative mode with session isolation and audit logging.</li>
 * <li><b>DIGITAL_TWIN</b> — Read-heavy advisory mode for plant operations support.</li>
 * <li><b>ENTERPRISE</b> — Restricted mode with approval gates, rate limiting, and minimal execution
 * tools.</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class IndustrialProfile {

  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /**
   * Deployment mode for the MCP server.
   */
  public enum DeploymentMode {
    /** Full access for a single engineer. All tools available. */
    DESKTOP_ENGINEER,
    /** Collaborative mode with session isolation and audit logging. */
    STUDY_TEAM,
    /** Read-heavy advisory mode for plant operations support. */
    DIGITAL_TWIN,
    /** Restricted enterprise mode with approval gates and rate limiting. */
    ENTERPRISE
  }

  /**
   * Tool classification by risk level.
   */
  public enum ToolCategory {
    /** Read-only tools that retrieve data or compute results without side effects. */
    ADVISORY,
    /** Tools that run calculations — side-effect-free but computationally significant. */
    CALCULATION,
    /** Tools that modify simulation state (sessions, variables, parameters). */
    EXECUTION,
    /** Platform tools for security, plugins, multi-server — high governance needs. */
    PLATFORM
  }

  /** Active deployment mode. */
  private static volatile DeploymentMode activeMode = DeploymentMode.DESKTOP_ENGINEER;

  /** Whether auto-validation is enforced on all calculation/execution tools. */
  private static volatile boolean autoValidationEnabled = true;

  /** Whether human approval is required for execution tools. */
  private static volatile boolean approvalGateEnabled = false;

  /**
   * Private constructor — utility class.
   */
  private IndustrialProfile() {}

  /**
   * Tool-to-category classification for all MCP tools.
   */
  private static final Map<String, ToolCategory> TOOL_CATEGORIES = buildToolCategories();

  /**
   * Industrial core — the subset of tools recommended for production engineering use.
   */
  private static final Set<String> INDUSTRIAL_CORE =
      Collections.unmodifiableSet(new HashSet<>(Arrays.asList("runFlash", "runProcess", "runPVT",
          "runFlowAssurance", "calculateStandard", "crossValidateModels", "runParametricStudy",
          "validateResults", "generateReport", "validateInput", "searchComponents",
          "getCapabilities", "getExample", "getSchema", "getPropertyTable", "getPhaseEnvelope",
          "runBatch", "runPipeline", "sizeEquipment", "compareProcesses")));

  /**
   * Builds the tool-to-category mapping.
   *
   * @return unmodifiable map of tool name to category
   */
  private static Map<String, ToolCategory> buildToolCategories() {
    Map<String, ToolCategory> map = new LinkedHashMap<String, ToolCategory>();

    // Advisory tools — read-only, no side effects
    map.put("searchComponents", ToolCategory.ADVISORY);
    map.put("getCapabilities", ToolCategory.ADVISORY);
    map.put("getExample", ToolCategory.ADVISORY);
    map.put("getSchema", ToolCategory.ADVISORY);
    map.put("validateInput", ToolCategory.ADVISORY);
    map.put("validateResults", ToolCategory.ADVISORY);
    map.put("listSimulationUnits", ToolCategory.ADVISORY);
    map.put("listUnitVariables", ToolCategory.ADVISORY);
    map.put("getSimulationVariable", ToolCategory.ADVISORY);
    map.put("getAutomationLearningReport", ToolCategory.ADVISORY);
    map.put("diagnoseAutomation", ToolCategory.ADVISORY);
    map.put("getProgress", ToolCategory.ADVISORY);
    map.put("queryDataCatalog", ToolCategory.ADVISORY);
    map.put("generateReport", ToolCategory.ADVISORY);

    // Calculation tools — compute results, no persistent state changes
    map.put("runFlash", ToolCategory.CALCULATION);
    map.put("runBatch", ToolCategory.CALCULATION);
    map.put("getPropertyTable", ToolCategory.CALCULATION);
    map.put("getPhaseEnvelope", ToolCategory.CALCULATION);
    map.put("runProcess", ToolCategory.CALCULATION);
    map.put("runPVT", ToolCategory.CALCULATION);
    map.put("runFlowAssurance", ToolCategory.CALCULATION);
    map.put("calculateStandard", ToolCategory.CALCULATION);
    map.put("runPipeline", ToolCategory.CALCULATION);
    map.put("runReservoir", ToolCategory.CALCULATION);
    map.put("runFieldEconomics", ToolCategory.CALCULATION);
    map.put("runDynamic", ToolCategory.CALCULATION);
    map.put("runBioprocess", ToolCategory.CALCULATION);
    map.put("crossValidateModels", ToolCategory.CALCULATION);
    map.put("runParametricStudy", ToolCategory.CALCULATION);
    map.put("sizeEquipment", ToolCategory.CALCULATION);
    map.put("compareProcesses", ToolCategory.CALCULATION);
    map.put("generateVisualization", ToolCategory.CALCULATION);

    // Execution tools — modify state, write data
    map.put("setSimulationVariable", ToolCategory.EXECUTION);
    map.put("saveSimulationState", ToolCategory.EXECUTION);
    map.put("compareSimulationStates", ToolCategory.EXECUTION);
    map.put("manageSession", ToolCategory.EXECUTION);
    map.put("solveTask", ToolCategory.EXECUTION);
    map.put("composeWorkflow", ToolCategory.EXECUTION);

    // Platform tools — security, persistence, multi-server
    map.put("streamSimulation", ToolCategory.PLATFORM);
    map.put("composeMultiServerWorkflow", ToolCategory.PLATFORM);
    map.put("manageSecurity", ToolCategory.PLATFORM);
    map.put("manageState", ToolCategory.PLATFORM);
    map.put("manageValidationProfile", ToolCategory.PLATFORM);
    map.put("runPlugin", ToolCategory.PLATFORM);

    return Collections.unmodifiableMap(map);
  }

  /**
   * Gets the current deployment mode.
   *
   * @return the active deployment mode
   */
  public static DeploymentMode getActiveMode() {
    return activeMode;
  }

  /**
   * Sets the deployment mode. This changes which tools are accessible and which governance controls
   * are active.
   *
   * @param mode the deployment mode to activate
   */
  public static void setActiveMode(DeploymentMode mode) {
    activeMode = mode;
    switch (mode) {
      case DESKTOP_ENGINEER:
        autoValidationEnabled = true;
        approvalGateEnabled = false;
        break;
      case STUDY_TEAM:
        autoValidationEnabled = true;
        approvalGateEnabled = false;
        break;
      case DIGITAL_TWIN:
        autoValidationEnabled = true;
        approvalGateEnabled = true;
        break;
      case ENTERPRISE:
        autoValidationEnabled = true;
        approvalGateEnabled = true;
        break;
    }
  }

  /**
   * Checks whether a tool is accessible in the current deployment mode.
   *
   * @param toolName the MCP tool name
   * @return true if the tool is allowed
   */
  public static boolean isToolAllowed(String toolName) {
    switch (activeMode) {
      case DESKTOP_ENGINEER:
        return true; // all tools available
      case STUDY_TEAM:
        // All except raw security management
        return !"manageSecurity".equals(toolName);
      case DIGITAL_TWIN:
        // Advisory + Calculation only, plus read-only state operations
        ToolCategory cat = TOOL_CATEGORIES.get(toolName);
        return cat == ToolCategory.ADVISORY || cat == ToolCategory.CALCULATION
            || "getSimulationVariable".equals(toolName) || "listSimulationUnits".equals(toolName)
            || "listUnitVariables".equals(toolName);
      case ENTERPRISE:
        // Industrial core only
        return INDUSTRIAL_CORE.contains(toolName);
      default:
        return true;
    }
  }

  /**
   * Checks whether a tool invocation requires human approval in the current mode.
   *
   * @param toolName the MCP tool name
   * @return true if approval is required before execution
   */
  public static boolean requiresApproval(String toolName) {
    if (!approvalGateEnabled) {
      return false;
    }
    ToolCategory cat = TOOL_CATEGORIES.get(toolName);
    return cat == ToolCategory.EXECUTION || cat == ToolCategory.PLATFORM;
  }

  /**
   * Returns whether auto-validation is enabled.
   *
   * @return true if every calculation/execution tool auto-validates results
   */
  public static boolean isAutoValidationEnabled() {
    return autoValidationEnabled;
  }

  /**
   * Gets the category for a specific tool.
   *
   * @param toolName the MCP tool name
   * @return the tool category, or null if unknown
   */
  public static ToolCategory getToolCategory(String toolName) {
    return TOOL_CATEGORIES.get(toolName);
  }

  /**
   * Returns the set of tools in the industrial core.
   *
   * @return unmodifiable set of tool names
   */
  public static Set<String> getIndustrialCore() {
    return INDUSTRIAL_CORE;
  }

  /**
   * Returns a JSON description of all deployment modes and their tool access.
   *
   * @return JSON string describing all profiles
   */
  public static String describeProfiles() {
    JsonObject root = new JsonObject();
    root.addProperty("status", "success");
    root.addProperty("activeMode", activeMode.name());
    root.addProperty("autoValidation", autoValidationEnabled);
    root.addProperty("approvalGateEnabled", approvalGateEnabled);

    // List profiles
    JsonArray profiles = new JsonArray();
    for (DeploymentMode mode : DeploymentMode.values()) {
      JsonObject profile = new JsonObject();
      profile.addProperty("name", mode.name());
      profile.addProperty("description", getProfileDescription(mode));
      profile.addProperty("toolCount", countAllowedTools(mode));
      profile.addProperty("approvalGate",
          mode == DeploymentMode.DIGITAL_TWIN || mode == DeploymentMode.ENTERPRISE);
      profile.addProperty("auditLogging", mode != DeploymentMode.DESKTOP_ENGINEER);
      profile.addProperty("sessionIsolation", mode != DeploymentMode.DESKTOP_ENGINEER);
      profile.addProperty("rateLimiting", mode == DeploymentMode.ENTERPRISE);
      profiles.add(profile);
    }
    root.add("profiles", profiles);

    // Tool classification
    JsonObject toolClassification = new JsonObject();
    for (Map.Entry<String, ToolCategory> entry : TOOL_CATEGORIES.entrySet()) {
      toolClassification.addProperty(entry.getKey(), entry.getValue().name());
    }
    root.add("toolClassification", toolClassification);

    // Industrial core
    JsonArray core = new JsonArray();
    for (String tool : INDUSTRIAL_CORE) {
      core.add(tool);
    }
    root.add("industrialCore", core);

    return GSON.toJson(root);
  }

  /**
   * Returns a human-readable description of a deployment mode.
   *
   * @param mode the deployment mode
   * @return description string
   */
  private static String getProfileDescription(DeploymentMode mode) {
    switch (mode) {
      case DESKTOP_ENGINEER:
        return "Full access for a single engineer. All 42 tools available. "
            + "Auto-validation on. No approval gates. Ideal for study work and exploration.";
      case STUDY_TEAM:
        return "Collaborative mode for engineering teams. All tools except raw security "
            + "management. Session isolation and audit logging enabled. Auto-validation on.";
      case DIGITAL_TWIN:
        return "Read-heavy advisory mode for plant operations support. Advisory and "
            + "calculation tools only. Execution tools require human approval. "
            + "Ideal for operator assistance and what-if analysis.";
      case ENTERPRISE:
        return "Restricted mode for governed deployments. Industrial core tools only "
            + "(20 tools). Approval gates on all state-modifying operations. "
            + "Rate limiting and full audit logging. Recommended for enterprise integration.";
      default:
        return "Unknown mode";
    }
  }

  /**
   * Counts the number of tools allowed in a given mode.
   *
   * @param mode the deployment mode
   * @return count of allowed tools
   */
  private static int countAllowedTools(DeploymentMode mode) {
    DeploymentMode saved = activeMode;
    activeMode = mode;
    int count = 0;
    for (String tool : TOOL_CATEGORIES.keySet()) {
      if (isToolAllowed(tool)) {
        count++;
      }
    }
    activeMode = saved;
    return count;
  }
}
