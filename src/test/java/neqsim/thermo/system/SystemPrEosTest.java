package neqsim.thermo.system;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import neqsim.thermodynamicOperations.ThermodynamicOperations;


public class SystemPrEosTest {
    public static ThermodynamicOperations testOps;

    /**
     * <p>
     * setUp.
     * </p>
     */
    @BeforeAll
    public static void setUp() {
        SystemInterface testSystem = new SystemPrEos(260.0, 5.0);

        testOps = new ThermodynamicOperations(testSystem);
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
    }

    @Test
    public void TestcalcPTphaseEnvelope() {
        testOps.calcPTphaseEnvelope(true);
    }

    @Test
    public void testTPflash() {
        testOps.TPflash();
    }
}
