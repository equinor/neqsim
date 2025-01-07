package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * PhaseEnvelope2 class.
 * </p>
 *
 * @author evensolbraa
 * @version $Id: $Id
 * @since 2.2.3
 */
public class PhaseEnvelope2 {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PhaseEnvelope2.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    SystemInterface testSystem = new SystemSrkEos(280.0, 1.00);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    testSystem.addComponent("methane", 90.0);
    testSystem.addComponent("propane", 3.0);
    testSystem.addComponent("i-butane", 1.8);
    testSystem.addComponent("n-butane", 1.433);
    testSystem.addComponent("n-hexane", 1.433);
    testSystem.setMixingRule(2);
    try {
      testOps.calcPTphaseEnvelope();
      testOps.displayResult();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }
}
