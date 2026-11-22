package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.catalog.ExampleCatalog;
import neqsim.mcp.catalog.SchemaCatalog;

/**
 * Exercises inventory balance evidence through the same JSON runner used by the root-cause tool.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class RootCauseMassBalanceTest {
  /**
   * Builds the catalog process request with simulation verification disabled for this test.
   *
   * @return root-cause tool request
   */
  private static JsonObject request() {
    String example = ExampleCatalog.getExample("root-cause", "compressor-high-vibration");
    JsonObject input = JsonParser.parseString(example).getAsJsonObject();
    input.addProperty("simulationEnabled", false);
    return input;
  }

  /**
   * Builds a two-second separator event with 5 kg/s feed, 4 kg/s export and constant inventory.
   *
   * @return mass-balance evidence block
   */
  private static JsonObject evidence() {
    return JsonParser.parseString("{\"sampleIntervalSeconds\":1,"
        + "\"signals\":{\"inventory\":[100,100,100],\"feed\":[5,5,5],\"export\":[4,4,4]},"
        + "\"inventoryChangeBoundsKg\":{\"inventory\":0.2},"
        + "\"meters\":[{\"tag\":\"feed\",\"from\":null,\"to\":\"inventory\",\"errorKgPerS\":0.05},"
        + "{\"tag\":\"export\",\"from\":\"inventory\",\"to\":null,\"errorKgPerS\":0.05}]}").getAsJsonObject();
  }

  /**
   * Verifies conserved severity, units, ambiguous alternatives and legacy tool behavior.
   */
  @Test
  void exposesAmbiguousBalanceWithoutChangingHypothesisRanking() {
    JsonObject input = request();
    JsonObject legacy = JsonParser.parseString(RootCauseRunner.run(input.toString())).getAsJsonObject();
    assertEquals("success", legacy.get("status").getAsString());
    assertFalse(legacy.has("massBalance"));
    input.add("massBalance", evidence());
    JsonObject output = JsonParser.parseString(RootCauseRunner.run(input.toString())).getAsJsonObject();
    assertEquals("success", output.get("status").getAsString(), output.toString());
    assertEquals(legacy.get("hypotheses"), output.get("hypotheses"));
    JsonObject balance = output.getAsJsonObject("massBalance");
    assertEquals("AMBIGUOUS", balance.get("status").getAsString());
    assertEquals(3, balance.getAsJsonArray("candidates").size());
    JsonObject residual = balance.getAsJsonObject("residuals").getAsJsonObject("inventory");
    assertEquals(1.0, residual.get("residualKgPerS").getAsDouble(), 1e-12);
    assertEquals(0.2, residual.get("boundKgPerS").getAsDouble(), 1e-12);
  }

  /**
   * Verifies strict errors on missing uncertainties, mistyped samples, nonfinite input and undeclared signals.
   */
  @Test
  void rejectsMalformedEvidenceRatherThanFillingOrIgnoringValues() {
    JsonObject block = evidence();
    block.getAsJsonArray("meters").get(0).getAsJsonObject().remove("errorKgPerS");
    assertRejected(block, "errorKgPerS");
    block = evidence();
    block.getAsJsonObject("signals").getAsJsonArray("feed").add("5");
    assertRejected(block, "finite number");
    block = evidence();
    block.getAsJsonObject("signals").add("unused", JsonParser.parseString("[0,0,0]"));
    assertRejected(block, "exactly");
    block = evidence();
    block.add("sampleIntervalSeconds", JsonParser.parseString("1e1000"));
    assertRejected(block, "finite number");
  }

  /**
   * Executes the published separator/receiver evidence block through the tool entry point.
   *
   * @throws IOException when the guide cannot be read
   */
  @Test
  void executesPublishedSharedMeterExample() throws IOException {
    String guide = new String(Files.readAllBytes(Paths.get("docs/diagnostics/root_cause_analysis.md")),
        StandardCharsets.UTF_8);
    int section = guide.indexOf("### Worked separator and receiver case");
    int start = guide.indexOf("```json", section) + "```json".length();
    int end = guide.indexOf("```", start);
    JsonObject example = JsonParser.parseString(guide.substring(start, end)).getAsJsonObject();
    JsonObject request = request();
    request.add("massBalance", example.get("massBalance"));
    JsonObject result = JsonParser.parseString(RootCauseRunner.run(request.toString())).getAsJsonObject();
    JsonObject balance = result.getAsJsonObject("massBalance");
    assertEquals("SINGLE_CANDIDATE", balance.get("status").getAsString());
    JsonObject candidate = balance.getAsJsonArray("candidates").get(0).getAsJsonObject();
    assertEquals("transfer", candidate.get("tag").getAsString());
    assertEquals(0.9, candidate.get("minimumKgPerS").getAsDouble(), 1e-12);
    assertEquals(1.1, candidate.get("maximumKgPerS").getAsDouble(), 1e-12);
  }

  /**
   * Verifies the schema exposes both the input units and the output evidence.
   */
  @Test
  void discoversEvidenceThroughCatalog() {
    JsonObject input = JsonParser.parseString(SchemaCatalog.rootCauseInputSchema()).getAsJsonObject();
    JsonObject fields = input.getAsJsonObject("properties").getAsJsonObject("massBalance")
        .getAsJsonObject("properties");
    JsonObject meter = fields.getAsJsonObject("meters").getAsJsonObject("items");
    assertTrue(meter.getAsJsonObject("properties").has("errorKgPerS"));
    assertFalse(meter.get("additionalProperties").getAsBoolean());
    JsonObject output = JsonParser.parseString(SchemaCatalog.rootCauseOutputSchema()).getAsJsonObject();
    assertTrue(output.getAsJsonObject("properties").getAsJsonObject("massBalance").getAsJsonObject("properties")
        .has("candidates"));
  }

  /**
   * Asserts that the tool reports an actionable JSON error.
   *
   * @param evidence malformed optional evidence block
   * @param detail expected diagnostic detail
   */
  private static void assertRejected(JsonObject evidence, String detail) {
    JsonObject input = request();
    input.add("massBalance", evidence);
    JsonObject output = JsonParser.parseString(RootCauseRunner.run(input.toString())).getAsJsonObject();
    assertEquals("error", output.get("status").getAsString());
    assertTrue(output.get("message").getAsString().contains(detail), output.toString());
    assertFalse(output.has("hypotheses"));
  }
}
