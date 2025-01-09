package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class StreamSaturatorUtilTest extends neqsim.NeqSimTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;
  double pressure_inlet = 85.0;
  double temperature_inlet = 35.0;
  double gasFlowRate = 5.0;
  ProcessSystem processOps = null;

  @Test
  void testRun() {
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("water", 1.0);

    Stream inletStream = new Stream("inlet stream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");
    StreamSaturatorUtil streamSaturator = new StreamSaturatorUtil("saturator", inletStream);

    processOps = new ProcessSystem();
    processOps.add(inletStream);
    processOps.add(streamSaturator);
    processOps.run();

    assertEquals(0.0012319218375683974,
        streamSaturator.getOutletStream().getFluid().getPhase(0).getComponent("water").getx(),
        1e-8);
  }

  @Test
  void testSetApprachToSaturation() {
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("water", 1.0);

    Stream inletStream = new Stream("inlet stream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");
    StreamSaturatorUtil streamSaturator = new StreamSaturatorUtil("saturator", inletStream);
    streamSaturator.setApprachToSaturation(0.93);

    processOps = new ProcessSystem();
    processOps.add(inletStream);
    processOps.add(streamSaturator);
    processOps.run();

    assertEquals(0.0012319218375683974 * 0.93,
        streamSaturator.getOutletStream().getFluid().getPhase(0).getComponent("water").getx(),
        1e-3);
  }

  @Test
  void testNeedRecalculation() {
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("water", 1.0);

    Stream inletStream = new Stream("inlet stream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");
    StreamSaturatorUtil streamSaturator = new StreamSaturatorUtil("saturator", inletStream);

    processOps = new ProcessSystem();
    processOps.add(inletStream);
    processOps.add(streamSaturator);
    processOps.run();

    ((Stream) processOps.getUnit("inlet stream")).setTemperature(298.1, "K");
    assertTrue(((StreamSaturatorUtil) processOps.getUnit("saturator")).needRecalculation());
    processOps.run();
    assertFalse(((StreamSaturatorUtil) processOps.getUnit("saturator")).needRecalculation());
  }
}
