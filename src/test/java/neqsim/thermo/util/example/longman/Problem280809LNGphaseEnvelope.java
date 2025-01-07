package neqsim.thermo.util.example.longman;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemGERG2004Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * Problem280809LNGphaseEnvelope class.
 * </p>
 *
 * @author lozhang
 * @version $Id: $Id
 * @since 2.2.3
 */
public class Problem280809LNGphaseEnvelope {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(Problem280809LNGphaseEnvelope.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    SystemInterface testSystem = new SystemGERG2004Eos(230, 50.00);
    testSystem.addComponent("methane", 0.80);
    testSystem.addComponent("ethane", 0.05);
    testSystem.addComponent("propane", 0.03);
    testSystem.addComponent("CO2", 0.06);
    testSystem.addComponent("nitrogen", 0.05);
    // testSystem.addComponent("benzene",0.01);

    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    try {
      testOps.calcPTphaseEnvelope(true); // 0.05, 0.000005);
      testOps.displayResult();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    testSystem.display();
    // System.out.println("tempeerature " + (testSystem.getTemperature() - 273.15));
    // testOps.displayResult();
    // System.out.println("Cricondenbar " + testOps.get("cricondenbar")[0] + " " +
    // testOps.get("cricondenbar")[1]);
  }
}
