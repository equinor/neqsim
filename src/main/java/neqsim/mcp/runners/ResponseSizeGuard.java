package neqsim.mcp.runners;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Keeps MCP tool responses within a size an agent and a transport can actually handle.
 *
 * <p>
 * A full plant model produces a per-unit property report measured in megabytes. Such a response is useless to a
 * language model — it exhausts the context window before any reasoning happens — and a single multi-megabyte JSON-RPC
 * line can break the stdio transport, ending the session rather than returning an answer.
 * </p>
 *
 * <p>
 * When a response exceeds the configured limit, the bulky payload members are dropped in descending size order until
 * the response fits. The envelope (status, tool, validation, quality gate, provenance) is never dropped, and a
 * {@code truncation} block records what was omitted and how to retrieve it selectively — normally by registering the
 * model with {@code manageModel} and drilling in through {@code listSimulationUnits} and {@code getSimulationVariable}.
 * </p>
 *
 * <p>
 * The {@code getCapabilities} manifest uses discovery-specific recovery guidance and retains its Phase 0 evidence
 * inventory because that contract has no separate selective-retrieval route.
 * </p>
 *
 * <p>
 * The limit is configurable through the {@code neqsim.mcp.maxResponseBytes} system property or the
 * {@code NEQSIM_MCP_MAX_RESPONSE_BYTES} environment variable. Set it to {@code 0} to disable trimming.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class ResponseSizeGuard {

  private static final Gson GSON = new GsonBuilder().serializeSpecialFloatingPointValues().create();

  /** Default maximum serialized response size in bytes. */
  private static final int DEFAULT_MAX_BYTES = 262144;

  /** Configured maximum serialized response size in bytes; 0 disables trimming. */
  private static final int MAX_BYTES = readLimit();

  /** Envelope members that must survive trimming. */
  private static final List<String> PROTECTED_FIELDS = Collections
      .unmodifiableList(java.util.Arrays.asList("apiVersion", "status", "tool", "message", "provenance", "validation",
          "qualityGate", "warnings", "errors", "truncation"));

  /** Discovery members that have no equivalent selective-retrieval route. */
  private static final List<String> PROTECTED_CAPABILITY_FIELDS = Collections
      .unmodifiableList(java.util.Arrays.asList("phase0EvidenceInventory"));

  /**
   * Private constructor — utility class.
   */
  private ResponseSizeGuard() {
  }

  /**
   * Returns the configured response size limit.
   *
   * @return maximum serialized bytes, or 0 when trimming is disabled
   */
  public static int getMaxBytes() {
    return MAX_BYTES;
  }

  /**
   * Trims a response in place when it exceeds the configured limit.
   *
   * @param response the response object to inspect and possibly trim
   * @param toolName the MCP tool that produced the response
   * @return true when the response was trimmed
   */
  public static boolean enforce(JsonObject response, String toolName) {
    if (response == null || MAX_BYTES <= 0) {
      return false;
    }
    int size = serializedSize(response);
    if (size <= MAX_BYTES) {
      return false;
    }

    JsonArray omitted = new JsonArray();
    for (String field : trimCandidates(response, toolName)) {
      JsonElement removed = response.remove(field);
      if (removed == null) {
        continue;
      }
      JsonObject entry = new JsonObject();
      entry.addProperty("field", field);
      entry.addProperty("approximateBytes", serializedSize(removed));
      entry.addProperty("summary", describe(removed));
      omitted.add(entry);

      // Mirror the removal into the canonical data block so both views stay consistent.
      if (response.has("data") && response.get("data").isJsonObject()) {
        response.getAsJsonObject("data").remove(field);
      }
      size = serializedSize(response);
      if (size <= MAX_BYTES) {
        break;
      }
    }

    if (omitted.size() == 0) {
      return false;
    }

    JsonObject truncation = new JsonObject();
    truncation.addProperty("truncated", true);
    truncation.addProperty("reason",
        "Response exceeded the " + MAX_BYTES + " byte limit. Large payloads exhaust an agent's context "
            + "and can break the stdio transport, so bulk detail was omitted rather than returned.");
    truncation.addProperty("originalBytes", size + estimatedBytes(omitted));
    truncation.addProperty("returnedBytes", size);
    truncation.addProperty("limitBytes", MAX_BYTES);
    truncation.add("omitted", omitted);
    truncation.addProperty("howToRetrieve", recoveryGuidance(toolName));
    truncation.addProperty("configuration",
        "Raise or disable the limit with neqsim.mcp.maxResponseBytes (0 disables trimming).");
    response.add("truncation", truncation);

    JsonArray warnings = response.has("warnings") && response.get("warnings").isJsonArray()
        ? response.getAsJsonArray("warnings")
        : new JsonArray();
    warnings.add("Response truncated for tool '" + toolName + "'. See the 'truncation' block.");
    response.add("warnings", warnings);
    return true;
  }

  /**
   * Lists trimmable members in descending serialized size.
   *
   * @param response the response object
   * @param toolName the MCP tool that produced the response
   * @return field names eligible for removal, largest first
   */
  private static List<String> trimCandidates(JsonObject response, String toolName) {
    List<Map.Entry<String, Integer>> sized = new ArrayList<Map.Entry<String, Integer>>();
    for (Map.Entry<String, JsonElement> entry : response.entrySet()) {
      if (isProtected(entry.getKey(), toolName) || "data".equals(entry.getKey())) {
        continue;
      }
      sized.add(new java.util.AbstractMap.SimpleEntry<String, Integer>(entry.getKey(),
          Integer.valueOf(serializedSize(entry.getValue()))));
    }
    Collections.sort(sized, new Comparator<Map.Entry<String, Integer>>() {
      @Override
      public int compare(Map.Entry<String, Integer> left, Map.Entry<String, Integer> right) {
        return right.getValue().compareTo(left.getValue());
      }
    });
    List<String> names = new ArrayList<String>();
    for (Map.Entry<String, Integer> entry : sized) {
      names.add(entry.getKey());
    }
    return names;
  }

  /**
   * Returns whether a response member must survive transport trimming.
   *
   * @param fieldName response member name
   * @param toolName MCP tool that produced the response
   * @return true when the member must not be removed
   */
  private static boolean isProtected(String fieldName, String toolName) {
    return PROTECTED_FIELDS.contains(fieldName)
        || ("getCapabilities".equals(toolName) && PROTECTED_CAPABILITY_FIELDS.contains(fieldName));
  }

  /**
   * Returns selective-retrieval guidance appropriate for the response type.
   *
   * @param toolName MCP tool that produced the response
   * @return recovery guidance for omitted fields
   */
  private static String recoveryGuidance(String toolName) {
    if ("getCapabilities".equals(toolName)) {
      return "Use getSchema and getExample for focused tool contracts, getBenchmarkTrust for "
          + "tool-specific trust evidence, and MCP catalog resources for selective discovery. "
          + "The Phase 0 evidence inventory is retained in this response.";
    }
    return "Register the model with manageModel(action='register'), then read only what you need via "
        + "listSimulationUnits, listUnitVariables and getSimulationVariable on the returned modelId.";
  }

  /**
   * Describes an omitted element so the agent knows what was dropped.
   *
   * @param element the omitted element
   * @return a short structural description
   */
  private static String describe(JsonElement element) {
    if (element.isJsonObject()) {
      JsonObject object = element.getAsJsonObject();
      int shown = 0;
      StringBuilder keys = new StringBuilder();
      for (String key : object.keySet()) {
        if (shown == 5) {
          keys.append(", ...");
          break;
        }
        if (shown > 0) {
          keys.append(", ");
        }
        keys.append(key);
        shown++;
      }
      return "object with " + object.size() + " entries: " + keys;
    }
    if (element.isJsonArray()) {
      return "array with " + element.getAsJsonArray().size() + " entries";
    }
    return "scalar value";
  }

  /**
   * Sums the approximate byte sizes recorded for omitted entries.
   *
   * @param omitted the omitted-entry array
   * @return total approximate bytes
   */
  private static int estimatedBytes(JsonArray omitted) {
    int total = 0;
    for (JsonElement element : omitted) {
      JsonObject entry = element.getAsJsonObject();
      if (entry.has("approximateBytes")) {
        total += entry.get("approximateBytes").getAsInt();
      }
    }
    return total;
  }

  /**
   * Returns the serialized UTF-8 size of an element.
   *
   * @param element the element to measure
   * @return size in bytes
   */
  private static int serializedSize(JsonElement element) {
    return GSON.toJson(element).getBytes(StandardCharsets.UTF_8).length;
  }

  /**
   * Reads the configured limit.
   *
   * @return maximum bytes, or the default when unset or unusable
   */
  private static int readLimit() {
    String raw = System.getProperty("neqsim.mcp.maxResponseBytes");
    if (raw == null || raw.trim().isEmpty()) {
      raw = System.getenv("NEQSIM_MCP_MAX_RESPONSE_BYTES");
    }
    if (raw == null || raw.trim().isEmpty()) {
      return DEFAULT_MAX_BYTES;
    }
    try {
      int parsed = Integer.parseInt(raw.trim());
      return parsed < 0 ? DEFAULT_MAX_BYTES : parsed;
    } catch (NumberFormatException e) {
      return DEFAULT_MAX_BYTES;
    }
  }
}
