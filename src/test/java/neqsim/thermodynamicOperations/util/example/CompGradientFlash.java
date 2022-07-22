package neqsim.thermodynamicOperations.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * CompGradientFlash class.
 * </p>
 *
 * @author esol
 * @version $Id: $Id
 * @since 2.2.3
 */
public class CompGradientFlash {
    static Logger logger = LogManager.getLogger(CompGradientFlash.class);

    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String args[]) {
      SystemInterface testSystem = new SystemSrkEos(273.15 + 0, 80.0); // 30.01325);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testSystem.addComponent("methane", 11.0);
        // testSystem.addComponent("ethane", 4.0);
        testSystem.addComponent("n-heptane", 0.03);
        // testSystem.addComponent("n-octane", 0.0001);

        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        testSystem.init(0);
        testSystem.init(3);
        logger.info("enthalpy " + testSystem.getPhase(1).getEnthalpy());

        SystemInterface newSystem = null;
        try {
            testOps.dewPointTemperatureFlash();
            testSystem.display();
            double dewTemp = testSystem.getTemperature();
            testSystem.setTemperature(dewTemp + 10.1);
            testSystem.init(0);
            newSystem = testOps.TPgradientFlash(0.0001, dewTemp).phaseToSystem(0);
            newSystem.init(0);
            ThermodynamicOperations testOps2 = new ThermodynamicOperations(newSystem);
            testOps2.dewPointTemperatureFlash();
            newSystem.display();

            // testSystem.display();
        } catch (Exception e) {
            logger.error(e.toString());
        }
    }
}
