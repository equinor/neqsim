package neqsim.processsimulation.processequipment.absorber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import neqsim.processsimulation.processequipment.stream.Stream;

public class SimpleAbsorberTest extends neqsim.NeqSimTest {
  neqsim.thermo.system.SystemFurstElectrolyteEos testSystem;

  @BeforeEach
  void setUp() {
    testSystem = new neqsim.thermo.system.SystemFurstElectrolyteEos((273.15 + 80.0), 50.00);
    testSystem.addComponent("methane", 120.00);
    testSystem.addComponent("CO2", 20.0);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(4);
  }

  @Disabled("Disabled until neqsim.processSimulation.processEquipment.adsorber.SimpleAdsorber is fixed")
  @Test
  void testRun() {
    Stream stream_Hot = new Stream("Stream1", testSystem);
    neqsim.processsimulation.processequipment.absorber.SimpleAbsorber absorber1 =
        new neqsim.processsimulation.processequipment.absorber.SimpleAbsorber("absorber",
            stream_Hot);
    absorber1.setAproachToEquilibrium(0.75);

    // TODO: Test is not well behaved
    /*
     * neqsim.processSimulation.processSystem.ProcessSystem operations = new
     * neqsim.processSimulation.processSystem.ProcessSystem(); operations.add(stream_Hot);
     * operations.add(absorber1);
     *
     * operations.run();
     */
    // operations.displayResult();
  }
}
