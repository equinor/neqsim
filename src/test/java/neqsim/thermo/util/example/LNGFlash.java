package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * LNGFlash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class LNGFlash {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(LNGFlash.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkEos(20, 5.0);

    testSystem.addComponent("methane", 110.02);
    // testSystem.addComponent("n-pentane", 1e-10);
    testSystem.addComponent("n-hexane", 1.00001);
    // testSystem.addTBPfraction("C7", 0.1, 86.0/1000.0, 0.7);

    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.initProperties();
    // testSystem.setSolidPhaseCheck("n-hexane");
    // testSystem.addSolidComplexPhase("wax");
    testSystem.display();
    testSystem.init(0);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.bubblePointTemperatureFlash();
      testSystem.display();

      testOps.dewPointTemperatureFlash();
      testSystem.display();

      // testOps.TPflash();
      // testSystem.display();
      // testSystem.setMolarComposition(new double[] {0.1,0.1,0.1});
      // testOps.TPflash();
      // testOps.dewPointTemperatureFlash();
      // testSystem.display();
      // testOps.freezingPointTemperatureFlash();
      // testOps.calcWAT();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    /*
     * testSystem.reset(); testSystem.addComponent("methane", 1.0);
     * testSystem.addComponent("n-hexane", 0.000000009); testOps = new
     * ThermodynamicOperations(testSystem); try { testOps.TPflash(); // testSystem.display(); //
     * testOps.freezingPointTemperatureFlash(); // testOps.calcWAT(); testSystem.display(); } catch
     * (Exception ex) { logger.error(ex.getMessage(),e); } }
     */
  }
}
