package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * SulfureDeposition class.
 * </p>
 *
 * @author esol
 * @since 2.2.3
 * @version $Id: $Id
 */
public class SulfureDeposition {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(SulfureDeposition.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String args[]) {
    // SystemInterface testSystem = new SystemSrkTwuCoonEos(220.15, 6.0);
    SystemInterface testSystem = new SystemSrkEos(273.15 + 65.0, 12.0);
    // SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 62.5, 12);
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    // testSystem.addComponent("methane", 1);
    // testSystem.addComponent("CO2",100-79.9);
    // testSystem.addComponent("water", 0.91);
    // testSystem.addComponent("n-heptane", 0.91);
    // testSystem.addComponent("oxygen", 1.1);

    // Vigdis 3 stage
    /*
     * testSystem.addComponent("nitrogen", 0.000001); testSystem.addComponent("CO2", 0.000053);
     * testSystem.addComponent("methane", 0.001125); testSystem.addComponent("ethane", 0.007257);
     * testSystem.addComponent("propane", 0.028675); testSystem.addComponent("i-butane", 0.009183);
     * testSystem.addComponent("n-butane", 0.034027); testSystem.addComponent("i-pentane",
     * 0.017530); testSystem.addComponent("n-pentane", 0.028188);
     * testSystem.addComponent("n-hexane", 0.045485); testSystem.addTBPfraction("Vigdis_C7-C8",
     * 0.143218, 99.07 / 1000.0, 0.75111); testSystem.addTBPfraction("Vigdis_C9-C12", 0.208009,
     * 138.64 / 1000.0, 0.80122); testSystem.addTBPfraction("Vigdis_C13-C18", 0.217329, 213.76 /
     * 1000.0, 0.85103); testSystem.addTBPfraction("Vigdis_C19-C29", 0.156069, 333.84 / 1000.0,
     * 0.89639); testSystem.addTBPfraction("Vigdis_C30-C80", 0.076902, 525.0 / 1000.0, 0.94072);
     */
    // Vigdis 2 stage
    /*
     * testSystem.addComponent("nitrogen", 0.000301); testSystem.addComponent("CO2", 0.000171);
     * testSystem.addComponent("methane", 0.023407); testSystem.addComponent("ethane", 0.005422);
     * testSystem.addComponent("propane", 0.005413); testSystem.addComponent("i-butane", 0.00095);
     * testSystem.addComponent("n-butane", 0.003003); testSystem.addComponent("i-pentane",
     * 0.001173); testSystem.addComponent("n-pentane", 0.001785);
     * testSystem.addComponent("n-hexane", 0.002514); testSystem.addTBPfraction("Vigdis_C7-C8",
     * 0.0075, 99.07 / 1000.0, 0.75111); testSystem.addTBPfraction("Vigdis_C9-C12", 0.010572, 138.64
     * / 1000.0, 0.80122); testSystem.addTBPfraction("Vigdis_C13-C18", 0.011015, 213.76 / 1000.0,
     * 0.85103); testSystem.addTBPfraction("Vigdis_C19-C29", 0.00791, 333.84 / 1000.0, 0.89639);
     * testSystem.addTBPfraction("Vigdis_C30-C80", 0.003898, 525.0 / 1000.0, 0.94072); //
     * testSystem.addComponent("water", 0.2514);
     */
    // snorre
    /*
     * testSystem.addComponent("nitrogen", 0.000501); testSystem.addComponent("CO2", 0.000172);
     * testSystem.addComponent("methane", 0.018972); testSystem.addComponent("ethane", 0.007619);
     * testSystem.addComponent("propane", 0.010628); testSystem.addComponent("i-butane", 0.001855);
     * testSystem.addComponent("n-butane", 0.006983); testSystem.addComponent("i-pentane",
     * 0.002583); testSystem.addComponent("n-pentane", 0.003948);
     * testSystem.addComponent("n-hexane", 0.000302); testSystem.addTBPfraction("Snorre_C6",
     * 0.004846, 86 / 1000.0, 0.664); testSystem.addTBPfraction("Snorre_C7", 0.006924, 96 / 1000.0,
     * 0.738); testSystem.addTBPfraction("Snorre_C8", 0.006924, 107.0 / 1000.0, 0.765);
     * testSystem.addTBPfraction("Snorre_C9", 0.00426, 121.0 / 1000.0, 0.781);
     * testSystem.addTBPfraction("Snorre_C10-C13", 0.010273, 152.0 / 1000.0, 0.809);
     * testSystem.addTBPfraction("Snorre_C14-C16", 0.005681, 205.0 / 1000.0, 0.835);
     * testSystem.addTBPfraction("Snorre_C17-C20", 0.005613, 255.0 / 1000.0, 0.854);
     * testSystem.addTBPfraction("Snorre_C21-C24", 0.003976, 309.0 / 1000.0, 0.871);
     * testSystem.addTBPfraction("Snorre_C25-C28", 0.002817, 365.0 / 1000.0, 0.885);
     * testSystem.addTBPfraction("Snorre_C29-C34", 0.002762, 433.0 / 1000.0, 0.899);
     * testSystem.addTBPfraction("Snorre_C35-C41", 0.001847, 523.0 / 1000.0, 0.915);
     * testSystem.addTBPfraction("Snorre_C42-C52", 0.001367, 643.0 / 1000.0, 0.931);
     * testSystem.addTBPfraction("Snorre_C53-C80", 0.000787, 867.0 / 1000.0, 0.955);
     */
    // testSystem.addComponent("propane",1.05);
    // testSystem.addComponent("n-butane",0.06);
    // testSystem.addComponent("i-butane",0.6);
    // testSystem.addComponent("n-pentane",0.01);
    // testSystem.addComponent("i-pentane",0.07);
    // testSystem.addComponent("benzene",79.9);
    // testSystem.addComponent("water",150.0e-6);
    // testSystem.addComponent("n-hexane",0.23);
    // testSystem.addComponent("n-heptane",0.08);
    // testSystem.addComponent("n-octane",0.03);
    // testSystem.addComponent("S8", 30e-9 * 100);
    // testSystem.addComponent("water", 0.0301);
    // testSystem.addPlusFraction("C6+", 0.58, 172.0/1000, 0.95);
    // if(testSystem.characterizePlusFraction()){
    // testSystem.getCharacterization().setPseudocomponents(true);
    // testSystem.addPlusFraction(7,15);
    // }
    // testSystem.addComponent("water", 0.00301);
    // testSystem.addComponent("S8", 0.00000048028452962001990);
    // testSystem.createDatabase(true);
    testSystem.addComponent("nitrogen", 5.84704017689321e-003);
    testSystem.addComponent("CO2", 0.021);
    testSystem.addComponent("methane", 0.63);
    testSystem.addComponent("ethane", 0.134769062252199);
    testSystem.addComponent("propane", 9.11979242318279e-002);
    testSystem.addComponent("i-butane", 0.020654078469792);
    testSystem.addComponent("n-butane", 3.74972131983075e-002);
    testSystem.addComponent("i-pentane", 1.13683864588619e-002);
    testSystem.addComponent("n-pentane", 1.03129901150887e-002);
    testSystem.addComponent("n-hexane", 6.103129901150887e-002);
    testSystem.addComponent("S8", 10.0077E-06);
    testSystem.setMixingRule(2);

    testSystem.setMultiPhaseCheck(true);
    // testSystem.setSolidPhaseCheck("S8");

    try {
      // testOps.TPflash();
      testOps.TPSolidflash();
      // testOps.bubblePointPressureFlash();
      // testOps.calcPTphaseEnvelope();
      // testOps.displayResult();
      // testOps.bubblePointTemperatureFlash();
      // testOps.calcPTphaseEnvelope();
      // testOps.displayResult();
      // testOps.freezingPointTemperatureFlash();
      // ((thermo.phase.PhaseEosInterface)
      // testSystem.getPhase(0)).displayInteractionCoefficients("");

      logger.info("temperature " + (testSystem.getTemperature() - 273.15));
      logger.info("mol S8/mol gas (ppb) " + testSystem.getPhase(0).getComponent("S8").getx() * 1e9);
      logger.info("mg S8/Sm^3 gas " + testSystem.getPhase(0).getComponent("S8").getx()
          * testSystem.getPhase(0).getComponent("S8").getMolarMass() * 1e6
          * (101325 / (ThermodynamicConstantsInterface.R * 288.15)));

      logger.info("wt% S8 in gas " + testSystem.getPhase(0).getComponent("S8").getx()
          * testSystem.getPhase(0).getComponent("S8").getMolarMass()
          / testSystem.getPhase(0).getMolarMass() * 100);
      logger.info("wt% S8 in oil " + testSystem.getPhase(1).getComponent("S8").getx()
          * testSystem.getPhase(0).getComponent("S8").getMolarMass()
          / testSystem.getPhase(1).getMolarMass() * 100);
      // logger.info("ppb (wt) S8 in water " +
      // testSystem.getPhase(2).getComponent("S8").getx() *
      // testSystem.getPhase(0).getComponent("S8").getMolarMass() /
      // testSystem.getPhase(2).getMolarMass() * 1e9);
      logger.info("ppm (wt) S8 total " + testSystem.getPhase(0).getComponent("S8").getz()
          * testSystem.getPhase(0).getComponent("S8").getMolarMass() / testSystem.getMolarMass()
          * 1e6);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }
    testSystem.display();
  }
}
