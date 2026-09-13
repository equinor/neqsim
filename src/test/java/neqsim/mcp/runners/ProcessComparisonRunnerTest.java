package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.catalog.ExampleCatalog;

/** Tests the bounded canonical process-comparison contract. */
class ProcessComparisonRunnerTest {
  private static JsonObject example() {
    return JsonParser.parseString(ExampleCatalog.getExample("comparison", "two-cases"))
        .getAsJsonObject();
  }

  @Test
  void comparesTwoCasesInRequestOrder() {
    JsonObject result =
        JsonParser.parseString(ProcessComparisonRunner.run(example().toString())).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString());
    assertEquals(2, result.get("caseCount").getAsInt());
    assertEquals(2, result.get("successfulCaseCount").getAsInt());
    assertEquals(0, result.get("failedCaseCount").getAsInt());
    assertTrue(result.get("complete").getAsBoolean());
    assertEquals("Low Pressure", result.getAsJsonArray("caseNames").get(0).getAsString());
    assertEquals("High Pressure", result.getAsJsonArray("caseNames").get(1).getAsString());
    for (int index = 0; index < 2; index++) {
      JsonObject caseResult = result.getAsJsonArray("cases").get(index).getAsJsonObject();
      assertTrue(caseResult.get("converged").getAsBoolean());
      assertEquals("success", caseResult.getAsJsonObject("result").get("status").getAsString());
    }
  }

  @Test
  void preservesPartialCanonicalResultsAndCountsFailures() {
    JsonObject request = example();
    JsonObject failingCase = request.getAsJsonArray("cases").get(1).getAsJsonObject();
    failingCase.getAsJsonArray("process").get(0).getAsJsonObject()
        .addProperty("type", "NotARealUnit");
    JsonObject result =
        JsonParser.parseString(ProcessComparisonRunner.run(request.toString())).getAsJsonObject();
    assertEquals("success", result.get("status").getAsString());
    assertFalse(result.get("complete").getAsBoolean());
    assertEquals(1, result.get("successfulCaseCount").getAsInt());
    assertEquals(1, result.get("failedCaseCount").getAsInt());
    assertEquals(1, result.getAsJsonArray("errors").size());
    JsonObject failed = result.getAsJsonArray("cases").get(1).getAsJsonObject();
    assertFalse(failed.get("converged").getAsBoolean());
    assertEquals("error", failed.getAsJsonObject("result").get("status").getAsString());
    assertTrue(failed.has("error"));
  }

  @Test
  void rejectsInvalidCollectionsBeforeExecution() {
    assertError("{}");
    assertError("{\"cases\":[{}]}");
    assertError("{\"cases\":[{},{}]}");
    assertError("{\"cases\":[\"case\",\"case\"]}");
    JsonObject tooMany = new JsonObject();
    JsonArray cases = new JsonArray();
    for (int index = 0; index <= ProcessComparisonRunner.MAX_CASES; index++) {
      cases.add(new JsonObject());
    }
    tooMany.add("cases", cases);
    assertError(tooMany.toString());
  }

  @Test
  void rejectsInvalidAndDuplicateNames() {
    JsonObject request = example();
    request.getAsJsonArray("cases").get(0).getAsJsonObject().addProperty("name", " ");
    assertError(request.toString());

    request = example();
    JsonArray cases = request.getAsJsonArray("cases");
    cases.get(0).getAsJsonObject().addProperty("name", "duplicate");
    cases.get(1).getAsJsonObject().addProperty("name", "duplicate");
    assertError(request.toString());

    request = example();
    request.getAsJsonArray("cases").get(0).getAsJsonObject()
        .addProperty("name", repeated('n', ProcessComparisonRunner.MAX_NAME_LENGTH + 1));
    assertError(request.toString());
  }

  @Test
  void rejectsOversizedAndMalformedRequests() {
    assertError(null);
    assertError("[]");
    assertError("{not-json");
    JsonObject request = example();
    request.addProperty("padding", repeated('x', ProcessComparisonRunner.MAX_REQUEST_BYTES));
    assertError(request.toString());
  }

  private static void assertError(String request) {
    JsonObject result =
        JsonParser.parseString(ProcessComparisonRunner.run(request)).getAsJsonObject();
    assertEquals("error", result.get("status").getAsString());
    assertTrue(result.has("message"));
  }

  private static String repeated(char character, int count) {
    StringBuilder value = new StringBuilder(count);
    for (int index = 0; index < count; index++) {
      value.append(character);
    }
    return value.toString();
  }
}
