package neqsim.thermodynamicOperations.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author esol
 */
public class CriticalPointFlash {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(CriticalPointFlash.class);

    public static void main(String[] args) {
        SystemInterface testSystem = new SystemSrkEos(300, 80.01325);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        // testSystem.addComponent("water", 0.9);
        testSystem.addComponent("methane", 0.1);
        testSystem.addComponent("propane", 0.1);
        // testSystem.addComponent("i-butane", 0.1);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        testSystem.init(0);
        try {
            testOps.calcCricondenBar();
            // testOps.criticalPointFlash();
            // testOps.calcPTphaseEnvelope(true);
            // testOps.displayResult();
        } catch (Exception e) {
            logger.error(e.toString());
        }
        testSystem.display();
    }
}
