package neqsim.thermo.phase;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PhaseTypeTest {
  @Test
  void testValues() {
    for (PhaseType pt : PhaseType.values()) {
      // System.out.println("Phase type: " + pt);

      Assertions.assertTrue(pt.getValue() >= 0);
      Assertions.assertTrue(pt.getDesc().length() > 0);
    }
  }
}
