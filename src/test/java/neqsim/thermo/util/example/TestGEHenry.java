package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemGEWilson;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TestGEHenry class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestGEHenry {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestGEHenry.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemGEWilson(273.15 + 55.0, 1.301325);
    // SystemInterface testSystem = new SystemNRTL(273.15 + 55.0,1.301325);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.addComponent("methane", 10.0);
    // testSystem.addComponent("methanol", 1.0);
    testSystem.addComponent("water", 10.0);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.init(0);

    try {
      testOps.TPflash();
      // testOps.bubblePointPressureFlash(false); //(false);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    testSystem.display();
  }
}
