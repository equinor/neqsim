package neqsim.chemicalreactions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Test pH calculation for methane-water system at 50 bara and 20°C.
 */
@Tag("slow")
public class MethaneWaterPHTest {
  private static final Logger logger = LogManager.getLogger(MethaneWaterPHTest.class);

  @Test
  public void testMethaneWaterPH() {
    logger.info("\n=== Methane-Water pH Test ===");
    logger.info("Conditions: 50 bara, 20°C (293.15 K)");
    logger.info("--------------------------------------------------");

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
      logger.info("\nResults:");
      logger.info("Number of phases: " + system.getNumberOfPhases());

      // Print phase information
      for (int p = 0; p < system.getNumberOfPhases(); p++) {
        String phaseType = system.getPhase(p).getPhaseTypeName();
        logger.info("\nPhase " + p + " (" + phaseType + "):");

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
            logger.printf(org.apache.logging.log4j.Level.INFO, "  %-15s x = %.6f%n",
                system.getPhase(p).getComponent(i).getComponentName(), x);
          }
        }

        // Print pH for aqueous phase
        if (isAqueous) {
          double pH = system.getPhase(p).getpH();
          logger.printf(org.apache.logging.log4j.Level.INFO, "\n  >>> pH = %.4f <<<%n", pH);
        }
      }

      logger.info("\n--------------------------------------------------");
      logger.info("Note: Pure water at 20°C should have pH ≈ 7.0");
      logger.info("      (Kw at 20°C is slightly less than at 25°C)");

    } catch (Exception e) {
      logger.info("Error: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
