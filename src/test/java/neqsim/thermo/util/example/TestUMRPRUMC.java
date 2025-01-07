package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TestUMRPRUMC class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestUMRPRUMC {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestUMRPRUMC.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    // SystemInterface testSystem = new SystemUMRPRUMCEos(273.15 + 20, 10.0);
    SystemInterface testSystem = new SystemSrkEos(273.15 + 20, 15.0);
    // SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 20, 1.0);
    // testSystem.getCharacterization().setTBPModel("PedersenPR"); //(RiaziDaubert
    // PedersenPR PedersenSRK
    // testSystem.addComponent("CO2", 0.1)
    testSystem.addComponent("nitrogen", 1.1472);
    testSystem.addComponent("CO2", 0.5339);
    testSystem.addComponent("methane", 95.2412);
    testSystem.addComponent("ethane", 2.2029);
    testSystem.addComponent("propane", 0.3231);
    testSystem.addComponent("i-butane", 0.1341);
    testSystem.addComponent("n-butane", 0.0827);
    // testSystem.addComponent("22-dim-C3", 10.0);
    // testSystem.addComponent("i-pentane", 0.0679);
    // testSystem.addComponent("n-pentane", 0.0350);
    // testSystem.addComponent("c-C5", 0.0291185);

    // testSystem.addComponent("22-dim-C4", 0.00297);
    // testSystem.addComponent("23-dim-C4", 0.00689);
    // testSystem.addComponent("2-m-C5", 0.0352);
    // testSystem.addComponent("3-m-C5", 0.0108);

    // testSystem.addComponent("2-m-C5", 10.0);
    // testSystem.addComponent("n-hexane", 0.0176);
    // testSystem.addComponent("c-hexane", 0.0720);
    // testSystem.addComponent("benzene", 0.0017);

    // testSystem.addComponent("n-heptane", 0.0128);
    // testSystem.addComponent("toluene", 0.0043);
    // testSystem.addComponent("c-C7", 0.0518);
    // testSystem.addComponent("n-octane", 0.0038);
    // testSystem.addComponent("m-Xylene", 0.0031);
    // testSystem.addComponent("c-C8", 0.0098);
    // testSystem.addComponent("n-nonane", 0.0034);
    // testSystem.addComponent("nC10", 0.0053);
    // testSystem.addComponent("nC11", 0.0004);
    /*
     * testSystem.addComponent("c-hexane", 10.0); testSystem.addComponent("c-hexane", 10.0);
     * testSystem.addComponent("223-TM-C4", 10.0); testSystem.addComponent("n-heptane", 10.0);
     * testSystem.addComponent("n-heptane", 10.0); testSystem.addComponent("M-cy-C6", 10.0);
     * testSystem.addComponent("toluene", 10.0); testSystem.addComponent("33-DM-C6", 10.0);
     * testSystem.addComponent("n-octane", 10.0); testSystem.addComponent("ethylcyclohexane", 10.0);
     * testSystem.addComponent("m-Xylene", 10.0); testSystem.addComponent("3-M-C8", 10.0);
     * testSystem.addComponent("n-nonane", 10.0); testSystem.addComponent("n-Bcychexane", 10.0);
     * testSystem.addComponent("Pent-CC6", 10.0); // testSystem.addComponent("methanol", 10.0);
     */
    // testSystem.addComponent("water", 10.0);
    // testSystem.addComponent("n-octane", 10.0);

    // testSystem.addTBPfraction("C8", 1.0, 100.0 / 1000.0, 0.8);
    // testSystem.addTBPfraction("LP_C17", 0.03, 238.779998779297 / 1000.0,
    // 0.84325);
    // testSystem.addComponent("ethane", 1.0);
    // testSystem.addComponent("water", 7.0);
    // testSystem.addComponent("CO2", 1.0e-10);
    // testSystem.addComponent("MEG", 3.0);
    // testSystem.addComponent("ethane", 0.375);
    // // testSystem.addComponent("ethane", 99.9);
    // testSystem.addComponent("nC27", 0.25);
    testSystem.createDatabase(true);
    // testSystem.setHydrateCheck(true);
    // testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");
    // testSystem.setMixingRule(2);
    // testSystem.setMultiPhaseCheck(true);
    testSystem.init(0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    // testSystem.setAttractiveTerm(13);
    try {
      testOps.TPflash();
      testSystem.display();
      // for (int i = 0; i < 1; i++) {
      // testOps.TPflash();
      // testSystem.init(3);
      // testOps.hydrateFormationTemperature();
      // testSystem.init(3);
      // }
      testOps.calcPTphaseEnvelope();
      testOps.displayResult();
      // testOps.bubblePointPressureFlash(false);
      // testOps.dewPointTemperatureFlash(false);
      // testOps.calcPTphaseEnvelope(false);

      // testOps.displayResult();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    // System.out.println("activity coefficient water " +
    // testSystem.getPhase(1).getActivityCoefficient(1));
    // testSystem.display();
    // testSystem.saveObjectToFile("C:\\Users\\esol\\AppData\\Roaming\\neqsim\\fluids\\testUMR.neqsim","");
    // testSystem.saveFluid(30);
    // testSystem.saveObject(2187);
  }
}
