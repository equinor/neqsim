package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * LNGfreezingTestSolid1 class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class LNGfreezingTestSolid1 {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(LNGfreezingTestSolid1.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    // SystemInterface testSystem = new SystemUMRPRUMCEos(225.8488, 10.0);
    SystemInterface testSystem = new SystemSrkEos(245.0, 10.0);
    // testSystem.addComponent("nitrogen", 0.379);
    testSystem.addComponent("methane", 99.9);
    testSystem.addComponent("benzene", 0.1);
    // testSystem.addComponent("n-hexane", 2.0);
    // testSystem.addComponent("propane", 10);
    // testSystem.addComponent("benzene", 0.083);
    // testSystem.addComponent("ethane", 2.359);
    // testSystem.addComponent("propane", 3.1);
    // testSystem.addComponent("i-butane", 0.504);
    // testSystem.addComponent("n-butane", 0.85);
    // testSystem.addComponent("i-pentane", 0.323);
    // testSystem.addComponent("n-pentane", 0.231);
    // testSystem.addComponent("n-hexane", 0.173);

    // testSystem.addComponent("n-hexane", 0.01);

    // testSystem.addComponent("c-hexane", 0.0048);

    testSystem.createDatabase(true);
    // testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");
    testSystem.setMixingRule(2);
    testSystem.setSolidPhaseCheck("benzene");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    testSystem.getPhase(PhaseType.SOLID).getComponent("benzene").setHeatOfFusion(6000);
    try {
      // System.out.println("heat of fusion " +
      // testSystem.getPhase(PhaseType.SOLID).getComponent("benzene").getHeatOfFusion());

      testOps.TPSolidflash();
      // System.out.println("heat of fusion " +
      // testSystem.getPhase(PhaseType.SOLID).getComponent("benzene").getHeatOfFusion());
      // testOps.displayResult();
      // testOps.freezingPointTemperatureFlash();
      testSystem.display();
      // testOps.freezingPointTemperatureFlash();
      // testOps.TPflash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
  }
}
