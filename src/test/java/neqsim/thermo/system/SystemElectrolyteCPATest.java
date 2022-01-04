package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * ElectrolyteCPAEosTest class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */


public class ElectrolyteCPAEosTest {
    static SystemElectrolyteCPA thermoSystem;

    /**
     * <p>
     * setUp.
     * </p>
     */
    @BeforeAll
    public static void setUp() {
        thermoSystem = new SystemElectrolyteCPA(298.15, 1.01325);
        thermoSystem.addComponent("water", 1.0);
        thermoSystem.addComponent("methane", 0.1);
        thermoSystem.addComponent("Na+", 0.001);
        thermoSystem.addComponent("Cl-", 0.001);
        thermoSystem.setMixingRule(4);
    }

    /**
     * <p>
     * tearDown.
     * </p>
     */
    @AfterAll
    public static void tearDown() {}

    /**
     * <p>
     * testTPflash.
     * </p>
     */
    @Test
    public void testTPflash() {
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
        thermoSystem.initPhysicalProperties();

        thermoSystem.init();
        testOps.TPflash();
        assertEquals(thermoSystem.getNumberOfPhases(), 2);
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
}
