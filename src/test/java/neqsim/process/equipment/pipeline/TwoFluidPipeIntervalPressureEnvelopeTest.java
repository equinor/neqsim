package neqsim.process.equipment.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * A pressure screening must not depend on how often a caller samples the pipe. The interval envelope records every
 * accepted internal substep of a transient call.
 */
class TwoFluidPipeIntervalPressureEnvelopeTest {
  private static TwoFluidPipe blockedGasLine() {
    SystemInterface fluid = new SystemSrkEos(288.15, 70.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("nitrogen", 0.05);
    fluid.setMixingRule("classic");
    Stream inlet = new Stream("inlet", fluid);
    inlet.setFlowRate(8.0, "kg/sec");
    inlet.run();
    TwoFluidPipe pipe = new TwoFluidPipe("blocked line", inlet);
    pipe.setLength(400.0);
    pipe.setDiameter(0.20);
    pipe.setNumberOfSections(10);
    pipe.setOutletPressure(68.0, "bara");
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.run();
    return pipe;
  }

  @Test
  void envelopeIncludesEveryAcceptedSubstepAndResetsWithSteadyState() {
    TwoFluidPipe pipe = blockedGasLine();
    assertEquals(0, pipe.getLastIntervalPeakPressureProfile("bara").length);
    assertTrue(Double.isNaN(pipe.getLastIntervalPeakInletFacePressure("bara")));
    assertFalse(pipe.isLastIntervalPressureEnvelopeSubstepResolved());

    double[] start = pipe.getPressureProfile();
    double startTime = pipe.getSimulationTime();
    pipe.closeOutlet();
    double interval = 2.0;
    pipe.runTransient(interval, UUID.randomUUID());

    assertTrue(pipe.isLastIntervalPressureEnvelopeSubstepResolved());
    double[] end = pipe.getPressureProfile();
    double[] peak = pipe.getLastIntervalPeakPressureProfile("Pa");
    double[] when = pipe.getLastIntervalPeakPressureTimeProfile();
    double[] peakBara = pipe.getLastIntervalPeakPressureProfile("bara");
    assertEquals(start.length, peak.length);
    boolean interiorPeak = false;
    for (int i = 0; i < peak.length; i++) {
      assertTrue(peak[i] >= start[i] && peak[i] >= end[i], "The envelope bounds both sampled states");
      assertTrue(when[i] >= startTime - 1.0e-12 && when[i] <= startTime + interval + 1.0e-9);
      assertEquals(peak[i] / 1.0e5, peakBara[i], 1.0e-9);
      interiorPeak |= peak[i] > Math.max(start[i], end[i]) * (1.0 + 1.0e-6) && when[i] < startTime + interval;
    }
    assertTrue(interiorPeak, "A blocked-outlet pressure wave peaks between the two sampled states");
    double face = pipe.getLastIntervalPeakInletFacePressure("bara");
    assertTrue(face >= pipe.getInletFacePressure("bara") - 1.0e-9);
    assertTrue(pipe.getLastIntervalPeakInletFacePressureTime() <= startTime + interval + 1.0e-9);

    // A second call starts a new interval; it does not accumulate the previous excursion.
    pipe.runTransient(1.0e-3, UUID.randomUUID());
    double[] next = pipe.getLastIntervalPeakPressureProfile("Pa");
    double[] nextTime = pipe.getLastIntervalPeakPressureTimeProfile();
    for (int i = 0; i < next.length; i++) {
      assertTrue(nextTime[i] >= startTime + interval - 1.0e-9);
    }

    pipe.run();
    assertEquals(0, pipe.getLastIntervalPeakPressureProfile("bara").length);
    assertFalse(pipe.isLastIntervalPressureEnvelopeSubstepResolved());
  }

  @Test
  void transactionalRoutePublishesTheCandidateEnvelope() {
    TwoFluidPipe pipe = blockedGasLine();
    pipe.setTransactionalTransientEnabled(true);
    pipe.closeOutlet();
    pipe.runTransient(0.5, UUID.randomUUID());
    assertTrue(pipe.isLastIntervalPressureEnvelopeSubstepResolved());
    double[] peak = pipe.getLastIntervalPeakPressureProfile("bara");
    double[] end = pipe.getPressureProfile();
    assertEquals(end.length, peak.length);
    for (int i = 0; i < peak.length; i++) {
      assertTrue(peak[i] >= end[i] / 1.0e5 - 1.0e-12);
    }
  }
}
