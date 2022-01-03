package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting;

import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.BinaryEosFunction;

/**
 * <p>TestBinaryEosParameterFit class.</p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestBinaryEosParameterFit {

    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {

        LevenbergMarquardt optim = new LevenbergMarquardt();
        BinaryEosFunction function = new BinaryEosFunction();

        SystemSrkEos system = new SystemSrkEos(140, 5.0);
        system.addComponent("methane", 100);
        system.addComponent("water", 100);
        system.setMixingRule(2);
        system.init(0);
        system.init(1);

        SampleValue[] sample = new SampleValue[5];

        double sample1[] = {280.0, 1.01325};
        double standardDeviation1[] = {1.0e-3, 0.53e-2};
        sample[0] = new SampleValue((1.01325 * 0.9931724), 0.5e-1, sample1, standardDeviation1);
        sample[0].setFunction(function);
        sample[0].setThermodynamicSystem(system);

        double sample2[] = {290.0, 1.01325};
        double standardDeviation2[] = {1.0e-3, 0.53e-2};
        sample[1] = new SampleValue((1.01325 * 0.9861), 0.5e-2, sample2, standardDeviation2);
        sample[1].setFunction(function);
        sample[1].setThermodynamicSystem(system);

        double sample3[] = {330.0, 1.01325};
        double standardDeviation3[] = {1.0e-3, 0.53e-2};
        sample[2] = new SampleValue((1.01325 * 0.9434), 0.5e-2, sample3, standardDeviation3);
        sample[2].setFunction(function);
        sample[2].setThermodynamicSystem(system);

        double sample4[] = {312.0, 1.0};
        double standardDeviation4[] = {1.0e-3, 0.53e-2};
        sample[3] = new SampleValue((1.001325 * 0.9456), 0.5e-2, sample4, standardDeviation4);
        sample[3].setFunction(function);
        sample[3].setThermodynamicSystem(system);

        double sample5[] = {321.0, 1.0};
        double standardDeviation5[] = {1.0e-3, 0.53e-1};
        sample[4] = new SampleValue((3.1001325 * 0.9082), 0.5e-2, sample5, standardDeviation5);
        sample[4].setFunction(function);
        sample[4].setThermodynamicSystem(system);

        SampleSet sampleSet = new SampleSet(sample);
        optim.setSampleSet(sampleSet);

        optim.solve();
        // optim.runMonteCarloSimulation();
    }
}
