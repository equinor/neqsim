package neqsim.thermo.util.example;


import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPA;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author ESOL
 */

@Disabled
public class ElectrolyteCPAEosTest extends ModelBaseTest {

    private static final long serialVersionUID = 1000;


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

    public static void tearDown() {

    }

    @Test
    public void testTPflash() {
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
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
