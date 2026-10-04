package neqsim.mcp.runners;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Read-only, paginated agent and MCP engineering coverage evidence from the packaged source inventory.
 *
 * <p>
 * Declared routes and source references are not execution receipts or engineering qualification.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public final class EngineeringCoverageCatalog {
  /** Immutable-by-convention private catalog, never returned directly. */
  private static final JsonObject CATALOG = load();

  /** Private utility constructor. */
  private EngineeringCoverageCatalog() {
  }

  /**
   * Load generated evidence from this artifact, without filesystem or network access.
   *
   * @return catalog object
   */
  private static JsonObject load() {
    try (InputStream stream = EngineeringCoverageCatalog.class
        .getResourceAsStream("/neqsim/mcp/engineering-coverage.json")) {
      if (stream == null) {
        throw new IllegalStateException("Packaged engineering coverage inventory is missing");
      }
      return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    } catch (java.io.IOException exception) {
      throw new IllegalStateException("Cannot read engineering coverage inventory", exception);
    }
  }

  /**
   * Return compact counts and an explicit retrieval route.
   *
   * @return independent summary object
   */
  public static JsonObject summary() {
    JsonObject result = CATALOG.getAsJsonObject("summary").deepCopy();
    result.add("catalogDigest", CATALOG.get("catalogDigest"));
    result.add("denominator", CATALOG.get("denominator"));
    result.add("interpretation", CATALOG.get("interpretation"));
    result.addProperty("complete", false);
    result.addProperty("retrieval", "runCapability with action=coverage, view=capabilities or apis, offset and limit");
    return result;
  }

  /**
   * Query source evidence with deterministic pagination; this does not invoke calculations.
   *
   * @param request view, query, domain, offset, limit, and optional catalogDigest
   * @return bounded page with total matches and continuation information
   * @throws IllegalArgumentException if a filter or page is invalid or the supplied digest is stale
   */
  public static JsonObject query(JsonObject request) {
    String view = string(request, "view", "capabilities");
    if (!"capabilities".equals(view) && !"apis".equals(view)) {
      throw new IllegalArgumentException("view must be capabilities or apis");
    }
    String query = string(request, "query", "").toLowerCase(Locale.ROOT);
    String domain = string(request, "domain", "");
    if (query.length() > 200 || domain.length() > 100) {
      throw new IllegalArgumentException("Coverage query or domain is too long");
    }
    String digest = CATALOG.get("catalogDigest").getAsString();
    if (!string(request, "catalogDigest", digest).equals(digest)) {
      throw new IllegalArgumentException("Coverage catalog changed; restart pagination with the current digest");
    }
    int offset = integer(request, "offset", 0);
    int limit = integer(request, "limit", 20);
    if (offset < 0 || limit < 1 || limit > 50) {
      throw new IllegalArgumentException("offset must be non-negative and limit must be between 1 and 50");
    }
    JsonArray page = new JsonArray();
    int total = 0;
    for (JsonElement element : CATALOG.getAsJsonArray(view)) {
      JsonObject row = element.getAsJsonObject();
      if (!domain.isEmpty() && !domain.equals(row.get("domain").getAsString())) {
        continue;
      }
      String searchable = row.get("id").getAsString();
      if (row.has("title")) {
        searchable += " " + row.get("title").getAsString();
      }
      if (!searchable.toLowerCase(Locale.ROOT).contains(query)) {
        continue;
      }
      if (total >= offset && page.size() < limit) {
        page.add(row.deepCopy());
      }
      total++;
    }
    JsonObject result = new JsonObject();
    result.addProperty("status", "success");
    result.addProperty("action", "coverage");
    result.addProperty("schemaVersion", "1.0");
    result.addProperty("view", view);
    result.addProperty("catalogDigest", digest);
    result.addProperty("total", total);
    result.addProperty("offset", offset);
    result.addProperty("limit", limit);
    result.addProperty("hasMore", (long) offset + page.size() < total);
    if ((long) offset + page.size() < total) {
      result.addProperty("nextOffset", offset + page.size());
    }
    result.add("entries", page);
    result.add("interpretation", CATALOG.get("interpretation"));
    result.addProperty("complete", false);
    return result;
  }

  /**
   * Read a strictly typed optional string.
   *
   * @param request request object
   * @param key field name
   * @param fallback default value
   * @return requested or default value
   */
  private static String string(JsonObject request, String key, String fallback) {
    if (!request.has(key)) {
      return fallback;
    }
    JsonElement value = request.get(key);
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw new IllegalArgumentException(key + " must be a string");
    }
    return value.getAsString();
  }

  /**
   * Read an integer without silent truncation or overflow.
   *
   * @param request request object
   * @param key field name
   * @param fallback default value
   * @return requested or default value
   */
  private static int integer(JsonObject request, String key, int fallback) {
    if (!request.has(key)) {
      return fallback;
    }
    JsonElement value = request.get(key);
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
      throw new IllegalArgumentException(key + " must be an integer");
    }
    try {
      return value.getAsBigDecimal().intValueExact();
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException(key + " must be an integer in range", exception);
    }
  }
}
