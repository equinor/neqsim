package neqsim.process.processmodel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Validates JSON process definitions before building/running simulations. */
public final class ProcessJsonValidator {
  private ProcessJsonValidator() {}

  public static ValidationReport validate(String json) {
    ValidationReport report = new ValidationReport();
    if (json == null || json.trim().isEmpty()) {
      report.addError("JSON input is null or empty");
      return report;
    }

    JsonObject root;
    try {
      root = JsonParser.parseString(json).getAsJsonObject();
    } catch (Exception ex) {
      report.addError("JSON parse error: " + ex.getMessage());
      return report;
    }

    if (!root.has("process") || !root.get("process").isJsonArray()) {
      report.addError("Missing required 'process' array");
      return report;
    }

    JsonArray process = root.getAsJsonArray("process");
    Set<String> names = new HashSet<String>();
    Set<String> validRefs = new HashSet<String>();

    for (int i = 0; i < process.size(); i++) {
      JsonObject unit = process.get(i).getAsJsonObject();
      String type = getString(unit, "type");
      String name = getString(unit, "name");

      if (type == null || type.trim().isEmpty()) {
        report.addError("process[" + i + "] is missing required field 'type'");
      }
      if (name == null || name.trim().isEmpty()) {
        report.addError("process[" + i + "] is missing required field 'name'");
        name = "unnamed_" + i;
      }
      if (names.contains(name)) {
        report.addError("Duplicate unit name: '" + name + "'");
      }
      names.add(name);
      validRefs.add(name);
      validRefs.add(name + ".out");
      validRefs.add(name + ".outStream");
      validRefs.add(name + ".gasOut");
      validRefs.add(name + ".liquidOut");
      validRefs.add(name + ".gasOutStream");
      validRefs.add(name + ".liquidOutStream");
      validRefs.add(name + ".splitStream_0");
      validRefs.add(name + ".splitStream_1");
    }

    for (int i = 0; i < process.size(); i++) {
      JsonObject unit = process.get(i).getAsJsonObject();
      validateReferenceField(report, validRefs, unit, i, "inlet");
      if (unit.has("inlets") && unit.get("inlets").isJsonArray()) {
        JsonArray inlets = unit.getAsJsonArray("inlets");
        for (int j = 0; j < inlets.size(); j++) {
          String ref = inlets.get(j).getAsString();
          if (!validRefs.contains(ref)) {
            report.addWarning("process[" + i + "].inlets[" + j + "] references unknown stream '"
                + ref + "'");
          }
        }
      }
      if (unit.has("streams") && unit.get("streams").isJsonObject()) {
        JsonObject streams = unit.getAsJsonObject("streams");
        for (Map.Entry<String, JsonElement> e : streams.entrySet()) {
          if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isString()) {
            String ref = e.getValue().getAsString();
            if (isLikelyInputKey(e.getKey()) && !validRefs.contains(ref)) {
              report.addWarning("process[" + i + "].streams." + e.getKey()
                  + " references unknown stream '" + ref + "'");
            }
          }
        }
      }
    }
    return report;
  }

  private static void validateReferenceField(ValidationReport report, Set<String> validRefs,
      JsonObject unit, int idx, String key) {
    if (unit.has(key) && unit.get(key).isJsonPrimitive() && unit.get(key).getAsJsonPrimitive().isString()) {
      String ref = unit.get(key).getAsString();
      if (!validRefs.contains(ref)) {
        report.addWarning("process[" + idx + "]." + key + " references unknown stream '" + ref
            + "'");
      }
    }
  }

  private static boolean isLikelyInputKey(String key) {
    String k = key.toLowerCase();
    return k.contains("inlet") || "in".equals(k) || "feed".equals(k);
  }

  private static String getString(JsonObject obj, String key) {
    if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) {
      return null;
    }
    return obj.get(key).getAsString();
  }

  public static final class ValidationReport {
    private final List<String> errors = new ArrayList<String>();
    private final List<String> warnings = new ArrayList<String>();

    public boolean isValid() { return errors.isEmpty(); }
    public List<String> getErrors() { return errors; }
    public List<String> getWarnings() { return warnings; }
    public int getErrorCount() { return errors.size(); }
    public int getWarningCount() { return warnings.size(); }
    public void addError(String error) { errors.add(error); }
    public void addWarning(String warning) { warnings.add(warning); }
  }
}
