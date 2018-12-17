/*
 * TestAcentric.java
 *
 * Created on 23. januar 2001, 22:08
 */

package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.acentricFactorFitting;

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
public class TestFitToAntoineVapPres extends java.lang.Object {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(TestFitToAntoineVapPres.class);
    
    /** Creates new TestAcentric */
    public TestFitToAntoineVapPres() {
    }
    
    
    public static void main(String[] args){
        
        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList sampleList = new ArrayList();
        
        
        try{
            for(int i=0;i<30;i++){
                AcentricFunctionScwartzentruber function = new  AcentricFunctionScwartzentruber();
                //double guess[] = {0.5212918734, -1.1520807481, -0.0138898820};        // Piperazine
                double guess[] = {0.1032, 0.00365, -2.064};        // AceticAcid
                function.setInitialGuess(guess);
                
                SystemInterface testSystem = new SystemSrkSchwartzentruberEos(273.15+i*5, 1.101);
                // testSystem.addComponent("Piperazine", 100.0);
                testSystem.addComponent("AceticAcid", 100.0);
                testSystem.createDatabase(true);
                testSystem.setMixingRule(0);
                
                SystemInterface testSystemAntoine = new SystemSrkSchwartzentruberEos(273.15+i*5, 1.101);
                // testSystemAntoine.addComponent("Piperazine", 100.0);
                testSystemAntoine.addComponent("AceticAcid", 100.0);
                testSystemAntoine.setMixingRule(0);
                neqsim.thermodynamicOperations.ThermodynamicOperations opsAntione = new neqsim.thermodynamicOperations.ThermodynamicOperations(testSystemAntoine);
                
                double sample1[] = {i*5+273.15};  // temperature
                double standardDeviation1[] = {0.1,0.1,0.1}; // std.dev temperature    // presure std.dev pressure
                double val = Math.log(testSystemAntoine.getPhase(0).getComponent(0).getAntoineVaporPressure(273.15+i*5));
//                opsAntione.bubblePointPressureFlash(false);
//                double val = Math.log(testSystemAntoine.getPressure());
                SampleValue sample = new SampleValue(val, val/100.0, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sampleList.add(sample);
            }
        }
        catch(Exception e){
            logger.info("database error" + e);
        }
        
        SampleSet sampleSet = new SampleSet(sampleList);
        optim.setSampleSet(sampleSet);
        
        // do simulations
        //optim.solve();
        //optim.runMonteCarloSimulation();
        optim.displayCurveFit();
        //        optim.writeToCdfFile("c:/testFit.nc");
        //        optim.writeToTextFile("c:/testFit.txt");
    }
}
