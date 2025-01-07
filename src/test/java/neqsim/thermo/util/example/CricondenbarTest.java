package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * CricondenbarTest class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public class CricondenbarTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(CricondenbarTest.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    SystemInterface testSystem = new SystemSrkEos(249.02, 50.0);
    testSystem.addComponent("methane", 0.943);
    testSystem.addComponent("ethane", 0.027);
    testSystem.addComponent("propane", 0.0074);
    testSystem.addComponent("n-butane", 0.00049);
    testSystem.addComponent("n-pentane", 0.001);
    testSystem.addComponent("n-hexane", 0.0027);
    testSystem.addComponent("nitrogen", 0.014);
    // testSystem.chemicalReactionInit();
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.init(0);
    testSystem.init(1);
    testSystem.display();
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    try {
      // testSystem.setTemperature(250.0);
      testOps.calcCricondenBar();
      // 8 testOps.TPflash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    testSystem.display();
  }
}
