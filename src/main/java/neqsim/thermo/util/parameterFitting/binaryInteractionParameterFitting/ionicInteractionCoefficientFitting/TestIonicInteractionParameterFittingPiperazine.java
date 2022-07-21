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
 * TestIonicInteractionParameterFittingPiperazine class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestIonicInteractionParameterFittingPiperazine {
  static Logger logger = LogManager.getLogger(TestIonicInteractionParameterFittingPiperazine.class);

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

    double guess[] = {-0.0001868490, -0.0006868943, -0.0000210224, -0.0002324934, 0.0005};
    ResultSet dataSet = database.getResultSet("SELECT * FROM CO2waterMDEAPiperazine");// WHERE
                                                                                      // Temperature<393.15
                                                                                      // AND
                                                                                      // PressureCO2<4");

    try {
      int i = 0;
      while (dataSet.next() && i < 16) {
        i++;
        IonicInteractionParameterFittingFunction function =
            new IonicInteractionParameterFittingFunction();
        SystemInterface testSystem = new SystemFurstElectrolyteEos((273.15 + 25.0), 1.0);
        testSystem.addComponent("CO2", Double.parseDouble(dataSet.getString("x1")));
        testSystem.addComponent("MDEA", Double.parseDouble(dataSet.getString("x3")));
        testSystem.addComponent("piperazine", Double.parseDouble(dataSet.getString("x4")));
        testSystem.addComponent("water", Double.parseDouble(dataSet.getString("x2")));
        double temperature = Double.parseDouble(dataSet.getString("Temperature"));
        double pressure = Double.parseDouble(dataSet.getString("PressureCO2"));
        testSystem.setTemperature(temperature);
        testSystem.setPressure(pressure + 1.0);
        testSystem.chemicalReactionInit();
        testSystem.createDatabase(true);
        testSystem.setMixingRule(4);
        testSystem.init(0);
        double sample1[] = {testSystem.getPhase(0).getComponent(0).getNumberOfmoles()
            / testSystem.getPhase(0).getComponent(1).getNumberOfmoles()};
        double standardDeviation1[] = {0.01};
        double stddev = pressure;// Double.parseDouble(dataSet.getString("StandardDeviation"))
        SampleValue sample = new SampleValue(pressure, stddev, sample1, standardDeviation1);
        function.setInitialGuess(guess);
        // function.setBounds(bounds);
        sample.setFunction(function);
        sample.setReference(dataSet.getString("Reference"));
        sample.setThermodynamicSystem(testSystem);
        sampleList.add(sample);
      }
    } catch (Exception e) {
      logger.error("database error" + e);
    }

    SampleSet sampleSet = new SampleSet(sampleList);
    optim.setSampleSet(sampleSet);

    // do simulations

    // optim.solve();
    // optim.runMonteCarloSimulation();
    // optim.displayCurveFit();
    // optim.displayGraph();
    optim.displayCurveFit();
    optim.writeToTextFile("c:/testFit.txt");
  }
}
