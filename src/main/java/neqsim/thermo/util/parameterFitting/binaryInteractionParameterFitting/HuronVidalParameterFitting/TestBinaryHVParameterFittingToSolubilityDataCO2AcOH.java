package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.HuronVidalParameterFitting;

import java.sql.ResultSet;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkSchwartzentruberEos;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * TestBinaryHVParameterFittingToSolubilityDataCO2AcOH class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestBinaryHVParameterFittingToSolubilityDataCO2AcOH {
  static Logger logger =
      LogManager.getLogger(TestBinaryHVParameterFittingToSolubilityDataCO2AcOH.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @SuppressWarnings("unused")
  public static void main(String[] args) {
    LevenbergMarquardt optim = new LevenbergMarquardt();
    ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

    // inserting samples from database
    NeqSimDataBase database = new NeqSimDataBase();
    ResultSet dataSet = database.getResultSet("SELECT * FROM CO2AcOHdata WHERE X>0 AND y>0");

    try {
      logger.info("adding....");
      while (dataSet.next()) {
        BinaryHVParameterFittingToSolubilityData function =
            new BinaryHVParameterFittingToSolubilityData();
        double parameterGuess[] = {2500, -1500, -0.1, -0.1, 0.03};
        function.setInitialGuess(parameterGuess);

        SystemInterface testSystem = new SystemSrkSchwartzentruberEos(280, 1);
        testSystem.addComponent("CO2", 10.0);
        testSystem.addComponent("AceticAcid", 1.0);

        testSystem.createDatabase(true);
        testSystem.setMixingRule(4);

        double temperature = Double.parseDouble(dataSet.getString("Temperature"));
        double pressure = Double.parseDouble(dataSet.getString("Pressure"));
        double x = Double.parseDouble(dataSet.getString("x"));
        double y = Double.parseDouble(dataSet.getString("y"));

        if (x == 0) {
          continue;
        }

        testSystem.setTemperature(temperature);
        testSystem.setPressure(pressure);
        testSystem.init(0);

        double sample1[] = {testSystem.getPressure(), testSystem.getTemperature()};
        double standardDeviation1[] = {0.01, 0.01};

        SampleValue sample = new SampleValue(x, x / 100.0, sample1, standardDeviation1);
        sample.setFunction(function);
        sample.setThermodynamicSystem(testSystem);
        sample.setReference(Double.toString(testSystem.getTemperature()));
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      logger.error("database error" + ex);
    }

    SampleSet sampleSet = new SampleSet(sampleList);
    optim.setSampleSet(sampleSet);
    optim.solve();
    optim.displayCurveFit();
    optim.displayResult();
  }
}
