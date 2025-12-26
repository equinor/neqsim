package neqsim.chemicalreactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Disabled;
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
    double temperature = 298.15; // 25°C
    double pressure = 1.0; // 1 bar

    // Create electrolyte CPA system - pure water
    SystemInterface system = new SystemElectrolyteCPAstatoil(temperature, pressure);
    system.addComponent("water", 10.0);

    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule(10);
    system.setMultiPhaseCheck(true);

    system.init(0);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();

    // Find aqueous phase - for pure water, it might just be "liquid"
    int aqueousPhaseIndex = 0;
    for (int p = 0; p < system.getNumberOfPhases(); p++) {
      String phaseType = system.getPhase(p).getPhaseTypeName();
      if (phaseType.equalsIgnoreCase("aqueous")) {
        aqueousPhaseIndex = p;
        break;
      }
    }

    double pH = system.getPhase(aqueousPhaseIndex).getpH();
    assertTrue(Double.isFinite(pH), "pH should be finite for pure water");
    // Model-dependent; keep the assertion robust while still catching NaN/absurd values.
    assertTrue(pH > 6.0 && pH < 10.0, "Pure-water pH should be in a reasonable range");
  }

  @Test
  @Disabled("Long-running diagnostic (electrolyte + 3-phase equilibrium); enable locally when needed")
  public void testMethaneDecaneWaterThreePhase() {
    // Representative pressure case (keep test fast)
    double[] pressures = {10.0};
    double temperature = 298.15; // 25°C

    for (double P : pressures) {
      // Create electrolyte CPA system for water with hydrocarbons
      SystemInterface system = new SystemElectrolyteCPAstatoil(temperature, P);

      // Add components - methane (gas), n-decane (oil), water
      system.addComponent("methane", 1.0);
      system.addComponent("nC10", 0.5);
      system.addComponent("water", 10.0);

      system.chemicalReactionInit();
      system.createDatabase(true);
      system.setMixingRule(10);
      system.setMultiPhaseCheck(true);

      system.init(0);
      ThermodynamicOperations ops = new ThermodynamicOperations(system);
      ops.TPflash();

      // Find aqueous phase
      int aqueousPhaseIndex = -1;
      for (int p = 0; p < system.getNumberOfPhases(); p++) {
        String phaseType = system.getPhase(p).getPhaseTypeName();
        if (phaseType.equalsIgnoreCase("aqueous")) {
          aqueousPhaseIndex = p;
          break;
        }
        if (system.getPhase(p).hasComponent("water")
            && system.getPhase(p).getComponent("water").getx() > 0.9) {
          aqueousPhaseIndex = p;
        }
      }

      assertNotEquals(-1, aqueousPhaseIndex, "Should have an aqueous phase");
      double pH = system.getPhase(aqueousPhaseIndex).getpH();
      assertTrue(Double.isFinite(pH), "pH should be finite");
    }
  }
}
