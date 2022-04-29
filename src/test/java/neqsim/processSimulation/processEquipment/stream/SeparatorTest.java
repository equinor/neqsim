package neqsim.processSimulation.processEquipment.stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processSystem.ProcessSystem;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * @author ESOL
 *
 */
class SeparatorTest extends neqsim.NeqSimTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;
  double pressure_inlet = 55.0;
  double temperature_inlet = 35.0;
  double gasFlowRate = 5.0;
  ProcessSystem processOps = null;
  Stream inletStream = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  public void setUpBeforeClass() throws Exception {
    testSystem = new SystemSrkCPAstatoil(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("ethane", 10.0);
    testSystem.addComponent("n-decane", 10.0);
    testSystem.addComponent("water", 10.0);
    testSystem.setMixingRule(10);
    testSystem.setMultiPhaseCheck(true);

    processOps = new ProcessSystem();

    inletStream = new Stream("inletStream", testSystem);
    inletStream.setName("inlet stream");
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");

    Separator sep = new Separator("inlet separator");
    sep.setInletStream(inletStream);

    processOps.add(inletStream);
    processOps.add(sep);
    processOps.run();
  }



  @Test
  public void testNoFlow() {
    ((StreamInterface) processOps.getUnit("inletStream")).setFlowRate(0, "MSm3/day");
    processOps.run();
  }
}
