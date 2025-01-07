package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemCSPsrkEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TestCSPsrk class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestCSPsrk {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestCSPsrk.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemCSPsrkEos(158, 5.662);
    // SystemInterface testSystem = new SystemSrkEos(110.0, 1.262);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    testSystem.addComponent("nitrogen", 1.17);
    testSystem.addComponent("methane", 94.14);
    testSystem.addComponent("ethane", 5.33);
    testSystem.addComponent("propane", 1.1);
    // testSystem.addComponent("n-butane", 0.41);
    // testSystem.addComponent("n-pentane", 0.6);
    // testSystem.addComponent("n-hexane", 0.6);
    // testSystem.addComponent("n-heptane", 0.24);
    // testSystem.addComponent("n-octane", 0.14);
    // testSystem.addComponent("CO2", 1.06);

    // testSystem.setTemperature(120);
    // testSystem.setPressure(4.43);
    // testSystem.addComponent("nitrogen", 4.25);
    // testSystem.addComponent("methane", 81.3);
    // testSystem.addComponent("ethane", 4.75);
    // testSystem.addComponent("propane", 4.87);
    // testSystem.addComponent("i-butane", 2.41);
    // testSystem.addComponent("n-butane", 2.42);

    // testSystem.createDatabase(true);
    testSystem.setMixingRule(2);

    try {
      // testOps.TPflash();
      testOps.bubblePointPressureFlash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    testSystem.display();
    logger.info(testSystem.getTemperature() - 273.15);
  }
}
