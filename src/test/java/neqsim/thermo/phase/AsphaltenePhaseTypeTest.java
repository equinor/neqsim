package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Tests demonstrating asphaltene phase type in multi-phase flash calculations.
 */
public class AsphaltenePhaseTypeTest {
  /**
   * Test that PhaseType.ASPHALTENE exists and has correct properties.
   */
  @Test
  @DisplayName("PhaseType.ASPHALTENE enum validation")
  void testAsphaltenePhaseTypeEnum() {
    assertEquals("asphaltene", PhaseType.ASPHALTENE.getDesc());
    assertEquals(PhaseType.ASPHALTENE, PhaseType.byDesc("asphaltene"));
    assertEquals(PhaseType.ASPHALTENE, PhaseType.byName("ASPHALTENE"));

    // Asphaltene maps to SOLID state of matter
    assertEquals(StateOfMatter.SOLID, StateOfMatter.fromPhaseType(PhaseType.ASPHALTENE));
    assertTrue(StateOfMatter.isSolid(PhaseType.ASPHALTENE));
  }

  /**
   * Test TPflash with CPA system containing asphaltene that can precipitate. This demonstrates a
   * three-phase equilibrium: gas + oil + asphaltene (solid).
   */
  @Test
  @DisplayName("CPA TPflash with gas-oil-asphaltene three-phase equilibrium")
  void testThreePhaseAsphalteneFlash() {
    // Create CPA system - good for modeling associating compounds like asphaltene
    SystemInterface fluid = new SystemSrkCPAstatoil(350.0, 50.0); // Low pressure to get gas

    // Light components (will form gas phase)
    fluid.addComponent("methane", 0.50);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);

    // Heavy components (will form oil phase)
    fluid.addComponent("n-heptane", 0.25);
    fluid.addComponent("nC10", 0.07);

    // Asphaltene component (can precipitate as solid)
    fluid.addComponent("asphaltene", 0.05);

    fluid.setMixingRule("classic");

    // Enable solid phase check for asphaltene - this allows it to precipitate
    fluid.setSolidPhaseCheck("asphaltene");

    // Initialize the system
    fluid.init(0);
    fluid.init(1);

    // Perform TPflash with solid phase check
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    // Print results
    System.out.println("\n=== Three-Phase Asphaltene Flash Results ===");
    System.out.println("Temperature: " + (fluid.getTemperature() - 273.15) + " °C");
    System.out.println("Pressure: " + fluid.getPressure() + " bar");
    System.out.println("Number of phases: " + fluid.getNumberOfPhases());

    // Print phase information and update solid phase to ASPHALTENE if applicable
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      PhaseInterface phase = fluid.getPhase(i);

      // Check if this solid phase is predominantly asphaltene
      if (phase instanceof PhaseSolid) {
        PhaseSolid solidPhase = (PhaseSolid) phase;
        if (solidPhase.isAsphaltenePhase()) {
          solidPhase.updatePhaseTypeForAsphaltene();
        }
      }

      System.out.println("\nPhase " + i + ":");
      System.out.println("  Type: " + phase.getType());
      System.out.println("  Type desc: " + phase.getType().getDesc());
      System.out.println("  Beta (mole frac): " + phase.getBeta());
      System.out.println("  Molar mass: " + phase.getMolarMass() * 1000 + " g/mol");
    }

    // Check if we have an asphaltene phase now
    boolean hasAsphaltenePhase = false;
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      if (fluid.getPhase(i).getType() == PhaseType.ASPHALTENE) {
        hasAsphaltenePhase = true;
        break;
      }
    }

    System.out.println("\nHas ASPHALTENE phase type: " + hasAsphaltenePhase);

    fluid.prettyPrint();

    // At low pressure with light gas + heavy oil + asphaltene, we expect multiple phases
    assertTrue(fluid.getNumberOfPhases() >= 2, "Should have at least 2 phases at these conditions");
  }

  /**
   * Test setting phase type to ASPHALTENE directly.
   */
  @Test
  @DisplayName("Set phase type to ASPHALTENE")
  void testSetAsphaltenePhaseType() {
    SystemInterface fluid = new SystemSrkEos(373.15, 100.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("n-heptane", 0.5);
    fluid.setMixingRule("classic");
    fluid.init(0);
    fluid.init(1);

    // Get a phase and set its type to ASPHALTENE
    PhaseInterface phase = fluid.getPhase(0);
    phase.setType(PhaseType.ASPHALTENE);

    assertEquals(PhaseType.ASPHALTENE, phase.getType());
    assertEquals("asphaltene", phase.getType().getDesc());
  }

  /**
   * Test checking for asphaltene phase presence using hasPhaseType.
   */
  @Test
  @DisplayName("Check hasPhaseType for asphaltene")
  void testHasPhaseTypeAsphaltene() {
    SystemInterface fluid = new SystemSrkEos(373.15, 100.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("n-heptane", 0.5);
    fluid.setMixingRule("classic");
    fluid.init(0);
    fluid.init(1);

    // Initially, there should be no asphaltene phase
    // (This tests that the method exists and can be called)
    boolean hasAspPhase = fluid.hasPhaseType(PhaseType.ASPHALTENE);
    System.out.println("Has asphaltene phase: " + hasAspPhase);

    // Also test string-based lookup
    boolean hasAspByString = fluid.hasPhaseType("asphaltene");
    System.out.println("Has asphaltene phase (by string): " + hasAspByString);
  }

  /**
   * Test TPflash with CPA system for two-phase oil-asphaltene equilibrium. At high pressure (no
   * gas), we expect oil + asphaltene phases.
   */
  @Test
  @DisplayName("CPA TPflash with oil-asphaltene two-phase equilibrium")
  void testTwoPhaseOilAsphalteneFlash() {
    // Create CPA system at high pressure to suppress gas phase
    SystemInterface fluid = new SystemSrkCPAstatoil(350.0, 200.0); // High pressure

    // Heavy components only (will form single oil phase)
    fluid.addComponent("n-heptane", 0.45);
    fluid.addComponent("nC10", 0.30);
    fluid.addComponent("nC20", 0.15);

    // Asphaltene component (can precipitate as solid)
    fluid.addComponent("asphaltene", 0.10);

    fluid.setMixingRule("classic");

    // Enable solid phase check for asphaltene
    fluid.setSolidPhaseCheck("asphaltene");

    // Initialize the system
    fluid.init(0);
    fluid.init(1);

    // Perform TPflash with solid phase check
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    // Print results
    System.out.println("\n=== Two-Phase Oil-Asphaltene Flash Results ===");
    System.out.println("Temperature: " + (fluid.getTemperature() - 273.15) + " °C");
    System.out.println("Pressure: " + fluid.getPressure() + " bar");
    System.out.println("Number of phases: " + fluid.getNumberOfPhases());

    // Print phase information
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      PhaseInterface phase = fluid.getPhase(i);
      System.out.println("\nPhase " + i + ":");
      System.out.println("  Type: " + phase.getType());
      System.out.println("  Type desc: " + phase.getType().getDesc());
      System.out.println("  Beta (mole frac): " + phase.getBeta());
      System.out.println("  Molar mass: " + phase.getMolarMass() * 1000 + " g/mol");
    }

    // Check phase types
    boolean hasOilPhase = fluid.hasPhaseType(PhaseType.OIL) || fluid.hasPhaseType(PhaseType.LIQUID);
    boolean hasAsphaltenePhase = fluid.hasPhaseType(PhaseType.ASPHALTENE);
    boolean hasGasPhase = fluid.hasPhaseType(PhaseType.GAS);

    System.out.println("\nHas OIL/LIQUID phase: " + hasOilPhase);
    System.out.println("Has ASPHALTENE phase: " + hasAsphaltenePhase);
    System.out.println("Has GAS phase: " + hasGasPhase);

    fluid.prettyPrint();

    // At high pressure with heavy oil + asphaltene, we expect oil + asphaltene (no gas)
    assertTrue(fluid.getNumberOfPhases() >= 2, "Should have at least 2 phases");
    assertTrue(hasOilPhase, "Should have an oil/liquid phase at high pressure");

    // If asphaltene precipitated, it should be labeled as ASPHALTENE not SOLID
    if (fluid.hasPhaseType(PhaseType.SOLID) || hasAsphaltenePhase) {
      // The solid phase should be typed as ASPHALTENE
      assertTrue(hasAsphaltenePhase, "Solid phase should be typed as ASPHALTENE");
    }
  }

  /**
   * Test the useEosProperties flag on PhaseSolid for asphaltene phases.
   */
  @Test
  @DisplayName("Test EOS properties flag for solid asphaltene phases")
  void testUseEosPropertiesFlag() {
    // Create CPA system at high pressure to suppress gas phase
    SystemInterface fluid = new SystemSrkCPAstatoil(350.0, 200.0);

    fluid.addComponent("n-heptane", 0.45);
    fluid.addComponent("nC10", 0.30);
    fluid.addComponent("nC20", 0.15);
    fluid.addComponent("asphaltene", 0.10);

    fluid.setMixingRule("classic");
    fluid.setSolidPhaseCheck("asphaltene");
    fluid.init(0);
    fluid.init(1);

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();

    // Find the asphaltene phase
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      PhaseInterface phase = fluid.getPhase(i);
      if (phase instanceof PhaseSolid) {
        PhaseSolid solidPhase = (PhaseSolid) phase;
        if (solidPhase.isAsphaltenePhase()) {
          // Get default (literature-based) density
          double litDensity = solidPhase.getDensity("kg/m3");
          System.out.println("Literature-based density: " + litDensity + " kg/m3");

          // By default, useEosProperties should be false
          assertFalse(solidPhase.isUseEosProperties(),
              "Default should be literature-based properties");

          // Enable EOS properties
          solidPhase.setUseEosProperties(true);
          assertTrue(solidPhase.isUseEosProperties());

          // The density should now use EOS calculation
          double eosDensity = solidPhase.getDensity("kg/m3");
          System.out.println("EOS-based density: " + eosDensity + " kg/m3");

          // EOS density may differ from literature value
          // (Just verify it returns a valid value)
          assertTrue(eosDensity > 0, "EOS density should be positive");
          assertFalse(Double.isNaN(eosDensity), "EOS density should not be NaN");

          // Restore default behavior
          solidPhase.setUseEosProperties(false);
          double restoredDensity = solidPhase.getDensity("kg/m3");
          assertEquals(litDensity, restoredDensity, 0.1,
              "Should return to literature-based density");
        }
      }
    }
  }

  /**
   * Test PhaseType.LIQUID_ASPHALTENE for Pedersen's liquid-liquid approach.
   */
  @Test
  @DisplayName("Test LIQUID_ASPHALTENE phase type")
  void testLiquidAsphaltenePhaseType() {
    // Verify LIQUID_ASPHALTENE enum exists and has correct properties
    assertEquals("asphaltene liquid", PhaseType.LIQUID_ASPHALTENE.getDesc());
    assertEquals(PhaseType.LIQUID_ASPHALTENE, PhaseType.byDesc("asphaltene liquid"));

    // LIQUID_ASPHALTENE should be a liquid state of matter
    assertTrue(StateOfMatter.isLiquid(PhaseType.LIQUID_ASPHALTENE),
        "LIQUID_ASPHALTENE should be liquid");
    assertFalse(StateOfMatter.isSolid(PhaseType.LIQUID_ASPHALTENE),
        "LIQUID_ASPHALTENE should not be solid");

    // Both ASPHALTENE and LIQUID_ASPHALTENE should be recognized by isAsphaltene
    assertTrue(StateOfMatter.isAsphaltene(PhaseType.ASPHALTENE));
    assertTrue(StateOfMatter.isAsphaltene(PhaseType.LIQUID_ASPHALTENE));
  }

  /**
   * Test isAsphalteneRich method for liquid phases.
   */
  @Test
  @DisplayName("Test isAsphalteneRich for liquid phases")
  void testIsAsphalteneRichForLiquidPhases() {
    // Create system with asphaltene component
    SystemInterface fluid = new SystemSrkCPAstatoil(350.0, 200.0);

    fluid.addComponent("n-heptane", 0.45);
    fluid.addComponent("nC10", 0.30);
    fluid.addComponent("asphaltene", 0.25); // High asphaltene fraction

    fluid.setMixingRule("classic");
    fluid.init(0);
    fluid.init(1);

    // Check the phase
    PhaseInterface phase = fluid.getPhase(0);

    // If asphaltene fraction > 50%, it should be asphaltene-rich
    System.out.println("Phase type: " + phase.getType());
    System.out.println("Is asphaltene-rich: " + phase.isAsphalteneRich());

    // The method should exist and return a boolean
    boolean isRich = phase.isAsphalteneRich();
    // With 25% asphaltene, it should NOT be asphaltene-rich (need > 50%)
    assertFalse(isRich, "Phase with 25% asphaltene should not be asphaltene-rich");

    // Create system with > 50% asphaltene
    SystemInterface fluid2 = new SystemSrkCPAstatoil(350.0, 200.0);
    fluid2.addComponent("n-heptane", 0.2);
    fluid2.addComponent("asphaltene", 0.8); // 80% asphaltene
    fluid2.setMixingRule("classic");
    fluid2.init(0);
    fluid2.init(1);

    PhaseInterface phase2 = fluid2.getPhase(0);
    System.out.println("Phase2 type: " + phase2.getType());
    System.out.println("Phase2 is asphaltene-rich: " + phase2.isAsphalteneRich());

    // With 80% asphaltene, it should be asphaltene-rich
    assertTrue(phase2.isAsphalteneRich(), "Phase with 80% asphaltene should be asphaltene-rich");
  }
}
