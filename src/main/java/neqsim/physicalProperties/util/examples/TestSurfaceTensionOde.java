package neqsim.physicalProperties.util.examples;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.logging.log4j.*;

/**
 *
 * @author oberg
 */
public class TestSurfaceTensionOde {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(TestSurfaceTensionOde.class);

    public static void main(String args[]) {

        double yscale;
        SystemInterface testSystem = new SystemSrkEos(273.15, 1.0);

        testSystem.addComponent("methane", 5.0);
        // testSystem.addComponent("TEG", 5.0);
        testSystem.addComponent("n-heptane", 2.01);
        // testSystem.addComponent("n-heptane", 112.0);
        // testSystem.addComponent("water", 10.0);
        // testSystem.addComponent("water", 50.0);
        // testSystem.addComponent("MEG", 50.0);

        // testSystem.addComponent("water", 100);

        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        // testSystem.setMultiPhaseCheck(true);
        testSystem.init(0);
        testSystem.getInterphaseProperties().setInterfacialTensionModel(1);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        try {
            testOps.TPflash();
        } catch (Exception e) {
            logger.error(e.toString());
        }
        testSystem.display();
    }
}
