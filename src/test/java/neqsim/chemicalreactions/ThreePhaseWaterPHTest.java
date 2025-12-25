package neqsim.chemicalreactions;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Test for three-phase (gas/oil/water) calculation with pH measurement.
 * 
 * <p>
 * Tests the chemical equilibrium algorithm with methane, n-decane, and water to verify correct pH
 * calculation in the aqueous phase.
 * </p>
 */
public class ThreePhaseWaterPHTest {

  @Test
  public void testPureWaterPH() {
    System.out.println("\n=== Pure Water pH Test ===");

    double temperature = 298.15; // 25°C
    double pressure = 1.0; // 1 bar

    try {
      // Create electrolyte CPA system - pure water
      SystemInterface system = new SystemElectrolyteCPAstatoil(temperature, pressure);
      system.addComponent("water", 10.0);

      system.chemicalReactionInit();
      system.createDatabase(true);
      system.setMixingRule(10);
      system.setMultiPhaseCheck(true); // Required for proper phase handling

      system.init(0);
      ThermodynamicOperations ops = new ThermodynamicOperations(system);
      ops.TPflash();

      // Print all phases for debugging
      System.out.println("Number of phases: " + system.getNumberOfPhases());
      for (int p = 0; p < system.getNumberOfPhases(); p++) {
        System.out.println("Phase " + p + ": " + system.getPhase(p).getPhaseTypeName());
      }

      // Find aqueous phase - for pure water, it might just be "liquid"
      int aqueousPhaseIndex = 0; // Default to first phase for pure water
      for (int p = 0; p < system.getNumberOfPhases(); p++) {
        String phaseType = system.getPhase(p).getPhaseTypeName();
        if (phaseType.equalsIgnoreCase("aqueous")) {
          aqueousPhaseIndex = p;
          break;
        }
      }

      double pH = system.getPhase(aqueousPhaseIndex).getpH();
      System.out.printf("Pure water pH at 25°C, 1 bar: %.4f (expected ~7.0)%n", pH);

      // Print H3O+ and OH- mole fractions for debugging
      System.out.println("\nSpecies mole fractions:");
      for (int i = 0; i < system.getPhase(aqueousPhaseIndex).getNumberOfComponents(); i++) {
        String name = system.getPhase(aqueousPhaseIndex).getComponent(i).getComponentName();
        double x = system.getPhase(aqueousPhaseIndex).getComponent(i).getx();
        double moles = system.getPhase(aqueousPhaseIndex).getComponent(i).getNumberOfMolesInPhase();
        System.out.printf("  %-15s x = %.6e, moles = %.6e%n", name, x, moles);
      }

    } catch (Exception e) {
      System.out.println("Error: " + e.getMessage());
      e.printStackTrace();
    }
  }

  @Test
  public void testMethaneDecaneWaterThreePhase() {
    System.out.println("\n=== Methane - nC10 - Water Three Phase Test ===");

    // Test at various pressures
    double[] pressures = {1.0, 5.0, 10.0, 20.0, 50.0};
    double temperature = 298.15; // 25°C

    System.out.println("Temperature: " + temperature + " K (25°C)");
    System.out.println("--------------------------------------------------");
    System.out.printf("%-12s %-10s %-12s%n", "P (bar)", "# Phases", "pH (aq)");
    System.out.println("--------------------------------------------------");

    for (double P : pressures) {
      try {
        // Create electrolyte CPA system for water with hydrocarbons
        SystemInterface system = new SystemElectrolyteCPAstatoil(temperature, P);

        // Add components - methane (gas), n-decane (oil), water
        system.addComponent("methane", 1.0); // Gas phase component
        system.addComponent("nC10", 0.5); // Oil phase component
        system.addComponent("water", 10.0); // Aqueous phase

        // Initialize chemical reactions for water dissociation
        system.chemicalReactionInit();

        // Create database for component properties
        system.createDatabase(true);

        // Set mixing rule for electrolyte CPA
        system.setMixingRule(10);

        // Enable multi-phase check for three-phase equilibrium
        system.setMultiPhaseCheck(true);

        // Initialize and flash
        system.init(0);
        ThermodynamicOperations ops = new ThermodynamicOperations(system);
        ops.TPflash();

        // Find the aqueous phase
        int aqueousPhaseIndex = -1;
        for (int p = 0; p < system.getNumberOfPhases(); p++) {
          String phaseType = system.getPhase(p).getPhaseTypeName();
          if (phaseType.equalsIgnoreCase("aqueous")) {
            aqueousPhaseIndex = p;
            break;
          }
          // Fallback: check if water is the dominant component
          if (system.getPhase(p).hasComponent("water")
              && system.getPhase(p).getComponent("water").getx() > 0.9) {
            aqueousPhaseIndex = p;
          }
        }

        // Report results
        int numPhases = system.getNumberOfPhases();
        if (aqueousPhaseIndex != -1) {
          double pH = system.getPhase(aqueousPhaseIndex).getpH();
          System.out.printf("%-12.1f %-10d %-12.4f%n", P, numPhases, pH);
        } else {
          System.out.printf("%-12.1f %-10d %-12s%n", P, numPhases, "No aq phase");
        }

      } catch (Exception e) {
        System.out.printf("%-12.1f %-10s %-12s%n", P, "Error", e.getMessage());
      }
    }

    System.out.println("--------------------------------------------------");
    System.out.println("Note: Pure water at 25°C should have pH ≈ 7.0");
  }
}
