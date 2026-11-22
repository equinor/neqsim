package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.fielddevelopment.reservoir.VfpDeckReader;

/**
 * Tests of {@link VfpTableRunner} on a synthetic gas well.
 *
 * @author ESOL
 * @version 1.0
 */
class VfpTableRunnerTest {
  private static String request() {
    return "{\"wellType\":\"gas\",\"tableNumber\":4,\"datumDepth_m\":2500,\"unitSystem\":\"METRIC\","
        + "\"model\":\"SRK\",\"gasComposition\":{\"methane\":0.90,\"ethane\":0.06,\"propane\":0.03,"
        + "\"n-butane\":0.01},\"liquidComposition\":{\"n-hexane\":0.6,\"n-octane\":0.4},\"includeWater\":true,"
        + "\"survey\":{\"md_m\":[0,1500,3200],\"tvd_m\":[0,1450,2500],\"innerDiameter_m\":[0.15,0.15]},"
        + "\"correlation\":\"GRAY\",\"numberOfSegments\":4,\"bottomholeTemperature_C\":95,"
        + "\"wellheadTemperature_C\":40,\"axes\":{\"rates_Sm3d\":[300000,600000,900000],"
        + "\"outletPressures_bara\":[40,60,80],\"waterRatios\":[1e-6,1e-4],\"gasRatios\":[1e-5,1e-4]},"
        + "\"provenance\":[\"synthetic test well\"]}";
  }

  @Test
  void gasWellRequestReturnsDeckAndTable() {
    String response = VfpTableRunner.run(request());
    JsonObject out = JsonParser.parseString(response).getAsJsonObject();
    assertEquals("success", out.get("status").getAsString(), response);
    JsonObject data = out.getAsJsonObject("data");
    String deck = data.get("deck").getAsString();
    assertTrue(deck.contains("VFPPROD"));
    assertTrue(deck.contains("synthetic test well"));
    VfpDeckReader.ParsedTable parsed = VfpDeckReader.parse(deck);
    assertEquals(4, parsed.getTableNumber());
    assertEquals(3, parsed.getAxes()[0].length);
    JsonArray bhp = data.getAsJsonArray("bhp_bara");
    assertEquals(3, bhp.size());
    double first = bhp.get(0).getAsJsonArray().get(0).getAsJsonArray().get(0).getAsJsonArray().get(0).getAsJsonArray()
        .get(0).getAsDouble();
    assertTrue(first > 40.0 && first < 200.0, "BHP " + first);
    JsonObject validation = data.getAsJsonObject("validation");
    assertEquals(0, validation.get("providerFailures").getAsInt());
    assertEquals(0.0, validation.get("deckRoundTripMaxAbsError_bara").getAsDouble(), 1e-9);
  }

  @Test
  void missingAxesGivesStructuredError() {
    JsonObject out = JsonParser.parseString(VfpTableRunner.run("{\"gasComposition\":{\"methane\":1.0}}"))
        .getAsJsonObject();
    assertEquals("error", out.get("status").getAsString());
    assertNotNull(out.getAsJsonArray("errors"));
    assertEquals("MISSING_AXES", out.getAsJsonArray("errors").get(0).getAsJsonObject().get("code").getAsString());
  }
}
