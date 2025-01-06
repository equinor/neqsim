package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class SystemThermoNameTagTest {
  static neqsim.thermo.system.SystemInterface testSystem = null;

  /**
   * <p>
   * setUp.
   * </p>
   */
  @BeforeAll
  public static void setUp() {
    testSystem = new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
    testSystem.addComponent("nitrogen", 0.01);
    testSystem.addComponent("CO2", 0.01);
    testSystem.addComponent("methane", 0.68);
    testSystem.setMixingRule("classic");
    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initProperties();
  }

  @Test
  public void SetNameTag() {
    String[] a = testSystem.getCompNames();
    String[] b = testSystem.getComponentNames();
    assertArrayEquals(a, b);

    SystemInterface tmpSystem = testSystem.clone();
    tmpSystem.setFluidName(null);
    String[] c = tmpSystem.getCompNames();
    assertArrayEquals(a, c);

    String prefix = "test";
    tmpSystem.setComponentNameTag(prefix);
    String[] c2 = tmpSystem.getCompNames();
    for (String str : c2) {
      assertTrue(str.startsWith(prefix));
    }
  }
}
