package neqsim.pvtsimulation.util.parameterfitting;

import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterfitting.SampleSet;
import neqsim.statistics.parameterfitting.SampleValue;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * TestWaxTuning class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestWaxTuning {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestWaxTuning.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

    try {
      System.out.println("adding....");
      int iterations = 0;
      while (iterations < 1) {
        iterations = iterations + 1;
        WaxFunction function = new WaxFunction();
        double[] guess = {1.074, 6.584e-4, 0.1915};
        function.setInitialGuess(guess);

        SystemInterface tempSystem = new SystemSrkEos(273.15 + 20, 10.0);
        tempSystem.addComponent("methane", 67.42);
        tempSystem.addTBPfraction("C10", 10.8, 135.3 / 1000.0, 0.7864);
        tempSystem.addPlusFraction("C11", 12.58, 456.2 / 1000.0, 0.89398);
        tempSystem.getCharacterization().characterisePlusFraction();
        tempSystem.getWaxModel().addTBPWax();
        tempSystem.createDatabase(true);
        tempSystem.setMixingRule(2);
        tempSystem.addSolidComplexPhase("wax");
        tempSystem.setMultiphaseWaxCheck(true);
        tempSystem.init(0);
        tempSystem.init(1);

        double[] sample1 = {273.15 + 20.0};
        double waxContent = 2.0;
        double[] standardDeviation1 = {1.5};
        SampleValue sample =
            new SampleValue(waxContent, waxContent / 100.0, sample1, standardDeviation1);
        sample.setFunction(function);
        function.setInitialGuess(guess);
        sample.setThermodynamicSystem(tempSystem);
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      logger.error("database error", ex);
    }

    SampleSet sampleSet = new SampleSet(sampleList);

    LevenbergMarquardt optim = new LevenbergMarquardt();
    optim.setMaxNumberOfIterations(5);

    optim.setSampleSet(sampleSet);
    // optim.solve();
    optim.displayCurveFit();
  }
}
