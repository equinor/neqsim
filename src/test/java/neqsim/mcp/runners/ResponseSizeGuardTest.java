package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link ResponseSizeGuard}.
 *
 * <p>
 * A full plant model produces a per-unit report of several megabytes. Returning it exhausts an agent's context and can
 * break the stdio transport mid-session, so oversized responses must be trimmed to a usable summary while the envelope
 * survives intact.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ResponseSizeGuardTest {

  private static final Gson GSON = new Gson();

  /**
   * Builds a response carrying a bulky report block.
   *
   * @param reportEntries number of report entries to generate
   * @return a response object
   */
  private JsonObject responseWithReport(int reportEntries) {
    JsonObject response = new JsonObject();
    response.addProperty("apiVersion", "1.0");
    response.addProperty("status", "success");
    response.addProperty("tool", "runProcess");
    response.addProperty("processSystemName", "json-process");

    JsonObject report = new JsonObject();
    for (int i = 0; i < reportEntries; i++) {
      JsonObject unit = new JsonObject();
      for (int p = 0; p < 40; p++) {
        unit.addProperty("property_" + p, "value-" + i + "-" + p + "-padding-padding-padding");
      }
      report.add("unit_" + i, unit);
    }
    response.add("report", report);

    JsonObject validation = new JsonObject();
    validation.addProperty("valid", true);
    response.add("validation", validation);
    return response;
  }

  /**
   * A small response must pass through untouched.
   */
  @Test
  @DisplayName("Small responses are not trimmed")
  void testSmallResponseUntouched() {
    JsonObject response = responseWithReport(2);
    assertFalse(ResponseSizeGuard.enforce(response, "runProcess"), "Small response must not be trimmed");
    assertTrue(response.has("report"), "Report must survive");
    assertFalse(response.has("truncation"), "No truncation block for a small response");
  }

  /**
   * An oversized response must be trimmed below the limit while keeping the envelope and explaining how to retrieve the
   * omitted detail.
   */
  @Test
  @DisplayName("Oversized responses are trimmed below the limit with recovery guidance")
  void testOversizedResponseIsTrimmed() {
    JsonObject response = responseWithReport(4000);
    int originalBytes = GSON.toJson(response).getBytes(StandardCharsets.UTF_8).length;
    assertTrue(originalBytes > ResponseSizeGuard.getMaxBytes(), "Fixture must exceed the limit, was " + originalBytes);

    assertTrue(ResponseSizeGuard.enforce(response, "runProcess"), "Oversized response must be trimmed");

    int trimmedBytes = GSON.toJson(response).getBytes(StandardCharsets.UTF_8).length;
    assertTrue(trimmedBytes <= ResponseSizeGuard.getMaxBytes(),
        "Trimmed response must fit the limit, was " + trimmedBytes);

    assertFalse(response.has("report"), "The bulky member must be the one removed");
    assertTrue(response.has("status"), "Envelope status must survive");
    assertTrue(response.has("tool"), "Envelope tool must survive");
    assertTrue(response.has("validation"), "Validation block must survive");
    assertTrue(response.has("processSystemName"), "Small informative fields must survive");

    JsonObject truncation = response.getAsJsonObject("truncation");
    assertTrue(truncation.get("truncated").getAsBoolean());
    assertEquals(originalBytes, truncation.get("originalBytes").getAsInt());
    assertEquals(trimmedBytes, truncation.get("returnedBytes").getAsInt());
    assertTrue(truncation.getAsJsonArray("omitted").size() > 0, "Omitted members must be listed");
    assertTrue(truncation.get("howToRetrieve").getAsString().contains("manageModel"),
        "Guidance must point at the model-handle drill-down path");
    assertTrue(response.getAsJsonArray("warnings").size() > 0, "Truncation must raise a warning");
  }

  /**
   * The implementation and Phase 0 evidence inventories have no separate selective-retrieval route, so they must remain
   * discoverable when the larger capability catalog is trimmed.
   */
  @Test
  @DisplayName("Capability trimming preserves implementation and Phase 0 evidence contracts")
  void testCapabilitiesPreserveInventoriesWhenTrimmed() {
    JsonObject response = JsonParser.parseString(CapabilitiesRunner.getCapabilities()).getAsJsonObject();
    int originalBytes = GSON.toJson(response).getBytes(StandardCharsets.UTF_8).length;
    assertTrue(originalBytes > ResponseSizeGuard.getMaxBytes(),
        "Capability fixture must exercise the response-size guard, was " + originalBytes);

    assertTrue(ResponseSizeGuard.enforce(response, "getCapabilities"), "Oversized capability response must be trimmed");

    int trimmedBytes = GSON.toJson(response).getBytes(StandardCharsets.UTF_8).length;
    assertTrue(trimmedBytes <= ResponseSizeGuard.getMaxBytes(),
        "Trimmed capability response must fit the limit, was " + trimmedBytes);
    assertTrue(response.has("phase0EvidenceInventory"),
        "The non-retrievable Phase 0 evidence contract must survive trimming");
    assertTrue(response.getAsJsonObject("data").has("phase0EvidenceInventory"),
        "The canonical data view must retain the same evidence contract");
    assertTrue(response.has("implementationInventory"),
        "The non-retrievable implementation inventory must survive trimming");
    assertTrue(response.getAsJsonObject("data").has("implementationInventory"),
        "The canonical data view must retain the same implementation inventory");
    JsonObject implementationInventory = response.getAsJsonObject("implementationInventory");
    assertTrue(implementationInventory.get("complete").getAsBoolean());
    assertEquals(71, implementationInventory.get("toolBindingCount").getAsInt());
    assertEquals(60, implementationInventory.get("implementationClassCount").getAsInt());
    assertEquals(207, implementationInventory.get("equipmentTypeCount").getAsInt());
    assertEquals(2, implementationInventory.get("reportPathCount").getAsInt());
    assertEquals("neqsim.mcp.runners.ProcessRunner",
        implementationInventory.getAsJsonObject("toolImplementationBindings").get("runProcess").getAsString());
    assertTrue(response.getAsJsonObject("phase0EvidenceInventory").has("tests"));
    assertTrue(response.getAsJsonObject("phase0EvidenceInventory").has("knownLimitations"));
    JsonObject truncation = response.getAsJsonObject("truncation");
    assertEquals(originalBytes, truncation.get("originalBytes").getAsInt());
    assertEquals(trimmedBytes, truncation.get("returnedBytes").getAsInt());
    assertFalse(truncation.getAsJsonArray("omitted").toString().contains("implementationInventory"));
    assertFalse(truncation.getAsJsonArray("omitted").toString().contains("phase0EvidenceInventory"));
    assertTrue(truncation.get("howToRetrieve").getAsString().contains("getSchema"),
        "Discovery truncation must point to focused capability retrieval");
  }

  /**
   * The trimmed response must still be parseable JSON — a truncated payload is worse than useless if the client cannot
   * read it.
   */
  @Test
  @DisplayName("Trimmed response remains valid JSON")
  void testTrimmedResponseIsValidJson() {
    JsonObject response = responseWithReport(4000);
    ResponseSizeGuard.enforce(response, "runProcess");
    JsonObject reparsed = JsonParser.parseString(GSON.toJson(response)).getAsJsonObject();
    assertTrue(reparsed.has("truncation"));
    assertTrue("success".equals(reparsed.get("status").getAsString()));
  }
}
