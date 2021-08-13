/*
 * TestAcentric.java
 *
 * Created on 23. januar 2001, 22:08
 */

package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.HuronVidalParameterFitting;

import java.util.ArrayList;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class TestBinaryHVfitToActivity implements Cloneable {

    private static final long serialVersionUID = 1000;

    /** Creates new TestAcentric */
    public TestBinaryHVfitToActivity() {}

    public static void main(String[] args) {

        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

        // inserting samples from database
        int numb = 0;
        while (numb < 20) {
            numb++;
            BinaryHVparameterFitToActivityCoefficientFunction function =
                    new BinaryHVparameterFitToActivityCoefficientFunction();
            SystemInterface testSystem = new SystemSrkEos(253.0 + numb * 50.0, 1.01);
            // testSystem.addComponent("CO2", numb/100.0); // legger til komponenter til
            // systemet
            testSystem.addComponent("MDEA", 1.0);
            testSystem.addComponent("water", 10.0);
            // testSystem.createDatabase(true);
            // testSystem.chemicalReactionInit();
            testSystem.setMixingRule(4);
            testSystem.init(0);
            double parameterGuess[] = {320.0, -40.0, -5.89, 8.9, 0.3475}; // HV
            function.setInitialGuess(parameterGuess);
            double sample1[] = {numb / 1000.0, testSystem.getTemperature()};
            double standardDeviation1[] = {1.0};
            SampleValue sample = new SampleValue(0.001, 1.0 / 1000.0, sample1, standardDeviation1);
            sample.setFunction(function);
            sample.setThermodynamicSystem(testSystem);
            sampleList.add(sample);
        }

        SampleSet sampleSet = new SampleSet(sampleList);
        optim.setSampleSet(sampleSet);

        // do simulations
        // optim.solve();
        // optim.runMonteCarloSimulation();
        optim.displayCurveFit();
        optim.writeToTextFile("c:/testFit.txt");
    }
}
