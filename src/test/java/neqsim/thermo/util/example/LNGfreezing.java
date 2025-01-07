package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * LNGfreezing class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class LNGfreezing {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(LNGfreezing.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkEos(273.15 - 152.8, 5);
    // SystemInterface testSystem = new SystemSrkTwuCoonEos(162, 59.7);
    // SystemInterface testSystem = new SystemPCSAFT(159, 50.0);
    testSystem.addComponent("CO2", 0.17000);
    testSystem.addComponent("nitrogen", 1.101268581777448);
    testSystem.addComponent("methane", 0.324059461687833);
    testSystem.addComponent("ethane", 0.274475926106361);
    testSystem.addComponent("propane", 0.0305789847160361);
    // testSystem.addComponent("i-butane", 0.0050000);
    // testSystem.addComponent("n-butane", 0.269617045712322);
    // testSystem.addComponent("i-pentane", 0.0012000);
    // testSystem.addComponent("n-pentane", 0.12000);
    // testSystem.addComponent("benzene", 0.0002000);
    // testSystem.addComponent("n-hexane", 0.0002000);
    // testSystem.addComponent("water", 0.0000000551);
    // testSystem.addComponent("propane", 1.715);
    // testSystem.addComponent("nitrogen", 2.5);
    // testSystem.addComponent("22-dim-C3", 500e-4);
    // testSystem.addComponent("n-hexane", 0.01);
    // testSystem.addComponent("nitrogen", 0.05);
    // testSystem.addComponent("CO2", 10.0);

    // testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    // testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");
    testSystem.setSolidPhaseCheck("CO2");
    testSystem.init(0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      // testOps.TPflash();
      // testOps.bubblePointPressureFlash(false);
      // thermodynamicOperations.flashOps.saturationOps.freezingPointTemperatureFlash
      // operation = new
      // thermodynamicOperations.flashOps.saturationOps.freezingPointTemperatureFlash(testSystem);
      // System.out.println("funk " + operation.calcFunc());
      testOps.freezingPointTemperatureFlash();
      // System.out.println("freeze temperature " + (testSystem.getTemperature() -
      // 273.15));
      // testOps.TPSolidflash();
      testSystem.display();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }
}
