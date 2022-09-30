package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.cpaParam;

import java.sql.ResultSet;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAs;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * TestCPA class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestCPA {
  static Logger logger = LogManager.getLogger(TestCPA.class);

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
    // ResultSet dataSet = database.getResultSet( "SELECT * FROM
    // activityCoefficientTable WHERE Component1='MDEA' AND Component2='water'");

    try (ResultSet dataSet = database.getResultSet(
        "SELECT * FROM PureComponentVapourPressures WHERE ComponentName='MDEA' AND VapourPressure>0")) {
      while (dataSet.next()) {
        CPAFunction function = new CPAFunction();

        SystemInterface testSystem = new SystemSrkCPAs(280, 0.1);
        // SystemInterface testSystem = new SystemSrkEos(280, 0.001);
        testSystem.addComponent(dataSet.getString("ComponentName"), 100.0);

        // testSystem.createDatabase(true);
        double sample1[] = {Double.parseDouble(dataSet.getString("Temperature"))};
        testSystem.setTemperature(sample1[0]);
        double standardDeviation1[] = {0.1};
        double val = Double.parseDouble(dataSet.getString("VapourPressure"));
        testSystem.setPressure(val);
        double stddev = val / 100.0;
        double logVal = Math.log(val);
        SampleValue sample = new SampleValue(val, stddev, sample1, standardDeviation1);
        testSystem.init(0);
        // double guess[] = {6602,2.45,0.27,353.69,0.05129};
        // double guess[] = {9.341E4,1.953E0,1.756E-1,92.69,0.129};
        // 9,3204676672 7,8456679213 2,4447709134 0,2495070040 16344,0651342815

        // double guess[] =
        // {((ComponentSrk)testSystem.getPhase(0).getComponent(0)).geta(),((ComponentSrk)testSystem.getPhase(0).getComponent(0)).getb(),testSystem.getPhase(0).getComponent(0).getAcentricFactor(),0.04567};
        double guess[] = {13.21, 39.123563589168, 1.1692, 0.0188, 14337.0}; // ,
                                                                            // 0.3255175584,
                                                                            // 10725.7300849509};
        // abs 3.2% 10,8185533003 33,0294376487 1,0676048144 0,0221795587
        // 12220,2224075760

        function.setInitialGuess(guess);

        sample.setFunction(function);
        sample.setThermodynamicSystem(testSystem);
        sample.setReference(dataSet.getString("Reference"));
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      logger.error("database error" + ex);
    }

    dataSet =
        database.getResultSet("SELECT * FROM PureComponentDensity WHERE ComponentName='MDEA'");

    try {
      while (dataSet.next()) {
        CPAFunctionDens function = new CPAFunctionDens();
        SystemInterface testSystem = new SystemSrkCPAs(280, 0.001);
        // SystemInterface testSystem = new SystemSrkEos(280, 0.001);
        testSystem.addComponent(dataSet.getString("ComponentName"), 100.0);
        testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
        double sample1[] = {Double.parseDouble(dataSet.getString("Temperature"))};
        testSystem.setTemperature(sample1[0]);
        testSystem.init(0);
        testSystem.setMixingRule(1);
        double standardDeviation1[] = {0.1};
        double val = Double.parseDouble(dataSet.getString("Density"));
        SampleValue sample = new SampleValue(val, val / 100.0, sample1, standardDeviation1);
        // double guess[] =
        // {testSystem.getPhase(0).geta(),testSystem.getPhase(0).getb(),
        // testSystem.getPhase(0).getComponent(0).getAcentricFactor()}; // , 2260.69,
        // 0.0229};
        double guess[] = {13.21, 39.123563589168, 1.1692, 0.0188, 14337.0}; // ,
                                                                            // 0.4354649799,
                                                                            // 0.3255175584,
                                                                            // 10725.7300849509};

        // {((ComponentSrk)testSystem.getPhase(0).getComponent(0)).geta(),((ComponentSrk)testSystem.getPhase(0).getComponent(0)).getb(),testSystem.getPhase(0).getComponent(0).getAcentricFactor(),0.04567};

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
