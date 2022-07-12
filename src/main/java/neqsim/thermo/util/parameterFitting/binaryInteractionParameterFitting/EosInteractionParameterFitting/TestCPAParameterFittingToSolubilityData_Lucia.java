package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.EosInteractionParameterFitting;

import java.sql.ResultSet;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPA;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * TestCPAParameterFittingToSolubilityData_Lucia class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestCPAParameterFittingToSolubilityData_Lucia {
    static Logger logger =
            LogManager.getLogger(TestCPAParameterFittingToSolubilityData_Lucia.class);

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
                "SELECT * FROM luciadata8 WHERE Component='methane' AND Temperature>410.15 AND Pressure<100000000 AND L2<>NULL AND L2>0.00000001 ORDER BY Temperature,Pressure");// AND
                                                                                                                                                                                 // Reference='Houghton1957'
                                                                                                                                                                                 // AND
                                                                                                                                                                                 // Reference<>'Nighswander1989'
                                                                                                                                                                                 // AND
                                                                                                                                                                                 // Temperature>278.15
                                                                                                                                                                                 // AND
                                                                                                                                                                                 // Temperature<383.15
                                                                                                                                                                                 // AND
                                                                                                                                                                                 // Pressure<60.01325");
        // ResultSet dataSet = database.getResultSet( "SELECT * FROM LuciaData8 WHERE
        // Component='nitrogen' AND Temperature<390 AND L2<>NULL AND L2>0.0000000001
        // ORDER BY Temperature,Pressure");
        // ResultSet dataSet = database.getResultSet( "SELECT * FROM LuciaData8 WHERE
        // Component='CO2' AND Temperature<390 AND L2<>NULL AND L2>0.0000000001 ORDER BY
        // Temperature,Pressure");
        // ResultSet dataSet = database.getResultSet( "SELECT * FROM LuciaData8 WHERE
        // Component='ethane' AND Temperature<390 AND Pressure<10000000 AND L2<>NULL AND
        // L2>0.0000000001 ORDER BY Temperature,Pressure");
        // ResultSet dataSet = database.getResultSet( "SELECT * FROM LuciaData8 WHERE
        // Component='propane' AND Temperature<390 AND Pressure<15000000 AND L2<>NULL
        // AND L2>0.0000000001 AND ID>2204 AND ID<2410 ORDER BY Temperature,Pressure");
        // ResultSet dataSet = database.getResultSet( "SELECT * FROM
        // binarySolubilityData WHERE ComponentSolute='methane' AND
        // ComponentSolvent='water'");

        try {
            int p = 0;
            logger.info("adding....");
            while (!dataSet.next() && p < 50) {
                p++;
                CPAParameterFittingToSolubilityData function =
                        new CPAParameterFittingToSolubilityData();

                SystemInterface testSystem =
                        new SystemSrkCPA(Double.parseDouble(dataSet.getString("Temperature")),
                                Double.parseDouble(dataSet.getString("Pressure")) / 1.0e5);
                // SystemInterface testSystem = new
                // SystemSrkEos(Double.parseDouble(dataSet.getString("Temperature")),
                // Double.parseDouble(dataSet.getString("Pressure"))/1.0e5);
                testSystem.addComponent("methane", 1.0);
                // testSystem.addComponent("propane", 1.0);
                testSystem.addComponent("water", 10.0);
                // testSystem.createDatabase(true);
                testSystem.setMixingRule(7);
                testSystem.init(0);
                double sample1[] = {testSystem.getPressure(), testSystem.getTemperature()};
                double standardDeviation1[] = {0.01};
                double val = Double.parseDouble(dataSet.getString("L2"));
                double sdev = val / 100.0;
                SampleValue sample = new SampleValue(val, sdev, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);// 34.7
                sample.setReference(Double.toString(testSystem.getTemperature()));
                // double parameterGuess[] = {0.05155112588}; //srk
                double parameterGuess[] = {0.0459393339}; // cpa-srk- metan 23.658199 abs dev bias
                                                          // -6.267%
                // double parameterGuess[] = {0.1592294845}; //cpa-pr - metan 21.9
                // double parameterGuess[] = {-0.059201934}; //cpa-srk - nitrogen
                // double parameterGuess[] = {0.1};
                // double parameterGuess[] = {-0.059201934}; //cpa-pr - nitrogen 29.20534 abs
                // bias 23.93392
                // double parameterGuess[] = {-0.0586254634}; //cpa-srk - CO2 abs 18.5043 bias
                // -11.3665
                // double parameterGuess[] = {0.0160496243}; //cpa-pr - CO2
                // double parameterGuess[] = {0.13287685}; //srk

                function.setInitialGuess(parameterGuess);
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error", e);
        }

        // dataSet = database.getResultSet( "SELECT * FROM LuciaData WHERE
        // Component='methane' AND ID<3000 AND Temperature<380 AND Pressure<100000000
        // AND Y<>NULL AND Y>0.00000001 ORDER BY Temperature,Pressure");// AND
        // Reference='Houghton1957' AND Reference<>'Nighswander1989' AND
        // Temperature>278.15 AND Temperature<383.15 AND Pressure<60.01325");
        dataSet = database.getResultSet(
                "SELECT * FROM LuciaData8 WHERE Component='methane' AND Temperature>273.15 AND Pressure<153000000 AND Y<>NULL AND Y>0.000000001 ORDER BY Temperature,Pressure");
        // dataSet = database.getResultSet( "SELECT * FROM LuciaData8 WHERE
        // Component='nitrogen' AND Temperature<390 AND Y<>NULL AND Y>0.000000001 ORDER
        // BY Temperature,Pressure");
        // dataSet = database.getResultSet( "SELECT * FROM LuciaData8 WHERE
        // Component='CO2' AND Temperature>310 AND Y<>NULL AND Y>0.000000001 ORDER BY
        // Temperature,Pressure");
        // dataSet = database.getResultSet( "SELECT * FROM LuciaData8 WHERE
        // Component='ethane' AND Temperature<390 AND Pressure<10000000 AND Y<>NULL AND
        // Y>0.000000001 ORDER BY Temperature,Pressure");
        // dataSet = database.getResultSet( "SELECT * FROM LuciaData8 WHERE
        // Component='propane' AND Temperature<390 AND Pressure<15000000 AND Y<>NULL AND
        // Y>0.000000001 AND ID>2204 AND ID<2410 ORDER BY Temperature,Pressure");
        // ResultSet dataSet = database.getResultSet( "SELECT * FROM
        // binarySolubilityData WHERE ComponentSolute='methane' AND
        // ComponentSolvent='water'");
        try {
            int p = 0;
            logger.info("adding....");
            while (dataSet.next() && p < 150) {
                p++;
                CPAParameterFittingToSolubilityData_Vap function =
                        new CPAParameterFittingToSolubilityData_Vap();

                SystemInterface testSystem =
                        new SystemSrkCPA(Double.parseDouble(dataSet.getString("Temperature")),
                                Double.parseDouble(dataSet.getString("Pressure")) / 1.0e5);
                // SystemInterface testSystem = new
                // SystemSrkEos(Double.parseDouble(dataSet.getString("Temperature")),
                // Double.parseDouble(dataSet.getString("Pressure"))/1.0e5);
                testSystem.addComponent("methane", 1.0);
                // testSystem.addComponent("propane", 1.0);
                testSystem.addComponent("water", 10.0);
                // testSystem.createDatabase(true);
                testSystem.init(0);
                testSystem.setMixingRule(7);

                double sample1[] = {testSystem.getPressure(), testSystem.getTemperature()};
                double standardDeviation1[] = {0.01};
                double val = 1.0 - Double.parseDouble(dataSet.getString("Y"));
                double sdev = val / 100.0;
                SampleValue sample = new SampleValue(val, sdev, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(Double.toString(testSystem.getTemperature()));
                // double parameterGuess[] = {0.0459393339}; //cpa-srk- metan 23.658199 abs dev
                // bias -6.267%
                double parameterGuess[] = {0.0459393339}; // cpa-pr - metan
                // double parameterGuess[] = {-0.059201934}; //cpa-srk - nitrogen
                // double parameterGuess[] = {0.1};
                // double parameterGuess[] = {0.2413992410}; //cpa-pr - nitrogen
                // double parameterGuess[] = {-0.0586254634}; //cpa-srk - CO2 12.0
                // double parameterGuess[] = {0.0160496243}; //cpa-pr - CO2
                function.setInitialGuess(parameterGuess);
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error", e);
        }

        SampleSet sampleSet = new SampleSet(sampleList);
        optim.setSampleSet(sampleSet);

        // do simulations
        // optim.solve();
        // optim.runMonteCarloSimulation();
        // optim.displayResult();
        optim.displayCurveFit();
        //optim.writeToCdfFile("c:/testFit.nc");
        optim.writeToTextFile("c:/testFit.txt");
    }
}
