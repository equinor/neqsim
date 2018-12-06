/*
 * TestAcentric.java
 *
 * Created on 23. januar 2001, 22:08
 */

// To find HV parameters for CO2 - MDEA system

package neqsim.thermo.util.parameterFitting.Procede.CH4MDEA;

import neqsim.util.database.NeqSimDataBase;
import java.sql.*;
import java.util.*;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkSchwartzentruberEos;
/**
 *
 * @author  Neeraj Agrawal
 * @version
 */
public class TestBinaryHVParameterFittingToEquilibriumData_CH4 extends java.lang.Object {

    private static final long serialVersionUID = 1000;
    
    /** Creates new TestAcentric */
    public TestBinaryHVParameterFittingToEquilibriumData_CH4() {
    }
    
    
    public static void main(String[] args){
        
        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList sampleList = new ArrayList();
             
        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet =  database.getResultSet("NeqSimDataBase",  "SELECT * FROM CH4MDEA"); 
        double guess[] = {500, -500, 1e-10, 1e-10, 0.3}; 
        try{
           
            while(dataSet.next()){
                BinaryHVParameterFittingFunction_CH4 function = new BinaryHVParameterFittingFunction_CH4();
                
                function.setInitialGuess(guess);
                                
                int ID = dataSet.getInt("ID");
                double temperature = Double.parseDouble(dataSet.getString("Temperature"));
                double pressure = Double.parseDouble(dataSet.getString("Pressure"));
                double x1 = Double.parseDouble(dataSet.getString("x1"));
                double x2 = Double.parseDouble(dataSet.getString("x2"));
                double x3 = Double.parseDouble(dataSet.getString("x3"));
         
                SystemInterface testSystem = new SystemSrkSchwartzentruberEos(temperature, pressure);
           
                testSystem.addComponent("methane", x1);
                testSystem.addComponent("water", x2);
                testSystem.addComponent("MDEA",x3);
                
                testSystem.createDatabase(true);
                testSystem.setMixingRule(4);
                testSystem.init(0);
                
                
                double sample1[] = {temperature}; 
                double standardDeviation1[] = {temperature/100.0}; 
                
                SampleValue sample = new SampleValue(pressure, pressure/100.0, sample1, standardDeviation1);
                                                
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(Double.toString(ID));
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
        optim.displayCurveFit();
        //optim.displayResult();
    }
}
