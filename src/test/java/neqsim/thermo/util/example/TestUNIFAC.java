package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPsrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TestUNIFAC class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestUNIFAC {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestUNIFAC.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemPsrkEos(273.15 + 120.0, 0.15);
    // SystemInterface testSystem = new SystemSrkSchwartzentruberEos(273.15 + 25.0,
    // 1.01325301325);
    // SystemInterface testSystem = new SystemNRTL(273.15 + 174.0,1.301325);
    // SystemInterface testSystem = new SystemPsrkEos(273.15 + 74.0,1.301325);
    // SystemInterface testSystem = new SystemSrkEos(143.15,1.301325);
    // SystemInterface testSystem = new SystemSrkSchwartzentruberEos(193.15
    // ,10.301325);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    // testSystem.addComponent("acetone", 100.0);
    // testSystem.addComponent("n-pentane", 100.00047);
    // testSystem.addComponent("c-C6", 90.0);
    // testSystem.addComponent("methane", 10.0);
    // testSystem.addComponent("ethane", 10.0);
    testSystem.addComponent("TEG", 0.05);
    testSystem.addComponent("MDEA", 0.95);
    // testSystem.addComponent("water", 10.0);
    testSystem.createDatabase(true);
    // testSystem.setMixingRule(4);
    testSystem.setMixingRule("HV", "UNIFAC_PSRK");
    testSystem.init(0);
    testSystem.init(1);
    logger.info(testSystem.getPhase(1).getActivityCoefficient(0));
    logger.info("gibbs " + testSystem.getPhase(1).getExcessGibbsEnergy());
    try {
      // testOps.bubblePointPressureFlash(false);
      testOps.dewPointPressureFlash();
      // testOps.bubblePointTemperatureFlash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    testSystem.display();
    logger.info(testSystem.getPhase(1).getActivityCoefficient(0));
    logger.info("gibbs " + testSystem.getPhase(1).getGibbsEnergy());
  }
}
