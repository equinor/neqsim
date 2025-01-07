package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TestFluidIssues class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestFluidIssues {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestFluidIssues.class);

  /**
   * This method is just meant to test the thermo package.
   *
   * @param args an array of {@link java.lang.String} objects
   */

  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    // SystemInterface testSystem = new SystemSrkEos(303.15, 10.01325);
    SystemInterface testSystem = new SystemSrkCPAstatoil(303.15, 15.0);
    // SystemInterface testSystem = new SystemSrkSchwartzentruberEos(298.15,
    // ThermodynamicConstantsInterface.referencePressure);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.addComponent("methane", 100.0);
    // testSystem.addComponent("water", 100.0);
    // testSystem.addComponent("TEG", 100.0);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    logger.info("start benchmark TPflash......");

    long time = System.currentTimeMillis();
    testOps.TPflash();
    for (int i = 0; i < 5; i++) {
      testOps.TPflash();
    }
    // Trying to see what happens if an extra component is added
    testSystem.init(0);
    testSystem.addComponent("water", 100.0);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    testOps.TPflash();

    logger.info("Time taken for benchmark flash = " + (System.currentTimeMillis() - time));
    testOps.displayResult();

    // time for 5000 flash calculations
    // Results Dell Portable PIII 750 MHz - JDK 1.3.1:
    // mixrule 1 (Classic - no interaction): 6.719 sec
    // mixrule 2 (Classic): 6.029 sec ny PC 1.108 sec
    // mixrule 4 (Huron-Vidal2): 17.545 sec
    // mixrule 6 (Wong-Sandler): 12.859 sec

    // // system:
    // SystemSrkEos testSystem = new SystemSrkEos(303.15, 10.01325);
    // ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    // testSystem.addComponent("methane", 100.0);
    // testSystem.addComponent("water", 100.0);
    // testSystem.setMixingRule(1);
  }
}
