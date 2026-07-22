package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * CPAEosTest class.
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public class SystemSrkCPAstatoilTest extends neqsim.NeqSimTest {
  static SystemInterface thermoSystem = null;

  /**
   * setUp.
   */
  @BeforeAll
  public static void setUp() {
    thermoSystem = new SystemSrkCPAstatoil(298.0, 10.0);
    thermoSystem.addComponent("methane", 11.0);
    thermoSystem.addComponent("CO2", 1.0);
    thermoSystem.addComponent("MEG", 1.0);
    thermoSystem.addComponent("water", 11.0);
    thermoSystem.createDatabase(true);
    thermoSystem.setMixingRule(10);
  }

  /**
   * testTPflash.
   */
  @Test
  public void testTPflash() {
    ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
    testOps.TPflash();
    assertEquals(2, thermoSystem.getNumberOfPhases());
  }

  /**
   * testinitPhysicalProperties.
   */
  @Test
  public void testinitPhysicalProperties() {
    thermoSystem.initPhysicalProperties();
    assertEquals(thermoSystem.getPhase(0).getPhysicalProperties().getDensity(),
        thermoSystem.getPhase(0).getPhysicalProperties().getDensity());
  }

  /**
   * testPHflash.
   */
  @Test
  public void testPHflash() {
    ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
    testOps.TPflash();
    thermoSystem.init(3);
    double enthalpy = thermoSystem.getEnthalpy();
    testOps.PHflash(enthalpy + 10.0);
    thermoSystem.init(3);

    assertEquals(Math.round(enthalpy + 10.0), Math.round(thermoSystem.getEnthalpy()));
  }

  /**
   * testPSflash.
   */
  @Test
  public void testPSflash() {
    ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
    testOps.TPflash();
    thermoSystem.init(3);
    double entropy = thermoSystem.getEntropy();
    testOps.PSflash(entropy + 10.0);
    thermoSystem.init(3);

    assertEquals(Math.round(entropy + 10.0), Math.round(thermoSystem.getEntropy()));
  }
}
