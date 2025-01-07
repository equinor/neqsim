package neqsim.thermodynamicoperations.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * OLGApropGenerator class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public class OLGApropGenerator {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(OLGApropGenerator.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkEos(273.15, 10.0);
    testSystem.addComponent("nitrogen", 0.01848);
    testSystem.addComponent("CO2", 0.837478);
    testSystem.addComponent("methane", 2.135464);
    testSystem.addComponent("ethane", 0.6941);
    testSystem.addComponent("propane", 0.46402);
    testSystem.addComponent("i-butane", 0.302664);
    testSystem.addComponent("n-butane", 0.2696);
    testSystem.addComponent("i-pentane", 0.18108);
    testSystem.addComponent("n-pentane", 0.422286);
    testSystem.addTBPfraction("C6_PC", 0.01753, 86.178 / 1000.0, 0.66399);
    testSystem.addTBPfraction("C7_PC", 0.0231839, 96.0 / 1000.0, 0.738);
    testSystem.addTBPfraction("C8_PC", 0.006674, 107.0 / 1000.0, 0.8097);
    testSystem.addTBPfraction("C9_PC", 0.000660625, 120.99 / 1000.0, 0.8863);
    testSystem.addTBPfraction("C10_PC", 8.07355e-5, 144.178 / 1000.0, 0.8526);

    // testSystem.addComponent("water", 28.97100);
    // testSystem.addComponent("TEG",65.65524299);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);

    testSystem.setMultiPhaseCheck(true);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      // testSystem.setTemperature(380.0);
      // testSystem.setPressure(80.0);
      // testOps.TPflash();
      // testSystem.display();

      testSystem.setTemperature(273.15 + 20.85);
      testSystem.setPressure(13);
      testOps.TPflash();
      testSystem.display();

      String fileName = "c:/temp//OLGAneqsim.tab";
      testOps.OLGApropTable(273.15, 273.15 + 50.0, 40, 1.0, 220.0, 40, fileName, 0);
      testOps.displayResult();
    } catch (Exception ex) {
      testSystem.display();
      logger.error(ex.getMessage(), ex);
    }
  }
}
