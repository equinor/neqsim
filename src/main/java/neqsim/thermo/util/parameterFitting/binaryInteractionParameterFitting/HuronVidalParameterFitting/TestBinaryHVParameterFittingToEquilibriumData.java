/*
 * TestAcentric.java
 *
 * Created on 23. januar 2001, 22:08
 */

package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.HuronVidalParameterFitting;

import neqsim.util.database.NeqSimDataBase;
import java.sql.*;
import java.util.*;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.apache.logging.log4j.*;
/**
 *
 * @author  Even Solbraa
 * @version
 */
public class TestBinaryHVParameterFittingToEquilibriumData extends java.lang.Object {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(TestBinaryHVParameterFittingToEquilibriumData.class);
    
    /** Creates new TestAcentric */
    public TestBinaryHVParameterFittingToEquilibriumData() {
    }
    
    
    public static void main(String[] args){
        
        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList sampleList = new ArrayList();
        
        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet =  database.getResultSet(  "SELECT * FROM BinaryEquilibriumData WHERE Component1='methane' AND Component2='ethane'");
        //  ResultSet dataSet =  database.getResultSet(  "SELECT * FROM activityCoefficientTable WHERE Component1='MDEA' AND Component2='water'");
        
        try{
            
            logger.info("adding....");
            while(dataSet.next()){
                BinaryHVParameterFittingToEquilibriumData function = new BinaryHVParameterFittingToEquilibriumData();
                double guess[] = {1000, 1000};
                function.setInitialGuess(guess);
                
                SystemInterface testSystem = new SystemSrkEos(280, 0.01);
                testSystem.addComponent(dataSet.getString("Component1"), 1.0);
                testSystem.addComponent(dataSet.getString("Component2"), 1.0);
                testSystem.setMixingRule(3);
                testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                double sample1[] = {Double.parseDouble(dataSet.getString("x1")),  Double.parseDouble(dataSet.getString("y1"))};  // temperature
                double standardDeviation1[] = {0.01,0.01}; // std.dev temperature    // presure std.dev pressure
                SampleValue sample = new SampleValue(0.0, 0.01, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sampleList.add(sample);
            }
        }
        catch(Exception e){
            logger.error("database error" + e);
        }
        
        SampleSet sampleSet = new SampleSet(sampleList);
        optim.setSampleSet(sampleSet);
        
        // do simulations
        optim.solve();
        //optim.runMonteCarloSimulation();
        optim.displayResult();
    }
}
