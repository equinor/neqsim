package neqsim.process.safety.overpressure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PipelinePressureProtectionCalculator}.
 */
public class PipelinePressureProtectionCalculatorTest {
  /**
   * A segment rated above the source pressure should be FULLY_RATED.
   */
  @Test
  void testFullyRated() {
    PipelinePressureProtectionCalculator calc = new PipelinePressureProtectionCalculator();
    calc.setPressureBasis(120.0, 150.0, 1.1);
    calc.setBarriers(145.0, 160.0);
    calc.calcProtection();

    assertTrue(calc.isFullyRated(), "Source below design should be fully rated");
    assertEquals("FULLY_RATED", calc.getVerdict());
    assertTrue(calc.isProtectionAdequate());
    assertTrue(calc.getDesignMargin() > 0.0, "Design margin should be positive");
    assertNotNull(calc.toJson());
  }

  /**
   * An under-rated segment with adequate barriers should require two barriers.
   */
  @Test
  void testTwoBarriersRequired() {
    PipelinePressureProtectionCalculator calc = new PipelinePressureProtectionCalculator();
    calc.setPressureBasis(250.0, 150.0, 1.1);
    calc.setBarriers(145.0, 160.0);
    calc.calcProtection();

    assertFalse(calc.isFullyRated(), "Source above design should not be fully rated");
    assertEquals(165.0, calc.getMaxIncidentalPressure(), 1e-9, "MIP = 1.1 * 150");
    assertTrue(calc.isBarrier1Adequate());
    assertTrue(calc.isBarrier2Adequate());
    assertEquals("TWO_BARRIERS_REQUIRED", calc.getVerdict());
    assertTrue(calc.isProtectionAdequate());
  }

  /**
   * An under-rated segment with a barrier above MIP should be INSUFFICIENT.
   */
  @Test
  void testInsufficientProtection() {
    PipelinePressureProtectionCalculator calc = new PipelinePressureProtectionCalculator();
    calc.setPressureBasis(250.0, 150.0, 1.1);
    calc.setBarriers(145.0, 200.0);
    calc.calcProtection();

    assertFalse(calc.isBarrier2Adequate(), "Barrier 2 above MIP should be inadequate");
    assertEquals("INSUFFICIENT", calc.getVerdict());
    assertFalse(calc.isProtectionAdequate());
  }
}
