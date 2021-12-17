package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author esol
 */
public class TestSurfaceTenison {
    static Logger logger = LogManager.getLogger(TestSurfaceTenison.class);

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemSrkEos(310.95, 20.00);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testSystem.addComponent("methane", 0.736);
        // testSystem.addComponent("CO2", 91.681);
        // testSystem.addComponent("etbhane", 0.251);
        // testSystem.addComponent("propane", 0.332);
        testSystem.addComponent("n-butane", 0.264);
        testSystem.addComponent("n-pentane", 0.344);
        // testSystem.addComponent("n-hexane", 0.272);
        // testSystem.addComponent("n-heptane", 4);
        // testSystem.addComponent("n-octane", 0.419);
        // testSystem.addComponent("nC10", 1.4);
        // testSystem.addComponent("nC14", 0.6);
        testSystem.useVolumeCorrection(false);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        try {
            testOps.TPflash();
        } catch (Exception e) {
            logger.error(e.toString());
        }
        testSystem.display();
        testSystem.display();
        testSystem.display();
    }
}
