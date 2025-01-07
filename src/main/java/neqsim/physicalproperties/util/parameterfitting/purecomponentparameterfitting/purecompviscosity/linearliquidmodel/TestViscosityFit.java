package neqsim.physicalproperties.util.parameterfitting.purecomponentparameterfitting.purecompviscosity.linearliquidmodel;

import java.sql.ResultSet;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterfitting.SampleSet;
import neqsim.statistics.parameterfitting.SampleValue;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.ExcludeFromJacocoGeneratedReport;
import neqsim.util.database.NeqSimExperimentDatabase;

/**
 * <p>
 * TestViscosityFit class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestViscosityFit {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestViscosityFit.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    LevenbergMarquardt optim = new LevenbergMarquardt();
    ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

    // inserting samples from database
    NeqSimExperimentDatabase database = new NeqSimExperimentDatabase();

    try (ResultSet dataSet = database.getResultSet(
        "SELECT * FROM purecomponentviscosity WHERE ComponentName='MEG' ORDER BY Temperature")) {
      while (dataSet.next()) {
        ViscosityFunction function = new ViscosityFunction();
        // double guess[] = {-66.2, 11810, 0.1331, -0.0000983}; //mdea
        // double guess[] = {-5.771E1, 7.647E3, 1.442E-1, -1.357E-4}; //water
        double guess[] = {-10.14, 3868.803, -0.00550507}; // ,0.000001}; //,0.001}; //MEG
        // double guess[] = { -53.92523097004079, 9741.992308,0,0.106066223998382};
        // //TEG
        function.setInitialGuess(guess);
        SystemInterface testSystem = new SystemSrkEos(280, 0.001);
        // logger.info("component " + dataSet.getString("ComponentName"));
        testSystem.addComponent(dataSet.getString("ComponentName"), 100.0);
        testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        double temp = Double.parseDouble(dataSet.getString("Temperature"));
        testSystem.setTemperature(temp);
        testSystem.init(0);
        double sample1[] = {temp};
        double standardDeviation1[] = {0.1};
        SampleValue sample = new SampleValue(Double.parseDouble(dataSet.getString("Viscosity")),
            Double.parseDouble(dataSet.getString("StdDev")), sample1, standardDeviation1);
        sample.setFunction(function);
        sample.setThermodynamicSystem(testSystem);
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      logger.error("database error", ex);
    }

    // double sample1[] = { 0.1 };
    // for (int i = 0; i < sampleList.size(); i++) {
    // logger.info("ans: " +
    // ((SampleValue)sampleList.get(i)).getFunction().calcValue(sample1));
    // }

    SampleSet sampleSet = new SampleSet(sampleList);
    optim.setSampleSet(sampleSet);
    optim.setMaxNumberOfIterations(100);
    // do simulations
    optim.solve();
    optim.displayCurveFit();
    // optim.runMonteCarloSimulation();
    // optim.displayCurveFit();
    // optim.writeToTextFile("c:/testFit.txt");
  }
}
