package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * BubbleFlash class.
 * </p>
 *
 * @author esol
 * @since 2.2.3
 * @version $Id: $Id
 */
public class BubbleFlash {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(BubbleFlash.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 25.0, 1.0);
    // SystemInterface testSystem = new SystemSrkEos(288, 26.9);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    testSystem.addComponent("PG", 0.05175);
    // testSystem.addComponent("n-butane", 0.5175);
    // testSystem.addComponent("TEG", 0.0000000225);

    // testSystem.addComponent("MEG", 30);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    // testSystem.setMixingRule("HV", "UNIFAC_PSRK");
    try {
      // testOps.dewPointPressureFlash();
      // testOps.bubblePointTemperatureFlash();
      testOps.TPflash();
      // testSystem.display();
      // testOps.constantPhaseFractionPressureFlash(1.0);
      testSystem.display();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    // System.out.println("wt% MEG " +
    // 100*testSystem.getPhase(1).getComponent("MEG").getx()*testSystem.getPhase(1).getComponent("MEG").getMolarMass()/testSystem.getPhase(1).getMolarMass());

    // testSystem.display();
  }
}
