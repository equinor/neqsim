package neqsim.mcp.runners;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.mcp.catalog.ExampleCatalog;

/**
 * Tests for {@link EquipmentSizingRunner}.
 */
class EquipmentSizingRunnerTest {

  @Test
  void testSizeSeparator() {
    String json = ExampleCatalog.getExample("equipment-sizing", "separator");
    String result = EquipmentSizingRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
    assertEquals("separator", obj.get("equipmentType").getAsString());
    assertTrue(obj.has("sizing"));
    assertTrue(obj.has("designBasis"));
    assertTrue(obj.getAsJsonObject("sizing").has("vesselDiameter_m"));
  }

  @Test
  void testSizeCompressor() {
    String json = ExampleCatalog.getExample("equipment-sizing", "compressor");
    String result = EquipmentSizingRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("success", obj.get("status").getAsString());
    assertEquals("compressor", obj.get("equipmentType").getAsString());
    assertTrue(obj.has("sizing"));
    assertTrue(obj.getAsJsonObject("sizing").has("power_kW"));
    assertTrue(obj.getAsJsonObject("sizing").get("power_kW").getAsDouble() > 0);
  }

  @Test
  void testUnknownEquipmentType() {
    String json = "{\"equipmentType\": \"boiler\", \"components\": {\"methane\": 1.0}}";
    String result = EquipmentSizingRunner.run(json);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }

  @Test
  void testNullInput() {
    String result = EquipmentSizingRunner.run(null);
    JsonObject obj = JsonParser.parseString(result).getAsJsonObject();
    assertEquals("error", obj.get("status").getAsString());
  }
}
