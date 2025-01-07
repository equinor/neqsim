package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * HydrateFlash class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class HydrateFlash {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(HydrateFlash.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    // SystemInterface testSystem = new SystemSrkCPAstatoil(288.15, 10.450);
    SystemInterface testSystem = new SystemSrkEos(273.15 + 10.5, 51.0);
    // SystemInterface testSystem = new SystemUMRPRUEos(273.15 - 10.5, 5.0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    // testSystem.addComponent("CO2", 1.5);
    // testSystem.addComponent("H2S", 1.5);
    // testSystem.addComponent("nitrogen", 0);
    testSystem.addComponent("methane", 10.0);
    // testSystem.addComponent("ethane", 3.0);
    // testSystem.addComponent("propane", 1.0);
    /*
     * testSystem.addComponent("propane", 1.88); testSystem.addComponent("i-butane", 0.08);
     * testSystem.addComponent("n-butane", 0.12); testSystem.addComponent("n-pentane", 1.64);
     * testSystem.addComponent("n-heptane", 1.64);
     */

    // testSystem.addComponent("MEG", 1);

    testSystem.addTBPfraction("C6", 3.29, 86.178 / 1000.0, 0.6640);
    testSystem.addTBPfraction("C7", 2.22, 96.0 / 1000.0, 0.7380);
    testSystem.addTBPfraction("C8", 0.32, 107.0 / 1000.0, 0.7650);
    testSystem.addTBPfraction("C9", 0.177, 121.0 / 1000.0, 0.7810);
    // testSystem.addTBPfraction("C10", 1.22, 184.0 / 1000.0, 0.81020);
    // testSystem.addTBPfraction("C11", 4.93, 147.0 / 1000.0, 0.7960);
    // testSystem.addTBPfraction("C12", 3.84, 161.0 / 1000.0, 0.8100);
    // testSystem.addTBPfraction("C13", 2.74, 175.0 / 1000.0, 0.8250);
    testSystem.addTBPfraction("C14", 2.19, 290.0 / 1000.0, 0.8360);

    // testSystem.addTBPfraction("C15", 3.0, 291.0 / 1000.0, 0.85790282291);
    // testSystem.addTBPfraction("C15", 2.64, 206.0 / 1000.0, 0.8420);

    // testSystem.addComponent("TEG", 0.5);

    // testSystem.addComponent("CO2", 1.5);
    // testSystem.addComponent("MEG", 8.3);

    // testSystem.addComponent("NaCl", 12.4);
    // testSystem.addComponent("Na+", 6.2);
    // testSystem.addComponent("Cl-", 6.2);
    // testSystem.addComponent("MEG", 1.517);
    // testSystem.setSolidPhaseCheck("TEG");
    // testSystem.setSolidPhaseCheck("MEG");
    // testSystem.addSolidComplexPhase("MEG");
    // testSystem.addSolidComplexPhase("wax");
    testSystem.setHeavyTBPfractionAsPlusFraction();

    testSystem.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(8);
    testSystem.getCharacterization().characterisePlusFraction();

    // testSystem.addComponent("MEG", 1.015);
    testSystem.addComponent("water", 10.015);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    // testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");
    testSystem.setHydrateCheck(true);
    // testSystem.setMultiPhaseCheck(true);
    testSystem.init(0);
    // testSystem = testSystem.autoSelectModel();
    testOps = new ThermodynamicOperations(testSystem);
    // testSystem.setSolidPhaseCheck("water");

    try {
      // testOps.TPflash();
      testOps.hydrateFormationTemperature();
      // testOps.calcTOLHydrateFormationTemperature();
      // double[] temp = {288.15, 285.2, 283.5}; //, 277.3}; //, 285.15}; //, 284.0}; //,
      // 283.5, 283.0}; //, 280.15}; // , 268.15, 288.0, 274.6, 274.2, 273.7,
      // 273.15}; //, 297.8, 297.5};
      // double[] pres = {100.0, 100.0, 100.0, 100, 100, 100}; //, 100, 100,
      // 100,100,100};
      // testOps.calcImobilePhaseHydrateTemperature(temp, pres);
      // testOps.dewPointTemperatureFlash();
      // testSystem.init(0);
      // testSystem.setHydrateCheck(true);
      // testSystem.setMultiPhaseCheck(true);
      // System.out.println("temperature1 " + (testSystem.getTemperature() - 273.15));
      // testOps.setRunAsThread(true);

      // // testSystem.setSolidPhaseCheck("water");
      // testOps.freezingPointTemperatureFlash();
      // testSystem.setSolidPhaseCheck(false);
      // testSystem.setHydrateCheck(false);
      // testSystem.init(0);
      for (int i = 0; i < 1; i++) {
        // testOps.hydrateFormationTemperature();
      }
      // testOps.TPflash();
      // System.out.println("temperature2 " + (testSystem.getTemperature() - 273.15));
      // boolean isFinished = testOps.waitAndCheckForFinishedCalculation(1000000);
      // System.out.println("finished? " + isFinished);

      // testOps.freezingPointTemperatureFlash();
      // testOps.calcSolidComlexTemperature();
      // testOps.TPflash();
      // testOps.calcWAT();
      // testOps.TPSolidflash();
      // testOps.dewPointTemperatureFlash();
      // testOps.waterDewPointTemperatureFlash();
      // testOps.hydrateFormationTemperature(0);

      // testOps.TPflash();
      // testOps.dewPointTemperatureFlash();
      // testOps.freezingPointTemperatureFlash();
      // testOps.bubblePointPressureFlash(false);
      // testOps.hydrateFormationTemperature(1);
      // testOps.waterPrecipitationTemperature();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    testSystem.display();
    // System.out.println("temperature " + (testSystem.getTemperature() - 273.15));
    // System.out.println("activity coef water " +
    // testSystem.getPhase(1).getActivityCoefficientSymetric(testSystem.getPhase(1).getComponent("water").getComponentNumber()));

    // System.out.println("wt% TEG " + 100 *
    // testSystem.getPhase(1).getComponent("TEG").getx() *
    // testSystem.getPhase(1).getComponent("TEG").getMolarMass() /
    // (testSystem.getPhase(1).getComponent("TEG").getx() *
    // testSystem.getPhase(1).getComponent("TEG").getMolarMass() +
    // testSystem.getPhase(1).getComponent("water").getx() *
    // testSystem.getPhase(1).getComponent("water").getMolarMass()));
    // testSystem.display();
    // System.out.println("kg vann/MSm^3 gas " +
    // (testSystem.getPhase(0).getComponent("water").getx() *
    // testSystem.getPhase(0).getComponent("water").getMolarMass()
    // *ThermodynamicConstantsInterface.atm /
    // ThermodynamicConstantsInterface.R / 288.15) * 1.0e6);
    // System.out.println("activity coef water " +
    // testSystem.getPhase(1).getActivityCoefficientSymetric(1));
    // int n = testSystem.getNumberOfPhases()-1;
    // double megwtfrac =
    // testSystem.getPhase(n).getComponent("MEG").getMolarMass()*testSystem.getPhase(n).getComponent("MEG").getx()/testSystem.getPhase(n).getMolarMass();
    // System.out.println("wt % MEG " + megwtfrac*100);
    /*
     * SystemInterface testSystem2 = new SystemSrkCPAstatoil(273.0 - 12, 60.0);
     * testSystem2.addComponent("methane", 1.0 -
     * testSystem.getPhase(0).getComponent("water").getx()); testSystem2.addComponent("water",
     * testSystem.getPhase(0).getComponent("water").getx()); testSystem2.createDatabase(true);
     *
     * testSystem2.setMixingRule(7); testSystem2.init(0); testSystem2.init(1);
     * ThermodynamicOperations testOps2 = new ThermodynamicOperations(testSystem2); try {
     * testOps2.waterDewPointTemperatureFlash(); } catch (Exception ex) {
     * logger.error(ex.getMessage()) } testSystem2.display();
     */
  }
}
