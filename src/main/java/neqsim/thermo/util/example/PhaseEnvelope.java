package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemDefault;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.*;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

import java.awt.image.BufferedImage;

import org.apache.log4j.Logger;
import org.jfree.chart.JFreeChart;

/*
 * PhaseEnvelope.java
 *
 * Created on 27. september 2001, 10:21
 */

/**
 *
 * @author esol
 * @version
 */
public class PhaseEnvelope {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(PhaseEnvelope.class);

    /**
     * Creates new PhaseEnvelope
     */
    public PhaseEnvelope() {
    }

    public static void main(String args[]) {

        // SystemInterface testSystem = new SystemUMRPRUEos(225.65, 1.00);
        //SystemInterface testSystem = new SystemPrEos1978(223.15,50.00);
        //SystemInterface testSystem = new SystemPrGassemEos(253.15,50.00);
        SystemInterface testSystem = new SystemUMRPRUMCEos(280.0, 41.00);
        //SystemInterface testSystem = new SystemPrDanesh(273.15+80.0,100.00);
        //SystemInterface testSystem = new SystemPrEosDelft1998(223.15,50.00);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

        testSystem.addComponent("nitrogen",1.1715);
        testSystem.addComponent("CO2", 1.2661);
        testSystem.addComponent("methane", 76.9645);
        testSystem.addComponent("ethane", 10.3185);
        testSystem.addComponent("propane", 6.7815);
        testSystem.addComponent("i-butane", 0.9409);
        testSystem.addComponent("n-butane", 1.7977);
        testSystem.addComponent("22-dim-C3", 1.7977);
        testSystem.addComponent("i-pentane", 0.2678);
        testSystem.addComponent("n-pentane",0.2323);
        testSystem.addComponent("c-C5", 0.0112);
        testSystem.addComponent("22-dim-C4", 0.0022);
        testSystem.addComponent("23-dim-C4", 0.0027);
        testSystem.addComponent("2-m-C5", 0.0177);
        testSystem.addComponent("3-m-C5", 0.0082);
        testSystem.addComponent("n-hexane", 0.0156);

        testSystem.addComponent("n-heptane", 0.0024838567);
        testSystem.addComponent("c-hexane", 0.0123687984);
        testSystem.addComponent("benzene", 0.0050181982);
        
        testSystem.addComponent("n-octane", 0.0000100970);
        testSystem.addComponent("c-C7", 0.0009491160);
        testSystem.addComponent("toluene", 0.0005553338);
        testSystem.addComponent("n-nonane", 0.0000201940);
        testSystem.addComponent("c-C8", 0.0001110668);
        //testSystem.addComponent("m-Xylene", 0.0000000000);
        testSystem.addComponent("nC10", 0.0001110668);
        
       // testSystem.addComponent("nC10", 1e-4);
         
         
          
        //    testSystem.addComponent("n-octane", 0.027);
       // testSystem.addComponent("nC13", .3);
        //    testSystem.addTBPfraction("C6", 1.587, 86.178 / 1000.0, 0.70255);
        //   testSystem.addTBPfraction("C7", 2.566, 91.5 / 1000.0, 0.738);
        //  testSystem.addTBPfraction("C8", 2.764, 101.2 / 1000.0, 0.765);
        //  testSystem.addTBPfraction("C9", 1.71, 119.1 / 1000.0, 0.781);
       // testSystem.addTBPfraction("C10", 1.647, 254.9 / 1000.0, 0.894871);

     //   testSystem.addComponent("water", 100.2);
          // testSystem.addPlusFraction("C11", 0.01, 256.2 / 1000.0, 0.92787278398);
        //  testSystem.getCharacterization().
      
    //    testSystem.getCharacterization().getLumpingModel().setNumberOfLumpedComponents(12);
      // testSystem.getCharacterization().characterisePlusFraction();
        //
        testSystem.createDatabase(true);

   //     testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");
        // testSystem.setMultiPhaseCheck(true);
        // 1- orginal no interaction 2- classic w interaction
        // 3- Huron-Vidal 4- Wong-Sandler
        //testSystem.setMixingRule(2);//"UNIFAC_UMRPRU");
   //     testSystem.setHydrateCheck(true);
        // testSystem.setBmixType(0);

        // Calculates the phase envelope for the  mixture
        //testOps.calcPTphaseEnvelope(true);

        // Calculates the phase envelope for pashe fraction x and 1-x
        //calcPTphaseEnvelope(minimum pressure, phase fraction);
        try {

            /*
            testOps.setRunAsThread(true);
            testOps.waterDewPointLine(10, 200);
            boolean isFinished = testOps.waitAndCheckForFinishedCalculation(50000);
            double[][] waterData = testOps.getData();
            
            testOps.hydrateEquilibriumLine(10, 200);
            isFinished = testOps.waitAndCheckForFinishedCalculation(50000);
            double[][] hydData = testOps.getData();
            
            testSystem.addComponent("water", -testSystem.getPhase(0).getComponent("water").getNumberOfmoles());
           */
                    testOps.calcPTphaseEnvelope();//true);
           // isFinished = testOps.waitAndCheckForFinishedCalculation(10000);
          //  testOps.addData("water", waterData);
          //  testOps.addData("hydrate", hydData);
      //           testOps.calcPTphaseEnvelopeNew();
            testOps.displayResult();
      //      testOps.getJfreeChart();
            // testOps.dewPointPressureFlash();
            //testOps.bubblePointTemperatureFlash();
          //  JFreeChart jfreeObj = testOps.getJfreeChart();
         //   BufferedImage buf = jfreeObj.createBufferedImage(640, 400, null);
        } catch (Exception e) {
            logger.error("error",e);
        }
       
        testSystem.display();
      //  testOps.get("DewT");
        //   thermo.ThermodynamicModelTest testModel = new thermo.ThermodynamicModelTest(testSystem);
        //  testModel.runTest();

        //System.out.println("tempeerature " + (testSystem.getTemperature() - 273.15));
        //    testOps.displayResult();
        logger.info("Cricondenbar " + testOps.get("cricondenbar")[0] + " " + testOps.get("cricondenbar")[1]);
        logger.info("Cricondentherm " + testOps.get("cricondentherm")[0] + " " + testOps.get("cricondentherm")[1]);
    }
}
