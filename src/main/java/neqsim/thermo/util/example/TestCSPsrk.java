package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemCSPsrkEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.logging.log4j.*;

/*
 * TPflash.java
 *
 * Created on 27. september 2001, 09:43
 */

/*
 *
 * @author esol
 * 
 * @version
 */
public class TestCSPsrk {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(TestCSPsrk.class);

    /** Creates new TPflash */
    public TestCSPsrk() {}

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemCSPsrkEos(158, 5.662);
        // SystemInterface testSystem = new SystemSrkEos(110.0, 1.262);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

        testSystem.addComponent("nitrogen", 1.17);
        testSystem.addComponent("methane", 94.14);
        testSystem.addComponent("ethane", 5.33);
        testSystem.addComponent("propane", 1.1);
        // testSystem.addComponent("n-butane", 0.41);
        // testSystem.addComponent("n-pentane", 0.6);
        // testSystem.addComponent("n-hexane", 0.6);
        // testSystem.addComponent("n-heptane", 0.24);
        // testSystem.addComponent("n-octane", 0.14);
        // testSystem.addComponent("CO2", 1.06);

        // testSystem.setTemperature(120);
        // testSystem.setPressure(4.43);
        // testSystem.addComponent("nitrogen", 4.25);
        // testSystem.addComponent("methane", 81.3);
        // testSystem.addComponent("ethane", 4.75);
        // testSystem.addComponent("propane", 4.87);
        // testSystem.addComponent("i-butane", 2.41);
        // testSystem.addComponent("n-butane", 2.42);

        // testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        try {
            // testOps.TPflash();
            testOps.bubblePointPressureFlash();
        } catch (Exception e) {
            logger.error(e.toString());
        }
        testSystem.display();
        logger.info(testSystem.getTemperature() - 273.15);
    }
}
