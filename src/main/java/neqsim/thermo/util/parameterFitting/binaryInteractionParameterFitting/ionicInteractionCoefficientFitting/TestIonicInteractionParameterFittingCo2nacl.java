package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.ionicInteractionCoefficientFitting;

import java.sql.ResultSet;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * TestIonicInteractionParameterFittingCo2nacl class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestIonicInteractionParameterFittingCo2nacl {
  static Logger logger = LogManager.getLogger(TestIonicInteractionParameterFittingCo2nacl.class);

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
    ResultSet dataSet = database.getResultSet(
        "SELECT * FROM co2wation WHERE comp3='K+' AND temperature>340 AND pressure<190");
    // ResultSet dataSet = database.getResultSet( "SELECT * FROM
    // activityCoefficientTable WHERE Component1='MDEA' AND Component2='water'");AND
    // Reference='Lemoine2000'
    // double guess[] = {-0.0000110329, -0.1238487876}; //Na+
    // double guess[] = {0.0000258505}; //Na+ all temp
    // double guess[] = {0.0000080642}; //Na+ 40
    // double guess[] = {0.0000542456}; //Na+ 80
    // double guess[] = { -0.0000442947, -0.1933846606}; //k+
    // double guess[] = {-0.00000650867}; //k+ 40
    double guess[] = {0.0000267226}; // k+ 80

    try {
      int i = 0;
      while (dataSet.next() && i < 403) {
        i++;
        IonicInteractionParameterFittingFunctionCo2nacl function =
            new IonicInteractionParameterFittingFunctionCo2nacl();
        double temperature = Double.parseDouble(dataSet.getString("temperature"));
        double pressure = Double.parseDouble(dataSet.getString("pressure"));
        SystemInterface testSystem = new SystemFurstElectrolyteEos(temperature, pressure);
        testSystem.addComponent("CO2", 10.1);
        testSystem.addComponent("water", 1.0, "kg/sec");
        testSystem.addComponent(dataSet.getString("comp3"),
            Double.parseDouble(dataSet.getString("x3-molal")));
        testSystem.addComponent(dataSet.getString("comp4"),
            Double.parseDouble(dataSet.getString("x4-molal")));
        // testSystem.createDatabase(true);
        testSystem.setPressure(pressure);
        testSystem.setTemperature(temperature);
        testSystem.setMixingRule(4);
        testSystem.init(0);
        double sample1[] = {pressure};
        double standardDeviation1[] = {0.01};
        double value = Double.parseDouble(dataSet.getString("x1-molfrac"));
        SampleValue sample = new SampleValue(value, value / 100.0, sample1, standardDeviation1);
        function.setInitialGuess(guess);
        sample.setFunction(function);
        sample.setDescription(Double.toString(pressure));
        sample.setThermodynamicSystem(testSystem);
        sampleList.add(sample);
      }
    } catch (Exception e) {
      logger.error("database error" + e);
    }

    SampleSet sampleSet = new SampleSet(sampleList);
    optim.setSampleSet(sampleSet);

    // do simulations

    optim.solve();
    // optim.runMonteCarloSimulation();
    // optim.displayCurveFit();
    // optim.displayGraph();
    optim.displayCurveFit();
    optim.writeToTextFile("c:/testFit.txt");
  }
}
