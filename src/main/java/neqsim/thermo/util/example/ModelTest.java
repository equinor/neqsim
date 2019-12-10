package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.log4j.Logger;

/*
 * TPflash.java
 *
 * Created on 27. september 2001, 09:43
 */

/**
 *
 * @author esol
 * @version
 */
public class ModelTest {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(ModelTest.class);

    /**
     * Creates new TPflash
     */
    public ModelTest() {
    }

    public static void main(String args[]) {
        //SystemInterface testSystem = new SystemFurstElectrolyteEos(280.15,10.00);

       //  SystemInterface testSystem = new SystemSrkEos(298.15, 10.01325);
        SystemInterface testSystem = new SystemSrkCPAstatoil(273.14 + 92, 42.0);
        // SystemInterface testSystem = new SystemElectrolyteCPAstatoil(273.14 + 12, 61.0);
        //       SystemInterface testSystem = new SystemFurstElectrolyteEos(273.14 + 12, 61.0);
         // SystemInterface testSystem = new SystemUMRPRUMCEos(300.0, 10.0);
        // SystemInterface testSystem = new SystemSrkEos(298.15, 1.01325);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

        testSystem.addComponent("methane", 100);
         testSystem.addComponent("n-heptane", 1);
       // testSystem.addComponent("water", 100);
        //   testSystem.addComponent("methane", 1);
        //      testSystem.addComponent("ethane", 1);
        //  testSystem.addComponent("TEG", 9);
        //    testSystem.addComponent("MEG", 3.1);
        //  testSystem.addComponent("n-octane", 3.1);
        //    testSystem.addComponent("Na+", 0.1);
        //    testSystem.addComponent("Cl-", 0.1);
        //   testSystem.addComponent("MEG", 2.1);
        //  testSystem.addComponent("methanol", 5.3);
        ///    testSystem.addComponent("MEG", 5.3);
          //   testSystem.addComponent("MEG", 10.0);
         //  testSystem.addTBPfraction("C8", 10.1, 90.0 / 1000.0, 0.8);
            testSystem.addComponent("MEG", 10.5);
        testSystem.createDatabase(true);
        //testSystem.useVolumeCorrection(true);
           testSystem.setMixingRule(2);
        // testSystem.setMixingRule("HV", "NRTL");
        //testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");
        //testSystem.setMixingRule(9);
        testSystem.init(0);
        testOps.TPflash();
        testSystem.init(3);

        neqsim.thermo.ThermodynamicModelTest testModel = new neqsim.thermo.ThermodynamicModelTest(testSystem);
        testModel.runTest();
        testSystem.display();

        testSystem.init(3);
        double cp = testSystem.getPhase(1).getCp();

        testSystem.setTemperature(testSystem.getTemperature() + 0.001);
        testSystem.init(3);
        double ent1 = testSystem.getPhase(1).getEnthalpy();

        testSystem.setTemperature(testSystem.getTemperature() - 0.002);
        testSystem.init(3);
        double ent2 = testSystem.getPhase(1).getEnthalpy();
        testSystem.saveFluid(3217);

        double numCp = (ent1 - ent2) / 0.002;

        logger.info("Cp " + cp + " numCp " + numCp);

    }
}
