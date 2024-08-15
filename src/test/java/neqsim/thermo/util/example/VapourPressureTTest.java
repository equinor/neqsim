package neqsim.thermo.util.example;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * VapourPressureTTest class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public class VapourPressureTTest {
  static SystemInterface thermoSystem = null;

  /**
   * <p>
   * setUp.
   * </p>
   */
  @BeforeAll
  public static void setUp() {
    thermoSystem = new SystemSrkCPAstatoil(313.0, 1.0);
    thermoSystem.addComponent("propane", 10.0);
    thermoSystem.setMixingRule(10);
  }

  /**
   * <p>
   * testDewBubblePointT.
   * </p>
   */
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
    } catch (Exception ex) {
    }

    assertTrue(Math.abs(bubblePointT - dewPointT) < 1e-2);
  }
}
