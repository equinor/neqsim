package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseGERG2008Eos;
import neqsim.thermo.util.gerg.GERG2008Type;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests for SystemGERG2008Eos with GERG-2008-H2 model selection.
 */
class SystemGERG2008EosH2Test {

  /**
   * Test that the default model type is STANDARD.
   */
  @Test
  void testDefaultModelType() {
    SystemGERG2008Eos system = new SystemGERG2008Eos(298.15, 10.0);
    assertEquals(GERG2008Type.STANDARD, system.getGergModelType());
    assertFalse(system.isUsingHydrogenEnhancedModel());
  }

  /**
   * Test setting the model type to HYDROGEN_ENHANCED.
   */
  @Test
  void testSetHydrogenEnhancedModel() {
    SystemGERG2008Eos system = new SystemGERG2008Eos(298.15, 10.0);
    system.setGergModelType(GERG2008Type.HYDROGEN_ENHANCED);
    assertEquals(GERG2008Type.HYDROGEN_ENHANCED, system.getGergModelType());
    assertTrue(system.isUsingHydrogenEnhancedModel());
    assertEquals("GERG2008-H2-EOS", system.getModelName());
  }

  /**
   * Test the convenience method useHydrogenEnhancedModel().
   */
  @Test
  void testUseHydrogenEnhancedModel() {
    SystemGERG2008Eos system = new SystemGERG2008Eos(298.15, 10.0);
    system.useHydrogenEnhancedModel();
    assertTrue(system.isUsingHydrogenEnhancedModel());
  }

  /**
   * Test that phases inherit the model type.
   */
  @Test
  void testPhasesInheritModelType() {
    SystemGERG2008Eos system = new SystemGERG2008Eos(298.15, 10.0);
    system.addComponent("methane", 0.8);
    system.addComponent("hydrogen", 0.2);
    system.setGergModelType(GERG2008Type.HYDROGEN_ENHANCED);

    // Check that phases have the correct model type
    for (int i = 0; i < system.getNumberOfPhases(); i++) {
      if (system.getPhase(i) instanceof PhaseGERG2008Eos) {
        PhaseGERG2008Eos phase = (PhaseGERG2008Eos) system.getPhase(i);
        assertEquals(GERG2008Type.HYDROGEN_ENHANCED, phase.getGergModelType());
      }
    }
  }

  /**
   * Test that GERG-2008 and GERG-2008-H2 give different results for hydrogen mixtures.
   */
  @Test
  void testH2ModelGivesDifferentResults() {
    // Create system with standard GERG-2008
    SystemGERG2008Eos systemStandard = new SystemGERG2008Eos(300.0, 50.0);
    systemStandard.addComponent("methane", 0.7);
    systemStandard.addComponent("hydrogen", 0.3);
    ThermodynamicOperations opsStandard = new ThermodynamicOperations(systemStandard);
    opsStandard.TPflash();
    double densityStandard = systemStandard.getPhase(0).getDensity();

    // Create system with GERG-2008-H2
    SystemGERG2008Eos systemH2 = new SystemGERG2008Eos(300.0, 50.0);
    systemH2.addComponent("methane", 0.7);
    systemH2.addComponent("hydrogen", 0.3);
    systemH2.useHydrogenEnhancedModel();
    ThermodynamicOperations opsH2 = new ThermodynamicOperations(systemH2);
    opsH2.TPflash();
    double densityH2 = systemH2.getPhase(0).getDensity();

    // The densities should be different (GERG-2008-H2 has updated parameters)
    // The difference should be small but measurable
    double relativeDifference = Math.abs(densityStandard - densityH2) / densityStandard * 100;
    assertTrue(relativeDifference > 0.01, "Density difference should be measurable");
    assertTrue(relativeDifference < 5.0, "Density difference should be within reasonable range");

    System.out.println("GERG-2008 density: " + densityStandard + " kg/m3");
    System.out.println("GERG-2008-H2 density: " + densityH2 + " kg/m3");
    System.out.println("Relative difference: " + relativeDifference + "%");
  }

  /**
   * Test CO2-H2 mixture where differences are most pronounced.
   */
  @Test
  void testCO2H2MixtureDifferences() {
    // CO2-H2 has a new departure function in GERG-2008-H2, so differences are larger
    double temperature = 350.0; // K
    double pressure = 100.0; // bar

    // Standard GERG-2008
    SystemGERG2008Eos systemStandard = new SystemGERG2008Eos(temperature, pressure);
    systemStandard.addComponent("CO2", 0.5);
    systemStandard.addComponent("hydrogen", 0.5);
    ThermodynamicOperations opsStandard = new ThermodynamicOperations(systemStandard);
    opsStandard.TPflash();

    // GERG-2008-H2
    SystemGERG2008Eos systemH2 = new SystemGERG2008Eos(temperature, pressure);
    systemH2.addComponent("CO2", 0.5);
    systemH2.addComponent("hydrogen", 0.5);
    systemH2.useHydrogenEnhancedModel();
    ThermodynamicOperations opsH2 = new ThermodynamicOperations(systemH2);
    opsH2.TPflash();

    double densityStandard = systemStandard.getPhase(0).getDensity();
    double densityH2 = systemH2.getPhase(0).getDensity();

    // CO2-H2 should show larger differences due to new departure function
    double relativeDifference = Math.abs(densityStandard - densityH2) / densityStandard * 100;
    assertTrue(relativeDifference > 0.1, "CO2-H2 should show significant differences");

    System.out.println("CO2-H2 Mixture at " + temperature + " K, " + pressure + " bar:");
    System.out.println("  GERG-2008 density: " + densityStandard + " kg/m3");
    System.out.println("  GERG-2008-H2 density: " + densityH2 + " kg/m3");
    System.out.println("  Relative difference: " + relativeDifference + "%");
  }

  /**
   * Test cloning preserves the model type.
   */
  @Test
  void testClonePreservesModelType() {
    SystemGERG2008Eos system = new SystemGERG2008Eos(298.15, 10.0);
    system.addComponent("methane", 0.8);
    system.addComponent("hydrogen", 0.2);
    system.useHydrogenEnhancedModel();

    SystemGERG2008Eos clonedSystem = system.clone();
    assertEquals(GERG2008Type.HYDROGEN_ENHANCED, clonedSystem.getGergModelType());
    assertTrue(clonedSystem.isUsingHydrogenEnhancedModel());
  }
}
