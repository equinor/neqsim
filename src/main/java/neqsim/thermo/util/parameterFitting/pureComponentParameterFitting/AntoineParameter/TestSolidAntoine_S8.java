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
import neqsim.thermo.system.SystemSrkEos;
/**
 *
 * @author  Even Solbraa
 * @version
 */
public class TestSolidAntoine_S8 extends java.lang.Object {

    private static final long serialVersionUID = 1000;
    
    /** Creates new TestAcentric */
    public TestSolidAntoine_S8() {
    }
    
    
    public static void main(String[] args){
        
        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList sampleList = new ArrayList();
        
        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        
        ResultSet dataSet =  database.getResultSet(  "SELECT * FROM PureComponentVapourPressures WHERE ComponentName='S8' AND VapourPressure<100");
        
        try{
            System.out.println("adding....");
            while(dataSet.next()){
                AntoineSolidFunctionS8 function = new  AntoineSolidFunctionS8();
                //double guess[] = {8.046, -4600.0, -144.0};     // S8
                double guess[] = {1.181E1, -8.356E3};     // S8
                function.setInitialGuess(guess);
                
               SystemInterface testSystem = new SystemSrkEos(280, 0.001);
                testSystem.addComponent(dataSet.getString("ComponentName"), 100.0);             // legger til komponenter til systemet
                double sample1[] = {Double.parseDouble(dataSet.getString("Temperature"))};  // temperature
                double vappres = Double.parseDouble(dataSet.getString("VapourPressure"));
                double standardDeviation1[] = {0.15}; // std.dev temperature    // presure std.dev pressure
                SampleValue sample = new SampleValue(vappres, Double.parseDouble(dataSet.getString("StandardDeviation")), sample1, standardDeviation1);
                sample.setFunction(function);
                
                function.setInitialGuess(guess);
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
        optim.displayCurveFit();
        optim.writeToCdfFile("c:/testFit.nc");
        optim.writeToTextFile("c:/testFit.txt");
    }
}
