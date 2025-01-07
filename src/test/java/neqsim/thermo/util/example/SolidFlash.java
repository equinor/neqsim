package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * SolidFlash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class SolidFlash {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SolidFlash.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkCPAstatoil(269.671, 1.00);
    // SystemInterface testSystem = new SystemSrkTwuCoonEos(273.15 - 165, 1.0);
    // SystemInterface testSystem = new SystemPrEos(91.617,1.1168013258);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    // DataHandling output = new DataHandling();
    // testSystem.addComponent("water", 1.0-0.1525);
    testSystem.addComponent("methane", 121.8);
    // testSystem.addComponent("ethane", 0.05);
    // testSystem.addComponent("propane", 0.03);
    // testSystem.addComponent("n-hexane", 10.01);
    // testSystem.addComponent("nitrogen", 0.05);
    testSystem.addComponent("MEG", 0.0916);
    testSystem.addComponent("water", 10.916);

    // testSystem.addComponent("propane", 2.6844);
    // testSystem.addComponent("i-butane", 0.415);
    // testSystem.addComponent("n-butane", 0.8188);
    // testSystem.addComponent("iC5", 0.1814);
    // testSystem.addComponent("n-pentane", 0.1702);
    // testSystem.addComponent("n-hexane", 0.1003);
    // testSystem.addComponent("n-heptane", 0.0449);
    // testSystem.addComponent("benzene", 180.0e-4);
    // testSystem.addComponent("H2S", 10.0);
    // testSystem.addComponent("nitrogen", 10e-6);
    // testSystem.addComponent("ethane" , 0.1);

    testSystem.setMultiPhaseCheck(true);

    // testSystem.setSolidPhaseCheck("water");
    testSystem.createDatabase(true);
    testSystem.setMixingRule(9);
    testSystem.useVolumeCorrection(true);

    testSystem.setSolidPhaseCheck("water");
    // testSystem.setSolidPhaseCheck("CO2");
    testSystem.init(0);
    try {
      testOps.freezingPointTemperatureFlash();
      // testOps.TPSolidflash();
      // testSystem.display();
      // testOps.waterDewPointTemperatureFlash();
      // testOps.bubblePointTemperatureFlash();
      testSystem.display();
      // testOps.dewPointTemperatureFlash();
      // testSystem.display();
      // testOps.TPflash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }
}
