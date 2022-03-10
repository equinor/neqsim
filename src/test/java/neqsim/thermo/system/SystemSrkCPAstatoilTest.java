package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * CPAEosTest class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public class SystemSrkCPAstatoilTest {
    static SystemInterface thermoSystem = null;

    /**
     * <p>
     * setUp.
     * </p>
     */
    @BeforeAll
    public static void setUp() {
        thermoSystem = new SystemSrkCPAstatoil(298.0, 10.0);
        thermoSystem.addComponent("methane", 11.0);
        thermoSystem.addComponent("CO2", 1.0);
        thermoSystem.addComponent("MEG", 1.0);
        thermoSystem.addComponent("water", 11.0);
        thermoSystem.createDatabase(true);
        thermoSystem.setMixingRule(10);
    }

    /**
     * <p>
     * testTPflash.
     * </p>
     */
    @Test
    public void testTPflash() {
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
        testOps.TPflash();
        assertEquals(2, thermoSystem.getNumberOfPhases());
    }

    /**
     * <p>
     * initPhysicalProperties.
     * </p>
     */
    @Test
    public void initPhysicalProperties() {
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
