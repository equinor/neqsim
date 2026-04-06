package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for the CapabilitiesRunner.
 */
class CapabilitiesRunnerTest {

  @Test
  void testGetCapabilities() {
    String result = CapabilitiesRunner.getCapabilities();
    assertNotNull(result);

    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertTrue("success".equals(obj.get("status").getAsString()));
    assertTrue(obj.has("engine"));
    assertTrue(obj.has("thermodynamics"));
    assertTrue(obj.has("processSimulation"));
    assertTrue(obj.has("calculationModes"));
    assertTrue(obj.has("engineeringDomains"));
    assertTrue(obj.has("trustModel"));

    // Trust model should describe provenance
    JsonObject trust = obj.getAsJsonObject("trustModel");
    assertTrue(trust.get("provenanceIncluded").getAsBoolean());
  }

  @Test
  void testCapabilitiesAreCached() {
    String result1 = CapabilitiesRunner.getCapabilities();
    String result2 = CapabilitiesRunner.getCapabilities();
    // Should return same reference (cached)
    assertTrue(result1 == result2, "Capabilities should be cached");
  }
}
