package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.logging.log4j.*;

public class LNGfreezingTestSolid1 {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(LNGfreezingTestSolid1.class);

    public static void main(String args[]) {
        // SystemInterface testSystem = new SystemUMRPRUMCEos(225.8488, 10.0);
        SystemInterface testSystem = new SystemSrkEos(245.0, 10.0);
        // testSystem.addComponent("nitrogen", 0.379);
        testSystem.addComponent("methane", 99.9);
        testSystem.addComponent("benzene", 0.1);
//        testSystem.addComponent("n-hexane", 2.0);
        // testSystem.addComponent("propane", 10);
        // testSystem.addComponent("benzene", 0.083);
        // testSystem.addComponent("ethane", 2.359);
        // testSystem.addComponent("propane", 3.1);
//        testSystem.addComponent("i-butane", 0.504);
        // testSystem.addComponent("n-butane", 0.85);
        // testSystem.addComponent("i-pentane", 0.323);
        // testSystem.addComponent("n-pentane", 0.231);
        // testSystem.addComponent("n-hexane", 0.173);

        // testSystem.addComponent("n-hexane", 0.01);

        // testSystem.addComponent("c-hexane", 0.0048);

        testSystem.createDatabase(true);
        // testSystem.setMixingRule("HV", "UNIFAC_UMRPRU");
        testSystem.setMixingRule(2);
        testSystem.setSolidPhaseCheck("benzene");
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

        testSystem.getPhase(3).getComponent("benzene").setHeatOfFusion(6000);
        try {
            // System.out.println("heat of fusion " +
            // testSystem.getPhase(3).getComponent("benzene").getHeatOfFusion());

            testOps.TPSolidflash();
            // System.out.println("heat of fusion " +
            // testSystem.getPhase(3).getComponent("benzene").getHeatOfFusion());
            // testOps.displayResult();
            // testOps.freezingPointTemperatureFlash();
            testSystem.display();
            // testOps.freezingPointTemperatureFlash();
            // testOps.TPflash();
        } catch (Exception e) {
            logger.error("error", e);
        }
    }
}
