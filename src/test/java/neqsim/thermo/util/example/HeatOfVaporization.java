package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/*
 *
 * @author esol @version
 */
public class HeatOfVaporization {
    static Logger logger = LogManager.getLogger(HeatOfVaporization.class);

    public static void main(String[] args) {
        SystemInterface testSystem = new SystemSrkCPAstatoil(288.15000000, 0.001);//
        testSystem.addComponent("TEG", 1);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(10);
        testSystem.init(0);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        try {
            testOps.bubblePointPressureFlash(false);
            testSystem.display();
            double heatVap = testSystem.getHeatOfVaporization();
            logger.info("heat of vaporization " + heatVap + " J/mol");
            logger.info("heat of vaporization " + (heatVap / testSystem.getMolarMass()) + " J/kg");
        } catch (Exception e) {
            logger.error(e.toString());
        }
    }
}
