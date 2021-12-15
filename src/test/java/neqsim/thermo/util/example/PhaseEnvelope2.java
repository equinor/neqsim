/**
 * 
 */
package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * @author evensolbraa
 *
 */
public class PhaseEnvelope2 {
    static Logger logger = LogManager.getLogger(PhaseEnvelope2.class);

    /**
     * @param args
     */
    public static void main(String[] args) {
        SystemInterface testSystem = new SystemSrkEos(280.0, 1.00);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

        testSystem.addComponent("methane", 90.0);
        testSystem.addComponent("propane", 3.0);
        testSystem.addComponent("i-butane", 1.8);
        testSystem.addComponent("n-butane", 1.433);
        testSystem.addComponent("n-hexane", 1.433);
        testSystem.setMixingRule(2);
        try {
            testOps.calcPTphaseEnvelope();
            testOps.displayResult();
        } catch (Exception e) {
            logger.error("error", e);
        }
    }
}
