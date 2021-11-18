package neqsim.thermo.util.example;

import neqsim.thermo.system.*;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.logging.log4j.*;

/*
 *
 * @author esol @version
 */
public class TPflash2 {
    static Logger logger = LogManager.getLogger(TPflash2.class);

    public static void main(String[] args) {
        SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 80.0, 1.01325);
        testSystem.addComponent("nitrogen", 8.71604938);
        // testSystem.addComponent("oxygen", 22.71604938);
        testSystem.addComponent("water", 110.234567901);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(10);
        // testSystem.setMultiPhaseCheck(true);
        SystemInterface testSystem2 = new SystemSrkCPAstatoil(273.15 + 80.0, 1.01325);
        testSystem2.addComponent("nitrogen", 8.71604938);
        // testSystem.addComponent("oxygen", 22.71604938);
        testSystem2.addComponent("MEG", 110.234567901);
        testSystem2.createDatabase(true);
        testSystem2.setMixingRule(10);
        testSystem.addFluid(testSystem2);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

        // testOps.TPflash();

        try {
            testOps.TPflash();
            // testOps.waterDewPointTemperatureMultiphaseFlash();
        } catch (Exception e) {
            logger.error("error", e);
        }
        // testSystem.init(0);
        // testSystem.init(1);

        testSystem.display();
    }
}
