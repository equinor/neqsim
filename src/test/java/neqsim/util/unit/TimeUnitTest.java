package neqsim.util.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimeUnitTest extends neqsim.NeqSimTest {
  @Test
  @DisplayName("getSIUnit returns 's' for seconds")
  void testGetSIUnit() {
    assertEquals("s", new TimeUnit(1.0, "sec").getSIUnit());
  }

  @Test
  @DisplayName("getSIvalue converts various units to seconds")
  void testSIValue() {
    assertEquals(1.0, new TimeUnit(1.0, "s").getSIvalue(), 1e-12);
    assertEquals(1.0, new TimeUnit(1.0, "sec").getSIvalue(), 1e-12);
    assertEquals(1.0, new TimeUnit(1.0, "second").getSIvalue(), 1e-12);
    assertEquals(60.0, new TimeUnit(1.0, "min").getSIvalue(), 1e-12);
    assertEquals(60.0, new TimeUnit(1.0, "minute").getSIvalue(), 1e-12);
    assertEquals(3600.0, new TimeUnit(1.0, "h").getSIvalue(), 1e-12);
    assertEquals(3600.0, new TimeUnit(1.0, "hr").getSIvalue(), 1e-12);
    assertEquals(3600.0, new TimeUnit(1.0, "hour").getSIvalue(), 1e-12);
    assertEquals(86400.0, new TimeUnit(1.0, "d").getSIvalue(), 1e-12);
    assertEquals(86400.0, new TimeUnit(1.0, "day").getSIvalue(), 1e-12);
  }

  @Test
  @DisplayName("getValue converts from one unit to another")
  void testDirectConversions() {
    TimeUnit time = new TimeUnit(2.0, "hr");
    assertEquals(120.0, time.getValue("min"), 1e-12);
    assertEquals(7200.0, time.getValue("sec"), 1e-12);
    assertEquals(2.0, time.getValue("h"), 1e-12);
    assertEquals(1.0 / 12.0, time.getValue("day"), 1e-12);
  }

  @Test
  @DisplayName("static convert method works for various conversions")
  void testStaticConvert() {
    assertEquals(2.0, TimeUnit.convert(7200.0, "sec", "hr"), 1e-12);
    assertEquals(2.0, TimeUnit.convert(120.0, "min", "hr"), 1e-12);
    assertEquals(1440.0, TimeUnit.convert(1.0, "day", "min"), 1e-12);
    assertEquals(86400.0, TimeUnit.convert(1.0, "d", "s"), 1e-12);
    assertEquals(0.5, TimeUnit.convert(30.0, "min", "hr"), 1e-12);
  }

  @Test
  @DisplayName("all unit name aliases are supported")
  void testUnitAliases() {
    assertEquals(1.0, new TimeUnit(1.0, "s").getSIvalue(), 1e-12);
    assertEquals(1.0, new TimeUnit(1.0, "sec").getSIvalue(), 1e-12);
    assertEquals(1.0, new TimeUnit(1.0, "second").getSIvalue(), 1e-12);
    assertEquals(60.0, new TimeUnit(1.0, "min").getSIvalue(), 1e-12);
    assertEquals(60.0, new TimeUnit(1.0, "minute").getSIvalue(), 1e-12);
    assertEquals(3600.0, new TimeUnit(1.0, "h").getSIvalue(), 1e-12);
    assertEquals(3600.0, new TimeUnit(1.0, "hr").getSIvalue(), 1e-12);
    assertEquals(3600.0, new TimeUnit(1.0, "hour").getSIvalue(), 1e-12);
    assertEquals(86400.0, new TimeUnit(1.0, "d").getSIvalue(), 1e-12);
    assertEquals(86400.0, new TimeUnit(1.0, "day").getSIvalue(), 1e-12);
  }

  @Test
  @DisplayName("fractional and zero values are handled correctly")
  void testFractionalAndZeroValues() {
    assertEquals(1800.0, new TimeUnit(0.5, "hr").getSIvalue(), 1e-12);
    assertEquals(0.0, new TimeUnit(0.0, "hr").getSIvalue(), 1e-12);
    assertEquals(30.0, new TimeUnit(0.5, "min").getSIvalue(), 1e-12);
    assertEquals(0.5, TimeUnit.convert(0.5, "sec", "sec"), 1e-12);
  }

  @Test
  @DisplayName("conversion between all supported unit pairs")
  void testAllConversionPairs() {
    // seconds to everything
    assertEquals(1.0 / 60.0, TimeUnit.convert(1.0, "s", "min"), 1e-12);
    assertEquals(1.0 / 3600.0, TimeUnit.convert(1.0, "s", "hr"), 1e-12);
    assertEquals(1.0 / 86400.0, TimeUnit.convert(1.0, "s", "day"), 1e-12);

    // minutes to everything
    assertEquals(60.0, TimeUnit.convert(1.0, "min", "s"), 1e-12);
    assertEquals(1.0 / 60.0, TimeUnit.convert(1.0, "min", "hr"), 1e-12);
    assertEquals(1.0 / 1440.0, TimeUnit.convert(1.0, "min", "day"), 1e-12);

    // hours to everything
    assertEquals(3600.0, TimeUnit.convert(1.0, "hr", "s"), 1e-12);
    assertEquals(60.0, TimeUnit.convert(1.0, "hr", "min"), 1e-12);
    assertEquals(1.0 / 24.0, TimeUnit.convert(1.0, "hr", "day"), 1e-12);

    // days to everything
    assertEquals(86400.0, TimeUnit.convert(1.0, "day", "s"), 1e-12);
    assertEquals(1440.0, TimeUnit.convert(1.0, "day", "min"), 1e-12);
    assertEquals(24.0, TimeUnit.convert(1.0, "day", "hr"), 1e-12);
  }

  @Test
  @DisplayName("unsupported unit throws RuntimeException")
  void testUnsupportedUnit() {
    TimeUnit time = new TimeUnit(1.0, "sec");
    assertThrows(RuntimeException.class, () -> time.getValue("week"));
    assertThrows(RuntimeException.class, () -> time.getValue("month"));
    assertThrows(RuntimeException.class, () -> time.getValue("year"));
  }

  @Test
  @DisplayName("large values are converted accurately")
  void testLargeValues() {
    assertEquals(365.0 * 24.0, TimeUnit.convert(365.0, "day", "hr"), 1e-10);
    assertEquals(10.0 * 60.0, TimeUnit.convert(10.0, "min", "sec"), 1e-12);
    assertEquals(1000.0 * 3600.0, TimeUnit.convert(1000.0, "hr", "sec"), 1e-8);
  }

  @Test
  @DisplayName("identity conversions (same unit)")
  void testIdentityConversions() {
    assertEquals(42.0, TimeUnit.convert(42.0, "sec", "s"), 1e-12);
    assertEquals(42.0, TimeUnit.convert(42.0, "min", "minute"), 1e-12);
    assertEquals(42.0, TimeUnit.convert(42.0, "hour", "hr"), 1e-12);
    assertEquals(42.0, TimeUnit.convert(42.0, "d", "day"), 1e-12);
  }
}
