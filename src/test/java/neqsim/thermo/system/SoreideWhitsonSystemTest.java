package neqsim.thermo.system;

import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class SoreideWhitsonSystemTest {
  /**
   * Test Soreide-Whitson system with zero salinity. Checks that the phase mole fractions for
   * nitrogen, CO2, methane, ethane, and water in both gas and aqueous phases match the expected
   * values for a system with no added salt.
   */
  @Test
  public void testSoreideWhitsonSetup() {
    // Create a Soreide-Whitson system

    // SystemPrEos testSystem = new neqsim.thermo.system.SystemPrEos(298.0, 20.0);
    SystemSoreideWhitson testSystem = new SystemSoreideWhitson(298.0, 20.0);
    testSystem.addComponent("nitrogen", 0.1, "mole/sec");
    testSystem.addComponent("CO2", 0.2, "mole/sec");
    testSystem.addComponent("methane", 0.3, "mole/sec");
    testSystem.addComponent("ethane", 0.3, "mole/sec");
    testSystem.addComponent("water", 0.1, "mole/sec");
    testSystem.addSalinity(0, "mole/sec");
    testSystem.setTotalFlowRate(15, "mole/sec");
    testSystem.setMixingRule(11);
    testSystem.setPressure(40.0, "bara");
    testSystem.setTemperature(45.0, "C");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();

    // Check phase mole fractions for both gas and aqueous phases
    double[] expectedGasFractions = {1.10818E-1, 2.21422E-1, 3.32456E-1, 3.32456E-1, 2.84796E-3};
    double[] expectedAqueousFractions =
        {4.43467E-5, 2.07206E-3, 1.26653E-4, 1.29507E-4, 9.97627E-1};
    String[] componentNames = {"nitrogen", "CO2", "methane", "ethane", "water"};
    double tolerance = 1e-6;

    for (int phaseIdx = 0; phaseIdx < 2; phaseIdx++) {
      for (int compIdx = 0; compIdx < componentNames.length; compIdx++) {
        double moleFrac = testSystem.getPhase(phaseIdx).getComponent(compIdx).getx();
        double expected =
            (phaseIdx == 0) ? expectedGasFractions[compIdx] : expectedAqueousFractions[compIdx];
        org.junit.jupiter.api.Assertions.assertEquals(expected, moleFrac, tolerance,
            "Phase " + phaseIdx + " component " + componentNames[compIdx] + " mole fraction");
      }
    }

    testSystem.prettyPrint();
  }

  /**
   * Test Soreide-Whitson system with nonzero salinity (0.05 mole/sec). Checks that the phase mole
   * fractions for nitrogen, CO2, methane, ethane, and water in both gas and aqueous phases match
   * the expected values for a system with added salt. This validates the effect of salinity on
   * phase equilibrium and partitioning.
   */
  @Test
  public void testSoreideWhitsonSetup2() {
    // Create a Soreide-Whitson system
    SystemSoreideWhitson testSystem = new SystemSoreideWhitson(298.0, 20.0);
    testSystem.addComponent("nitrogen", 0.1, "mole/sec");
    testSystem.addComponent("CO2", 0.2, "mole/sec");
    testSystem.addComponent("methane", 0.3, "mole/sec");
    testSystem.addComponent("ethane", 0.3, "mole/sec");
    testSystem.addComponent("water", 0.1, "mole/sec");
    testSystem.addSalinity(0.05, "mole/sec");
    testSystem.setTotalFlowRate(15, "mole/sec");
    testSystem.setMixingRule(11);
    testSystem.setPressure(40.0, "bara");
    testSystem.setTemperature(45.0, "C");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();

    // Check phase mole fractions for both gas and aqueous phases (with salinity)
    double[] expectedGasFractions = {1.10836E-1, 2.2151E-1, 3.32509E-1, 3.3251E-1, 2.63456E-3};
    double[] expectedAqueousFractions = {2.53209E-5, 1.55814E-3, 7.67909E-5, 7.0922E-5, 9.98269E-1};
    String[] componentNames = {"nitrogen", "CO2", "methane", "ethane", "water"};
    double tolerance = 1e-6;

    for (int phaseIdx = 0; phaseIdx < 2; phaseIdx++) {
      for (int compIdx = 0; compIdx < componentNames.length; compIdx++) {
        double moleFrac = testSystem.getPhase(phaseIdx).getComponent(compIdx).getx();
        double expected =
            (phaseIdx == 0) ? expectedGasFractions[compIdx] : expectedAqueousFractions[compIdx];
        org.junit.jupiter.api.Assertions.assertEquals(expected, moleFrac, tolerance,
            "Phase " + phaseIdx + " component " + componentNames[compIdx] + " mole fraction");
      }
    }

    // Check salinity concentration in aqueous phase
    double expectedSalinity = 1.8877351154938637;
    double actualSalinity = testSystem.getPhase(1).getSalinityConcentration();
    org.junit.jupiter.api.Assertions.assertEquals(expectedSalinity, actualSalinity, 1e-8,
        "Aqueous phase salinity concentration");

  }

}

