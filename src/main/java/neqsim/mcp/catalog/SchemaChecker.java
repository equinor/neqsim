package neqsim.mcp.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Minimal JSON Schema (draft 2020-12 subset) checker for the input contracts in {@link SchemaCatalog}.
 *
 * <p>
 * Supports the keywords the catalog actually uses: {@code type}, {@code required}, {@code properties}, {@code items},
 * {@code enum}, {@code const}, {@code minItems}, {@code allOf}, {@code anyOf}, {@code oneOf} and {@code if}/
 * {@code then}. Unknown keywords are ignored, so a schema that uses richer features still yields a best-effort check
 * rather than an exception. The purpose is pre-flight feedback to an agent: every violation is returned as a
 * human-readable message with a JSON-pointer-like path.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class SchemaChecker {

  private SchemaChecker() {
  }

  /**
   * Checks an instance against a tool input schema from {@link SchemaCatalog}.
   *
   * @param toolName snake_case tool name, e.g. {@code run_relief}
   * @param instance JSON instance to check
   * @return violation messages; empty when the instance satisfies the schema subset, or when no schema exists
   */
  public static List<String> checkToolInput(String toolName, JsonElement instance) {
    String schemaJson = SchemaCatalog.getSchema(toolName, "input");
    List<String> violations = new ArrayList<String>();
    if (schemaJson == null) {
      return violations;
    }
    check(JsonParser.parseString(schemaJson), instance, "$", violations);
    return violations;
  }

  /**
   * Recursively checks an instance against a schema node.
   *
   * @param schema schema node
   * @param instance instance node
   * @param path JSON-pointer-like path of the instance node, used in messages
   * @param violations mutable list receiving violation messages
   */
  public static void check(JsonElement schema, JsonElement instance, String path, List<String> violations) {
    if (schema == null || !schema.isJsonObject()) {
      return;
    }
    JsonObject s = schema.getAsJsonObject();

    if (s.has("type") && !typeMatches(s.get("type"), instance)) {
      violations.add(path + " must be of type " + s.get("type") + " but was " + describe(instance));
      return;
    }
    if (s.has("const") && !s.get("const").equals(instance)) {
      violations.add(path + " must equal " + s.get("const"));
    }
    if (s.has("enum") && s.get("enum").isJsonArray() && !s.getAsJsonArray("enum").contains(instance)) {
      violations.add(path + " must be one of " + s.get("enum") + " but was " + instance);
    }

    if (instance != null && instance.isJsonObject()) {
      JsonObject obj = instance.getAsJsonObject();
      if (s.has("required")) {
        for (JsonElement req : s.getAsJsonArray("required")) {
          if (!obj.has(req.getAsString())) {
            violations.add(path + " is missing required field '" + req.getAsString() + "'");
          }
        }
      }
      if (s.has("properties")) {
        for (Map.Entry<String, JsonElement> entry : s.getAsJsonObject("properties").entrySet()) {
          if (obj.has(entry.getKey())) {
            check(entry.getValue(), obj.get(entry.getKey()), path + "." + entry.getKey(), violations);
          }
        }
      }
    }

    if (instance != null && instance.isJsonArray()) {
      JsonArray arr = instance.getAsJsonArray();
      if (s.has("minItems") && arr.size() < s.get("minItems").getAsInt()) {
        violations.add(path + " must have at least " + s.get("minItems").getAsInt() + " item(s)");
      }
      if (s.has("items")) {
        for (int i = 0; i < arr.size(); i++) {
          check(s.get("items"), arr.get(i), path + "[" + i + "]", violations);
        }
      }
    }

    if (s.has("allOf")) {
      for (JsonElement sub : s.getAsJsonArray("allOf")) {
        check(sub, instance, path, violations);
      }
    }
    if (s.has("if") && s.has("then") && satisfies(s.get("if"), instance)) {
      check(s.get("then"), instance, path, violations);
    }
    if (s.has("anyOf")) {
      int matches = countMatches(s.getAsJsonArray("anyOf"), instance);
      if (matches == 0) {
        violations.add(path + " must satisfy at least one of: " + summarize(s.getAsJsonArray("anyOf")));
      }
    }
    if (s.has("oneOf")) {
      int matches = countMatches(s.getAsJsonArray("oneOf"), instance);
      if (matches != 1) {
        violations.add(path + " must satisfy exactly one of: " + summarize(s.getAsJsonArray("oneOf")) + " (matched "
            + matches + ")");
      }
    }
  }

  /**
   * Tests whether an instance satisfies a schema node without recording violations.
   *
   * @param schema schema node
   * @param instance instance node
   * @return true when no violation is produced
   */
  private static boolean satisfies(JsonElement schema, JsonElement instance) {
    List<String> scratch = new ArrayList<String>();
    check(schema, instance, "$", scratch);
    return scratch.isEmpty();
  }

  /**
   * Counts how many alternatives an instance satisfies.
   *
   * @param alternatives schema alternatives
   * @param instance instance node
   * @return number of satisfied alternatives
   */
  private static int countMatches(JsonArray alternatives, JsonElement instance) {
    int matches = 0;
    for (JsonElement alt : alternatives) {
      if (satisfies(alt, instance)) {
        matches++;
      }
    }
    return matches;
  }

  /**
   * Summarizes alternatives as their required-field lists for a compact message.
   *
   * @param alternatives schema alternatives
   * @return human-readable summary
   */
  private static String summarize(JsonArray alternatives) {
    List<String> parts = new ArrayList<String>();
    for (JsonElement alt : alternatives) {
      if (alt.isJsonObject() && alt.getAsJsonObject().has("required")) {
        parts.add(alt.getAsJsonObject().get("required").toString());
      } else {
        parts.add(alt.toString());
      }
    }
    return String.join(" | ", parts);
  }

  /**
   * Checks the JSON Schema {@code type} keyword.
   *
   * @param type schema type (string or array of strings)
   * @param instance instance node
   * @return true when the instance has one of the listed types
   */
  private static boolean typeMatches(JsonElement type, JsonElement instance) {
    if (type.isJsonArray()) {
      for (JsonElement t : type.getAsJsonArray()) {
        if (typeMatches(t, instance)) {
          return true;
        }
      }
      return false;
    }
    String t = type.getAsString();
    if (instance == null || instance.isJsonNull()) {
      return "null".equals(t);
    }
    switch (t) {
    case "object":
      return instance.isJsonObject();
    case "array":
      return instance.isJsonArray();
    case "string":
      return instance.isJsonPrimitive() && instance.getAsJsonPrimitive().isString();
    case "boolean":
      return instance.isJsonPrimitive() && instance.getAsJsonPrimitive().isBoolean();
    case "number":
      return instance.isJsonPrimitive() && instance.getAsJsonPrimitive().isNumber();
    case "integer":
      return instance.isJsonPrimitive() && instance.getAsJsonPrimitive().isNumber()
          && instance.getAsDouble() == Math.rint(instance.getAsDouble());
    default:
      return true;
    }
  }

  /**
   * Describes the JSON type of an instance for messages.
   *
   * @param instance instance node
   * @return type name
   */
  private static String describe(JsonElement instance) {
    if (instance == null || instance.isJsonNull()) {
      return "null";
    }
    if (instance.isJsonObject()) {
      return "object";
    }
    if (instance.isJsonArray()) {
      return "array";
    }
    if (instance.getAsJsonPrimitive().isString()) {
      return "string";
    }
    if (instance.getAsJsonPrimitive().isBoolean()) {
      return "boolean";
    }
    return "number";
  }
}
