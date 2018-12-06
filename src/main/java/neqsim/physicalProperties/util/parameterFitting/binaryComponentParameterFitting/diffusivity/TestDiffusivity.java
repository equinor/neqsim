/*
 * TestAcentric.java
 *
 * Created on 23. januar 2001, 22:08
 */

package neqsim.physicalProperties.util.parameterFitting.binaryComponentParameterFitting.diffusivity;

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
public class TestDiffusivity extends java.lang.Object {

    private static final long serialVersionUID = 1000;
    
    /** Creates new TestAcentric */
    public TestDiffusivity() {
    }
    
    
    public static void main(String[] args){
        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList sampleList = new ArrayList();
        
        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet =  database.getResultSet("NeqSimDataBase",  "SELECT * FROM BinaryLiquidDiffusionCoefficientData WHERE ComponentSolute='CO2' AND ComponentSolvent='water'");
        
        try{
            System.out.println("adding....");
            while(dataSet.next()){
                DiffusivityFunction function = new DiffusivityFunction();
                double guess[] = {0.001};
                function.setInitialGuess(guess);
                SystemInterface testSystem = new SystemSrkEos(280, 0.001);
                testSystem.addComponent(dataSet.getString("ComponentSolute"), 1.0e-10);
                testSystem.addComponent(dataSet.getString("ComponentSolvent"), 1.0);
                testSystem.createDatabase(true);
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
                testSystem.init(0);
                testSystem.setPhysicalPropertyModel(4);
                testSystem.initPhysicalProperties();
                double sample1[] = {testSystem.getTemperature()};  // temperature
                double standardDeviation1[] = {0.1}; // std.dev temperature    // presure std.dev pressure
                SampleValue sample = new SampleValue(Double.parseDouble(dataSet.getString("DiffusionCoefficient")), 0.01, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sampleList.add(sample);
            }
        }
        catch(Exception e){
            System.out.println("database error" + e);
        }
//        
//        double sample1[] = {0.1};
//        for(int i=0;i<sampleList.size();i++){
//            System.out.println("ans: " + ((SampleValue)sampleList.get(i)).getFunction().calcValue(sample1));
//        }
        
        SampleSet sampleSet = new SampleSet(sampleList);
        optim.setSampleSet(sampleSet);
        
        // do simulations
        //optim.solve();
        //optim.runMonteCarloSimulation();
        optim.displayGraph();
        //optim.writeToTextFile("c:/testFit.txt");
        
    }
}
