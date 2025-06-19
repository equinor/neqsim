package neqsim.thermo.system;

import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class SoreideWhitsonSystemTest {
  @Test
  public void testSoreideWhitsonSetup() {
    // Create a Soreide-Whitson system

    //SystemPrEos testSystem = new neqsim.thermo.system.SystemPrEos(298.0, 20.0);
    SystemSoreideWhitson testSystem = new SystemSoreideWhitson(298.0, 20.0);
    testSystem.addComponent("methane", 0.8, "mole/sec");
    testSystem.addComponent("water", 0.2, "mole/sec");
    testSystem.addSalinity(0, "mole/sec");
    testSystem.setTotalFlowRate(1e7, "kg/hr");
    testSystem.setMixingRule(11);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();

    System.out.println("Salinity  " + testSystem.getPhase(1).getSalinityConcentration() + " mol/L");

    testSystem.prettyPrint();
  }
}
