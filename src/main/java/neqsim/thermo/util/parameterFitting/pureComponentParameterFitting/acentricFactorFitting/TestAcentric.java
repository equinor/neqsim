/*
 * TestAcentric.java
 *
 * Created on 23. januar 2001, 22:08
 */

package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.acentricFactorFitting;

import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * <p>
 * TestAcentric class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestAcentric {
  /**
   * <p>
   * Constructor for TestAcentric.
   * </p>
   */
  public TestAcentric() {}

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String[] args) {
    LevenbergMarquardt optim = new LevenbergMarquardt();

    AcentricFunction function = new AcentricFunction();
    double guess[] = {0.001};
    function.setInitialGuess(guess);

    SystemInterface testSystem1 = new SystemPrEos(140, 2.0);
    testSystem1.addComponent("methane", 78.81102);
    testSystem1.init(0);
    testSystem1.init(1);

    SystemInterface testSystem2 = new SystemPrEos(140, 2.0);
    testSystem2.addComponent("methane", 78.81102);
    testSystem2.init(0);
    testSystem2.init(1);

    SystemInterface testSystem3 = new SystemPrEos(140, 2.0);
    testSystem3.addComponent("methane", 78.81102);
    testSystem3.init(0);
    testSystem3.init(1);

    SampleValue[] sample = new SampleValue[4];
    double sample1[] = {120};
    double standardDeviation1[] = {0.001};
    sample[0] = new SampleValue(1.916, 0.01, sample1, standardDeviation1);
    sample[0].setFunction(function);
    sample[0].setThermodynamicSystem(testSystem1);

    // creating the sampleset
    double sample2[] = {130};
    double standardDeviation2[] = {0.001};
    sample[1] = new SampleValue(3.676, 0.01, sample2, standardDeviation2);
    sample[1].setFunction(function);
    sample[1].setThermodynamicSystem(testSystem2);

    double sample3[] = {140};
    double standardDeviation3[] = {0.001};
    sample[2] = new SampleValue(6.416, 0.01, sample3, standardDeviation3);
    sample[2].setFunction(function);
    sample[2].setThermodynamicSystem(testSystem3);

    double sample4[] = {150};
    double standardDeviation4[] = {0.001};
    sample[3] = new SampleValue(10.405, 0.01, sample4, standardDeviation4);
    sample[3].setFunction(function);
    sample[3].setThermodynamicSystem(testSystem1);

    SampleSet sampleSet = new SampleSet(sample);
    optim.setSampleSet(sampleSet);

    // do simulations
    optim.solve();
    // optim.runMonteCarloSimulation();
    optim.displayResult();
  }
}
