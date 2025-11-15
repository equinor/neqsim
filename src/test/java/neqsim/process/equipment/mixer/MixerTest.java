package neqsim.process.equipment.mixer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
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
  static void setUpBeforeClass() {
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
   * Test method for {@link neqsim.process.equipment.mixer.Mixer#run()}.
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
   * Test method for {@link neqsim.process.equipment.mixer.Mixer#run()}.
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
    gasStream.setFlowRate(100.1, "kg/hr");
    assertTrue(gasStream.needRecalculation());
    processOps.run();
    assertFalse(gasStream.needRecalculation());
  }

  /**
   * Test method for {@link neqsim.process.equipment.mixer.Mixer#run()}.
   */
  @Test
  void testRunDifferentPressures() {
    StreamInterface gasStream2 = (StreamInterface) gasStream.clone();
    StreamInterface waterStream2 = (StreamInterface) waterStream.clone();

    gasStream2.setPressure(10.0, "bara");
    waterStream2.setPressure(30.0, "bara");

    gasStream2.run();
    waterStream2.run();

    double totalEnthalpy =
        gasStream2.getFluid().getEnthalpy("J") + waterStream2.getFluid().getEnthalpy("J");

    Mixer testMixer = new Mixer("test mixer");
    testMixer.addStream(waterStream2);
    testMixer.addStream(gasStream2);
    testMixer.run();

    assertEquals(totalEnthalpy, testMixer.getOutletStream().getFluid().getEnthalpy("J"), 1e-1);
    assertEquals(10.0, testMixer.getOutletStream().getPressure("bara"), 1e-1);
  }

  /**
   * Test method for mass balance conservation in Mixer.
   */
  @Test
  void testMassBalanceConservation() {
    Mixer testMixer = new Mixer("test mixer");
    testMixer.addStream(gasStream);
    testMixer.addStream(waterStream);
    testMixer.run();

    // Mass balance should be approximately zero (outlet flow - inlet flow)
    // getMassBalance() now only counts streams with flow > minimumFlow()
    double massBalance = testMixer.getMassBalance("kg/hr");
    assertEquals(0.0, massBalance, 1e-6,
        "Mixer mass balance error: outlet flow should equal sum of inlet flows");
  }
}
