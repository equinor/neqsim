package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * HeatOfVaporization class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class HeatOfVaporization {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(HeatOfVaporization.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    SystemInterface testSystem = new SystemSrkCPAstatoil(288.15000000, 0.001);
    testSystem.addComponent("TEG", 1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    testSystem.init(0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.bubblePointPressureFlash(false);
      testSystem.display();
      double heatVap = testSystem.getHeatOfVaporization();
      logger.info("heat of vaporization " + heatVap + " J/mol");
      logger.info("heat of vaporization " + (heatVap / testSystem.getMolarMass()) + " J/kg");
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }
}
