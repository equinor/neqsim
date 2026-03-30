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
}
