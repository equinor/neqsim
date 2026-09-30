package neqsim.process.mechanicaldesign.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AxialThrustScreening}.
 *
 * @author ESOL
 * @version 1.0
 */
public class AxialThrustScreeningTest {

  /** Exact linear data: thrust = 10 - 5*flow, zero crossing at flow = 2. */
  @Test
  public void testFitLinearAndZeroCrossing() {
    double[] flow = {0.0, 1.0, 2.0, 3.0, 4.0};
    double[] thrust = {10.0, 5.0, 0.0, -5.0, -10.0};
    double[] fit = AxialThrustScreening.fitLinear(flow, thrust);
    assertEquals(-5.0, fit[0], 1.0e-9);
    assertEquals(10.0, fit[1], 1.0e-9);
    assertEquals(2.0, AxialThrustScreening.zeroCrossingFlow(fit[0], fit[1]), 1.0e-9);
  }

  /** Vendor-style thrust-vs-flow data point pair (Appendix B digitisation style). */
  @Test
  public void testEvaluateNearCrossing() {
    double[] flow = {1.40e6, 2.00e6};
    double[] thrust = {0.35, -0.45};
    // Design flow ~1.832 MSm3/h sits slightly past the crossing on the negative side.
    AxialThrustScreening.Result r = AxialThrustScreening.evaluate(flow, thrust, 1.832203e6, 0.10);
    assertEquals(1.6625e6, r.getZeroCrossingFlow(), 1.0e3);
    assertTrue(r.getPredictedThrust() < 0.0);
    assertEquals("INBOARD", r.getThrustDirection());
    assertTrue(r.toJson().contains("zeroCrossingFlow"));
  }

  /** A flow right at the crossing is flagged near null thrust; one far away is not. */
  @Test
  public void testNearNullThrustFlag() {
    double[] flow = {0.0, 1.0, 2.0, 3.0, 4.0};
    double[] thrust = {10.0, 5.0, 0.0, -5.0, -10.0};
    assertTrue(AxialThrustScreening.evaluate(flow, thrust, 2.05, 0.10).isNearNullThrust());
    assertFalse(AxialThrustScreening.evaluate(flow, thrust, 4.0, 0.10).isNearNullThrust());
    AxialThrustScreening.Result onCrossing = AxialThrustScreening.evaluate(flow, thrust, 2.0, 0.10);
    assertEquals("NULL", onCrossing.getThrustDirection());
    assertEquals(0.0, onCrossing.getMarginFraction(), 1.0e-9);
  }

  /** Invalid input is rejected. */
  @Test
  public void testInvalidInput() {
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        AxialThrustScreening.fitLinear(new double[] {1.0}, new double[] {1.0});
      }
    });
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        AxialThrustScreening.fitLinear(new double[] {1.0, 1.0, 1.0}, new double[] {1.0, 2.0, 3.0});
      }
    });
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        AxialThrustScreening.zeroCrossingFlow(0.0, 5.0);
      }
    });
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        AxialThrustScreening.evaluate(new double[] {0.0, 1.0}, new double[] {1.0, -1.0}, 0.5, 0.0);
      }
    });
  }
}
