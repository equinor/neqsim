package neqsim.thermo.util.parameterFitting.Procede.Density;

import java.sql.ResultSet;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkSchwartzentruberEos;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * TestRackettZ class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestRackettZ {
    static Logger logger = LogManager.getLogger(TestRackettZ.class);

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
        ResultSet dataSet = database
                .getResultSet("SELECT * FROM PureComponentDensity WHERE ComponentName = 'Water'");

        try {
            logger.info("adding....");
            while (dataSet.next()) {
                RackettZ function = new RackettZ();
                // double guess[] = {0.2603556815}; //MDEA
                double guess[] = {0.2356623744}; // Water
                function.setInitialGuess(guess);

                double T = Double.parseDouble(dataSet.getString("Temperature"));
                double P = Double.parseDouble(dataSet.getString("Pressure"));
                double density = Double.parseDouble(dataSet.getString("Density"));

                SystemInterface testSystem = new SystemSrkSchwartzentruberEos(T, P);
                testSystem.addComponent("water", 1.0);

                testSystem.createDatabase(true);
                testSystem.useVolumeCorrection(true);
                testSystem.setMixingRule(4);
                testSystem.init(0);
                testSystem.init(1);

                double sample1[] = {T}; // temperature
                double standardDeviation1[] = {T / 100}; // std.dev temperature // presure std.dev
                                                         // pressure

                SampleValue sample =
                        new SampleValue(density, density / 100.0, sample1, standardDeviation1);
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
    }
}
