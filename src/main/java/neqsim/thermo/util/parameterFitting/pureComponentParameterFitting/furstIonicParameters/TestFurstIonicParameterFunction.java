/*
 * TestAcentric.java
 *
 * Created on 23. januar 2001, 22:08
 */

package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.furstIonicParameters;

import java.sql.ResultSet;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.database.NeqSimDataBase;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class TestFurstIonicParameterFunction {

        private static final long serialVersionUID = 1000;
        static Logger logger = LogManager.getLogger(TestFurstIonicParameterFunction.class);

        /** Creates new TestAcentric */
        public TestFurstIonicParameterFunction() {}

        public static void main(String[] args) {

                LevenbergMarquardt optim = new LevenbergMarquardt();
                ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

                // inserting samples from database
                NeqSimDataBase database = new NeqSimDataBase();
                // double[] guess = {0.0000001880, 0.0000014139, 0.0000284666, 0.0000389043,
                // -0.0000000451, 0.0000088136};
                // double[] guess = {0.0000250998, 0.0000198635, -0.0000000311, -0.0000006630};
                // double[] guess = { 0.0000001116, 0.0000053822, 0.0000691846, 0.0000049520,
                // -0.0000000605, -0.0000216352};
                // double[] guess = {0.0000000640, 0.0000011845 , 0.0000209744, 0.0000089071,
                // -0.0000000272, 0.0000019014};//cpa-el
                double[] guess = {0.0000000752, 0.0000037242, 0.0000250998, 0.0000198635,
                                -0.0000000311, -0.0000006630};
                // ResultSet dataSet = database.getResultSet( "SELECT * FROM ionicData WHERE
                // Description IN ('NaCl')");//,'LiCl','Sr2Br','Sr2I')");
                ResultSet dataSet = database.getResultSet(
                                "SELECT * FROM ionicData WHERE ion1<>'H3Oplus2' ORDER BY ion1,ion2,x2");
                // ResultSet dataSet = database.getResultSet( "SELECT * FROM ionicData WHERE
                // ion1='Na+' AND ion2='Cl-'");
                int numb = 0;
                try {
                        logger.info("adding....");
                        while (dataSet.next() && numb < 22265) {
                                numb++;

                                FurstIonicParameterFunction function =
                                                new FurstIonicParameterFunction();
                                function.setInitialGuess(guess);
                                double x2 = Double.parseDouble(dataSet.getString("x2"));
                                // SystemInterface testSystem = new SystemFurstElectrolyteEos(280,
                                // 1.0);
                                SystemInterface testSystem =
                                                new SystemElectrolyteCPAstatoil(280.0, 1.0);
                                // testSystem.addComponent(dataSet.getString("ComponentSolvent"),
                                // Double.parseDouble(dataSet.getString("x1")));
                                testSystem.addComponent("water",
                                                Double.parseDouble(dataSet.getString("x1")));
                                testSystem.addComponent(dataSet.getString("Ion1"), x2);
                                testSystem.addComponent(dataSet.getString("Ion2"),
                                                Double.parseDouble(dataSet.getString("x3")));
                                testSystem.createDatabase(true);
                                testSystem.setMixingRule(10);
                                testSystem.setTemperature(Double
                                                .parseDouble(dataSet.getString("Temperature")));
                                testSystem.setPressure(
                                                Double.parseDouble(dataSet.getString("Pressure")));
                                testSystem.init(0);
                                double sample1[] = {x2 / 0.01802}; // temperature
                                double standardDeviation1[] = {0.01}; // std.dev temperature //
                                                                      // presure std.dev
                                                                      // pressure
                                double osmcoef = Double.parseDouble(
                                                dataSet.getString("OsmoticCoefficient"));

                                testSystem.init(1);

                                SampleValue sample = new SampleValue(osmcoef, osmcoef / 100.0,
                                                sample1, standardDeviation1);
                                // SampleValue sample = new
                                // SampleValue(Double.parseDouble(dataSet.getString("IonicActivity")),
                                // Double.parseDouble(dataSet.getString("stddev2")), sample1,
                                // standardDeviation1);
                                sample.setFunction(function);
                                sample.setThermodynamicSystem(testSystem);
                                sample.setReference(dataSet.getString("Reference"));
                                sample.setDescription(dataSet.getString("Description"));
                                sampleList.add(sample);
                        }

                        // dataSet = database.getResultSet( "SELECT * FROM ionicData WHERE
                        // ion1<>'H3Oplus2' AND IonicActivity>=0.01");
                        // dataSet = database.getResultSet( "SELECT * FROM ionicData WHERE
                        // Description
                        // IN ('NaCl','LiCl','Sr2Br','Sr2I') AND IonicActivity>=0.01");
                        dataSet = database.getResultSet(
                                        "SELECT * FROM ionicData WHERE ion1='Na+' AND ion2='Cl-'");
                        logger.info("setting new for activity");
                        while (!dataSet.next()) {
                                FurstIonicParameterFunction_Activity function =
                                                new FurstIonicParameterFunction_Activity();
                                function.setInitialGuess(guess);
                                double x2 = Double.parseDouble(dataSet.getString("x2"));
                                // SystemInterface testSystem = new SystemFurstElectrolyteEos(280,
                                // 1.0);
                                SystemInterface testSystem =
                                                new SystemElectrolyteCPAstatoil(280, 1.0);
                                testSystem.addComponent(dataSet.getString("ComponentSolvent"),
                                                Double.parseDouble(dataSet.getString("x1")));
                                testSystem.addComponent(dataSet.getString("Ion1"), x2);
                                testSystem.addComponent(dataSet.getString("Ion2"),
                                                Double.parseDouble(dataSet.getString("x3")));
                                testSystem.createDatabase(true);
                                testSystem.setMixingRule(10);
                                testSystem.setTemperature(Double
                                                .parseDouble(dataSet.getString("Temperature")));
                                testSystem.setPressure(
                                                Double.parseDouble(dataSet.getString("Pressure")));
                                testSystem.init(0);
                                double sample1[] = {x2 / 0.01802}; // temperature
                                double standardDeviation1[] = {0.01}; // std.dev temperature //
                                                                      // presure std.dev
                                                                      // pressure
                                // SampleValue sample = new
                                // SampleValue(Double.parseDouble(dataSet.getString("OsmoticCoefficient")),
                                // Double.parseDouble(dataSet.getString("stddev1")), sample1,
                                // standardDeviation1);
                                double ionact = Double
                                                .parseDouble(dataSet.getString("IonicActivity"));
                                SampleValue sample = new SampleValue(ionact, ionact, sample1,
                                                standardDeviation1);
                                sample.setFunction(function);
                                sample.setThermodynamicSystem(testSystem);
                                sample.setReference(dataSet.getString("Reference"));
                                sample.setDescription(dataSet.getString("Description"));
                                sampleList.add(sample);
                        }

                        dataSet = database.getResultSet(
                                        "SELECT * FROM saltdens WHERE ion1='Na+' AND ion2='Cl-'");
                        logger.info("fitting to ionic density");
                        while (!dataSet.next()) {
                                FurstIonicParameterFunction_Density function =
                                                new FurstIonicParameterFunction_Density();
                                function.setInitialGuess(guess);
                                double x2 = Double.parseDouble(dataSet.getString("molfrac1"));
                                double x3 = Double.parseDouble(dataSet.getString("molfrac2"));
                                double x1 = 1.0 - x2 - x3;
                                SystemInterface testSystem =
                                                new SystemElectrolyteCPAstatoil(280, 1.0);
                                // SystemInterface testSystem = new SystemElectrolyteCPA(280, 1.0);
                                testSystem.addComponent(dataSet.getString("solvent"), x1);
                                testSystem.addComponent(dataSet.getString("Ion1"), x2);
                                testSystem.addComponent(dataSet.getString("Ion2"), x3);
                                testSystem.setTemperature(Double
                                                .parseDouble(dataSet.getString("temperature")));
                                testSystem.setPressure(
                                                Double.parseDouble(dataSet.getString("pressure")));
                                testSystem.createDatabase(true);
                                testSystem.setMixingRule(7);
                                testSystem.init(0);
                                double sample1[] = {x2 / 0.01802}; // temperature
                                double standardDeviation1[] = {0.01}; // std.dev temperature //
                                                                      // presure std.dev
                                                                      // pressure
                                // SampleValue sample = new
                                // SampleValue(Double.parseDouble(dataSet.getString("OsmoticCoefficient")),
                                // Double.parseDouble(dataSet.getString("stddev1")), sample1,
                                // standardDeviation1);
                                double density = Double.parseDouble(dataSet.getString("density"))
                                                * 1000.0;
                                SampleValue sample = new SampleValue(density, density / 100.0,
                                                sample1, standardDeviation1);
                                sample.setFunction(function);
                                sample.setThermodynamicSystem(testSystem);
                                sampleList.add(sample);
                        }
                } catch (Exception e) {
                        logger.error("database error: ", e);
                }

                // double sample1[] = {0.1};
                // for(int i=0;i<sampleList.size();i++){
                // logger.info("ans: " +
                // ((SampleValue)sampleList.get(i)).getFunction().calcValue(sample1));
                // }

                SampleSet sampleSet = new SampleSet(sampleList);
                optim.setSampleSet(sampleSet);

                // do simulations
                optim.solve();
                optim.displayResult();
                // optim.runMonteCarloSimulation();
                optim.displayCurveFit();
                optim.writeToCdfFile("c:/testFit.nc");
                optim.writeToTextFile("c:/testFit.txt");
        }
}
