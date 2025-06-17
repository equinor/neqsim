package automatic.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import neqsim.util.unit.TemperatureUnit;

class TemperatureUnitConversionTest {
  @Test
  void testConversion() {
    TemperatureUnit tempC = new TemperatureUnit(0.0, "C");
    assertEquals(273.15, tempC.getValue("K"), 1e-6);
    assertEquals(32.0, tempC.getValue("F"), 1e-6);

    TemperatureUnit tempK = new TemperatureUnit(300.0, "K");
    assertEquals(26.85, tempK.getValue("C"), 1e-2);
  }

  @Test
  void testInvalidUnit() {
    TemperatureUnit temp = new TemperatureUnit(0.0, "C");
    assertThrows(IllegalArgumentException.class, () -> temp.getConversionFactor("X"));
  }
}
