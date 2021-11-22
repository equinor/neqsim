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
 *
 * @author Even Solbraa
 * @version
 */
public class TestBinaryHVfitToActivityCoefficientDB {
    static Logger logger = LogManager.getLogger(TestBinaryHVfitToActivityCoefficientDB.class);

    public static void main(String[] args) {
        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database.getResultSet(
                "SELECT * FROM activitycoefficienttable WHERE Component1='MDEA' AND Component2='water' AND Temperature>293.15  AND x1<=1.0 ORDER BY Temperature,x1");

        try {
            while (dataSet.next()) {
                BinaryHVparameterFitToActivityCoefficientFunction function =
                        new BinaryHVparameterFitToActivityCoefficientFunction();

                double x1 = Double.parseDouble(dataSet.getString("x1")) * 100;
                SystemInterface testSystem = new SystemSrkSchwartzentruberEos(
                        Double.parseDouble(dataSet.getString("Temperature")),
                        Double.parseDouble(dataSet.getString("Pressure")));
                testSystem.addComponent(dataSet.getString("Component1"), x1); // legger
                                                                              // til
                                                                              // komponenter
                                                                              // til
                                                                              // systemet
                testSystem.addComponent(dataSet.getString("Component2"),
                        Double.parseDouble(dataSet.getString("x2")) * 100);
                // testSystem.chemicalReactionInit();
                // testSystem.createDatabase(true);
                testSystem.setMixingRule(4);
                testSystem.init(0);

                double sample1[] = {x1, testSystem.getTemperature()};
                double standardDeviation1[] = {x1 / 100.0};
                double val = Double.parseDouble(dataSet.getString("gamma1"));
                SampleValue sample = new SampleValue(val, val / 100.0, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(Double.toString(testSystem.getTemperature()));
                // function.setDatabaseParameters();
                // double guess[] = {-1466.3924707953, 1197.4327552750,
                // 5.9188456398,
                // -7.2410712156, 0.2127650110};
                double guess[] = {-1460.6790723030, 1200.6447170870, 5.8929954883, -7.2400706727,
                        0.2131035181};

                function.setInitialGuess(guess);
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        dataSet = database.getResultSet(
                "SELECT * FROM BinaryFreezingPointData WHERE ComponentSolvent1='MDEA' ORDER BY FreezingTemperature");

        try {
            while (!dataSet.next()) {
                FreezeSolidFunction function = new FreezeSolidFunction();
                // double guess[] = {-1466.3924707953, 1197.4327552750,
                // 5.9188456398,
                // -7.2410712156, 0.2127650110};
                double guess[] = {-1460.6790723030, 1200.6447170870, 5.8929954883, -7.2400706727,
                        0.2131035181};

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
                double standardDeviation1[] = {0.1, 0.1, 0.1}; // std.dev
                                                               // temperature //
                                                               // presure
                                                               // std.dev pressure
                double val = Double.parseDouble(dataSet.getString("FreezingTemperature"));
                testSystem.setTemperature(val);
                SampleValue sample = new SampleValue(val, val / 700, sample1, standardDeviation1);
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
        optim.writeToTextFile("c:/testFit.txt");
    }
}
