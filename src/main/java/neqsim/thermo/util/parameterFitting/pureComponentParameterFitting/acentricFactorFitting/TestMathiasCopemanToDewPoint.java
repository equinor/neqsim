package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.acentricFactorFitting;

import java.sql.ResultSet;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkMathiasCopeman;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * TestMathiasCopemanToDewPoint class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestMathiasCopemanToDewPoint {
    static Logger logger = LogManager.getLogger(TestMathiasCopemanToDewPoint.class);

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
        ResultSet dataSet = null;

        // // PVTsim14 values for MC-parameters
        // double guess[] ={0.547, -0.399, 0.575, // methane
        // 0.685, -0.428, 0.738, //ethane
        // 0.773, -0.509, 1.031, // propane
        // 0.849, -0.552, 1.077, //n-butane)
        // 0.780, -0.273,0.659, //i-butane
        // 0.937, -0.766, 1.848, // n-pentane
        // 0.547, -0.399, 1.575, //c-hexane
        // 0.846, -0.386, 0.884, // benzene
        // 1.087, -0.747, 1.577} ; //n-heptane
        //
        //
        // initial guess
        // double guess[] ={1.4184728684, -1.6053750221, 0.7429086086, // methane
        // 1.6853, -0.4284, 0.7382, //ethane
        // 1.8653, -1.4386, 1.3447, // propane
        // 1.8080, -0.2127, 1.3629, //n-butane)
        // 1.78038835, -1.273389543,0.659787118550, //i-butane
        // 0.903861134, -0.3585531518594, 0.66388522, // n-pentane
        // 0.547, -1.399, 1.575, //c-hexane
        // 0.8457,-1.3856,1.8843, // benzene
        // 1.0874, -1.7465, 1.5765} ; //n-heptane

        // optimized chi-square: 21706 abs dev 2.65
        double guess[] = {0.7131945439, 0.4140076386, -2.5691833434, 4.3710359391, -1.1086000763,
                7.3866869416, 1.1183339865, -2.1831081128, 0.9948380388, 1.5665073967,
                -5.5636308059, -1.4091055663, 0.8232405436, -0.3922156128, -1.4347079752,
                1.3771397711, -1.2103436623, 0.0028643344, 1.3423413878, -0.3330681068,
                -2.1604332713, 1.1554915030, -1.7359747766, 1.1084438340, 1.1800358360,
                -1.4810259893, 1.4887592454};//

        String nameList[] = {"methane", "ethane", "propane", "n-butane", "i-butane", "n-pentane",
                "c-hexane", "benzene", "n-heptane"};

        for (int compNumb = 0; compNumb < nameList.length; compNumb++) {
            dataSet = database
                    .getResultSet("SELECT * FROM PureComponentVapourPressures WHERE ComponentName='"
                            + nameList[compNumb]
                            + "' AND VapourPressure>0 ORDER BY Temperature ASC");

            try {
                long numerOfPoint = 3;
                logger.error("point " + numerOfPoint);
                int i = 0;
                while (dataSet.next()) {
                    i++;
                    if (i % numerOfPoint == 0) {
                        MathiasCopemanToDewPoint function = new MathiasCopemanToDewPoint();
                        function.setInitialGuess(guess);

                        SystemInterface testSystem = new SystemSrkMathiasCopeman(
                                Double.parseDouble(dataSet.getString("Temperature")),
                                Double.parseDouble(dataSet.getString("VapourPressure")));
                        testSystem.addComponent(dataSet.getString("ComponentName"), 100.0);
                        // testSystem.createDatabase(true);
                        double sample1[] = {testSystem.getPressure()}; // temperature
                        double standardDeviation1[] = {0.1, 0.1, 0.1}; // std.dev temperature //
                                                                       // presure std.dev
                                                                       // pressure
                        double val = testSystem.getTemperature();
                        double stdErr = 1.0;
                        SampleValue sample =
                                new SampleValue(val, stdErr, sample1, standardDeviation1);
                        sample.setFunction(function);
                        sample.setReference(dataSet.getString("Reference"));
                        sample.setThermodynamicSystem(testSystem);
                        sampleList.add(sample);
                    }
                }
            } catch (Exception e) {
                logger.error("database error" + e);
            }
        }

        dataSet = database
                .getResultSet("SELECT * FROM dewPointDataSynthHCStatoil WHERE Pressure<80.0");// "0
                                                                                              // AND
                                                                                              // reference='Morch2004gas1'");

        try {
            long numerOfPoint = 1;
            logger.info("point " + numerOfPoint);
            int i = 0;
            while (dataSet.next() && i < 100) {
                i++;
                if (i % numerOfPoint == 0) {
                    MathiasCopemanToDewPoint function = new MathiasCopemanToDewPoint();
                    function.setInitialGuess(guess);

                    SystemInterface testSystem = new SystemSrkMathiasCopeman(
                            Double.parseDouble(dataSet.getString("Temperature")),
                            Double.parseDouble(dataSet.getString("Pressure")));
                    testSystem.addComponent(dataSet.getString("comp1"),
                            Double.parseDouble(dataSet.getString("x1")));
                    testSystem.addComponent(dataSet.getString("comp2"),
                            Double.parseDouble(dataSet.getString("x2")));
                    testSystem.addComponent(dataSet.getString("comp3"),
                            Double.parseDouble(dataSet.getString("x3")));
                    testSystem.addComponent(dataSet.getString("comp4"),
                            Double.parseDouble(dataSet.getString("x4")));
                    testSystem.addComponent(dataSet.getString("comp5"),
                            Double.parseDouble(dataSet.getString("x5")));
                    testSystem.addComponent(dataSet.getString("comp6"),
                            Double.parseDouble(dataSet.getString("x6")));
                    testSystem.addComponent(dataSet.getString("comp7"),
                            Double.parseDouble(dataSet.getString("x7")));
                    testSystem.addComponent(dataSet.getString("comp8"),
                            Double.parseDouble(dataSet.getString("x8")));
                    testSystem.addComponent(dataSet.getString("comp9"),
                            Double.parseDouble(dataSet.getString("x9")));
                    // testSystem.addComponent(dataSet.getString("comp9"),
                    // Double.parseDouble(dataSet.getString("x9")));
                    // testSystem.addComponent(dataSet.getString("comp10"),
                    // Double.parseDouble(dataSet.getString("x10")));
                    // testSystem.createDatabase(true);
                    double sample1[] = {testSystem.getPressure()}; // temperature
                    double standardDeviation1[] = {0.1, 0.1, 0.1}; // std.dev temperature // presure
                                                                   // std.dev pressure
                    double val = testSystem.getTemperature();
                    double stdErr = 1.0;
                    SampleValue sample = new SampleValue(val, stdErr, sample1, standardDeviation1);
                    sample.setFunction(function);
                    sample.setReference(dataSet.getString("reference"));
                    sample.setThermodynamicSystem(testSystem);
                    sampleList.add(sample);
                }
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
        // optim.writeToCdfFile("c:/testFit.nc");
        // optim.writeToTextFile("c:/testFit.txt");
    }
}
