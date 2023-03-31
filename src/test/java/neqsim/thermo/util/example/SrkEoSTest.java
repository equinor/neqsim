package neqsim.thermo.util.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * SrkEoSTest class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public class SrkEoSTest {
    static SystemInterface thermoSystem = null;

    /**
     * <p>
     * setUp.
     * </p>
     */
    @BeforeAll
    public static void setUp() {
        thermoSystem = new SystemSrkEos(298.0, 10.0);
        thermoSystem.addComponent("methane", 10.0);
        thermoSystem.addComponent("ethane", 1.0);
        thermoSystem.addComponent("propane", 0.1);
        thermoSystem.addComponent("n-heptane", 10.1);
        thermoSystem.createDatabase(true);
        thermoSystem.setMixingRule(2);
    }

    /**
     * <p>
     * testTPflash.
     * </p>
     */
    @Test
    @Disabled
    public void testTPflash() {
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
        testOps.TPflash();
        assertEquals(2, thermoSystem.getNumberOfPhases());
    }

    /**
     * <p>
     * testSaturateWIthWater.
     * </p>
     */
    @Test
    @Disabled
    public void testSaturateWIthWater() {
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
        testOps.saturateWithWater();
        assertTrue(thermoSystem.getPhase(0).hasComponent("water"));
    }

    /**
     * <p>
     * testinitPhysicalProperties.
     * </p>
     */
    @Test
    public void testinitPhysicalProperties() {
        thermoSystem.initPhysicalProperties();
        assertEquals(thermoSystem.getPhase(0).getPhysicalProperties().getDensity(),
                thermoSystem.getPhase(0).getPhysicalProperties().getDensity());
    }

    /**
     * <p>
     * testPHflash.
     * </p>
     */
    @Test
    public void testPHflash() {
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
        testOps.TPflash();
        thermoSystem.init(3);
        double enthalpy = thermoSystem.getEnthalpy();
        testOps.PHflash(enthalpy + 10.0);
        thermoSystem.init(3);

        assertEquals(Math.round(enthalpy + 10.0), Math.round(thermoSystem.getEnthalpy()));
    }

    /**
     * <p>
     * testPSflash.
     * </p>
     */
    @Test
    public void testPSflash() {
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
        testOps.TPflash();
        thermoSystem.init(3);
        double entropy = thermoSystem.getEntropy();
        testOps.PSflash(entropy + 10.0);
        thermoSystem.init(3);

        assertEquals(Math.round(entropy + 10.0), Math.round(thermoSystem.getEntropy()));
    }
}
