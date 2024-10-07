package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class AmmoniaTest extends neqsim.NeqSimTest {
  static SystemInterface thermoSystem = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeAll
  static void setUpBeforeClass() throws Exception {
    thermoSystem = new SystemSrkEos(298.0, ThermodynamicConstantsInterface.referencePressure);
    thermoSystem.addComponent("ammonia", 1.0);
    thermoSystem.init(0);
  }

  @Test
  public void bublePointTemperatureTest() {
    ThermodynamicOperations testOps = new ThermodynamicOperations(thermoSystem);
    try {
      testOps.bubblePointTemperatureFlash();
    } catch (Exception e) {
      e.printStackTrace();
    }
    assertEquals(-33.039831, thermoSystem.getTemperature("C"), 0.01);
  }

  @Test
  public void molarMassTest() {
    assertEquals(0.017, thermoSystem.getMolarMass("kg/mol"), 0.01);
  }
}
