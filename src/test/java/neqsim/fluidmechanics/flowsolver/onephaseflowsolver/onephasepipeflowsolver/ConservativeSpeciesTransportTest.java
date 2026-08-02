package neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/** Tests conservative n-1 implicit finite-volume species transport. */
class ConservativeSpeciesTransportTest {
  @Test
  void closesEveryComponentWithoutNormalizationOrClipping() {
    String[] names = { "methane", "nitrogen", "carbon dioxide" };
    double[][] oldMassFraction = { { 0.80, 0.75 }, { 0.15, 0.20 }, { 0.05, 0.05 } };
    double[] inletMassFraction = { 0.70, 0.20, 0.10 };
    double[] oldCellMassKg = { 100.0, 100.0 };
    double[] newCellMassKg = { 105.0, 100.0 };
    double[] faceMassFlowKgPerSecond = { 1.0, 0.5, 0.5 };

    OnePhaseSpeciesConservationReport report = ConservativeSpeciesTransport.solve(names, oldMassFraction,
        inletMassFraction, oldCellMassKg, newCellMassKg, faceMassFlowKgPerSecond, 10.0);

    assertEquals(OnePhaseSpeciesConservationReport.ConservationReason.CONVERGED, report.getReason(),
        report.getMessage());
    assertTrue(report.getMaximumRelativeInventoryResidual() < 1.0e-14, report.getMessage());
    assertEquals(0.0, report.getMaximumMassFractionSumError(), 0.0);
    assertTrue(report.getMinimumMassFraction() >= 0.0);
    assertTrue(report.getMaximumMassFraction() <= 1.0);

    double[][] profile = report.getMassFractionProfile();
    assertEquals((80.0 + 10.0 * 1.0 * 0.70) / (105.0 + 10.0 * 0.5), profile[0][0], 1.0e-15);
    assertEquals((75.0 + 10.0 * 0.5 * profile[0][0]) / (100.0 + 10.0 * 0.5), profile[0][1], 1.0e-15);
  }

  @Test
  void reproducesImplicitUpwindStepRecurrence() {
    String[] names = { "carrier", "tracer" };
    double[][] oldMassFraction = { { 1.0, 1.0, 1.0, 1.0 }, { 0.0, 0.0, 0.0, 0.0 } };
    double[] inletMassFraction = { 0.0, 1.0 };
    double[] cellMassKg = { 10.0, 10.0, 10.0, 10.0 };
    double[] faceMassFlowKgPerSecond = { 1.0, 1.0, 1.0, 1.0, 1.0 };

    OnePhaseSpeciesConservationReport report = ConservativeSpeciesTransport.solve(names, oldMassFraction,
        inletMassFraction, cellMassKg, cellMassKg, faceMassFlowKgPerSecond, 5.0);

    assertTrue(report.isConverged(), report.getMessage());
    double[] expectedTracer = { 1.0 / 3.0, 1.0 / 9.0, 1.0 / 27.0, 1.0 / 81.0 };
    assertArrayEquals(expectedTracer, report.getMassFractionProfile()[1], 1.0e-15);
    assertTrue(report.getMaximumRelativeInventoryResidual() < 1.0e-14);
  }

  @Test
  void repeatedStepMatchesClosedFormAndInventoryOverFlowResidenceTime() {
    int cells = 8;
    double cellMassKg = 10.0;
    double massFlowKgPerSecond = 1.0;
    double timeStepSeconds = 5.0;
    double courantNumber = massFlowKgPerSecond * timeStepSeconds / cellMassKg;
    double successProbability = courantNumber / (1.0 + courantNumber);
    double failureProbability = 1.0 / (1.0 + courantNumber);
    double[][] profile = uniformProfile(cells, 0.0);
    double[] cellMasses = filled(cells, cellMassKg);
    double[] faceFlows = filled(cells + 1, massFlowKgPerSecond);

    for (int step = 1; step <= 20; step++) {
      OnePhaseSpeciesConservationReport report = ConservativeSpeciesTransport.solve(
          new String[] { "carrier", "tracer" }, profile, new double[] { 0.0, 1.0 }, cellMasses, cellMasses, faceFlows,
          timeStepSeconds);

      assertTrue(report.isConverged(), report.getMessage());
      assertTrue(report.getMaximumRelativeInventoryResidual() < 1.0e-14, report.getMessage());
      profile = report.getMassFractionProfile();
      for (int cell = 0; cell < cells; cell++) {
        double expected = negativeBinomialStepResponse(step, cell, successProbability, failureProbability);
        assertEquals(expected, profile[1][cell], 2.0e-14, "step=" + step + ", cell=" + cell);
      }
    }

    cells = 12;
    cellMassKg = 300.0;
    timeStepSeconds = 60.0;
    profile = uniformProfile(cells, 0.0);
    cellMasses = filled(cells, cellMassKg);
    faceFlows = filled(cells + 1, massFlowKgPerSecond);
    double impulseSum = 0.0;
    double firstMomentSeconds = 0.0;
    for (int step = 1; step <= 480; step++) {
      double tracerInlet = step == 1 ? 1.0 : 0.0;
      OnePhaseSpeciesConservationReport report = ConservativeSpeciesTransport.solve(
          new String[] { "carrier", "tracer" }, profile, new double[] { 1.0 - tracerInlet, tracerInlet }, cellMasses,
          cellMasses, faceFlows, timeStepSeconds);
      assertTrue(report.isConverged(), report.getMessage());
      profile = report.getMassFractionProfile();
      double outletImpulse = profile[1][cells - 1];
      impulseSum += outletImpulse;
      firstMomentSeconds += (step - 1) * timeStepSeconds * outletImpulse;
    }

    double expectedResidenceTimeSeconds = cells * cellMassKg / massFlowKgPerSecond;
    assertEquals(1.0, impulseSum, 1.0e-10);
    assertEquals(expectedResidenceTimeSeconds, firstMomentSeconds / impulseSum, 1.0e-6);
  }

  @Test
  void thirtyMinutePulseClosesLongDurationInventoryAndImprovesWithRefinement() {
    PulseResult pulse = runPulse(12, 60.0, 3600.0, 1800.0, 21600.0);

    assertEquals(0.0, pulse.globalInventoryResidualKg, 1.0e-9);
    assertEquals(1800.0, pulse.cumulativeInletTracerKg, 1.0e-12);
    assertTrue(pulse.peakStep > pulse.pulseSteps);
    assertTrue(pulse.peakOutletMassFraction > 0.2);
    assertTrue(pulse.finalOutletMassFraction < 1.0e-8);
    assertTrue(pulse.cumulativeOutletTracerKg / pulse.cumulativeInletTracerKg > 0.999999);

    double coarseError = plugFlowPulseError(12, 60.0);
    double refinedError = plugFlowPulseError(24, 30.0);
    assertTrue(refinedError < 0.8 * coarseError,
        "Expected joint grid/time refinement to reduce numerical pulse spreading: coarse=" + coarseError + ", refined="
            + refinedError);
  }

  @Test
  void rejectsInvalidClosureAndReversedFlowWithoutRepairingInputs() {
    assertFalse(OnePhaseSpeciesConservationReport.ConservationReason.COUPLING_NOT_CONVERGED.isConverged());
    String[] names = { "carrier", "tracer" };
    double[][] invalidOld = { { 0.8 }, { 0.3 } };
    OnePhaseSpeciesConservationReport invalid = ConservativeSpeciesTransport.solve(names, invalidOld,
        new double[] { 0.8, 0.2 }, new double[] { 10.0 }, new double[] { 10.0 }, new double[] { 1.0, 1.0 }, 1.0);

    assertEquals(OnePhaseSpeciesConservationReport.ConservationReason.INVALID_STATE, invalid.getReason());
    assertFalse(invalid.isConverged());
    assertTrue(invalid.getMessage().contains("sum to one without normalization"));

    double[][] validOld = { { 0.8 }, { 0.2 } };
    OnePhaseSpeciesConservationReport reversed = ConservativeSpeciesTransport.solve(names, validOld,
        new double[] { 0.8, 0.2 }, new double[] { 10.0 }, new double[] { 10.0 }, new double[] { 1.0, -1.0 }, 1.0);
    assertEquals(OnePhaseSpeciesConservationReport.ConservationReason.UNSUPPORTED_FLOW, reversed.getReason());
    assertTrue(reversed.getMessage().contains("zero and reversed flow"));
  }

  @Test
  void reportDefensivelyCopiesProfiles() {
    OnePhaseSpeciesConservationReport report = ConservativeSpeciesTransport.solve(new String[] { "carrier", "tracer" },
        new double[][] { { 0.8 }, { 0.2 } }, new double[] { 0.8, 0.2 }, new double[] { 10.0 }, new double[] { 10.0 },
        new double[] { 1.0, 1.0 }, 1.0);
    double[][] first = report.getMassFractionProfile();
    first[0][0] = -1.0;
    assertEquals(0.8, report.getMassFractionProfile()[0][0], 1.0e-15);
    assertFalse(OnePhaseSpeciesConservationReport.notRun().toJson().contains("NaN"));
  }

  private static PulseResult runPulse(int cells, double timeStepSeconds, double residenceTimeSeconds,
      double pulseDurationSeconds, double totalTimeSeconds) {
    double massFlowKgPerSecond = 1.0;
    double cellMassKg = residenceTimeSeconds * massFlowKgPerSecond / cells;
    int pulseSteps = (int) Math.round(pulseDurationSeconds / timeStepSeconds);
    int totalSteps = (int) Math.round(totalTimeSeconds / timeStepSeconds);
    double[][] profile = uniformProfile(cells, 0.0);
    double[] cellMasses = filled(cells, cellMassKg);
    double[] faceFlows = filled(cells + 1, massFlowKgPerSecond);
    double cumulativeInletTracerKg = 0.0;
    double cumulativeOutletTracerKg = 0.0;
    double peakOutletMassFraction = 0.0;
    int peakStep = 0;
    double courantNumber = massFlowKgPerSecond * timeStepSeconds / cellMassKg;
    double successProbability = courantNumber / (1.0 + courantNumber);
    double failureProbability = 1.0 / (1.0 + courantNumber);

    for (int step = 1; step <= totalSteps; step++) {
      double tracerInlet = step <= pulseSteps ? 1.0 : 0.0;
      OnePhaseSpeciesConservationReport report = ConservativeSpeciesTransport.solve(
          new String[] { "carrier", "tracer" }, profile, new double[] { 1.0 - tracerInlet, tracerInlet }, cellMasses,
          cellMasses, faceFlows, timeStepSeconds);

      assertTrue(report.isConverged(), report.getMessage());
      assertTrue(report.getMaximumRelativeInventoryResidual() < 1.0e-12, report.getMessage());
      profile = report.getMassFractionProfile();
      double outletMassFraction = profile[1][cells - 1];
      double expectedOutlet = negativeBinomialStepResponse(step, cells - 1, successProbability, failureProbability)
          - negativeBinomialStepResponse(step - pulseSteps, cells - 1, successProbability, failureProbability);
      assertEquals(expectedOutlet, outletMassFraction, 2.0e-13, "step=" + step);
      cumulativeInletTracerKg += report.getInletBoundaryMassKg()[1];
      cumulativeOutletTracerKg += report.getOutletBoundaryMassKg()[1];
      if (outletMassFraction > peakOutletMassFraction) {
        peakOutletMassFraction = outletMassFraction;
        peakStep = step;
      }
    }

    double finalTracerInventoryKg = 0.0;
    for (int cell = 0; cell < cells; cell++) {
      finalTracerInventoryKg += cellMasses[cell] * profile[1][cell];
    }
    double globalResidualKg = finalTracerInventoryKg - cumulativeInletTracerKg + cumulativeOutletTracerKg;
    return new PulseResult(pulseSteps, peakStep, peakOutletMassFraction, profile[1][cells - 1], cumulativeInletTracerKg,
        cumulativeOutletTracerKg, globalResidualKg);
  }

  private static double plugFlowPulseError(int cells, double timeStepSeconds) {
    double residenceTimeSeconds = 3600.0;
    double pulseDurationSeconds = 1800.0;
    double cellMassKg = residenceTimeSeconds / cells;
    int pulseSteps = (int) Math.round(pulseDurationSeconds / timeStepSeconds);
    int totalSteps = (int) Math.round(3.0 * residenceTimeSeconds / timeStepSeconds);
    double[][] profile = uniformProfile(cells, 0.0);
    double[] cellMasses = filled(cells, cellMassKg);
    double[] faceFlows = filled(cells + 1, 1.0);
    double absoluteErrorSeconds = 0.0;
    for (int step = 1; step <= totalSteps; step++) {
      double tracerInlet = step <= pulseSteps ? 1.0 : 0.0;
      OnePhaseSpeciesConservationReport report = ConservativeSpeciesTransport.solve(
          new String[] { "carrier", "tracer" }, profile, new double[] { 1.0 - tracerInlet, tracerInlet }, cellMasses,
          cellMasses, faceFlows, timeStepSeconds);
      assertTrue(report.isConverged(), report.getMessage());
      profile = report.getMassFractionProfile();
      double lagTimeSeconds = (step - 1) * timeStepSeconds;
      double idealOutlet = lagTimeSeconds >= residenceTimeSeconds
          && lagTimeSeconds < residenceTimeSeconds + pulseDurationSeconds ? 1.0 : 0.0;
      absoluteErrorSeconds += Math.abs(profile[1][cells - 1] - idealOutlet) * timeStepSeconds;
    }
    return absoluteErrorSeconds / pulseDurationSeconds;
  }

  private static double negativeBinomialStepResponse(int steps, int cell, double successProbability,
      double failureProbability) {
    if (steps <= 0) {
      return 0.0;
    }
    double term = Math.pow(successProbability, cell + 1);
    double sum = term;
    for (int delay = 0; delay < steps - 1; delay++) {
      term *= (double) (delay + cell + 1) / (delay + 1) * failureProbability;
      sum += term;
    }
    return sum;
  }

  private static double[][] uniformProfile(int cells, double tracerMassFraction) {
    double[][] profile = new double[2][cells];
    for (int cell = 0; cell < cells; cell++) {
      profile[0][cell] = 1.0 - tracerMassFraction;
      profile[1][cell] = tracerMassFraction;
    }
    return profile;
  }

  private static double[] filled(int length, double value) {
    double[] values = new double[length];
    for (int index = 0; index < length; index++) {
      values[index] = value;
    }
    return values;
  }

  private static final class PulseResult {
    private final int pulseSteps;
    private final int peakStep;
    private final double peakOutletMassFraction;
    private final double finalOutletMassFraction;
    private final double cumulativeInletTracerKg;
    private final double cumulativeOutletTracerKg;
    private final double globalInventoryResidualKg;

    private PulseResult(int pulseSteps, int peakStep, double peakOutletMassFraction, double finalOutletMassFraction,
        double cumulativeInletTracerKg, double cumulativeOutletTracerKg, double globalInventoryResidualKg) {
      this.pulseSteps = pulseSteps;
      this.peakStep = peakStep;
      this.peakOutletMassFraction = peakOutletMassFraction;
      this.finalOutletMassFraction = finalOutletMassFraction;
      this.cumulativeInletTracerKg = cumulativeInletTracerKg;
      this.cumulativeOutletTracerKg = cumulativeOutletTracerKg;
      this.globalInventoryResidualKg = globalInventoryResidualKg;
    }
  }
}
