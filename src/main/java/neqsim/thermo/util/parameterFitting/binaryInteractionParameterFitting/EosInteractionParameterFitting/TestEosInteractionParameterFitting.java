package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.EosInteractionParameterFitting;

import java.sql.ResultSet;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * TestEosInteractionParameterFitting class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestEosInteractionParameterFitting {
    static Logger logger = LogManager.getLogger(TestEosInteractionParameterFitting.class);

    /**
     * <p>
     * Constructor for TestEosInteractionParameterFitting.
     * </p>
     */
    public TestEosInteractionParameterFitting() {}

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
                "SELECT * FROM binaryequilibriumdata WHERE Component1='methane' AND Component2='ethane'");
        // ResultSet dataSet = database.getResultSet( "SELECT * FROM
        // activityCoefficientTable WHERE Component1='MDEA' AND Component2='water'");

        try {
            logger.info("adding....");
            while (dataSet.next()) {
                EosInteractionParameterFittingFunction function =
                        new EosInteractionParameterFittingFunction();
                double guess[] = {0.01};
                function.setInitialGuess(guess);

                SystemInterface testSystem = new SystemSrkEos(280, 0.01);
                testSystem.addComponent(dataSet.getString("Component1"), 1.0);
                testSystem.addComponent(dataSet.getString("Component2"), 1.0);
                testSystem.setMixingRule(2);
                testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                double sample1[] = {Double.parseDouble(dataSet.getString("x1")),
                        Double.parseDouble(dataSet.getString("y1"))}; // temperature
                double standardDeviation1[] = {0.01, 0.01};
                SampleValue sample = new SampleValue(0.0, 0.01, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        SampleSet sampleSet = new SampleSet(sampleList);
        optim.setSampleSet(sampleSet);

        // do simulations
        optim.solve();
        optim.displayCurveFit();
        // optim.runMonteCarloSimulation();
        // optim.displayResult();
    }
}
