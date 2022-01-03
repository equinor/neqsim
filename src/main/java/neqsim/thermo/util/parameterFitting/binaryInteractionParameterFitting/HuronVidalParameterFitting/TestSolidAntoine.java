package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.HuronVidalParameterFitting;

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
 * <p>TestSolidAntoine class.</p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestSolidAntoine {

    static Logger logger = LogManager.getLogger(TestSolidAntoine.class);

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
                "SELECT * FROM BinaryFreezingPointData WHERE ComponentSolvent1='MEG' ORDER BY FreezingTemperature");

        try {
            while (dataSet.next()) {
                FreezeSolidFunction function = new FreezeSolidFunction();
                double guess[] = {-7566.84658558, 17.38710706}; // MEG
                function.setInitialGuess(guess);

                SystemInterface testSystem = new SystemSrkSchwartzentruberEos(280, 1.101);
                testSystem.addComponent(dataSet.getString("ComponentSolvent1"),
                        Double.parseDouble(dataSet.getString("x1")));
                testSystem.addComponent(dataSet.getString("ComponentSolvent2"),
                        Double.parseDouble(dataSet.getString("x2")));
                // testSystem.createDatabase(true);
                testSystem.setSolidPhaseCheck(true);
                testSystem.setMixingRule(4);
                testSystem.init(0);
                double sample1[] = {testSystem.getPhase(0).getComponent(0).getz()}; // temperature
                double standardDeviation1[] = {0.1, 0.1, 0.1}; // std.dev temperature // presure
                                                               // std.dev pressure
                double val = Double.parseDouble(dataSet.getString("FreezingTemperature"));
                testSystem.setTemperature(val);
                SampleValue sample = new SampleValue(val, val / 100.0, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setReference(dataSet.getString("Reference"));
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
        optim.displayCurveFit();
        optim.writeToCdfFile("c:/testFit.nc");
        optim.writeToTextFile("c:/testFit.txt");
    }
}
