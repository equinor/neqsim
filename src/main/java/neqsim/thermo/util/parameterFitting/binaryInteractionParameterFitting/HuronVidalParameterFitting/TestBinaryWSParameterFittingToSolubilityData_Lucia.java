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
 * <p>
 * TestBinaryWSParameterFittingToSolubilityData_Lucia class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestBinaryWSParameterFittingToSolubilityData_Lucia {
    static Logger logger =
            LogManager.getLogger(TestBinaryWSParameterFittingToSolubilityData_Lucia.class);

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
                "SELECT * FROM LuciaData WHERE Component='methane' AND Temperature<520 AND L2<>NULL AND L2>0.00000001 ORDER BY Temperature,Pressure");// AND
                                                                                                                                                      // Reference='Houghton1957'
                                                                                                                                                      // AND
                                                                                                                                                      // Reference<>'Nighswander1989'
                                                                                                                                                      // AND
                                                                                                                                                      // Temperature>278.15
                                                                                                                                                      // AND
                                                                                                                                                      // Temperature<383.15
                                                                                                                                                      // AND
                                                                                                                                                      // Pressure<60.01325");
        // ResultSet dataSet = database.getResultSet( "SELECT * FROM LuciaData WHERE
        // Component='CO2' AND Temperature>250 AND Temperature<420 AND L2<>NULL AND
        // L2>0.00000001 ORDER BY Temperature,Pressure");// AND Reference='Houghton1957'
        // AND Reference<>'Nighswander1989' AND Temperature>278.15 AND
        // Temperature<383.15 AND Pressure<60.01325");

        try {
            int p = 0;
            while (dataSet.next() && p < 100) {
                p++;
                BinaryWSParameterFittingToSolubilityData function =
                        new BinaryWSParameterFittingToSolubilityData();

                // SystemInterface testSystem = new SystemFurstElectrolyteEos(280, 1.0);
                SystemInterface testSystem = new SystemSrkSchwartzentruberEos(
                        Double.parseDouble(dataSet.getString("Temperature")),
                        Double.parseDouble(dataSet.getString("Pressure")) / 1e5);
                // SystemInterface testSystem = new
                // SystemSrkEos(Double.parseDouble(dataSet.getString("Temperature")),
                // Double.parseDouble(dataSet.getString("Pressure"))/1e5);
                // SystemInterface testSystem = new
                // SystemPrEos(Double.parseDouble(dataSet.getString("Temperature")),
                // Double.parseDouble(dataSet.getString("Pressure"))/1e5);

                // testSystem.addComponent("CO2", 10.0);
                testSystem.addComponent("methane", 10.0);
                testSystem.addComponent("water", 10.0);

                // testSystem.addComponent("CO2", 1.0);
                // testSystem.addComponent("water", 1.0);
                // testSystem.addComponent("MDEA", 1.000001);
                // testSystem.chemicalReactionInit();
                // testSystem.createDatabase(true);
                testSystem.setMixingRule(5);

                // testSystem.getChemicalReactionOperations().solveChemEq(0);
                // testSystem.getChemicalReactionOperations().solveChemEq(1);

                // testSystem.isChemicalSystem(false);

                // testSystem.addComponent("NaPlus", 1.0e-10);
                // testSystem.addComponent("methane", 1.1);
                testSystem.init(0);
                double sample1[] = {testSystem.getPressure(), testSystem.getTemperature()}; // temperature
                double standardDeviation1[] = {0.01}; // std.dev temperature // presure std.dev
                                                      // pressure
                double val = Double.parseDouble(dataSet.getString("L2"));
                double sdev = val / 100.0;
                SampleValue sample = new SampleValue(val, sdev, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(Double.toString(testSystem.getTemperature()));
                // double parameterGuess[] = {3932.0, -4127.0, -5.89, 8.9}; // HV CO2
                double parameterGuess[] = {4802.7795779589, -440.6638711230, -7.6109236981,
                        4.8742002317, 0.1, -0.0420817811}; // HV methan570
                // double parameterGuess[] = {3204.3057406886, -2753.7379912645, -12.4728330162
                // , 13.0150379323}; // HV
                // double parameterGuess[] = {8.992E3, -3.244E3, -8.424E0, -1.824E0}; // HV
                // double parameterGuess[] = {-7.132E2, -3.933E2};//, 3.96E0, 9.602E-1}; //,
                // 1.239}; //WS
                function.setInitialGuess(parameterGuess);
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        dataSet = database.getResultSet(
                "SELECT * FROM LuciaData WHERE Component='methane' AND ID<3000 AND Temperature<520 AND Y<>NULL AND Y>0.00000001 ORDER BY Temperature,Pressure");// AND
                                                                                                                                                                // Reference='Houghton1957'
                                                                                                                                                                // AND
                                                                                                                                                                // Reference<>'Nighswander1989'
                                                                                                                                                                // AND
                                                                                                                                                                // Temperature>278.15
                                                                                                                                                                // AND
                                                                                                                                                                // Temperature<383.15
                                                                                                                                                                // AND
                                                                                                                                                                // Pressure<60.01325");
        // dataSet = database.getResultSet( "SELECT * FROM LuciaData WHERE
        // Component='CO2' AND ID<3000 AND Temperature>250 AND Temperature<420 AND
        // Y<>NULL AND Y>0.00000001 ORDER BY Temperature,Pressure");// AND
        // Reference='Houghton1957' AND Reference<>'Nighswander1989' AND
        // Temperature>278.15 AND Temperature<383.15 AND Pressure<60.01325");

        // ResultSet dataSet = database.getResultSet( "SELECT * FROM
        // activityCoefficientTable WHERE Component1='MDEA' AND Component2='water'");
        // testSystem.addComponent(dataSet.getString("ComponentSolute"), 1.0);
        // testSystem.addComponent(dataSet.getString("ComponentSolvent"), 1.0);
        try {
            int p = 0;
            while (dataSet.next() && p < 100) {
                p++;
                BinaryWSParameterFittingToSolubilityData function =
                        new BinaryWSParameterFittingToSolubilityData(0, 0);

                // SystemInterface testSystem = new
                // SystemFurstElectrolyteEos(Double.parseDouble(dataSet.getString("Temperature")),
                // Double.parseDouble(dataSet.getString("Pressure"))/1e5);
                SystemInterface testSystem = new SystemSrkSchwartzentruberEos(
                        Double.parseDouble(dataSet.getString("Temperature")),
                        Double.parseDouble(dataSet.getString("Pressure")) / 1e5);
                // SystemInterface testSystem = new
                // SystemSrkEos(Double.parseDouble(dataSet.getString("Temperature")),
                // Double.parseDouble(dataSet.getString("Pressure"))/1e5);
                // SystemInterface testSystem = new
                // SystemPrEos(Double.parseDouble(dataSet.getString("Temperature")),
                // Double.parseDouble(dataSet.getString("Pressure"))/1e5);
                // testSystem.addComponent("CO2", 10.0);
                testSystem.addComponent("methane", 10.0);
                testSystem.addComponent("water", 10.0);

                // testSystem.addComponent("CO2", 1.0);
                // testSystem.addComponent("water", 1.0);
                // testSystem.addComponent("MDEA", 1.000001);
                // testSystem.chemicalReactionInit();
                // testSystem.createDatabase(true);
                testSystem.setMixingRule(5);

                // testSystem.getChemicalReactionOperations().solveChemEq(0);
                // testSystem.getChemicalReactionOperations().solveChemEq(1);

                // testSystem.isChemicalSystem(false);

                // testSystem.addComponent("NaPlus", 1.0e-10);
                // testSystem.addComponent("methane", 1.1);
                testSystem.init(0);
                double sample1[] = {testSystem.getPressure(), testSystem.getTemperature()}; // temperature
                double standardDeviation1[] = {0.01}; // std.dev temperature // presure std.dev
                                                      // pressure
                double val = 1.0 - Double.parseDouble(dataSet.getString("Y"));
                double sdev = val / 100.0;
                SampleValue sample = new SampleValue(val, sdev, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(Double.toString(testSystem.getTemperature()));
                // double parameterGuess[] = {3932.0, -4127.0, -5.89, 8.9}; // HV CO2
                double parameterGuess[] = {4802.7795779589, -440.6638711230, -7.6109236981,
                        4.8742002317, 0.1, -0.0420817811}; // HV methan570
                // double parameterGuess[] = {3204.3057406886, -2753.7379912645, -12.4728330162
                // , 13.0150379323}; // HV
                // double parameterGuess[] = {8.992E3, -3.244E3, -8.424E0, -1.824E0}; // HV
                // double parameterGuess[] = {-7.132E2, -3.933E2};//, 3.96E0, 9.602E-1}; //,
                // 1.239}; //WS
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
        optim.displayResult();
        // optim.writeToCdfFile("c:/testFit.nc");
        // optim.writeToTextFile("c:/testFit.txt");
    }
}
