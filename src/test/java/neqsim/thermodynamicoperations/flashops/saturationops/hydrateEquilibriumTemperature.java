package neqsim.thermodynamicoperations.flashops.saturationops;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.thermo.mixingrule.EosMixingRuleType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class hydrateEquilibriumTemperature {
  @Test
  void testRun() {
    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 0, 100.0);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    testSystem.addComponent("methane", 79.0);
    testSystem.addComponent("ethane", 10.10);
    testSystem.addComponent("propane", 2.050);
    testSystem.addComponent("water", 10);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(EosMixingRuleType.CLASSIC_TX_CPA);

    testSystem.init(0);
    testSystem.setMultiPhaseCheck(true);
    testSystem.setHydrateCheck(true);

    double hydt;
    try {
      testOps.hydrateFormationTemperature();
      hydt = testSystem.getTemperature("C");
      Assertions.assertEquals(12.7593994282, hydt, 1e-3);
    } catch (Exception ex) {
      ex.toString();
    }

  }
}
