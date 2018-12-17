package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.log4j.Logger;

/*
 * PhaseEnvelope.java
 *
 * Created on 27. september 2001, 10:21
 */

/**
 *
 * @author  esol
 * @version
 */
public class TestGERGwater {

    private static final long serialVersionUID = 1000;
    static Logger logger = Logger.getLogger(TestGERGwater.class);

    /** Creates new PhaseEnvelope */
    public TestGERGwater() {
    }

    public static void main(String args[]) {
        //  SystemInterface testSystem = new SystemGERGwaterEos(273.15-20.0, 100.0);
        SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 - 20.0, 100.0);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testSystem.addComponent("methane", 80.6);
        testSystem.addComponent("ethane", 9.46);
        testSystem.addComponent("propane", 4.6);
        testSystem.addComponent("i-butane", 100.0 - 80.6 - 9.46 - 4.6 - 2.6 - 0.6);
        testSystem.addComponent("CO2", 2.6);
        testSystem.addComponent("nitrogen", 0.6);
        //     testSystem.addComponent("ethane", 0.08);
//        testSystem.addComponent("propane", 0.02);
        testSystem.addComponent("water", 178.3e-4);

        testSystem.createDatabase(true);
        // 1- orginal no interaction 2- classic w interaction
        // 3- Huron-Vidal 4- Wong-Sandler
        testSystem.setMixingRule(7);
        //testSystem.setMixingRule("HV", "UNIFAC_PSRK");
        testSystem.init(0);

        try {
            //      testOps.TPflash();
            //      testOps.dewPointTemperatureFlash();
            testOps.waterDewPointTemperatureFlash();
            testSystem.display();
        } catch (Exception e) {
            logger.error(e.toString());
        }

    }
}
