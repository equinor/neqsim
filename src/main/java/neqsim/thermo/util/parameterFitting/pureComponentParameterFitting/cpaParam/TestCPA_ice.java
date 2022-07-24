package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.cpaParam;

import java.sql.ResultSet;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPA;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * TestCPA_ice class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestCPA_ice {
  static Logger logger = LogManager.getLogger(TestCPA_ice.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @SuppressWarnings("unused")
  public static void main(String[] args) {
    ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

    // inserting samples from database
    NeqSimDataBase database = new NeqSimDataBase();
    double guess[] = {1.453, 0.9894, 1.0669, 0.05787}; // water - srk-cpa
    // 1,6297102017 1,1253994266 1,1701135830 0,0701182891
    double bounds[][] = {{0, 3.0055}, {0, 8.0055}, {0.00001, 10.001}, {-1.0015, 1.0015},
        {-320.0015, 320.0015}, {-320.901, 320.900195}, {-1.0, 1000}, {-0.800001, 0.8},
        {-80000.01, 20000.8}, {-0.01, 10.6}, {-0.01, 0.0015}, {-0.01, 0.0015}};

    ResultSet dataSet = database.getResultSet(
        "SELECT * FROM SolidVapourPressure WHERE ComponentName='water' ORDER BY Temperature");

    try {
      while (dataSet.next()) {
        CPAFunction function = new CPAFunction();
        SystemInterface testSystem =
            new SystemSrkCPA(Double.parseDouble(dataSet.getString("Temperature")),
                Double.parseDouble(dataSet.getString("VapourPressure")));
        // SystemInterface testSystem = new SystemSrkEos(280, 0.001);
        testSystem.addComponent(dataSet.getString("ComponentName"), 1.0);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        // testSystem.init(0);

        double temp = testSystem.getTemperature();
        double val = testSystem.getPressure();

        double sample1[] = {temp};
        double standardDeviation1[] = {0.1};

        double stddev = val / 100.0;
        double logVal = Math.log(val);
        SampleValue sample = new SampleValue(val, stddev, sample1, standardDeviation1);

        // function.setBounds(bounds);
        sample.setFunction(function);
        sample.setThermodynamicSystem(testSystem);
        function.setInitialGuess(guess);
        sample.setReference(dataSet.getString("Reference"));
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      logger.error("database error" + ex);
    }

    dataSet = database.getResultSet(
        "SELECT * FROM SolidVapourPressure WHERE ComponentName='water' ORDER BY Temperature");

    try {
      while (dataSet.next()) {
        CPAFunctionDens function = new CPAFunctionDens(1);
        SystemInterface testSystem =
            new SystemSrkCPA(Double.parseDouble(dataSet.getString("Temperature")), 1.1);
        // SystemInterface testSystem = new SystemSrkEos(280, 0.001);
        double temp = testSystem.getTemperature();
        testSystem.addComponent(dataSet.getString("ComponentName"), 100.0);
        testSystem.setPressure(Double.parseDouble(dataSet.getString("VapourPressure")));
        // testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
        testSystem.setTemperature(temp);
        testSystem.setMixingRule(1);
        // testSystem.init(0);
        // double dens = Double.parseDouble(dataSet.getString("liquiddensity"));
        double dens = Double.parseDouble(dataSet.getString("soliddensity"));
        double sample1[] = {temp};
        double standardDeviation1[] = {0.1};
        SampleValue sample = new SampleValue(dens, dens / 100.0, sample1, standardDeviation1);
        // double guess[] = {46939.4738048507, 1.5971863018, 0.7623134978,
        // 0.0292037583};

        // double guess[] = {9.341E4,1.953E0,1.756E-1,92.69,0.129};
        function.setInitialGuess(guess);
        sample.setFunction(function);
        sample.setThermodynamicSystem(testSystem);
        sample.setReference(dataSet.getString("Reference"));
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      logger.error("database error" + ex);
    }

    dataSet = database.getResultSet(
        "SELECT * FROM PureComponentVapourPressures WHERE ComponentName='water' ORDER BY Temperature");

    try {
      while (!dataSet.next()) {
        CPAFunctionDens function = new CPAFunctionDens(0);
        SystemInterface testSystem = new SystemSrkCPA(280, 5.001);
        double temp = Double.parseDouble(dataSet.getString("Temperature"));
        // SystemInterface testSystem = new SystemSrkEos(280, 0.001);
        testSystem.addComponent(dataSet.getString("ComponentName"), 100.0);
        testSystem.setPressure(Double.parseDouble(dataSet.getString("VapourPressure")) + 0.5);
        testSystem.setTemperature(temp);
        testSystem.setMixingRule(2);
        testSystem.init(0);
        double dens = Double.parseDouble(dataSet.getString("gasdensity"));
        double sample1[] = {temp};
        double standardDeviation1[] = {0.1};
        SampleValue sample = new SampleValue(dens, dens / 100.0, sample1, standardDeviation1);
        // double guess[] = {46939.4738048507, 1.5971863018, 0.7623134978,
        // 0.0292037583};

        // double guess[] = {9.341E4,1.953E0,1.756E-1,92.69,0.129};
        function.setInitialGuess(guess);
        sample.setFunction(function);
        sample.setThermodynamicSystem(testSystem);
        sample.setReference(dataSet.getString("Reference"));
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      logger.error("database error" + ex);
    }

    dataSet = database.getResultSet(
        "SELECT * FROM PureComponentVapourPressures WHERE ComponentName='water' AND Temperature>273.15 AND Temperature<620.15 ORDER BY Temperature");

    try {
      while (dataSet.next()) {
        CPAFunctionDens function = new CPAFunctionDens(0);
        SystemInterface testSystem = new SystemSrkCPA(280, 5.001);
        double temp = Double.parseDouble(dataSet.getString("Temperature"));
        // SystemInterface testSystem = new SystemSrkEos(280, 0.001);
        testSystem.addComponent(dataSet.getString("ComponentName"), 100.0);
        testSystem.setPressure(Double.parseDouble(dataSet.getString("VapourPressure")) + 0.5);
        testSystem.setTemperature(temp);
        testSystem.setMixingRule(2);
        testSystem.init(0);
        double dens = Double.parseDouble(dataSet.getString("gasdensity"));
        double sample1[] = {temp};
        double standardDeviation1[] = {0.1};
        SampleValue sample = new SampleValue(dens, dens / 100.0, sample1, standardDeviation1);
        // double guess[] = {46939.4738048507, 1.5971863018, 0.7623134978,
        // 0.0292037583};

        // double guess[] = {9.341E4,1.953E0,1.756E-1,92.69,0.129};
        function.setInitialGuess(guess);
        sample.setFunction(function);
        sample.setThermodynamicSystem(testSystem);
        sample.setReference(dataSet.getString("Reference"));
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      logger.error("database error" + ex);
    }

    SampleSet sampleSet = new SampleSet(sampleList);

    LevenbergMarquardt optim = new LevenbergMarquardt();
    optim.setSampleSet(sampleSet);

    // do simulations
    optim.solve();
    // optim.runMonteCarloSimulation();
    optim.displayCurveFit();
    optim.writeToTextFile("c:/test.txt");
  }
}
