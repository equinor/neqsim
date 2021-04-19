/*
 * TestAcentric.java
 *
 * Created on 23. januar 2001, 22:08
 */
package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.cpaParam;

import neqsim.util.database.NeqSimExperimentDatabase;

import java.sql.*;
import java.util.*;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import org.apache.logging.log4j.*;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class TestCPA_TEG extends java.lang.Object {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(TestCPA_TEG.class);

    /**
     * Creates new TestAcentric
     */
    public TestCPA_TEG() {
    }

    public static void main(String[] args) {

        ArrayList sampleList = new ArrayList();

        // inserting samples from database
        NeqSimExperimentDatabase database = new NeqSimExperimentDatabase();

        // double guess[] = {13.21, 39.1260, 1.1692, 0.0188, 1.4337};//,1.0008858863,
        // 1.8649645470, -4.6720397496}; // MEG - srk-cpa
        // double guess[] = {0.903477158616734, 1.514853438, -1.86430399826};//
        double guess[] = { 0.28454, -0.0044236 };//
        // double guess[] = {0.28652795, 0.001};
        // double guess[] ={ 0.6224061375113976, -0.050295759360433255,
        // 0.7162394329011095};//water CPA statoil
        // double guess[] ={ 1.683161439854159, -2.0134329439188,
        // 2.1912144731621446};//water CPA statoil

        // double guess[] = {2.97, 3.7359, 0.0692, 0.0787};//, 0.01787};//co2
//double guess[] = {0.1};//
        ResultSet dataSet = database.getResultSet(
                "SELECT * FROM PureComponentVapourPressures WHERE ComponentName='TEG' AND Temperature>273.15 AND Temperature<690.0 ORDER BY Temperature");

        try {
            while (!dataSet.next()) {
                CPAFunction function = new CPAFunction();
                SystemInterface testSystem = new SystemSrkCPAstatoil(
                        Double.parseDouble(dataSet.getString("Temperature")),
                        Double.parseDouble(dataSet.getString("VapourPressure")));
                testSystem.addComponent(dataSet.getString("ComponentName"), 1.0);
                testSystem.createDatabase(true);
                testSystem.setMixingRule(2);
                // testSystem.init(0);

                double temp = testSystem.getTemperature();
                double val = testSystem.getPressure();

                double sample1[] = { temp }; // temperature
                double standardDeviation1[] = { 0.1 }; // std.dev temperature // presure std.dev pressure

                double stddev = val / 50.0;
                double logVal = Math.log(val);
                SampleValue sample = new SampleValue(val, stddev, sample1, standardDeviation1);

                // function.setBounds(bounds);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                function.setInitialGuess(guess);
                sample.setReference(dataSet.getString("Reference"));
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        dataSet = database.getResultSet(
                "SELECT * FROM PureComponentDensity WHERE ComponentName='MEG' AND Temperature>173.15 ORDER BY Temperature");
        try {
            while (dataSet.next()) {
                CPAFunctionDens function = new CPAFunctionDens(1);
                SystemInterface testSystem = new SystemSrkCPAstatoil(
                        Double.parseDouble(dataSet.getString("Temperature")), 1.1);
                // SystemInterface testSystem = new SystemSrkEos(280, 0.001);
                double temp = testSystem.getTemperature();
                testSystem.addComponent(dataSet.getString("ComponentName"), 100.0);
                testSystem.createDatabase(true);
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                // testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                testSystem.setTemperature(temp);
                testSystem.setMixingRule(1);
                // testSystem.init(0);
                double dens = Double.parseDouble(dataSet.getString("Density"));
                // double dens = Double.parseDouble(dataSet.getString("Density"));
                double sample1[] = { temp }; // temperature
                double standardDeviation1[] = { 0.1 }; // std.dev temperature // presure std.dev pressure
                SampleValue sample = new SampleValue(dens, dens / 100.0, sample1, standardDeviation1);
                // double guess[] = {46939.4738048507, 1.5971863018, 0.7623134978,
                // 0.0292037583};

                // double guess[] = {9.341E4,1.953E0,1.756E-1,92.69,0.129};
                function.setInitialGuess(guess);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(dataSet.getString("Reference"));
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        dataSet = database.getResultSet(
                "SELECT * FROM PureComponentCpHeatCapacity WHERE ComponentName='TEG' AND Temperature>263.15 ORDER BY Temperature");
        try {
            while (!dataSet.next()) {
                CPAFunctionCp function = new CPAFunctionCp(1);
                SystemInterface testSystem = new SystemSrkCPAstatoil(
                        Double.parseDouble(dataSet.getString("Temperature")), 1.1);
                // SystemInterface testSystem = new SystemSrkEos(280, 0.001);
                double temp = testSystem.getTemperature();
                testSystem.addComponent(dataSet.getString("ComponentName"), 100.0);
                testSystem.createDatabase(true);
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                // testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                testSystem.setTemperature(temp);
                testSystem.setMixingRule(1);
                // testSystem.init(0);
                double dens = Double.parseDouble(dataSet.getString("HeatCapacityCp"));
                // double dens = Double.parseDouble(dataSet.getString("Density"));
                double sample1[] = { temp }; // temperature
                double standardDeviation1[] = { 0.1 }; // std.dev temperature // presure std.dev pressure
                SampleValue sample = new SampleValue(dens, dens / 100.0, sample1, standardDeviation1);
                // double guess[] = {46939.4738048507, 1.5971863018, 0.7623134978,
                // 0.0292037583};

                // double guess[] = {9.341E4,1.953E0,1.756E-1,92.69,0.129};
                function.setInitialGuess(guess);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(dataSet.getString("Reference"));
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        SampleSet sampleSet = new SampleSet(sampleList);

        LevenbergMarquardt optim = new LevenbergMarquardt();
        optim.setSampleSet(sampleSet);

        // do simulations
        optim.solve();
        // optim.runMonteCarloSimulation();
        optim.displayResult();
        optim.displayCurveFit();
        optim.writeToTextFile("c:/test.txt");
    }
}
