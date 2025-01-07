package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemBWRSEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TestMBWR32 class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestMBWR32 {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestMBWR32.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemBWRSEos(298.15, 0.101);
    // SystemInterface testSystem = new SystemSrkEos(111.15, 5.01);
    // SystemInterface testSystem = new SystemPrEos(111.0, 1.0523);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    testSystem.addComponent("methane", 1.0);

    testSystem.createDatabase(true);
    testSystem.setMixingRule(1);

    testSystem.init(0);
    testSystem.init(3);
    logger.info("Z " + testSystem.getLowestGibbsEnergyPhase().getZ());

    try {
      // testOps.TPflash();
      testOps.bubblePointTemperatureFlash();
      // testOps.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      logger.info(ex.toString());
    }
    testSystem.display();
  }
}
