package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.acentricFactorFitting;

import java.sql.ResultSet;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.database.NeqSimExperimentDatabase;

/**
 * <p>TestClassicAcentric class.</p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestClassicAcentric {
    static Logger logger = LogManager.getLogger(TestClassicAcentric.class);

    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    @SuppressWarnings("unused")
    public static void main(String[] args) {
        ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

        // inserting samples from database
        NeqSimExperimentDatabase database = new NeqSimExperimentDatabase();
        // ResultSet dataSet = database.getResultSet( "SELECT * FROM
        // PureComponentVapourPressures WHERE ComponentName='methane' AND
        // VapourPressure<100");
        // ResultSet dataSet = database.getResultSet( "SELECT * FROM
        // activityCoefficientTable WHERE Component1='MDEA' AND Component2='water'");

        // neqsim.util.database.NeqSimExperimentDatabase.setConnectionString("jdbc:mysql://tr-w33:3306/neqsimexperimentaldata");

        ResultSet dataSet = database.getResultSet(
                "SELECT * FROM purecomponentvapourpressures WHERE ComponentName='water' AND VapourPressure<100");

        try {
            while (dataSet.next()) {
                ClassicAcentricFunction function = new ClassicAcentricFunction();
                double guess[] = {0.3311};
                double bound[][] = {{0, 1.0},};
                function.setInitialGuess(guess);

                SystemInterface testSystem = new SystemSrkEos(280, 0.001);
                testSystem.addComponent(dataSet.getString("ComponentName"), 100.0); // legger
                                                                                    // komponenter
                                                                                    // til systemet
                double sample1[] = {Double.parseDouble(dataSet.getString("Temperature"))}; // temperature
                double vappres = Double.parseDouble(dataSet.getString("VapourPressure"));
                double standardDeviation1[] = {0.15}; // std.dev temperature // presure std.dev
                                                      // pressure
                SampleValue sample = new SampleValue(Math.log(vappres),
                        Double.parseDouble(dataSet.getString("StandardDeviation")), sample1,
                        standardDeviation1);
                sample.setFunction(function);
                function.setInitialGuess(guess);
                // testSystem.createDatabase(true);
                // function.setBounds(bound);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(dataSet.getString("Reference"));
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        SampleSet sampleSet = new SampleSet(sampleList);

        LevenbergMarquardt optim = new LevenbergMarquardt();
        optim.setSampleSet(sampleSet);

        // do simulations
        optim.solve();
        // optim.runMonteCarloSimulation(50);
        optim.displayCurveFit();
        // optim.writeToTextFile("c:/test.txt");
    }
}
