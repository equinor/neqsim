package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link SecurityRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class SecurityRunnerTest {

  @Test
  void testGetStatus() {
    String json = "{\"action\": \"getStatus\"}";
    String result = SecurityRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Security status failed: " + result);
  }

  @Test
  void testGetAuditLog() {
    String json = "{\"action\": \"getAuditLog\"}";
    String result = SecurityRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Audit log failed: " + result);
  }

  @Test
  void testGetRateLimits() {
    String json = "{\"action\": \"getRateLimits\"}";
    String result = SecurityRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Rate limits failed: " + result);
  }

  @Test
  void testNullInput() {
    String result = SecurityRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }
}
