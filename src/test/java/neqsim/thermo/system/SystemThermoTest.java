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

    neqsim.thermo.system.SystemPrEos fluid2 = new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
    fluid2.addComponent("methane", 1.0);
    fluid2.addTBPfraction("C7", 1.0, 0.09, 0.81);

    fluid1.addFluid(fluid2);

    assertEquals(2.0, fluid1.getComponent(0).getNumberOfmoles());
    assertEquals(2.0, fluid1.getComponent(1).getNumberOfmoles());

    assertEquals(2.0, fluid1.getComponent("methane").getNumberOfmoles());
    assertEquals(2.0, fluid1.getComponent("C7_PC").getNumberOfmoles());

    neqsim.thermo.system.SystemPrEos fluid3 = new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
    fluid3.addComponent("nitrogen", 1.0);
    fluid3.addTBPfraction("C8", 1.0, 0.092, 0.82);

    fluid1.addFluid(fluid3);

    assertEquals(2.0, fluid1.getComponent("methane").getNumberOfmoles());
    assertEquals(1.0, fluid1.getComponent("nitrogen").getNumberOfmoles());
    assertEquals(1.0, fluid1.getComponent("C8_PC").getNumberOfmoles());
  }

  /**
   * <p>
   * testSetPressure
   * </p>
   */
  @Test
  public void testSetPressure() {
    neqsim.thermo.system.SystemPrEos fluid = new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
    fluid.addComponent("nitrogen", 1.0);
    fluid.setPressure(0.0, "barg");

    assertEquals(1.01325, fluid.getPressure("bara"), 1e-4);
    assertEquals(0.0, fluid.getPressure("barg"), 1e-4);


  }
}
