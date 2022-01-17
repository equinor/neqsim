package neqsim.statistics.monteCarloSimulation.util.example;

import java.util.ArrayList;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;

/**
 * <p>
 * TestLevenbergMarquardt_MonteCarlo class.
 * </p>
 *
 * @author Even Solbraa
 * @since 2.2.3
 * @version $Id: $Id
 */
public class TestLevenbergMarquardt_MonteCarlo {
    /**
     * <p>
     * main.
     * </p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

        TestFunction function = new TestFunction();

        double sample1[] = {0.1};
        double standardDeviation1[] = {0.1};
        SampleValue sample_01 = new SampleValue(0.5, 0.05, sample1, standardDeviation1);
        sample_01.setFunction(function);
        sampleList.add(sample_01);

        double sample2[] = {0.2};
        double standardDeviation2[] = {0.2};
        SampleValue sample_02 = new SampleValue(0.3, 0.03, sample2, standardDeviation2);
        sample_02.setFunction(function);
        sampleList.add(sample_02);

        double sample3[] = {0.3};
        double standardDeviation3[] = {0.3};
        SampleValue sample_03 = new SampleValue(0.1, 0.01, sample3, standardDeviation3);
        sample_03.setFunction(function);
        sampleList.add(sample_03);

        double guess[] = {0.311, 1.0};
        function.setInitialGuess(guess);

        LevenbergMarquardt optim = new LevenbergMarquardt();
        SampleSet sampleSet = new SampleSet(sampleList);
        optim.setSampleSet(sampleSet);

        optim.solve();
        optim.runMonteCarloSimulation();
        optim.displayCurveFit();
    }
}
