package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemGEWilson;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/*
 *
 * @author esol
 * 
 * @version
 */
/**
 * <p>TestGEHenry class.</p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestGEHenry {
    static Logger logger = LogManager.getLogger(TestGEHenry.class);

    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String args[]) {
        //
        SystemInterface testSystem = new SystemGEWilson(273.15 + 55.0, 1.301325);
        // SystemInterface testSystem = new SystemNRTL(273.15 + 55.0,1.301325);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testSystem.addComponent("methane", 10.0);
        // testSystem.addComponent("methanol", 1.0);
        testSystem.addComponent("water", 10.0);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        testSystem.init(0);

        try {
            testOps.TPflash();
            // testOps.bubblePointPressureFlash(false);//(false);
        } catch (Exception e) {
            logger.error(e.toString(), e);
        }
        testSystem.display();
    }
}
