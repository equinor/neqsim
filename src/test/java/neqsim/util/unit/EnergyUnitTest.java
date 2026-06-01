package neqsim.util.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class EnergyUnitTest extends neqsim.NeqSimTest {
  @Test
  void testSIValue() {
    assertEquals(1000.0, new EnergyUnit(1.0, "kJ").getSIvalue(), 1e-12);
    assertEquals(3.6e6, new EnergyUnit(1.0, "kWh").getSIvalue(), 1e-6);
  }
}
