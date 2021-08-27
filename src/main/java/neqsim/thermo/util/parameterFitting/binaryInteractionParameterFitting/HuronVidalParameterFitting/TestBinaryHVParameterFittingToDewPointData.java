/*
 * TestAcentric.java
 *
 * Created on 23. januar 2001, 22:08
 */

package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.HuronVidalParameterFitting;

import java.sql.ResultSet;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.util.database.NeqSimDataBase;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class TestBinaryHVParameterFittingToDewPointData {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(TestBinaryHVParameterFittingToDewPointData.class);

    /** Creates new TestAcentric */
    public TestBinaryHVParameterFittingToDewPointData() {}

    public static void main(String[] args) {

        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database.getResultSet(
                "SELECT * FROM dewpointquaternary WHERE temperature>173.1 AND x4>0.0000021 ORDER BY x4,pressure");
        // ResultSet dataSet = database.getResultSet( "SELECT * FROM
        // activityCoefficientTable WHERE Component1='MDEA' AND Component2='water'");
        // testSystem.addComponent(dataSet.getString("ComponentSolute"), 1.0);
        // testSystem.addComponent(dataSet.getString("ComponentSolvent"), 1.0);
        try {
            int p = 0;
            logger.info("adding....");
            while (dataSet.next() && p < 300) {
                p++;
                BinaryHVParameterFittingToDewPointData function =
                        new BinaryHVParameterFittingToDewPointData();

                // SystemInterface testSystem = new SystemFurstElectrolyteEos(280, 1.0);
                // SystemInterface testSystem = new SystemSrkSchwartzentruberEos(290, 1.0);
                SystemInterface testSystem = new SystemPrEos(290, 1.0);
                testSystem.addComponent(dataSet.getString("comp1"),
                        Double.parseDouble(dataSet.getString("x1")));
                testSystem.addComponent(dataSet.getString("comp2"),
                        Double.parseDouble(dataSet.getString("x2")));
                testSystem.addComponent("ethane", Double.parseDouble(dataSet.getString("x3")));
                testSystem.addComponent(dataSet.getString("comp4"),
                        Double.parseDouble(dataSet.getString("x4")));
                // testSystem.setSolidPhaseCheck(true);
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
                testSystem.setTemperature(Double.parseDouble(dataSet.getString("temperature")));
                testSystem.setPressure(Double.parseDouble(dataSet.getString("pressure")));
                testSystem.init(0);
                double sample1[] = {testSystem.getPressure(), testSystem.getTemperature()}; // temperature
                double standardDeviation1[] = {0.01}; // std.dev temperature // presure std.dev
                                                      // pressure
                SampleValue sample = new SampleValue(testSystem.getTemperature(),
                        testSystem.getTemperature() / 100.0, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(Double.toString(testSystem.getTemperature()));
                double parameterGuess[] = {0.01};
                // double parameterGuess[] = {4799.35, -2772.29, 0.6381, -1.68096};
                // double parameterGuess[] = {3932.0, -4127.0, -5.89, 8.9}; // HV CO2
                // double parameterGuess[] = {5023.6600682957, -136.4306560594, -3.9812435921,
                // 1.4579901393}; // HV methane
                // double parameterGuess[] = {3204.3057406886, -2753.7379912645, -12.4728330162
                // , 13.0150379323}; // HV
                // double parameterGuess[] = {8.992E3, -3.244E3, -8.424E0, -1.824E0}; // HV
                // double parameterGuess[] = {-7.132E2, -3.933E2};//, 3.96E0, 9.602E-1}; //,
                // 1.239}; //WS
                sample.setReference(Double.toString(testSystem.getPressure()));
                function.setInitialGuess(parameterGuess);
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
        // optim.displayResult();
        optim.displayCurveFit();
        optim.displayResult();
        // optim.writeToCdfFile("c:/testFit.nc");
        // optim.writeToTextFile("c:/testFit.txt");
    }
}
