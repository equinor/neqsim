package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Software-contract tests for the paired reporting and task-workflow handoff surfaces.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ReportRunnerTest {

  @Test
  void structuredReportPreservesMetadataTablesChartsAndSummary() {
    String request = "{\"reportType\":\"custom\",\"title\":\"Compression review\","
        + "\"author\":\"Contract test\",\"includeValidation\":false,\"data\":{"
        + "\"temperature_C\":25.0,\"pressure_bar\":50.0,"
        + "\"curve\":[1.0,2.0,3.0],\"conclusions\":\"Synthetic evidence only.\"}}";

    JsonObject report = JsonParser.parseString(ReportRunner.run(request)).getAsJsonObject();

    assertFalse(report.has("status"));
    assertEquals("Compression review", report.get("title").getAsString());
    assertEquals("Contract test", report.get("author").getAsString());
    assertEquals("custom", report.get("reportType").getAsString());
    assertFalse(report.get("generatedAt").getAsString().isEmpty());
    assertTrue(report.get("markdown").getAsString().contains("# Compression review"));
    assertTrue(report.get("markdown").getAsString().contains("Synthetic evidence only."));
    assertEquals(1, report.getAsJsonArray("tables").size());
    assertEquals(1, report.getAsJsonArray("chartData").size());
    assertEquals("curve", report.getAsJsonArray("chartData").get(0).getAsJsonObject().get("name").getAsString());
    assertFalse(report.has("validation"));

    JsonObject summary = report.getAsJsonObject("summary");
    assertEquals(2, summary.get("numericFields").getAsInt());
    assertEquals(0, summary.get("objectFields").getAsInt());
    assertEquals(1, summary.get("arrayFields").getAsInt());
    assertEquals(4, summary.get("totalFields").getAsInt());

    JsonArray rows = report.getAsJsonArray("tables").get(0).getAsJsonObject().getAsJsonArray("rows");
    assertEquals("temperature_C", rows.get(0).getAsJsonArray().get(0).getAsString());
    assertEquals("C", rows.get(0).getAsJsonArray().get(2).getAsString());
    assertEquals("bara", rows.get(1).getAsJsonArray().get(2).getAsString());
  }

  @Test
  void reportOptionalSectionsFollowRequestFlags() {
    JsonObject report = JsonParser
        .parseString(ReportRunner.run("{\"includeChartData\":false,\"includeValidation\":false,\"data\":{}}"))
        .getAsJsonObject();

    assertFalse(report.has("chartData"));
    assertFalse(report.has("validation"));
    assertTrue(report.has("tables"));
    assertTrue(report.has("summary"));
  }

  @Test
  void reportInputFailuresAreStructuredAndFailClosed() {
    assertReportError(ReportRunner.run(null));
    assertReportError(ReportRunner.run("{bad json}"));
    assertReportError(ReportRunner.run("[]"));
  }

  @Test
  void taskWorkflowBridgeCreatesResultsJsonWithProvenance() {
    String request = "{\"action\":\"toResultsJson\",\"sourceRunner\":\"runFlash\","
        + "\"approach\":\"Synthetic SRK handoff\",\"conclusions\":\"Contract shape only\","
        + "\"toolOutput\":{\"status\":\"success\",\"fluid\":{"
        + "\"conditions\":{\"temperature_K\":300.0,\"pressure_bara\":42.0},"
        + "\"properties\":{\"density_kgm3\":10.5,\"molarMass_kgmol\":0.020}},"
        + "\"flash\":{\"numberOfPhases\":2}}}";

    JsonObject response = JsonParser.parseString(TaskWorkflowBridge.run(request)).getAsJsonObject();

    assertEquals("success", response.get("status").getAsString());
    JsonObject results = response.getAsJsonObject("resultsJson");
    JsonObject keyResults = results.getAsJsonObject("key_results");
    assertEquals(26.85, keyResults.get("temperature_C").getAsDouble(), 1.0e-10);
    assertEquals(42.0, keyResults.get("pressure_bar").getAsDouble(), 0.0);
    assertEquals(10.5, keyResults.get("density_kgm3").getAsDouble(), 0.0);
    assertEquals(2, keyResults.get("number_of_phases").getAsInt());
    assertEquals("success", results.getAsJsonObject("validation").get("status").getAsString());
    assertTrue(results.getAsJsonObject("validation").get("acceptance_criteria_met").getAsBoolean());
    assertEquals("Synthetic SRK handoff", results.get("approach").getAsString());
    assertEquals("Contract shape only", results.get("conclusions").getAsString());
    assertTrue(results.get("figure_captions").isJsonObject());
    assertTrue(results.get("figure_discussion").isJsonArray());
    assertTrue(results.get("equations").isJsonArray());
    assertTrue(results.get("tables").isJsonArray());
    assertTrue(results.get("references").isJsonArray());
    assertEquals("neqsim-mcp-server", results.getAsJsonObject("_meta").get("source").getAsString());
    assertEquals("runFlash", results.getAsJsonObject("_meta").get("tool").getAsString());
    assertEquals("TaskWorkflowBridge", results.getAsJsonObject("_meta").get("generated_by").getAsString());
  }

  @Test
  void taskWorkflowBridgePublishesSchemaAndRejectsInvalidRequests() {
    JsonObject schema = JsonParser.parseString(TaskWorkflowBridge.run("{\"action\":\"getSchema\"}"))
        .getAsJsonObject();
    assertEquals("success", schema.get("status").getAsString());
    for (String field : new String[] { "key_results", "validation", "approach", "conclusions", "uncertainty",
        "risk_evaluation", "benchmark_validation" }) {
      assertTrue(schema.getAsJsonObject("fields").has(field), "Missing schema field " + field);
    }

    assertBridgeError(TaskWorkflowBridge.run(null));
    assertBridgeError(TaskWorkflowBridge.run("{bad json}"));
    assertBridgeError(TaskWorkflowBridge.run("{\"action\":\"unknown\"}"));
    assertBridgeError(TaskWorkflowBridge.run("{\"action\":\"toResultsJson\"}"));
  }

  private static void assertReportError(String result) {
    assertNotNull(result);
    JsonObject error = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", error.get("status").getAsString());
    assertEquals("REPORT_ERROR",
        error.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }

  private static void assertBridgeError(String result) {
    assertNotNull(result);
    JsonObject error = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", error.get("status").getAsString());
    assertFalse(error.get("message").getAsString().isEmpty());
  }
}
