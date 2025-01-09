package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseEos;

/**
 * Test class for verifying the behavior of EOS (Equation of State) mixing rules in the NeqSim
 * library.
 */
public class EosMixingRulesTest {
  @Test
  void testSetMixingRuleName() {
    neqsim.thermo.system.SystemPrEos testSystem = new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("CO2", 0.01);
    testSystem.changeComponentName(testSystem.getComponent(0).getName(),
        (testSystem.getComponent(0).getName() + "__well1"));
    testSystem.changeComponentName(testSystem.getComponent(1).getName(),
        (testSystem.getComponent(1).getName() + "__well1"));

    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("CO2", 0.01);
    testSystem.changeComponentName(testSystem.getComponent(2).getName(),
        (testSystem.getComponent(2).getName() + "__well2"));
    testSystem.changeComponentName(testSystem.getComponent(3).getName(),
        (testSystem.getComponent(3).getName() + "__well2"));

    testSystem.setMixingRule("classic");

    double kij =
        ((PhaseEos) testSystem.getPhase(0)).getEosMixingRule().getBinaryInteractionParameter(0, 1);
    double kij2 =
        ((PhaseEos) testSystem.getPhase(0)).getEosMixingRule().getBinaryInteractionParameter(3, 0);

    // Print kij
    assertEquals(-0.019997, kij, 1e-5);
    assertTrue(kij == kij2);
  }

  @Test
  void testMEGOil() {
    neqsim.thermo.system.SystemSrkCPAstatoil testSystem =
        new neqsim.thermo.system.SystemSrkCPAstatoil(298.0, 10.0);
    testSystem.addTBPfraction("C8", 0.01, 90.9 / 1000.0, 0.9);
    testSystem.addComponent("ethanol", 0.01);

    testSystem.setMixingRule("classic");

    double kij =
        ((PhaseEos) testSystem.getPhase(0)).getEosMixingRule().getBinaryInteractionParameter(0, 1);

    // Print kij
    assertEquals(-0.05, kij, 1e-5);
  }

  @Test
  void testHCoilInter() {
    neqsim.thermo.system.SystemPrEos testSystem = new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
    testSystem.addTBPfraction("C8", 0.01, 90.9 / 1000.0, 0.9);
    testSystem.addComponent("CO2", 0.01);
    testSystem.changeComponentName(testSystem.getComponent(0).getName(),
        (testSystem.getComponent(0).getName() + "__well1"));
    testSystem.changeComponentName(testSystem.getComponent(1).getName(),
        (testSystem.getComponent(1).getName() + "__well1"));

    testSystem.addTBPfraction("C8", 0.01, 90.9 / 1000.0, 0.9);
    testSystem.addComponent("CO2", 0.01);
    testSystem.changeComponentName(testSystem.getComponent(2).getName(),
        (testSystem.getComponent(2).getName() + "__well2"));
    testSystem.changeComponentName(testSystem.getComponent(3).getName(),
        (testSystem.getComponent(3).getName() + "__well2"));

    testSystem.setMixingRule("classic");

    double kij =
        ((PhaseEos) testSystem.getPhase(0)).getEosMixingRule().getBinaryInteractionParameter(0, 1);
    double kij2 =
        ((PhaseEos) testSystem.getPhase(0)).getEosMixingRule().getBinaryInteractionParameter(3, 0);

    // Print kij
    assertEquals(0.1, kij, 1e-5);
    assertTrue(kij == kij2);
  }
}
