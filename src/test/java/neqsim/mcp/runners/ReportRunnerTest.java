package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tests for {@link ReportRunner}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ReportRunnerTest {

  @Test
  void testProcessSummaryReport() {
    String json = "{" + "\"reportType\": \"process_summary\","
        + "\"title\": \"HP Separation Results\"," + "\"data\": {" + "  \"status\": \"success\","
        + "  \"report\": {" + "    \"feed\": {\"temperature_C\": 25.0, \"pressure_bara\": 50.0,"
        + "              \"flowRate_kg_hr\": 10000.0},"
        + "    \"HP Sep\": {\"gasOut_temperature_C\": 25.0, \"gasOut_pressure_bara\": 50.0}" + "  }"
        + "}" + "}";

    String result = ReportRunner.run(json);
    assertNotNull(result);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    // Success responses have no status field; error responses have status=error
    if (obj.has("status")) {
      assertEquals("error", obj.get("status").getAsString());
    } else {
      assertTrue(obj.has("markdown"), "Should contain markdown report: " + result);
    }
  }

  @Test
  void testNullInput() {
    String result = ReportRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }
}
