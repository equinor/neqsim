package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.hydrate;

import java.sql.ResultSet;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * TestHydrateFunction class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestHydrateFunction {
  static Logger logger = LogManager.getLogger(TestHydrateFunction.class);

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
    NeqSimDataBase database = new NeqSimDataBase();

    double[] guess = {155.8090060223, 3.1907109417, 0.4069986258}; // methane fitted statoil
                                                                  // sCPA-MC
    // double[] guess = { 176.4, 3.2641, 0.5651}; //ethane fitted statoil sCPA-MC
    // double[] guess = {126.5783867132, 3.1325067483, 0.3591889027}; // nitrogen
    // fitted statoil sCPA-MC
    // double[] guess = {205.8859461427, 3.3205773626, 0.6623796853}; // propane
    // fitted statoil sCPA-MC
    // double[] guess = { 225.1504438988, 3.0810162204, 0.8708186545}; // i-butane
    // fitted statoil sCPA-MC

    // double[] guess = { 154.7937576383, 3.2008300801, 0.4526279094}; //methane
    // fitted sCPA
    // double[] guess = { 170.1623828321, 3.0372071690, 0.6846338805}; //CO2
    // double[] guess = { 154.0936564578, 3.1720449934, 0.4436459226}; //,
    // -293247.7186651294}; //methane fitted'
    // double[] guess = {17.5988597357, -6056.9305919979};

    // ljeps, ljdiam,a

    int numb = 0;
    try (ResultSet dataSet = database.getResultSet(
        "SELECT * FROM HydratePureComp WHERE GuestMolecule='methane' AND Type<>'IHV' AND Pressure<57  AND Temperature>273.15")) {
      logger.info("adding....");
      while (dataSet.next() && numb < 6) {
        numb++;
        HydrateFunction function = new HydrateFunction();
        function.setInitialGuess(guess);
        double pres = Double.parseDouble(dataSet.getString("Pressure"));
        double temp = Double.parseDouble(dataSet.getString("Temperature"));
        // SystemInterface testSystem = new SystemSrkSchwartzentruberEos(280, 1.0);
        SystemInterface testSystem = new SystemSrkCPAstatoil(280, 1.0);
        testSystem.addComponent(dataSet.getString("GuestMolecule"), 1.0);
        testSystem.addComponent("water", 1.0);
        // testSystem.setSolidPhaseCheck(true);
        testSystem.setHydrateCheck(true);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(7);
        testSystem.setTemperature(temp);
        testSystem.setPressure(pres);
        testSystem.init(0);
        double sample1[] = {temp};
        double standardDeviation1[] = {0.1};
        SampleValue sample = new SampleValue(temp, 1.0, sample1, standardDeviation1);
        // SampleValue sample = new
        // SampleValue(Double.parseDouble(dataSet.getString("IonicActivity")),
        // Double.parseDouble(dataSet.getString("stddev2")), sample1,
        // standardDeviation1);
        sample.setFunction(function);
        sample.setThermodynamicSystem(testSystem);
        sample.setDescription(Double.toString(testSystem.getPressure()));
        sample.setReference(dataSet.getString("Reference"));
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      logger.error("database error: ", ex);
    }

    SampleSet sampleSet = new SampleSet(sampleList);
    optim.setSampleSet(sampleSet);

    // do simulations
    optim.solve();
    // optim.runMonteCarloSimulation();
    optim.displayCurveFit();
    // optim.writeToTextFile("c:/testFit.txt");
  }
}
