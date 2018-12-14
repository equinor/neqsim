/*
 * TestAcentric.java
 *
 * Created on 23. januar 2001, 22:08
 */
package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.EosInteractionParameterFitting;

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
 * @author Even Solbraa
 * @version
 */
public class TestEosParameterFittingToMercurySolubility extends java.lang.Object {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new TestAcentric
     */
    public TestEosParameterFittingToMercurySolubility() {
    }

    public static void main(String[] args) {

        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList sampleList = new ArrayList();

        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();

        ResultSet dataSet = database.getResultSet( "SELECT * FROM binarySolubilityData WHERE ComponentSolute='mercury' AND ComponentSolvent='MEG'");
        //   double parameterGuess[] = {0.13}; // mercury-methane
        // double parameterGuess[] = {0.0496811275399517}; // mercury-methane
        // double parameterGuess[] = {0.0704}; // mercury-ethane
             double parameterGuess[] = {-0.03310000498911416}; // mercury-ibutane
       // double parameterGuess[] = {0.0674064646735}; // mercury-propane
        //double parameterGuess[] = { 0.3674008071}; // mercury-CO2
        //double parameterGuess[] = {  0.016529772608}; // mercury-nitrogen
        try {
            int p = 0;
            System.out.println("adding....");
            while (dataSet.next() && p < 40) {
                p++;
                CPAParameterFittingToSolubilityData function = new CPAParameterFittingToSolubilityData(0, 0);

                SystemInterface testSystem = new SystemSrkEos(290, 1.0);
                testSystem.addComponent("mercury", 10.0);
                testSystem.addComponent("MEG", 10.0);
                testSystem.getPhase(0).getComponent("mercury").setAtractiveTerm(12);
                testSystem.getPhase(1).getComponent("mercury").setAtractiveTerm(12);
                testSystem.createDatabase(true);
                testSystem.setMultiPhaseCheck(true);
                testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure"))+2);
                testSystem.setMixingRule(2);
                testSystem.init(0);
                double sample1[] = {testSystem.getPressure(), testSystem.getTemperature()};  // temperature
                double standardDeviation1[] = {0.13}; // std.dev temperature    // presure std.dev pressure
                double x1 = Double.parseDouble(dataSet.getString("x1"));
                SampleValue sample = new SampleValue(x1, x1 / 100.0, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                function.setInitialGuess(parameterGuess);
                sampleList.add(sample);
            }
        } catch (Exception e) {
            System.out.println("database error" + e);
        }

        SampleSet sampleSet = new SampleSet(sampleList);
        optim.setSampleSet(sampleSet);

        // do simulations
        optim.solve();
        //optim.runMonteCarloSimulation();
        //  optim.displayResult();
        optim.displayCurveFit();
        //optim.writeToCdfFile("c:/testFit.nc");
        //optim.writeToTextFile("c:/testFit.txt");
    }
}
