package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TVPexample class.
 * </p>
 *
 * @author MLLU
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TVPexample {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TVPexample.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    SystemInterface testSystem = new SystemSrkEos(275.15 + 37.7778, 1.0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.addComponent("methane", 0.1);
    testSystem.addComponent("ethane", 0.2);
    testSystem.addComponent("propane", 0.3);
    testSystem.addComponent("i-butane", 0.3);
    testSystem.addComponent("n-butane", 0.1);
    testSystem.addComponent("i-pentane", 0.1);
    testSystem.addComponent("n-pentane", 100.0);
    testSystem.addComponent("n-hexane", 100.0);
    testSystem.addComponent("n-heptane", 100.0);
    testSystem.addComponent("n-octane", 100.0);

    testSystem.createDatabase(true);
    // testSystem.setMixingRule(10);

    testOps.TPflash();
    testSystem.display();

    try {
      testOps.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      logger.error("Exception thrown in bubble point flash");
    }
    testSystem.display();
  }
}
