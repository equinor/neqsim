package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPCSAFT;
import neqsim.util.ExcludeFromJacocoGeneratedReport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * CalcActivityFromPR class.
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class CalcActivityFromPR {
  private static final Logger logger = LogManager.getLogger(CalcActivityFromPR.class);

  /**
   * main.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    SystemInterface testSystem = new SystemPCSAFT(150.0, 10.0);
    testSystem.addComponent("methane", 1.0);
    testSystem.addComponent("n-hexane", 10.0001);
    testSystem.setMixingRule(1);
    testSystem.createDatabase(true);
    testSystem.init(0);
    testSystem.init(3);
    // logger.info("activity coefficient " +
    // testSystem.getPhase(1).getActivityCoefficient(1,1));
    testSystem.display();
  }
}
