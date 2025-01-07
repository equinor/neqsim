package neqsim.physicalproperties.util.parameterfitting.purecomponentparameterfitting.purecompinterfacetension;

import java.sql.ResultSet;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterfitting.SampleSet;
import neqsim.statistics.parameterfitting.SampleValue;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.util.ExcludeFromJacocoGeneratedReport;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * TestParachorFit class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestParachorFit {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestParachorFit.class);

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

    try (NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database
            .getResultSet("SELECT * FROM purecomponentsurfacetension WHERE ComponentName='MEG'")) {
      while (dataSet.next()) {
        ParachorFunction function = new ParachorFunction();
        double guess[] = {207.2}; // methane
        function.setInitialGuess(guess);

        SystemInterface testSystem = new SystemSrkCPAstatoil(280, 0.001);
        testSystem.addComponent(dataSet.getString("ComponentName"), 100.0);
        testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
        testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
        testSystem.getInterphaseProperties().setInterfacialTensionModel(-10);
        testSystem.useVolumeCorrection(true);
        testSystem.setMixingRule(2);
        testSystem.init(0);
        testSystem.setNumberOfPhases(2);
        testSystem.init(3);
        double sample1[] = {testSystem.getTemperature(), testSystem.getPressure()};
        double standardDeviation1[] = {0.1, 0.1};
        SampleValue sample =
            new SampleValue(Double.parseDouble(dataSet.getString("SurfaceTension")),
                Double.parseDouble(dataSet.getString("StandardDeviation")), sample1,
                standardDeviation1);
        sample.setFunction(function);
        sample.setThermodynamicSystem(testSystem);
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      logger.error("database error", ex);
    }

    SampleSet sampleSet = new SampleSet(sampleList);
    optim.setSampleSet(sampleSet);

    // do simulations
    optim.solve();
    optim.displayCurveFit();
    // optim.runMonteCarloSimulation();
    optim.displayCurveFit();

    // optim.writeToTextFile("c:/testFit.txt");
  }
}
