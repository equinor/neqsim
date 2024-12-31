package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.database.NeqSimDataBase;

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

  @Test
  void testSolidFLash2() {
    NeqSimDataBase.setCreateTemporaryTables(true);
    neqsim.thermo.system.SystemPrEos testSystem =
        new neqsim.thermo.system.SystemPrEos(283.15, 100.0);
    testSystem.addComponent("nitrogen", 1.75);
    testSystem.addComponent("CO2", 0.23);
    testSystem.addComponent("methane", 93.84);
    testSystem.addComponent("ethane", 3.54);
    testSystem.addComponent("propane", 0.585);
    testSystem.addComponent("i-butane", 0.41);
    testSystem.addComponent("n-butane", 0.13);
    testSystem.addComponent("i-pentane", 0.0945);
    testSystem.addComponent("n-pentane", 0.033);
    testSystem.addTBPfraction("C6", 0.1, 82.23 / 1000.0, 0.6762);
    testSystem.addTBPfraction("C7", 0.185, 88.65 / 1000.0, 0.757);
    testSystem.addTBPfraction("C8", 0.118, 103.65 / 1000.0, 0.761);
    testSystem.addTBPfraction("C9", 0.051, 120.65 / 1000.0, 0.775);
    testSystem.addTBPfraction("C10", 0.0222, 134.65 / 1000.0, 0.795);
    testSystem.addTBPfraction("C11", 0.0145, 147.65 / 1000.0, 0.813);
    testSystem.addTBPfraction("C12", 0.0095, 161.65 / 1000.0, 0.83);
    testSystem.addTBPfraction("C13", 0.0062, 175.65 / 1000.0, 0.845);
    testSystem.addTBPfraction("C14", 0.004, 190.65 / 1000.0, 0.859);
    testSystem.addTBPfraction("C15", 0.0026, 206.65 / 1000.0, 0.872);
    testSystem.addTBPfraction("C16", 0.003, 222.65 / 1000.0, 0.885);
    testSystem.addTBPfraction("C17", 0.0017, 222.65 / 1000.0, 0.885);
    testSystem.addTBPfraction("C18", 0.0011, 237.65 / 1000.0, 0.898);
    testSystem.addTBPfraction("C98", 0.0007, 251.65 / 1000.0, 0.907);
    testSystem.addTBPfraction("C20", 0.0005, 263.65 / 1000.0, 0.918);
    testSystem.addTBPfraction("C21", 0.0009, 301.65 / 1000.0, 0.945);
    testSystem.addComponent("S8", 1.0e-5);
    testSystem.setMixingRule("classic");
    testSystem.setMultiPhaseCheck(true);
    testSystem.setTotalFlowRate(1.0, "MSm3/hr");
    testSystem.addComponent("S8", 100.0, "kg/hr");
    testSystem.setSolidPhaseCheck("S8");
    NeqSimDataBase.setCreateTemporaryTables(false);
    ThermodynamicOperations thermoops = new ThermodynamicOperations(testSystem);
    thermoops.TPflash();
    // thermoops.TPSolidflash();
    // testSystem.prettyPrint();
    assertEquals(3, testSystem.getNumberOfPhases());
    assertTrue(testSystem.hasPhaseType(PhaseType.SOLID));

    // System.out.println(
    // "kg S8 per MSm3 gas " + (testSystem.getPhase(0).getComponent("S8").getFlowRate("kg/hr")
    // + testSystem.getPhase(1).getComponent("S8").getFlowRate("kg/hr")));
    // System.out.println("m3 oil per MSm3 " +
    // (testSystem.getPhase(PhaseType.OIL).getFlowRate("m3/hr")
    // * 24 / testSystem.getPhase(PhaseType.GAS).getFlowRate("MSm3/day")));
  }
}
