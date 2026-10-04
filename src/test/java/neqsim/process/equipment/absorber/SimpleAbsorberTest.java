package neqsim.process.equipment.absorber;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;

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
    neqsim.process.equipment.absorber.SimpleAbsorber absorber1 = new neqsim.process.equipment.absorber.SimpleAbsorber(
        "absorber", stream_Hot);
    absorber1.setAproachToEquilibrium(0.75);

    // TODO: Test is not well behaved
    /*
     * neqsim.processSimulation.processSystem.ProcessSystem operations = new
     * neqsim.processSimulation.processSystem.ProcessSystem(); operations.add(stream_Hot); operations.add(absorber1);
     *
     * operations.run();
     */
    // operations.displayResult();
  }

  @Test
  void testRunDiagnosticsAreExplicitBeforeCalculation() {
    SimpleAbsorber absorber = new SimpleAbsorber("diagnostic absorber");

    assertFalse(absorber.isLastRunConverged());
    assertEquals(0, absorber.getLastIterationCount());
    assertTrue(Double.isNaN(absorber.getLastConvergenceError()));
    assertEquals("NOT_RUN", absorber.getLastRunExitReason());
  }

  @Test
  void testSetNameBeforeStreamsAreInitialized() {
    SimpleTEGAbsorber absorber = new SimpleTEGAbsorber("TEG absorber");

    assertDoesNotThrow(() -> absorber.setName("renamed absorber"));
    assertEquals("renamed absorber", absorber.getName());
  }
}
