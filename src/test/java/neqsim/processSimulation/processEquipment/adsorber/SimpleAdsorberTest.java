package neqsim.processSimulation.processEquipment.adsorber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.stream.Stream;

public class SimpleAdsorberTest {
  neqsim.thermo.system.SystemFurstElectrolyteEos testSystem;

  @BeforeEach
  void setUp() {
    testSystem = new neqsim.thermo.system.SystemFurstElectrolyteEos((273.15 + 80.0), 50.00);
    testSystem.addComponent("methane", 120.00);
    testSystem.addComponent("CO2", 20.0);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(4);
  }

  @Test
  void testRun() {
    Stream stream_Hot = new Stream("Stream1", testSystem);
    neqsim.processSimulation.processEquipment.adsorber.SimpleAdsorber adsorber1 =
        new neqsim.processSimulation.processEquipment.adsorber.SimpleAdsorber("adsorber",
            stream_Hot);
    adsorber1.setAproachToEquilibrium(0.75);

    // todo: Test is not well behaved
    /*
     * neqsim.processSimulation.processSystem.ProcessSystem operations = new
     * neqsim.processSimulation.processSystem.ProcessSystem(); operations.add(stream_Hot);
     * operations.add(adsorber1);
     * 
     * operations.run();
     */
    // operations.displayResult();
  }
}
