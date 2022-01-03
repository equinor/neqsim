package neqsim.thermo.util.example;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import neqsim.thermo.system.SystemFurstElectrolyteEos;

/**
 * <p>
 * ElectrolyteScrkEosTest class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
@Disabled
public class ElectrolyteScrkEosTest extends ModelBaseTest {
    /**
     * <p>
     * setUp.
     * </p>
     */
    @BeforeAll
    public static void setUp() {
        thermoSystem = new SystemFurstElectrolyteEos(298.15, 1.01325);
        thermoSystem.addComponent("Na+", 0.01);
        thermoSystem.addComponent("Cl-", 0.01);
        thermoSystem.addComponent("water", 1.0);
        thermoSystem.createDatabase(true);
        thermoSystem.setMixingRule(1);
    }

    /**
     * <p>
     * tearDown.
     * </p>
     */
    @AfterAll
    public static void tearDown() {}
}
