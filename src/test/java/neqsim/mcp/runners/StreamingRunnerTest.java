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
 * Contract tests for {@link StreamingRunner}.
 *
 * @author Even Solbraa
 * @version 1.1
 */
class StreamingRunnerTest {

  private JsonObject run(String json) {
    return JsonParser.parseString(StreamingRunner.run(json)).getAsJsonObject();
  }

  private String errorCode(JsonObject response) {
    JsonArray errors = response.getAsJsonArray("errors");
    return errors.get(0).getAsJsonObject().get("code").getAsString();
  }

  @Test
  void testListOperationsReportsBounds() {
    JsonObject result = run("{\"action\": \"list\"}");
    assertTrue(result.has("operations"), result.toString());
    JsonObject limits = result.getAsJsonObject("requestLimits");
    assertEquals(StreamingRunner.MAX_SWEEP_POINTS, limits.get("maxSweepPoints").getAsInt());
    assertEquals(StreamingRunner.MAX_DYNAMIC_STEPS, limits.get("maxDynamicSteps").getAsInt());
    assertEquals(StreamingRunner.MAX_MONTE_CARLO_ITERATIONS, limits.get("maxMonteCarloIterations").getAsInt());
    assertEquals(StreamingRunner.MAX_RESULTS_PER_POLL, limits.get("maxResultsPerPoll").getAsInt());
  }

  @Test
  void testPollAndCancelNonexistentOperation() {
    JsonObject poll = run("{\"action\": \"poll\", \"operationId\": \"nonexistent-id\"," + " \"lastIndex\": 0}");
    assertEquals("error", poll.get("status").getAsString());
    assertEquals("NOT_FOUND", errorCode(poll));

    JsonObject cancel = run("{\"action\": \"cancel\", \"operationId\": \"nonexistent-id\"}");
    assertEquals("error", cancel.get("status").getAsString());
    assertEquals("NOT_FOUND", errorCode(cancel));
  }

  @Test
  void testNullMalformedAndUnknownInputFailClosed() {
    assertEquals("STREAMING_ERROR", errorCode(run(null)));
    assertEquals("STREAMING_ERROR", errorCode(run("{")));
    assertEquals("UNKNOWN_ACTION", errorCode(run("{\"action\": \"deleteEverything\"}")));
  }

  @Test
  void testSweepRequiresBoundedPointsAndComposition() {
    JsonObject missing = run("{\"action\": \"startSweep\", \"points\": 2}");
    assertEquals("MISSING_COMPONENTS", errorCode(missing));

    String prefix = "{\"action\":\"startSweep\",\"components\":{\"methane\":1.0},";
    assertEquals("INVALID_POINTS", errorCode(run(prefix + "\"points\":0}")));
    assertEquals("INVALID_POINTS",
        errorCode(run(prefix + "\"points\":" + (StreamingRunner.MAX_SWEEP_POINTS + 1) + "}")));
  }

  @Test
  void testSweepRejectsAmbiguousVariableUnitAndRange() {
    String prefix = "{\"action\":\"startSweep\",\"components\":{\"methane\":1.0},";
    assertEquals("INVALID_SWEEP_VARIABLE", errorCode(run(prefix + "\"sweepVariable\":\"enthalpy\",\"points\":2}")));
    assertEquals("INVALID_UNIT",
        errorCode(run(prefix + "\"sweepVariable\":\"temperature\",\"unit\":\"rankine\"," + "\"points\":2}")));
    assertEquals("INVALID_RANGE", errorCode(
        run(prefix + "\"sweepVariable\":\"pressure\",\"unit\":\"bara\"," + "\"from\":0,\"to\":10,\"points\":2}")));
  }

  @Test
  void testMonteCarloRequiresBoundedFiniteDistribution() {
    String prefix = "{\"action\":\"startMonteCarlo\",\"components\":{\"methane\":1.0},";
    assertEquals("INVALID_ITERATIONS", errorCode(run(prefix + "\"iterations\":0}")));
    assertEquals("INVALID_ITERATIONS",
        errorCode(run(prefix + "\"iterations\":" + (StreamingRunner.MAX_MONTE_CARLO_ITERATIONS + 1) + "}")));
    assertEquals("INVALID_DISTRIBUTION", errorCode(run(prefix + "\"iterations\":1,\"temperatureStd\":-1}")));
  }

  @Test
  void testDynamicRequiresProcessAndBoundedTiming() {
    assertEquals("MISSING_PROCESS", errorCode(run("{\"action\":\"startDynamic\"}")));
    String prefix = "{\"action\":\"startDynamic\",\"processJson\":{},";
    assertEquals("INVALID_TIME_RANGE", errorCode(run(prefix + "\"totalTime\":1,\"timeStep\":0}")));
    assertEquals("INVALID_STEP_COUNT",
        errorCode(run(prefix + "\"totalTime\":" + (StreamingRunner.MAX_DYNAMIC_STEPS + 1) + ",\"timeStep\":1}")));
  }

  @Test
  void testNegativePollCursorFailsClosed() {
    JsonObject started = run("{\"action\":\"startSweep\",\"components\":{\"methane\":1.0},"
        + "\"sweepVariable\":\"temperature\",\"from\":20,\"to\":20,\"points\":1}");
    assertEquals("success", started.get("status").getAsString(), started.toString());
    assertEquals("started", started.get("operationStatus").getAsString(), started.toString());
    String operationId = started.get("operationId").getAsString();
    JsonObject poll = run("{\"action\":\"poll\",\"operationId\":\"" + operationId + "\",\"lastIndex\":-1}");
    assertEquals("INVALID_CURSOR", errorCode(poll));
    run("{\"action\":\"cancel\",\"operationId\":\"" + operationId + "\"}");
  }

  @Test
  void testBoundedSweepLifecycleAndPollMetadata() throws InterruptedException {
    JsonObject started = run("{\"action\":\"startSweep\",\"components\":{\"methane\":1.0},"
        + "\"sweepVariable\":\"temperature\",\"from\":20,\"to\":21,\"points\":2}");
    assertEquals("success", started.get("status").getAsString(), started.toString());
    assertEquals("started", started.get("operationStatus").getAsString(), started.toString());
    String operationId = started.get("operationId").getAsString();

    JsonObject poll = null;
    for (int attempt = 0; attempt < 200; attempt++) {
      poll = run("{\"action\":\"poll\",\"operationId\":\"" + operationId + "\",\"lastIndex\":0}");
      if ("completed".equals(poll.get("operationStatus").getAsString())) {
        break;
      }
      Thread.sleep(25L);
    }

    assertNotNull(poll);
    assertEquals("success", poll.get("status").getAsString(), poll.toString());
    assertEquals("completed", poll.get("operationStatus").getAsString(), poll.toString());
    assertEquals(2, poll.get("completedSteps").getAsInt());
    assertEquals(2, poll.get("totalResultCount").getAsInt());
    assertEquals(StreamingRunner.MAX_RESULTS_PER_POLL, poll.get("maxResultsPerPoll").getAsInt());
    assertFalse(poll.get("hasMoreResults").getAsBoolean());

    JsonObject cancelledAfterCompletion = run("{\"action\":\"cancel\",\"operationId\":\"" + operationId + "\"}");
    assertEquals("completed", cancelledAfterCompletion.get("operationStatus").getAsString(),
        "Cancellation must not overwrite a terminal outcome");
  }
}
