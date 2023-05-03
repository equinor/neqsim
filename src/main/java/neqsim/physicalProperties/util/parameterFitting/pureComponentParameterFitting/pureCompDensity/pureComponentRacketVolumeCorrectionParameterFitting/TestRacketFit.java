package neqsim.physicalProperties.util.parameterFitting.pureComponentParameterFitting.pureCompDensity.pureComponentRacketVolumeCorrectionParameterFitting;

import java.sql.ResultSet;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * TestRacketFit class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestRacketFit {
  static Logger logger = LogManager.getLogger(TestRacketFit.class);

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
    // ResultSet dataSet = database.getResultSet("NeqSimDataBase", "SELECT * FROM
    // activityCoefficientTable WHERE Component1='MDEA' AND Component2='water'");

    try (ResultSet dataSet =
        database.getResultSet("SELECT * FROM purecomponentdensity WHERE ComponentName='MEG'")) {
      logger.info("adding....");
      while (dataSet.next()) {
        RacketFunction function = new RacketFunction();
        double guess[] = {0.3211};
        function.setInitialGuess(guess);

        SystemInterface testSystem = new SystemSrkEos(280, 0.001);
        testSystem.addComponent(dataSet.getString("ComponentName"), 100.0);
        testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
        testSystem.init(0);
        testSystem.setMixingRule(2);
        double sample1[] = {Double.parseDouble(dataSet.getString("Temperature"))};
        double standardDeviation1[] = {0.1};
        SampleValue sample = new SampleValue(Double.parseDouble(dataSet.getString("Density")),
            Double.parseDouble(dataSet.getString("StandardDeviation")), sample1,
            standardDeviation1);
        sample.setFunction(function);
        sample.setThermodynamicSystem(testSystem);
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      logger.error("database error" + ex);
    }

    SampleSet sampleSet = new SampleSet(sampleList);
    optim.setSampleSet(sampleSet);

    // do simulations
    optim.solve();
    // optim.runMonteCarloSimulation();
    optim.displayCurveFit();
    // optim.displayCurveFit();

    optim.writeToTextFile("c:/testFit.txt");
  }
}
