package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TestUMRPRU class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestUMRPRU {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestUMRPRU.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    // SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 - 3.5, 33.0);
    // SystemInterface testSystem = new SystemUMRPRUMCEos(273.15 + 20.5, 70.0);
    // SystemInterface testSystem = new SystemPsrkEos(273.15 - 133.85, 8.557);
    SystemInterface testSystem = new SystemUMRPRUMCEos(273.15 + 15, 10.0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    // testSystem.addComponent("nitrogen", 1);
    // testSystem.addComponent("CO2", 1);
    testSystem.addComponent("methane", 80);
    // testSystem.addComponent("ethane", 15);
    // testSystem.addComponent("propane", 1);
    // testSystem.addComponent("i-butane", 0.1);
    // testSystem.addComponent("n-butane", 0.1);
    // testSystem.addComponent("i-pentane", 0.05);
    // testSystem.addComponent("n-pentane", 0.05);
    testSystem.addComponent("n-heptane", 22.05);

    testSystem.addTBPfraction("C7", .0010, 85.5 / 1000.0, 0.66533);
    testSystem.addTBPfraction("C8", .0010, 91.06 / 1000.0, 0.74433);
    testSystem.addTBPfraction("C9", .0010, 103.61 / 1000.0, 0.76833);
    testSystem.addTBPfraction("C10", 0.001, 117.2 / 1000.0, 0.785);
    testSystem.addTBPfraction("C11", 0.001, 155.04 / 1000.0, 0.800);
    testSystem.addTBPfraction("C12", .01, 249.98 / 1000.0, 0.84566);
    testSystem.addTBPfraction("C13", .01, 458.76 / 1000.0, 0.89767);

    testSystem.addComponent("water", 10.1);
    // testSystem.addComponent("MEG", 10.1);
    // testSystem.addComponent("TEG", 10.1);
    // testSystem.addTBPfraction("C10", 0.1, 117.2 / 1000.0, 0.785);
    testSystem.createDatabase(true);
    // testSystem.setMixingRule(2);

    testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");
    testSystem.setMultiPhaseCheck(true);
    // testSystem.setMultiPhaseCheck(true);
    // testSystem.setMixingRule("HV", "UNIFAC_PSRK");
    // testSystem.setMixingRule(2);
    // testSystem.setBmixType(1);
    testSystem.init(0);
    // testSystem.saveObject(2043);
    // testSystem.init(3,1);
    try {
      // testOps.dewPointTemperatureFlash();
      for (int i = 0; i < 1; i++) {
        testOps.TPflash();
      }
      // testSystem.display();
      // double enthalpy = testSystem.getEnthalpy();
      // testSystem.setPressure(20.0);
      // testOps.PHflash(enthalpy, 0);
      // testSystem.display();
      // testOps.setRunAsThread(true);
      // testOps.calcPTphaseEnvelope(true); //true);
      // boolean isFinished = testOps.waitAndCheckForFinishedCalculation(10000);

      // testOps.displayResult();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    // testSystem.saveObject(880);
    // testSystem.saveFluid(30, "Norne");
    testSystem.display();
    testSystem.init(3);
    double cp = testSystem.getPhase(1).getCp();

    testSystem.setTemperature(testSystem.getTemperature() + 0.001);
    testSystem.init(3);
    double ent1 = testSystem.getPhase(1).getEnthalpy();

    testSystem.setTemperature(testSystem.getTemperature() - 0.002);
    testSystem.init(3);
    double ent2 = testSystem.getPhase(1).getEnthalpy();

    double numCp = (ent1 - ent2) / 0.002;

    // System.out.println("Cp " + cp + " numCp " + numCp);
    // System.out.println("entropy " + testSystem.getPhase(1).getEntropy());

    // thermo.ThermodynamicModelTest testModel = new
    // thermo.ThermodynamicModelTest(testSystem);
    // testModel.runTest();
    // testSystem.display();
  }
}
