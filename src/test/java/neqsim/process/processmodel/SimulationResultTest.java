package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for SimulationResult — structured error/success responses.
 *
 * @author Even Solbraa
 */
class SimulationResultTest {

  @Test
  void testSuccessResult() {
    ProcessSystem process = new ProcessSystem("test");
    SimulationResult result = SimulationResult.success(process, "{}", null);
    assertTrue(result.isSuccess());
    assertFalse(result.isError());
    assertEquals(SimulationResult.Status.SUCCESS, result.getStatus());
    assertNotNull(result.getProcessSystem());
    assertNotNull(result.getReportJson());
    assertTrue(result.getErrors().isEmpty());
  }

  @Test
  void testErrorResult() {
    SimulationResult result =
        SimulationResult.error("TEST_ERROR", "Something went wrong", "Fix it by doing X");
    assertTrue(result.isError());
    assertFalse(result.isSuccess());
    assertEquals(1, result.getErrors().size());
    assertEquals("TEST_ERROR", result.getErrors().get(0).getCode());
    assertEquals("Something went wrong", result.getErrors().get(0).getMessage());
    assertEquals("Fix it by doing X", result.getErrors().get(0).getRemediation());
  }

  @Test
  void testToJsonSuccess() {
    ProcessSystem process = new ProcessSystem("test-process");
    SimulationResult result = SimulationResult.success(process, null, null);
    String json = result.toJson();
    assertNotNull(json);
    assertTrue(json.contains("\"status\": \"success\""));
    assertTrue(json.contains("test-process"));
  }

  @Test
  void testToJsonError() {
    SimulationResult result = SimulationResult.error("MISSING_INLET", "Inlet reference not found",
        "Define the unit before referencing it");
    String json = result.toJson();
    assertNotNull(json);
    assertTrue(json.contains("\"status\": \"error\""));
    assertTrue(json.contains("MISSING_INLET"));
    assertTrue(json.contains("Inlet reference not found"));
    assertTrue(json.contains("Define the unit before referencing it"));
  }

  @Test
  void testToJsonWithWarnings() {
    java.util.List<String> warnings = new java.util.ArrayList<>();
    warnings.add("Property power not found on Cooler");
    warnings.add("Using default mixing rule");
    SimulationResult result = SimulationResult.success(new ProcessSystem("test"), null, warnings);
    assertTrue(result.hasWarnings(), "hasWarnings() should be true");
    assertEquals(2, result.getWarnings().size());
    String json = result.toJson();
    assertTrue(json.contains("warnings"), "JSON should contain warnings key");
    assertTrue(json.contains("Property power not found on Cooler"));
    assertTrue(json.contains("Using default mixing rule"));
  }

  @Test
  void testToJsonWithReport() {
    String reportJson = "{\"unit\": \"feed\", \"temperature\": 298.15}";
    SimulationResult result = SimulationResult.success(new ProcessSystem("test"), reportJson, null);
    String json = result.toJson();
    assertTrue(json.contains("\"report\""));
    assertTrue(json.contains("298.15"));
  }

  @Test
  void testErrorDetailToJsonObject() {
    SimulationResult.ErrorDetail detail = new SimulationResult.ErrorDetail("MISSING_INLET",
        "No inlet stream", "Compressor-1", "Connect a stream");
    com.google.gson.JsonObject jsonObj = detail.toJsonObject();
    assertEquals("MISSING_INLET", jsonObj.get("code").getAsString());
    assertEquals("No inlet stream", jsonObj.get("message").getAsString());
    assertEquals("Compressor-1", jsonObj.get("unit").getAsString());
    assertEquals("Connect a stream", jsonObj.get("remediation").getAsString());
  }

  @Test
  void testErrorDetailToString() {
    SimulationResult.ErrorDetail detail =
        new SimulationResult.ErrorDetail("ERR", "message", "unit1", "fix it");
    String str = detail.toString();
    assertTrue(str.contains("ERR"));
    assertTrue(str.contains("message"));
    assertTrue(str.contains("unit1"));
    assertTrue(str.contains("fix it"));
  }

  @Test
  void testFailureWithProcess() {
    ProcessSystem process = new ProcessSystem("partial");
    java.util.List<SimulationResult.ErrorDetail> errors = new java.util.ArrayList<>();
    errors.add(new SimulationResult.ErrorDetail("TEST", "err", null, "fix"));
    SimulationResult result = SimulationResult.failure(process, errors, null);
    assertTrue(result.isError());
    assertNotNull(result.getProcessSystem());
    assertEquals(1, result.getErrors().size());
  }

  @Test
  void testToStringFormats() {
    SimulationResult success = SimulationResult.success(new ProcessSystem("t"), null, null);
    assertTrue(success.toString().contains("SUCCESS"));

    SimulationResult error = SimulationResult.error("E", "m", "r");
    assertTrue(error.toString().contains("ERROR"));
    assertTrue(error.toString().contains("1 errors"));
  }
}
