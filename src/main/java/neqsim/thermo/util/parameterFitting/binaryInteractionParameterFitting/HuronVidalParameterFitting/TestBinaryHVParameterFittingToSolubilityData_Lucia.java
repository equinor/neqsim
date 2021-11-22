
package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.HuronVidalParameterFitting;

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
public class TestBinaryHVParameterFittingToSolubilityData_Lucia {
    static Logger logger =
            LogManager.getLogger(TestBinaryHVParameterFittingToSolubilityData_Lucia.class);


    public static void main(String[] args) {
        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database.getResultSet(
                "SELECT * FROM LuciaData8 WHERE Component='methane' AND Temperature<520 AND L2<>NULL AND L2>0.0000000001 ORDER BY Temperature,Pressure");// AND
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
        // Component='CO2' AND Temperature>250 AND Temperature<420 AND
        // Pressure<700000000 AND L2 IS NOT NULL AND L2>0.000000001 ORDER BY
        // Temperature,Pressure");// AND Reference='Houghton1957' AND
        // Reference<>'Nighswander1989' AND Temperature>278.15 AND Temperature<383.15
        // AND Pressure<60.01325");
        // ResultSet dataSet = database.getResultSet( "SELECT * FROM LuciaData8 WHERE
        // Component='propane' AND Temperature>250 AND Temperature<420 AND
        // Pressure<700000000 AND L2<>NULL AND L2>0.000000001 ORDER BY
        // Temperature,Pressure");// AND Reference='Houghton1957' AND
        // Reference<>'Nighswander1989' AND Temperature>278.15 AND Temperature<383.15
        // AND Pressure<60.01325");
        // ResultSet dataSet = database.getResultSet( "SELECT * FROM LuciaData8 WHERE
        // Component='methane' AND ID1>662 AND ID1<760 AND Temperature<520 AND L2<>NULL
        // AND L2>0.0000000001 ORDER BY Temperature,Pressure");// AND
        // Reference='Houghton1957' AND Reference<>'Nighswander1989' AND
        // Temperature>278.15 AND Temperature<383.15 AND Pressure<60.01325");
        // ResultSet dataSet = database.getResultSet( "SELECT * FROM LuciaData8 WHERE
        // Component='H2S' AND Temperature>250 AND Temperature<420 AND Pressure<10000000
        // AND L2<>NULL AND L2>0.000000001 ORDER BY Temperature,Pressure");
        try {
            int p = 0;
            logger.info("adding....");
            while (dataSet.next() && p < 150) {
                p++;
                BinaryHVParameterFittingToSolubilityData function =
                        new BinaryHVParameterFittingToSolubilityData(1, 1);

                // SystemInterface testSystem = new SystemFurstElectrolyteEos(280, 1.0);
                // SystemInterface testSystem = new
                // SystemPrEos1978(Double.parseDouble(dataSet.getString("Temperature")),
                // Double.parseDouble(dataSet.getString("Pressure"))/1e5);
                // SystemInterface testSystem = new
                // SystemSrkEos(Double.parseDouble(dataSet.getString("Temperature")),
                // Double.parseDouble(dataSet.getString("Pressure"))/1e5);
                // SystemInterface testSystem = new
                // SystemSrkEos(Double.parseDouble(dataSet.getString("Temperature")),
                // Double.parseDouble(dataSet.getString("Pressure")) / 1e5);
                SystemInterface testSystem = new SystemSrkCPAstatoil(
                        Double.parseDouble(dataSet.getString("Temperature")),
                        Double.parseDouble(dataSet.getString("Pressure")) / 1e5);
                // testSystem.addComponent("propane", 10.0);
                testSystem.addComponent("methane", 10.0);
                testSystem.addComponent("water", 100.0);

                // testSystem.addComponent("CO2", 1.0);
                // testSystem.addComponent("water", 1.0);
                // testSystem.addComponent("MDEA", 1.000001);
                // testSystem.chemicalReactionInit();
                // testSystem.createDatabase(true);
                testSystem.setMixingRule(4);
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
                // double parameterGuess[] = {4239.63, -232.924, -5.01417, 2.3761};
                // double parameterGuess[] = {3932.0, -4127.0, -5.89, 8.9}; // HV CO2
                // double parameterGuess[] = {1859.4161214985, -1025.4679064974, -1.5607986741,
                // 2.1430069589}; // HV H2S

                // double parameterGuess[] = {1193.8840735911, 69.2494254233, -7.8323508140,
                // 4.5299137720}; // H2S MDEA
                // double parameterGuess[] ={5251.7374371982, -3121.2788585048, -0.8420253536,
                // -0.5123316046};//;//,0.03};//co2 scsrk-ny
                // double parameterGuess[] ={-1584, 3517, 3.9121943388, -0.44,-0.1};//propane
                // double parameterGuess[] = { 5607, 0.897598343, -123.6011438188,
                // -6.5496550381, 2.1378539395}; // HV methan570
                double parameterGuess[] =
                        {6114.2013874102, -188.264597921, -10.7239107857559, 2.310651690177652}; // HV
                                                                                                 // methan570
                // double parameterGuess[] = {3204.3057406886, -2753.7379912645, -12.4728330162
                // , 13.0150379323}; // HV
                // double parameterGuess[] = {8.992E3, -3.244E3, -8.424E0, -1.824E0}; // HV
                // double parameterGuess[] = {-7.132E2, -3.933E2};//, 3.96E0, 9.602E-1}; //,
                // 1.239}; //WS
                function.setInitialGuess(parameterGuess);
                sampleList.add(sample);
                logger.info("liq points " + p);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        dataSet = database.getResultSet(
                "SELECT * FROM LuciaData8 WHERE Component='methane' AND ID<3000 AND Temperature<520 AND Y<>NULL AND Y>0.0000000001 ORDER BY Temperature,Pressure");// AND
                                                                                                                                                                   // Reference='Houghton1957'
                                                                                                                                                                   // AND
                                                                                                                                                                   // Reference<>'Nighswander1989'
                                                                                                                                                                   // AND
                                                                                                                                                                   // Temperature>278.15
                                                                                                                                                                   // AND
                                                                                                                                                                   // Temperature<383.15
                                                                                                                                                                   // AND
                                                                                                                                                                   // Pressure<60.01325");
        // dataSet = database.getResultSet( "SELECT * FROM LuciaData8 WHERE
        // Component='CO2' AND ID<3000 AND Temperature>250 AND Pressure<700000000 AND
        // Temperature<420 AND Y IS NOT NULL AND Y>0.0000000001 ORDER BY
        // Temperature,Pressure");// AND Reference='Houghton1957' AND
        // Reference<>'Nighswander1989' AND Temperature>278.15 AND Temperature<383.15
        // AND Pressure<60.01325");
        // dataSet = database.getResultSet( "SELECT * FROM LuciaData8 WHERE
        // Component='methane' AND Temperature>273.15 AND Pressure<153000000 AND Y<>NULL
        // AND Y>0.000000001 ORDER BY Temperature,Pressure");

        // ResultSet dataSet = database.getResultSet( "SELECT * FROM
        // activityCoefficientTable WHERE Component1='MDEA' AND Component2='water'");
        // testSystem.addComponent(dataSet.getString("ComponentSolute"), 1.0);
        // testSystem.addComponent(dataSet.getString("ComponentSolvent"), 1.0);
        try {
            int p = 0;
            logger.info("adding....");
            while (dataSet.next() && p < 150) {
                p++;
                BinaryHVParameterFittingToSolubilityData function =
                        new BinaryHVParameterFittingToSolubilityData(0, 0);

                // SystemInterface testSystem = new
                // SystemFurstElectrolyteEos(Double.parseDouble(dataSet.getString("Temperature")),
                // Double.parseDouble(dataSet.getString("Pressure"))/1e5);
                // SystemInterface testSystem = new
                // SystemPrEos1978(Double.parseDouble(dataSet.getString("Temperature")),
                // Double.parseDouble(dataSet.getString("Pressure"))/1e5);
                // SystemInterface testSystem = new
                // SystemSrkEos(Double.parseDouble(dataSet.getString("Temperature")),
                // Double.parseDouble(dataSet.getString("Pressure"))/1e5);
                // SystemInterface testSystem = new
                // SystemSrkEos(Double.parseDouble(dataSet.getString("Temperature")),
                // Double.parseDouble(dataSet.getString("Pressure")) / 1e5);
                // testSystem.addComponent("CO2", 1.0);
                SystemInterface testSystem = new SystemSrkCPAstatoil(
                        Double.parseDouble(dataSet.getString("Temperature")),
                        Double.parseDouble(dataSet.getString("Pressure")) / 1e5);

                testSystem.addComponent("methane", 10.0);
                testSystem.addComponent("water", 10.0);

                // testSystem.addComponent("CO2", 1.0);
                // testSystem.addComponent("water", 1.0);
                // testSystem.addComponent("MDEA", 1.000001);
                // testSystem.chemicalReactionInit();
                // testSystem.createDatabase(true);
                testSystem.setMixingRule(4);

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
                // double parameterGuess[] = {4239.63, -232.924, -5.01417, 2.3761};
                // double parameterGuess[] ={3209.3031222305, -2016.3262143626, 4.2211091944,
                // -3.3157456878};//
                // double parameterGuess[] = {5640.0, -3793.0, -5.89, 8.9}; // HV CO2
                // double parameterGuess[] ={5251.7374371982, -3121.2788585048, -0.8420253536,
                // -0.5123316046};//;//,0.03};//co2 scsrk-ny
                double parameterGuess[] =
                        {6114.2013874102, -188.264597921, -10.7239107857559, 2.310651690177652}; // HV
                                                                                                 // methane
                // double parameterGuess[] = {3204.3057406886, -2753.7379912645, -12.4728330162
                // , 13.0150379323}; // HV
                // double parameterGuess[] = {8.992E3, -3.244E3, -8.424E0, -1.824E0}; // HV
                // double parameterGuess[] = {-7.132E2, -3.933E2};//, 3.96E0, 9.602E-1}; //,
                // 1.239}; //WS
                function.setInitialGuess(parameterGuess);
                sample.setDescription(Double.toString(testSystem.getTemperature()));
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
