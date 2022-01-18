package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.HuronVidalParameterFitting;

import java.sql.ResultSet;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * TestBinaryUMRPRUFittingToSolubilityData class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestBinaryUMRPRUFittingToSolubilityData {
    static Logger logger = LogManager.getLogger(TestBinaryUMRPRUFittingToSolubilityData.class);

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

        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database.getResultSet(
                "SELECT * FROM binarySolubilityData WHERE ComponentSolute='Hg' AND ComponentSolvent='n-decane'");

        double parameterGuess[] = {188.385052774267, -0.84022345};// , 2630.871733876947};

        try {
            int p = 0;
            logger.info("adding....");
            while (dataSet.next() && p < 31) {
                p++;
                UMRPRUFunction function = new UMRPRUFunction();
                SystemInterface testSystem = new SystemUMRPRUMCEos(290, 1.0);
                testSystem.addComponent("nC10", 10.0);
                testSystem.addComponent("mercury", 1.0);

                testSystem.createDatabase(true);
                testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");

                testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                logger.info("pressure " + testSystem.getPressure());
                testSystem.init(0);
                double sample1[] = {testSystem.getPressure(), testSystem.getTemperature()};
                double standardDeviation1[] = {0.01};

                SampleValue sample = new SampleValue(Double.parseDouble(dataSet.getString("x1")),
                        Double.parseDouble(dataSet.getString("StandardDeviation")), sample1,
                        standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(Double.toString(testSystem.getTemperature()));
                function.setInitialGuess(parameterGuess);
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        dataSet = database.getResultSet(
                "SELECT * FROM binarySolubilityData WHERE ComponentSolute='Hg' AND ComponentSolvent='n-heptane'");

        try {
            int p = 0;
            logger.info("adding....");
            while (dataSet.next() && p < 60) {
                p++;
                UMRPRUFunction function = new UMRPRUFunction();
                SystemInterface testSystem = new SystemUMRPRUMCEos(290, 1.0);
                testSystem.addComponent("n-heptane", 10.0);
                testSystem.addComponent("mercury", 1.0);

                testSystem.createDatabase(true);
                testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");

                testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                logger.info("pressure " + testSystem.getPressure());
                testSystem.init(0);
                double sample1[] = {testSystem.getPressure(), testSystem.getTemperature()};
                double standardDeviation1[] = {0.01};

                SampleValue sample = new SampleValue(Double.parseDouble(dataSet.getString("x1")),
                        Double.parseDouble(dataSet.getString("StandardDeviation")), sample1,
                        standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(Double.toString(testSystem.getTemperature()));
                function.setInitialGuess(parameterGuess);
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        dataSet = database.getResultSet(
                "SELECT * FROM binarySolubilityData WHERE ComponentSolute='Hg' AND ComponentSolvent='n-octane'");

        try {
            int p = 0;
            logger.info("adding....");
            while (dataSet.next() && p < 50) {
                p++;
                UMRPRUFunction function = new UMRPRUFunction();
                SystemInterface testSystem = new SystemUMRPRUMCEos(290, 1.0);
                testSystem.addComponent("n-octane", 10.0);
                testSystem.addComponent("mercury", 1.0);

                testSystem.createDatabase(true);
                testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");

                testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                logger.info("pressure " + testSystem.getPressure());
                testSystem.init(0);
                double sample1[] = {testSystem.getPressure(), testSystem.getTemperature()};
                double standardDeviation1[] = {0.01};

                SampleValue sample = new SampleValue(Double.parseDouble(dataSet.getString("x1")),
                        Double.parseDouble(dataSet.getString("StandardDeviation")), sample1,
                        standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(Double.toString(testSystem.getTemperature()));
                function.setInitialGuess(parameterGuess);
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.info("database error" + e);
        }

        dataSet = database.getResultSet(
                "SELECT * FROM binarySolubilityData WHERE ComponentSolute='Hg' AND ComponentSolvent='n-hexane'");

        try {
            int p = 0;
            logger.error("adding....");
            while (dataSet.next() && p < 30) {
                p++;
                UMRPRUFunction function = new UMRPRUFunction();
                SystemInterface testSystem = new SystemUMRPRUMCEos(290, 1.0);
                testSystem.addComponent("n-hexane", 10.0);
                testSystem.addComponent("mercury", 1.0);

                testSystem.createDatabase(true);
                testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");

                testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                logger.info("pressure " + testSystem.getPressure());
                testSystem.init(0);
                double sample1[] = {testSystem.getPressure(), testSystem.getTemperature()};
                double standardDeviation1[] = {0.01};

                SampleValue sample = new SampleValue(Double.parseDouble(dataSet.getString("x1")),
                        Double.parseDouble(dataSet.getString("StandardDeviation")), sample1,
                        standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(Double.toString(testSystem.getTemperature()));
                function.setInitialGuess(parameterGuess);
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        dataSet = database.getResultSet(
                "SELECT * FROM binarySolubilityData WHERE ComponentSolute='Hg' AND ComponentSolvent='n-pentane'");

        try {
            int p = 0;
            logger.info("adding....");
            while (dataSet.next() && p < 30) {
                p++;
                UMRPRUFunction function = new UMRPRUFunction();
                SystemInterface testSystem = new SystemUMRPRUMCEos(290, 1.0);
                testSystem.addComponent("n-pentane", 10.0);
                testSystem.addComponent("mercury", 1.0);

                testSystem.createDatabase(true);
                testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");

                testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                logger.info("pressure " + testSystem.getPressure());
                testSystem.init(0);
                double sample1[] = {testSystem.getPressure(), testSystem.getTemperature()};
                double standardDeviation1[] = {0.01};

                SampleValue sample = new SampleValue(Double.parseDouble(dataSet.getString("x1")),
                        Double.parseDouble(dataSet.getString("StandardDeviation")), sample1,
                        standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(Double.toString(testSystem.getTemperature()));
                function.setInitialGuess(parameterGuess);
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        dataSet = database.getResultSet(
                "SELECT * FROM binarySolubilityData WHERE ComponentSolute='Hg' AND ComponentSolvent='butane'");

        try {
            int p = 0;
            logger.info("adding....");
            while (dataSet.next() && p < 30) {
                p++;
                UMRPRUFunction function = new UMRPRUFunction();
                SystemInterface testSystem = new SystemUMRPRUMCEos(290, 1.0);
                testSystem.addComponent("n-butane", 10.0);
                testSystem.addComponent("mercury", 1.0);

                testSystem.createDatabase(true);
                testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");

                testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                logger.info("pressure " + testSystem.getPressure());
                testSystem.init(0);
                double sample1[] = {testSystem.getPressure(), testSystem.getTemperature()};
                double standardDeviation1[] = {0.01};

                SampleValue sample = new SampleValue(Double.parseDouble(dataSet.getString("x1")),
                        Double.parseDouble(dataSet.getString("StandardDeviation")), sample1,
                        standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(Double.toString(testSystem.getTemperature()));
                function.setInitialGuess(parameterGuess);
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        dataSet = database.getResultSet(
                "SELECT * FROM binarySolubilityData WHERE ComponentSolute='Hg' AND ComponentSolvent='propane'");

        try {
            int p = 0;
            logger.info("adding....");
            while (dataSet.next() && p < 30) {
                p++;
                UMRPRUFunction function = new UMRPRUFunction();
                SystemInterface testSystem = new SystemUMRPRUMCEos(290, 1.0);
                testSystem.addComponent("propane", 10.0);
                testSystem.addComponent("mercury", 1.0);

                testSystem.createDatabase(true);
                testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");

                testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                logger.info("pressure " + testSystem.getPressure());
                testSystem.init(0);
                double sample1[] = {testSystem.getPressure(), testSystem.getTemperature()};
                double standardDeviation1[] = {0.01};

                SampleValue sample = new SampleValue(Double.parseDouble(dataSet.getString("x1")),
                        Double.parseDouble(dataSet.getString("StandardDeviation")), sample1,
                        standardDeviation1);
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
        optim.displayResult();
        // optim.writeToCdfFile("c:/testFit.nc");
        // optim.writeToTextFile("c:/testFit.txt");
    }
}
