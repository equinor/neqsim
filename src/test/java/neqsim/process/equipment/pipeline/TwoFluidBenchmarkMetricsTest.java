package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TwoFluidBenchmarkMetricsTest {
  @Test
  void fitsRateSweepExponentInsteadOfOnlyCheckingOneLevel() {
    double[] rates = { 1.0, 2.0, 4.0, 8.0 };
    double[] pressureDrops = new double[rates.length];
    for (int index = 0; index < rates.length; index++) {
      pressureDrops[index] = 3.5 * Math.pow(rates[index], 1.8);
    }
    assertEquals(1.8, TwoFluidBenchmarkMetrics.fitRateExponent(rates, pressureDrops), 1.0e-12);
  }

  @Test
  void reportsScaleFreeProfileLocalizationAndMeshSpread() {
    assertEquals(9.0, TwoFluidBenchmarkMetrics.maximumToMedianRatio(new double[] { 1.0, 1.0, 1.0, 9.0 }), 0.0);
    assertEquals(0.2, TwoFluidBenchmarkMetrics.relativeMeshSpread(80.0, 100.0), 1.0e-15);
    assertEquals(0.2, TwoFluidBenchmarkMetrics.relativeMeshSpread(100.0, 80.0), 1.0e-15);
  }

  @Test
  void recoversPeriodBandAndCompletedCyclesFromSettledSignal() {
    double[] time = new double[481];
    double[] signal = new double[481];
    for (int index = 0; index < time.length; index++) {
      time[index] = 0.25 * index;
      signal[index] = 2.0 + Math.sin(2.0 * Math.PI * time[index] / 10.0);
    }

    TwoFluidBenchmarkMetrics.LimitCycleMetrics metrics = TwoFluidBenchmarkMetrics.analyzeLimitCycle(time, signal, 20.0);

    assertTrue(metrics.hasRepeatedCycle());
    assertEquals(10.0, metrics.getPeriodSeconds(), 1.0e-12);
    assertEquals(8, metrics.getCompletedCycleCount());
    assertEquals(1.902113032590307, metrics.getP10ToP90Band(), 1.0e-12);
  }

  @Test
  void startupSpikeDoesNotMasqueradeAsSettledLimitCycle() {
    double[] time = new double[101];
    double[] signal = new double[101];
    for (int index = 0; index < time.length; index++) {
      time[index] = index;
      signal[index] = index < 10 ? 100.0 * Math.exp(-0.5 * index) : 5.0;
    }

    TwoFluidBenchmarkMetrics.LimitCycleMetrics metrics = TwoFluidBenchmarkMetrics.analyzeLimitCycle(time, signal, 20.0);

    assertFalse(metrics.hasRepeatedCycle());
    assertEquals(0, metrics.getCompletedCycleCount());
    assertEquals(0.0, metrics.getP10ToP90Band(), 0.0);
  }
}
