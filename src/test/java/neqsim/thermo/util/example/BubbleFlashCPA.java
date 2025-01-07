package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * BubbleFlashCPA class.
 * </p>
 *
 * @author esol
 * @since 2.2.3
 * @version $Id: $Id
 */
public class BubbleFlashCPA {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(BubbleFlashCPA.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 - 36.6, 63.2);
    // SystemInterface testSystem = new SystemSrkEos(273.15- 50.6, 63.2);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    // double x =0.99581

    testSystem.addComponent("nitrogen", 24856.5, "kg/hr");
    testSystem.addComponent("methane", 479618.17, "kg/hr");
    testSystem.addComponent("ethane", 65819, "kg/hr");
    testSystem.addComponent("propane", 51950, "kg/hr");
    testSystem.addComponent("i-butane", 6697, "kg/hr");
    testSystem.addComponent("n-butane", 6461, "kg/hr");
    testSystem.addComponent("n-pentane", 16.73, "kg/hr");
    testSystem.addComponent("i-pentane", 51.4, "kg/hr");
    // testSystem.addComponent("1-propanol", 17.9, "Nlitre/min");
    testSystem.addComponent("water", 0.9, "Nlitre/min");

    testSystem.addComponent("MEG", 100.0);
    testSystem.addComponent("TEG", 100.0);
    /*
     * testSystem.addComponent("methane", 69.243);
     *
     * testSystem.addComponent("CO2", 4.113); testSystem.addComponent("ethane", 8.732);
     * testSystem.addComponent("propane", 4.27); testSystem.addComponent("n-pentane", 1.641);
     * testSystem.addComponent("i-pentane", 0.877);
     *
     * testSystem.addComponent("benzene", 1.27);
     *
     * testSystem.addTBPfraction("C6", 1.49985, 86.178 / 1000.0, 0.664);
     * testSystem.addTBPfraction("C7", 1.359864, 96.0 / 1000.0, 0.738);
     * testSystem.addTBPfraction("C8", 0.939906, 107.0 / 1000.0, 0.765);
     * testSystem.addTBPfraction("C9", 0.879912, 121.0 / 1000.0, 0.781);
     * testSystem.addTBPfraction("C10", 0.45, 134.0 / 1000.0, 0.792);
     */
    // testSystem.addPlusFraction("C11+", 3.44, 231.0/1000, 0.87);
    // testSystem.getCharacterization().characterisePlusFraction();

    testSystem.setMultiPhaseCheck(true);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    try {
      // testOps.bubblePointTemperatureFlash();
      // testOps.dewPointTemperatureFlash();
      // testOps.bubblePointPressureFlash(false);
      testOps.TPflash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    testSystem.saveFluid(37);
    testSystem.display();
    testSystem.clone();
    logger.info("activity " + testSystem.getPhase(1).getActivityCoefficient(0));

    // thermo.ThermodynamicModelTest testModel = new
    // thermo.ThermodynamicModelTest(testSystem);
    // testModel.runTest();
  }
}
