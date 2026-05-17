package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.catalog.ExampleCatalog;

/**
 * Tests for {@link FieldDevelopmentRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class FieldDevelopmentRunnerTest {

  @Test
  void testNorwegianNCS() {
    String result = FieldDevelopmentRunner.run(ExampleCatalog.economicsNorwegianNCS());
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "NCS economics failed: " + result);
  }

  @Test
  void testDeclineCurve() {
    String result = FieldDevelopmentRunner.run(ExampleCatalog.economicsDeclineCurve());
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Decline curve failed: " + result);
  }

  @Test
  void testNullInput() {
    String result = FieldDevelopmentRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }
}
