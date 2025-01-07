package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TPflash2 class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TPflash2 {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TPflash2.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    SystemInterface testSystem =
        new SystemSrkCPAstatoil(273.15 + 80.0, ThermodynamicConstantsInterface.referencePressure);
    testSystem.addComponent("nitrogen", 8.71604938);
    // testSystem.addComponent("oxygen", 22.71604938);
    testSystem.addComponent("water", 110.234567901);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    // testSystem.setMultiPhaseCheck(true);
    SystemInterface testSystem2 =
        new SystemSrkCPAstatoil(273.15 + 80.0, ThermodynamicConstantsInterface.referencePressure);
    testSystem2.addComponent("nitrogen", 8.71604938);
    // testSystem.addComponent("oxygen", 22.71604938);
    testSystem2.addComponent("MEG", 110.234567901);
    testSystem2.createDatabase(true);
    testSystem2.setMixingRule(10);
    testSystem.addFluid(testSystem2);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    // testOps.TPflash();

    try {
      testOps.TPflash();
      // testOps.waterDewPointTemperatureMultiphaseFlash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    // testSystem.init(0);
    // testSystem.init(1);

    testSystem.display();
  }
}
