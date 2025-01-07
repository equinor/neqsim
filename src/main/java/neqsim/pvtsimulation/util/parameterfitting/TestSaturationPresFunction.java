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
 * TestSaturationPresFunction class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestSaturationPresFunction {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(TestSaturationPresFunction.class);

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
      int i = 0;
      while (i < 1) {
        SaturationPressureFunction function = new SaturationPressureFunction();
        double[] guess = {341.6 / 1000.0};
        function.setInitialGuess(guess);

        SystemInterface tempSystem = new SystemSrkEos(273.15 + 120, 100.0);
        tempSystem.addComponent("nitrogen", 0.34);
        tempSystem.addComponent("CO2", 3.59);
        tempSystem.addComponent("methane", 67.42);
        tempSystem.addComponent("ethane", 9.02);
        tempSystem.addComponent("propane", 4.31);
        tempSystem.addComponent("i-butane", 0.93);
        tempSystem.addComponent("n-butane", 1.71);
        tempSystem.addComponent("i-pentane", 0.74);
        tempSystem.addComponent("n-pentane", 0.85);
        tempSystem.addComponent("n-hexane", 1.38);
        tempSystem.addTBPfraction("C7", 1.5, 109.00 / 1000.0, 0.6912);
        tempSystem.addTBPfraction("C8", 1.69, 120.20 / 1000.0, 0.7255);
        tempSystem.addTBPfraction("C9", 1.14, 129.5 / 1000.0, 0.7454);
        tempSystem.addTBPfraction("C10", 0.8, 135.3 / 1000.0, 0.7864);
        tempSystem.addPlusFraction("C11", 4.58, 341.2 / 1000.0, 0.8398);
        tempSystem.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(12);
        // tempSystem.getCharacterization().characterisePlusFraction();
        tempSystem.createDatabase(true);
        tempSystem.setMixingRule(2);
        tempSystem.init(0);
        tempSystem.init(1);

        double[] sample1 = {273.15 + 100};
        double satPres = 400.0;
        double[] standardDeviation1 = {1.5};
        SampleValue sample = new SampleValue(satPres, satPres / 100.0, sample1, standardDeviation1);
        sample.setFunction(function);
        function.setInitialGuess(guess);
        sample.setThermodynamicSystem(tempSystem);
        sampleList.add(sample);

        i++;
      }
    } catch (Exception ex) {
      logger.error("database error", ex);
    }

    SampleSet sampleSet = new SampleSet(sampleList);

    LevenbergMarquardt optim = new LevenbergMarquardt();
    optim.setMaxNumberOfIterations(5);

    optim.setSampleSet(sampleSet);
    optim.solve();
    optim.displayCurveFit();
  }
}
