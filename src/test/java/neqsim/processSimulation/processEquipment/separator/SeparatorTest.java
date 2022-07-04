package neqsim.processSimulation.processEquipment.separator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    processOps = new ProcessSystem();

    StreamInterface inletStream = new Stream("inlet stream", testSystem);
    inletStream.setPressure(pressure_inlet, "bara");
    inletStream.setTemperature(temperature_inlet, "C");
    inletStream.setFlowRate(gasFlowRate, "MSm3/day");

    Separator sep = new Separator("inlet separator");
    sep.setInletStream(inletStream);

    StreamInterface gasFromSep = new Stream("liquid from separator", sep.getLiquidOutStream());
    StreamInterface liqFromSep = new Stream("liquid from separator", sep.getLiquidOutStream());

    processOps.add(inletStream);
    processOps.add(sep);
    // processOps.add(gasFromSep);
    // processOps.add(liqFromSep);

    // processOps.run();
  }

  @Test
  public void testFlow() {
    ((StreamInterface) processOps.getUnit("inlet stream")).setFlowRate(0.01, "MSm3/day");
    processOps.run();
  }

  @Test
  public void testOnePhase() {
    ((StreamInterface) processOps.getUnit("inlet stream")).setFlowRate(1.0, "MSm3/day");
    ((StreamInterface) processOps.getUnit("inlet stream")).getFluid()
        .setMolarComposition(new double[] {1.0, 0.0, 0.0, 0.0});

    processOps.run();
  }
}
