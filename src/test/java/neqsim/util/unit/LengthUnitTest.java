package neqsim.util.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class LengthUnitTest extends neqsim.NeqSimTest {
  @Test
  void testSIValue() {
    assertEquals(1.0, new LengthUnit(100.0, "cm").getSIvalue(), 1e-12);
    assertEquals(0.3048, new LengthUnit(1.0, "ft").getSIvalue(), 1e-12);
  }

  @Test
  void testDirectConversions() {
    LengthUnit length = new LengthUnit(1.0, "m");
    assertEquals(1000.0, length.getValue("mm"), 1e-12);
    assertEquals(3.280839895, length.getValue("ft"), 1e-9);
    assertEquals(12.0, LengthUnit.convert(1.0, "ft", "in"), 1e-12);
  }

  @Test
  void testUnsupportedUnit() {
    LengthUnit length = new LengthUnit(1.0, "m");
    assertThrows(RuntimeException.class, () -> length.getValue("yard"));
  }

  @Test
  void testUnitAliasesAndConvert() {
    assertEquals(1.0, new LengthUnit(1.0, "meter").getSIvalue(), 1e-12);
    assertEquals(1.0, new LengthUnit(1.0, "metre").getSIvalue(), 1e-12);
    assertEquals(1000.0, new LengthUnit(1.0, "km").getSIvalue(), 1e-12);
    assertEquals(0.0254, new LengthUnit(1.0, "inch").getSIvalue(), 1e-12);
    assertEquals(0.3048, new LengthUnit(1.0, "feet").getSIvalue(), 1e-12);
    assertEquals(1.0e-3, new LengthUnit(1.0, "mm").getSIvalue(), 1e-15);

    assertEquals(100.0, LengthUnit.convert(1.0, "m", "cm"), 1e-12);
    assertEquals(0.001, LengthUnit.convert(1.0, "m", "km"), 1e-12);
    assertEquals(39.37007874, new LengthUnit(1.0, "m").getValue("in"), 1e-6);
  }

  @Test
  void testUnsupportedInputUnit() {
    assertThrows(RuntimeException.class, () -> new LengthUnit(1.0, "yard").getSIvalue());
  }
}
