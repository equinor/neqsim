/*
 * TestISO1982.java
 *
 * Created on 13. juni 2004, 23:49
 */

package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.logging.log4j.*;

/**
 *
 * @author ESOL
 */
public class TestISO1982 {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(TestISO1982.class);

    /** Creates a new instance of TestISO1982 */
    public TestISO1982() {
    }

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemSrkEos(290.15, 30.00);

        testSystem.addComponent("methane", 50);
        testSystem.addComponent("ethane", 50);
        testSystem.addComponent("propane", 50);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testOps.TPflash();

        logger.info("ISO calc: " + testSystem.getStandard("ISO1982").getValue("Energy", "KJ/Sm3"));
    }
}
