package neqsim.thermodynamicoperations.flashops;

import org.junit.jupiter.api.Test;
import neqsim.thermo.mixingrule.EosMixingRulesInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * @author ESOL
 */
class TPmultiflashTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;
  static ThermodynamicOperations testOps = null;

  void testC1C7() {
    final double kij = 0.05;
    SystemInterface testSystem = new neqsim.thermo.system.SystemPrEos();
    testSystem.addComponent("methane", 70.0);
    testSystem.addComponent("n-heptane", 30.0);

    testSystem.setMixingRule("classic");

    ((EosMixingRulesInterface) testSystem.getPhase(0).getMixingRule())
        .setBinaryInteractionParameter(0, 1, kij);
    ((EosMixingRulesInterface) testSystem.getPhase(1).getMixingRule())
        .setBinaryInteractionParameter(0, 1, kij);

    testSystem.setMultiPhaseCheck(true);

    testSystem.setTemperature(155.1, "K");
    for (double p = 10.0; p <= 150.0; p += 0.1) {
      testSystem.setPressure(p, "bara");
      testOps = new ThermodynamicOperations(testSystem);
      testOps.TPflash();
      testSystem.initProperties();
      System.out.println("Pressure: " + p + " bara");
      testSystem.prettyPrint();
      if (testSystem.getNumberOfPhases() == 1) {
        System.out.println("Single phase detected at pressure: " + p + " bara");
      } else {
        System.out.println("Multiple phases detected at pressure: " + p + " bara");
      }
    }

  }

  @Test
  void testC1C72() {
    final double kij = 0.05;
    SystemInterface testSystem = new neqsim.thermo.system.SystemPrEos();
    testSystem.addComponent("methane", 70.0);
    testSystem.addComponent("n-heptane", 30.0);

    testSystem.setMixingRule("classic");

    ((EosMixingRulesInterface) testSystem.getPhase(0).getMixingRule())
        .setBinaryInteractionParameter(0, 1, kij);
    ((EosMixingRulesInterface) testSystem.getPhase(1).getMixingRule())
        .setBinaryInteractionParameter(0, 1, kij);

    testSystem.setMultiPhaseCheck(true);

    testSystem.setTemperature(155.1, "K");
    testSystem.setPressure(84.4, "bara");
    testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
    assert (testSystem.getNumberOfPhases() == 2) : "Expected 2 phases, got "
        + testSystem.getNumberOfPhases();


  }

}
