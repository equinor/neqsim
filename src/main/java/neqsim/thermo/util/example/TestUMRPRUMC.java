package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemUMRPRUMCEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/*
 * TPflash.java
 *
 * Created on 27. september 2001, 09:43
 */

/*
 *
 * @author esol @version
 */
public class TestUMRPRUMC {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new TPflash
     */
    public TestUMRPRUMC() {
    }

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemUMRPRUMCEos(273.15 + 20, 100);
        //    SystemInterface testSystem = new SystemSrkEos(273.15 + 20, 15.0);
       // SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 20, 1.0);
        //testSystem.getCharacterization().setTBPModel("PedersenPR");//(RiaziDaubert  PedersenPR  PedersenSRK
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        //         testSystem.addComponent("CO2", 0.1);
          testSystem.addComponent("CO2", 1);
        testSystem.addComponent("H2S", 1);
        testSystem.addComponent("methane", 1000.0);
        testSystem.addComponent("ethane", 10.0);
        testSystem.addComponent("propane", 10.0);
        testSystem.addComponent("i-butane", 10.0);
        testSystem.addComponent("n-butane", 10.0);
        testSystem.addComponent("n-butane", 10.0);
       // testSystem.addComponent("22-dim-C3", 10.0);
        testSystem.addComponent("i-pentane", 10.0);
        testSystem.addComponent("n-pentane", 10.0);
       // testSystem.addComponent("c-C5", 10.0);
      //  testSystem.addComponent("2-m-C5", 10.0);
        testSystem.addComponent("n-hexane", 10.0);
        testSystem.addComponent("benzene", 10.0);
        testSystem.addComponent("c-hexane", 10.0);
     /*   testSystem.addComponent("c-hexane", 10.0);
        testSystem.addComponent("c-hexane", 10.0);
        testSystem.addComponent("223-TM-C4", 10.0);
        testSystem.addComponent("n-heptane", 10.0);
        testSystem.addComponent("n-heptane", 10.0);
        testSystem.addComponent("M-cy-C6", 10.0);
        testSystem.addComponent("toluene", 10.0);
        testSystem.addComponent("33-DM-C6", 10.0);
        testSystem.addComponent("n-octane", 10.0);
        testSystem.addComponent("ethylcyclohexane", 10.0);
        testSystem.addComponent("m-Xylene", 10.0);
        testSystem.addComponent("3-M-C8", 10.0);
        testSystem.addComponent("n-nonane", 10.0);
        testSystem.addComponent("n-Bcychexane", 10.0);
        testSystem.addComponent("Pent-CC6", 10.0);
      //  testSystem.addComponent("methanol", 10.0);*/
       //  testSystem.addComponent("water", 10.0);
        //   testSystem.addComponent("n-octane", 10.0);
        //   

           testSystem.addTBPfraction("C8", 1.0, 100.0 / 1000.0, 0.8);
            testSystem.addTBPfraction("LP_C17", 0.03, 238.779998779297 / 1000.0, 0.84325);
        //   testSystem.addComponent("ethane", 1.0);
    //    testSystem.addComponent("water", 7.0);
        //  testSystem.addComponent("CO2", 1.0e-10);
    //    testSystem.addComponent("MEG", 3.0);
        //      testSystem.addComponent("ethane", 0.375);
//       //    testSystem.addComponent("ethane", 99.9);
        //   testSystem.addComponent("nC27", 0.25);
        testSystem.createDatabase(true);
      //  testSystem.setHydrateCheck(true);
        //testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");
           testSystem.setMixingRule(2);
        //  testSystem.setMultiPhaseCheck(true);
        testSystem.init(0);
        //  testSystem.setAtractiveTerm(13);
        try {
            testOps.TPflash();
            for (int i = 0; i < 1000000; i++) {
                testOps.TPflash();
                testSystem.init(3);
             //   testOps.hydrateFormationTemperature();
             testSystem.init(3);
            }
            //testOps.bubblePointPressureFlash(false);
            //  testOps.dewPointTemperatureFlash(false);
            //       testOps.calcPTphaseEnvelope(false);
            //        testOps.displayResult();
        } catch (Exception e) {
            System.out.println(e.toString());
        }
        //System.out.println("activity coefficient water " + testSystem.getPhase(1).getActivityCoefficient(1));
        testSystem.display();
   //     testSystem.saveFluid(30);
        //testSystem.saveObject(2187);

    }
}
