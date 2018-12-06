/*
 * TestAcentric.java
 *
 * Created on 23. januar 2001, 22:08
 */
package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.EosInteractionParameterFitting;

import neqsim.util.database.NeqSimDataBase;
import java.sql.*;
import java.util.*;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class TestBinaryCPAfitToActivityCoefficientDB extends java.lang.Object implements Cloneable {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new TestAcentric
     */
    public TestBinaryCPAfitToActivityCoefficientDB() {
    }

    public static void main(String[] args) {

        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList sampleList = new ArrayList();

        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database.getResultSet("NeqSimDataBase", "SELECT * FROM activityCoefficientTable WHERE ((Component1='TEG' AND Component2='water') OR (Component1='water' AND Component2='TEG')) AND ReferenceID<>'shell data'");

        try {
            while (dataSet.next()) {
                BinaryCPAparameterFitToActivityCoefficientFunction function = new BinaryCPAparameterFitToActivityCoefficientFunction();

                double x1 = Double.parseDouble(dataSet.getString("x1")) * 100;
                double x2 = Double.parseDouble(dataSet.getString("x2")) * 100;
                SystemInterface testSystem = new SystemSrkCPAstatoil(Double.parseDouble(dataSet.getString("Temperature")), Double.parseDouble(dataSet.getString("Pressure")));
                testSystem.addComponent(dataSet.getString("Component1"), x1);             // legger til komponenter til systemet
                testSystem.addComponent(dataSet.getString("Component2"), x2);
                // testSystem.chemicalReactionInit();
                testSystem.createDatabase(true);
                testSystem.setMixingRule(10);
                testSystem.init(0);

                double sample1[] = {x2, testSystem.getTemperature()};
                double standardDeviation1[] = {x2 / 100.0};
                double val = Double.parseDouble(dataSet.getString("gamma2"));
                SampleValue sample = new SampleValue(val, val / 100.0, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(Double.toString(testSystem.getTemperature()));
                // function.setDatabaseParameters();
                //double guess[] = {-1466.3924707953, 1197.4327552750, 5.9188456398, -7.2410712156, 0.2127650110};
                double guess[] = {-0.241488376,-0.344136439,0.0004315217};//,0.02};//, -55};//,-30};

                function.setInitialGuess(guess);
                sampleList.add(sample);
            }
        } catch (Exception e) {
            System.out.println("database error" + e);
        }



        SampleSet sampleSet = new SampleSet(sampleList);
        optim.setSampleSet(sampleSet);

        // do simulations
        optim.solve();
        //optim.runMonteCarloSimulation();
        optim.displayCurveFit();
        optim.writeToTextFile("c:/testFit.txt");
    }
}
