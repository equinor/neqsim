package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TPMultiFlash class.
 * </p>
 *
 * @author esol
 * @since 2.2.3
 * @version $Id: $Id
 */
public class TPMultiFlash {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TPMultiFlash.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @SuppressWarnings("unused")
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    // SystemInterface testSystem = new SystemPrEos1978(273.15,100.0);
    // SystemInterface testSystem = new SystemSrkCPAstatoil(273.15,100.0);
    SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 40, 80.4);
    // SystemInterface testSystem = new SystemSrkMathiasCopeman(273.15+20., 1000.0);
    // SystemInterface testSystem = new SystemSrkSchwartzentruberEos(273.15+0.0,
    // 1.0);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testSystem.addComponent("CO2", 3.59);
    testSystem.addComponent("nitrogen", 0.34);

    testSystem.addComponent("methane", 167.42);
    /*
     * testSystem.addComponent("ethane", 29.02); testSystem.addComponent("propane", 14.31);
     * testSystem.addComponent("i-butane", 0.93); testSystem.addComponent("n-butane", 1.71);
     * testSystem.addComponent("i-pentane", 0.74); testSystem.addComponent("n-pentane", 0.85);
     * testSystem.addComponent("n-hexane", 1.38); testSystem.addTBPfraction("C7", 15.5, 59.00 /
     * 1000.0, 0.73); testSystem.addTBPfraction("C8", 5.69, 112.20 / 1000.0, 0.77);
     * testSystem.addTBPfraction("C9", 10.14, 87.5 / 1000.0, 0.755);
     * testSystem.addTBPfraction("C10", 0.8, 114.3 / 1000.0, 0.77);
     * testSystem.addPlusFraction("C11", 4.58, 236.2 / 1000.0, 0.8398);
     * testSystem.addPlusFraction("C11", 4.58, 266.2 / 1000.0, 0.8398);
     */
    // testSystem.addComponent("toluene", 0.009);
    // testSystem.addTBPfraction("C7",0.626, 96.00/1000.0, 0.738);
    // testSystem.addComponent("MDEA", 0.54);
    testSystem.addComponent("water", 0.01);
    // testSystem.addComponent("propane", 4.0062);
    // testSystem.addComponent("i-butane", 0.6 4205);
    // testSystem.addComponent("methane", 10.0);
    // testSystem.addComponent("ethane", 9.24);
    // testSystem.addComponent("propane", 2.57);
    // testSystem.addComponent("CO2", 5.19);
    // testSystem.addComponent("CO2", 30.4107);
    // testSystem.addComponent("MEG", 30.0);
    // testSystem.addComponent("MEG", 7.00);

    testSystem.setMultiPhaseCheck(true);

    // testSystem.setSolidPhaseCheck(true);
    // testSystem.setHydrateCheck(true);
    // testSystem.getCharacterization().characterisePlusFraction();
    testSystem.createDatabase(true);
    // testSystem.useVolumeCorrection(true);
    testSystem.setMixingRule(10);

    // System.out.println("activity water " +
    // testSystem.getPhase(1).getActivityCoefficient(2));
    // 1- orginal no interaction 2- classic w interaction
    // 3- Huron-Vidal 4- Wong-Sandler
    // testSystem.setMixingRule(1);

    try {
      int phase = 0;

      testSystem.init(0);
      testSystem.useVolumeCorrection(true);
      testSystem.setNumberOfPhases(1);
      testSystem.setMaxNumberOfPhases(1);

      for (int i = 0; i < 100000; i++) {
        double[] x = testSystem.getMolarComposition();
        testSystem.setMolarComposition(x);
        testSystem.init(0, 0);
        testSystem.setTemperature(298);
        testSystem.setPressure(10);
        if (phase == 1) {
          phase = 0;
        } else {
          phase = 1;
        }

        testSystem.setPhaseType(0, PhaseType.byValue(phase));
        testSystem.init(2, 0);
        testSystem.initPhysicalProperties();
      }
      // testOps.TPflash();
      // testOps.saturateWithWater();
      // testOps.calcPTphaseEnvelope();
      // testOps.displayResult();
      // testSystem.display();
      // SystemInterface newSyst = testSystem.phaseToSystem(testSystem.getPhase(1));
      // newSyst.setPressure(1.4);
      // newSyst.setTemperature(273 + 40.0);
      // ThermodynamicOperations testOps2 = new ThermodynamicOperations(newSyst);
      // testOps2.TPflash();
      // newSyst.display();
      // testOps.bubblePointPressureFlash();
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    // System.out.println("Henrys Constant " +
    // 1.0/testSystem.getPhase(1).getComponent("CO2").getx()*testSystem.getPressure());
    // System.out.println("water fugacity " +
    // testSystem.getPhase(0).getComponent("water").getx()*testSystem.getPhase(0).getComponent("water").getFugacityCoefficient()*testSystem.getPressure());
    // System.out.println("partial pressure water " +
    // testSystem.getPhase(0).getComponent("water").getx()*testSystem.getPressure());
    // System.out.println("activity water " +
    // testSystem.getPhase(1).getActivityCoefficient(2));
    // System.out.println("wt%MEG " +
    // testSystem.getPhase(1).getComponent("MEG").getMolarMass() *
    // testSystem.getPhase(1).getComponent("MEG").getx() /
    // testSystem.getPhase(1).getMolarMass() * 1e2);
    testSystem.display();
  }
}
