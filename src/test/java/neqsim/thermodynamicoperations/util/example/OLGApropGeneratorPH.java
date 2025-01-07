package neqsim.thermodynamicoperations.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * OLGApropGeneratorPH class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public class OLGApropGeneratorPH {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(OLGApropGeneratorPH.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkEos(383.15, 1.0);
    // testSystem.addComponent("ethane", 10.0);
    testSystem.addComponent("water", 10.0);
    // testSystem.addComponent("n-heptane", 1.0);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);

    testSystem.setMultiPhaseCheck(true);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.dewPointTemperatureFlash();
      // testOps.TPflash();
      testSystem.display();
      double maxEnthalpy = testSystem.getEnthalpy();
      logger.info(" maxEnthalpy " + maxEnthalpy);
      testOps.bubblePointTemperatureFlash();
      testSystem.display();
      double minEnthalpy = testSystem.getEnthalpy();

      // testOps.PHflash(maxEnthalpy + 49560, 0);
      String fileName = "c:/Appl/OLGAneqsim.tab";
      testOps.OLGApropTablePH(minEnthalpy, maxEnthalpy, 41, testSystem.getPressure(), 2, 41,
          fileName, 0);
      testOps.displayResult();
    } catch (Exception ex) {
      testSystem.display();
      logger.error(ex.getMessage(), ex);
    }
  }
}
