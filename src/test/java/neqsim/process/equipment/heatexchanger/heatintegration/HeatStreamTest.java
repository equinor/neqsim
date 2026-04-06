package neqsim.process.equipment.heatexchanger.heatintegration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for the HeatStream class.
 */
class HeatStreamTest {

  @Test
  void testHotStreamClassification() {
    HeatStream stream = new HeatStream("H1", 200, 100, 15);
    assertEquals(HeatStream.StreamType.HOT, stream.getType());
    assertEquals("H1", stream.getName());
  }

  @Test
  void testColdStreamClassification() {
    HeatStream stream = new HeatStream("C1", 50, 150, 20);
    assertEquals(HeatStream.StreamType.COLD, stream.getType());
  }

  @Test
  void testTemperatureConversion() {
    HeatStream stream = new HeatStream("H1", 100, 50, 10);
    // Supply = 100 C = 373.15 K
    assertEquals(373.15, stream.getSupplyTemperature(), 0.01);
    // Target = 50 C = 323.15 K
    assertEquals(323.15, stream.getTargetTemperature(), 0.01);
    assertEquals(100.0, stream.getSupplyTemperatureC(), 0.01);
    assertEquals(50.0, stream.getTargetTemperatureC(), 0.01);
  }

  @Test
  void testEnthalpyChange() {
    HeatStream stream = new HeatStream("H1", 200, 100, 15);
    // Duty = MCp * |dT| = 15 * 100 = 1500 kW
    assertEquals(1500.0, stream.getEnthalpyChange(), 0.01);
  }

  @Test
  void testHeatCapacityFlowRate() {
    HeatStream stream = new HeatStream("H1", 200, 100, 25.5);
    assertEquals(25.5, stream.getHeatCapacityFlowRate(), 0.001);
  }

  @Test
  void testSetters() {
    HeatStream stream = new HeatStream("test", 100, 50, 10);
    stream.setName("updated");
    assertEquals("updated", stream.getName());

    stream.setSupplyTemperatureC(150);
    assertEquals(150.0, stream.getSupplyTemperatureC(), 0.01);

    stream.setTargetTemperatureC(80);
    assertEquals(80.0, stream.getTargetTemperatureC(), 0.01);

    stream.setHeatCapacityFlowRate(20.0);
    assertEquals(20.0, stream.getHeatCapacityFlowRate(), 0.001);
  }

  @Test
  void testEqualTemperaturesDefaultsToCold() {
    // When supply == target, direction is ambiguous; defaults to COLD
    HeatStream stream = new HeatStream("S1", 100, 100, 10);
    assertEquals(HeatStream.StreamType.COLD, stream.getType());
    assertEquals(0.0, stream.getEnthalpyChange(), 0.01);
  }
}
