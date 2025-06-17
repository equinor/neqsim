package automatic.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import neqsim.util.unit.PressureUnit;
import neqsim.thermo.ThermodynamicConstantsInterface;

class PressureUnitConversionTest {
  @Test
  void testBasicConversion() {
    PressureUnit bar = new PressureUnit(1.0, "bara");
    assertEquals(100.0, bar.getValue("kPa"), 1e-6);

    PressureUnit barg = new PressureUnit(1.0, "barg");
    double expectedBara = 1.0 + ThermodynamicConstantsInterface.referencePressure;
    assertEquals(expectedBara, barg.getValue("bara"), 1e-6);
  }

  @Test
  void testInvalidUnit() {
    PressureUnit unit = new PressureUnit(1.0, "bara");
    assertThrows(RuntimeException.class, () -> unit.getConversionFactor("unknown"));
  }
}
