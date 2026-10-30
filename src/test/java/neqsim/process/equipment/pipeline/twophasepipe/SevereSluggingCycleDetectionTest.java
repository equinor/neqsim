package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Independent pulse-train controls for the severe-slugging fixture's liquid-cycle measurement.
 *
 * @author NeqSim
 * @version 1.0
 */
class SevereSluggingCycleDetectionTest {
  /** A single extreme reverse-flow sample must not hide the later blowout/fallback cycles. */
  @Test
  void reverseFlowOutlierDoesNotEraseRepeatedCycles() {
    assertCycles(pulseTrain(-30.0), 2, 40.0);
  }

  /** A single extreme forward-flow sample must not redefine every fallback event. */
  @Test
  void forwardFlowOutlierDoesNotEraseRepeatedCycles() {
    double[] rates = pulseTrain(0.0);
    rates[10] = 100.0;
    assertCycles(rates, 2, 40.0);
  }

  /** The unchanged periodic signal supplies two independently specified forty-second intervals. */
  @Test
  void measuresKnownCyclePeriod() {
    assertCycles(pulseTrain(0.0), 2, 40.0);
  }

  /** Low-flow ripples without a blowout above the feed do not constitute production cycles. */
  @Test
  void doesNotCountLowFlowRipplesAsBlowoutCycles() {
    double[] rates = pulseTrain(0.0);
    for (int index = 0; index < rates.length; index++) {
      rates[index] = Math.min(rates[index], 1.6);
    }
    assertNoCycle(rates);
  }

  /** A single recovered excursion cannot supply a complete interval between repeated events. */
  @Test
  void doesNotCountAnIsolatedReverseFlowSpike() {
    double[] rates = new double[121];
    java.util.Arrays.fill(rates, 3.0);
    rates[60] = -30.0;
    assertNoCycle(rates);
  }

  /** Flat and monotone traces must not acquire a cycle from threshold selection. */
  @Test
  void doesNotCountFlatOrMonotoneFlow() {
    double[] rates = new double[121];
    java.util.Arrays.fill(rates, 0.0);
    assertNoCycle(rates);
    for (int index = 0; index < rates.length; index++) {
      rates[index] = 4.0 * index / (rates.length - 1.0);
    }
    assertNoCycle(rates);
  }

  /**
   * Construct three recovered, zero-production troughs at 20, 60 and 100 seconds.
   *
   * @param firstTroughKgPerSecond value of the first trough, kg/s
   * @return one-second liquid-rate samples, kg/s
   */
  private static double[] pulseTrain(double firstTroughKgPerSecond) {
    double[] rates = new double[121];
    java.util.Arrays.fill(rates, 3.0);
    rates[20] = firstTroughKgPerSecond;
    rates[60] = 0.0;
    rates[100] = 0.0;
    return rates;
  }

  /**
   * Check known event spacing without running the thermohydraulic fixture.
   *
   * @param rates liquid mass rates, kg/s
   * @param expectedCount completed trough intervals
   * @param expectedPeriodSeconds independently specified interval, s
   */
  private static void assertCycles(double[] rates, int expectedCount, double expectedPeriodSeconds) {
    SevereSluggingExperimentalBenchmarkTest.LowProductionCycleMetrics metrics = analyze(rates);
    assertEquals(expectedCount, metrics.completedCycleCount);
    assertEquals(expectedPeriodSeconds, metrics.periodSeconds, 1.0e-12);
  }

  /**
   * Require an explicit missing-period result for signals without repeated blowout/fallback.
   *
   * @param rates liquid mass rates, kg/s
   */
  private static void assertNoCycle(double[] rates) {
    SevereSluggingExperimentalBenchmarkTest.LowProductionCycleMetrics metrics = analyze(rates);
    assertEquals(0, metrics.completedCycleCount);
    assertTrue(Double.isNaN(metrics.periodSeconds));
  }

  /**
   * Analyze a one-second synthetic sample grid through the actual fixture's detector.
   *
   * @param rates liquid mass rates, kg/s
   * @return measured cycle metrics
   */
  private static SevereSluggingExperimentalBenchmarkTest.LowProductionCycleMetrics analyze(double[] rates) {
    List<Double> times = new ArrayList<>();
    List<Double> samples = new ArrayList<>();
    for (int index = 0; index < rates.length; index++) {
      times.add((double) index);
      samples.add(rates[index]);
    }
    return SevereSluggingExperimentalBenchmarkTest.analyzeLowProductionCycles(times, samples);
  }
}
