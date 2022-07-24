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
 * TestIonicInteractionParameterFitting_Sleipner class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestIonicInteractionParameterFitting_Sleipner {
  static Logger logger = LogManager.getLogger(TestIonicInteractionParameterFitting_Sleipner.class);

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

    NeqSimDataBase database = new NeqSimDataBase();
    ResultSet dataSet = database.getResultSet("SELECT * FROM Sleipner");
    // ResultSet dataSet = database.getResultSet( "SELECT * FROM Sleipneracid");

    try {
      int i = 0;
      while (dataSet.next()) {
        i++;
        IonicInteractionParameterFittingFunction_Sleipner function =
            new IonicInteractionParameterFittingFunction_Sleipner();
        // double guess[] = {3.19e-4,-3.12174e-4,-0.2};
        // double guess[]={0.0013916772,-3.951608e-4,-1.8265457361}; //AAD 10.31 %
        // double guess[]={0.0014020738,-3.053262}; //AAD 11.44%
        // double guess[]={0.0011258675,-9.282787e-4,-1.5524349158}; //AAD 10.74 %
        double guess[] = {1.3053127e-3, -2.546896e-4, -0.975168}; // AAD 4.02 %
        double bounds[][] = {{-1e-2, 1e-2}, {-1e-3, 0}, {-1, 1}};

        double ID = Double.parseDouble(dataSet.getString("ID"));
        double pressure = Double.parseDouble(dataSet.getString("Pressure"));
        double temperature = Double.parseDouble(dataSet.getString("Temperature"));
        double x1 = Double.parseDouble(dataSet.getString("x1"));
        double x2 = Double.parseDouble(dataSet.getString("x2"));
        double x3 = Double.parseDouble(dataSet.getString("x3"));
        double x4 = Double.parseDouble(dataSet.getString("x4"));

        SystemInterface testSystem = new SystemFurstElectrolyteEos(temperature, pressure);
        testSystem.addComponent("CO2", x1);
        testSystem.addComponent("AceticAcid", x3);
        testSystem.addComponent("MDEA", x4);
        testSystem.addComponent("water", x2);

        testSystem.chemicalReactionInit();
        testSystem.createDatabase(true);
        testSystem.setMixingRule(4);
        testSystem.init(0);

        double sample1[] = {x1 / x4};
        double standardDeviation1[] = {0.1, 0.01};
        double stddev = 0.01;
        SampleValue sample = new SampleValue(pressure, stddev, sample1, standardDeviation1);
        function.setInitialGuess(guess);
        // function.setBounds(bounds);
        sample.setFunction(function);
        sample.setDescription(Double.toString(ID));
        sample.setThermodynamicSystem(testSystem);
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      logger.error("database error" + ex);
    }

    SampleSet sampleSet = new SampleSet(sampleList);
    optim.setSampleSet(sampleSet);

    optim.solve();

    // optim.displayGraph();
    optim.displayCurveFit();
  }
}
