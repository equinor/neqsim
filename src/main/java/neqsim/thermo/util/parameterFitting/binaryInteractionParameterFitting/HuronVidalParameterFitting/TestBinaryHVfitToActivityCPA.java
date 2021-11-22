package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.HuronVidalParameterFitting;

import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAs;
import neqsim.thermo.system.SystemSrkEos;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class TestBinaryHVfitToActivityCPA implements Cloneable {
    static Logger logger = LogManager.getLogger(TestBinaryHVfitToActivityCPA.class);

    public static void main(String[] args) {
        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

        // double parameterGuess[] ={2012.8210952954, -1074.4698351045, 5.5675858688, -1.0503110172,
        // -0.0082939895};//, 1.3505557460};//, 0.1169806819}; /SRK-EOS
        // double parameterGuess[] ={1506.3, -863.3, 4.11, -0.603, -0.0145};//,
        // 1.3505557460};//, 0.1169806819}; /PR-EOS
        double parameterGuess[] = {-359.2, 351.7, -1.31, 2.44, 0.25}; // PVTsim SRK
        // double parameterGuess[] ={ -446.3717107738, 523.8799876586, -1.2101168104,
        // 1.1754366244, 0.1726026869}; // fitted MeOH

        // inserting samples from database
        int numb = 0;
        for (int i = 0; i < 1; i++) {
            numb++;
            for (int j = 0; j < 10; j++) {
                BinaryHVparameterFitToActivityCoefficientFunction function =
                        new BinaryHVparameterFitToActivityCoefficientFunction();
                SystemInterface testSystem = new SystemSrkEos(268.15 + 10.0 * i, 1.0);
                testSystem.addComponent("water", 100.0 - j * j);
                // testSystem.addComponent("MEG", 1.0+j*j);
                testSystem.addComponent("methanolPVTsim", 1.0 + j * j);
                testSystem.createDatabase(true);
                // testSystem.chemicalReactionInit();
                testSystem.setMixingRule(4);
                testSystem.init(0);

                SystemInterface testSystem2 = new SystemSrkCPAs(268.15 + 10.0 * i, 1.0);
                testSystem2.addComponent("water", 100.0 - j * j);
                // testSystem2.addComponent("MEG",1.0+j*j);
                testSystem2.addComponent("methanol", 1.0 + j * j);

                testSystem2.createDatabase(true);
                testSystem2.setMixingRule(9);
                testSystem2.init(0);
                testSystem2.init(2);
                double activ = testSystem2.getPhase(1).getActivityCoefficient(0);
                logger.info("activity " + activ + " molfraction MEG "
                        + testSystem2.getPhase(1).getComponent("methanol").getx());

                function.setInitialGuess(parameterGuess);
                double sample1[] = {testSystem2.getPhase(1).getComponent("methanol").getx(),
                        testSystem.getTemperature()};
                double standardDeviation1[] = {1.0};
                SampleValue sample =
                        new SampleValue(activ, activ / 100.0, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sampleList.add(sample);
            }
        }

        for (int i = 0; i < 0; i++) {
            numb++;
            for (int j = 0; j < 10; j++) {
                BinaryHVparameterFitToActivityCoefficientFunction function =
                        new BinaryHVparameterFitToActivityCoefficientFunction();
                SystemInterface testSystem = new SystemPrEos(300.0 + 20.0 * i, 1.0);
                testSystem.addComponent("water", 100.0 - j * j);
                // testSystem.addComponent("MEG", 1.0+j*j);
                testSystem.addComponent("methanolPVTsim", 1.0 + j * j);
                testSystem.createDatabase(true);
                // testSystem.chemicalReactionInit();
                testSystem.setMixingRule(4);
                testSystem.init(0);

                SystemInterface testSystem2 = new SystemSrkCPAs(300.0 + 20.0 * i, 1.0);
                testSystem2.addComponent("water", 100.0 - j * j);
                // testSystem2.addComponent("MEG",1.0+j*j);
                testSystem.addComponent("methanolPVTsim", 1.0 + j * j);

                // testSystem2.createDatabase(true);
                testSystem2.setMixingRule(7);
                testSystem2.init(0);
                testSystem2.init(2);
                double activ = testSystem2.getPhase(1).getActivityCoefficient(0);
                logger.info("activity " + activ + " molfraction MEG "
                        + testSystem2.getPhase(1).getComponent("MEG").getx());

                function.setInitialGuess(parameterGuess);
                double sample1[] = {numb / 1000.0, testSystem.getTemperature()};
                double standardDeviation1[] = {1.0};
                SampleValue sample =
                        new SampleValue(activ, activ / 100.0, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sampleList.add(sample);
            }
        }

        for (int i = 0; i < 0; i++) {
            numb++;
            for (int j = 0; j < 0; j++) {
                BinaryHVparameterFitToActivityCoefficientFunction function =
                        new BinaryHVparameterFitToActivityCoefficientFunction();
                SystemInterface testSystem = new SystemPrEos(253.0 + 20.0 * i, 1.0);
                // testSystem.addComponent("MEG", 1.0+j*10);
                testSystem.addComponent("methanolPVTsim", 1.0 + j * j);
                testSystem.addComponent("water", 100.0 - j * 10);
                testSystem.createDatabase(true);
                // testSystem.chemicalReactionInit();
                testSystem.setMixingRule(4);
                testSystem.init(0);

                SystemInterface testSystem2 = new SystemSrkCPAs(253.0 + 20.0 * i, 1.0);
                // testSystem2.addComponent("MEG",1.0+j*10);
                testSystem.addComponent("methanolPVTsim", 1.0 + j * j);
                testSystem2.addComponent("water", 100.0 - j * 10);
                // testSystem2.createDatabase(true);
                testSystem2.setMixingRule(7);
                testSystem2.init(0);
                testSystem2.init(2);
                double activ = testSystem2.getPhase(1).getActivityCoefficient(0);
                logger.info("activity " + activ + " molfraction MEG "
                        + testSystem2.getPhase(1).getComponent("MEG").getx());
                function.setInitialGuess(parameterGuess);
                double sample1[] = {numb / 1000.0, testSystem.getTemperature()};
                double standardDeviation1[] = {1.0};
                SampleValue sample =
                        new SampleValue(activ, activ / 100.0, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sampleList.add(sample);
            }
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
