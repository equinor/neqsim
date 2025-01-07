package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TestElectrolyteCPAstatoil class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestElectrolyteCPAstatoil {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestElectrolyteCPAstatoil.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    // SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 4.0, 100);
    // SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 10.0, 3.0);
    // SystemInterface testSystem = new
    // SystemFurstElectrolyteEosMod2004(273.15+40.0, 33.0);
    SystemInterface testSystem = new SystemElectrolyteCPAstatoil(273.15 + 40.0, 33.0);

    // SystemInterface testSystem = new SystemSrkCPAstatoil(273.15+10.0, 3.0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.addComponent("methane", 111.0);
    testSystem.addComponent("CO2", 119.0);
    // testSystem.addComponent("H2S", 0.01001);
    // testSystem.addComponent("nC10", 11.0);
    testSystem.addComponent("water", 18.02 * 1000);
    // testSystem.addComponent("MDEA", 0.01);
    testSystem.addComponent("Na+", 40);
    testSystem.addComponent("CL-", 40);
    // testSystem.addComponent("OH-", 10.0e-3);

    // testSystem.addComponent("Fe++", 3.0e-4);
    // testSystem.addComponent("Ca++", 1.0e-4);
    testSystem.chemicalReactionInit();
    testSystem.createDatabase(true);
    testSystem.setMixingRule(10);
    // testSystem.setMixingRule(4);
    // testSystem.setHydrateCheck(true);
    testSystem.setMultiPhaseCheck(true);
    try {
      // testOps.hydrateFormationTemperature(1);
      testOps.TPflash();
      testSystem.display();
      // testOps.checkScalePotential(1);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    logger.info("pH" + testSystem.getPhase(1).getpH());
    // System.out.println("Mean ionic activity coefficient Na+Cl- " +
    // testSystem.getPhase(1).getMeanIonicActivity(2, 3));
    // System.out.println("Osmotic coefficient " +
    // testSystem.getPhase(1).getOsmoticCoefficientOfWater());
    logger.info("water activity coefficient " + testSystem.getPhase(1).getActivityCoefficient(1));
    logger
        .info("water activity coefficient " + testSystem.getPhase(1).getActivityCoefficient(1, 2));
  }
}
