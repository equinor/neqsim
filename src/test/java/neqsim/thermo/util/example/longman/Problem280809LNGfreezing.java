package neqsim.thermo.util.example.longman;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemGERG2004Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class Problem280809LNGfreezing {
    static Logger logger = LogManager.getLogger(Problem280809LNGfreezing.class);

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemGERG2004Eos(170, 1);
        testSystem.addComponent("methane", 0.97);
        // testSystem.addComponent("ethane", 0.0197);
        // testSystem.addComponent("propane", 0.03);
        // testSystem.addComponent("benzene",0.002);
        testSystem.addComponent("CO2", 0.03);
        // testSystem.addComponent("nitrogen", 0.1);

        // testSystem.addComponent("n-hexane", 0.01);

        // testSystem.addComponent("c-hexane", 0.0048);

        testSystem.createDatabase(true);
        // testSystem.setMixingRule(2);
        // testSystem.setSolidPhaseCheck("benzene");
        // testSystem.setSolidPhaseCheck("CO2");
        testSystem.init(0);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        try {
            testOps.TPflash();
            testSystem.display();
            // testOps.bubblePointPressureFlash(false);
            // testOps.freezingPointTemperatureFlash();
            // testSystem.display();
        } catch (Exception e) {
            logger.error("error", e);
        }
    }
}
