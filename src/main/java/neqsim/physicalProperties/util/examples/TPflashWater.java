package neqsim.physicalProperties.util.examples;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/*
 *
 * @author esol
 * 
 * @version
 */
public class TPflashWater {

    static Logger logger = LogManager.getLogger(TPflashWater.class);

    public static void main(String args[]) {
        SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 + 40.0, 100.01325);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

        // testSystem.addComponent("methane", 0.3);
        // testSystem.addComponent("n-heptane", 0.000071);
        // testSystem.addComponent("water", 0.02, "kg/sec");
        // testSystem.addComponent("water", 0.97);
        testSystem.addComponent("TEG", 0.103);

        // testSystem.createDatabase(true);
        testSystem.setMixingRule(10);
        // testSystem.setMultiPhaseCheck(true);

        try {
            testOps.TPflash();
        } catch (Exception e) {
            logger.error(e.toString());
        }
        testSystem.initPhysicalProperties();
        // System.out.println("viscosity " + testSystem.getViscosity());
        System.out.println("viscosity " + testSystem.getPhase("aqueous").getViscosity());
        testSystem.display();
        // System.out.println("surftens 0-2 " +
        // testSystem.getInterphaseProperties().getSurfaceTension(0,2));
    }
}
