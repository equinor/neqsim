package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.CharacterisationParameters;

import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * <p>TestCharacterisation class.</p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestCharacterisation {

    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {

        LevenbergMarquardt optim = new LevenbergMarquardt();

        CharacterisationFunction function = new CharacterisationFunction();
        double guess[] = {0.001};
        function.setInitialGuess(guess);

        SystemInterface testSystem1 = new SystemPrEos(140, 2.0);
        testSystem1.addComponent("methane", 78.81102);
        testSystem1.init(0);
        testSystem1.init(1);

        SampleValue[] sample = new SampleValue[2];

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
        sample[1].setThermodynamicSystem(testSystem1);

        SampleSet sampleSet = new SampleSet(sample);
        optim.setSampleSet(sampleSet);

        optim.solve();
        optim.displayResult();
    }
}
