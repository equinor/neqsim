package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author MLLU
 */
public class TVPexample {
    static Logger logger = LogManager.getLogger(TVPexample.class);

    public TVPexample() {};

    public static void main(String[] args) {
        SystemInterface testSystem = new SystemSrkEos(275.15 + 37.7778, 1.0);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testSystem.addComponent("methane", 0.1);
        testSystem.addComponent("ethane", 0.2);
        testSystem.addComponent("propane", 0.3);
        testSystem.addComponent("i-butane", 0.3);
        testSystem.addComponent("n-butane", 0.1);
        testSystem.addComponent("i-pentane", 0.1);
        testSystem.addComponent("n-pentane", 100.0);
        testSystem.addComponent("n-hexane", 100.0);
        testSystem.addComponent("n-heptane", 100.0);
        testSystem.addComponent("n-octane", 100.0);

        testSystem.createDatabase(true);
        // testSystem.setMixingRule(10);

        testOps.TPflash();
        testSystem.display();

        try {
            testOps.bubblePointPressureFlash(false);
        } catch (Exception e) {
            logger.error("Exception thrown in bubble point flash");
        }
        testSystem.display();

    }
}
