package neqsim.thermo.system;

import org.junit.jupiter.api.BeforeAll;
import neqsim.thermo.ThermodynamicConstantsInterface;

/**
 * <p>
 * ScrkEosTest class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public class SystemSrkSchwartzentruberEosTest extends ModelBaseTest {
    /**
     * <p>
     * setUp.
     * </p>
     */
    @BeforeAll
    public static void setUp() {
      thermoSystem = new SystemSrkSchwartzentruberEos(298.15,
          ThermodynamicConstantsInterface.referencePressure);
        thermoSystem.addComponent("methanol", 1.0);
        thermoSystem.addComponent("water", 1.0);
        thermoSystem.createDatabase(true);
        thermoSystem.setMixingRule(1);
    }
}
