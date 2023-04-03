package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class NewComponentTest extends neqsim.NeqSimTest {
  static SystemInterface thermoSystem = null;

  /**
   * @throws java.lang.Exception
   */
  @BeforeAll
  static void setUpBeforeClass() throws Exception {}

  @Test
  public void newComponentTest() {
    thermoSystem = new SystemSrkEos(298.0, 1.01325);
    thermoSystem.addComponent("ammonia", 1.0);
    thermoSystem.init(0);
    assertEquals(0.01703052, thermoSystem.getMolarMass("kg/mol"), 0.01);
    assertEquals(405.6, thermoSystem.getComponent(0).getTC(), 0.01);
  }
}
