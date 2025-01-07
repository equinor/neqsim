package neqsim.physicalproperties.util.parameterfitting.purecomponentparameterfitting.purecompviscosity.chungmethod;

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
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * TestChungFit class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestChungFit {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestChungFit.class);

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

    try (NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database.getResultSet("SELECT * FROM purecomponentviscosity") // WHERE
                                                                                          // ComponentName='MDEA*'");
    ) {
      while (dataSet.next()) {
        ChungFunction function = new ChungFunction();
        double guess[] = {0.3211};
        function.setInitialGuess(guess);

        SystemInterface testSystem = new SystemSrkEos(280, 0.001);
        testSystem.addComponent("MDEA", 100.0);
        // testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
        testSystem.createDatabase(true);
        testSystem.init(0);
        testSystem.setMixingRule(2);
        double sample1[] = {Double.parseDouble(dataSet.getString("Temperature"))};
        double standardDeviation1[] = {0.1};
        SampleValue sample = new SampleValue(Double.parseDouble(dataSet.getString("Viscosity")),
            0.001, sample1, standardDeviation1);
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
    // optim.runMonteCarloSimulation();
    optim.displayResult();
    optim.displayCurveFit();
  }
}
