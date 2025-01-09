package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.pvtsimulation.simulation.SaturationPressure;
import neqsim.pvtsimulation.simulation.SimulationInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * PhaseEnvelope class.
 * </p>
 *
 * @author esol
 * @since 2.2.3
 * @version $Id: $Id
 */
public class PhaseEnvelope {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(PhaseEnvelope.class);

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
    // SystemInterface testSystem = new SystemUMRPRUEos(225.65, 1.00);
    // SystemInterface testSystem = new SystemPrEos1978(223.15,50.00);
    // SystemInterface testSystem = new SystemPrGassemEos(253.15,50.00);
    SystemInterface testSystem = new SystemUMRPRUMCEos(280.0, 41.00);
    // SystemInterface testSystem = new SystemPrDanesh(273.15+80.0,100.00);
    // SystemInterface testSystem = new SystemPrEosDelft1998(223.15,50.00);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

    testSystem.addComponent("nitrogen", 1.1427);
    testSystem.addComponent("CO2", 0.5364);
    testSystem.addComponent("methane", 95.2399);
    testSystem.addComponent("ethane", 2.2126);
    testSystem.addComponent("propane", 0.3236);
    testSystem.addComponent("i-butane", 0.1342);
    testSystem.addComponent("n-butane", 0.0812);
    // testSystem.addComponent("22-dim-C3", 1.7977);
    testSystem.addComponent("i-pentane", 0.0684);
    testSystem.addComponent("n-pentane", 0.0344);
    // testSystem.addComponent("c-C5", 0.0112);
    // testSystem.addComponent("22-dim-C4", 0.0022);
    // testSystem.addComponent("23-dim-C4", 0.0027);
    testSystem.addComponent("2-m-C5", 0.0341);
    testSystem.addComponent("3-m-C5", 0.0105);
    testSystem.addComponent("n-hexane", 0.0172);
    testSystem.addComponent("c-hexane", 0.0701);
    testSystem.addComponent("benzene", 0.0016);
    testSystem.addComponent("n-heptane", 0.0124);
    testSystem.addComponent("toluene", 0.0042);
    testSystem.addComponent("c-C7", 0.0504);
    testSystem.addComponent("n-octane", 0.0037);
    testSystem.addComponent("m-Xylene", 0.0032);
    testSystem.addComponent("c-C8", 0.0095);
    testSystem.addComponent("n-nonane", 0.0033);
    // testSystem.addTBPfraction("C10", 0.0053, 134.0/1000.0, 0.79);
    // testSystem.addTBPfraction("C11", 0.0004, 147.0/1000.0, 0.8);

    testSystem.addComponent("nC10", 0.0058);
    testSystem.addComponent("nC11", 0.0005);
    // testSystem.addComponent("nC12", 0.0004);

    // testSystem.addComponent("m-Xylene", 0.0000000000);

    // testSystem.addComponent("nC10", 1e-4);

    // testSystem.addComponent("n-octane", 0.027);
    // testSystem.addComponent("nC13", .3);
    // testSystem.addTBPfraction("C6", 1.587, 86.178 / 1000.0, 0.70255);
    // testSystem.addTBPfraction("C7", 2.566, 91.5 / 1000.0, 0.738);
    // testSystem.addTBPfraction("C8", 2.764, 101.2 / 1000.0, 0.765);
    // testSystem.addTBPfraction("C9", 1.71, 119.1 / 1000.0, 0.781);
    // testSystem.addTBPfraction("C10", 1.647, 254.9 / 1000.0, 0.894871);

    // testSystem.addComponent("water", 100.2);
    // testSystem.addPlusFraction("C11", 0.01, 256.2 / 1000.0, 0.92787278398);
    // testSystem.getCharacterization().

    // testSystem.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(12);
    // testSystem.getCharacterization().characterisePlusFraction();

    // testSystem.createDatabase(true);

    testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");
    // testSystem.setMultiPhaseCheck(true);
    // testSystem.setMixingRule(2); //"UNIFAC_UMRPRU");
    // testSystem.setHydrateCheck(true);
    // testSystem.setBmixType(0);

    // Calculates the phase envelope for the mixture
    // testOps.calcPTphaseEnvelope(true);

    // Calculates the phase envelope for pashe fraction x and 1-x
    // calcPTphaseEnvelope(minimum pressure, phase fraction);
    try {
      /*
       * testOps.setRunAsThread(true); testOps.waterDewPointLine(10, 200); boolean isFinished =
       * testOps.waitAndCheckForFinishedCalculation(50000); double[][] waterData =
       * testOps.getData();
       *
       * testOps.hydrateEquilibriumLine(10, 200); isFinished =
       * testOps.waitAndCheckForFinishedCalculation(50000); double[][] hydData = testOps.getData();
       *
       * testSystem.addComponent("water",
       * -testSystem.getPhase(0).getComponent("water").getNumberOfmoles());
       */
      // testOps.calcPTphaseEnvelope(); //true);
      // testOps.displayResult();
      // logger.info("Cricondenbar " + testOps.get("cricondenbar")[0] + " " +
      // testOps.get("cricondenbar")[1]);
      // logger.info("Cricondentherm " + testOps.get("cricondentherm")[0] + " " +
      // testOps.get("cricondentherm")[1]);
      // isFinished = testOps.waitAndCheckForFinishedCalculation(10000);
      // testOps.addData("water", waterData);
      // testOps.addData("hydrate", hydData);
      // testOps.calcPTphaseEnvelopeNew();
      // testOps.displayResult();

      testSystem.setTemperature(273.15 - 0.0);
      testSystem.setPressure(50.0);
      SimulationInterface satPresSim = new SaturationPressure(testSystem);
      satPresSim.run();
      satPresSim.getThermoSystem().display();
      // testOps.getJfreeChart();
      // testOps.dewPointPressureFlash();
      // testOps.bubblePointTemperatureFlash();
      // JFreeChart jfreeObj = testOps.getJfreeChart();
      // BufferedImage buf = jfreeObj.createBufferedImage(640, 400, null);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    // testSystem.display();
    // testOps.get("DewT");
    // thermo.ThermodynamicModelTest testModel = new
    // thermo.ThermodynamicModelTest(testSystem);
    // testModel.runTest();

    // System.out.println("tempeerature " + (testSystem.getTemperature() - 273.15));
    // testOps.displayResult();
  }
}
