package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TwoFluidBenchmarkMetricsTest {
  @Test
  void fitsRateSweepExponentInsteadOfOnlyCheckingOneLevel() {
    double[] rates = {1.0, 2.0, 4.0, 8.0};
    double[] pressureDrops = new double[rates.length];
    for (int index = 0; index < rates.length; index++) {
      pressureDrops[index] = 3.5 * Math.pow(rates[index], 1.8);
    }
    assertEquals(1.8, TwoFluidBenchmarkMetrics.fitRateExponent(rates, pressureDrops), 1.0e-12);
  }

  @Test
  void reportsScaleFreeProfileLocalizationAndMeshSpread() {
    assertEquals(9.0, TwoFluidBenchmarkMetrics.maximumToMedianRatio(new double[] {1.0, 1.0, 1.0, 9.0}), 0.0);
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
    assertEquals(2.0, metrics.getMedianCyclePeakToPeak(), 1.0e-12);
    assertEquals(0.0, metrics.getRelativeCycleAmplitudeSpread(), 1.0e-12);
    assertEquals(0.0, metrics.getRelativeCyclePeriodSpread(), 1.0e-12);
    assertEquals(9, metrics.getMedianUpcrossingTimesSeconds().length);
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

  @Test
  void measuresCompleteCycleStabilityInsteadOfStartupExtrema() {
    double[] time = new double[241];
    double[] signal = new double[241];
    for (int index = 0; index < time.length; index++) {
      time[index] = 0.5 * index;
      double amplitude = time[index] < 20.0 ? 20.0 : 3.0;
      signal[index] = 100.0 + amplitude * Math.sin(2.0 * Math.PI * time[index] / 12.0);
    }

    TwoFluidBenchmarkMetrics.LimitCycleMetrics metrics = TwoFluidBenchmarkMetrics.analyzeLimitCycle(time, signal, 36.0);

    assertTrue(metrics.getCompletedCycleCount() >= 5);
    assertEquals(6.0, metrics.getMedianCyclePeakToPeak(), 1.0e-12);
    assertEquals(12.0, metrics.getPeriodSeconds(), 1.0e-12);
    assertEquals(0.0, metrics.getRelativeCycleAmplitudeSpread(), 1.0e-12);
    assertEquals(0.0, metrics.getRelativeCyclePeriodSpread(), 1.0e-12);
  }

  @Test
  void distinguishesFixedPhaseLagFromDriftingResponse() {
    double[] time = new double[481];
    double[] pressure = new double[481];
    double[] lockedProduction = new double[481];
    double[] driftingProduction = new double[481];
    for (int index = 0; index < time.length; index++) {
      time[index] = 0.25 * index;
      pressure[index] = Math.sin(2.0 * Math.PI * time[index] / 10.0);
      lockedProduction[index] = Math.sin(2.0 * Math.PI * (time[index] - 2.0) / 10.0);
      driftingProduction[index] = Math.sin(2.0 * Math.PI * time[index] / 11.0);
    }
    TwoFluidBenchmarkMetrics.LimitCycleMetrics pressureCycles = TwoFluidBenchmarkMetrics.analyzeLimitCycle(time,
        pressure, 20.0);
    TwoFluidBenchmarkMetrics.LimitCycleMetrics lockedCycles = TwoFluidBenchmarkMetrics.analyzeLimitCycle(time,
        lockedProduction, 20.0);
    TwoFluidBenchmarkMetrics.LimitCycleMetrics driftingCycles = TwoFluidBenchmarkMetrics.analyzeLimitCycle(time,
        driftingProduction, 20.0);

    TwoFluidBenchmarkMetrics.PhaseLockMetrics locked = TwoFluidBenchmarkMetrics.analyzePhaseLock(pressureCycles,
        lockedCycles);
    TwoFluidBenchmarkMetrics.PhaseLockMetrics drifting = TwoFluidBenchmarkMetrics.analyzePhaseLock(pressureCycles,
        driftingCycles);

    assertTrue(locked.hasRepeatedPhaseLock());
    assertEquals(0.2, locked.getMeanPhaseOffsetCycles(), 1.0e-12);
    assertEquals(0.0, locked.getMaximumPhaseDeviationCycles(), 1.0e-12);
    assertTrue(locked.getCircularResultant() > 0.999999);
    assertTrue(drifting.getMaximumPhaseDeviationCycles() > 0.1);
    assertTrue(drifting.getCircularResultant() < 0.95);
  }

  @Test
  void doesNotReuseResponseCrossingsToAcceptASubharmonic() {
    double[] time = new double[481];
    double[] pressure = new double[481];
    double[] subharmonicProduction = new double[481];
    for (int index = 0; index < time.length; index++) {
      time[index] = 0.25 * index;
      pressure[index] = Math.sin(2.0 * Math.PI * time[index] / 10.0);
      subharmonicProduction[index] = Math.sin(2.0 * Math.PI * time[index] / 20.0);
    }
    TwoFluidBenchmarkMetrics.LimitCycleMetrics pressureCycles = TwoFluidBenchmarkMetrics.analyzeLimitCycle(time,
        pressure, 20.0);
    TwoFluidBenchmarkMetrics.LimitCycleMetrics productionCycles = TwoFluidBenchmarkMetrics.analyzeLimitCycle(time,
        subharmonicProduction, 20.0);

    TwoFluidBenchmarkMetrics.PhaseLockMetrics phaseLock = TwoFluidBenchmarkMetrics.analyzePhaseLock(pressureCycles,
        productionCycles);

    assertFalse(phaseLock.hasRepeatedPhaseLock());
    assertEquals(productionCycles.getMedianUpcrossingTimesSeconds().length, phaseLock.getMatchedCrossings());
    assertTrue(phaseLock.getMatchedCrossings() < phaseLock.getReferenceCrossings() - 1);
  }
}
