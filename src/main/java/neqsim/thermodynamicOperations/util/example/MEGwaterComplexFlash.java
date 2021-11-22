package neqsim.thermodynamicOperations.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author ESOL
 */
public class MEGwaterComplexFlash {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(MEGwaterComplexFlash.class);

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 50, 1.0);
        testSystem.addComponent("methane", 0.01);
        // testSystem.addComponent("ethane", 0.10);
        // testSystem.addComponent("n-heptane", 0.5);
        // testSystem.addComponent("MEG", 0.139664804);
        testSystem.addComponent("TEG", 0.01);
        testSystem.addComponent("water", 1.0 - 0.01);

        testSystem.createDatabase(true);
        testSystem.setMixingRule(10);
        testSystem.init(0);
        // logger.info("fug "
        // +Math.log(testSystem.getPhase(1).getComponent("TEG").getFugasityCoefficient()));
        testSystem.setSolidPhaseCheck("water");
        // testSystem.setMultiPhaseCheck(true);
        double entdiff = 0;
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

        try {
            // testOps.TPflash();
            // testSystem.display();
            // testOps.freezingPointTemperatureFlash();
            testOps.calcSolidComlexTemperature("TEG", "water");
            testSystem.display();
        } catch (Exception e) {
            logger.error(e.toString(), e);
        }
        logger.info("temperature " + (testSystem.getTemperature() - 273.15));
        logger.info("activity water " + testSystem.getPhase(1).getActivityCoefficient(2));
        logger.info("activity TEG " + testSystem.getPhase(1).getActivityCoefficient(1));
    }
}
