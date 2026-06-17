package neqsim.util.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class TimeUnitTest extends neqsim.NeqSimTest {
  @Test
  void testSIValue() {
    assertEquals(3600.0, new TimeUnit(1.0, "hr").getSIvalue(), 1e-12);
    assertEquals(86400.0, new TimeUnit(1.0, "day").getSIvalue(), 1e-12);
  }

  @Test
  void testDirectConversions() {
    TimeUnit time = new TimeUnit(2.0, "hr");
    assertEquals(120.0, time.getValue("min"), 1e-12);
    assertEquals(7200.0, time.getValue("sec"), 1e-12);
    assertEquals(2.0, time.getValue(7200.0, "sec", "hr"), 1e-12);
  }

  @Test
  void testUnsupportedUnit() {
    TimeUnit time = new TimeUnit(1.0, "sec");
    assertThrows(RuntimeException.class, () -> time.getValue("week"));
  }
}
