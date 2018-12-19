package neqsim.physicalProperties.util.examples;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.log4j.Logger;

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
public class TPflashWater {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(TPflashWater.class);

    /**
     * Creates new TPflash
     */
    public TPflashWater() {
    }

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 0.0, 100.0189);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

        testSystem.addComponent("methane", 0.3);
       // testSystem.addComponent("n-heptane", 0.000071);
        //testSystem.addComponent("water", 0.02, "kg/sec");
        testSystem.addComponent("TEG", 0.98);

        testSystem.createDatabase(true);
        testSystem.setMixingRule(10);
        testSystem.init(0);
        testSystem.init(1);
        testSystem.setMultiPhaseCheck(true);
        testSystem.initPhysicalProperties();

        try {
            testOps.TPflash();
          
        } catch (Exception e) {
            logger.error(e.toString());
        }
        testSystem.display();
        //    System.out.println("surftens 0-2 " + testSystem.getInterphaseProperties().getSurfaceTension(0,2));
    }
}
