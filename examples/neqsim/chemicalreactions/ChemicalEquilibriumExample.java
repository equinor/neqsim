package neqsim.chemicalreactions;

import java.util.Arrays;
import java.util.stream.Collectors;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Demonstrates solving a simple gas-phase combustion equilibrium using the chemical reaction
 * utilities from the test suite.
 */
public class ChemicalEquilibriumExample {

  private ChemicalEquilibriumExample() {}

  public static void main(String[] args) {
    SystemSrkEos testSystem = new SystemSrkEos(303.3, 2.8);
    testSystem.addComponent("methane", 5.0, "kg/sec");
    testSystem.addComponent("nitrogen", 100.0, "kg/sec");
    testSystem.addComponent("oxygen", 40.0, "kg/sec");

    testSystem.chemicalReactionInit();
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.init(0);
    testSystem.setMaxNumberOfPhases(1);
    testSystem.setNumberOfPhases(1);

    try {
      testSystem.getChemicalReactionOperations().solveChemEq(0, 0);
      String composition = Arrays.stream(testSystem.getPhase(0).getComponents())
          .map(comp -> comp.getName() + "=" + comp.getx() * 100.0 + " mol%")
          .collect(Collectors.joining(", "));

      System.out.println("Gas phase composition after equilibrium: " + composition);
    } catch (Exception e) {
      System.err.println("Chemical equilibrium calculation failed: " + e.getMessage());
    }
  }
}
