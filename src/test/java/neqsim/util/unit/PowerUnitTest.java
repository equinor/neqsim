package neqsim.util.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class PowerUnitTest extends neqsim.NeqSimTest {
  @Test
  void testSIValue() {
    assertEquals(1000.0, new PowerUnit(1.0, "kW").getSIvalue(), 1e-12);
    assertEquals(745.699872, new PowerUnit(1.0, "hp").getSIvalue(), 1e-12);
  }
}
