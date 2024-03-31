package neqsim.thermodynamicOperations.flashOps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * @author ESOL
 */
class TPsolidFlash {
  @Test
  void testSolidFLash() {
    neqsim.thermo.system.SystemPrEos testSystem =
        new neqsim.thermo.system.SystemPrEos(283.15, 20.0);
    testSystem.addComponent("methane", 90.0);
    testSystem.addComponent("nC10", 10.0);
    testSystem.addTBPfraction("C11", 1.0, 150.0 / 1000.0, 0.82);
    //   testSystem.addComponent("nC10", 10.0);
    testSystem.addComponent("S8", 10.0);
    testSystem.createDatabase(true);
    testSystem.setMixingRule("classic");
    testSystem.setMultiPhaseCheck(true);
    testSystem.setSolidPhaseCheck("S8");
    testSystem.init(0);
    ThermodynamicOperations thermoops = new ThermodynamicOperations(testSystem);
    // thermoops.TPflash();
    thermoops.TPSolidflash();
    testSystem.prettyPrint();
    assertEquals(3, testSystem.getNumberOfPhases());
    assertTrue(testSystem.hasPhaseType(PhaseType.SOLID));

  }

}
