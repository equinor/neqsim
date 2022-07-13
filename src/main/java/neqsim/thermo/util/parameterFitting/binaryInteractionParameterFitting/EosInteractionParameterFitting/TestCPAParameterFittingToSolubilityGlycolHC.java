package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.EosInteractionParameterFitting;

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
 * TestCPAParameterFittingToSolubilityGlycolHC class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestCPAParameterFittingToSolubilityGlycolHC {
    static Logger logger = LogManager.getLogger(TestCPAParameterFittingToSolubilityGlycolHC.class);

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
        NeqSimDataBase database = new NeqSimDataBase();// AND reference<>'Lindboe2002'
        ResultSet dataSet = database.getResultSet(
                "SELECT * FROM hcglycollldata WHERE comp1='n-heptane' AND comp2='MEG' AND reference='Lindboe2002' ORDER BY Temperature,Pressure");// AND
                                                                                                                                                  // Reference='Houghton1957'
                                                                                                                                                  // AND
                                                                                                                                                  // Reference<>'Nighswander1989'
                                                                                                                                                  // AND
                                                                                                                                                  // Temperature>278.15
                                                                                                                                                  // AND
                                                                                                                                                  // Temperature<383.15
                                                                                                                                                  // AND
                                                                                                                                                  // Pressure<60.01325");
        // double parameterGuess[] = {0.0471326591, 5.14, 10.819, 0.6744, 0.0141};
        // double parameterGuess[] = {0.0602997387, 5.2137117933, 10.3039876875,
        // 0.6714377099, 0.0178639622}; // fitted to all data
        double parameterGuess[] = {1924, 4938};// , -1.11, 1.24};
        // double parameterGuess[] = {0.0471326591};

        try {
            int p = 0;
            logger.info("adding....");
            while (!dataSet.next() && p < 50) {
                p++;
                CPAParameterFittingToSolubilityData function =
                        new CPAParameterFittingToSolubilityData();

                // SystemInterface testSystem = new
                // SystemSrkCPAs(Double.parseDouble(dataSet.getString("temperature"))+273.15,
                // Double.parseDouble(dataSet.getString("pressure")));
                SystemInterface testSystem = new SystemSrkEos(
                        Double.parseDouble(dataSet.getString("temperature")) + 273.15,
                        Double.parseDouble(dataSet.getString("pressure")));

                testSystem.addComponent("n-heptane", 1.0);
                testSystem.addComponent("MEG", 10.0);
                // testSystem.createDatabase(true);
                // testSystem.setMixingRule(7);
                testSystem.setMixingRule(4);
                testSystem.init(0);
                double sample1[] = {testSystem.getTemperature()};
                double standardDeviation1[] = {0.01};
                double val = Double.parseDouble(dataSet.getString("x-glyinhc"));
                double sdev = val / 100.0;
                SampleValue sample = new SampleValue(val, sdev, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);// 34.7
                sample.setReference(Double.toString(testSystem.getTemperature()));
                function.setInitialGuess(parameterGuess);
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        dataSet = database.getResultSet(
                "SELECT * FROM HCGlycolLLdata WHERE comp1='n-heptane' AND comp2='MEG' AND reference='Lindboe2002' ORDER BY Temperature,Pressure");

        try {
            int p = 0;
            logger.info("adding....");
            while (!dataSet.next() && p < 50) {
                p++;
                CPAParameterFittingToSolubilityData_Vap function =
                        new CPAParameterFittingToSolubilityData_Vap();

                // SystemInterface testSystem = new
                // SystemSrkCPAs(Double.parseDouble(dataSet.getString("temperature"))+273.15,
                // Double.parseDouble(dataSet.getString("pressure")));
                SystemInterface testSystem = new SystemSrkEos(
                        Double.parseDouble(dataSet.getString("temperature")) + 273.15,
                        Double.parseDouble(dataSet.getString("pressure")));

                testSystem.addComponent("n-heptane", 1.00);
                testSystem.addComponent("MEG", 10.0);
                testSystem.init(0);
                testSystem.setMixingRule(4);
                double sample1[] = {testSystem.getTemperature()};
                double standardDeviation1[] = {0.01};
                double val = Double.parseDouble(dataSet.getString("x-hcinglyc"));
                double sdev = val / 100.0;
                SampleValue sample = new SampleValue(val, sdev, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(Double.toString(testSystem.getTemperature()));
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
        //optim.writeToCdfFile("c:/testFit.nc");
        optim.writeToTextFile("c:/testFit.txt");
    }
}
