package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

class SystemThermoTest extends neqsim.NeqSimTest {
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

  /**
   * <p>
   * test setPressure
   * </p>
   */
  @Test
  @DisplayName("test setPressure")
  public void testTPflash2() {
    assertEquals(10.0, testSystem.getPressure("bara"));
    testSystem.setPressure(110000.0, "Pa");
    assertEquals(1.1, testSystem.getPressure());
  }

  /**
   * <p>
   * testAddFluids
   * </p>
   */
  @Test
  @DisplayName("test addFluids with pseudo component")
  public void testAddFluids() {

    neqsim.thermo.system.SystemPrEos fluid1 = new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
    fluid1.addComponent("methane", 1.0);
    fluid1.addTBPfraction("C7", 1.0, 0.09, 0.81);
    fluid1.setMixingRule("classic");

    neqsim.thermo.system.SystemPrEos fluid2 = new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
    fluid2.addComponent("methane", 1.0);
    fluid2.addTBPfraction("C7", 1.0, 0.09, 0.81);
    fluid2.setMixingRule("classic");

    fluid1.addFluid(fluid2);

    assertEquals(2.0, fluid1.getComponent(0).getNumberOfmoles());
    assertEquals(2.0, fluid1.getComponent(0).getNumberOfmoles());

    assertEquals(2.0, fluid1.getComponent("methane").getNumberOfmoles());
    assertEquals(2.0, fluid1.getComponent("C7_PC").getNumberOfmoles());
  }
}
