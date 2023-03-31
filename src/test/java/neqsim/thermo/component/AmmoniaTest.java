package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class AmmoniaTest extends neqsim.NeqSimTest {
  static SystemInterface thermoSystem = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeAll
  static void setUpBeforeClass() throws Exception {
    thermoSystem = new SystemSrkEos(298.0, 1.01325);
    thermoSystem.addComponent("ammonia", 1.0);
  }

  @Test
  public void bublePointTemperatureTest() {
    ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
    try {
      testOps.bubblePointTemperatureFlash();
    } catch (Exception e) {
      e.printStackTrace();
    }
    assertEquals(-33.03983, thermoSystem.getTemperature("C"), 0.01);
  }
}
