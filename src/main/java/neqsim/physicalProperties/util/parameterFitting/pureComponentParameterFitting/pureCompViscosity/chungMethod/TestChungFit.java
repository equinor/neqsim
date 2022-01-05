package neqsim.physicalProperties.util.parameterFitting.pureComponentParameterFitting.pureCompViscosity.chungMethod;

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
 * TestChungFit class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestChungFit {
    static Logger logger = LogManager.getLogger(TestChungFit.class);

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
        ResultSet dataSet = database.getResultSet("SELECT * FROM purecomponentviscosity");// WHERE
                                                                                          // ComponentName='MDEA*'");

        try {
            while (dataSet.next()) {
                ChungFunction function = new ChungFunction();
                double guess[] = {0.3211};
                function.setInitialGuess(guess);

                SystemInterface testSystem = new SystemSrkEos(280, 0.001);
                testSystem.addComponent("MDEA", 100.0);
                // testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                testSystem.createDatabase(true);
                testSystem.init(0);
                testSystem.setMixingRule(2);
                double sample1[] = {Double.parseDouble(dataSet.getString("Temperature"))}; // temperature
                double standardDeviation1[] = {0.1}; // std.dev temperature // presure std.dev
                                                     // pressure
                SampleValue sample =
                        new SampleValue(Double.parseDouble(dataSet.getString("Viscosity")), 0.001,
                                sample1, standardDeviation1);
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
        optim.displayCurveFit();
    }
}
