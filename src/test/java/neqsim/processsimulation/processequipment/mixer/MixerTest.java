package neqsim.processsimulation.processequipment.mixer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.processsimulation.processequipment.stream.Stream;
import neqsim.processsimulation.processsystem.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

/**
 * @author ESOL
 */
class MixerTest {
  static neqsim.thermo.system.SystemInterface testSystem;
  static neqsim.thermo.system.SystemInterface waterSystem;
  static Stream gasStream;
  static Stream waterStream;

  /**
   * @throws java.lang.Exception
   */
  @BeforeAll
  static void setUpBeforeClass() throws Exception {
    testSystem = new SystemSrkEos(298.15, 1.0);
    testSystem.addComponent("water", 0.0);
    testSystem.addComponent("methane", 1.0);
    testSystem.addComponent("ethane", 1.0);
    testSystem.addComponent("nC10", 0.1);
    testSystem.addTBPfraction("C10", 0.01, 0.366, 0.94);
    testSystem.setMixingRule(2);
    testSystem.setMultiPhaseCheck(true);

    waterSystem = testSystem.clone();
    waterSystem.setMolarComposition(new double[] {1.0, 0.0, 0.0, 0.0, 0.0});

    gasStream = new Stream("turbine stream", testSystem);
    gasStream.setFlowRate(1.0, "MSm3/day");
    gasStream.setTemperature(50.0, "C");
    gasStream.setPressure(2.0, "bara");
    gasStream.run();
    waterStream = new Stream("water stream", waterSystem);
    waterStream.setFlowRate(100000., "kg/day");
    waterStream.setTemperature(50.0, "C");
    waterStream.setPressure(2.0, "bara");
    waterStream.run();
  }

  /**
   * Test method for {@link neqsim.processsimulation.processequipment.mixer.Mixer#run()}.
   */
  @Test
  void testRun() {
    Mixer testMixer = new Mixer("test mixer");
    testMixer.addStream(gasStream);
    testMixer.addStream(waterStream);
    testMixer.run();
    assertEquals(testMixer.getOutletStream().getFluid().getEnthalpy("kJ/kg"), -177.27666625251516,
        1e-1);
  }

  /**
   * Test method for {@link neqsim.processsimulation.processequipment.mixer.Mixer#run()}.
   */
  @Test
  void testNeedRecalculation() {
    Mixer testMixer = new Mixer("test mixer");
    testMixer.addStream(gasStream);
    testMixer.addStream(waterStream);
    testMixer.run();
    ProcessSystem processOps = new ProcessSystem();
    processOps.add(gasStream);
    processOps.add(waterStream);
    processOps.add(testMixer);
    processOps.run();
    assertFalse(gasStream.needRecalculation());
    assertFalse(waterStream.needRecalculation());
    assertFalse(testMixer.needRecalculation());
    gasStream.setFlowRate(100.1, "kg/hr");
    assertTrue(gasStream.needRecalculation());
    assertTrue(testMixer.needRecalculation());
    processOps.run();
    assertFalse(gasStream.needRecalculation());
    assertFalse(testMixer.needRecalculation());
  }
}
