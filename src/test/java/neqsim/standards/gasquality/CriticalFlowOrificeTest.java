package neqsim.standards.gasquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CriticalFlowOrifice}.
 */
public class CriticalFlowOrificeTest {
  /**
   * The critical pressure ratio for k = 1.3 should be approximately 0.546.
   */
  @Test
  void testCriticalPressureRatio() {
    double rc = CriticalFlowOrifice.criticalPressureRatio(1.3);
    assertEquals(0.5457, rc, 1e-3, "Critical pressure ratio for k=1.3 should be ~0.546");

    double rcDiatomic = CriticalFlowOrifice.criticalPressureRatio(1.4);
    assertEquals(0.5283, rcDiatomic, 1e-3, "Critical pressure ratio for k=1.4 should be ~0.528");
  }

  /**
   * The static isChoked test should return true for a large pressure drop and false for a small one.
   */
  @Test
  void testIsChoked() {
    assertTrue(CriticalFlowOrifice.isChoked(5.0e6, 1.0e5, 1.3), "Large drop to atmosphere should be choked");
    assertFalse(CriticalFlowOrifice.isChoked(5.0e6, 4.5e6, 1.3), "Small drop should not be choked");
  }

  /**
   * The choked mass flow should be positive and increase with throat area and upstream pressure.
   */
  @Test
  void testChokedMassFlow() {
    CriticalFlowOrifice calc = new CriticalFlowOrifice();
    calc.setGeometry(1.0e-4, 0.85);
    calc.setUpstreamConditions(5.0e6, 50.0, 1.3);
    calc.calcCriticalFlow();

    assertTrue(calc.getCriticalMassFlow() > 0.0, "Choked mass flow should be positive");
    assertTrue(calc.isFlowChoked(), "Flow should be flagged choked");
    assertTrue(calc.getCriticalExpansionFactor() > 0.0, "Critical expansion factor should be positive");
    assertEquals(CriticalFlowOrifice.criticalPressureRatio(1.3), calc.getCriticalPressureRatio(), 1e-9);
    assertNotNull(calc.toJson());

    CriticalFlowOrifice bigger = new CriticalFlowOrifice();
    bigger.setGeometry(2.0e-4, 0.85);
    bigger.setUpstreamConditions(5.0e6, 50.0, 1.3);
    bigger.calcCriticalFlow();
    assertTrue(bigger.getCriticalMassFlow() > calc.getCriticalMassFlow(),
        "Larger throat area should give higher mass flow");
  }

  /**
   * Higher upstream pressure should raise the choked mass flow.
   */
  @Test
  void testHigherPressureHigherFlow() {
    CriticalFlowOrifice low = new CriticalFlowOrifice();
    low.setGeometry(1.0e-4, 0.85);
    low.setUpstreamConditions(2.0e6, 20.0, 1.3);
    low.calcCriticalFlow();

    CriticalFlowOrifice high = new CriticalFlowOrifice();
    high.setGeometry(1.0e-4, 0.85);
    high.setUpstreamConditions(8.0e6, 80.0, 1.3);
    high.calcCriticalFlow();

    assertTrue(high.getCriticalMassFlow() > low.getCriticalMassFlow(),
        "Higher upstream pressure/density should give higher choked flow");
  }
}
