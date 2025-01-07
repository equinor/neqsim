package neqsim.thermodynamicoperations.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * SolidFlash class.
 * </p>
 *
 * @author ESOL
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
    SystemInterface testSystem = new SystemUMRPRUMCEos(273.15 - 30, 18.0);
    // testSystem.addComponent("nitrogen", 83.33);
    // testSystem.addComponent("oxygen", 8.49);
    // testSystem.addComponent("argon", 0.87);
    // testSystem.addComponent("CO2", 7.3);

    // testSystem.addComponent("nitrogen", 8.33);
    // testSystem.addComponent("methane", 0.17);
    // testSystem.addComponent("ethane", 0.87);
    testSystem.addComponent("CO2", 1.83);

    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    // testSystem.setMixingRule("HV", null);
    testSystem.setSolidPhaseCheck("CO2");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.TPflash();
      testSystem.display();
      testSystem.initProperties();

      double enthalpy = testSystem.getEnthalpy();

      testSystem.setPressure(1.0);

      testOps.PHflash(enthalpy);
      // testOps.TPflash();
      // testOps.PHsolidFlash(enthalpy);
      // // testOps.TPSolidflash();
      // testOps.freezingPointTemperatureFlash();
      testSystem.display();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }
}
