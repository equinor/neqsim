package neqsim.chemicalreactions;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Test pH calculation for methane-water system at 50 bara and 20°C.
 */
public class MethaneWaterPHTest {

  @Test
  public void testMethaneWaterPH() {
    System.out.println("\n=== Methane-Water pH Test ===");
    System.out.println("Conditions: 50 bara, 20°C (293.15 K)");
    System.out.println("--------------------------------------------------");

    double temperature = 293.15; // 20°C in Kelvin
    double pressure = 50.0; // 50 bara

    try {
      // Create electrolyte CPA system
      SystemInterface system = new SystemElectrolyteCPAstatoil(temperature, pressure);

      // Add components - methane and water
      system.addComponent("methane", 1.0); // 1 mol methane
      system.addComponent("water", 10.0); // 10 mol water

      // Initialize chemical reactions for water dissociation
      system.chemicalReactionInit();

      // Create database for component properties
      system.createDatabase(true);

      // Set mixing rule for electrolyte CPA
      system.setMixingRule(10);

      // Enable multi-phase check
      system.setMultiPhaseCheck(true);

      // Initialize
      system.init(0);

      // Run flash calculation
      ThermodynamicOperations ops = new ThermodynamicOperations(system);
      ops.TPflash();

      // Report results
      System.out.println("\nResults:");
      System.out.println("Number of phases: " + system.getNumberOfPhases());

      // Print phase information
      for (int p = 0; p < system.getNumberOfPhases(); p++) {
        String phaseType = system.getPhase(p).getPhaseTypeName();
        System.out.println("\nPhase " + p + " (" + phaseType + "):");

        // Check if this is the aqueous phase
        boolean isAqueous = "aqueous".equalsIgnoreCase(phaseType);
        if (!isAqueous && system.getPhase(p).hasComponent("water")) {
          double waterX = system.getPhase(p).getComponent("water").getx();
          isAqueous = waterX > 0.5;
        }

        // Print composition
        for (int i = 0; i < system.getPhase(p).getNumberOfComponents(); i++) {
          double x = system.getPhase(p).getComponent(i).getx();
          if (x > 1e-10) {
            System.out.printf("  %-15s x = %.6f%n",
                system.getPhase(p).getComponent(i).getComponentName(), x);
          }
        }

        // Print pH for aqueous phase
        if (isAqueous) {
          double pH = system.getPhase(p).getpH();
          System.out.printf("\n  >>> pH = %.4f <<<%n", pH);
        }
      }

      System.out.println("\n--------------------------------------------------");
      System.out.println("Note: Pure water at 20°C should have pH ≈ 7.0");
      System.out.println("      (Kw at 20°C is slightly less than at 25°C)");

    } catch (Exception e) {
      System.out.println("Error: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
