package neqsim.mcp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FlashRequest}.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class FlashRequestTest {

  @Test
  void testDefaultConstructor() {
    FlashRequest req = new FlashRequest();

    assertEquals("SRK", req.getModel());
    assertEquals("TP", req.getFlashType());
    assertEquals("classic", req.getMixingRule());
    assertNotNull(req.getTemperature());
    assertNotNull(req.getPressure());
    assertNotNull(req.getComponents());
    assertTrue(req.getComponents().isEmpty());
  }

  @Test
  void testBuilderPattern() {
    FlashRequest req =
        new FlashRequest().setModel("PR").setTemperature(new ValueWithUnit(25.0, "C"))
            .setPressure(new ValueWithUnit(50.0, "bara")).setFlashType("TP")
            .addComponent("methane", 0.85).addComponent("ethane", 0.15).setMixingRule("classic");

    assertEquals("PR", req.getModel());
    assertEquals(25.0, req.getTemperature().getValue(), 1e-9);
    assertEquals("C", req.getTemperature().getUnit());
    assertEquals(2, req.getComponents().size());
    assertEquals(0.85, req.getComponents().get("methane"), 1e-9);
  }

  @Test
  void testFromJson_complete() {
    JsonObject json = new JsonObject();
    json.addProperty("model", "PR");

    JsonObject temp = new JsonObject();
    temp.addProperty("value", 100.0);
    temp.addProperty("unit", "C");
    json.add("temperature", temp);

    JsonObject pres = new JsonObject();
    pres.addProperty("value", 30.0);
    pres.addProperty("unit", "bara");
    json.add("pressure", pres);

    json.addProperty("flashType", "dewPointT");
    json.addProperty("mixingRule", "classic");

    JsonObject comps = new JsonObject();
    comps.addProperty("methane", 0.90);
    comps.addProperty("ethane", 0.10);
    json.add("components", comps);

    FlashRequest req = FlashRequest.fromJson(json);

    assertEquals("PR", req.getModel());
    assertEquals(100.0, req.getTemperature().getValue(), 1e-9);
    assertEquals("C", req.getTemperature().getUnit());
    assertEquals(30.0, req.getPressure().getValue(), 1e-9);
    assertEquals("dewPointT", req.getFlashType());
    assertEquals(2, req.getComponents().size());
  }

  @Test
  void testFromJson_minimal() {
    JsonObject json = new JsonObject();
    JsonObject comps = new JsonObject();
    comps.addProperty("methane", 1.0);
    json.add("components", comps);

    FlashRequest req = FlashRequest.fromJson(json);

    // Should use defaults
    assertEquals("SRK", req.getModel());
    assertEquals("TP", req.getFlashType());
    assertEquals("classic", req.getMixingRule());
    assertEquals(1, req.getComponents().size());
  }

  @Test
  void testFromJson_withEnthalpySpec() {
    JsonObject json = new JsonObject();
    JsonObject comps = new JsonObject();
    comps.addProperty("methane", 1.0);
    json.add("components", comps);
    json.addProperty("flashType", "PH");

    JsonObject enth = new JsonObject();
    enth.addProperty("value", -5000.0);
    enth.addProperty("unit", "J/mol");
    json.add("enthalpy", enth);

    FlashRequest req = FlashRequest.fromJson(json);

    assertNotNull(req.getEnthalpy());
    assertEquals(-5000.0, req.getEnthalpy().getValue(), 1e-9);
    assertEquals("J/mol", req.getEnthalpy().getUnit());
  }

  @Test
  void testSettersReturnThis() {
    FlashRequest req = new FlashRequest();

    // Verify chaining works (compile check + non-null)
    FlashRequest result =
        req.setModel("CPA").setFlashType("PH").setEnthalpy(new ValueWithUnit(-5000.0, "J/mol"))
            .setEntropy(new ValueWithUnit(100.0, "J/molK"))
            .setVolume(new ValueWithUnit(0.001, "m3/mol"));

    assertNotNull(result);
    assertEquals("CPA", result.getModel());
  }
}
