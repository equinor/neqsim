/*
 * TestAcentric.java
 *
 * Created on 23. januar 2001, 22:08
 */

package neqsim.physicalProperties.util.parameterFitting.pureComponentParameterFitting.pureCompViscosity.chungMethod;

import neqsim.util.database.NeqSimDataBase;
import java.sql.*;
import java.util.*;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
/**
 *
 * @author  Even Solbraa
 * @version
 */
public class TestChungFit extends java.lang.Object {

    private static final long serialVersionUID = 1000;
    
    /** Creates new TestAcentric */
    public TestChungFit() {
    }
    
    
    public static void main(String[] args){
        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList sampleList = new ArrayList();
        
        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet =  database.getResultSet("SELECT * FROM PureComponentViscosity");// WHERE ComponentName='MDEA*'");

        try{
            
            System.out.println("adding....");
            while(dataSet.next()){
                ChungFunction function = new ChungFunction();
                double guess[] = {0.3211};
                function.setInitialGuess(guess);
                
                SystemInterface testSystem = new SystemSrkEos(280, 0.001);
                testSystem.addComponent("MDEA", 100.0);             // legger til komponenter til systemet
               // testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                testSystem.createDatabase(true);
                testSystem.init(0);
                testSystem.setMixingRule(2);
                double sample1[] = {Double.parseDouble(dataSet.getString("Temperature"))};  // temperature
                double standardDeviation1[] = {0.1}; // std.dev temperature    // presure std.dev pressure
                SampleValue sample = new SampleValue(Double.parseDouble(dataSet.getString("Viscosity")), 0.001, sample1, standardDeviation1);
                sample.setFunction(function);
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
        optim.solve();
        //optim.runMonteCarloSimulation();
        optim.displayResult();
        optim.displayCurveFit();
        
    }
}
