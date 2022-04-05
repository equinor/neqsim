/**
 * 
 */
package neqsim.processSimulation.processEquipment.mixer;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * @author ESOL
 *
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
    
    waterSystem =testSystem.clone();
    waterSystem.setMolarComposition(new double[]{1.0, 0.0, 0.0,0.0, 0.0});

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
   * Test method for {@link neqsim.processSimulation.processEquipment.mixer.Mixer#run()}.
   */
  @Test
  void testRun() {
    
    Mixer testMixer = new Mixer("test mixer");
    testMixer.addStream(gasStream);
    testMixer.addStream(waterStream);
    testMixer.run();
    assertEquals(testMixer.getOutStream().getFluid().getEnthalpy("kJ/kg"),-177.27666625251516, 1e-3);
  }

}
