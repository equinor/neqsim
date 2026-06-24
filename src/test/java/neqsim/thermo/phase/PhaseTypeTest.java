package neqsim.thermo.phase;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PhaseTypeTest {
  private static final Logger logger = LogManager.getLogger(PhaseTypeTest.class);

  @SuppressWarnings("deprecation")
  @Test
  void testValues() {
    for (PhaseType pt : PhaseType.values()) {
      // logger.info("Phase type: " + pt);

      Assertions.assertTrue(pt.getValue() >= 0);
      Assertions.assertTrue(pt.getDesc().length() > 0);
    }
  }
}
