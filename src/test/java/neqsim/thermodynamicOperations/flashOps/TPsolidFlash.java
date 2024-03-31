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
    testSystem.addComponent("CO2", 1.0);
    testSystem.addComponent("methane", 80.0);
    testSystem.addComponent("ethane", 5.0);
    testSystem.addTBPfraction("C11", 0.01, 150.0 / 1000.0, 0.82);
    testSystem.addTBPfraction("C12", 0.01, 170.0 / 1000.0, 0.84);
    testSystem.addComponent("S8", 10.0);
    testSystem.setMixingRule("classic");
    testSystem.setMultiPhaseCheck(true);
    testSystem.setSolidPhaseCheck("S8");
    ThermodynamicOperations thermoops = new ThermodynamicOperations(testSystem);
    // thermoops.TPflash();
    thermoops.TPSolidflash();
    // testSystem.prettyPrint();
    assertEquals(3, testSystem.getNumberOfPhases());
    assertTrue(testSystem.hasPhaseType(PhaseType.SOLID));

    // System.out.println(
    // "kg S8 per kg HC " + (testSystem.getPhase(0).getComponent("S8").getFlowRate("kg/hr")
    // + testSystem.getPhase(1).getComponent("S8").getFlowRate("kg/hr"))
    // / (testSystem.getPhase(0).getFlowRate("kg/hr")
    // + testSystem.getPhase(1).getFlowRate("kg/hr")));

  }

}
