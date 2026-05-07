package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.mcp.catalog.ExampleCatalog;
import neqsim.mcp.model.ApiEnvelope;
import neqsim.mcp.model.ProcessResult;

/**
 * Tests for {@link ProcessRunner#runTyped(String)}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ProcessRunnerTypedTest {

  @Test
  void testRunTyped_simpleProcess() {
    String json = ExampleCatalog.processSimpleSeparation();

    ApiEnvelope<ProcessResult> result = ProcessRunner.runTyped(json);

    assertTrue(result.isSuccess());
    assertNotNull(result.getData());
    assertNotNull(result.getData().getProcessSystemName());
    assertNotNull(result.getData().getProcessSystem());
  }

  @Test
  void testRunTyped_nullInput() {
    ApiEnvelope<ProcessResult> result = ProcessRunner.runTyped(null);

    assertFalse(result.isSuccess());
    assertEquals("INPUT_ERROR", result.getErrors().get(0).getCode());
  }

  @Test
  void testRunTyped_emptyInput() {
    ApiEnvelope<ProcessResult> result = ProcessRunner.runTyped("");

    assertFalse(result.isSuccess());
    assertEquals("INPUT_ERROR", result.getErrors().get(0).getCode());
  }

  @Test
  void testRunTyped_malformedJson() {
    ApiEnvelope<ProcessResult> result = ProcessRunner.runTyped("{not valid json");

    assertFalse(result.isSuccess());
    assertEquals("SIMULATION_ERROR", result.getErrors().get(0).getCode());
  }

  @Test
  void testRunTyped_processModelAreas() {
    ApiEnvelope<ProcessResult> result = ProcessRunner.runTyped(processModelJson());

    assertTrue(result.isSuccess());
    assertNotNull(result.getData());
    assertTrue(result.getData().isProcessModel());
    assertEquals("json-process-model", result.getData().getProcessModelName());
    assertEquals(2, result.getData().getAreaNames().size());
    assertNotNull(result.getData().getProcessModel());
  }

  private static String processModelJson() {
    String fluid = "\"fluid\": {" + "\"model\": \"SRK\"," + "\"temperature\": 298.15,"
        + "\"pressure\": 50.0," + "\"components\": {\"methane\": 0.9, \"ethane\": 0.1}" + "}";
    String separation =
        "{" + fluid + "," + "\"process\": [" + "{\"type\": \"Stream\", \"name\": \"feed\","
            + "\"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
            + "{\"type\": \"Separator\", \"name\": \"Sep\", \"inlet\": \"feed\"}" + "]}";
    String compression =
        "{" + fluid + "," + "\"process\": [" + "{\"type\": \"Stream\", \"name\": \"compFeed\","
            + "\"properties\": {\"flowRate\": [10000.0, \"kg/hr\"]}},"
            + "{\"type\": \"Compressor\", \"name\": \"Comp\", \"inlet\": \"compFeed\","
            + "\"properties\": {\"outletPressure\": [80.0, \"bara\"]}}" + "]}";
    return "{\"areas\": {\"separation\": " + separation + ", \"compression\": " + compression
        + "}}";
  }
}
