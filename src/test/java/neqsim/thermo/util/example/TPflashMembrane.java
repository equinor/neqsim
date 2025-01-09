package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TPflashMembrane class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TPflashMembrane {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TPflashMembrane.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    // SystemInterface testSystem2 =
    // util.serialization.SerializationManager.open("c:/test.fluid");
    // testSystem2.display();
    SystemInterface testSystem =
        new SystemSrkEos(298, ThermodynamicConstantsInterface.referencePressure);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    testSystem.addComponent("CO2", 10.0);
    testSystem.addComponent("propane", 100.0, 0);
    testSystem.addComponent("propane", 100.0, 1);

    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);

    testSystem.init_x_y();
    testSystem.getPhase(0).setPressure(30.0);
    testSystem.getPhase(1).setPressure(2.0);
    testSystem.setAllPhaseType(PhaseType.byValue(1));
    testSystem.allowPhaseShift(false);

    try {
      String[] comps = {"CO2"};
      testOps.dTPflash(comps);
      // testOps.TPflash();
      testSystem.display();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }
}
