package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.AntoineParameter;

import java.sql.ResultSet;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.util.database.NeqSimDataBase;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class TestSolidAntoine {
    static Logger logger = LogManager.getLogger(TestSolidAntoine.class);

    public static void main(String[] args) {
        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();

        // ResultSet dataSet = database.getResultSet( "SELECT * FROM
        // BinaryFreezingPointData WHERE ComponentSolvent1='MEG' ORDER BY
        // FreezingTemperature");
        ResultSet dataSet = database.getResultSet(
                "SELECT * FROM BinaryFreezingPointData WHERE ComponentSolvent1='MEG' ORDER BY FreezingTemperature");
        int i = 0;
        try {
            while (dataSet.next() && i < 4) {
                i++;
                AntoineSolidFunction function = new AntoineSolidFunction();
                double guess[] = {-7800.0, 10.09}; // MEG
                function.setInitialGuess(guess);

                SystemInterface testSystem = new SystemSrkCPAstatoil(280, 1.101);
                testSystem.addComponent(dataSet.getString("ComponentSolvent1"),
                        Double.parseDouble(dataSet.getString("x1")));
                testSystem.addComponent(dataSet.getString("ComponentSolvent2"),
                        Double.parseDouble(dataSet.getString("x2")));
                testSystem.createDatabase(true);
                testSystem.setMixingRule(7);

                testSystem.setSolidPhaseCheck("MEG");
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
        // optim.solve();
        // optim.runMonteCarloSimulation();
        optim.displayCurveFit();
        optim.writeToCdfFile("c:/testFit.nc");
        optim.writeToTextFile("c:/testFit.txt");
    }
}
