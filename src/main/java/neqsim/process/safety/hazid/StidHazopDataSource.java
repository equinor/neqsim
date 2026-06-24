package neqsim.process.safety.hazid;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Normalized STID and P&amp;ID-to-HAZOP JSON bridge that builds {@link HAZOPTemplate} nodes from externally normalized
 * evidence.
 *
 * <p>
 * This class does not connect to STID, document-management systems, or P&amp;ID tools directly. Those systems should
 * normalize their tag, node, and worksheet evidence into JSON and pass it to this deterministic Java adapter so the
 * resulting HAZOP nodes are fully traceable and reproducible.
 * </p>
 *
 * <p>
 * Expected JSON shape (all keys optional, tolerant to missing values):
 * </p>
 *
 * <pre>
 * {
 *   "projectName": "Example",
 *   "installationCode": "AAA",
 *   "nodes": [
 *     {
 *       "nodeId": "Node-01: Inlet separator",
 *       "tag": "VA-2001",
 *       "designIntent": "Separate inlet gas and liquid at 60 bara",
 *       "deviations": [
 *         {
 *           "guideWord": "MORE",
 *           "parameter": "LEVEL",
 *           "cause": "Liquid outlet valve fails closed",
 *           "consequence": "Liquid carry-over to compressor",
 *           "safeguard": "LAHH-2001 trips inlet valve",
 *           "recommendation": "Verify trip set-point"
 *         }
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class StidHazopDataSource {
  /** Top-level array keys interpreted as HAZOP nodes. */
  public static final String[] NODE_ARRAY_KEYS = new String[] { "nodes", "hazopNodes", "items" };

  /** Object keys interpreted as the HAZOP deviation array within a node. */
  public static final String[] DEVIATION_ARRAY_KEYS = new String[] { "deviations", "deviationRows", "rows" };

  private final JsonObject source;

  /**
   * Creates a source from JSON text.
   *
   * @param json normalized STID or P&amp;ID-to-HAZOP JSON text
   */
  public StidHazopDataSource(String json) {
    this(JsonParser.parseString(json).getAsJsonObject());
  }

  /**
   * Creates a source from a JSON object.
   *
   * @param source normalized JSON object, or null for an empty source
   */
  public StidHazopDataSource(JsonObject source) {
    this.source = source == null ? new JsonObject() : source;
  }

  /**
   * Reads the optional project name.
   *
   * @return project name, or empty string when not present
   */
  public String getProjectName() {
    return source.has("projectName") ? source.get("projectName").getAsString() : "";
  }

  /**
   * Reads the optional installation code used for traceability.
   *
   * @return installation code, or empty string when not present
   */
  public String getInstallationCode() {
    return source.has("installationCode") ? source.get("installationCode").getAsString() : "";
  }

  /**
   * Builds HAZOP node templates from the normalized JSON.
   *
   * @return list of HAZOP node templates, one per node record (never null)
   */
  public List<HAZOPTemplate> read() {
    List<HAZOPTemplate> nodes = new ArrayList<HAZOPTemplate>();
    JsonArray array = firstArray(source, NODE_ARRAY_KEYS);
    if (array == null) {
      return nodes;
    }
    int index = 1;
    for (int i = 0; i < array.size(); i++) {
      JsonElement element = array.get(i);
      if (!element.isJsonObject()) {
        continue;
      }
      HAZOPTemplate node = buildNode(element.getAsJsonObject(), index);
      nodes.add(node);
      index++;
    }
    return nodes;
  }

  /**
   * Builds a single HAZOP node template from a node record.
   *
   * @param record node JSON object
   * @param index 1-based node index used for default identifiers
   * @return populated HAZOP node template
   */
  private HAZOPTemplate buildNode(JsonObject record, int index) {
    String tag = optString(record, "tag", "");
    String nodeId = optString(record, "nodeId", "");
    if (nodeId.isEmpty()) {
      String label = optString(record, "name", tag);
      nodeId = String.format(Locale.ROOT, "Node-%02d: %s", index, label.isEmpty() ? "Unnamed" : label);
    }
    String designIntent = optString(record, "designIntent", "");
    if (designIntent.isEmpty()) {
      designIntent = optString(record, "description", "Design intent to be confirmed.");
    }
    HAZOPTemplate node = new HAZOPTemplate(nodeId, designIntent);
    JsonArray deviations = firstArray(record, DEVIATION_ARRAY_KEYS);
    if (deviations != null) {
      for (int i = 0; i < deviations.size(); i++) {
        JsonElement element = deviations.get(i);
        if (element.isJsonObject()) {
          addDeviation(node, element.getAsJsonObject());
        }
      }
    }
    return node;
  }

  /**
   * Adds one deviation row to a node from a deviation record.
   *
   * @param node target HAZOP node
   * @param record deviation JSON object
   */
  private void addDeviation(HAZOPTemplate node, JsonObject record) {
    HAZOPTemplate.GuideWord guideWord = parseGuideWord(optString(record, "guideWord", ""));
    HAZOPTemplate.Parameter parameter = parseParameter(optString(record, "parameter", ""));
    if (guideWord == null || parameter == null) {
      return;
    }
    String cause = optString(record, "cause", "TBD");
    String consequence = optString(record, "consequence", "TBD");
    String safeguard = optString(record, "safeguard", "TBD");
    String recommendation = record.has("recommendation") && !record.get("recommendation").isJsonNull()
        ? record.get("recommendation").getAsString()
        : null;
    node.addDeviation(guideWord, parameter, cause, consequence, safeguard, recommendation);
  }

  /**
   * Parses a HAZOP guide-word, tolerant to spacing, case, and underscores.
   *
   * @param raw raw guide-word text
   * @return matching guide-word, or null when unrecognized
   */
  static HAZOPTemplate.GuideWord parseGuideWord(String raw) {
    String key = normalizeToken(raw);
    if (key.isEmpty()) {
      return null;
    }
    for (HAZOPTemplate.GuideWord value : HAZOPTemplate.GuideWord.values()) {
      if (value.name().equals(key)) {
        return value;
      }
    }
    return null;
  }

  /**
   * Parses a HAZOP parameter, tolerant to spacing, case, and underscores.
   *
   * @param raw raw parameter text
   * @return matching parameter, or null when unrecognized
   */
  static HAZOPTemplate.Parameter parseParameter(String raw) {
    String key = normalizeToken(raw);
    if (key.isEmpty()) {
      return null;
    }
    for (HAZOPTemplate.Parameter value : HAZOPTemplate.Parameter.values()) {
      if (value.name().equals(key)) {
        return value;
      }
    }
    return null;
  }

  /**
   * Normalizes an enum-like token to upper-case with underscores.
   *
   * @param raw raw token text
   * @return normalized token, or empty string when null/blank
   */
  private static String normalizeToken(String raw) {
    if (raw == null) {
      return "";
    }
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) {
      return "";
    }
    return trimmed.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
  }

  /**
   * Returns the first JSON array present under any of the candidate keys.
   *
   * @param object source object
   * @param keys candidate array keys
   * @return first matching JSON array, or null when none present
   */
  private static JsonArray firstArray(JsonObject object, String[] keys) {
    for (String key : keys) {
      if (object.has(key) && object.get(key).isJsonArray()) {
        return object.getAsJsonArray(key);
      }
    }
    return null;
  }

  /**
   * Reads an optional string field with a fallback default.
   *
   * @param object source object
   * @param key field key
   * @param fallback default value when the field is absent or null
   * @return field value, or the fallback
   */
  private static String optString(JsonObject object, String key, String fallback) {
    if (object.has(key) && !object.get(key).isJsonNull()) {
      return object.get(key).getAsString();
    }
    return fallback;
  }
}
