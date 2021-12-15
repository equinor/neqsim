package neqsim.thermo.util.example;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
// import junit.framework.TestCase;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author ESOL
 */
public class SrkEoSTest {

    static SystemInterface thermoSystem = null;


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


    @Test
    public void testTPflash() {
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
        testOps.TPflash();
        assertEquals(thermoSystem.getNumberOfPhases(), 2);
    }

    @Disabled
    @Test
    public void testSaturateWIthWater() {
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
        testOps.saturateWithWater();
        assertTrue(thermoSystem.getPhase(0).hasComponent("water"));
    }

    @Test
    public void initPhysicalProperties() {
        thermoSystem.initPhysicalProperties();
        assertEquals(thermoSystem.getPhase(0).getPhysicalProperties().getDensity(),
                thermoSystem.getPhase(0).getPhysicalProperties().getDensity());
    }

    @Test
    public void testPHflash() {
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
        testOps.TPflash();
        thermoSystem.init(3);
        double enthalpy = thermoSystem.getEnthalpy();
        testOps.PHflash(enthalpy + 10.0);
        thermoSystem.init(3);

        double enthalpy2 = thermoSystem.getEnthalpy();

        assertEquals(Math.round(enthalpy + 10.0), Math.round(enthalpy2));
    }

    @Test
    public void testPSflash() {
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
        testOps.TPflash();
        thermoSystem.init(3);
        double entropy = thermoSystem.getEntropy();
        testOps.PSflash(entropy + 10.0);
        thermoSystem.init(3);

        double entropy2 = thermoSystem.getEntropy();

        assertEquals(Math.round(entropy + 10.0), Math.round(entropy2));
    }



}
