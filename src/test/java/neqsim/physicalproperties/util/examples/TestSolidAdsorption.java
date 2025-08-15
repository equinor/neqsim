package neqsim.physicalproperties.util.examples;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TestSolidAdsorption class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestSolidAdsorption {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestSolidAdsorption.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkEos(288.15, 1.4);
    testSystem.addComponent("methane", 1.0);
    testSystem.addComponent("CO2", 0.1);
    testSystem.addComponent("n-heptane", 0.1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.TPflash();
      testSystem.display();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    testSystem.getInterphaseProperties().initAdsorption();
    testSystem.getInterphaseProperties().setSolidAdsorbentMaterial("AC"); // AC Norit R1
    testSystem.getInterphaseProperties().calcAdsorption();
    // testSystem.initPhysicalProperties();
    System.out.println("surface excess CO2 from gas "
        + testSystem.getInterphaseProperties().getAdsorptionCalc("gas").getSurfaceExcess("CO2")
        + " kg CO2/kg AC");
    System.out.println("surface excess CO2 from oil "
        + testSystem.getInterphaseProperties().getAdsorptionCalc("oil").getSurfaceExcess("CO2")
        + " kg CO2/kg AC");
  }
}
