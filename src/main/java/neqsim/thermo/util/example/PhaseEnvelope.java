package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.log4j.Logger;

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
        SystemInterface testSystem = new SystemSrkCPAstatoil(280, 41.00);
        //SystemInterface testSystem = new SystemPrDanesh(273.15+80.0,100.00);
        //SystemInterface testSystem = new SystemPrEosDelft1998(223.15,50.00);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        //        testSystem.addComponent("nC10", 50.34);
        testSystem.addComponent("nitrogen", 0.1545);
        // testSystem.addComponent("H2S", 110.00821);
        //  testSystem.addComponent("CO2", 58.00821);
        testSystem.addComponent("methane", 31.465);
        testSystem.addComponent("ethane", 10.58);
        testSystem.addComponent("propane", 4.1736);
        testSystem.addComponent("i-butane", 0.008);
        testSystem.addComponent("n-butane", 0.433);
        testSystem.addComponent("i-pentane", 0.896);
     //   testSystem.addComponent("n-pentane", 1.242);
     //    testSystem.addComponent("n-hexane", 1.587);
       //    testSystem.addComponent("n-heptane", 0.068);
        //      testSystem.addComponent("n-octane", 0.127);
        //    testSystem.addComponent("n-octane", 0.027);
        // testSystem.addComponent("n-nonane", 0.003);
        //    testSystem.addTBPfraction("C6", 1.587, 86.178 / 1000.0, 0.70255);
        //   testSystem.addTBPfraction("C7", 2.566, 91.5 / 1000.0, 0.738);
        //  testSystem.addTBPfraction("C8", 2.764, 101.2 / 1000.0, 0.765);
        //  testSystem.addTBPfraction("C9", 1.71, 119.1 / 1000.0, 0.781);
     //      testSystem.addPlusFraction("C10", 10.647, 154.9 / 1000.0, 0.7871);

     //   testSystem.addComponent("water", 100.2);
        //   testSystem.addPlusFraction("C11", 22.1, 156.2 / 1000.0, 0.787278398);
        //  testSystem.getCharacterization().
        testSystem.getCharacterization().characterisePlusFraction();
        //
        testSystem.createDatabase(true);
        // testSystem.setMultiPhaseCheck(true);
        // 1- orginal no interaction 2- classic w interaction
        // 3- Huron-Vidal 4- Wong-Sandler
        testSystem.setMixingRule(9);//"UNIFAC_UMRPRU");
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
                    testOps.calcPTphaseEnvelope(true);//true);
           // isFinished = testOps.waitAndCheckForFinishedCalculation(10000);
          //  testOps.addData("water", waterData);
          //  testOps.addData("hydrate", hydData);
      //           testOps.calcPTphaseEnvelopeNew();
            testOps.displayResult();
      //      testOps.getJfreeChart();
            // testOps.dewPointPressureFlash();
            //testOps.bubblePointTemperatureFlash();
        } catch (Exception e) {
            logger.error("error",e);
        }
        testSystem.display();
        //   thermo.ThermodynamicModelTest testModel = new thermo.ThermodynamicModelTest(testSystem);
        //  testModel.runTest();

        //System.out.println("tempeerature " + (testSystem.getTemperature() - 273.15));
        //    testOps.displayResult();
        logger.info("Cricondenbar " + testOps.get("cricondenbar")[0] + " " + testOps.get("cricondenbar")[1]);
        logger.info("Cricondentherm " + testOps.get("cricondentherm")[0] + " " + testOps.get("cricondentherm")[1]);
    }
}
