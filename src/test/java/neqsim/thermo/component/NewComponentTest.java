package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseSrkEos;
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
  public void createComponentTest() {
    PhaseSrkEos p = new PhaseSrkEos();
    RuntimeException thrown = Assertions.assertThrows(RuntimeException.class, () -> {
      p.addComponent("", 0, 0, 0);
    });
    Assertions.assertEquals(
        "neqsim.util.exception.InvalidInputException: ComponentSrk:createComponent - Input component_name can not be empty",
        thrown.getMessage());

    RuntimeException thrown_2 = Assertions.assertThrows(RuntimeException.class, () -> {
      p.addComponent(null, 0, 0, 0);
    });
    Assertions.assertEquals(
        "neqsim.util.exception.InvalidInputException: PhaseSrkEos:addcomponent - Input name can not be null",
        thrown_2.getMessage());
  }

  @Test
  public void newComponentTest() {
    thermoSystem = new SystemSrkEos(298.0, 1.01325);
    thermoSystem.addComponent("ammonia", 1.0);
    thermoSystem.init(0);
    assertEquals(0.01703052, thermoSystem.getMolarMass("kg/mol"), 0.01);
    assertEquals(405.4, thermoSystem.getComponent(0).getTC(), 0.01);
  }
}
