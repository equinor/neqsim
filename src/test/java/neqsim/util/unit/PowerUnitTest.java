package neqsim.util.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class PowerUnitTest extends neqsim.NeqSimTest {
  @Test
  void testSIValue() {
    assertEquals(1000.0, new PowerUnit(1.0, "kW").getSIvalue(), 1e-12);
    assertEquals(745.699872, new PowerUnit(1.0, "hp").getSIvalue(), 1e-12);
  }

  @Test
  void testAllConversionFactorsAndConvert() {
    assertEquals(1.0, new PowerUnit(1.0, "W").getSIvalue(), 1e-12);
    assertEquals(1.0e6, new PowerUnit(1.0, "MW").getSIvalue(), 1e-6);
    assertEquals(0.29307107, new PowerUnit(1.0, "BTU/hr").getSIvalue(), 1e-9);

    assertEquals(1000.0, PowerUnit.convert(1.0, "MW", "kW"), 1e-6);
    assertEquals(1.0, new PowerUnit(1000.0, "W").getValue("kW"), 1e-12);
    assertEquals(745.699872, new PowerUnit(1.0, "hp").getValue("W"), 1e-6);
  }

  @Test
  void testUnsupportedUnitThrows() {
    assertThrows(RuntimeException.class, () -> new PowerUnit(1.0, "erg/s").getSIvalue());
    assertThrows(RuntimeException.class, () -> new PowerUnit(1.0, "W").getValue("erg/s"));
  }
}
