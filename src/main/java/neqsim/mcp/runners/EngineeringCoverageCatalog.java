package neqsim.mcp.runners;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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
  /** Raw bytes of the packaged inventory. */
  private static final byte[] RAW = readResource();

  /** Immutable-by-convention private catalog, never returned directly. */
  private static final JsonObject CATALOG = JsonParser.parseString(new String(RAW, StandardCharsets.UTF_8))
      .getAsJsonObject();

  /** SHA-256 of the packaged inventory bytes; derived so the committed file holds no global hash line. */
  private static final String DIGEST = sha256(RAW);

  /** Counts derived from the inventory rows for the same reason. */
  private static final JsonObject SUMMARY = summarize(CATALOG);

  /** Private utility constructor. */
  private EngineeringCoverageCatalog() {
  }

  /**
   * Read the packaged inventory, without filesystem or network access.
   *
   * @return resource bytes
   */
  private static byte[] readResource() {
    try (InputStream stream = EngineeringCoverageCatalog.class
        .getResourceAsStream("/neqsim/mcp/engineering-coverage.json")) {
      if (stream == null) {
        throw new IllegalStateException("Packaged engineering coverage inventory is missing");
      }
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] chunk = new byte[8192];
      int read;
      while ((read = stream.read(chunk)) != -1) {
        buffer.write(chunk, 0, read);
      }
      return buffer.toByteArray();
    } catch (java.io.IOException exception) {
      throw new IllegalStateException("Cannot read engineering coverage inventory", exception);
    }
  }

  /**
   * Hash bytes with SHA-256.
   *
   * @param data bytes to hash
   * @return lower-case hexadecimal digest
   */
  private static String sha256(byte[] data) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte value : hash) {
        hex.append(String.format("%02x", value));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  /**
   * Count inventory rows; mirrors the summary computed by devtools/build_engineering_coverage.py.
   *
   * @param catalog parsed inventory
   * @return summary counts including per-domain counts
   */
  private static JsonObject summarize(JsonObject catalog) {
    JsonArray apis = catalog.getAsJsonArray("apis");
    JsonArray capabilities = catalog.getAsJsonArray("capabilities");
    int anchors = 0;
    int withSkill = 0;
    int withAgent = 0;
    int review = 0;
    Map<String, int[]> domains = new LinkedHashMap<String, int[]>();
    for (JsonElement element : apis) {
      JsonObject row = element.getAsJsonObject();
      boolean anchored = row.getAsJsonArray("capabilities").size() > 0;
      boolean skilled = row.getAsJsonArray("skills").size() > 0;
      boolean reviewRequired = "review_required".equals(row.get("disposition").getAsString());
      String domain = row.get("domain").getAsString();
      int[] counts = domains.get(domain);
      if (counts == null) {
        counts = new int[4];
        domains.put(domain, counts);
      }
      counts[0]++;
      anchors += anchored ? 1 : 0;
      counts[1] += anchored ? 1 : 0;
      withSkill += skilled ? 1 : 0;
      counts[2] += skilled ? 1 : 0;
      withAgent += row.getAsJsonArray("agents").size() > 0 ? 1 : 0;
      review += reviewRequired ? 1 : 0;
      counts[3] += reviewRequired ? 1 : 0;
    }
    int classified = 0;
    Map<String, Integer> byClass = new LinkedHashMap<String, Integer>();
    for (String name : new String[] {"supported", "internal", "experimental", "deprecated"}) {
      byClass.put(name, 0);
    }
    for (JsonElement element : capabilities) {
      JsonObject row = element.getAsJsonObject();
      if (!row.has("operations")) {
        continue;
      }
      for (JsonElement operation : row.getAsJsonArray("operations")) {
        String classification = operation.getAsJsonObject().get("classification").getAsString();
        byClass.put(classification, byClass.get(classification) + 1);
        classified++;
      }
    }
    JsonObject domainCounts = new JsonObject();
    for (Map.Entry<String, int[]> entry : domains.entrySet()) {
      JsonObject counts = new JsonObject();
      counts.addProperty("publicTypes", entry.getValue()[0]);
      counts.addProperty("registeredAnchors", entry.getValue()[1]);
      counts.addProperty("withSkillMention", entry.getValue()[2]);
      counts.addProperty("reviewRequired", entry.getValue()[3]);
      domainCounts.add(entry.getKey(), counts);
    }
    JsonObject summary = new JsonObject();
    summary.addProperty("publicTypes", apis.size());
    summary.addProperty("registeredCapabilities", capabilities.size());
    summary.addProperty("classifiedOperations", classified);
    summary.addProperty("supportedOperations", byClass.get("supported"));
    summary.addProperty("internalOperations", byClass.get("internal"));
    summary.addProperty("experimentalOperations", byClass.get("experimental"));
    summary.addProperty("deprecatedOperations", byClass.get("deprecated"));
    summary.addProperty("registeredAnchors", anchors);
    summary.addProperty("withSkillMention", withSkill);
    summary.addProperty("withAgentMention", withAgent);
    summary.addProperty("reviewRequired", review);
    summary.add("domains", domainCounts);
    return summary;
  }

  /**
   * Return compact counts and an explicit retrieval route.
   *
   * @return independent summary object
   */
  public static JsonObject summary() {
    JsonObject result = SUMMARY.deepCopy();
    result.addProperty("catalogDigest", DIGEST);
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
    String digest = DIGEST;
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
