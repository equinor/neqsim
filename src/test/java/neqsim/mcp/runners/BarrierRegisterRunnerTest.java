package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.catalog.ExampleCatalog;
import neqsim.mcp.catalog.SchemaCatalog;

/**
 * Tests for {@link BarrierRegisterRunner}.
 */
class BarrierRegisterRunnerTest {

  /**
   * Verifies that the catalog example produces a valid audit result.
   */
  @Test
  void testCatalogExampleCreatesSafetyHandoffs() {
    String result = BarrierRegisterRunner.run(ExampleCatalog.getExample("safety", "barrier-register"));
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", obj.get("status").getAsString());
    JsonObject summary = obj.getAsJsonObject("summary");
    assertEquals(1, summary.get("sceCount").getAsInt());
    assertEquals(2, summary.get("barrierCount").getAsInt());
    assertEquals(2, summary.get("availableBarrierCount").getAsInt());

    JsonObject validation = obj.getAsJsonObject("validation");
    assertTrue(validation.get("valid").getAsBoolean());

    JsonArray layers = obj.getAsJsonObject("lopaHandoff").getAsJsonArray("layers");
    assertEquals(2, layers.size());
    assertTrue(obj.getAsJsonObject("silHandoff").getAsJsonArray("candidates").size() >= 1);
    assertEquals(2, obj.getAsJsonObject("bowTieHandoff").getAsJsonArray("barriers").size());
  }

  /**
   * Verifies that the compact documentation example runs and validates.
   */
  @Test
  void testDocumentationExampleRuns() {
    String json = "{\"action\":\"audit\",\"register\":{"
        + "\"registerId\":\"BR-HP-SEP-001\","
        + "\"name\":\"HP separator overpressure barrier register\","
        + "\"evidence\":[{\"evidenceId\":\"EV-SRS-001\","
        + "\"documentId\":\"SRS-HP-101\","
        + "\"documentTitle\":\"HIPPS safety requirements specification\","
        + "\"revision\":\"1\",\"section\":\"Performance requirements\","
        + "\"page\":12,\"sourceReference\":\"SIF-HIPPS-101 table\","
        + "\"excerpt\":\"SIF-HIPPS-101 shall achieve PFDavg <= 1E-3.\","
        + "\"confidence\":0.95}],"
        + "\"performanceStandards\":[{\"id\":\"PS-HIPPS-101\","
        + "\"title\":\"HIPPS overpressure protection\","
        + "\"safetyFunction\":\"Prevent HP separator overpressure from blocked outlet\","
        + "\"demandMode\":\"LOW_DEMAND\",\"targetPfd\":0.001,"
        + "\"requiredAvailability\":0.99,\"proofTestIntervalHours\":8760,"
        + "\"responseTimeSeconds\":2.0,"
        + "\"acceptanceCriteria\":[\"Close inlet ESD valve before separator MAWP is exceeded\"],"
        + "\"evidenceRefs\":[\"EV-SRS-001\"]}],"
        + "\"barriers\":[{\"id\":\"B-HIPPS-101\","
        + "\"name\":\"HIPPS inlet shutdown\",\"type\":\"PREVENTION\","
        + "\"status\":\"AVAILABLE\",\"pfd\":0.001,"
        + "\"performanceStandardId\":\"PS-HIPPS-101\","
        + "\"equipmentTags\":[\"V-101\",\"ESDV-101\"],"
        + "\"hazardIds\":[\"HAZ-OP-001\"],\"evidenceRefs\":[\"EV-SRS-001\"]}],"
        + "\"safetyCriticalElements\":[{\"id\":\"SCE-V-101\",\"tag\":\"V-101\","
        + "\"name\":\"HP separator pressure protection\",\"type\":\"PROCESS_EQUIPMENT\","
        + "\"equipmentTags\":[\"V-101\"],\"barrierRefs\":[\"B-HIPPS-101\"],"
        + "\"evidenceRefs\":[\"EV-SRS-001\"]}]}}";
    JsonObject obj = JsonParser.parseString(BarrierRegisterRunner.run(json)).getAsJsonObject();

    assertEquals("success", obj.get("status").getAsString());
    assertTrue(obj.getAsJsonObject("validation").get("valid").getAsBoolean());
    assertEquals(1, obj.getAsJsonObject("lopaHandoff").getAsJsonArray("layers").size());
  }

  /**
   * Verifies that validation findings are returned for incomplete registers.
   */
  @Test
  void testMissingEvidenceReturnsValidationFindings() {
    String json = "{\"register\":{\"registerId\":\"BR-X\",\"barriers\":[{"
        + "\"id\":\"B-X\",\"name\":\"Manual isolation\",\"status\":\"AVAILABLE\","
        + "\"type\":\"PREVENTION\",\"pfd\":0.1}]}}";
    String result = BarrierRegisterRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();

    assertEquals("success", obj.get("status").getAsString());
    JsonObject validation = obj.getAsJsonObject("validation");
    assertEquals(false, validation.get("valid").getAsBoolean());
    assertTrue(validation.getAsJsonArray("findings").size() > 0);
    assertEquals(0, obj.getAsJsonObject("lopaHandoff").getAsJsonArray("layers").size());
  }

  /**
   * Verifies that invalid input reports an error envelope.
   */
  @Test
  void testInvalidJsonReturnsError() {
    String result = BarrierRegisterRunner.run("not-json");
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }

  /**
   * Verifies that schemas and examples are discoverable through the catalogs.
   */
  @Test
  void testCatalogDiscovery() {
    assertTrue(ExampleCatalog.getCategories().contains("safety"));
    assertNotNull(ExampleCatalog.getExample("safety", "barrier-register"));
    assertTrue(SchemaCatalog.getToolNames().contains("run_barrier_register"));
    assertNotNull(SchemaCatalog.getSchema("run_barrier_register", "input"));
    assertNotNull(SchemaCatalog.getSchema("run_barrier_register", "output"));
  }
}
