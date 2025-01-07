package neqsim.thermodynamicoperations.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * MEGwaterComplexFlash class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public class MEGwaterComplexFlash {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(MEGwaterComplexFlash.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 50, 1.0);
    testSystem.addComponent("methane", 0.01);
    // testSystem.addComponent("ethane", 0.10);
    // testSystem.addComponent("n-heptane", 0.5);
    // testSystem.addComponent("MEG", 0.139664804);
    testSystem.addComponent("TEG", 0.01);
    testSystem.addComponent("water", 1.0 - 0.01);

    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    testSystem.init(0);
    // logger.info("fug "
    // +Math.log(testSystem.getPhase(1).getComponent("TEG").getFugacityCoefficient()));
    testSystem.setSolidPhaseCheck("water");
    // testSystem.setMultiPhaseCheck(true);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    try {
      // testOps.TPflash();
      // testSystem.display();
      // testOps.freezingPointTemperatureFlash();
      testOps.calcSolidComlexTemperature("TEG", "water");
      testSystem.display();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    logger.info("temperature " + (testSystem.getTemperature() - 273.15));
    logger.info("activity water " + testSystem.getPhase(1).getActivityCoefficient(2));
    logger.info("activity TEG " + testSystem.getPhase(1).getActivityCoefficient(1));
  }
}
