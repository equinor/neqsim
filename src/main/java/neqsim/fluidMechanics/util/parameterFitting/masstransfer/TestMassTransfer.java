package neqsim.fluidMechanics.util.parameterFitting.masstransfer;

import java.sql.ResultSet;
import java.util.ArrayList;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * TestMassTransfer class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestMassTransfer {
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
    ResultSet dataSet = database.getResultSet(
        "SELECT * FROM purecomponentvapourpressures WHERE ComponentName='water' AND VapourPressure<100");

    try {
      System.out.println("adding....");
      while (dataSet.next()) {
        MassTransferFunction function = new MassTransferFunction();
        double[] guess = {0.3311};
        double bound[][] = {{0, 1.0},};
        function.setInitialGuess(guess);
        SystemInterface testSystem = new SystemSrkEos(280, 0.001);
        testSystem.addComponent(dataSet.getString("ComponentName"), 100.0);
        double[] sample1 = {Double.parseDouble(dataSet.getString("Temperature"))};
        double vappres = Double.parseDouble(dataSet.getString("VapourPressure"));
        double[] standardDeviation1 = {0.15};
        SampleValue sample = new SampleValue(Math.log(vappres),
            Double.parseDouble(dataSet.getString("StandardDeviation")), sample1,
            standardDeviation1);
        sample.setFunction(function);
        function.setInitialGuess(guess);
        // function.setBounds(bound);
        sample.setThermodynamicSystem(testSystem);
        sample.setReference(dataSet.getString("Reference"));
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      System.out.println("database error" + ex);
    }

    SampleSet sampleSet = new SampleSet(sampleList);

    LevenbergMarquardt optim = new LevenbergMarquardt();
    optim.setSampleSet(sampleSet);

    // do simulations
    optim.solve();
    // optim.runMonteCarloSimulation(50);
    // optim.displayCurveFit();
    // optim.writeToTextFile("c:/test.txt");
  }
}
