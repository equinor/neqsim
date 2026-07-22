package neqsim.physicalproperties.util.examples;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * TPflashWater class.
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TPflashWater {
  private static final Logger logger = LogManager.getLogger(TPflashWater.class);

  /** Logger object for class. */

  /**
   * main.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 40.0, 100.01325);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    // testSystem.addComponent("methane", 0.3);
    // testSystem.addComponent("n-heptane", 0.000071);
    // testSystem.addComponent("water", 0.02, "kg/sec");
    // testSystem.addComponent("water", 0.97);
    testSystem.addComponent("TEG", 0.103);

    // testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    // testSystem.setMultiPhaseCheck(true);

    try {
      testOps.TPflash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    testSystem.initPhysicalProperties();
    // logger.info("viscosity " + testSystem.getViscosity());
    logger.info("viscosity " + testSystem.getPhase("aqueous").getViscosity());
    testSystem.display();
    // logger.info("surftens 0-2 " +
    // testSystem.getInterphaseProperties().getSurfaceTension(0,2));
  }
}
