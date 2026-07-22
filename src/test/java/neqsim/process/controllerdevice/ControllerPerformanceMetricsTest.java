package neqsim.process.controllerdevice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ControllerPerformanceMetrics} (NIP-2): loop-tuning KPI helper computing IAE, ISE, ITAE,
 * process-value variability, valve travel, reversals and settling time from a controller event log or from raw arrays.
 */
class ControllerPerformanceMetricsTest {

  @Test
  void testEmptyLogReturnsZeroMetrics() {
    ControllerPerformanceMetrics m = ControllerPerformanceMetrics
        .fromEventLog(Collections.<ControllerEvent>emptyList());
    Assertions.assertEquals(0, m.getSampleCount());
    Assertions.assertEquals(0.0, m.getIntegralAbsoluteError());
    Assertions.assertEquals(0.0, m.getControllerOutputTravel());
    Assertions.assertEquals(0, m.getControllerOutputReversals());
    Assertions.assertEquals(0.0, m.getSettlingTime());
  }

  @Test
  void testNullLogReturnsZeroMetrics() {
    ControllerPerformanceMetrics m = ControllerPerformanceMetrics.fromEventLog(null);
    Assertions.assertEquals(0, m.getSampleCount());
  }

  @Test
  void testConstantErrorIntegralCriteria() {
    // Constant error of 2.0 over 10 s: IAE = 2*10 = 20, ISE = 4*10 = 40.
    double[] time = { 0.0, 2.0, 4.0, 6.0, 8.0, 10.0 };
    double[] sp = new double[time.length];
    double[] pv = new double[time.length];
    double[] op = new double[time.length];
    for (int i = 0; i < time.length; i++) {
      sp[i] = 10.0;
      pv[i] = 12.0;
      op[i] = 50.0;
    }
    ControllerPerformanceMetrics m = ControllerPerformanceMetrics.fromArrays(time, pv, sp, op);
    Assertions.assertEquals(6, m.getSampleCount());
    Assertions.assertEquals(10.0, m.getDuration(), 1e-9);
    Assertions.assertEquals(20.0, m.getIntegralAbsoluteError(), 1e-9);
    Assertions.assertEquals(40.0, m.getIntegralSquaredError(), 1e-9);
    Assertions.assertEquals(2.0, m.getPeakAbsoluteError(), 1e-9);
    // No output movement -> no travel and no reversals.
    Assertions.assertEquals(0.0, m.getControllerOutputTravel(), 1e-9);
    Assertions.assertEquals(0, m.getControllerOutputReversals());
  }

  @Test
  void testValveTravelAndReversals() {
    // Output goes 50 -> 60 -> 55 -> 65: travel = 10 + 5 + 10 = 25, one reversal (up, down, up).
    double[] time = { 0.0, 1.0, 2.0, 3.0 };
    double[] sp = { 10.0, 10.0, 10.0, 10.0 };
    double[] pv = { 10.0, 10.0, 10.0, 10.0 };
    double[] op = { 50.0, 60.0, 55.0, 65.0 };
    ControllerPerformanceMetrics m = ControllerPerformanceMetrics.fromArrays(time, pv, sp, op);
    Assertions.assertEquals(25.0, m.getControllerOutputTravel(), 1e-9);
    Assertions.assertEquals(2, m.getControllerOutputReversals());
  }

  @Test
  void testProcessValueVariability() {
    // PV alternates 9, 11 around mean 10 -> population std dev = 1.
    double[] time = { 0.0, 1.0, 2.0, 3.0 };
    double[] sp = { 10.0, 10.0, 10.0, 10.0 };
    double[] pv = { 9.0, 11.0, 9.0, 11.0 };
    double[] op = { 50.0, 50.0, 50.0, 50.0 };
    ControllerPerformanceMetrics m = ControllerPerformanceMetrics.fromArrays(time, pv, sp, op);
    Assertions.assertEquals(10.0, m.getMeanProcessValue(), 1e-9);
    Assertions.assertEquals(1.0, m.getProcessValueStandardDeviation(), 1e-9);
  }

  @Test
  void testSettlingTime() {
    // Error settles inside 2% band (band = 0.02*max(|10|,1) = 0.2) at t = 6 s.
    double[] time = { 0.0, 2.0, 4.0, 6.0, 8.0, 10.0 };
    double[] sp = { 10.0, 10.0, 10.0, 10.0, 10.0, 10.0 };
    double[] pv = { 13.0, 11.0, 10.3, 10.05, 10.02, 10.0 };
    double[] op = { 50.0, 50.0, 50.0, 50.0, 50.0, 50.0 };
    ControllerPerformanceMetrics m = ControllerPerformanceMetrics.fromArrays(time, pv, sp, op);
    // Last sample outside the band is at t = 4 s (error 0.3 > 0.2).
    Assertions.assertEquals(4.0, m.getSettlingTime(), 1e-9);
  }

  @Test
  void testMismatchedArrayLengthsThrows() {
    double[] time = { 0.0, 1.0 };
    double[] pv = { 1.0 };
    double[] sp = { 1.0, 1.0 };
    double[] op = { 1.0, 1.0 };
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> ControllerPerformanceMetrics.fromArrays(time, pv, sp, op));
  }

  @Test
  void testFromEventLogMatchesArrays() {
    List<ControllerEvent> log = new ArrayList<>();
    log.add(new ControllerEvent(0.0, 12.0, 10.0, 2.0, 50.0));
    log.add(new ControllerEvent(5.0, 11.0, 10.0, 1.0, 55.0));
    log.add(new ControllerEvent(10.0, 10.0, 10.0, 0.0, 52.0));
    ControllerPerformanceMetrics m = ControllerPerformanceMetrics.fromEventLog(log);
    Assertions.assertEquals(3, m.getSampleCount());
    Assertions.assertEquals(10.0, m.getDuration(), 1e-9);
    // Valve travel = |55-50| + |52-55| = 5 + 3 = 8, one reversal.
    Assertions.assertEquals(8.0, m.getControllerOutputTravel(), 1e-9);
    Assertions.assertEquals(1, m.getControllerOutputReversals());
    Assertions.assertEquals(2.0, m.getPeakAbsoluteError(), 1e-9);
  }
}
