

package neqsim.thermodynamicOperations.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author ESOL
 */
public class OLGApropGeneratorPH {
    static Logger logger = LogManager.getLogger(OLGApropGeneratorPH.class);

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemSrkEos(383.15, 1.0);
        // testSystem.addComponent("ethane", 10.0);
        testSystem.addComponent("water", 10.0);
        // testSystem.addComponent("n-heptane", 1.0);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        testSystem.setMultiPhaseCheck(true);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        try {
            testOps.dewPointTemperatureFlash();
            // testOps.TPflash();
            testSystem.display();
            double maxEnthalpy = testSystem.getEnthalpy();
            logger.info(" maxEnthalpy " + maxEnthalpy);
            testOps.bubblePointTemperatureFlash();
            testSystem.display();
            double minEnthalpy = testSystem.getEnthalpy();

            // testOps.PHflash(maxEnthalpy + 49560, 0);
            String fileName = "c:/Appl/OLGAneqsim.tab";
            testOps.OLGApropTablePH(minEnthalpy, maxEnthalpy, 41, testSystem.getPressure(), 2, 41,
                    fileName, 0);
            testOps.displayResult();
        } catch (Exception e) {
            testSystem.display();
            logger.error(e.toString());
        }
    }
}
