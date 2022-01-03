package neqsim.standards.util.example;

import neqsim.standards.gasQuality.Standard_ISO6976;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>Test_ISO6976 class.</p>
 *
 * @author esol
 * @since 2.2.3
 */
public class Test_ISO6976 {
        /**
         * <p>main.</p>
         *
         * @param args an array of {@link java.lang.String} objects
         */
        @SuppressWarnings("unused")
        public static void main(String args[]) {
                SystemInterface testSystem = new SystemSrkEos(273.15 - 150.0, 1.0);

                ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
                /*
                 * testSystem.addComponent("methane", 0.922393); testSystem.addComponent("ethane",
                 * 0.025358); testSystem.addComponent("propane", 0.01519);
                 * testSystem.addComponent("n-butane", 0.000523);
                 * testSystem.addComponent("i-butane", 0.001512);
                 * testSystem.addComponent("n-pentane", 0.002846);
                 * testSystem.addComponent("i-pentane", 0.002832);
                 * testSystem.addComponent("22-dim-C3", 0.001015);
                 * testSystem.addComponent("n-hexane", 0.002865);
                 * testSystem.addComponent("nitrogen", 0.01023); testSystem.addComponent("CO2",
                 * 0.015236);
                 * 
                 */

                /*
                 * 
                 * testSystem.addComponent("methane", 0.9247); testSystem.addComponent("ethane",
                 * 0.035); testSystem.addComponent("propane", 0.0098);
                 * testSystem.addComponent("n-butane", 0.0022); testSystem.addComponent("i-butane",
                 * 0.0034); testSystem.addComponent("n-pentane", 0.0006);
                 * testSystem.addComponent("nitrogen", 0.0175); testSystem.addComponent("CO2",
                 * 0.0068);
                 * 
                 */

                testSystem.addComponent("methane", 0.931819);
                testSystem.addComponent("ethane", 0.025618);
                testSystem.addComponent("nitrogen", 0.010335);
                testSystem.addComponent("CO2", 0.015391);
                // testSystem.addComponent("water", 0.016837);

                // testSystem.addComponent("water", 0.016837);

                /*
                 * testSystem.addComponent("n-hexane", 0.0); testSystem.addComponent("n-heptane",
                 * 0.0); testSystem.addComponent("n-octane", 0.0);
                 * testSystem.addComponent("n-nonane", 0.0); testSystem.addComponent("nC10", 0.0);
                 * 
                 * testSystem.addComponent("CO2", 0.68); testSystem.addComponent("H2S", 0.0);
                 * testSystem.addComponent("water", 0.0); testSystem.addComponent("oxygen", 0.0);
                 * testSystem.addComponent("carbonmonoxide", 0.0);
                 * testSystem.addComponent("nitrogen", 1.75);
                 */
                // testSystem.addComponent("MEG", 1.75);
                testSystem.createDatabase(true);
                testSystem.setMixingRule(2);

                testSystem.init(0);
                Standard_ISO6976 standard = new Standard_ISO6976(testSystem, 0, 15.55, "volume");
                standard.setReferenceState("real");
                standard.setReferenceType("volume");
                standard.calculate();
                System.out.println("Comp Factor " + standard.getValue("CompressionFactor"));
                System.out.println("Superior Calorific Value "
                                + standard.getValue("SuperiorCalorificValue"));
                System.out.println("Inferior Calorific Value "
                                + standard.getValue("InferiorCalorificValue"));
                System.out.println("GCV " + standard.getValue("GCV"));
                System.out.println("Superior Wobbe " + standard.getValue("SuperiorWobbeIndex"));
                System.out.println("Inferior Wobbe " + standard.getValue("InferiorWobbeIndex"));
                System.out.println("relative density " + standard.getValue("RelativeDensity"));
                System.out.println("compression factor " + standard.getValue("CompressionFactor"));
                System.out.println("molar mass " + standard.getValue("MolarMass"));
                standard.display("test");
                /*
                 * StandardInterface standardUK = new UKspecifications_ICF_SI(testSystem);
                 * standardUK.calculate(); System.out.println("ICF " +
                 * standardUK.getValue("IncompleteCombustionFactor", ""));
                 * 
                 * System.out.println("HID " +
                 * testSystem.getPhase(0).getComponent("methane").getHID(273.15 - 150.0));
                 * System.out.println("Hres " +
                 * testSystem.getPhase(0).getComponent("methane").getHresTP(273.15 - 150.0));
                 */
        }
}
