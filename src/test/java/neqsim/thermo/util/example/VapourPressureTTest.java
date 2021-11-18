
package neqsim.thermo.util.example;

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
public class VapourPressureTTest {
    static SystemInterface thermoSystem = null;

    @BeforeAll
    public static void setUp() {
        thermoSystem = new SystemSrkEos(128.0, 10.0);
        thermoSystem.addComponent("methane", 10.0);
        thermoSystem.createDatabase(true);
        thermoSystem.setMixingRule(2);
    }

    @Disabled
    @Test
    public void testDewBubblePointT() {
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
        double startTemp = thermoSystem.getTemperature();
        double bubblePointT = 0.0, dewPointT = 10.0;
        thermoSystem.setPressure(10.0);
        try {
            testOps.bubblePointTemperatureFlash();
            bubblePointT = thermoSystem.getTemperature();
            thermoSystem.setTemperature(startTemp);
            testOps.dewPointTemperatureFlash(false);
            dewPointT = thermoSystem.getTemperature();
        } catch (Exception e) {
        }

        assertTrue(Math.abs(bubblePointT - dewPointT) < 1e-2);
    }

    @Disabled
    @Test
    public void testSaturateWIthWater() {
        ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
        testOps.saturateWithWater();
        assertTrue(thermoSystem.getPhase(0).hasComponent("water"));
    }
}
