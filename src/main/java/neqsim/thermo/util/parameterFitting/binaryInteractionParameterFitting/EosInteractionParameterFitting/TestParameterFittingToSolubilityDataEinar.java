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
import neqsim.thermo.system.SystemPrEos;
/**
 *
 * @author  Even Solbraa
 * @version
 */
public class TestParameterFittingToSolubilityDataEinar extends java.lang.Object {

    private static final long serialVersionUID = 1000;
    
    /** Creates new TestAcentric */
    public TestParameterFittingToSolubilityDataEinar() {
    }
    
    
    public static void main(String[] args){
        
        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList sampleList = new ArrayList();
        
        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet =  database.getResultSet(  "SELECT * FROM binarySolubilityDataEinar WHERE ComponentSolute='methane' AND ComponentSolvent='Water'");
      
        try{
            int p=0;
            System.out.println("adding....");
            while(dataSet.next() && p<4000){
                p++;
                CPAParameterFittingToSolubilityData function = new CPAParameterFittingToSolubilityData();
                
                SystemInterface testSystem = new SystemPrEos(290, 1.0); // SystemPrEos // SystemSrkSchwartzentruberEos // SystemSrkEos //SystemSrkMathiasCopemanEos
                testSystem.addComponent("methane", 1.0);  //CO2  // nitrogen // methane
                testSystem.addComponent("water", 10.0);
                testSystem.createDatabase(true);
                testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure"))/1.0e5);
                testSystem.setMixingRule(2);
                testSystem.init(0);
                double sample1[] = {testSystem.getPressure(),testSystem.getTemperature()};  // temperature
                double standardDeviation1[] = {testSystem.getPressure()/100.0,testSystem.getTemperature()/100.0}; // std.dev temperature    // presure std.dev pressure
                SampleValue sample = new SampleValue(Double.parseDouble(dataSet.getString("x1")), Double.parseDouble(dataSet.getString("StandardDeviation")), sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(Double.toString(testSystem.getTemperature()));
                //double parameterGuess[] = {-0.130}; //srk
                double parameterGuess[] = {0.0000001};
                function.setInitialGuess(parameterGuess);
                sampleList.add(sample);
            }
        }
        catch(Exception e){
            System.out.println("database error" + e);
        }
        
        SampleSet sampleSet = new SampleSet(sampleList);
        optim.setSampleSet(sampleSet);
        
        // do simulations
        optim.solve();
        //optim.runMonteCarloSimulation();
        //    optim.displayResult();
        optim.displayCurveFit();
        optim.writeToCdfFile("c:/testFit.nc");
        optim.writeToTextFile("c:/testFit.txt");
    }
}
