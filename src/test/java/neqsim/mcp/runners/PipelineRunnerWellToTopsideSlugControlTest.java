package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** MCP replay coverage for the synthetic well-to-topside slug-control case. */
class PipelineRunnerWellToTopsideSlugControlTest {
  /** Rejects unsupported mesh choices before starting the expensive replay. */
  @Test
  void rejectsUnsupportedMeshRefinement() {
    JsonObject result = JsonParser
        .parseString(PipelineRunner.run("{\"analysis\":\"wellToTopsideSlugControl\",\"durationSeconds\":4,"
            + "\"timeStepSeconds\":0.25,\"sampleIntervalSeconds\":1,\"meshRefinement\":3}"))
        .getAsJsonObject();
    assertEquals("error", result.get("status").getAsString());
    assertEquals("INPUT_ERROR", result.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }

  /** Verifies a bounded replay can be invoked and serialized through the existing runner. */
  @Test
  void invokesShortReplayThroughPipelineRunner() {
    JsonObject result = JsonParser
        .parseString(PipelineRunner.run("{\"analysis\":\"wellToTopsideSlugControl\",\"durationSeconds\":4,"
            + "\"timeStepSeconds\":0.25,\"sampleIntervalSeconds\":1,\"meshRefinement\":1}"))
        .getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), result.toString());
    assertEquals("neqsim-5036-well-to-topside", result.getAsJsonObject("data").get("caseId").getAsString());
    assertEquals("qualified fixed pressure", result.getAsJsonObject("data").get("outletBoundaryMode").getAsString());
    JsonObject evidence = result.getAsJsonObject("quantityEvidence");
    assertEquals(60, evidence.get("meshCells").getAsInt());
    assertFalse(evidence.get("pairedRefinementExecuted").getAsBoolean());
    assertFalse(evidence.get("independentFieldComparison").getAsBoolean());
    assertEquals(result.getAsJsonObject("data").getAsJsonObject("regulated").get("liquidPeak_kg_s"),
        evidence.getAsJsonObject("regulated").get("peakRiserLiquidKgPerSecond"));
    assertTrue(result.getAsJsonObject("data").getAsJsonObject("openLoop").getAsJsonArray("timeSeries").size() >= 4);
    assertTrue(result.getAsJsonObject("data").getAsJsonObject("regulated").getAsJsonArray("timeSeries").size() >= 4);
  }

  /** Runs both control modes through the public MCP route and retains the sampled time series. */
  @Test
  @Tag("slow")
  void returnsMatchedComparisonAndConservativeTimeSeries() {
    JsonObject result = JsonParser
        .parseString(PipelineRunner.run("{\"analysis\":\"wellToTopsideSlugControl\",\"durationSeconds\":90,"
            + "\"timeStepSeconds\":0.25,\"sampleIntervalSeconds\":1,\"meshRefinement\":1}"))
        .getAsJsonObject();
    assertEquals("success", result.get("status").getAsString(), result.toString());
    JsonObject data = result.getAsJsonObject("data");
    assertTrue(data.get("meanProductionRelativeDifference").getAsDouble() < 0.02);
    assertTrue(data.get("liquidPeakRelativeChange").getAsDouble() < -0.02);
    assertTrue(data.get("separatorPressureRangeRelativeChange").getAsDouble() < 0.0);
    double openSpread = pressureStandardDeviation(data.getAsJsonObject("openLoop"));
    double regulatedSpread = pressureStandardDeviation(data.getAsJsonObject("regulated"));
    assertTrue(openSpread - regulatedSpread > 0.02,
        "open/regulated pressure spread (bar): " + openSpread + "/" + regulatedSpread);
    assertTrue(data.getAsJsonObject("openLoop").get("maximumPipeResidual_kg").getAsDouble() < 1.0e-8);
    assertTrue(data.getAsJsonObject("regulated").get("maximumPipeResidual_kg").getAsDouble() < 1.0e-8);
    assertTrue(data.getAsJsonObject("openLoop").getAsJsonArray("timeSeries").size() >= 90);
    assertTrue(data.getAsJsonObject("regulated").getAsJsonArray("timeSeries").size() >= 90);
  }

  /**
   * Standard deviation across all one-second pressure samples in the replay's analysis window.
   *
   * @param replay one controller configuration
   * @return population standard deviation in bar
   */
  private static double pressureStandardDeviation(JsonObject replay) {
    StandardDeviation pressure = new StandardDeviation(false);
    double start = replay.get("analysisStart_s").getAsDouble();
    for (JsonElement element : replay.getAsJsonArray("timeSeries")) {
      JsonObject sample = element.getAsJsonObject();
      if (sample.get("time_s").getAsDouble() >= start) {
        pressure.increment(sample.get("separatorPressure_bara").getAsDouble());
      }
    }
    return pressure.getResult();
  }
}
