package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * FreezeMEGwater class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class FreezeMEGwater {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(FreezeMEGwater.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 10.0, 1.0);
    // SystemInterface testSystem = new SystemSrkCPAstatoil(273.15-23.0, 1.0);
    // testSystem.setMultiPhaseCheck(true);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.addComponent("methane", 1.1);
    // testSystem.addComponent("ethane", 15.0);
    testSystem.addComponent("MEG", 0.9);
    // testSystem.addComponent("methanol", 0.5);
    // testSystem.addComponent("n-heptane", 5.0);
    testSystem.addComponent("TEG", 0.1);
    // testSystem.addComponent("MEG", 1.0 - 0.1);

    // testSystem.setMultiPhaseCheck(true);

    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    testSystem.setSolidPhaseCheck("MEG");
    testSystem.init(0);
    try {
      // testOps.TPflash();
      testOps.freezingPointTemperatureFlash();
      // testOps.calcSolidComlexTemperature("TEG", "water");
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    testSystem.display();
    // System.out.println("temperature " + (testSystem.getTemperature() - 273.15));
    // System.out.println("act water " + testSystem.getPhase(1).getActivityCoefficient(1));
    // System.out.println("act MEG " + testSystem.getPhase(1).getActivityCoefficient(0));
    // try{
    // testOps.bubblePointPressureFlash(false);
    // }
    // catch(Exception ex){
    // System.out.println("error");
    // }
    // testSystem.display();
  }
}
