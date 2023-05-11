package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.EosInteractionParameterFitting;

import java.sql.ResultSet;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.database.NeqSimExperimentDatabase;

/**
 * <p>
 * TestEosParameterFittingToMercurySolubility class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestEosParameterFittingToMercurySolubility {
  static Logger logger = LogManager.getLogger(TestEosParameterFittingToMercurySolubility.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String[] args) {
    LevenbergMarquardt optim = new LevenbergMarquardt();
    ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

    // inserting samples from database
    NeqSimExperimentDatabase database = new NeqSimExperimentDatabase();

    // double parameterGuess[] = {0.13}; // mercury-methane
    // double parameterGuess[] = {0.0496811275399517}; // mercury-methane
    // double parameterGuess[] = {0.0704}; // mercury-ethane
    double parameterGuess[] = {-0.03310000498911416}; // mercury-ibutane
    // double parameterGuess[] = {0.0674064646735}; // mercury-propane
    // double parameterGuess[] = { 0.3674008071}; // mercury-CO2
    // double parameterGuess[] = { 0.016529772608}; // mercury-nitrogen
    try (ResultSet dataSet = database.getResultSet(
        "SELECT * FROM binarysolubilitydata WHERE ComponentSolute='mercury' AND ComponentSolvent='n-hexane'")) {
      int p = 0;
      logger.info("adding....");
      while (dataSet.next() && p < 40) {
        p++;
        CPAParameterFittingToSolubilityData function =
            new CPAParameterFittingToSolubilityData(0, 0);

        SystemInterface testSystem = new SystemSrkEos(290, 1.0);
        testSystem.addComponent("mercury", 10.0);
        testSystem.addComponent("n-hexane", 10.0);
        testSystem.getPhase(0).getComponent("mercury").setAttractiveTerm(12);
        testSystem.getPhase(1).getComponent("mercury").setAttractiveTerm(12);
        testSystem.createDatabase(true);
        testSystem.setMultiPhaseCheck(true);
        testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
        testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")) + 2);
        testSystem.setMixingRule(2);
        testSystem.init(0);
        double sample1[] = {testSystem.getPressure(), testSystem.getTemperature()};
        double standardDeviation1[] = {0.13};
        double x1 = Double.parseDouble(dataSet.getString("x1"));
        SampleValue sample = new SampleValue(x1, x1 / 100.0, sample1, standardDeviation1);
        sample.setFunction(function);
        sample.setThermodynamicSystem(testSystem);
        function.setInitialGuess(parameterGuess);
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      logger.error("database error", ex);
    }

    SampleSet sampleSet = new SampleSet(sampleList);
    optim.setSampleSet(sampleSet);

    // do simulations
    optim.solve();
    // optim.runMonteCarloSimulation();
    // optim.displayResult();
    optim.displayCurveFit();
    // optim.writeToTextFile("c:/testFit.txt");
  }
}
