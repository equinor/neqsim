package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPA;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.logging.log4j.*;

/*
 * TPflash.java
 *
 * Created on 27. september 2001, 09:43
 */

/*
 *
 * @author  esol
 * @version
 */
public class TPflashMethanolWaterPropane {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(TPflashMethanolWaterPropane.class);

    /**
     * Creates new TPflash
     */
    public TPflashMethanolWaterPropane() {
    }

    public static void main(String args[]) {

        SystemInterface testSystem = new SystemSrkCPA(300, 10.01325);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
   /*     testSystem.addComponent("methane", 150.0e-2);
        testSystem.addComponent("propane", 150.0e-3);
        testSystem.addComponent("methanol", 0.5);
        testSystem.addComponent("water", 0.5);

        testSystem.createDatabase(true);
        testSystem.setMixingRule(10);
        testSystem.setMultiPhaseCheck(true);
*/
        testSystem = testSystem.readObject(100);
        testOps = new ThermodynamicOperations(testSystem);
        testSystem.init(0);
        try {
            testOps.TPflash();
            // testOps.bubblePointPressureFlash(false);
            testSystem.display();
        } catch (Exception e) {
            logger.error(e.toString());
        }
        // testSystem.saveFluid(3019);
    }
}
