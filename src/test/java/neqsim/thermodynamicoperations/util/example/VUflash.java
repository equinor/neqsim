package neqsim.thermodynamicoperations.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * VUflash class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class VUflash {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(VUflash.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkEos(273.15 + 15, 10.01325);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.addComponent("methane", 11.0);
    // testSystem.addComponent("ethane", 4.0);
    // testSystem.addComponent("n-heptane", 10.5);
    testSystem.addComponent("water", 10.5);
    // testSystem.addComponent("TEG", 0.000000);
    // testSystem.setMultiPhaseCheck(true);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.setMultiPhaseCheck(true);

    testSystem.init(0);
    testSystem.display();
    try {
      testOps.TPflash();
      testSystem.display();

      logger.info("Volume " + testSystem.getVolume() * 1.1 + " internalEnergy "
          + testSystem.getInternalEnergy());
      // testSystem.setPressure(5);
      // testOps.PHflash(testSystem.getEnthalpy(), 0);
      testOps.VUflash(testSystem.getVolume() * 1.1, testSystem.getInternalEnergy());
      logger.info(
          "Volume " + testSystem.getVolume() + " internalEnergy " + testSystem.getInternalEnergy());

      testSystem.display();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }
}
