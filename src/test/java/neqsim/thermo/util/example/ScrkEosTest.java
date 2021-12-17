package neqsim.thermo.util.example;

import org.junit.jupiter.api.BeforeAll;
import neqsim.thermo.system.SystemSrkSchwartzentruberEos;

/**
 *
 * @author ESOL
 */

public class ScrkEosTest extends ModelBaseTest {
    @BeforeAll
    public static void setUp() {
        thermoSystem = new SystemSrkSchwartzentruberEos(298.15, 1.01325);
        thermoSystem.addComponent("methanol", 1.0);
        thermoSystem.addComponent("water", 1.0);
        thermoSystem.createDatabase(true);
        thermoSystem.setMixingRule(1);
    }
}
