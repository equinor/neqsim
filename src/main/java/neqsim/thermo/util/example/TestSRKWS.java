package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPsrkEos;
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
public class TestSRKWS {
    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(TestSRKWS.class);

    /** Creates new TPflash */
    public TestSRKWS() {}

    public static void main(String args[]) {
        //

        // SystemInterface testSystem = new SystemSrkEos(245.8, 50.0);
        SystemInterface testSystem = new SystemPsrkEos(245.8, 50.0);
        // SystemInterface testSystem = new SystemCSPsrkEos(245.8, 70.0);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

        testSystem.addComponent("methane", 84.29);
        testSystem.addComponent("ethane", 10.09);
        testSystem.addComponent("propane", 4.0);
        testSystem.addComponent("i-butane", 0.59);
        testSystem.addComponent("n-butane", 1.02);

        testSystem.createDatabase(true);
        // testSystem.setMixingRule(2);
        // testSystem.setMixingRule("HV","UNIFAC_PSRK");
        testSystem.setMixingRule("WS", "UNIFAC_PSRK");
        testSystem.init(0);
        testSystem.init(3);

        try {
            testOps.dewPointTemperatureFlash();// (false);
            // testOps.bubblePointTemperatureFlash();
            // testOps.calcPTphaseEnvelope(0.0005, 0.0001); testOps.displayResult();
        } catch (Exception e) {
            logger.error(e.toString());
        }
        testSystem.display();
        logger.info(testSystem.getTemperature() - 273.15);
    }
}
