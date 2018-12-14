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
public class TestCPAParameterFittingToSolubilityData extends java.lang.Object {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new TestAcentric
     */
    public TestCPAParameterFittingToSolubilityData() {
    }

    public static void main(String[] args) {

        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList sampleList = new ArrayList();

        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        //ResultSet dataSet =  database.getResultSet(  "SELECT * FROM binarySolubilityData WHERE ComponentSolute='CO2' AND ComponentSolvent='water' AND Reference='Houghton1957' AND Reference<>'Nighswander1989' AND Temperature>283.15 AND Temperature<373.15 AND Pressure<60.01325");
        ResultSet dataSet = database.getResultSet( "SELECT * FROM binarySolubilityData WHERE ComponentSolute='methane' AND ComponentSolvent='MEG'");
        double parameterGuess[] = {0.07,0.1340991545744328435900};//, 0.000117974}; //cpa
               
        try {
            int p = 0;
            System.out.println("adding....");
            while (dataSet.next() && p < 200) {
                p++;
                CPAParameterFittingToSolubilityData function = new CPAParameterFittingToSolubilityData();

                SystemInterface testSystem = new SystemSrkCPAstatoil(290, 1.0);
                //SystemInterface testSystem = new SystemSrkEos(290, 1.0);
                testSystem.addComponent("methane", 1.0);
                testSystem.addComponent("MEG", 10.0);
                testSystem.createDatabase(true);
                testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                testSystem.setMixingRule(10);
                testSystem.init(0);
                double sample1[] = {testSystem.getPressure(), testSystem.getTemperature()};  // temperature
                double standardDeviation1[] = {0.13}; // std.dev temperature    // presure std.dev pressure
                SampleValue sample = new SampleValue(Double.parseDouble(dataSet.getString("x1")), Double.parseDouble(dataSet.getString("StandardDeviation")), sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(Double.toString(testSystem.getTemperature()));
                //double parameterGuess[] = {-0.130}; //srk
                // double parameterGuess[] = {-0.0668706940}; //cpa
                function.setInitialGuess(parameterGuess);
                sampleList.add(sample);
            }
        } catch (Exception e) {
            System.out.println("database error" + e);
        }
        
        dataSet = database.getResultSet( "SELECT * FROM BinaryEquilibriumData WHERE Component1='methane' AND Component2='MEG'");
        try {
            int p = 0;
            System.out.println("adding....");
            while (dataSet.next() && p < 0) {
                p++;
                CPAParameterFittingToSolubilityData function = new CPAParameterFittingToSolubilityData(0,1);

                SystemInterface testSystem = new SystemSrkCPAstatoil(290, 1.0);
                //SystemInterface testSystem = new SystemSrkEos(290, 1.0);
                testSystem.addComponent("methane", 1.0);
                testSystem.addComponent("MEG", 1.0);
                //testSystem.createDatabase(true);
                testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                testSystem.setMixingRule(10);
                testSystem.init(0);
                double sample1[] = {testSystem.getPressure(), testSystem.getTemperature()};  // temperature
                double standardDeviation1[] = {0.13}; // std.dev temperature    // presure std.dev pressure
                double value = Double.parseDouble(dataSet.getString("y2"));
                SampleValue sample = new SampleValue(value, value/100.0, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(Double.toString(testSystem.getTemperature()));
                //double parameterGuess[] = {-0.130}; //srk
                // double parameterGuess[] = {-0.0668706940}; //cpa
                function.setInitialGuess(parameterGuess);
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
   //         optim.displayResult();
        //optim.displayCurveFit();
        //optim.writeToCdfFile("c:/testFit.nc");
        //optim.writeToTextFile("c:/testFit.txt");
    }
}
