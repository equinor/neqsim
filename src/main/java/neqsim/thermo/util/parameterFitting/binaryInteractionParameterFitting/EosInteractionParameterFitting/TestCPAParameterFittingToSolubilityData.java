/*
 * TestAcentric.java
 *
 * Created on 23. januar 2001, 22:08
 */
package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.EosInteractionParameterFitting;

import neqsim.util.database.NeqSimDataBase;
import neqsim.util.database.NeqSimExperimentDatabase;

import java.sql.*;
import java.util.*;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import org.apache.logging.log4j.*;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class TestCPAParameterFittingToSolubilityData extends java.lang.Object {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(TestCPAParameterFittingToSolubilityData.class);
    

    /**
     * Creates new TestAcentric
     */
    public TestCPAParameterFittingToSolubilityData() {
    }

    public static void main(String[] args) {

        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList sampleList = new ArrayList();

        // inserting samples from database
        NeqSimExperimentDatabase database = new NeqSimExperimentDatabase();
        ResultSet dataSet =  database.getResultSet(  "SELECT * FROM binarySolubilityData WHERE ComponentSolute='CO2' AND ComponentSolvent='water' AND Reference='Houghton1957' AND Reference<>'Nighswander1989' AND Temperature>283.15 AND Temperature<373.15 AND Pressure<60.01325 ORDER BY Temperature");
        //ResultSet dataSet = database.getResultSet( "SELECT * FROM binarysolubilitydata WHERE ComponentSolute='methane' AND ComponentSolvent='water'  AND Temperature>278.0 AND Temperature<350.0");
        double parameterGuess[] = {-0.27686, 0.001121};//, 0.000117974}; //cpa
               
        try {
            int p = 0;
            logger.info("adding....");
            while (dataSet.next() && p < 200) {
                p++;
                CPAParameterFittingToSolubilityData function = new CPAParameterFittingToSolubilityData(1,0);

                SystemInterface testSystem = new SystemSrkCPAstatoil(290, 1.0);
                //SystemInterface testSystem = new SystemSrkEos(290, 1.0);
                testSystem.addComponent("CO2", 1.0);
                testSystem.addComponent("water", 10.0);
                testSystem.createDatabase(true);
                testSystem.setMixingRule(10);
                testSystem.setMultiPhaseCheck(true);
                testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
              
                testSystem.init(0);
                double sample1[] = {testSystem.getPressure(), testSystem.getTemperature()};  // temperature
                double standardDeviation1[] = {0.13,0.12}; // std.dev temperature    // presure std.dev pressure
                double expVal = Double.parseDouble(dataSet.getString("x1"));
                SampleValue sample = new SampleValue(expVal, Double.parseDouble(dataSet.getString("StandardDeviation")), sample1, standardDeviation1);
                function.setInitialGuess(parameterGuess);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(Double.toString(testSystem.getTemperature()));
                //double parameterGuess[] = {-0.130}; //srk
                // double parameterGuess[] = {-0.0668706940}; //cpa
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }
        /*
        
        dataSet = database.getResultSet( "SELECT * FROM BinaryEquilibriumData WHERE Component1='methane' AND Component2='MEG'");
        try {
            int p = 0;
            logger.info("adding....");
            while (!dataSet.next() && p < 0) {
                p++;
                CPAParameterFittingToSolubilityData function = new CPAParameterFittingToSolubilityData(0,1);

                SystemInterface testSystem = new SystemSrkCPAstatoil(290, 1.0);
                //SystemInterface testSystem = new SystemSrkEos(290, 1.0);
                testSystem.addComponent("methane", 1.0);
                testSystem.addComponent("MEG", 1.0);
                //testSystem.createDatabase(true);
                testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                testSystem.setMixingRule(10);
                testSystem.init(0);
                double sample1[] = {testSystem.getPressure(), testSystem.getTemperature()};  // temperature
                double standardDeviation1[] = {0.13}; // std.dev temperature    // presure std.dev pressure
                double value = Double.parseDouble(dataSet.getString("y2"));
                SampleValue sample = new SampleValue(value, value/100.0, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(Double.toString(testSystem.getTemperature()));
                //double parameterGuess[] = {-0.130}; //srk
                // double parameterGuess[] = {-0.0668706940}; //cpa
                function.setInitialGuess(parameterGuess);
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }
*/

        SampleSet sampleSet = new SampleSet(sampleList);
        optim.setSampleSet(sampleSet);

        // do simulations
   //     optim.solve();
        //optim.runMonteCarloSimulation();
       optim.calcDeviation();
            optim.displayResult();
        optim.displayCurveFit();
        //optim.writeToCdfFile("c:/testFit.nc");
        //optim.writeToTextFile("c:/testFit.txt");
    }
}
