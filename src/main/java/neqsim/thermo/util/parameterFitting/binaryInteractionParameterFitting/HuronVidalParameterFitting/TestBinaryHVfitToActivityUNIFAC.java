package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.HuronVidalParameterFitting;

import java.util.ArrayList;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkSchwartzentruberEos;
import neqsim.thermo.system.SystemUNIFACpsrk;

/**
 * <p>TestBinaryHVfitToActivityUNIFAC class.</p>
 *
 * @author Even Solbraa
 */
public class TestBinaryHVfitToActivityUNIFAC implements Cloneable {


    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {

        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

        // inserting samples from database
        int numb = 0;
        for (int i = 0; i < 20; i++) {
            numb++;
            BinaryHVparameterFitToActivityCoefficientFunction function =
                    new BinaryHVparameterFitToActivityCoefficientFunction();
            SystemInterface testSystem = new SystemSrkSchwartzentruberEos(273.0 + i * 5, 1.01);
            testSystem.addComponent("Piperazine", 1.0);
            testSystem.addComponent("AceticAcid", 1000.0);
            testSystem.createDatabase(true);
            // testSystem.chemicalReactionInit();
            testSystem.setMixingRule(4);
            testSystem.init(0);

            SystemInterface testSystem2 = new SystemUNIFACpsrk(273.0 + i * 5, 1.01);
            testSystem2.addComponent("Piperazine", 1.0);
            testSystem2.addComponent("AceticAcid", 1000.0);

            testSystem2.createDatabase(true);
            testSystem2.setMixingRule(2);
            testSystem2.init(0);
            testSystem2.init(1);
            double activ = testSystem2.getPhase(1).getActivityCoefficient(0);
            // System.out.println("activity " + activ);

            // double parameterGuess[] ={4600.679072303, -1200.64471708, -3.89,
            // 1.9};//Piperazine - water
            double parameterGuess[] = {-460.679072303, 120.64471708, -3.89, 1.9};// Piuperazine
                                                                                 // -AceticAcid
            function.setInitialGuess(parameterGuess);
            double sample1[] = {numb / 1000.0, testSystem.getTemperature()};
            double standardDeviation1[] = {1.0};
            SampleValue sample =
                    new SampleValue(activ, activ / 1000.0, sample1, standardDeviation1);
            sample.setFunction(function);
            sample.setThermodynamicSystem(testSystem);
            sampleList.add(sample);
        }

        SampleSet sampleSet = new SampleSet(sampleList);
        optim.setSampleSet(sampleSet);

        // do simulations
        optim.solve();
        // optim.runMonteCarloSimulation();
        optim.displayCurveFit();
        optim.writeToTextFile("c:/testFit.txt");
    }
}
