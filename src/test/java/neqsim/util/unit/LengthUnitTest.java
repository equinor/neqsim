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
    assertEquals(12.0, length.getValue(1.0, "ft", "in"), 1e-12);
  }

  @Test
  void testUnsupportedUnit() {
    LengthUnit length = new LengthUnit(1.0, "m");
    assertThrows(RuntimeException.class, () -> length.getValue("yard"));
  }
}
