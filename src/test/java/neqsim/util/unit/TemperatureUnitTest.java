package neqsim.util.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TemperatureUnitTest extends neqsim.NeqSimTest {
  /**
   * <p>
   * testSetPressure
   * </p>
   */
  @Test
  public void testSetTemperature() {
    neqsim.thermo.system.SystemPrEos fluid = new neqsim.thermo.system.SystemPrEos(298.0, 10.0);
    fluid.addComponent("nitrogen", 10.0);
    fluid.addComponent("nC10", 10.0);
    fluid.setPressure(0.0, "barg");

    ThermodynamicOperations testOps = new ThermodynamicOperations(fluid);
    testOps.TPflash();
    fluid.initProperties();

    assertEquals(ThermodynamicConstantsInterface.referencePressure, fluid.getPressure("bara"),
        1e-4);
    assertEquals(24.850000000000, fluid.getTemperature("C"), 1e-4);
    assertEquals(76.7300000, fluid.getTemperature("F"), 1e-4);
    assertEquals(536.4, fluid.getTemperature("R"), 1e-4);

    fluid.setTemperature(11.0, "F");
    testOps.TPflash();

    assertEquals(470.67, fluid.getTemperature("R"), 1e-4);
    assertEquals(-11.6666666666, fluid.getTemperature("C"), 1e-4);
    assertEquals(261.483333333, fluid.getTemperature("K"), 1e-4);

    fluid.setTemperature(527.67, "R");
    testOps.TPflash();

    assertEquals(68.0, fluid.getTemperature("F"), 1e-4);
    assertEquals(20.0, fluid.getTemperature("C"), 1e-4);
    assertEquals(293.15, fluid.getTemperature("K"), 1e-4);

    fluid.setTemperature(25.25, "C");
    testOps.TPflash();

    assertEquals(77.4499999999, fluid.getTemperature("F"), 1e-4);
    assertEquals(537.12, fluid.getTemperature("R"), 1e-4);
    assertEquals(298.4, fluid.getTemperature("K"), 1e-4);
  }

  /**
   * Verify that requesting conversions with unsupported units throws an exception.
   */
  @Test
  public void testUnsupportedUnit() {
    TemperatureUnit unit = new TemperatureUnit(0.0, "test");
    assertThrows(IllegalArgumentException.class, () -> unit.getValue(0.0, "X", "K"));
    assertThrows(IllegalArgumentException.class, () -> unit.getValue(0.0, "K", "X"));
  }
}

