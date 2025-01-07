package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPA;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * ModelTest class.
 * </p>
 *
 * @author esol
 * @since 2.2.3
 * @version $Id: $Id
 */
public class ModelTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ModelTest.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    // SystemInterface testSystem = new SystemFurstElectrolyteEos(280.15,10.00);

    // SystemInterface testSystem = new SystemSrkEos(500, 1.0);
    // SystemInterface testSystem = new SystemSrkCPAstatoil(273+150, 1.0);
    SystemInterface testSystem = new SystemSrkCPA(273 + 150, 70);
    // SystemInterface testSystem = new SystemSrkCPAs(273+150, 1.0);
    // SystemInterface testSystem = new SystemElectrolyteCPAstatoil(273.14 + 12,
    // 61.0);
    // SystemInterface testSystem = new SystemFurstElectrolyteEos(273.14 + 12,
    // 61.0);
    // SystemInterface testSystem = new SystemUMRPRUMCEos(300.0, 10.0);
    // SystemInterface testSystem = new SystemSrkEos(298.15,
    // ThermodynamicConstantsInterface.referencePressure);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    // testSystem.addComponent("methane", 100);
    // testSystem.addComponent("n-heptane", 1);
    testSystem.addComponent("water", 50);
    // testSystem.addComponent("methane", 10);
    // testSystem.addComponent("ethane", 15);
    testSystem.addComponent("TEG", 9);
    testSystem.addComponent("MEG", 50);
    // testSystem.addComponent("n-octane", 3.1);
    // testSystem.addComponent("Na+", 0.1);
    // testSystem.addComponent("Cl-", 0.1);
    // testSystem.addComponent("MEG", 2.1);
    testSystem.addComponent("methanol", 20);
    // testSystem.addComponent("MEG", 5.3);
    // testSystem.addComponent("MEG", 10.0);
    // testSystem.addTBPfraction("C8", 10.1, 90.0 / 1000.0, 0.8);
    // testSystem.addComponent("MEG", 10.5);
    // testSystem.createDatabase(true);
    // testSystem.useVolumeCorrection(true);
    testSystem.setMixingRule(10);
    // testSystem.setMixingRule("HV", "NRTL");
    // testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");
    // testSystem.setMixingRule(9);
    testSystem.init(0);
    testOps.TPflash();
    testSystem.init(3);

    neqsim.thermo.ThermodynamicModelTest testModel =
        new neqsim.thermo.ThermodynamicModelTest(testSystem);
    testModel.runTest();
    // testSystem.display();

    testSystem.init(3);
    double cp = testSystem.getPhase(1).getCp();

    testSystem.setTemperature(testSystem.getTemperature() + 0.001);
    testSystem.init(3);
    double ent1 = testSystem.getPhase(1).getEnthalpy();

    testSystem.setTemperature(testSystem.getTemperature() - 0.002);
    testSystem.init(3);
    double ent2 = testSystem.getPhase(1).getEnthalpy();
    // testSystem.saveFluid(3217);

    double numCp = (ent1 - ent2) / 0.002;

    logger.info("Cp " + cp + " numCp " + numCp);
  }
}
