package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.logging.log4j.*;

public class LNGFlash {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(LNGFlash.class);

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemSrkEos(20, 5.0);

        testSystem.addComponent("methane", 110.02);
        // testSystem.addComponent("n-pentane", 1e-10);
        testSystem.addComponent("n-hexane", 1.00001);
        // testSystem.addTBPfraction("C7", 0.1, 86.0/1000.0, 0.7);

        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        testSystem.initProperties();
        // testSystem.setSolidPhaseCheck("n-hexane");
        // testSystem.addSolidComplexPhase("wax");
        testSystem.display();
        testSystem.init(0);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        try {
            testOps.bubblePointTemperatureFlash();
            testSystem.display();

            testOps.dewPointTemperatureFlash();
            testSystem.display();

            // testOps.TPflash();
            // testSystem.display();
            // testSystem.setMolarComposition(new double[] {0.1,0.1,0.1});
            // testOps.TPflash();
            // testOps.dewPointTemperatureFlash();
            // testSystem.display();
            // testOps.freezingPointTemperatureFlash();
            // testOps.calcWAT();
        } catch (Exception e) {
            logger.error("error", e);
        }
        /*
         * testSystem.reset(); testSystem.addComponent("methane", 1.0);
         * testSystem.addComponent("n-hexane", 0.000000009); testOps = new
         * ThermodynamicOperations(testSystem); try { testOps.TPflash(); // testSystem.display(); //
         * testOps.freezingPointTemperatureFlash(); // testOps.calcWAT(); testSystem.display(); }
         * catch (Exception e) { logger.error("error",e); } }
         * 
         * 
         */
    }
}
