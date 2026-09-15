package neqsim.process.processmodel;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.stream.StreamInterface;

/** Regression tests for JSON Stream inlet wiring. */
class JsonProcessBuilderStreamConnectionTest {

  @Test
  void testProductStreamInletTracksEquipmentOutlet() {
    String json = "{" + "\"fluid\": {\"model\": \"SRK\", \"temperature\": 300.0, \"pressure\": 50.0,"
        + " \"components\": {\"methane\": 1.0}}," + "\"process\": ["
        + " {\"type\": \"Stream\", \"name\": \"Feed\","
        + "  \"properties\": {\"flowRate\": [1000.0, \"kg/hr\"]}},"
        + " {\"type\": \"Cooler\", \"name\": \"Cooler\", \"inlet\": \"Feed\","
        + "  \"properties\": {\"outTemperature\": [20.0, \"C\"]}},"
        + " {\"type\": \"Stream\", \"name\": \"Product\", \"inlet\": \"Cooler.outlet\"}"
        + "]}";

    SimulationResult result = new JsonProcessBuilder().build(json);

    assertTrue(result.isSuccess(), "Build should succeed: " + result.toJson());
    ProcessSystem process = result.getProcessSystem();
    Cooler cooler = (Cooler) process.getUnit("Cooler");
    StreamInterface product = (StreamInterface) process.getUnit("Product");
    assertNotNull(cooler);
    assertNotNull(product);

    StreamInterface coolerOutlet = cooler.getOutletStream();
    assertNotNull(coolerOutlet);
    assertSame(coolerOutlet.getFluid(), product.getFluid(),
        "A JSON Stream with an inlet must wrap the referenced product stream, not clone a new source fluid");
  }
}
