package neqsim.thermo.util.benchmark;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TPflash_benchmark class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 */
public class TPflash_benchmark {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TPflash_benchmark.class);

  /**
   * This method is just meant to test the thermo package.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkEos(303.15, 35.01325);
    // SystemInterface testSystem = new SystemSrkCPAstatoil(303.15, 10.0);
    // SystemInterface testSystem = new SystemUMRPRUMCEos(303.0, 10.0);
    // SystemInterface testSystem = new SystemSrkSchwartzentruberEos(298.15,
    // ThermodynamicConstantsInterface.referencePressure);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    testSystem.addComponent("nitrogen", 0.0028941);
    testSystem.addComponent("CO2", 0.054069291);
    testSystem.addComponent("methane", 0.730570915);
    testSystem.addComponent("ethane", 0.109004002);
    testSystem.addComponent("propane", 0.061518891);
    testSystem.addComponent("n-butane", 0.0164998);
    testSystem.addComponent("i-butane", 0.006585);
    testSystem.addComponent("n-pentane", 0.005953);
    testSystem.addComponent("i-pentane", 0.0040184);
    testSystem.addTBPfraction("C6", 0.6178399, 86.17801 / 1000.0, 0.6639999);
    testSystem.addComponent("water", 0.27082);
    // testSystem.addComponent("TEG", 1.0);
    // testSystem.addTBPfraction("C7",1.0,250.0/1000.0,0.9);

    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    testSystem.setMultiPhaseCheck(true);

    // testSystem.autoSelectMixingRule();
    // testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");
    logger.info("start benchmark TPflash......");

    testSystem.init(0);
    long time = System.currentTimeMillis();

    for (int i = 0; i < 5000; i++) {
      // testSystem.init(3, 0);
      testOps.TPflash();
      // testSystem.initPhysicalProperties();
      // testSystem.init(0);
      // testSystem.init(1);
    }

    System.out.println("Time taken for benchmark flash = " + (System.currentTimeMillis() - time));
    // testSystem.display();
    // SystemInterface testSystem2 =
    // testSystem.readObjectFromFile("c:/temp/test2.neqsim", "test2.neqsim");
    // testSystem2.init(3);
    // testSystem2.display();
    // testSystem2.init(0);
    // testSystem2.init(3);
    // time for 5000 flash calculations
    // Results Dell Portable PIII 750 MHz - JDK 1.3.1:
    // mixrule 1 (Classic - no interaction): 6.719 sec
    // mixrule 2 (Classic): 6.029 sec ny PC 1.108 sec
    // mixrule 4 (Huron-Vidal2): 17.545 sec
    // mixrule 6 (Wong-Sandler): 12.859 sec
    // test of ijAlgo matrix - before 4134 ms / 3962 ms
    // // system:
    // SystemSrkEos testSystem = new SystemSrkEos(303.15, 10.01325);
    // ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    // testSystem.addComponent("methane", 100.0);
    // testSystem.addComponent("water", 100.0);
    // testSystem.setMixingRule(1);
  }
}
