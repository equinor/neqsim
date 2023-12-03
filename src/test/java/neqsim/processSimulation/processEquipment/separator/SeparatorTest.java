package neqsim.processSimulation.processEquipment.separator;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.measurementDevice.LevelTransmitter;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
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
  Separator sep = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  public void setUpBeforeClass() throws Exception {
    testSystem = new SystemSrkCPAstatoil(298.0, 10.0);
    testSystem.addComponent("methane", 100.0);
    testSystem.addComponent("ethane", 10.0);
    testSystem.addComponent("nC10", 10.0);
    testSystem.addComponent("water", 10.0);
    testSystem.setMixingRule(10);
    testSystem.setMultiPhaseCheck(true);

    StreamInterface inletStream = new Stream("inlet stream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");

    sep = new Separator("inlet separator");
    sep.setInletStream(inletStream);

    processOps = new ProcessSystem();
    processOps.add(inletStream);
    processOps.add(sep);
  }

  @Test
  public void testFlow() {
    LevelTransmitter lt = new LevelTransmitter("levelTransmitter", sep);
    Assertions.assertEquals(0.5, lt.getMeasuredValue(), 1e-12);
    ((StreamInterface) processOps.getUnit("inlet stream")).setFlowRate(0.01, "MSm3/day");
    processOps.run();
    Assertions.assertEquals(0.5, lt.getMeasuredValue(), 1e-12);
    Assertions.assertEquals(lt.getMeasuredValue() * 100, lt.getMeasuredPercentValue(), 1e-12);
  }

  @Test
  public void testOnePhase() {
    ((StreamInterface) processOps.getUnit("inlet stream")).setFlowRate(1.0, "MSm3/day");
    ((StreamInterface) processOps.getUnit("inlet stream")).getFluid()
        .setMolarComposition(new double[] {1.0, 0.0, 0.0, 0.0});

    processOps.run();
  }
}
