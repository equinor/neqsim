package neqsim.thermodynamicOperations.flashOps.saturationOps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class HydrateInhibitorwtFlashTest {
  @Test
  void testRun() {
    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 0, 100.0);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    testSystem.addComponent("nitrogen", 79.0);
    testSystem.addComponent("oxygen", 21.0);
    // testSystem.addComponent("ethane", 0.10);
    // testSystem.addComponent("propane", 0.050);
    // testSystem.addComponent("i-butane", 0.0050);
    testSystem.addComponent("MEG", 0.000001);
    testSystem.addComponent("water", 0.0010);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);

    testSystem.init(0);
    testSystem.setMultiPhaseCheck(true);
    testSystem.setHydrateCheck(true);

    try {
      // creates HydrateInhibitorwtFlash object and calls run on it.
      testOps.hydrateInhibitorConcentrationSet("MEG", 0.99);
      double cons = 100 * testSystem.getPhase(0).getComponent("MEG").getNumberOfmoles()
          * testSystem.getPhase(0).getComponent("MEG").getMolarMass()
          / (testSystem.getPhase(0).getComponent("MEG").getNumberOfmoles()
              * testSystem.getPhase(0).getComponent("MEG").getMolarMass()
              + testSystem.getPhase(0).getComponent("water").getNumberOfmoles()
                  * testSystem.getPhase(0).getComponent("water").getMolarMass());
      assertEquals(98.54736778391424, cons);
    } catch (Exception ex) {
      ex.toString();
    }
    // testSystem.display();

    assertEquals(0.019690143220139962,
        testSystem.getPhase(0).getComponent("MEG").getNumberOfmoles());
  }
}
