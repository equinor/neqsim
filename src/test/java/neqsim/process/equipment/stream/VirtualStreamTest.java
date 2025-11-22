package neqsim.process.equipment.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class VirtualStreamTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;
  double pressure_inlet = 85.0;
  double temperature_inlet = 35.0;
  double gasFlowRate = 5.0;
  ProcessSystem processOps = null;
  VirtualStream virtStream = null;
  Stream inletStream = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  public void setUpBeforeClass() {
    testSystem = new SystemSrkEos(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("ethane", 100.0);
    processOps = new ProcessSystem();
    inletStream = new Stream("inlet stream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");

    virtStream = new VirtualStream("virt stream", inletStream);
    processOps.add(inletStream);
    processOps.add(virtStream);

    processOps.run();
  }

  @Test
  void testRun() {
    virtStream.run();
  }

  @Test
  void testSetComposition() {
    virtStream.setComposition(new double[] {0.6, 0.4}, "molefrac");
    virtStream.run();
    assertEquals(0.6, virtStream.getOutStream().getFluid().getComponent(0).getx(), 1e-6);
  }

  @Test
  void testSetFlowRate() {
    virtStream.setFlowRate(2.0, "MSm3/day");
    virtStream.run();
    assertEquals(2.0, virtStream.getOutStream().getFlowRate("MSm3/day"), 1e-6);
  }

  @Test
  void testSetPressure() {
    virtStream.setPressure(12.0, "bara");
    virtStream.run();
    assertEquals(12.0, virtStream.getOutStream().getPressure("bara"), 1e-6);
  }

  @Test
  void testSetReferenceStream() {
    virtStream.setReferenceStream(inletStream);
  }

  @Test
  void testSetTemperature() {
    virtStream.setTemperature(22.0, "C");
    virtStream.run();
    assertEquals(22.0, virtStream.getOutStream().getTemperature("C"), 1e-6);
  }

  @Test
  void testSolvedFlag() {
    virtStream.run();
    assertTrue(virtStream.solved());
  }
}
