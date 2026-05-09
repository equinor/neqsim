package neqsim.mcp.runners;

import java.util.Arrays;
import java.util.Collections;
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

  /**
   * Explicit trust tier for each tool. This separation is enforced in code, not just documented.
   */
  public enum ToolTier {
    /**
     * Tier 1 — Trusted core. Validated against NIST/experimental data, documented accuracy bounds,
     * clear error behavior. Available in all deployment modes including ENTERPRISE.
     */
    TRUSTED_CORE,
    /**
     * Tier 2 — Engineering advanced. Tested against literature/industry cases, suitable for
     * screening studies. Available in DESKTOP_ENGINEER and STUDY_TEAM.
     */
    ENGINEERING_ADVANCED,
    /**
     * Tier 3 — Experimental/research. Functional but limited validation, or high-autonomy tools
     * that are hard to validate. Available in DESKTOP_ENGINEER only.
     */
    EXPERIMENTAL
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
   * Tier 1 — Trusted core. Validated against NIST/experimental data, documented accuracy bounds,
   * clear error behavior. This is the smallest credible surface for enterprise adoption.
   */
  private static final Set<String> INDUSTRIAL_CORE =
      Collections.unmodifiableSet(new HashSet<>(Arrays.asList("runFlash", "runProcess",
          "validateInput", "validateResults", "calculateStandard", "searchComponents",
          "getCapabilities", "getExample", "getSchema", "getPropertyTable", "getPhaseEnvelope",
          "getBenchmarkTrust", "checkToolAccess", "manageIndustrialProfile", "listSimulationUnits",
          "listUnitVariables", "getSimulationVariable", "compareSimulationStates",
          "diagnoseAutomation", "getAutomationLearningReport", "getProgress")));

  /**
   * Tier 2 — Engineering advanced. Tested against literature/industry cases, suitable for screening
   * studies and engineering workflows. Available in DESKTOP_ENGINEER and STUDY_TEAM.
   */
  private static final Set<String> ENGINEERING_ADVANCED =
      Collections.unmodifiableSet(new HashSet<>(Arrays.asList("runPVT", "runPipeline",
          "runFlowAssurance", "crossValidateModels", "runParametricStudy", "runBatch",
          "sizeEquipment", "compareProcesses", "generateReport", "generateVisualization",
          "queryDataCatalog", "setSimulationVariable", "saveSimulationState", "runMaterialsReview",
          "runRelief", "runLOPA", "runSIL", "runRiskMatrix", "runFlareNetwork", "runHAZOP",
          "runBarrierRegister", "runSafetySystemPerformance")));

  /**
   * Tier 3 — Experimental/research. Functional but limited validation, or high-autonomy tools that
   * are difficult to validate for industrial use. Available in DESKTOP_ENGINEER only.
   */
  private static final Set<String> EXPERIMENTAL_TOOLS =
      Collections.unmodifiableSet(new HashSet<>(Arrays.asList("runReservoir", "runFieldEconomics",
          "runDynamic", "runBioprocess", "solveTask", "composeWorkflow", "manageSession",
          "streamSimulation", "composeMultiServerWorkflow", "manageSecurity", "manageState",
          "manageValidationProfile", "runPlugin", "bridgeTaskWorkflow")));

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
    map.put("bridgeTaskWorkflow", ToolCategory.ADVISORY);

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
    map.put("runMaterialsReview", ToolCategory.CALCULATION);
    map.put("runReservoir", ToolCategory.CALCULATION);
    map.put("runFieldEconomics", ToolCategory.CALCULATION);
    map.put("runDynamic", ToolCategory.CALCULATION);
    map.put("runBioprocess", ToolCategory.CALCULATION);
    map.put("crossValidateModels", ToolCategory.CALCULATION);
    map.put("runParametricStudy", ToolCategory.CALCULATION);
    map.put("sizeEquipment", ToolCategory.CALCULATION);
    map.put("compareProcesses", ToolCategory.CALCULATION);
    map.put("generateVisualization", ToolCategory.CALCULATION);
    // Process safety tools (API 520/521, IEC 61508/61511, ISO 31000)
    map.put("runRelief", ToolCategory.CALCULATION);
    map.put("runLOPA", ToolCategory.CALCULATION);
    map.put("runSIL", ToolCategory.CALCULATION);
    map.put("runRiskMatrix", ToolCategory.CALCULATION);
    map.put("runFlareNetwork", ToolCategory.CALCULATION);
    map.put("runHAZOP", ToolCategory.CALCULATION);
    map.put("runBarrierRegister", ToolCategory.CALCULATION);
    map.put("runSafetySystemPerformance", ToolCategory.CALCULATION);

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
        return true; // all tiers available — engineer chooses risk
      case STUDY_TEAM:
        // Tier 1 + Tier 2 only; experimental and platform tools blocked
        return INDUSTRIAL_CORE.contains(toolName) || ENGINEERING_ADVANCED.contains(toolName);
      case DIGITAL_TWIN:
        // Advisory + Calculation from Tier 1 only — no write, no experimental
        ToolCategory cat = TOOL_CATEGORIES.get(toolName);
        return INDUSTRIAL_CORE.contains(toolName)
            && (cat == ToolCategory.ADVISORY || cat == ToolCategory.CALCULATION);
      case ENTERPRISE:
        // Tier 1 only
        return INDUSTRIAL_CORE.contains(toolName);
      default:
        return true;
    }
  }

  /**
   * Enforces tool access for the current deployment mode. Returns an error JSON string if the tool
   * is blocked, or null if the tool is allowed. Call at the top of every @Tool method to prove
   * governance is enforced in code, not just described in docs.
   *
   * @param toolName the MCP tool name being invoked
   * @return null if allowed, or a JSON error string if blocked
   */
  public static String enforceAccess(String toolName) {
    if (isToolAllowed(toolName)) {
      return null;
    }
    JsonObject error = new JsonObject();
    error.addProperty("status", "blocked");
    error.addProperty("tool", toolName);
    error.addProperty("mode", activeMode.name());
    ToolTier tier = getToolTier(toolName);
    error.addProperty("tier", tier != null ? tier.name() : "UNKNOWN");
    error.addProperty("reason",
        "Tool '" + toolName + "' is not available in " + activeMode.name()
            + " mode. This mode allows "
            + (activeMode == DeploymentMode.ENTERPRISE ? "Tier 1 (TRUSTED_CORE) only."
                : activeMode == DeploymentMode.STUDY_TEAM
                    ? "Tier 1 (TRUSTED_CORE) and Tier 2 (ENGINEERING_ADVANCED) only."
                    : "a restricted subset of tools."));
    error.addProperty("remediation",
        "Switch to DESKTOP_ENGINEER mode or request approval for this tool.");
    return GSON.toJson(error);
  }

  /**
   * Returns the tier classification for a tool.
   *
   * @param toolName the MCP tool name
   * @return the tier, or null if the tool is not classified
   */
  public static ToolTier getToolTier(String toolName) {
    if (INDUSTRIAL_CORE.contains(toolName)) {
      return ToolTier.TRUSTED_CORE;
    }
    if (ENGINEERING_ADVANCED.contains(toolName)) {
      return ToolTier.ENGINEERING_ADVANCED;
    }
    if (EXPERIMENTAL_TOOLS.contains(toolName)) {
      return ToolTier.EXPERIMENTAL;
    }
    return null;
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
   * Returns the set of Tier 2 (engineering advanced) tools.
   *
   * @return unmodifiable set of tool names
   */
  public static Set<String> getEngineeringAdvanced() {
    return ENGINEERING_ADVANCED;
  }

  /**
   * Returns the set of Tier 3 (experimental) tools.
   *
   * @return unmodifiable set of tool names
   */
  public static Set<String> getExperimentalTools() {
    return EXPERIMENTAL_TOOLS;
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

    // Tier 1 — Trusted Core
    JsonArray core = new JsonArray();
    for (String tool : INDUSTRIAL_CORE) {
      core.add(tool);
    }
    root.add("tier1_trustedCore", core);

    // Tier 2 — Engineering Advanced
    JsonArray advanced = new JsonArray();
    for (String tool : ENGINEERING_ADVANCED) {
      advanced.add(tool);
    }
    root.add("tier2_engineeringAdvanced", advanced);

    // Tier 3 — Experimental
    JsonArray experimental = new JsonArray();
    for (String tool : EXPERIMENTAL_TOOLS) {
      experimental.add(tool);
    }
    root.add("tier3_experimental", experimental);

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
        return "Full access for a single engineer. Core, advanced, and experimental "
            + "tools available with clear tier labeling. Auto-validation on. "
            + "No approval gates. Ideal for study work and exploration.";
      case STUDY_TEAM:
        return "Collaborative mode for engineering teams. Core and advanced tools "
            + "available. Session isolation and audit logging enabled. "
            + "Auto-validation enforced on all calculations.";
      case DIGITAL_TWIN:
        return "Advisory-only mode for plant operations support. Advisory and "
            + "calculation tools only — no direct plant control, no write-back to "
            + "operational systems, no autonomous action execution without separate "
            + "approval architecture. Ideal for operator decision support and "
            + "what-if analysis.";
      case ENTERPRISE:
        return "Restricted mode for governed deployments. Approved industrial core "
            + "tools only. Approval gates on all state-modifying operations. "
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
