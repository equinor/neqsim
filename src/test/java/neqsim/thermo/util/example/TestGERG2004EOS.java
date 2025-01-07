package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemGERG2004Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TestGERG2004EOS class.
 * </p>
 *
 * @author esol
 * @since 2.2.3
 * @version $Id: $Id
 */
public class TestGERG2004EOS {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestGERG2004EOS.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemGERG2004Eos(29.74536 + 273.15, 90.66201);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.addComponent("nitrogen", 0.795203479);
    testSystem.addComponent("CO2", 1.729916572);
    testSystem.addComponent("methane", 92.03665177);
    testSystem.addComponent("ethane", 4.819934221);
    testSystem.addComponent("propane", 0.533168289);
    testSystem.addComponent("i-butane", 0.013320409);
    testSystem.addComponent("n-butane", 0.048501779);
    // testSystem.addComponent("n-butane", 0.07218);
    // testSystem.addComponent("i-butane", 0.003749);
    // testSystem.addComponent("n-pentane", 0.001920);
    // testSystem.addComponent("iC5", 0.001850);
    // testSystem.addComponent("n-hexane", 0.001160);
    // testSystem.addComponent("n-heptane", 0.000460);
    // testSystem.addComponent("n-octane", 0.000225);
    // testSystem.addComponent("hydrogen", 0.1);
    // testSystem.addComponent("oxygen", 0.1);
    // testSystem.addComponent("CO", 0.1);
    // testSystem.addComponent("water", 0.000800);
    // testSystem.addComponent("helium", 0.1);
    // testSystem.addComponent("argon", 1.0);

    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    // testSystem.setSolidPhaseCheck("CO2");
    testSystem.init(0);
    testSystem.init(1);

    try {
      testOps.TPflash();
      // testOps.bubblePointTemperatureFlash();
      // testOps.freezingPointTemperatureFlash();
      testSystem.display();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    // standards.gasQuality.Draft_GERG2004 temp2 = new
    // standards.gasQuality.Draft_GERG2004(testSystem);
    // temp2.calculate();
  }
}
