package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemUMRPRUMCEosNew;

/**
 * Tests for the StreamTransition class which transfers fluid properties between streams with
 * potentially different thermodynamic models.
 */
public class StreamTransitionTest {
  private StreamInterface inletStream;
  private StreamInterface outletStream;
  private StreamTransition streamTransition;
  private SystemInterface inletSystem;
  private SystemInterface outletSystem;

  @BeforeEach
  void setUp() {
    // Create inlet system with methane, ethane and water using SRK EOS model
    inletSystem = new SystemSrkEos(298.15, 5.0);
    inletSystem.addComponent("methane", 2.0);
    inletSystem.addComponent("ethane", 1.0);
    inletSystem.addComponent("water", 0.01);
    inletSystem.setMixingRule(2);
    inletSystem.init(0);

    // Create inlet stream
    inletStream = new Stream("inlet", inletSystem);
    inletStream.run();
  }

  /**
   * Tests stream transition where outlet system uses automatically selected model but contains the
   * same components as the inlet stream.
   */
  @Test
  void testTransitionWithAutoSelectedModel() {
    // Create outlet system with auto-selected model
    outletSystem = inletSystem.clone().autoSelectModel();
    outletSystem.init(0);

    // Create outlet stream
    outletStream = new Stream("outlet", outletSystem);
    outletStream.run();

    // Create and run StreamTransition
    streamTransition = new StreamTransition("test_transition", inletStream, outletStream);
    streamTransition.run();
    streamTransition.run(UUID.randomUUID());

    verifyTransitionResults();
  }

  /**
   * Tests stream transition where outlet system uses a different thermodynamic model (SRK-CPA) and
   * includes a component (TEG) that doesn't exist in the inlet stream. Can be useful when modelling
   * main process with eg. SRK/PR and TEG process with CPA.
   */
  @Test
  void testTransitionWithDifferentModel() {
    // Create outlet system with SRK-CPA model and different component set
    outletSystem = new SystemSrkCPAstatoil(298.15, 5.0);
    outletSystem.addComponent("methane", 0.0);
    outletSystem.addComponent("ethane", 0.0);
    outletSystem.addComponent("water", 0.01);
    outletSystem.addComponent("TEG", 0.0);
    outletSystem.setMixingRule(10);
    outletSystem.init(0);

    // Create outlet stream
    outletStream = new Stream("outlet", outletSystem);
    outletStream.run();

    // Create and run StreamTransition
    streamTransition = new StreamTransition("test_transition", inletStream, outletStream);
    streamTransition.run();
    streamTransition.run(UUID.randomUUID());

    verifyTransitionResults();
  }

  /**
   * Tests stream transition where inlet system contains more components than the outlet system.
   * This verifies that only matching components are transferred correctly.
   */
  @Test
  void testTransitionWithFewerOutletComponents() {
    // Create outlet system with fewer components than inlet
    outletSystem = new SystemUMRPRUMCEosNew(298.15, 5.0);
    outletSystem.addComponent("methane", 1.0);
    outletSystem.addComponent("water", 0.0);
    // Note: ethane is deliberately omitted from outlet system
    outletSystem.setMixingRule(2);
    outletSystem.init(0);

    // Create outlet stream
    outletStream = new Stream("outlet", outletSystem);
    outletStream.run();

    // Create and run StreamTransition
    streamTransition = new StreamTransition("test_transition", inletStream, outletStream);
    streamTransition.run();

    verifyTransitionResults();

    // Additional verification for this specific test case
    SystemInterface outSystem = outletStream.getFluid();

    // Verify that ethane is not present in the outlet system
    assertFalse(outSystem.getPhase(0).hasComponent("ethane"),
        "Ethane should not be present in outlet system");
  }

  /**
   * Helper method to verify that the stream transition correctly transferred properties from inlet
   * to outlet stream.
   */
  private void verifyTransitionResults() {
    // Verify temperature and pressure were transferred
    assertEquals(inletStream.getTemperature(), outletStream.getTemperature(),
        "Temperature should be transferred from inlet to outlet stream");
    assertEquals(inletStream.getPressure(), outletStream.getPressure(),
        "Pressure should be transferred from inlet to outlet stream");

    // Verify components were transferred correctly
    SystemInterface outSystem = outletStream.getFluid();
    SystemInterface inSystem = inletStream.getFluid();

    // Check that component moles match for components present in both systems
    for (int i = 0; i < inSystem.getNumberOfComponents(); i++) {
      String componentName = inSystem.getComponent(i).getName();
      if (outSystem.getPhase(0).hasComponent(componentName)) {
        assertEquals(inSystem.getComponent(i).getNumberOfmoles(),
            outSystem.getComponent(componentName).getNumberOfmoles(),
            "Moles of " + componentName + " should match between systems");
      }
    }
  }
}
