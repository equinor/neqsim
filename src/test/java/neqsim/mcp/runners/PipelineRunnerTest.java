package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.catalog.ExampleCatalog;

/**
 * Tests for {@link PipelineRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PipelineRunnerTest {

  @Test
  void testMultiphaseFlow() {
    String result = PipelineRunner.run(ExampleCatalog.pipelineMultiphase());
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString(), "Pipeline failed: " + result);
  }

  @Test
  void testNullInput() {
    String result = PipelineRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }
}
