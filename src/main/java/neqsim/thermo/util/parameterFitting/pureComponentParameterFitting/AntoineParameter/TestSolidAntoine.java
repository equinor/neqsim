/*
 * TestAcentric.java
 *
 * Created on 23. januar 2001, 22:08
 */

package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.AntoineParameter;

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
 * @author  Even Solbraa
 * @version
 */
public class TestSolidAntoine extends java.lang.Object {

    private static final long serialVersionUID = 1000;
    
    /** Creates new TestAcentric */
    public TestSolidAntoine() {
    }
    
    
    public static void main(String[] args){
        
        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList sampleList = new ArrayList();
        
        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        
        //ResultSet dataSet =  database.getResultSet(  "SELECT * FROM BinaryFreezingPointData WHERE ComponentSolvent1='MEG' ORDER BY FreezingTemperature");
        ResultSet dataSet =  database.getResultSet(  "SELECT * FROM BinaryFreezingPointData WHERE ComponentSolvent1='MEG' ORDER BY FreezingTemperature");
        int i=0;
        try{
            System.out.println("adding....");
            while(dataSet.next() && i<4){
                i++;
                AntoineSolidFunction function = new  AntoineSolidFunction();
                double guess[] = {-7800.0, 10.09};     // MEG
                function.setInitialGuess(guess);
                
                SystemInterface testSystem = new SystemSrkCPAstatoil(280, 1.101);
                testSystem.addComponent(dataSet.getString("ComponentSolvent1"), Double.parseDouble(dataSet.getString("x1")));
                testSystem.addComponent(dataSet.getString("ComponentSolvent2"), Double.parseDouble(dataSet.getString("x2")));
                testSystem.createDatabase(true);
                testSystem.setMixingRule(7);
                
                testSystem.setSolidPhaseCheck("MEG");
                testSystem.init(0);
                double sample1[] = {testSystem.getPhase(0).getComponent(0).getz()};  // temperature
                double standardDeviation1[] = {0.1,0.1,0.1}; // std.dev temperature    // presure std.dev pressure
                double val = Double.parseDouble(dataSet.getString("FreezingTemperature"));
                testSystem.setTemperature(val);
                SampleValue sample = new SampleValue(val, val/100.0, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setReference(dataSet.getString("Reference"));
                sample.setThermodynamicSystem(testSystem);
                sampleList.add(sample);
            }
        }
        catch(Exception e){
            System.out.println("database error" + e);
        }
        
        SampleSet sampleSet = new SampleSet(sampleList);
        optim.setSampleSet(sampleSet);
        
        // do simulations
       //optim.solve();
        //optim.runMonteCarloSimulation();
        optim.displayCurveFit();
        optim.writeToCdfFile("c:/testFit.nc");
        optim.writeToTextFile("c:/testFit.txt");
    }
}
