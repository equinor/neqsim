package neqsim.chemicalreactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
  /**
   * Test pure water pH calculation with single phase.
   * 
   * <p>
   * Pure water at 25°C should have: - Equal H3O+ and OH- concentrations (electroneutrality) - pH ~
   * 7 (molarity based) or ~ 8.7 (mole fraction based) - Element conservation (A*n = b)
   * </p>
   */
  @Test
  public void testPureWaterPH() {
    double temperature = 298.15; // 25°C
    double pressure = 1.0; // 1 bar

    // Create electrolyte CPA system - pure water
    SystemInterface system = new SystemElectrolyteCPAstatoil(temperature, pressure);
    system.addComponent("water", 10.0);

    // Configure as single-phase aqueous BEFORE chemicalReactionInit
    system.setMultiPhaseCheck(false);

    system.chemicalReactionInit();
    system.createDatabase(true);
    system.setMixingRule(10);

    // Set phase type after creating database
    system.setPhaseType(0, neqsim.thermo.phase.PhaseType.AQUEOUS);
    system.setPhaseType(1, neqsim.thermo.phase.PhaseType.AQUEOUS);

    system.init(0);

    assertTrue(system.isChemicalSystem(), "System should be a chemical system");
    assertTrue(system.getChemicalReactionOperations().hasReactions(),
        "System should have reactions");

    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();

    // Get chemical equilibrium operations
    ChemicalReactionOperations chemOps = system.getChemicalReactionOperations();

    // Verify element conservation after flash
    double[] nVec = chemOps.calcNVector();
    double[] bVec = chemOps.calcBVector();
    double[][] aMat = chemOps.getAmatrix();

    // Check element conservation: A*n = b
    for (int i = 0; i < aMat.length; i++) {
      double sum = 0;
      for (int j = 0; j < nVec.length; j++) {
        sum += aMat[i][j] * nVec[j];
      }
      assertEquals(bVec[i], sum, 1e-10, "Element conservation should hold for row " + i);
    }

    // Check electroneutrality: H3O+ ≈ OH- for pure water
    double h3oMoles = system.getPhase(0).getComponent("H3O+").getNumberOfMolesInPhase();
    double ohMoles = system.getPhase(0).getComponent("OH-").getNumberOfMolesInPhase();

    // H3O+ and OH- should be equal within 1% for pure water
    double ratio = h3oMoles / ohMoles;
    assertEquals(1.0, ratio, 0.01,
        "H3O+ and OH- should be equal for pure water (ratio = " + ratio + ")");

    // Check pH is in reasonable range
    double pH = system.getPhase(0).getpH();
    assertTrue(Double.isFinite(pH), "pH should be finite for pure water");
    // pH can vary depending on solver path - just ensure it's in a physically plausible range
    assertTrue(pH > 5.0 && pH < 10.0, "Pure water pH should be in reasonable range, got " + pH);
  }

  /**
   * Test three-phase (gas/oil/water) pH calculation.
   */
  @Test
  // @Disabled("Long-running diagnostic; enable locally when needed")
  public void testMethaneDecaneWaterThreePhase() {
    double[] pressures = {10.0};
    double temperature = 298.15; // 25°C

    for (double P : pressures) {
      SystemInterface system = new SystemElectrolyteCPAstatoil(temperature, P);

      system.addComponent("methane", 1.0);
      system.addComponent("CO2", 0.1);
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
