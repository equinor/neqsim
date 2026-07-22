package neqsim.mcp.runners;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.SimulationResult;
import neqsim.process.safety.hazid.HazopConsequenceAutoPopulator;
import neqsim.process.safety.hazid.HazopConsequenceFinding;
import neqsim.process.safety.hazid.HazopQuantificationLimits;

/**
 * MCP runner for quantifying a single HAZOP deviation against a NeqSim process simulation.
 *
 * <p>
 * Where {@link HAZOPStudyRunner} generates a whole worksheet, this runner answers the focused question an interactive
 * P&amp;ID Safety Analyser / HAZOP tool asks: <i>"for this node and this guide-word/parameter deviation, what does the
 * simulation say the consequence is?"</i> It builds and runs the supplied {@link ProcessSystem}, calls
 * {@link HazopConsequenceAutoPopulator#quantify}, then filters the findings to the requested node and deviation,
 * returning the quantified consequence with its design limit, verdict, governing standard and the auditable basis for
 * the limit.
 * </p>
 *
 * <p>
 * Input JSON shape:
 * </p>
 *
 * <pre>
 * {
 *   "process": { ... ProcessSystem JSON ... },
 *   "nodeTag": "K-100",            // optional unit name or node-id fragment filter
 *   "guideWord": "MORE",           // optional HAZOP guide word filter
 *   "parameter": "TEMPERATURE",    // optional process parameter filter
 *   "limits": {                     // optional design-limit policy
 *     "maxDischargeTemperatureC": 150.0,
 *     "minDesignMetalTemperatureC": -46.0,
 *     "maxDischargeTemperatureByUnit": { "K-100": 170.0 },
 *     "minDesignMetalTemperatureByUnit": { "V-100": -50.0 }
 *   }
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public final class HazopScenarioRunner {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();

  /** Schema version of the JSON contract returned by this runner. */
  public static final String SCHEMA_VERSION = "1.0";

  /**
   * Private constructor for utility class.
   */
  private HazopScenarioRunner() {
  }

  /**
   * Quantify a HAZOP deviation from JSON input.
   *
   * @param json JSON containing a process definition and optional node/deviation filters and design limits
   * @return JSON string with the matching quantified findings, or an error object
   */
  public static String run(String json) {
    if (json == null || json.trim().isEmpty()) {
      return errorJson("JSON input is null or empty");
    }
    try {
      JsonObject input = JsonParser.parseString(json).getAsJsonObject();
      String processJson = extractProcessJson(input);
      SimulationResult simulation = ProcessSystem.fromJsonAndRun(processJson);
      if (simulation.isError() || simulation.getProcessSystem() == null) {
        return errorJson("Process simulation failed before HAZOP scenario evaluation");
      }
      ProcessSystem process = simulation.getProcessSystem();

      HazopQuantificationLimits limits = parseLimits(input);
      HazopConsequenceAutoPopulator populator = new HazopConsequenceAutoPopulator();
      List<HazopConsequenceFinding> findings = populator.quantify(process, limits);

      String nodeTag = optString(input, "nodeTag", null);
      String guideWord = optString(input, "guideWord", null);
      String parameter = optString(input, "parameter", null);

      List<Map<String, Object>> matched = new ArrayList<Map<String, Object>>();
      for (HazopConsequenceFinding finding : findings) {
        if (!matchesNode(finding, nodeTag)) {
          continue;
        }
        if (!matchesEnum(finding.getGuideWord().name(), guideWord)) {
          continue;
        }
        if (!matchesEnum(finding.getParameter().name(), parameter)) {
          continue;
        }
        matched.add(toMap(finding));
      }

      Map<String, Object> output = new LinkedHashMap<String, Object>();
      output.put("schemaVersion", SCHEMA_VERSION);
      output.put("status", "ok");
      Map<String, Object> request = new LinkedHashMap<String, Object>();
      request.put("nodeTag", nodeTag);
      request.put("guideWord", guideWord);
      request.put("parameter", parameter);
      output.put("request", request);
      output.put("limitsPolicy", JsonParser.parseString(limits.toJson()));
      output.put("matchCount", Integer.valueOf(matched.size()));
      output.put("findings", matched);
      if (matched.isEmpty()) {
        output.put("note",
            "No quantifiable finding matched the requested node/deviation. Only compressor/expander MORE TEMPERATURE "
                + "and valve LESS TEMPERATURE deviations are currently simulation-backed.");
      }
      return GSON.toJson(output);
    } catch (Exception e) {
      return errorJson("HAZOP scenario evaluation failed: " + e.getMessage());
    }
  }

  /**
   * Convert a finding to an insertion-ordered map for stable JSON serialisation.
   *
   * @param finding the finding to convert
   * @return a map of the finding fields
   */
  private static Map<String, Object> toMap(HazopConsequenceFinding finding) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("nodeId", finding.getNodeId());
    map.put("unitName", finding.getUnitName());
    map.put("guideWord", finding.getGuideWord().name());
    map.put("parameter", finding.getParameter().name());
    map.put("computedValue", Double.valueOf(finding.getComputedValue()));
    map.put("designLimit", Double.valueOf(finding.getDesignLimit()));
    map.put("valueUnit", finding.getValueUnit());
    map.put("verdict", finding.getVerdict().name());
    map.put("calculator", finding.getCalculator());
    map.put("standardReference", finding.getStandardReference());
    map.put("limitBasis", finding.getLimitBasis());
    map.put("message", finding.getMessage());
    return map;
  }

  /**
   * Test whether a finding matches the requested node filter, comparing both the unit name and the node-id fragment.
   *
   * @param finding the finding to test
   * @param nodeTag the requested node filter, or null/empty to match any node
   * @return true if the finding matches the node filter
   */
  private static boolean matchesNode(HazopConsequenceFinding finding, String nodeTag) {
    if (nodeTag == null || nodeTag.trim().isEmpty()) {
      return true;
    }
    String tag = nodeTag.trim().toLowerCase(Locale.ROOT);
    String unitName = finding.getUnitName() == null ? "" : finding.getUnitName().toLowerCase(Locale.ROOT);
    String nodeId = finding.getNodeId() == null ? "" : finding.getNodeId().toLowerCase(Locale.ROOT);
    return unitName.equals(tag) || nodeId.contains(tag);
  }

  /**
   * Test whether an enum constant name matches a requested filter, case-insensitively.
   *
   * @param actual the enum constant name from the finding
   * @param requested the requested filter, or null/empty to match any value
   * @return true if the value matches the filter
   */
  private static boolean matchesEnum(String actual, String requested) {
    if (requested == null || requested.trim().isEmpty()) {
      return true;
    }
    return actual != null && actual.equalsIgnoreCase(requested.trim());
  }

  /**
   * Parse the optional design-limit policy from the input JSON.
   *
   * @param input the input JSON object
   * @return a populated limits holder (defaults when no policy is supplied)
   */
  private static HazopQuantificationLimits parseLimits(JsonObject input) {
    HazopQuantificationLimits limits = new HazopQuantificationLimits();
    if (input == null || !input.has("limits") || !input.get("limits").isJsonObject()) {
      return limits;
    }
    JsonObject node = input.getAsJsonObject("limits");
    if (node.has("maxDischargeTemperatureC") && node.get("maxDischargeTemperatureC").isJsonPrimitive()) {
      limits.setMaxDischargeTemperatureC(node.get("maxDischargeTemperatureC").getAsDouble());
    }
    if (node.has("minDesignMetalTemperatureC") && node.get("minDesignMetalTemperatureC").isJsonPrimitive()) {
      limits.setMinDesignMetalTemperatureC(node.get("minDesignMetalTemperatureC").getAsDouble());
    }
    applyOverrides(node, "maxDischargeTemperatureByUnit", limits, true);
    applyOverrides(node, "minDesignMetalTemperatureByUnit", limits, false);
    return limits;
  }

  /**
   * Apply a map of per-unit limit overrides to the limits holder.
   *
   * @param node the limits JSON object
   * @param key the override-map key to read
   * @param limits the limits holder to update
   * @param isDischarge true to set maximum discharge temperature overrides, false for MDMT overrides
   */
  private static void applyOverrides(JsonObject node, String key, HazopQuantificationLimits limits,
      boolean isDischarge) {
    if (!node.has(key) || !node.get(key).isJsonObject()) {
      return;
    }
    JsonObject overrides = node.getAsJsonObject(key);
    for (Map.Entry<String, JsonElement> entry : overrides.entrySet()) {
      if (entry.getValue() == null || !entry.getValue().isJsonPrimitive()) {
        continue;
      }
      double value = entry.getValue().getAsDouble();
      if (isDischarge) {
        limits.setMaxDischargeTemperatureC(entry.getKey(), value);
      } else {
        limits.setMinDesignMetalTemperatureC(entry.getKey(), value);
      }
    }
  }

  /**
   * Extract the process-definition JSON from the input, accepting either a "process" property or a bare process
   * definition.
   *
   * @param input the input JSON object
   * @return the process-definition JSON string
   */
  private static String extractProcessJson(JsonObject input) {
    if (input.has("process") && input.get("process").isJsonObject()) {
      return input.getAsJsonObject("process").toString();
    }
    return input.toString();
  }

  /**
   * Read an optional string property from a JSON object.
   *
   * @param obj the JSON object
   * @param key the property name
   * @param fallback the value to return when the property is missing or not a primitive
   * @return the string value, or {@code fallback}
   */
  private static String optString(JsonObject obj, String key, String fallback) {
    if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
      return obj.get(key).getAsString();
    }
    return fallback;
  }

  /**
   * Build a standard error response.
   *
   * @param message the error message
   * @return a JSON error object
   */
  private static String errorJson(String message) {
    Map<String, Object> error = new LinkedHashMap<String, Object>();
    error.put("schemaVersion", SCHEMA_VERSION);
    error.put("status", "error");
    error.put("error", message);
    return GSON.toJson(error);
  }
}
