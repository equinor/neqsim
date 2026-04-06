package neqsim.util.agentic;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for TaskResultValidator — validates results.json schema.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TaskResultValidatorTest {

  @Test
  @DisplayName("Valid results.json passes validation")
  void testValidResultsJson() {
    String json = "{\n"
        + "  \"key_results\": {\"outlet_temperature_C\": -18.5, \"pressure_drop_bar\": 3.2},\n"
        + "  \"validation\": {\"mass_balance_error_pct\": 0.01, \"acceptance_criteria_met\": true},\n"
        + "  \"approach\": \"Used SRK EOS with classic mixing rule\",\n"
        + "  \"conclusions\": \"The analysis shows good agreement\"\n" + "}";

    TaskResultValidator.ValidationReport report = TaskResultValidator.validate(json);
    assertTrue(report.isValid());
    assertEquals(0, report.getErrorCount());
    // Warnings for recommended keys that are missing
    assertTrue(report.getWarningCount() > 0);
  }

  @Test
  @DisplayName("Null input returns error")
  void testNullInput() {
    TaskResultValidator.ValidationReport report = TaskResultValidator.validate(null);
    assertFalse(report.isValid());
    assertEquals(1, report.getErrorCount());
    assertTrue(report.getErrors().get(0).message.contains("null or empty"));
  }

  @Test
  @DisplayName("Empty string returns error")
  void testEmptyInput() {
    TaskResultValidator.ValidationReport report = TaskResultValidator.validate("");
    assertFalse(report.isValid());
  }

  @Test
  @DisplayName("Invalid JSON returns error")
  void testInvalidJson() {
    TaskResultValidator.ValidationReport report = TaskResultValidator.validate("{broken json");
    assertFalse(report.isValid());
    assertTrue(report.getErrors().get(0).message.contains("Invalid JSON"));
  }

  @Test
  @DisplayName("Missing required keys are reported as errors")
  void testMissingRequiredKeys() {
    String json = "{\"key_results\": {\"a\": 1}}";

    TaskResultValidator.ValidationReport report = TaskResultValidator.validate(json);
    assertFalse(report.isValid());
    // Should have errors for missing: validation, approach, conclusions
    assertTrue(report.getErrorCount() >= 3);
  }

  @Test
  @DisplayName("Missing recommended keys are reported as warnings")
  void testMissingRecommendedKeys() {
    String json = "{\n" + "  \"key_results\": {\"temp\": 25.0},\n"
        + "  \"validation\": {\"acceptance_criteria_met\": true},\n"
        + "  \"approach\": \"SRK flash\",\n" + "  \"conclusions\": \"Done\"\n" + "}";

    TaskResultValidator.ValidationReport report = TaskResultValidator.validate(json);
    assertTrue(report.isValid());
    // Should have warnings for missing optional keys
    assertTrue(report.getWarningCount() > 0);
  }

  @Test
  @DisplayName("Uncertainty section with low n_simulations gets warning")
  void testLowSimulationCount() {
    String json = "{\n" + "  \"key_results\": {\"npv_million\": 100},\n"
        + "  \"validation\": {\"acceptance_criteria_met\": true},\n"
        + "  \"approach\": \"Monte Carlo\",\n" + "  \"conclusions\": \"Positive NPV\",\n"
        + "  \"uncertainty\": {\n" + "    \"method\": \"Monte Carlo\",\n"
        + "    \"n_simulations\": 50,\n" + "    \"p10\": 80,\n" + "    \"p50\": 100,\n"
        + "    \"p90\": 130\n" + "  }\n" + "}";

    TaskResultValidator.ValidationReport report = TaskResultValidator.validate(json);
    assertTrue(report.isValid());
    boolean foundLowSimWarning = false;
    for (TaskResultValidator.ValidationReport.Issue w : report.getWarnings()) {
      if (w.field.contains("n_simulations") && w.message.contains("low")) {
        foundLowSimWarning = true;
        break;
      }
    }
    assertTrue(foundLowSimWarning, "Should warn about low n_simulations");
  }

  @Test
  @DisplayName("Risk evaluation with missing fields gets warnings")
  void testRiskEvaluationValidation() {
    String json = "{\n" + "  \"key_results\": {\"a\": 1},\n"
        + "  \"validation\": {\"acceptance_criteria_met\": true},\n" + "  \"approach\": \"test\",\n"
        + "  \"conclusions\": \"test\",\n" + "  \"risk_evaluation\": {\n" + "    \"risks\": [\n"
        + "      {\"description\": \"Gas price drop\"}\n" + "    ]\n" + "  }\n" + "}";

    TaskResultValidator.ValidationReport report = TaskResultValidator.validate(json);
    assertTrue(report.isValid());
    // Should warn about missing id and risk_level in risk item, and missing overall_risk_level
    boolean foundRiskWarning = false;
    for (TaskResultValidator.ValidationReport.Issue w : report.getWarnings()) {
      if (w.field.contains("risk_evaluation")) {
        foundRiskWarning = true;
        break;
      }
    }
    assertTrue(foundRiskWarning, "Should warn about incomplete risk evaluation");
  }

  @Test
  @DisplayName("Figure discussion with missing fields gets warnings")
  void testFigureDiscussionValidation() {
    String json = "{\n" + "  \"key_results\": {\"a\": 1},\n"
        + "  \"validation\": {\"acceptance_criteria_met\": true},\n" + "  \"approach\": \"test\",\n"
        + "  \"conclusions\": \"test\",\n" + "  \"figure_discussion\": [\n"
        + "    {\"figure\": \"plot.png\"}\n" + "  ]\n" + "}";

    TaskResultValidator.ValidationReport report = TaskResultValidator.validate(json);
    assertTrue(report.isValid());
    boolean foundFigWarning = false;
    for (TaskResultValidator.ValidationReport.Issue w : report.getWarnings()) {
      if (w.field.contains("figure_discussion")) {
        foundFigWarning = true;
        break;
      }
    }
    assertTrue(foundFigWarning, "Should warn about missing figure discussion fields");
  }

  @Test
  @DisplayName("Report serializes to valid JSON")
  void testReportToJson() {
    String json = "{\"key_results\": {}}";
    TaskResultValidator.ValidationReport report = TaskResultValidator.validate(json);
    String reportJson = report.toJson();

    assertNotNull(reportJson);
    assertTrue(reportJson.contains("\"valid\""));
    assertTrue(reportJson.contains("\"errorCount\""));
    assertTrue(reportJson.contains("\"warningCount\""));
  }

  @Test
  @DisplayName("Complete valid results.json with all sections")
  void testCompleteValidJson() {
    String json = "{\n" + "  \"key_results\": {\"outlet_temperature_C\": -18.5},\n"
        + "  \"validation\": {\"mass_balance_error_pct\": 0.01, \"acceptance_criteria_met\": true},\n"
        + "  \"approach\": \"SRK EOS with classic mixing rule\",\n"
        + "  \"conclusions\": \"Good agreement with NIST data\",\n"
        + "  \"figure_captions\": {\"pressure_profile.png\": \"Pressure along pipeline\"},\n"
        + "  \"figure_discussion\": [\n"
        + "    {\"figure\": \"pressure_profile.png\", \"observation\": \"Pressure drops linearly\",\n"
        + "     \"mechanism\": \"Friction-dominated flow\", \"implication\": \"Pipeline is correctly sized\",\n"
        + "     \"recommendation\": \"Consider insulation for thermal losses\"}\n" + "  ],\n"
        + "  \"equations\": [{\"label\": \"Darcy\", \"latex\": \"\\\\Delta P = f L / D\"}],\n"
        + "  \"tables\": [{\"title\": \"Results\", \"headers\": [\"P\", \"T\"], \"rows\": [[100, 25]]}],\n"
        + "  \"references\": [{\"id\": \"Smith2019\", \"text\": \"Smith (2019)\"}],\n"
        + "  \"uncertainty\": {\n" + "    \"method\": \"Monte Carlo\",\n"
        + "    \"n_simulations\": 500,\n" + "    \"p10\": -5,\n" + "    \"p50\": 100,\n"
        + "    \"p90\": 250\n" + "  },\n" + "  \"risk_evaluation\": {\n" + "    \"risks\": [\n"
        + "      {\"id\": \"R1\", \"description\": \"Price drop\", \"risk_level\": \"High\",\n"
        + "       \"mitigation\": \"Hedging\"}\n" + "    ],\n"
        + "    \"overall_risk_level\": \"Medium\"\n" + "  }\n" + "}";

    TaskResultValidator.ValidationReport report = TaskResultValidator.validate(json);
    assertTrue(report.isValid());
    assertEquals(0, report.getErrorCount());
    // May still have warnings for deeply optional fields
  }

  @Test
  @DisplayName("Non-object JSON returns error")
  void testNonObjectJson() {
    TaskResultValidator.ValidationReport report = TaskResultValidator.validate("[1, 2, 3]");
    assertFalse(report.isValid());
    assertTrue(report.getErrors().get(0).message.contains("must be a JSON object"));
  }

  @Test
  @DisplayName("Empty approach and conclusions get warnings")
  void testEmptyStrings() {
    String json = "{\n" + "  \"key_results\": {\"a\": 1},\n"
        + "  \"validation\": {\"acceptance_criteria_met\": true},\n" + "  \"approach\": \"\",\n"
        + "  \"conclusions\": \"\"\n" + "}";

    TaskResultValidator.ValidationReport report = TaskResultValidator.validate(json);
    assertTrue(report.isValid());
    boolean foundApproachWarning = false;
    boolean foundConclusionWarning = false;
    for (TaskResultValidator.ValidationReport.Issue w : report.getWarnings()) {
      if (w.field.equals("approach")) {
        foundApproachWarning = true;
      }
      if (w.field.equals("conclusions")) {
        foundConclusionWarning = true;
      }
    }
    assertTrue(foundApproachWarning, "Should warn about empty approach");
    assertTrue(foundConclusionWarning, "Should warn about empty conclusions");
  }

  @Test
  @DisplayName("Valid standards_applied passes validation")
  void testValidStandardsApplied() {
    String json = "{\n" + "  \"key_results\": {\"a\": 1},\n"
        + "  \"validation\": {\"acceptance_criteria_met\": true},\n"
        + "  \"approach\": \"Used SRK EOS\",\n" + "  \"conclusions\": \"Good agreement\",\n"
        + "  \"standards_applied\": [\n" + "    {\n" + "      \"code\": \"API 520 Part I\",\n"
        + "      \"scope\": \"Relief valve sizing\",\n" + "      \"status\": \"PASS\"\n"
        + "    },\n" + "    {\n" + "      \"code\": \"NORSOK P-001\",\n"
        + "      \"scope\": \"Process design\",\n" + "      \"status\": \"INFO\"\n" + "    }\n"
        + "  ]\n" + "}";

    TaskResultValidator.ValidationReport report = TaskResultValidator.validate(json);
    assertTrue(report.isValid());
    // No warning about standards_applied since it is present with valid entries
    boolean hasStdWarning = false;
    for (TaskResultValidator.ValidationReport.Issue w : report.getWarnings()) {
      if (w.field.startsWith("standards_applied[")) {
        hasStdWarning = true;
      }
    }
    assertFalse(hasStdWarning, "Valid standards entries should produce no warnings");
  }

  @Test
  @DisplayName("Empty standards_applied array produces warning")
  void testEmptyStandardsApplied() {
    String json = "{\n" + "  \"key_results\": {\"a\": 1},\n"
        + "  \"validation\": {\"acceptance_criteria_met\": true},\n" + "  \"approach\": \"test\",\n"
        + "  \"conclusions\": \"test\",\n" + "  \"standards_applied\": []\n" + "}";

    TaskResultValidator.ValidationReport report = TaskResultValidator.validate(json);
    assertTrue(report.isValid());
    boolean foundEmpty = false;
    for (TaskResultValidator.ValidationReport.Issue w : report.getWarnings()) {
      if (w.field.equals("standards_applied") && w.message.contains("empty")) {
        foundEmpty = true;
      }
    }
    assertTrue(foundEmpty, "Should warn about empty standards_applied array");
  }

  @Test
  @DisplayName("Standards entry missing required fields produces warnings")
  void testStandardsMissingFields() {
    String json = "{\n" + "  \"key_results\": {\"a\": 1},\n"
        + "  \"validation\": {\"acceptance_criteria_met\": true},\n" + "  \"approach\": \"test\",\n"
        + "  \"conclusions\": \"test\",\n" + "  \"standards_applied\": [{\"code\": \"API 520\"}]\n"
        + "}";

    TaskResultValidator.ValidationReport report = TaskResultValidator.validate(json);
    assertTrue(report.isValid());
    boolean foundScopeWarn = false;
    boolean foundStatusWarn = false;
    for (TaskResultValidator.ValidationReport.Issue w : report.getWarnings()) {
      if (w.field.contains("scope")) {
        foundScopeWarn = true;
      }
      if (w.field.contains("status")) {
        foundStatusWarn = true;
      }
    }
    assertTrue(foundScopeWarn, "Should warn about missing scope");
    assertTrue(foundStatusWarn, "Should warn about missing status");
  }

  @Test
  @DisplayName("Invalid standards status produces warning")
  void testStandardsInvalidStatus() {
    String json = "{\n" + "  \"key_results\": {\"a\": 1},\n"
        + "  \"validation\": {\"acceptance_criteria_met\": true},\n" + "  \"approach\": \"test\",\n"
        + "  \"conclusions\": \"test\",\n"
        + "  \"standards_applied\": [{\"code\": \"ISO 13623\", \"scope\": \"Pipeline\", "
        + "\"status\": \"MAYBE\"}]\n" + "}";

    TaskResultValidator.ValidationReport report = TaskResultValidator.validate(json);
    assertTrue(report.isValid());
    boolean foundStatusWarn = false;
    for (TaskResultValidator.ValidationReport.Issue w : report.getWarnings()) {
      if (w.field.contains("status") && w.message.contains("MAYBE")) {
        foundStatusWarn = true;
      }
    }
    assertTrue(foundStatusWarn, "Should warn about unexpected status 'MAYBE'");
  }

  @Test
  @DisplayName("Non-array standards_applied produces error")
  void testStandardsNotArray() {
    String json = "{\n" + "  \"key_results\": {\"a\": 1},\n"
        + "  \"validation\": {\"acceptance_criteria_met\": true},\n" + "  \"approach\": \"test\",\n"
        + "  \"conclusions\": \"test\",\n" + "  \"standards_applied\": \"API 520\"\n" + "}";

    TaskResultValidator.ValidationReport report = TaskResultValidator.validate(json);
    assertFalse(report.isValid());
    boolean foundError = false;
    for (TaskResultValidator.ValidationReport.Issue e : report.getErrors()) {
      if (e.field.equals("standards_applied") && e.message.contains("JSON array")) {
        foundError = true;
      }
    }
    assertTrue(foundError, "Non-array standards_applied should produce an error");
  }
}
