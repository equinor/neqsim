package neqsim.util.agentic;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Validates task results.json files against the expected schema for NeqSim task-solving workflows.
 *
 * <p>
 * The task-solving workflow (see docs/development/TASK_SOLVING_GUIDE.md) produces a results.json
 * file at the end of each analysis notebook. This validator checks that the JSON conforms to the
 * expected schema, ensuring downstream report generators and cross-session learning systems can
 * reliably consume the output.
 * </p>
 *
 * <h2>Required Keys:</h2>
 * <ul>
 * <li><b>key_results</b> — Main numerical outputs with unit suffixes</li>
 * <li><b>validation</b> — Acceptance criteria check outcomes</li>
 * <li><b>approach</b> — Brief description of method used</li>
 * <li><b>conclusions</b> — Summary of findings</li>
 * </ul>
 *
 * <h2>Optional Keys (recommended for Standard/Comprehensive tasks):</h2>
 * <ul>
 * <li><b>figure_captions</b> — Map of filename to caption string</li>
 * <li><b>figure_discussion</b> — Array of discussion objects per figure</li>
 * <li><b>equations</b> — LaTeX equations for the report</li>
 * <li><b>tables</b> — Tabular data for the report</li>
 * <li><b>references</b> — Cited literature</li>
 * <li><b>uncertainty</b> — Monte Carlo P10/P50/P90 results</li>
 * <li><b>risk_evaluation</b> — Risk register with mitigation</li>
 * <li><b>benchmark_validation</b> — External data comparison</li>
 * <li><b>standards_applied</b> — Industry standards used with compliance status</li>
 * </ul>
 *
 * <h2>Usage:</h2>
 *
 * <pre>
 * {@code
 * String json = new String(Files.readAllBytes(Paths.get("results.json")));
 * TaskResultValidator.ValidationReport report = TaskResultValidator.validate(json);
 * if (!report.isValid()) {
 *   System.out.println(report.toJson());
 * }
 * }
 * </pre>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class TaskResultValidator implements Serializable {
  private static final long serialVersionUID = 1000L;

  private static final List<String> REQUIRED_KEYS =
      Arrays.asList("key_results", "validation", "approach", "conclusions");

  private static final List<String> RECOMMENDED_KEYS =
      Arrays.asList("figure_captions", "figure_discussion", "equations", "tables", "references",
          "uncertainty", "risk_evaluation", "standards_applied");

  private TaskResultValidator() {
    // Utility class
  }

  /**
   * Validate a results.json string against the expected schema.
   *
   * @param jsonString the JSON string to validate
   * @return validation report with errors and warnings
   */
  public static ValidationReport validate(String jsonString) {
    ValidationReport report = new ValidationReport();

    if (jsonString == null || jsonString.trim().isEmpty()) {
      report.addError("root", "results.json is null or empty");
      return report;
    }

    JsonObject root;
    try {
      JsonElement element = JsonParser.parseString(jsonString);
      if (!element.isJsonObject()) {
        report.addError("root",
            "results.json must be a JSON object, got: " + element.getClass().getSimpleName());
        return report;
      }
      root = element.getAsJsonObject();
    } catch (Exception e) {
      report.addError("root", "Invalid JSON: " + e.getMessage());
      return report;
    }

    // Check required keys
    for (String key : REQUIRED_KEYS) {
      if (!root.has(key)) {
        report.addError(key, "Required key '" + key + "' is missing");
      }
    }

    // Check recommended keys
    for (String key : RECOMMENDED_KEYS) {
      if (!root.has(key)) {
        report.addWarning(key, "Recommended key '" + key + "' is missing");
      }
    }

    // Validate key_results
    if (root.has("key_results")) {
      JsonElement krElem = root.get("key_results");
      if (krElem.isJsonObject()) {
        validateKeyResults(krElem.getAsJsonObject(), report);
      } else {
        report.addError("key_results", "'key_results' must be a JSON object");
      }
    }

    // Validate validation section
    if (root.has("validation")) {
      JsonElement valElem = root.get("validation");
      if (valElem.isJsonObject()) {
        validateValidationSection(valElem.getAsJsonObject(), report);
      } else {
        report.addError("validation", "'validation' must be a JSON object");
      }
    }

    // Validate approach
    if (root.has("approach")) {
      JsonElement approach = root.get("approach");
      if (!approach.isJsonPrimitive() || !approach.getAsJsonPrimitive().isString()) {
        report.addError("approach", "'approach' must be a string");
      } else if (approach.getAsString().trim().isEmpty()) {
        report.addWarning("approach", "'approach' is empty");
      }
    }

    // Validate conclusions
    if (root.has("conclusions")) {
      JsonElement conclusions = root.get("conclusions");
      if (!conclusions.isJsonPrimitive() || !conclusions.getAsJsonPrimitive().isString()) {
        report.addError("conclusions", "'conclusions' must be a string");
      } else if (conclusions.getAsString().trim().isEmpty()) {
        report.addWarning("conclusions", "'conclusions' is empty");
      }
    }

    // Validate uncertainty section if present
    if (root.has("uncertainty")) {
      validateUncertaintySection(root.get("uncertainty"), report);
    }

    // Validate risk_evaluation if present
    if (root.has("risk_evaluation")) {
      validateRiskEvaluation(root.get("risk_evaluation"), report);
    }

    // Validate figure_discussion if present
    if (root.has("figure_discussion")) {
      validateFigureDiscussion(root.get("figure_discussion"), report);
    }

    // Validate standards_applied if present
    if (root.has("standards_applied")) {
      validateStandardsApplied(root.get("standards_applied"), report);
    }

    return report;
  }

  /**
   * Validate the key_results section.
   *
   * @param keyResults the key_results object
   * @param report validation report to append to
   */
  private static void validateKeyResults(JsonObject keyResults, ValidationReport report) {
    if (keyResults == null || keyResults.size() == 0) {
      report.addWarning("key_results", "key_results is empty — add numerical outputs");
      return;
    }
    for (Map.Entry<String, JsonElement> entry : keyResults.entrySet()) {
      if (!entry.getValue().isJsonPrimitive()) {
        report.addWarning("key_results." + entry.getKey(),
            "Value should be a primitive (number or string), got: "
                + entry.getValue().getClass().getSimpleName());
      }
    }
  }

  /**
   * Validate the validation section.
   *
   * @param validation the validation object
   * @param report validation report to append to
   */
  private static void validateValidationSection(JsonObject validation, ValidationReport report) {
    if (validation == null || validation.size() == 0) {
      report.addWarning("validation", "validation section is empty");
      return;
    }
    if (!validation.has("acceptance_criteria_met")) {
      report.addWarning("validation",
          "Missing 'acceptance_criteria_met' boolean in validation section");
    }
  }

  /**
   * Validate the uncertainty section.
   *
   * @param uncertainty the uncertainty element
   * @param report validation report to append to
   */
  private static void validateUncertaintySection(JsonElement uncertainty, ValidationReport report) {
    if (!uncertainty.isJsonObject()) {
      report.addError("uncertainty", "uncertainty must be a JSON object");
      return;
    }
    JsonObject unc = uncertainty.getAsJsonObject();
    List<String> requiredUncKeys = Arrays.asList("method", "n_simulations", "p10", "p50", "p90");
    for (String key : requiredUncKeys) {
      if (!unc.has(key)) {
        report.addWarning("uncertainty." + key,
            "Missing recommended key '" + key + "' in uncertainty section");
      }
    }
    if (unc.has("n_simulations") && unc.get("n_simulations").isJsonPrimitive()) {
      int nSim = unc.get("n_simulations").getAsInt();
      if (nSim < 100) {
        report.addWarning("uncertainty.n_simulations",
            "n_simulations=" + nSim + " is low — recommend at least 200 for NeqSim simulations");
      }
    }
  }

  /**
   * Validate the risk_evaluation section.
   *
   * @param riskEval the risk_evaluation element
   * @param report validation report to append to
   */
  private static void validateRiskEvaluation(JsonElement riskEval, ValidationReport report) {
    if (!riskEval.isJsonObject()) {
      report.addError("risk_evaluation", "risk_evaluation must be a JSON object");
      return;
    }
    JsonObject risk = riskEval.getAsJsonObject();
    if (!risk.has("risks")) {
      report.addWarning("risk_evaluation.risks", "Missing 'risks' array in risk_evaluation");
    } else {
      JsonElement risksElem = risk.get("risks");
      if (!risksElem.isJsonArray()) {
        report.addError("risk_evaluation.risks", "'risks' must be a JSON array");
      } else {
        JsonArray risksArr = risksElem.getAsJsonArray();
        if (risksArr.size() == 0) {
          report.addWarning("risk_evaluation.risks", "risks array is empty");
        }
        for (int i = 0; i < risksArr.size(); i++) {
          if (risksArr.get(i).isJsonObject()) {
            JsonObject riskItem = risksArr.get(i).getAsJsonObject();
            if (!riskItem.has("id") || !riskItem.has("description")
                || !riskItem.has("risk_level")) {
              report.addWarning("risk_evaluation.risks[" + i + "]",
                  "Risk item missing required fields (id, description, risk_level)");
            }
          }
        }
      }
    }
    if (!risk.has("overall_risk_level")) {
      report.addWarning("risk_evaluation.overall_risk_level",
          "Missing 'overall_risk_level' in risk_evaluation");
    }
  }

  /**
   * Validate the figure_discussion section.
   *
   * @param figDisc the figure_discussion element
   * @param report validation report to append to
   */
  private static void validateFigureDiscussion(JsonElement figDisc, ValidationReport report) {
    if (!figDisc.isJsonArray()) {
      report.addError("figure_discussion", "figure_discussion must be a JSON array");
      return;
    }
    JsonArray arr = figDisc.getAsJsonArray();
    for (int i = 0; i < arr.size(); i++) {
      if (arr.get(i).isJsonObject()) {
        JsonObject disc = arr.get(i).getAsJsonObject();
        List<String> requiredFields =
            Arrays.asList("figure", "observation", "mechanism", "implication");
        for (String field : requiredFields) {
          if (!disc.has(field)) {
            report.addWarning("figure_discussion[" + i + "]." + field,
                "Missing '" + field + "' in figure discussion entry");
          }
        }
      }
    }
  }

  /**
   * Validate the standards_applied section.
   *
   * <p>
   * Each entry should have: code, version, scope, and status (PASS/FAIL/INFO). Optional fields:
   * design_value, limit, unit.
   * </p>
   *
   * @param standardsElem the standards_applied element
   * @param report validation report to append to
   */
  private static void validateStandardsApplied(JsonElement standardsElem, ValidationReport report) {
    if (!standardsElem.isJsonArray()) {
      report.addError("standards_applied", "standards_applied must be a JSON array");
      return;
    }
    JsonArray arr = standardsElem.getAsJsonArray();
    if (arr.size() == 0) {
      report.addWarning("standards_applied", "standards_applied array is empty");
    }
    for (int i = 0; i < arr.size(); i++) {
      if (arr.get(i).isJsonObject()) {
        JsonObject std = arr.get(i).getAsJsonObject();
        List<String> requiredFields = Arrays.asList("code", "scope", "status");
        for (String field : requiredFields) {
          if (!std.has(field)) {
            report.addWarning("standards_applied[" + i + "]." + field,
                "Missing '" + field + "' in standards entry");
          }
        }
        if (std.has("status") && std.get("status").isJsonPrimitive()) {
          String status = std.get("status").getAsString();
          if (!"PASS".equals(status) && !"FAIL".equals(status) && !"INFO".equals(status)
              && !"N/A".equals(status)) {
            report.addWarning("standards_applied[" + i + "].status",
                "Unexpected status '" + status + "' — expected PASS, FAIL, INFO, or N/A");
          }
        }
      }
    }
  }

  /**
   * Report of validation results for a results.json file.
   */
  public static class ValidationReport implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final List<Issue> errors;
    private final List<Issue> warnings;

    /**
     * Constructor.
     */
    public ValidationReport() {
      this.errors = new ArrayList<Issue>();
      this.warnings = new ArrayList<Issue>();
    }

    /**
     * Add an error.
     *
     * @param field the JSON field with the issue
     * @param message error description
     */
    public void addError(String field, String message) {
      errors.add(new Issue(field, message));
    }

    /**
     * Add a warning.
     *
     * @param field the JSON field with the issue
     * @param message warning description
     */
    public void addWarning(String field, String message) {
      warnings.add(new Issue(field, message));
    }

    /**
     * Check if the results.json is valid (no errors, warnings are OK).
     *
     * @return true if no errors found
     */
    public boolean isValid() {
      return errors.isEmpty();
    }

    /**
     * Get the error count.
     *
     * @return number of errors
     */
    public int getErrorCount() {
      return errors.size();
    }

    /**
     * Get the warning count.
     *
     * @return number of warnings
     */
    public int getWarningCount() {
      return warnings.size();
    }

    /**
     * Get all errors.
     *
     * @return unmodifiable list of errors
     */
    public List<Issue> getErrors() {
      return java.util.Collections.unmodifiableList(errors);
    }

    /**
     * Get all warnings.
     *
     * @return unmodifiable list of warnings
     */
    public List<Issue> getWarnings() {
      return java.util.Collections.unmodifiableList(warnings);
    }

    /**
     * Serialize the report to JSON.
     *
     * @return JSON report string
     */
    public String toJson() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("valid", isValid());
      map.put("errorCount", errors.size());
      map.put("warningCount", warnings.size());

      List<Map<String, String>> errorList = new ArrayList<Map<String, String>>();
      for (Issue e : errors) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("field", e.field);
        m.put("message", e.message);
        errorList.add(m);
      }
      map.put("errors", errorList);

      List<Map<String, String>> warningList = new ArrayList<Map<String, String>>();
      for (Issue w : warnings) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("field", w.field);
        m.put("message", w.message);
        warningList.add(m);
      }
      map.put("warnings", warningList);

      Gson gson =
          new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
      return gson.toJson(map);
    }

    /**
     * A single validation issue.
     */
    public static class Issue implements Serializable {
      private static final long serialVersionUID = 1000L;

      /** The JSON field path with the issue. */
      public final String field;
      /** Description of the issue. */
      public final String message;

      /**
       * Constructor.
       *
       * @param field field path
       * @param message issue message
       */
      Issue(String field, String message) {
        this.field = field;
        this.message = message;
      }
    }
  }
}
