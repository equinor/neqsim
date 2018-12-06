/*
 * TestAcentric.java
 *
 * Created on 23. januar 2001, 22:08
 */

/*
 *This program calculated Water - MDEA HV interaction parameters. Two types of data is available.
 *VLE data and freezing point depression data
 */

package neqsim.thermo.util.parameterFitting.Procede.WaterMDEA;

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
 * @author  Even Solbraa
 * @version
 */
public class TestBinaryHVParameterFitting_MDEA extends java.lang.Object{

    private static final long serialVersionUID = 1000;
    
    /** Creates new TestAcentric */
    public TestBinaryHVParameterFitting_MDEA(){
    }
        
    public static void main(String[] args){
        
        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList sampleList = new ArrayList();
        double ID, pressure, temperature, x1,x2,x3, gamma1, Hm, act1, act2;
        
        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        
        //double guess[] = {1201, -1461, -7.24, 5.89, 0.21};   //Even Solbraa
        //double guess[] = {733.1497651631, -1100.3362377120, -6.0060055689, 5.0938556111, 0.2082636701}; // Ans 2 using Heat of mixing as well
        double guess[] = {-5596.6518968945, 3995.5032952165, 10.9677849623, -8.0407258862, 0.2703018372}; 
        
        ResultSet dataSet =  database.getResultSet("NeqSimDataBase",  "SELECT * FROM WaterMDEA WHERE ID<62");
        /*try{
            int i=0;
            System.out.println("adding....");
            while(dataSet.next()){
                BinaryHVParameterFittingFunction_MDEA function = new BinaryHVParameterFittingFunction_MDEA();
                function.setInitialGuess(guess);
                
                ID = Double.parseDouble(dataSet.getString("ID"));
                pressure = Double.parseDouble(dataSet.getString("Pressure"));
                temperature = Double.parseDouble(dataSet.getString("Temperature"));
                x1 = Double.parseDouble(dataSet.getString("x1"));
                x2 = Double.parseDouble(dataSet.getString("x2"));
              
                //if(ID<35)
                //    continue;
            
                SystemInterface testSystem = new SystemSrkSchwartzentruberEos(temperature, 1.5*pressure);
                testSystem.addComponent("water",x1);
                testSystem.addComponent("MDEA", x2);
                
                System.out.println("...........ID............."+ ID);
                
                testSystem.createDatabase(true);
                testSystem.setMixingRule(4);
                testSystem.init(0);
                
                double sample1[] = {temperature};  
                double standardDeviation1[] = {0.1};
                double stddev = pressure/100.0;
                SampleValue sample = new SampleValue((pressure), stddev, sample1, standardDeviation1);
                
                sample.setFunction(function);
                sample.setReference(Double.toString(ID));
                sample.setThermodynamicSystem(testSystem);
                sampleList.add(sample);
            }
        }
        catch(Exception e){
            System.out.println("database error" + e);
        }
        
        
        dataSet =  database.getResultSet("NeqSimDataBase",  "SELECT * FROM WaterMDEA WHERE ID>61 AND ID<87");
      
        try{
            int i=0;
            
            System.out.println("adding....");
            while(dataSet.next()){
                i++;
                
                BinaryHVParameterFittingFunction_MDEA function = new BinaryHVParameterFittingFunction_MDEA(1,1);
                function.setInitialGuess(guess);
                
                ID = Double.parseDouble(dataSet.getString("ID"));
                temperature = Double.parseDouble(dataSet.getString("Temperature"));
                gamma1 = Double.parseDouble(dataSet.getString("gamma1"));
                x1 = Double.parseDouble(dataSet.getString("x1"));
                x2 = Double.parseDouble(dataSet.getString("x2"));
               
                               
                SystemInterface testSystem = new SystemFurstElectrolyteEos(temperature, 1.0);
                testSystem.addComponent("water",x1);
                testSystem.addComponent("MDEA", x2);
                System.out.println("...........ID............."+ ID);
                
                testSystem.createDatabase(true);
                testSystem.setMixingRule(4);
                testSystem.init(0);
                
                double sample1[] = {temperature};  
                double standardDeviation1[] = {0.1};
                double stddev = gamma1/100.0;
                SampleValue sample = new SampleValue(gamma1, stddev, sample1, standardDeviation1);
                
                sample.setFunction(function);
                sample.setReference(Double.toString(ID));
                sample.setThermodynamicSystem(testSystem);
                sampleList.add(sample);
            }
        }
        catch(Exception e){
            System.out.println("database error" + e);
        }*/
        
        /*dataSet =  database.getResultSet("NeqSimDataBase",  "SELECT * FROM WaterMDEA WHERE ID>86");
      
        try{
            int i=0;
            
            System.out.println("adding....");
            while(dataSet.next()){
                i++;
                
                BinaryHVParameterFittingFunction_MDEA function = new BinaryHVParameterFittingFunction_MDEA(1,2);
                function.setInitialGuess(guess);
                
                ID = Double.parseDouble(dataSet.getString("ID"));
                temperature = Double.parseDouble(dataSet.getString("Temperature"));
                Hm = Double.parseDouble(dataSet.getString("HeatOfMixing"));
                x1 = Double.parseDouble(dataSet.getString("x1"));
                x2 = Double.parseDouble(dataSet.getString("x2"));
               
                               
                SystemInterface testSystem = new SystemFurstElectrolyteEos(temperature, 1.0);
                testSystem.addComponent("water",x1);
                testSystem.addComponent("MDEA", x2);
                System.out.println("...........ID............."+ ID);
                
                testSystem.createDatabase(true);
                testSystem.setMixingRule(4);
                testSystem.init(0);
                
                double sample1[] = {temperature};  
                double standardDeviation1[] = {0.1};
                double stddev = Hm/100.0;
                SampleValue sample = new SampleValue(Hm, stddev, sample1, standardDeviation1);
                
                sample.setFunction(function);
         sample.setReference(Double.toString(ID));
                sample.setThermodynamicSystem(testSystem);
                sampleList.add(sample);
            }
        }
        catch(Exception e){
            System.out.println("database error" + e);
        }*/
        
        dataSet =  database.getResultSet("NeqSimDataBase",  "SELECT * FROM WaterMDEAactivity WHERE ID<20");
        try{
            int i=0;
            System.out.println("adding....");
            while(dataSet.next()){
                BinaryHVParameterFittingFunction_MDEA function = new BinaryHVParameterFittingFunction_MDEA(1,3);
                function.setInitialGuess(guess);
                
                ID = Double.parseDouble(dataSet.getString("ID"));
                act1 = Double.parseDouble(dataSet.getString("act1"));
                temperature = Double.parseDouble(dataSet.getString("Temperature"));
                x1 = Double.parseDouble(dataSet.getString("x1"));
               
            
                SystemInterface testSystem = new SystemSrkSchwartzentruberEos(temperature, 1);
                testSystem.addComponent("water",x1);
                testSystem.addComponent("MDEA", 1-x1);
                
                System.out.println("...........ID............."+ ID);
                
                testSystem.createDatabase(true);
                testSystem.setMixingRule(4);
                testSystem.init(0);
                
                double sample1[] = {x1};  
                double standardDeviation1[] = {0.1};
                double stddev = act1/100.0;
                SampleValue sample = new SampleValue(act1, stddev, sample1, standardDeviation1);
                
                sample.setFunction(function);
                sample.setReference(Double.toString(ID));
                sample.setThermodynamicSystem(testSystem);
                sampleList.add(sample);
            }
        }
        catch(Exception e){
            System.out.println("database error" + e);
        }
        
        dataSet =  database.getResultSet("NeqSimDataBase",  "SELECT * FROM WaterMDEAactivity WHERE ID>19");
        try{
            int i=0;
            System.out.println("adding....");
            while(dataSet.next()){
                BinaryHVParameterFittingFunction_MDEA function = new BinaryHVParameterFittingFunction_MDEA(1,4);
                function.setInitialGuess(guess);
                
                ID = Double.parseDouble(dataSet.getString("ID"));
                act2 = Double.parseDouble(dataSet.getString("act2"));
                temperature = Double.parseDouble(dataSet.getString("Temperature"));
                x1 = Double.parseDouble(dataSet.getString("x1"));
               
            
                SystemInterface testSystem = new SystemSrkSchwartzentruberEos(temperature, 1);
                testSystem.addComponent("water",x1);
                testSystem.addComponent("MDEA", 1-x1);
                
                System.out.println("...........ID............."+ ID);
                
                testSystem.createDatabase(true);
                testSystem.setMixingRule(4);
                testSystem.init(0);
                
                double sample1[] = {x1};  
                double standardDeviation1[] = {0.1};
                double stddev = act2/100.0;
                SampleValue sample = new SampleValue(act2, stddev, sample1, standardDeviation1);
                
                sample.setFunction(function);
                sample.setReference(Double.toString(ID));
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
        //optim.displayGraph();
        optim.displayCurveFit();
  
    }
}
