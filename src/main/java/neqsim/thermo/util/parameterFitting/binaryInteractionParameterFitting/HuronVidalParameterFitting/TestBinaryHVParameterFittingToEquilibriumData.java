package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.HuronVidalParameterFitting;

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
 * <p>TestBinaryHVParameterFittingToEquilibriumData class.</p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestBinaryHVParameterFittingToEquilibriumData {

    static Logger logger =
            LogManager.getLogger(TestBinaryHVParameterFittingToEquilibriumData.class);


    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {

        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database.getResultSet(
                "SELECT * FROM BinaryEquilibriumData WHERE Component1='methane' AND Component2='ethane'");
        // ResultSet dataSet = database.getResultSet( "SELECT * FROM
        // activityCoefficientTable WHERE Component1='MDEA' AND Component2='water'");

        try {

            logger.info("adding....");
            while (dataSet.next()) {
                BinaryHVParameterFittingToEquilibriumData function =
                        new BinaryHVParameterFittingToEquilibriumData();
                double guess[] = {1000, 1000};
                function.setInitialGuess(guess);

                SystemInterface testSystem = new SystemSrkEos(280, 0.01);
                testSystem.addComponent(dataSet.getString("Component1"), 1.0);
                testSystem.addComponent(dataSet.getString("Component2"), 1.0);
                testSystem.setMixingRule(3);
                testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                double sample1[] = {Double.parseDouble(dataSet.getString("x1")),
                        Double.parseDouble(dataSet.getString("y1"))}; // temperature
                double standardDeviation1[] = {0.01, 0.01}; // std.dev temperature // presure
                                                            // std.dev pressure
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
        // optim.runMonteCarloSimulation();
        optim.displayResult();
    }
}
