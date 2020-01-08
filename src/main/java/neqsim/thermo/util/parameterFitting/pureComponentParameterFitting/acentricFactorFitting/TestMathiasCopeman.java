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
import neqsim.thermo.system.SystemSrkMathiasCopeman;
import neqsim.thermo.system.SystemSrkTwuCoonEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.logging.log4j.*;
/**
 *
 * @author  Even Solbraa
 * @version
 */
public class TestMathiasCopeman extends java.lang.Object {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(TestMathiasCopeman.class);
    
    /** Creates new TestAcentric */
    public TestMathiasCopeman() {
    }
    
    
    public static void main(String[] args){
    	
        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList sampleList = new ArrayList();
        
       // String ComponentName = "CO2";
        String ComponentName = "methane";
       // String ComponentName = "ethane";
      //  String ComponentName = "propane";
        //String ComponentName = "n-butane"; 
      // String ComponentName = "i-butane";
        //String ComponentName = "i-pentane";
        // String ComponentName = "n-pentane";
        // String ComponentName = "nitrogen";
        //String ComponentName = "neon";
        
        try{
            //for(int i=0;i<30;i++){
                MathiasCopeman function = new  MathiasCopeman();
               // double guess[] = {0.8817389299100502,-0.8562550217314793,3.0949393273488686};  // CO2 chi sqr 0.0025 
                 double guess[] ={0.5379142011507664,-0.3219393792621987,0.4361033659755846} ;  // Methane  chi sqr 0.1425
                //double guess[] ={0.654848543521529,-0.18438917116800416,0.2601600498810114} ;  // ethane  chi sqr 0.35
               // double guess[] ={0.721211485876844,-0.09921664231818139,0.18182922712791916} ;  // PROPANE  chi sqr 0.2645
              //  double guess[] ={0.8080635549718828,-0.21272494485460103,0.36296147537551615} ;  // n-butane chi sqr 0.2645
                //double guess[] ={0.7803883597786199,-0.27338954339527566,0.6597871185507804} ;  // i-butane chi sqr 0.166
                //double guess[] ={0.8578874087217438,-0.26763822614020133,0.4586427557929788} ;  // i-pentane chi sqr 0.699
               // double guess[] ={0.9038611341253704,-0.3585531518594213,0.6638852213604798} ;  // n-pentane chi sqr 0.827
                //double guess[] ={0.5742583288610631,-0.3225948761079366,0.5906544971686629} ;  // nitrogen chi sqr 0.05144
                //double guess[] ={0.4844539990236587,-0.5088139220535864,0.7223945129559358} ;  // neon chi sqr 0.0577
                              
                
               function.setInitialGuess(guess);
                
                //SystemInterface testSystem = new SystemSrkSchwartzentruberEos(40, 1);
                //SystemInterface testSystem = new SystemSrkEos(280, 1);
                SystemInterface testSystem = new SystemSrkMathiasCopeman(280, 5);
                testSystem.addComponent(ComponentName, 100.0);
               // testSystem.addComponent(ComponentName2, 100.0);
                testSystem.setMixingRule(2);
                
                testSystem.createDatabase(true);
                
                //SystemInterface System2 = new SystemSrkSchwartzentruberEos(280, 5);
                SystemInterface System2 = new SystemSrkTwuCoonEos(280, 5);
                
                
                System2.addComponent(ComponentName, 100.0);
                //System2.addComponent(ComponentName2, 100.0);
                System2.setMixingRule(2);
                ThermodynamicOperations Ops = new ThermodynamicOperations(System2);
                
                
                double Ttp = testSystem.getPhase(0).getComponent(0).getTriplePointTemperature();
                double TC = testSystem.getPhase(0).getComponent(0).getTC();
                
                for(int i=0;i<30;i++){
                double temperature = Ttp+((TC-Ttp)/30)*i;
                //kan legge inn dewTflash for aa finne avvik til tilsvarende linje med schwarzentruber... da ogsaa for flerkomponent blandinger istedenfor antoine ligningen.
                //double pressure = testSystem.getPhase(0).7getComponent(0).getAntoineVaporPressure(temperature);
                System2.setTemperature(temperature);
                Ops.dewPointPressureFlash();
                double pressure = System2.getPressure();
                
                double sample1[] = {temperature};  // temperature
                double standardDeviation1[] = {0.1,0.1,0.1}; // std.dev temperature    // presure std.dev pressure
                double val = Math.log(pressure);
                SampleValue sample = new SampleValue(val, val/100.0, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setReference("NIST");
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
        
  //   optim.solve();
        //optim.runMonteCarloSimulation();
        optim.displayCurveFit();
//        optim.writeToCdfFile("c:/testFit.nc");
//        optim.writeToTextFile("c:/testFit.txt");
    }
}
