package neqsim.thermo.util.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
// import junit.framework.TestCase;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>SrkOilCharacterizationTest class.</p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public class SrkOilCharacterizationTest {
    static SystemInterface thermoSystem = null;

    /**
     * <p>setUp.</p>
     */
    @BeforeAll
    public static void setUp() {
        thermoSystem = new SystemSrkEos(298.0, 10.0);
        thermoSystem.addComponent("methane", 90.0);
        thermoSystem.addComponent("ethane", 10.0);
        thermoSystem.addComponent("propane", 4.0);
        thermoSystem.addComponent("i-butane", 4.0);
        thermoSystem.addComponent("n-butane", 4.0);
        thermoSystem.addTBPfraction("C7", 5.0, 93.30 / 1000.0, 0.73);
        thermoSystem.addTBPfraction("C8", 2.0, 106.60 / 1000.0, 0.7533);
        thermoSystem.addTBPfraction("C9", 1.0, 119.60 / 1000.0, 0.7653);
        thermoSystem.addPlusFraction("C10", 5.62, 281.0 / 1000.0, 0.882888);
        thermoSystem.getCharacterization().characterisePlusFraction();
        thermoSystem.addComponent("water", 1.0);
        thermoSystem.createDatabase(true);
        thermoSystem.setMixingRule(2);
    }

    /**
     * <p>testTPflash.</p>
     */
    @Test
    public void testTPflash() {
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
        testOps.TPflash();
        assertEquals(thermoSystem.getNumberOfPhases(), 2);
    }

    /**
     * <p>initPhysicalProperties.</p>
     */
    @Test
    public void initPhysicalProperties() {
        thermoSystem.initPhysicalProperties();
        assertEquals(thermoSystem.getPhase(0).getPhysicalProperties().getDensity(),
                thermoSystem.getPhase(0).getPhysicalProperties().getDensity());
    }

    /**
     * <p>testPHflash.</p>
     */
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

    /**
     * <p>testPSflash.</p>
     */
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
