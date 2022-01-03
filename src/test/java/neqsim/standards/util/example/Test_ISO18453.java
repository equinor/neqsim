package neqsim.standards.util.example;

import neqsim.standards.StandardInterface;
import neqsim.standards.gasQuality.Draft_ISO18453;
import neqsim.thermo.system.SystemGERGwaterEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>Test_ISO18453 class.</p>
 *
 * @author esol
 * @since 2.2.3
 */
public class Test_ISO18453 {
        /**
         * <p>main.</p>
         *
         * @param args an array of {@link java.lang.String} objects
         */
        @SuppressWarnings("unused")
        public static void main(String args[]) {
                SystemInterface testSystem = new SystemGERGwaterEos(273.15 - 5.0, 20.0);

                ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
                testSystem.addComponent("methane", 0.9);
                testSystem.addComponent("water", 0.0000051);

                testSystem.createDatabase(true);
                testSystem.setMixingRule(8);

                testSystem.init(0);

                StandardInterface standard = new Draft_ISO18453(testSystem);
                standard.setSalesContract("Base");
                standard.calculate();
                System.out.println("Draft_ISO18453: dew temp "
                                + standard.getValue("dewPointTemperature") + " "
                                + standard.getUnit("dewPointTemperature"));

                testSystem.setStandard("Draft_ISO18453");
                testSystem.getStandard().setSalesContract("Base");
                testSystem.getStandard().calculate();

                System.out.println("Draft_ISO18453: dew temp "
                                + testSystem.getStandard().getValue("dewPointTemperature") + " "
                                + testSystem.getStandard().getUnit("dewPointTemperature"));

                System.out.println("Draft_ISO18453: pressure "
                                + testSystem.getStandard().getValue("pressure") + " "
                                + testSystem.getStandard().getUnit("pressureUnit"));

                System.out.println("is onSpec ? " + testSystem.getStandard().isOnSpec());
        }
}
