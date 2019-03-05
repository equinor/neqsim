package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemSrkPenelouxEos;
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
public class TPflash {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new TPflash
     */
    public TPflash() {
    }

    public static void main(String[] args) {

        // SystemInterface testSystem = new SystemSrkEos(293.15, 999999.999999999/1.0e5);
        SystemInterface testSystem = new SystemSrkPenelouxEos(293.15, 999999.999999999 / 1.0e5);
        //SystemInterface testSystem = new SystemSrkCPAstatoil(86273.15 + 45.0, 22.0);//
        //   testSystem.addComponent("nitrogen", 72);
        //   testSystem.addComponent("oxygen", 28);

        testSystem.addComponent("methane", 79);
         testSystem.addComponent("n-hexane", 79);
        testSystem.addComponent("water", 21);
        testSystem.useVolumeCorrection(false);

        ///  testSystem.addComponent("nC10", 28.0);
        //estSystem.addComponent("benzene",21);
        //   testSystem.addTBPfraction("C10", 50.178, 248.5 / 1000.0, 0.81982);
        /*
        //testSystem.addComponent("mercury", 1);
        testSystem.addComponent("methane", 17.42);
        testSystem.addComponent("ethane", 1.454);
        testSystem.addComponent("propane", 2.914);
        testSystem.addComponent("i-butane", 1.146);
        testSystem.addComponent("n-butane", 2.75);
          testSystem.addComponent("n-pentane", 4.0);
          testSystem.addComponent("n-hexane", 3.949);
        //     testSystem.addComponent("CO2", 49.390402);
        testSystem.addTBPfraction("C7", 4.96, 96.2 / 1000.0, 0.7116912);
        testSystem.addTBPfraction("C8", 5.467, 109.2 / 1000.0, 0.7393);
        testSystem.addTBPfraction("C9", 4.387, 123.5 / 1000.0, 0.7583);
        testSystem.addTBPfraction("C10", 50.178, 348.5 / 1000.0, 0.8982);
         */
        //  testSystem.setHeavyTBPfractionAsPlusFraction();
        ///  testSystem.getCharacterization().characterisePlusFraction();
        // testSystem.addTBPfraction("C111", 4.58, 236.5 / 1000.0, 0.8398);
        //      testSystem.addComponent("methanol", 1);
        // testSystem.addComponent("MDEA", 1);
        //      testSystem.addComponent("i-butane", 2.68E-4);
        //      testSystem.addComponent("n-butane", 3.41E-4);
        // testSystem.addTBPfraction("C7", 0.2, 200.0 / 1000.0, 0.8932);
        //     testSystem.addComponent("MEG", 0.00476);
        // SystemInterface testSystem = new SystemSrkEos(273.851993 +15, 1);//
        //  testSystem.addComponent("TEG", 0.1);
        //      testSystem.addComponent("ethane", 5.154639175);
        //   testSystem.addComponent("propane", 3.092783505);
        ///   testSystem.addComponent("i-butane", 2.06185567);
        //  testSystem.addComponent("n-butane", 1.030927835);
        //testSystem.addComponent("n-pentane", 1.030927835);
        //     testSystem.addPlusFraction("C7", 5.154639175, 187 / 1000.0, 0.82);
        //testSystem.addTBPfraction("C7", 5.154639175, 548 / 1000.0, 0.932);
        // testSystem.addComponent("MEG", 212.030927835);
        //       testSystem.setHeavyTBPfractionAsPlusFraction();
        // testSystem.getCharacterization().characterisePlusFraction();
        //     testSystem.addComponent("water", 1-0.95,"kg/sec");//37.030927835, "kg/sec");
        //        testSystem.addComponent("TEG", 0.95,"kg/sec");//63.030927835,"kg/sec");
        //   SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 149.55, 1070.3);//
/*
         testSystem.addComponent("nitrogen", 9.9197e-9);
         testSystem.addComponent("CO2", 2.20061e-4); 
         testSystem.addComponent("methane", 22224.74663e-5);
         testSystem.addComponent("ethane", 3.84589e-5);
         testSystem.addComponent("propane", 1.51513e-5);
         testSystem.addComponent("i-butane", 6.86424e-5);
         testSystem.addComponent("n-butane", 4.37911e-5);
         testSystem.addComponent("i-pentane", 4.10298e-6);
         testSystem.addComponent("n-pentane", 9.31651e-6);
         testSystem.addComponent("n-hexane", 2.81419e-6);
         testSystem.addComponent("benzene", 2.48549e-4);
         testSystem.addComponent("n-heptane", 9.70174e-7);
         testSystem.addComponent("toluene", 6.17609e-4);
         testSystem.addComponent("n-octane", 8.12785e-7);
         testSystem.addComponent("m-Xylene", 1.144061e-4);
         testSystem.addComponent("n-nonane", 1.02985e-7);
         */
        //  testSystem.addComponent("nitrogen", 50);
        //    testSystem.addComponent("methanol", 8.08E-02);
        //  testSystem.addComponent("n-hexane", 50);
        //    testSystem.addTBPfraction("C7", 50.0, 150.0 / 1000.0, 0.8);
        //    testSystem.addComponent("water", 99.99443014);
        //  testSystem.addComponent("n-heptane",1);
        //testSystem.addComponent("nC5-BEenzene",1);
        //  testSystem.addComponent("MEG", 120.005553344);
        //    testSystem.addComponent("TEG", 1.65126E-05);
        //  testSystem.addComponent("n-hexane", 1);
        //    testSystem.addComponent("nC10", 1);
        /*  testSystem.addComponent("water", 0.50494);
         testSystem.addComponent("MEG", 0.493618);
         testSystem.addComponent("TEG", 0.01);
          
         */
        //   testSystem.getCharacterization().getLumpingModel().
        //   testSystem.addComponent("water", 50.0);
        //   testSystem.addComponent("MEG", 1.0e-20);
        //   testSystem.addComponent("TEG", 1.0e-10);
        //SystemInterface testSystem = new SystemSrkEos(273.15 + 40, 1.0);//
        //  testSystem.addComponent("CO2", 0.140986);
        //  testSystem.addComponent("methane", 0.317768);
        //   testSystem.addComponent("ethane", 0.247939);
        //    testSystem.addComponent("propane", 0.29282);
        //     testSystem.addComponent("water",10.076582);
        //   testSystem.addComponent("water", 4.8529e-004);
        //   testSystem.addTBPfraction("C8", 1.0, 185.03 / 1000.0, 0.926664);
        //  testSystem.addComponent("water", 1);
        //Johan Sverderup fluuid
        //, , , , , , ,  , , ,, , , , , , , , , 0., 0., , 
//testSystem.addTBPfraction("C12", 5.0, 131.2 / 1000.0, 0.763);
        /*
         testSystem.addComponent("CO2", 9.82253e-004);
         testSystem.addComponent("nitrogen", 5.28604e-005);
         testSystem.addComponent("methane", 1.3823e-004);
         testSystem.addComponent("ethane", 6.74613e-005);
         testSystem.addComponent("propane",  5.56535e-006);
         testSystem.addComponent("i-butane", 4.26725e-003);
         testSystem.addComponent("n-butane", 3.95019e-006);
         testSystem.addComponent("i-pentane",6.18058e-004);
         testSystem.addComponent("n-pentane", 2.01858e-003);
         //testSystem.addComponent("default", 0.85);
         testSystem.addTBPfraction("C12", 5.0, 131.2 / 1000.0, 0.763);
         testSystem.addTBPfraction("C8", 3.81142e-011, 85.03 / 1000.0, 0.6664);
         testSystem.addTBPfraction("C9", 4.75748e-011, 93.06 / 1000.0, 0.7255);
         testSystem.addTBPfraction("C10", 3.71449e-011, 105.7 / 1000.0, 0.7531);
         testSystem.addTBPfraction("C11", 7.16123e-011, 119.6 / 1000.0, 0.7735);
         testSystem.addTBPfraction("C12",  2.12198e-009, 143.2 / 1000.0, 0.8071);
         testSystem.addTBPfraction("C13", 2.69467e-011, 181.0 / 1000.0, 0.8558);
         testSystem.addTBPfraction("C14", 3.93699e-014, 220.6 / 1000.0, 0.8738);
         testSystem.addTBPfraction("C15", 2.95329e-017, 268.4 / 1000.0, 0.8909);
         testSystem.addTBPfraction("C16", 1.41154e-020, 324.6 / 1000.0, 0.9169);
         testSystem.addTBPfraction("C17", 1.2563e-024, 395.7 / 1000.0, 0.9441);
         testSystem.addTBPfraction("C18", 3.1774e-030, 532.3 / 1000.0, 0.9742);
         testSystem.addTBPfraction("C19", 1.24591e-030, 678.2 / 1000.0, 1.003);
         testSystem.addTBPfraction("C20", 1.24591e-030, 906.7 / 1000.0, 1.037);

         testSystem.addComponent("ethanol", 1);
         testSystem.addComponent("TEG", 0.853125);
         testSystem.addComponent("water",0.138636);
         testSystem.addComponent("MEG", 4.11222e-009);
         
         */
        //  ((PhaseEosInterface)testSystem.getPhase(0)).getMixingRule().setBinaryInteractionParameter(0, 1, 0.08);
        /*
         //Åsgard fluid
         testSystem.addComponent("CO2", 2.797);
         testSystem.addComponent("nitrogen", 0.215);
         testSystem.addComponent("methane", 67.173);
         testSystem.addComponent("ethane", 6.886);
         testSystem.addComponent("propane", 2.89);
         testSystem.addComponent("i-butane", 0.571);
         testSystem.addComponent("n-butane", 1.15);
         testSystem.addComponent("i-pentane", 0.483);
         testSystem.addComponent("n-pentane", 0.582);
         //testSystem.addComponent("default", 0.85);
         // testSystem.addTBPfraction("C12", 5.0, 131.2 / 1000.0, 0.763);
         testSystem.addTBPfraction("C6", 1.0, 86.178 / 1000.0, 0.664);
         testSystem.addTBPfraction("C7", 1.0, 96.00 / 1000.0, 0.738);
         testSystem.addTBPfraction("C8", 1.0, 107.0 / 1000.0, 0.765);
         testSystem.addTBPfraction("C9", 1.0, 121 / 1000.0, 0.781);
         testSystem.addTBPfraction("C10", 1.0, 250.0 / 1000.0, 0.845);

         testSystem.addComponent("MEG", 1);
         testSystem.addComponent("TEG", 1);
         testSystem.addComponent("ethanol", 1);
         testSystem.addComponent("water", 1);
         */

 /*
         testSystem.addComponent("methane", 24);
         testSystem.addComponent("n-heptane", 76);
         testSystem.addComponent("methanol", 70);
         testSystem.addComponent("water", 100-70);
         * 
         * 
         */
//testSystem.addComponent("water", 1);
// testSystem.addComponent("TEG", 1);
          testSystem.createDatabase(true);
         testSystem.setMixingRule(2);
        //   testSystem.useVolumeCorrection(true);
        ////   testSystem.setMultiPhaseCheck(true);
        //Y testSystem = testSystem.readObject(30);
        //testSystem.setTemperature(273.15);
        //testSystem.setTotalFlowRate(20.0, "MSm^3/day");
        //    testSystem.saveObject(2201);
        //    testSystem.init(0);
        //    testSystem.init(1);
        //    testSystem.setTotalNumberOfMoles(100);
        //    testSystem.init(1);
        //testSystem.setPhysicalPropertyModel(0);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

        // testSystem.setNumberOfPhases(1);
        try {
            // testOps.freezingPointTemperatureFlash();
            //     testSystem.setTemperature(60);
            //    testSystem.setPressure(85);
            //    testSystem.init(0);
            //     testSystem.setTotalFlowRate(1.2,"kg/hr");
            for (int i = 0; i < 1; i++) {
                //   testOps.dewPointTemperatureFlash();
                //  testSystem.initPhysicalProperties();
            }
        } catch (Exception e) {

        }
        // testSystem.addComponent("ethane", -0.01);
        testOps.TPflash();
        /*
        testSystem.display();
        testSystem.setMolarComposition(new double[]{0.9, 0, 1, 0.0, 0});
        for (int i = 0; i < 1; i++) {
            testOps.TPflash();
            testSystem.initPhysicalProperties();
        }
        testSystem.display();
        testSystem.setMolarComposition(new double[]{0.9, 0, 1, 0.10, 1});
        for (int i = 0; i < 1; i++) {
            testOps.TPflash();
            testSystem.initPhysicalProperties();
        }
         */
        testSystem.display();
        System.out.println("desnity " + testSystem.getPhase(0).getDensity());
   ((neqsim.thermo.phase.PhaseEosInterface) testSystem.getPhase(0)).displayInteractionCoefficients("");

    }
}
