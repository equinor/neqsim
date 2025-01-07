package neqsim.thermodynamicoperations.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * VLSolidTPFLash class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public class VLSolidTPFLash {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(VLSolidTPFLash.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemPrEos(208.2, 18.34);
    testSystem.addComponent("nitrogen", 0.379);
    testSystem.addComponent("CO2", 100);
    testSystem.addComponent("methane", 85.299);
    testSystem.addComponent("ethane", 7.359);
    testSystem.addComponent("propane", 3.1);
    testSystem.addComponent("i-butane", 0.504);
    testSystem.addComponent("n-butane", 0.85);
    testSystem.addComponent("i-pentane", 0.323);
    testSystem.addComponent("n-pentane", 0.231);
    testSystem.addComponent("n-hexane", 0.173);
    testSystem.addComponent("n-heptane", 0.078);

    testSystem.createDatabase(true);
    // 1- orginal no interaction 2- classic w interaction
    // 3- Huron-Vidal 4- Wong-Sandler
    testSystem.setMixingRule(2);
    // testSystem.setMultiPhaseCheck(true);
    testSystem.init(0);
    testSystem.setSolidPhaseCheck("CO2");
    // testSystem.display();
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    double entalp = 0;
    try {
      testOps.TPflash();
      // testOps.dewPointTemperatureFlash();
      testSystem.display();

      // testOps.freezingPointTemperatureFlash();
      // testSystem.display();
      testSystem.init(3);
      entalp = testSystem.getEnthalpy();
      // testSystem.setNumberOfPhases(3);
      // testSystem.setPressure(18.0);
      // testOps.TPflash();
      // testSystem.display();
      testOps.PHsolidFlash(entalp - 1000.0);
      // testOps.PHflash(entalp, 0);
      // testSystem.display();
      // testOps.freezingPointTemperatureFlash();
      testSystem.display();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    testSystem.init(3);
    // testSystem.display();
    // logger.info("enthalpy CO2 solid " + testSystem.getPhase(2).getEnthalpy() + "
    // index " + testSystem.getPhaseIndex(2));
    logger.info("total enthalpy " + (testSystem.getEnthalpy() - entalp));
    logger.info("out temperature " + (testSystem.getTemperature() - 273.15));
    // testSystem.display();
  }
}
