package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Tests for {@link SILRunner}. */
class SILRunnerTest {

  @Test
  void calculatesCanonicalDirectPfdBandWithScreeningBoundary() {
    JsonObject result = run("{\"name\":\"SIF-300\",\"claimedSIL\":2,\"pfdAvg\":0.005}");
    JsonObject screening = result.getAsJsonObject("screening");

    assertEquals("success", result.get("status").getAsString());
    assertTrue(result.get("screeningOnly").getAsBoolean());
    assertFalse(result.get("standardConformanceClaimed").getAsBoolean());
    assertEquals("CALLER_SUPPLIED_DIRECT_PFD_AVG", result.get("inputBasis").getAsString());
    assertEquals(2, screening.get("achievedSILBand").getAsInt());
    assertTrue(screening.get("claimedBandMetByPfd").getAsBoolean());
    assertTrue(screening.get("silBandIsIndicative").getAsBoolean());
    assertFalse(screening.get("architectureSuitabilityVerified").getAsBoolean());
  }

  @Test
  void calculatesCanonicalComponentContributionsAndPreservesOrder() {
    String input = "{" + "\"name\":\"SIF-100\",\"description\":\"HP shutdown\",\"claimedSIL\":2,"
        + "\"architecture\":\"1oo1\",\"proofTestInterval_hours\":8760,\"components\":["
        + "{\"name\":\"PT\",\"type\":\"sensor\",\"pfd\":0.001},"
        + "{\"name\":\"Logic\",\"type\":\"logic\",\"pfd\":0.0005},"
        + "{\"name\":\"Valve\",\"type\":\"finalElement\",\"pfd\":0.005}]}";
    JsonObject first = run(input);
    JsonObject second = run(input);
    JsonArray components = first.getAsJsonArray("components");

    assertEquals("success", first.get("status").getAsString());
    assertEquals("CALLER_SUPPLIED_COMPONENT_PFD_OR_FAILURE_RATE", first.get("inputBasis").getAsString());
    assertEquals(0.0065, first.getAsJsonObject("screening").get("pfdAvg").getAsDouble(), 0.0);
    assertEquals("PT", components.get(0).getAsJsonObject().get("name").getAsString());
    assertEquals("Logic", components.get(1).getAsJsonObject().get("name").getAsString());
    assertEquals("Valve", components.get(2).getAsJsonObject().get("name").getAsString());
    assertEquals(first, second);
  }

  @Test
  void delegatesFailureRateArchitectureFormulaToCanonicalModel() {
    JsonObject result = run("{" + "\"name\":\"SIF-200\",\"claimedSIL\":2,\"architecture\":\"1oo1\","
        + "\"proofTestInterval_hours\":8760,\"components\":["
        + "{\"name\":\"PT\",\"type\":\"sensor\",\"lambdaDU_per_hr\":1.0e-7},"
        + "{\"name\":\"Valve\",\"type\":\"finalElement\",\"architecture\":\"1oo2\"," + "\"lambdaDU_per_hr\":5.0e-7}]}");
    double expected = 0.00044439;
    assertEquals("success", result.get("status").getAsString());
    assertEquals(expected, result.getAsJsonObject("screening").get("pfdAvg").getAsDouble(), 1.0e-12);
    assertEquals(2, result.getAsJsonArray("components").size());
  }

  @Test
  void emitsExplicitSafetyAndLifecycleBoundary() {
    JsonObject result = run("{\"pfdAvg\":0.01}");
    String boundary = result.get("advisoryBoundary").getAsString();

    assertEquals(3, result.getAsJsonArray("assumptions").size());
    assertTrue(boundary.contains("does not select or approve SIL"));
    assertTrue(boundary.contains("proof-test effectiveness"));
    assertTrue(boundary.contains("independent functional-safety assessment"));
    assertTrue(result.get("standardContext").getAsString().contains("does not demonstrate conformance"));
  }

  @Test
  void rejectsMalformedWrongShapeMissingAndConflictingModesWithoutParserDetails() {
    assertErrorCode("", "INVALID_INPUT");
    assertErrorCode("[]", "INVALID_INPUT");
    assertErrorCode("{not-json", "INVALID_INPUT");
    assertErrorCode("{\"name\":\"X\"}", "INVALID_INPUT");
    assertErrorCode("{\"pfdAvg\":0.01,\"components\":[{\"name\":\"PT\",\"type\":\"sensor\",\"pfd\":0.01}]}",
        "INVALID_INPUT");
    assertFalse(run("{not-json").get("message").getAsString().contains("line"));
  }

  @Test
  void rejectsInvalidTopLevelNumbersArchitectureAndText() {
    for (String input : new String[] { "{\"pfdAvg\":0}", "{\"pfdAvg\":-0.1}", "{\"pfdAvg\":1.01}", "{\"pfdAvg\":NaN}",
        "{\"pfdAvg\":\"low\"}", "{\"pfdAvg\":0.01,\"claimedSIL\":0}", "{\"pfdAvg\":0.01,\"claimedSIL\":2.5}",
        "{\"pfdAvg\":0.01,\"claimedSIL\":5}", "{\"pfdAvg\":0.01,\"architecture\":\"2oo2\"}",
        "{\"pfdAvg\":0.01,\"proofTestInterval_hours\":0}", "{\"pfdAvg\":0.01,\"proofTestInterval_hours\":87601}",
        "{\"pfdAvg\":0.01,\"name\":\" \"}" }) {
      assertErrorCode(input, "INVALID_INPUT");
    }
  }

  @Test
  void rejectsInvalidComponentShapesTypesSourcesAndValues() {
    assertErrorCode("{\"components\":[]}", "INVALID_INPUT");
    for (String components : new String[] { "[\"component\"]", "[{\"type\":\"sensor\",\"pfd\":0.01}]",
        "[{\"name\":\"PT\",\"type\":\"other\",\"pfd\":0.01}]", "[{\"name\":\"PT\",\"type\":\"sensor\"}]",
        "[{\"name\":\"PT\",\"type\":\"sensor\",\"pfd\":0.01,\"lambdaDU_per_hr\":1e-7}]",
        "[{\"name\":\"PT\",\"type\":\"sensor\",\"pfd\":0}]", "[{\"name\":\"PT\",\"type\":\"sensor\",\"pfd\":1.01}]",
        "[{\"name\":\"PT\",\"type\":\"sensor\",\"lambdaDU_per_hr\":0}]",
        "[{\"name\":\"PT\",\"type\":\"sensor\",\"lambdaDU_per_hr\":1.01}]",
        "[{\"name\":\"PT\",\"type\":\"sensor\",\"architecture\":\"2oo2\",\"lambdaDU_per_hr\":1e-7}]" }) {
      assertErrorCode("{\"components\":" + components + "}", "INVALID_COMPONENT");
    }
  }

  @Test
  void boundsRequestComponentCountAndText() {
    StringBuilder components = new StringBuilder("[");
    for (int i = 0; i < 101; i++) {
      if (i > 0) {
        components.append(',');
      }
      components.append("{\"name\":\"C").append(i).append("\",\"type\":\"sensor\",\"pfd\":0.001}");
    }
    components.append(']');
    assertErrorCode("{\"components\":" + components + "}", "TOO_MANY_COMPONENTS");
    assertErrorCode("{\"components\":[{\"name\":\"" + repeat('n', 257) + "\",\"type\":\"sensor\",\"pfd\":0.01}]}",
        "INVALID_COMPONENT");
    assertErrorCode("{\"name\":\"" + repeat('x', 16400) + "\",\"pfdAvg\":0.01}", "REQUEST_TOO_LARGE");
  }

  @Test
  void rejectsAggregateProbabilityOutsideRange() {
    assertErrorCode("{\"components\":[{\"name\":\"A\",\"type\":\"sensor\",\"pfd\":0.6},"
        + "{\"name\":\"B\",\"type\":\"logic\",\"pfd\":0.5}]}", "CALCULATION_OUT_OF_RANGE");
  }

  private static JsonObject run(String input) {
    return JsonParser.parseString(SILRunner.run(input)).getAsJsonObject();
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
