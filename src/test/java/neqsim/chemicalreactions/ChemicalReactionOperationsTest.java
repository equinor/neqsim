package neqsim.chemicalReactions;

import org.junit.jupiter.api.BeforeAll;
import neqsim.thermo.system.SystemSrkEos;

public class ChemicalReactionOperationsTest {
  static SystemSrkEos testSystem;

  @BeforeAll
  public void setUp() {
    testSystem = new SystemSrkEos(303.3, 2.8);

    testSystem.addComponent("methane", 5.0, "kg/sec");
    // testSystem.addComponent("nitrogen", 100.0, "kg/sec");
    testSystem.addComponent("oxygen", 40.0, "kg/sec");
    testSystem.chemicalReactionInit();

    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.init(0);
    testSystem.setMaxNumberOfPhases(1);
    testSystem.setNumberOfPhases(1);
    testSystem.getChemicalReactionOperations().solveChemEq(0, 0);
  }
}
