package neqsim.thermo.util.example.longman;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class Problem15102009LNGfreezing {
    static Logger logger = LogManager.getLogger(Problem15102009LNGfreezing.class);

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemSrkEos(200, 11.44);
        testSystem.addComponent("methane", 100 - 33.4);
        // testSystem.addComponent("ethane", 0.0197);
        // testSystem.addComponent("propane", 0.03);
        // testSystem.addComponent("benzene",0.002);
        testSystem.addComponent("CO2", 33.4);
        // testSystem.addComponent("nitrogen", 1 - 0.452);

        // testSystem.addComponent("n-hexane", 0.01);

        // testSystem.addComponent("c-hexane", 0.0048);

        testSystem.setMultiPhaseCheck(true);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        testSystem.setSolidPhaseCheck("CO2");
        // testSystem.setSolidPhaseCheck("methane");
        testSystem.init(0);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        try {
            testOps.TPflash();
            // testSystem.display();
            // testOps.bubblePointPressureFlash(false);
            testOps.freezingPointTemperatureFlash();
            testSystem.display();
        } catch (Exception e) {
            logger.error("error", e);
        }
    }
}
