package neqsim.thermodynamicoperations.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * TVflash class.
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TVflash {
  private static final Logger logger = LogManager.getLogger(TVflash.class);

  /** Logger object for class. */

  /**
   * main.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    // SystemInterface testSystem2 =
    // util.serialization.SerializationManager.open("c:/test.fluid");
    // testSystem2.display();
    SystemInterface testSystem = new SystemSrkEos(273.15 + 5, 1.0);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.addComponent("nitrogen", 90.0);
    // testSystem.addComponent("ethane", 4.0);
    testSystem.addComponent("oxygen", 10.2);
    testSystem.addComponent("water", 1.5);

    // 1500 m3 vann

    // testSystem.addComponent("water", 1.0);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    // testSystem.setMultiPhaseCheck(true);
    testSystem.init(0);
    try {
      testOps.TPflash();
      testSystem.display();
      testSystem.setTemperature(273.15 + 55.1);
      testOps.TVflash(testSystem.getVolume());
      testSystem.display();
      // testOps.PVrefluxFlash(0.05, 1);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }
}
