package neqsim.util.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicConstantsInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

class TemperatureUnitTest extends neqsim.NeqSimTest {
  /**
   * testSetPressure
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

    assertEquals(ThermodynamicConstantsInterface.referencePressure, fluid.getPressure("bara"), 1e-4);
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
   * Verify direct temperature conversions between common units.
   */
  @Test
  public void testDirectConversions() {
    // 100 C to other units
    TemperatureUnit celsius = new TemperatureUnit(100.0, "C");
    assertEquals(373.15, celsius.getValue("K"), 1e-6);
    assertEquals(212.0, celsius.getValue("F"), 1e-6);
    assertEquals(671.67, celsius.getValue("R"), 1e-2);

    // 373.15 K to Celsius and Fahrenheit
    TemperatureUnit kelvin = new TemperatureUnit(373.15, "K");
    assertEquals(100.0, kelvin.getValue("C"), 1e-6);
    assertEquals(212.0, kelvin.getValue("F"), 1e-6);

    // 212 F to Kelvin and Celsius
    TemperatureUnit fahrenheit = new TemperatureUnit(212.0, "F");
    assertEquals(373.15, fahrenheit.getValue("K"), 1e-6);
    assertEquals(100.0, fahrenheit.getValue("C"), 1e-6);

    // 671.67 R to Celsius and Kelvin
    TemperatureUnit rankine = new TemperatureUnit(671.67, "R");
    assertEquals(373.15, rankine.getValue("K"), 1e-2);
    assertEquals(100.0, rankine.getValue("C"), 1e-2);
  }

  /**
   * Verify that requesting conversions with unsupported units throws an exception.
   */
  @Test
  public void testUnsupportedUnit() {
    TemperatureUnit unit = new TemperatureUnit(0.0, "K");
    assertThrows(IllegalArgumentException.class, () -> new TemperatureUnit(0.0, "X"));
    assertThrows(IllegalArgumentException.class, () -> unit.getValue("X"));
    assertThrows(IllegalArgumentException.class, () -> new TemperatureUnit(0.0, "X"));
  }

  @Test
  void constructorPreservesKelvinValueForEverySupportedInputUnit() {
    String[] units = {"K", "C", "F", "R"};
    double[] boilingWater = {373.15, 100.0, 212.0, 671.67};
    for (int i = 0; i < units.length; i++) {
      TemperatureUnit temperature = new TemperatureUnit(boilingWater[i], units[i]);
      assertEquals(373.15, temperature.getSIvalue(), 1.0e-10, units[i]);
      assertEquals(100.0, temperature.getValue("C"), 1.0e-10, units[i]);
      assertEquals(373.15, temperature.getSIvalue(), 1.0e-10, "Readback must not mutate the stored Kelvin value");
    }
    assertThrows(IllegalArgumentException.class, () -> new TemperatureUnit(10.0, null));
    assertThrows(IllegalArgumentException.class, () -> new TemperatureUnit(10.0, " "));
    assertThrows(IllegalArgumentException.class, () -> new TemperatureUnit(10.0, "X"));
    assertThrows(IllegalArgumentException.class, () -> new TemperatureUnit(10.0, "K").getValue("X"));
  }
}
