package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link WellIntegrityRunner}.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class WellIntegrityRunnerTest {

  /**
   * A complete producer with a normal annulus screens as success and acceptable.
   */
  @Test
  void testSuccessfulScreening() {
    String json = "{" + "\"wellId\":\"WELL-A1\",\"wellType\":\"OIL_PRODUCER\",\"installationCode\":\"AAA\","
        + "\"primaryEnvelope\":{\"elements\":["
        + "{\"type\":\"TUBING\",\"name\":\"Tubing\",\"status\":\"INTACT\",\"verified\":true},"
        + "{\"type\":\"DHSV\",\"name\":\"SCSSV\",\"status\":\"INTACT\",\"verified\":true},"
        + "{\"type\":\"XMAS_TREE\",\"name\":\"Tree\",\"status\":\"INTACT\",\"verified\":true}]},"
        + "\"secondaryEnvelope\":{\"elements\":["
        + "{\"type\":\"CASING\",\"name\":\"Casing\",\"status\":\"INTACT\",\"verified\":true},"
        + "{\"type\":\"CEMENT\",\"name\":\"Cement\",\"status\":\"INTACT\",\"verified\":true},"
        + "{\"type\":\"WELLHEAD\",\"name\":\"Wellhead\",\"status\":\"INTACT\",\"verified\":true}]},"
        + "\"annuli\":[{\"id\":\"A\",\"measuredPressureBara\":2.0,\"maaspBara\":80.0,\"bleedsToZero\":true}]}";
    String result = WellIntegrityRunner.run(json);
    JsonObject out = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", out.get("status").getAsString());
    assertEquals("WELL-A1", out.get("wellId").getAsString());
    assertTrue(out.get("reviewRequired").getAsBoolean());
    assertEquals("ACCEPTABLE", out.get("disposition").getAsString());
    assertTrue(out.get("barrierVerification").getAsJsonObject().get("verificationPassed").getAsBoolean());
  }

  /**
   * An annulus above MAASP yields an intervention disposition.
   */
  @Test
  void testInterventionDisposition() {
    String json = "{\"wellId\":\"WELL-A1\",\"wellType\":\"OIL_PRODUCER\","
        + "\"annuli\":[{\"id\":\"A\",\"measuredPressureBara\":90.0,\"maaspBara\":80.0}]}";
    String result = WellIntegrityRunner.run(json);
    JsonObject out = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", out.get("status").getAsString());
    assertEquals("INTERVENTION_REQUIRED", out.get("disposition").getAsString());
  }

  /**
   * Empty input returns a structured error.
   */
  @Test
  void testEmptyInputError() {
    String result = WellIntegrityRunner.run("");
    JsonObject out = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", out.get("status").getAsString());
  }
}
