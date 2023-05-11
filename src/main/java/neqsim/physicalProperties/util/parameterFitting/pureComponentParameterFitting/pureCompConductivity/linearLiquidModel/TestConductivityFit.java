package neqsim.physicalProperties.util.parameterFitting.pureComponentParameterFitting.pureCompConductivity.linearLiquidModel;

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
 * TestConductivityFit class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestConductivityFit {
  static Logger logger = LogManager.getLogger(TestConductivityFit.class);

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
    // ResultSet dataSet = database.getResultSet("NeqSimDataBase", "SELECT * FROM
    // activityCoefficientTable WHERE Component1='MDEA' AND Component2='water'");

    try (NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database.getResultSet(
            "SELECT * FROM purecomponentconductivitydata WHERE ComponentName='TEG'");) {
      logger.info("adding....");
      while (dataSet.next()) {
        ConductivityFunction function = new ConductivityFunction();
        double guess[] = {-0.384, 0.00525, -0.00000637};
        function.setInitialGuess(guess);

        SystemInterface testSystem = new SystemSrkEos(280, 1.1);
        testSystem.addComponent(dataSet.getString("ComponentName"), 100.0);
        testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
        testSystem.init(0);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        double sample1[] = {Double.parseDouble(dataSet.getString("Temperature"))};
        double standardDeviation1[] = {0.1};
        SampleValue sample = new SampleValue(Double.parseDouble(dataSet.getString("Conductivity")),
            Double.parseDouble(dataSet.getString("StandardDeviation")), sample1,
            standardDeviation1);
        sample.setFunction(function);
        sample.setThermodynamicSystem(testSystem);
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      logger.error("database error", ex);
    }

    double sample1[] = {0.1};
    for (int i = 0; i < sampleList.size(); i++) {
      logger.info("ans: " + (sampleList.get(i)).getFunction().calcValue(sample1));
    }

    SampleSet sampleSet = new SampleSet(sampleList);
    optim.setSampleSet(sampleSet);

    // do simulations
    optim.solve();
    // optim.runMonteCarloSimulation();
    optim.displayResult();
    optim.displayCurveFit();
  }
}
