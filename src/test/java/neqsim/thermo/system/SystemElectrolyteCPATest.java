package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

@Disabled
public class SystemElectrolyteCPATest extends ModelBaseTest {
    @BeforeAll
    public static void setUp() {
        thermoSystem = new SystemElectrolyteCPA(298.15, 1.01325);
        thermoSystem.addComponent("methane", 0.1);
        thermoSystem.addComponent("Na+", 0.001);
        thermoSystem.addComponent("Cl-", 0.001);
        thermoSystem.addComponent("water", 1.0);
        thermoSystem.createDatabase(true);
        thermoSystem.setMixingRule(1);
    }

    @AfterAll
    public static void tearDown() {}

    @Test
    public void testTPflash() {
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
        thermoSystem.initPhysicalProperties();

        thermoSystem.init();
        testOps.TPflash();
        assertEquals(thermoSystem.getNumberOfPhases(), 2);
    }

    @Test
    public void initPhysicalProperties() {
        thermoSystem.initPhysicalProperties();
        assertEquals(thermoSystem.getPhase(0).getPhysicalProperties().getDensity(),
                thermoSystem.getPhase(0).getPhysicalProperties().getDensity());
    }
}
