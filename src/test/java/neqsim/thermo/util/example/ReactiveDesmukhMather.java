package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemDesmukhMather;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * ReactiveDesmukhMather class.
 * </p>
 *
 * @author esol
 * @since 2.2.3
 * @version $Id: $Id
 */
public class ReactiveDesmukhMather {
  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemDesmukhMather(290.0, 5.1);

    testSystem.addComponent("CO2", 0.9000509251997);
    testSystem.addComponent("MDEA", 1.0);
    testSystem.addComponent("water", 9.0);

    testSystem.chemicalReactionInit();
    testSystem.setMixingRule(2);
    testSystem.createDatabase(true);
    // testSystem.setPhysicalPropertyModel(PhysicalPropertyModel.AMINE);

    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);

    try {
      ops.bubblePointPressureFlash(false);
    } catch (Exception ex) {
    }
    testSystem.display();
  }
}
