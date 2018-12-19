/*
 * TestAcentric.java
 *
 * Created on 23. januar 2001, 22:08
 */

package neqsim.thermo.util.parameterFitting.Procede.Density;

import neqsim.util.database.NeqSimDataBase;
import java.sql.*;
import java.util.*;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkSchwartzentruberEos;
import org.apache.log4j.Logger;
/**
 *
 * @author  Even Solbraa
 * @version
 */
public class TestRackettZ extends java.lang.Object {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(TestRackettZ.class);
    
    
    public TestRackettZ() {
    }
    
    
    public static void main(String[] args){
        
        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList sampleList = new ArrayList();
        
        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet =  database.getResultSet(  "SELECT * FROM PureComponentDensity WHERE ComponentName = 'Water'");
               
        try{
            logger.info("adding....");
            while(dataSet.next()){
                RackettZ function = new  RackettZ();
                //double guess[] = {0.2603556815};  //MDEA   
                double guess[] = {0.2356623744};    // Water 
                function.setInitialGuess(guess);
                            
                double T = Double.parseDouble(dataSet.getString("Temperature"));
                double P = Double.parseDouble(dataSet.getString("Pressure"));
                double density = Double.parseDouble(dataSet.getString("Density"));
                
                SystemInterface testSystem = new SystemSrkSchwartzentruberEos(T, P);
                testSystem.addComponent("water", 1.0);
                
                testSystem.createDatabase(true);
                testSystem.useVolumeCorrection(true);
                testSystem.setMixingRule(4);
                testSystem.init(0);
                testSystem.init(1);
                
                
                double sample1[] = {T};  // temperature
                double standardDeviation1[] = {T/100}; // std.dev temperature    // presure std.dev pressure
                
                SampleValue sample = new SampleValue(density, density/100.0, sample1,standardDeviation1 );
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
        optim.displayCurveFit();
    }
}
