package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * OsmoticCoefficient_1 class.
 * </p>
 *
 * @author esol
 * @since 2.2.3
 * @version $Id: $Id
 */
public class OsmoticCoefficient_1 {
  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @SuppressWarnings("unused")
  public static void main(String args[]) {
    SystemInterface testSystem = new SystemFurstElectrolyteEos(298.15, 1.01325);
    // SystemInterface testSystem = new SystemElectrolyteCPA(298.15,1.01325);
    // SystemInterface testSystem = new SystemSrkCPAs(298.15,1.01325);
    // SystemInterface testSystem = new
    // SystemSrkSchwartzentruberEos(298.15,1.01325);

    // creates a 0.1 molar solution 0.0018
    testSystem.addComponent("CO2", 0.1);
    testSystem.addComponent("water", 10.0);
    // testSystem.addComponent("HCO3-", 0.18018);
    // testSystem.addComponent("CO2", 0.0018018);
    // testSystem.addComponent("BrMinus", 0.018018);
    // testSystem.addComponent("H3Oplus", 0.00018018);
    testSystem.addComponent("MDEA", 1.0);

    testSystem.chemicalReactionInit();
    testSystem.createDatabase(true);

    testSystem.setMixingRule(7);
    testSystem.init(0);
    testSystem.init(3);
    // // System.out.println("volume " + testSystem.getPhase(1).getMolarVolume());
    // double meanact2 = testSystem.getPhase(1).getActivityCoefficient(2);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    try {
      testOps.bubblePointPressureFlash(false);
      // testOps.calcSaltSaturation("PbCl2");
    } catch (Exception ex) {
    }
    testSystem.display();

    double osmCoef = testSystem.getPhase(1).getOsmoticCoefficientOfWater();
    double meanact = testSystem.getPhase(1).getMeanIonicActivity(2, 3);
    double meanact2 = testSystem.getPhase(1).getActivityCoefficient(4, 1);
    // testSystem.getPhase(1).getActivityCoefficient(3);
    // testSystem.getPhase(1).getActivityCoefficient(3);
    // System.out.println("mean ionic activity: " + meanact);
    // System.out.println("Na+ ionic activity: " + meanact2);
    // System.out.println("osmotic coefficient: " + osmCoef);

    // thermo.ThermodynamicModelTest testModel = new
    // thermo.ThermodynamicModelTest(testSystem);
    // testModel.runTest();
  }
}
