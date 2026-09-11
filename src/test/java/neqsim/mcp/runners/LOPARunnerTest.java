package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link LOPARunner}.
 */
class LOPARunnerTest {

  @Test
  void calculatesCanonicalTargetMetCaseWithScreeningBoundary() {
    JsonObject result = run("{" + "\"scenario\":\"Overpressure of HP separator\","
        + "\"initiatingEventFrequency_per_year\":0.1," + "\"targetFrequency_per_year\":1.1e-4," + "\"layers\":["
        + "{\"name\":\"BPCS\",\"pfd\":0.1}," + "{\"name\":\"Relief valve\",\"pfd\":0.01}" + "]}");

    assertEquals("success", result.get("status").getAsString());
    assertTrue(result.get("screeningOnly").getAsBoolean());
    assertFalse(result.get("standardConformanceClaimed").getAsBoolean());
    assertEquals("CALLER_SUPPLIED_FREQUENCIES_AND_LAYER_PFDS", result.get("inputBasis").getAsString());
    assertTrue(result.get("advisoryBoundary").getAsString().contains("does not identify hazards"));
    assertEquals(3, result.getAsJsonArray("assumptions").size());

    JsonObject lopa = result.getAsJsonObject("lopa");
    assertEquals(0.1, lopa.get("initiatingEventFrequency").getAsDouble(), 0.0);
    assertEquals(0.0001, lopa.get("mitigatedFrequency").getAsDouble(), 1.0e-15);
    assertEquals(1000.0, lopa.get("totalRRF").getAsDouble(), 1.0e-12);
    assertTrue(result.getAsJsonObject("gapAnalysis").get("targetMet").getAsBoolean());
  }

  @Test
  void calculatesCanonicalGapAndLabelsIndicativeSilBand() {
    JsonObject result = run(
        "{" + "\"scenario\":\"Insufficient mitigation\"," + "\"initiatingEventFrequency_per_year\":0.5,"
            + "\"targetFrequency_per_year\":1.0e-5," + "\"layers\":[{\"name\":\"BPCS\",\"pfd\":0.1}]}");
    JsonObject gap = result.getAsJsonObject("gapAnalysis");

    assertEquals("success", result.get("status").getAsString());
    assertFalse(gap.get("targetMet").getAsBoolean());
    assertEquals(5000.0, gap.get("requiredAdditionalRRF").getAsDouble(), 0.0);
    assertEquals(3, gap.get("requiredAdditionalSIL").getAsInt());
    assertTrue(gap.get("requiredAdditionalSILIsIndicative").getAsBoolean());
    assertEquals(0.0002, gap.get("requiredAdditionalPFD").getAsDouble(), 0.0);
  }

  @Test
  void preservesCallerLayerOrderAndDeterministicDefaultScenario() {
    String input = "{" + "\"initiatingEventFrequency_per_year\":0.1," + "\"targetFrequency_per_year\":1.0e-5,"
        + "\"layers\":[" + "{\"name\":\"First\",\"pfd\":0.1},{\"name\":\"Second\",\"pfd\":0.2}]}";
    JsonObject first = run(input);
    JsonObject second = run(input);
    JsonArray layers = first.getAsJsonObject("lopa").getAsJsonArray("protectionLayers");

    assertEquals("unnamed scenario", first.getAsJsonObject("lopa").get("scenarioName").getAsString());
    assertEquals("First", layers.get(0).getAsJsonObject().get("name").getAsString());
    assertEquals("Second", layers.get(1).getAsJsonObject().get("name").getAsString());
    assertEquals(first, second);
  }

  @Test
  void rejectsMissingWrongShapeAndMalformedRequestsWithoutParserDetails() {
    assertErrorCode("", "INVALID_INPUT");
    assertErrorCode("[]", "INVALID_INPUT");
    assertErrorCode("{not-json", "INVALID_INPUT");
    assertErrorCode("{\"scenario\":\"x\"}", "INVALID_INPUT");
    JsonObject malformed = run("{not-json");
    assertFalse(malformed.get("message").getAsString().contains("line"));
  }

  @Test
  void rejectsNonFiniteNonPositiveAndWrongTypeFrequencies() {
    assertErrorCode(request("0", "1e-5", oneLayer()), "INVALID_INPUT");
    assertErrorCode(request("-0.1", "1e-5", oneLayer()), "INVALID_INPUT");
    assertErrorCode(request("NaN", "1e-5", oneLayer()), "INVALID_INPUT");
    assertErrorCode(request("0.1", "Infinity", oneLayer()), "INVALID_INPUT");
    assertErrorCode(request("\"often\"", "1e-5", oneLayer()), "INVALID_INPUT");
  }

  @Test
  void rejectsInvalidLayerShapesNamesAndPfds() {
    assertErrorCode(request("0.1", "1e-5", "[]"), "INVALID_INPUT");
    assertErrorCode(request("0.1", "1e-5", "[\"layer\"]"), "INVALID_LAYER");
    assertErrorCode(request("0.1", "1e-5", "[{\"pfd\":0.1}]"), "INVALID_LAYER");
    assertErrorCode(request("0.1", "1e-5", "[{\"name\":\" \" ,\"pfd\":0.1}]"), "INVALID_LAYER");
    assertErrorCode(request("0.1", "1e-5", "[{\"name\":\"x\",\"pfd\":0}]"), "INVALID_LAYER");
    assertErrorCode(request("0.1", "1e-5", "[{\"name\":\"x\",\"pfd\":1.01}]"), "INVALID_LAYER");
    assertErrorCode(request("0.1", "1e-5", "[{\"name\":\"x\",\"pfd\":NaN}]"), "INVALID_INPUT");
  }

  @Test
  void boundsRequestLayerCountAndNames() {
    assertErrorCode(request("0.1", "1e-5", "[{\"name\":\"" + repeat('n', 257) + "\",\"pfd\":0.1}]"), "INVALID_LAYER");
    StringBuilder layers = new StringBuilder("[");
    for (int i = 0; i < 101; i++) {
      if (i > 0) {
        layers.append(',');
      }
      layers.append("{\"name\":\"L").append(i).append("\",\"pfd\":1}");
    }
    layers.append(']');
    assertErrorCode(request("0.1", "1e-5", layers.toString()), "TOO_MANY_LAYERS");
    assertErrorCode(request("0.1", "1e-5", "[{\"name\":\"" + repeat('x', 16400) + "\",\"pfd\":0.1}]"),
        "REQUEST_TOO_LARGE");
  }

  @Test
  void rejectsUnderflowInsteadOfReportingAbsoluteProtection() {
    StringBuilder layers = new StringBuilder("[");
    for (int i = 0; i < 100; i++) {
      if (i > 0) {
        layers.append(',');
      }
      layers.append("{\"name\":\"L").append(i).append("\",\"pfd\":1e-100}");
    }
    layers.append(']');
    assertErrorCode(request("0.1", "1e-5", layers.toString()), "CALCULATION_OUT_OF_RANGE");
  }

  private static JsonObject run(String input) {
    return JsonParser.parseString(LOPARunner.run(input)).getAsJsonObject();
  }

  private static String request(String initiating, String target, String layers) {
    return "{\"initiatingEventFrequency_per_year\":" + initiating + ",\"targetFrequency_per_year\":" + target
        + ",\"layers\":" + layers + "}";
  }

  private static String oneLayer() {
    return "[{\"name\":\"Layer\",\"pfd\":0.1}]";
  }

  private static void assertErrorCode(String input, String code) {
    JsonObject result = run(input);
    assertEquals("error", result.get("status").getAsString());
    assertEquals(code, result.get("code").getAsString());
    assertTrue(result.get("screeningOnly").getAsBoolean());
    assertFalse(result.get("standardConformanceClaimed").getAsBoolean());
  }

  private static String repeat(char value, int count) {
    StringBuilder result = new StringBuilder(count);
    for (int i = 0; i < count; i++) {
      result.append(value);
    }
    return result.toString();
  }
}
