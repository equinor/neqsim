package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.freezingFit;

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
 * TestSolidComplexFunction class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class TestSolidComplexFunction {
  static Logger logger = LogManager.getLogger(TestSolidComplexFunction.class);

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

    // double parameterGuess[] = {0.1640550024}; //, 7578.080}; //, 245.0};
    double parameterGuess[] = {0.119803125, 4482.0};

    try (NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database.getResultSet(
        "SELECT * FROM comlexsolidfreezingdata WHERE Component1='TEG' AND Component2='water'")) {
      while (dataSet.next()) {
        SolidComplexFunction function = new SolidComplexFunction();

        double x1 = Double.parseDouble(dataSet.getString("x1")) * 100;
        double x2 = Double.parseDouble(dataSet.getString("x2")) * 100;
        double val = Double.parseDouble(dataSet.getString("temperature"));

        SystemInterface testSystem =
            new SystemSrkCPAstatoil(val, Double.parseDouble(dataSet.getString("pressure")));
        testSystem.addComponent(dataSet.getString("Component1"), x1);
        testSystem.addComponent(dataSet.getString("Component2"), x2);
        // testSystem.createDatabase(true);
        testSystem.setMixingRule(10);
        testSystem.init(0);
        double sample1[] = {testSystem.getPressure(), testSystem.getTemperature()};
        double standardDeviation1[] = {0.13, 0.1};

        SampleValue sample =
            new SampleValue(val, Double.parseDouble(dataSet.getString("StandardDeviation")),
                sample1, standardDeviation1);
        sample.setFunction(function);
        sample.setThermodynamicSystem(testSystem);
        sample.setReference(Double.toString(testSystem.getTemperature()));
        function.setInitialGuess(parameterGuess);
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    }

    SampleSet sampleSet = new SampleSet(sampleList);
    optim.setSampleSet(sampleSet);
    optim.solve();
  }
}
