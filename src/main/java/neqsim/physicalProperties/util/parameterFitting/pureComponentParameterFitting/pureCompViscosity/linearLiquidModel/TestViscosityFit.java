/*
 * TestAcentric.java
 *
 * Created on 23. januar 2001, 22:08
 */

package neqsim.physicalProperties.util.parameterFitting.pureComponentParameterFitting.pureCompViscosity.linearLiquidModel;

import neqsim.util.database.NeqSimDataBase;
import java.sql.*;
import java.util.*;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.apache.log4j.Logger;
/**
 *
 * @author  Even Solbraa
 * @version
 */
public class TestViscosityFit extends java.lang.Object {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(TestViscosityFit.class);
    
    /** Creates new TestAcentric */
    public TestViscosityFit() {
    }
    
    
    public static void main(String[] args){
        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList sampleList = new ArrayList();
        
        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet =  database.getResultSet("SELECT * FROM purecomponentviscosity WHERE ComponentName='MEG' ORDER BY Temperature");
       
        try{
            while(dataSet.next()){
                ViscosityFunction function = new ViscosityFunction();
                //double guess[] = {-66.2, 11810, 0.1331, -0.0000983}; //mdea
            //    double guess[] = {-5.771E1, 7.647E3, 1.442E-1, -1.357E-4}; //water
                double guess[] = {-10.14, 3868.803, -0.00550507};//,0.000001};//,0.001}; //MEG
              //    double guess[] = { -53.92523097004079, 9741.992308,0,0.106066223998382}; //TEG
                function.setInitialGuess(guess);
                SystemInterface testSystem = new SystemSrkEos(280, 0.001);
                //logger.info("component " + dataSet.getString("ComponentName"));
                testSystem.addComponent(dataSet.getString("ComponentName"), 100.0);             // legger til komponenter til systemet
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                testSystem.createDatabase(true);
                testSystem.setMixingRule(2);
                double temp = Double.parseDouble(dataSet.getString("Temperature"));
                testSystem.setTemperature(temp);
                testSystem.init(0);
                double sample1[] = {temp};  // temperature
                double standardDeviation1[] = {0.1}; // std.dev temperature    // presure std.dev pressure
                SampleValue sample = new SampleValue(Double.parseDouble(dataSet.getString("Viscosity")), Double.parseDouble(dataSet.getString("StdDev")), sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sampleList.add(sample);
            }
        }
        catch(Exception e){
            logger.error("database error" + e);
        }
        
        double sample1[] = {0.1};
        for(int i=0;i<sampleList.size();i++){
        //   logger.info("ans: " + ((SampleValue)sampleList.get(i)).getFunction().calcValue(sample1));
        }
        
        SampleSet sampleSet = new SampleSet(sampleList);
        optim.setSampleSet(sampleSet);
        optim.setMaxNumberOfIterations(100);
        // do simulations
        optim.solve();
        optim.displayCurveFit();
        //optim.runMonteCarloSimulation();
        //optim.displayCurveFit();
        //optim.writeToTextFile("c:/testFit.txt");
        
    }
}
