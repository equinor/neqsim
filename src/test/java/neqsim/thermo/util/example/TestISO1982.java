/*
 * TestISO1982.java
 *
 * Created on 13. juni 2004, 23:49
 */

package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TestISO1982 class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestISO1982 {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestISO1982.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkEos(290.15, 30.00);

    testSystem.addComponent("methane", 50);
    testSystem.addComponent("ethane", 50);
    testSystem.addComponent("propane", 50);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();

    logger.info("ISO calc: " + testSystem.getStandard("ISO1982").getValue("Energy", "KJ/Sm3"));
  }
}
