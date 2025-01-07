package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * H2Sdistribution class.
 * </p>
 *
 * @author esol
 * @since 2.2.3
 * @version $Id: $Id
 */
public class H2Sdistribution {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(H2Sdistribution.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemElectrolyteCPAstatoil(308.3, 32.8);
    testSystem.addComponent("H2S", 3100.0e-6);
    testSystem.addComponent("methane", 70.0);
    testSystem.addComponent("ethane", 8.0);
    testSystem.addComponent("propane", 8.0);
    testSystem.addTBPfraction("C6", 0.428, 86.178 / 1000.0, 0.664);
    testSystem.addTBPfraction("C7", 0.626, 96.00 / 1000.0, 0.738);
    testSystem.addTBPfraction("C8", 0.609, 107.000000000000 / 1000.0, 0.765);
    testSystem.addTBPfraction("C9", 0.309, 121.000000000000 / 1000.0, 0.781);
    testSystem.addTBPfraction("C12", 0.137, 161.000000000000 / 1000.0, 0.804900024);
    testSystem.addComponent("water", 50.00);
    testSystem.addComponent("Na+", 0.200);
    testSystem.addComponent("Cl+", 0.190);
    testSystem.addComponent("OH-", 3100.0e-8);
    testSystem.chemicalReactionInit();
    testSystem.createDatabase(true);
    testSystem.setMultiPhaseCheck(true);
    testSystem.setMixingRule(10);
    ThermodynamicOperations ops = new ThermodynamicOperations(testSystem);

    try {
      ops.TPflash();
    } catch (Exception ex) {
    }
    testSystem.display();
    // System.out.println("pH " + testSystem.getPhase("aqueous").getpH());
  }
}
