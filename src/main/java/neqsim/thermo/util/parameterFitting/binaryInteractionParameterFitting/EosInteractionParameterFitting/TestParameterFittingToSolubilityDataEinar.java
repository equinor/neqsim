package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.EosInteractionParameterFitting;

import java.sql.ResultSet;
import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * TestParameterFittingToSolubilityDataEinar class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestParameterFittingToSolubilityDataEinar {
    static Logger logger = LogManager.getLogger(TestParameterFittingToSolubilityDataEinar.class);

    /**
     * <p>
     * main.
     * </p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database.getResultSet(
                "SELECT * FROM binarysolubilitydataeinar WHERE ComponentSolute='methane' AND ComponentSolvent='Water'");

        try {
            int p = 0;
            logger.info("adding....");
            while (dataSet.next() && p < 4000) {
                p++;
                CPAParameterFittingToSolubilityData function =
                        new CPAParameterFittingToSolubilityData();

                SystemInterface testSystem = new SystemPrEos(290, 1.0);
                testSystem.addComponent("methane", 1.0);
                testSystem.addComponent("water", 10.0);
                testSystem.createDatabase(true);
                testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")) / 1.0e5);
                testSystem.setMixingRule(2);
                testSystem.init(0);
                double sample1[] = {testSystem.getPressure(), testSystem.getTemperature()}; // temperature
                double standardDeviation1[] =
                        {testSystem.getPressure() / 100.0, testSystem.getTemperature() / 100.0};
                SampleValue sample = new SampleValue(Double.parseDouble(dataSet.getString("x1")),
                        Double.parseDouble(dataSet.getString("StandardDeviation")), sample1,
                        standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(Double.toString(testSystem.getTemperature()));
                // double parameterGuess[] = {-0.130}; //srk
                double parameterGuess[] = {0.0000001};
                function.setInitialGuess(parameterGuess);
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        SampleSet sampleSet = new SampleSet(sampleList);
        optim.setSampleSet(sampleSet);

        // do simulations
        optim.solve();
        // optim.runMonteCarloSimulation();
        // optim.displayResult();
        optim.displayCurveFit();
        optim.writeToTextFile("c:/testFit.txt");
    }
}
