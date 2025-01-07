package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAs;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TestMEGFlash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestMEGFlash {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestMEGFlash.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkCPAs(273.15 + 20, 10.0);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    // testSystem.addComponent("CO2", 0.02);
    // testSystem.addComponent("H2S", 0.00666);
    // testSystem.addComponent("nitrogen", 0.03412);
    testSystem.addComponent("nitrogen", 7.37e-6);
    // testSystem.addComponent("ethane", 0.053);
    // testSystem.addComponent("propane", 0.021);
    // testSystem.addComponent("i-butane", 0.00442);
    // testSystem.addComponent("n-butane", 0.007);
    // testSystem.addComponent("iC5", 0.003);
    // testSystem.addComponent("n-pentane", 0.003);
    // testSystem.addComponent("n-hexane", 0.003);
    // testSystem.addComponent("benzene", 1.6e-4);
    // testSystem.addComponent("c-C6", 3.24e-4);
    // testSystem.addComponent("n-heptane", 0.002961);
    // testSystem.addComponent("toluene", 2.21e-4);
    // testSystem.addComponent("n-octane", 0.002906);
    // testSystem.addComponent("p-Xylene", 0.000995);
    // testSystem.addComponent("n-nonane", 0.002193);
    // testSystem.addComponent("nC10", 0.001616);
    // testSystem.addComponent("nC12", 0.003254);

    // testSystem.addComponent("MEG", 1.17/3.0*0.0453);
    testSystem.addComponent("water", 1.0);

    // testSystem.setSolidPhaseCheck("water");
    // testSystem.setHydrateCheck(true);

    // testSystem.createDatabase(true);
    testSystem.setMixingRule(7);
    // testSystem.setMultiPhaseCheck(true);
    try {
      testOps.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    testSystem.display();

    // int n = testSystem.getNumberOfPhases()-1;
    // double megwtfracInit =
    // testSystem.getPhase(n).getComponent("MEG").getMolarMass()*testSystem.getPhase(n).getComponent("MEG").getx()/testSystem.getPhase(n).getMolarMass();
    // System.out.println("wt % MEG " + megwtfracInit*100);
    // n = testSystem.getNumberOfPhases()-2;
    // megwtfracInit =
    // testSystem.getPhase(n).getComponent("MEG").getMolarMass()*testSystem.getPhase(n).getComponent("MEG").getx()/testSystem.getPhase(n).getMolarMass();
    // System.out.println("wt % MEG " + megwtfracInit*100);
  }
}
