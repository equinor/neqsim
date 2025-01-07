package neqsim.physicalproperties.util.examples;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TPflashTest class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TPflashTest {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TPflashTest.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @SuppressWarnings("unused")
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 20, 30.0);
    // SystemInterface testSystem = new SystemSrkCPAstatoil(273.15+20, 1.0);
    // SystemInterface testSystem = new SystemPrEos1978(273.15+10, 12.0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    // testSystem.addComponent("TEG", 10.71);
    testSystem.addComponent("methane", 14.01);
    // testSystem.changeComponentName("methane", "methaneHYME");
    // testSystem.addComponent("ethane", 0.01);
    // testSystem.addComponent("nitrogen", 1.1);
    // testSystem.addComponent("n-heptane", 51);
    // testSystem.addComponent("ethane", 8.53);
    // testSystem.addComponent("methane", 82.7);
    // testSystem.addComponent("ethane", 7.5);
    // testSystem.addComponent("propane", 3.4);
    // testSystem.addComponent("i-butane", 0.5);
    // testSystem.addComponent("n-butane", 0.9);
    // testSystem.addComponent("i-pentane", 10.24);
    // testSystem.addComponent("water", 0.4);
    testSystem.addComponent("MEG", 99.5); // , "kg/sec");
    testSystem.addComponent("water", 0.5); // , "kg/sec");
    // testSystem.addTBPfraction("C7", 10.36, 110.0 / 1000.0, 0.82);
    // testSystem.addTBPfraction("C10", 5.31, 150.0 / 1000.0, 0.89);
    // testSystem.addComponent("water", 1.1);
    /*
     * testSystem.addComponent("i-butane", 1.95); testSystem.addComponent("n-butane", 1.95);
     * testSystem.addComponent("n-pentane", 0.95); testSystem.addComponent("i-pentane", 0.95);
     *
     * testSystem.addTBPfraction("C6",0.10,100.0/1000.0,0.8);
     * testSystem.addTBPfraction("C7",0.060,110.0/1000.0,0.82);
     * testSystem.addTBPfraction("C8",0.00453,120.0/1000.0,0.83);
     * testSystem.addTBPfraction("C9",0.0031,130.0/1000.0,0.85);
     *
     * // testSystem.addPlusFraction("C10+", 0.008201, 142.0/1000, 0.9);
     *
     * // testSystem.getCharacterization().characterisePlusFraction();
     */
    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    // testSystem.setMultiPhaseCheck(true);
    // testSystem.setPhysicalPropertyModel(PhysicalPropertyType.BASIC);
    // testSystem.initPhysicalProperties();
    // testSystem.getInterphaseProperties().setInterfacialTensionModel(3);
    // testSystem.useVolumeCorrection(true);
    try {
      testOps.TPflash();
      // testSystem.tuneModel("viscosity",1.5e-4,0);
      // testOps.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    testSystem.initPhysicalProperties();

    try {
      testOps.TPflash();
      // testSystem.tuneModel("viscosity",1.5e-4,0);
      // testOps.bubblePointPressureFlash(false);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    testSystem.initPhysicalProperties();
    // double a =
    // testSystem.getPhase("oil").getPhysicalProperties().getDiffusionCoefficient(0,
    // 1);
    // testSystem.setPhysicalPropertyModel(PhysicalPropertyType.BASIC);
    // testSystem.getPhase(0).initPhysicalProperties("viscosity");
    double visc =
        testSystem.getPhase(1).getPhysicalProperties().getViscosityModel().calcViscosity();
    testSystem.display();
  }
}
