package neqsim.thermodynamicoperations.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * CriticalPointFlash class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class CriticalPointFlash {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CriticalPointFlash.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    SystemInterface testSystem = new SystemSrkEos(300, 80.01325);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    // testSystem.addComponent("water", 0.9);
    testSystem.addComponent("methane", 0.1);
    testSystem.addComponent("propane", 0.1);
    // testSystem.addComponent("i-butane", 0.1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.init(0);
    try {
      testOps.calcCricondenBar();
      // testOps.criticalPointFlash();
      // testOps.calcPTphaseEnvelope(true);
      // testOps.displayResult();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    testSystem.display();
  }
}
