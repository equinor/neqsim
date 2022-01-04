package neqsim.thermo.util.example;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/*
 * PhaseEnvelope.java
 *
 * Created on 27. september 2001, 10:21
 */

/**
 * <p>DewPointCurve class.</p>
 *
 * @author esol
 * @since 2.2.3
 * @version $Id: $Id
 */
public class DewPointCurve {
    static Logger logger = LogManager.getLogger(DewPointCurve.class);

    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String args[]) {
        SystemInterface testSystem = new SystemPrEos(260.0, 5.0);
        // testSystem = new SystemCSPsrkEos(290,50.6);
        // SystemInterface testSystem = new SystemSrkEos(195.9488, 47.0);
        // testSystem = new SystemPrEos(290,50.6);

        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        // testSystem.addComponent("nitrogen", 14.0);
        // testSystem.addComponent("methane", 93.505);
        testSystem.addComponent("methane", 0.3371);
        testSystem.addComponent("ethane", 0.3745);
        testSystem.addComponent("propane", 0.1);
        testSystem.addComponent("n-butane", 0.7153);
        // testSystem2.addComponent("propane", 1.008);
        // testSystem.addComponent("i-butane", 1.050);
        testSystem.addComponent("n-butane", 10.465);
        /*
         * testSystem.addComponent("n-pentane", 2653);
         *
         * testSystem.addComponent("n-hexane", 514.2); testSystem.addComponent("benzene", 61.03);
         * testSystem.addComponent("toluene", 24.63); testSystem.addComponent("c-hexane", 45.23);
         * testSystem.addComponent("n-heptane", 93.83); testSystem.addComponent("n-octane", 12.17);
         * testSystem.addComponent("n-nonane", 0.03); testSystem.addComponent("nC10", 0.01);
         */
        // testSystem.addComponent("CO2", 1.0);
        // testSystem.addComponent("water", 200.0e-4);
        // testSystem.addComponent("i-butane", 0.6013);
        testSystem.addComponent("n-butane", 1.018);
        // testSystem.addComponent("n-hexane", 0.1018);
        // testSystem.addComponent("n-heptane", 0.1757);

        testSystem.createDatabase(true);
        // 1- orginal no interaction 2- classic w interaction
        // 3- Huron-Vidal 4- Wong-Sandler
        testSystem.setMixingRule(2);
        // testSystem.setMixingRule("HV", "UNIFAC_PSRK");
        testSystem.init(0);

        try {
            testOps.calcPTphaseEnvelope(true);
            testOps.displayResult();
            // testOps.TPflash();
            // testOps.dewPointTemperatureFlash();
            // testSystem.display();
            // System.out.println("condensation rate: " +
            // testOps.dewPointTemperatureCondensationRate() * 1e6 + " mg/K/Sm^3");
            // System.out.println("condensation rate: " +
            // testOps.dewPointTemperatureCondensationRate() * 1e6 * 1.0 /
            // testSystem.getPressure() * testSystem.getPhase(0).getZ() *
            // testSystem.getTemperature() / 288.15 + " mg/K/Sm^3");
        } catch (Exception e) {
            logger.error(e.toString());
        }

        // testSystem.dewPointCondensationRate()
        /*
         * System.out.println("temp " + (testSystem.getTemperature() - 273.15)); for (int i = 0; i <
         * testSystem.getPhase(0).getNumberOfComponents(); i++) {
         * System.out.println("unsymetric activity coeff " +
         * testSystem.getPhase(1).getComponent(i).getName() + " " +
         * testSystem.getPhase(1).getActivityCoefficientUnSymetric(i)); } for (int i = 0; i <
         * testSystem.getPhase(0).getNumberOfComponents(); i++) {
         * System.out.println("symetric activity coeff " +
         * testSystem.getPhase(1).getComponent(i).getName() + " " +
         * testSystem.getPhase(1).getActivityCoefficientSymetric(i)); }
         * System.out.println("activity coeff " + testSystem.getPhase(1).getComponent(1).getName() +
         * " " + testSystem.getPhase(1).getActivityCoefficient(1, 0));
         *
         */

    }
}
