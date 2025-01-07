package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPA;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TPflashMethanolWaterPropane class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TPflashMethanolWaterPropane {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TPflashMethanolWaterPropane.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkCPA(300, 10.01325);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    /*
     * testSystem.addComponent("methane", 150.0e-2); testSystem.addComponent("propane", 150.0e-3);
     * testSystem.addComponent("methanol", 0.5); testSystem.addComponent("water", 0.5);
     *
     * testSystem.createDatabase(true); testSystem.setMixingRule(10);
     * testSystem.setMultiPhaseCheck(true);
     */
    testSystem = testSystem.readObject(100);
    testOps = new ThermodynamicOperations(testSystem);
    testSystem.init(0);
    try {
      testOps.TPflash();
      // testOps.bubblePointPressureFlash(false);
      testSystem.display();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    // testSystem.saveFluid(3019);
  }
}
