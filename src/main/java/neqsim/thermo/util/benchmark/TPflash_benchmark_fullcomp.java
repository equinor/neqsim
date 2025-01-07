package neqsim.thermo.util.benchmark;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TPflash_benchmark_fullcomp class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class TPflash_benchmark_fullcomp {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TPflash_benchmark_fullcomp.class);

  /**
   * This method is just meant to test the thermo package.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @SuppressWarnings("unused")
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    double[][] points;

    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 - 5.0, 10.0);
    // SystemInterface testSystem = new SystemSrkSchwartzentruberEos(298.15,
    // ThermodynamicConstantsInterface.referencePressure);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    // testSystem.addComponent("CO2", 2.1);
    // testSystem.addComponent("nitrogen", 1.16);
    testSystem.addComponent("methane", 26.19);
    testSystem.addComponent("propane", 8.27);

    testSystem.addComponent("propane", 7.5);
    testSystem.addComponent("i-butane", 1.83);
    testSystem.addComponent("n-butane", 4.05);
    testSystem.addComponent("iC5", 1.85);
    testSystem.addComponent("n-pentane", 2.45);
    testSystem.addComponent("n-hexane", 40.6);

    testSystem.addTBPfraction("C6", 1.49985, 86.3 / 1000.0, 0.7232);
    testSystem.addTBPfraction("C7", 0.0359864, 96.0 / 1000.0, 0.738);
    testSystem.addTBPfraction("C8", 0.939906, 107.0 / 1000.0, 0.765);
    testSystem.addTBPfraction("C9", 0.879912, 121.0 / 1000.0, 0.781);
    testSystem.addTBPfraction("C10", 0.45, 134.0 / 1000.0, 0.792);

    // testSystem.addComponent("methanol", 1.0);
    // testSystem.addComponent("MEG", 11.0);
    // testSystem.addComponent("water", 84.35);
    // testSystem.addComponent("methanol", 15.65);
    testSystem.setMultiPhaseCheck(true);
    testSystem.setHydrateCheck(true);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(9);
    logger.info("start benchmark TPflash......");
    long time = System.currentTimeMillis();
    for (int i = 0; i < 1000000; i++) {
      testOps.TPflash();
      try {
        // testOps.hydrateFormationTemperature();
        // testOps.calcTOLHydrateFormationTemperature();
      } catch (Exception ex) {
        logger.error(ex.getMessage(), ex);
      }
    }
    logger.info("Time taken for benchmark flash = " + (System.currentTimeMillis() - time));
    testOps.displayResult();

    // time for 5000 flash calculations
    // Results Dell Portable PIII 750 MHz - JDK 1.3.1:
    // mixrule 1 (Classic - no interaction): 6.719 sec
    // mixrule 2 (Classic): 6.029 sec ny PC 1.498 sec
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
