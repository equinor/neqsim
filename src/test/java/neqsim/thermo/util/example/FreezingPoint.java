package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>FreezingPoint class.</p>
 *
 * @author esol
 * @since 2.2.3
 * @version $Id: $Id
 */
public class FreezingPoint {
    static Logger logger = LogManager.getLogger(FreezingPoint.class);

    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        // SystemInterface testSystem = new SystemSrkSchwartzentruberEos(260.15,19.00);
        SystemInterface testSystem = new SystemSrkCPAstatoil(273.15 - 1, 1.01325);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);

        SystemInterface testSystem2 = new SystemSrkCPAstatoil(273.15 + 100, 1.01325);
        ThermodynamicOperations testOps2 = new ThermodynamicOperations(testSystem2);

        testSystem.addComponent("methane", 0.00882);
        // testSystem.addComponent("ethane",0.0836);
        // testSystem.addComponent("propane",0.0176);
        // testSystem.addComponent("i-butane",1.0-0.882-0.0836-0.0176);
        // testSystem.addComponent("n-butane",0.00576);
        // testSystem.addComponent("n-heptane",10.0);
        testSystem.addComponent("methanol", 40, "kg/min");
        testSystem.addComponent("water", 60, "kg/min");

        testSystem2.addComponent("methanol", 40, "kg/min");
        testSystem2.addComponent("water", 60, "kg/min");

        testSystem.createDatabase(true);
        // testSystem.setSolidPhaseCheck(true);
        testSystem.setHydrateCheck(true);
        // testSystem.setMultiPhaseCheck(true);
        testSystem.setMixingRule(7);
        testSystem.init(0);
        testSystem.init(2);

        testSystem2.createDatabase(true);
        testSystem2.setMixingRule(7);
        testSystem2.init(0);
        testSystem2.init(1);

        logger.info("activity coefficient water in teg "
                + testSystem.getPhase(1).getActivityCoefficient(1));
        try {
            // testOps.freezingPointTemperatureFlash();
            // testOps.waterDewPointTemperatureFlash();
            // testOps.dewPointTemperatureFlash();
            // testOps.bubblePointPressureFlash(false);
            // testOps.TPflash();
            testOps.hydrateFormationTemperature(0);
            testSystem.display();
            // testOps.dewPointTemperatureFlash();
            testSystem2.init(0);
            testOps2.bubblePointTemperatureFlash();
            testSystem2.display();
        } catch (Exception e) {
            logger.error(e.toString(), e);
        }
        logger.info("wt% methanol " + 100 * testSystem.getPhase(1).getComponent("methanol").getx()
                * testSystem.getPhase(1).getComponent("methanol").getMolarMass()
                / (testSystem.getPhase(1).getComponent("methanol").getx()
                        * testSystem.getPhase(1).getComponent("methanol").getMolarMass()
                        + testSystem.getPhase(1).getComponent("water").getx()
                                * testSystem.getPhase(1).getComponent("water").getMolarMass()));
        logger.info(
                "mol% methanol " + 100 * testSystem.getPhase(1).getComponent("methanol").getx());
    }
}
