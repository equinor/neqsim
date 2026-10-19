package neqsim.process.equipment.pump;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.NeqSimTest;

/**
 * Synthetic reduced-curve checks, not qualification of a manufacturer's operating envelope.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class PumpOperatingStatusTest extends NeqSimTest {
  /** The same reduced-flow BEP remains optimal at each requested speed. */
  @Test
  void bestEfficiencyStatusFollowsRequestedSpeed() {
    PumpChart chart = chart(false);
    for (double speed : new double[] {500.0, 1000.0, 1500.0, 2500.0}) {
      assertEquals("OPTIMAL", chart.getOperatingStatus(chart.getBestEfficiencyFlowRate(speed), speed),
          "BEP must be evaluated at the requested speed " + speed);
    }
  }

  /** Both sides of the reduced-flow upper boundary retain their classification. */
  @Test
  void highFlowBoundaryFollowsReducedCurveRange() {
    PumpChart chart = chart(false);
    for (double speed : new double[] {500.0, 1000.0, 1500.0, 2500.0}) {
      assertFalse(chart.checkStoneWall(89.0 * speed / 1000.0, speed));
      assertTrue(chart.checkStoneWall(91.0 * speed / 1000.0, speed));
      assertEquals("STONEWALL", chart.getOperatingStatus(91.0 * speed / 1000.0, speed));
    }
  }

  /** The analytical head maximum separates positive and negative slopes at each speed. */
  @Test
  void positiveHeadSlopeIsCheckedAtRequestedReducedFlow() {
    PumpChart chart = chart(true);
    // H(Q) = 100 + 2 Q - 0.025 Q^2 at 1000 rpm has its maximum at Q = 40 m3/hr.
    for (double speed : new double[] {500.0, 1000.0, 1500.0, 2500.0}) {
      assertTrue(chart.checkSurge2(30.0 * speed / 1000.0, speed));
      assertFalse(chart.checkSurge2(60.0 * speed / 1000.0, speed));
      assertEquals("SURGE", chart.getOperatingStatus(30.0 * speed / 1000.0, speed));
    }
    // The legacy no-speed overload continues to describe the reference-speed curve.
    assertTrue(chart.checkSurge1(30.0, 0.0));
    assertFalse(chart.checkSurge1(60.0, 0.0));
  }

  /** Relative efficiency uses the BEP efficiency at the same requested speed. */
  @Test
  void lowEfficiencyClassificationIsSpeedConsistent() {
    PumpChart chart = new PumpChart();
    chart.setCurves(new double[0], new double[] {1000.0}, new double[][] {{10.0, 50.0, 90.0}},
        new double[][] {{190.0, 150.0, 110.0}}, new double[][] {{40.0, 80.0, 40.0}});
    for (double speed : new double[] {500.0, 1000.0, 1500.0, 2500.0}) {
      assertEquals("LOW_EFFICIENCY", chart.getOperatingStatus(10.0 * speed / 1000.0, speed));
    }
  }

  /** Speed-aware slope checks retain virtual dispatch with a reference-speed flow coordinate. */
  @Test
  void speedAwareSlopeCheckDelegatesToLegacyHook() {
    PumpChart chart = new PumpChart() {
      /** {@inheritDoc} */
      @Override
      public boolean checkSurge1(double flow, double head) {
        assertEquals(30.0, flow, 1e-12);
        assertEquals(17.0, head, 0.0);
        return true;
      }

      /** {@inheritDoc} */
      @Override
      public double getHead(double flow, double speed) {
        assertEquals(60.0, flow, 0.0);
        assertEquals(2000.0, speed, 0.0);
        return 17.0;
      }
    };
    assertTrue(chart.checkSurge2(60.0, 2000.0));
  }

  /** Multiple supplied speeds do not turn the largest absolute flow into every speed's limit. */
  @Test
  void multiSpeedMapUsesReducedRatherThanGlobalAbsoluteLimit() {
    PumpChart chart = new PumpChart();
    chart.setCurves(new double[0], new double[] {1000.0, 2000.0},
        new double[][] {{10.0, 50.0, 90.0}, {20.0, 100.0, 180.0}},
        new double[][] {{190.0, 150.0, 110.0}, {760.0, 600.0, 440.0}},
        new double[][] {{64.0, 80.0, 64.0}, {64.0, 80.0, 64.0}});
    for (double speed : new double[] {1000.0, 1500.0, 2000.0}) {
      assertFalse(chart.checkStoneWall(89.0 * speed / 1000.0, speed));
      assertTrue(chart.checkStoneWall(91.0 * speed / 1000.0, speed));
      assertEquals("OPTIMAL", chart.getOperatingStatus(50.0 * speed / 1000.0, speed));
    }
  }

  /**
   * Build a synthetic quadratic map at 1000 rpm.
   *
   * @param hump whether to include a positive-slope low-flow region
   * @return fitted default pump chart
   */
  private PumpChart chart(boolean hump) {
    PumpChart chart = new PumpChart();
    double[] flows = {10.0, 30.0, 50.0, 70.0, 90.0};
    double[] heads = new double[flows.length];
    double[] efficiencies = new double[flows.length];
    for (int i = 0; i < flows.length; i++) {
      double flow = flows[i];
      heads[i] = hump ? 100.0 + 2.0 * flow - 0.025 * flow * flow : 200.0 - flow;
      efficiencies[i] = 80.0 - 0.01 * (flow - 50.0) * (flow - 50.0);
    }
    chart.setCurves(new double[0], new double[] {1000.0}, new double[][] {flows}, new double[][] {heads},
        new double[][] {efficiencies});
    return chart;
  }
}
