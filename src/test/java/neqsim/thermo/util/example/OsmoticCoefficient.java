package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * OsmoticCoefficient class.
 *
 * @author esol
 * @since 2.2.3
 * @version $Id: $Id
 */
public class OsmoticCoefficient {
  private static final Logger logger = LogManager.getLogger(OsmoticCoefficient.class);

  /**
   * main.
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @SuppressWarnings("unused")
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemElectrolyteCPAstatoil(298.0,
        ThermodynamicConstantsInterface.referencePressure);
    // SystemInterface testSystem = new SystemElectrolyteCPA(298.15,1.01325201325);
    // SystemInterface testSystem = new
    // SystemSrkCPAs(298.15,ThermodynamicConstantsInterface.referencePressure);
    // SystemInterface testSystem = new
    // SystemSrkSchwartzentruberEos(298.15,ThermodynamicConstantsInterface.referencePressure);

    // creates a 0.1 molar solution 0.0018
    testSystem.addComponent("methane", 10.0);
    // testSystem.addComponent("CO2", 0.001);

    testSystem.addComponent("water", 1000.0 / 18.02);

    testSystem.addComponent("Ca++", 2.0);
    testSystem.addComponent("Cl-", 2.0 * 2);

    testSystem.chemicalReactionInit();
    testSystem.createDatabase(true);
    testSystem.setMixingRule(7);
    testSystem.init(0);
    testSystem.init(1);

    // logger.info("furst 1 "
    // +((PhaseModifiedFurstElectrolyteEos)testSystem.getPhase(0)).reInitFurstParam();
    // // logger.info("volume " + testSystem.getPhase(1).getMolarVolume());
    double osmCoef = testSystem.getPhase(1).getOsmoticCoefficientOfWater();
    // logger.info("osmotic coefficient: " + osmCoef);

    // ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    // try{
    // // testOps.TPflash();
    // // testOps.calcSaltSaturation("NaCl");
    // } catch(Exception ex){
    // }
    testSystem.display();

    // logger.info("wt% water " + testSystem.getPhase(1).getWtFrac(0)*100);
    // double meanact = testSystem.getPhase(1).getMeanIonicActivity(2,3);
    // double meanact2 = testSystem.getPhase(1).getActivityCoefficient(3,1);
    // // testSystem.getPhase(1).getActivityCoefficient(3);
    // // testSystem.getPhase(1).getActivityCoefficient(3);
    // logger.info("mean ionic activity: " + meanact);
    // logger.info("Na+ ionic activity: " + meanact2);

    // thermo.ThermodynamicModelTest testModel = new
    // thermo.ThermodynamicModelTest(testSystem);
    // testModel.runTest();
  }
}
