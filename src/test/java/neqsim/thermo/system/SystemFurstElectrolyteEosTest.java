package neqsim.thermo.system;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/**
 * <p>
 * ElectrolyteScrkEosTest class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public class SystemFurstElectrolyteEosTest extends neqsim.NeqSimTest{
    static SystemFurstElectrolyteEos thermoSystem;

    /**
     * <p>
     * setUp.
     * </p>
     */
    @BeforeAll
    public static void setUp() {
        thermoSystem = new SystemFurstElectrolyteEos(298.15, 10.01325);
        thermoSystem.addComponent("methane", 1.0);
        thermoSystem.addComponent("water", 1.0);
        thermoSystem.addComponent("Na+", 0.01);
        thermoSystem.addComponent("Cl-", 0.01);
        thermoSystem.setMixingRule(4);
    }

    /**
     * <p>
     * tearDown.
     * </p>
     */
    @AfterAll
    public static void tearDown() {}
}
