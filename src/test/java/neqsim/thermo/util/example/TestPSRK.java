package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPsrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TestPSRK class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestPSRK {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestPSRK.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    // SystemInterface testSystem = new SystemSrkEos(325.8, 10.0);
    // SystemInterface testSystem = new SystemCSPsrkEos(245.8, 70.0);
    // SystemInterface testSystem = new SystemSrkEos(265.8, 20.0);
    SystemInterface testSystem = new SystemPsrkEos(255.32, 5);
    // SystemInterface testSystem = new SystemPrEos(240,50.0301325);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    // testSystem.addComponent("CO2", 84.29);
    // testSystem.addComponent("MDEA", 10.09);
    testSystem.addComponent("H2S", 0.99);
    testSystem.addComponent("methane", 0.01);
    // testSystem.addComponent("ethane", 0.02972);
    // testSystem.addComponent("propane", 1.01008);
    // testSystem.addComponent("i-butane", 0.0105);
    // testSystem.addComponent("n-butane", 1.01465);
    // testSystem.addComponent("nC10", 1.01465);

    // testSystem.addComponent("nitrogen", 10.2);
    // testSystem.addComponent("nitrogen", 10.01);
    // testSystem.addTBPfraction("C7", 1.01,100.0, 0.98);
    // testSystem.addTBPfraction("C8", 1.01,120.0, 1.01);
    testSystem.createDatabase(true);
    // testSystem.setMixingRule(1);
    // testSystem.set
    testSystem.setMixingRule("HV", "UNIFAC_PSRK");
    // testSystem.setMixingRule("WS","UNIFAC_PSRK");
    testSystem.init(0);
    testSystem.init(2);

    // thermo.ThermodynamicModelTest testModel = new
    // thermo.ThermodynamicModelTest(testSystem);
    // testModel.runTest();

    try {
      testOps.dewPointPressureFlash(); // (false);
      // testOps.dewPointTemperatureFlash();
      // testOps.calcPTphaseEnvelope(0.0005, 0.0001); testOps.displayResult();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    testSystem.display();
    logger.info(testSystem.getTemperature() - 273.15);
  }
}
