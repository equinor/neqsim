package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TestPCSAFT class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestPCSAFT {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestPCSAFT.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @SuppressWarnings("unused")
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    // SystemInterface testSystem = new SystemSrkEos(273.14, 50.00);
    // SystemInterface testSystem = new SystemGERG2004Eos(273.14-55, 75.00);
    // SystemInterface testSystem = new SystemGERG2004Eos(260.0, 50.00);
    SystemInterface testSystem = new SystemSrkCPAstatoil(353, 10.00);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    // testSystem.addComponent("methane", 1.9894305);
    testSystem.addComponent("n-heptane", 10.006343739);
    // testSystem.addComponent("nC15", 0.004225765);
    // testSystem.addComponent("i-butane", 1.05);
    // testSystem.addComponent("n-butane", 1.465);
    // testSystem.addTBPfraction("C7", 0.1, 110.0, 0.7);
    // testSystem.addTBPfraction("C8", 1.1, 120.0, 0.75);
    // testSystem.addTBPfraction("C9", 0.1, 140.0, 0.77);
    // testSystem.addComponent("methanol", 10.0);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(1);
    testSystem.init(0);
    try {
      testOps.TPflash();
      // testOps.dewPointTemperatureFlash();
      ThermodynamicModelTest test = new ThermodynamicModelTest(testSystem);

      // test.runTest();
      // testOps.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    testSystem.display();
    double entropy = testSystem.getEntropy();
    testSystem.setPressure(testSystem.getPressure() + 50.0);
    // double oldEnthalpy = testSystem.getEnthalpy();
    // testSystem.setTemperature(testSystem.getTemperature() + 10.0);
    try {
      // testOps.PHflash(oldEnthalpy-10000, 0);
      testOps.PSflash(entropy);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    testSystem.display();
    // System.out.println("enthalpy " + (testSystem.getEnthalpy() - oldEnthalpy));
    logger.info("fugacity gas" + testSystem.getPhase(0).getFugacity(0));
    logger.info("fugacity liquid" + testSystem.getPhase(1).getFugacity(0));
    logger.info("K " + testSystem.getPhase(1).getComponent(0).getFugacityCoefficient()
        / testSystem.getPhase(0).getComponent(0).getFugacityCoefficient());
  }
}
