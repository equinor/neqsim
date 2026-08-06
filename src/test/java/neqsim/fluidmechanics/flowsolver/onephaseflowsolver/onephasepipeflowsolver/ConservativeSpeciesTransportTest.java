package neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flowsolver.SpeciesAdvectionScheme;

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
    assertEquals(SpeciesAdvectionScheme.FIRST_ORDER_IMPLICIT, report.getTransportDiagnostics().getScheme());
    assertEquals(0.5, report.getTransportDiagnostics().getMaximumCellCourantNumber(), 0.0);
    assertEquals(1, report.getTransportDiagnostics().getSubsteps());
  }

  @Test
  void tvdSchemeClosesVariableMassStepWithoutNewExtrema() {
    String[] names = { "methane", "nitrogen", "carbon dioxide" };
    double[][] oldMassFraction = { { 0.90, 0.85, 0.80 }, { 0.08, 0.11, 0.15 }, { 0.02, 0.04, 0.05 } };
    double[] inletMassFraction = { 0.84, 0.10, 0.06 };
    double[] oldCellMassKg = { 10.0, 12.0, 8.0 };
    double[] faceMassFlowKgPerSecond = { 1.1, 1.0, 0.9, 0.8 };
    double timeStepSeconds = 1.0;
    double[] newCellMassKg = { 10.1, 12.1, 8.1 };

    OnePhaseSpeciesConservationReport report = ConservativeSpeciesTransport.solve(names, oldMassFraction,
        inletMassFraction, oldCellMassKg, newCellMassKg, faceMassFlowKgPerSecond, timeStepSeconds,
        SpeciesAdvectionScheme.TVD_VAN_LEER_SSP_RK2, new double[] { 100.0, 120.0, 80.0 });

    assertTrue(report.isConverged(), report.getMessage());
    assertTrue(report.getMinimumMassFraction() >= -ConservativeSpeciesTransport.MASS_FRACTION_TOLERANCE,
        report.getMessage());
    assertTrue(report.getMaximumMassFraction() <= 1.0 + ConservativeSpeciesTransport.MASS_FRACTION_TOLERANCE,
        report.getMessage());
    assertEquals(0.0, report.getMaximumMassFractionSumError(), 0.0);
    assertTrue(report.getMaximumRelativeInventoryResidual() < 1.0e-13, report.getMessage());
    assertEquals(SpeciesAdvectionScheme.TVD_VAN_LEER_SSP_RK2, report.getTransportDiagnostics().getScheme());
    assertTrue(report.getTransportDiagnostics().getMaximumFirstOrderImplicitNumericalDispersionM2PerSecond() > 0.0);
    assertFalse(report.getTransportDiagnostics().isPhysicalDispersionIncluded());
    assertTrue(report.getTransportDiagnostics().toJson().contains("\"cellPecletNumbers\""));
    double[] returnedCourantNumbers = report.getTransportDiagnostics().getCellCourantNumbers();
    returnedCourantNumbers[0] = Double.NaN;
    assertTrue(Double.isFinite(report.getTransportDiagnostics().getCellCourantNumbers()[0]));
    assertTrue(report.toJson().contains("\"transportDiagnostics\""));
    double[][] result = report.getMassFractionProfile();
    for (int component = 0; component < names.length; component++) {
      double minimumInput = inletMassFraction[component];
      double maximumInput = inletMassFraction[component];
      for (double value : oldMassFraction[component]) {
        minimumInput = Math.min(minimumInput, value);
        maximumInput = Math.max(maximumInput, value);
      }
      for (double value : result[component]) {
        assertTrue(value >= minimumInput - ConservativeSpeciesTransport.MASS_FRACTION_TOLERANCE,
            "New minimum for " + names[component] + ": " + value);
        assertTrue(value <= maximumInput + ConservativeSpeciesTransport.MASS_FRACTION_TOLERANCE,
            "New maximum for " + names[component] + ": " + value);
      }
    }
  }

  @Test
  void tvdSchemeSubstepsHighCourantStepsConservatively() {
    int cells = 8;
    double[][] initialProfile = uniformProfile(cells, 0.0);
    double[] cellMasses = filled(cells, 10.0);
    double[] faceFlows = filled(cells + 1, 1.0);

    OnePhaseSpeciesConservationReport report = ConservativeSpeciesTransport.solve(new String[] { "carrier", "tracer" },
        initialProfile, new double[] { 0.0, 1.0 }, cellMasses, cellMasses, faceFlows, 10.0,
        SpeciesAdvectionScheme.TVD_VAN_LEER_SSP_RK2, filled(cells, 100.0));

    assertTrue(report.isConverged(), report.getMessage());
    assertEquals(1.0, report.getTransportDiagnostics().getMaximumCellCourantNumber(), 0.0);
    assertEquals(3, report.getTransportDiagnostics().getSubsteps());
    assertTrue(report.getMinimumMassFraction() >= 0.0, report.getMessage());
    assertTrue(report.getMaximumMassFraction() <= 1.0, report.getMessage());
    assertTrue(report.getMaximumRelativeInventoryResidual() < 1.0e-13, report.getMessage());
  }

  @Test
  void tvdSchemePreservesLongPipelinePulseMoreSharplyThanImplicitUpwind() {
    SchemePulseResult implicit = runSchemePulse(SpeciesAdvectionScheme.FIRST_ORDER_IMPLICIT, 24, 1800.0, 48.8 * 3600.0,
        12.0 * 3600.0, 5.0 * 48.8 * 3600.0, 700000.0);
    SchemePulseResult tvd = runSchemePulse(SpeciesAdvectionScheme.TVD_VAN_LEER_SSP_RK2, 24, 1800.0, 48.8 * 3600.0,
        12.0 * 3600.0, 5.0 * 48.8 * 3600.0, 700000.0);

    assertEquals(0.0, implicit.globalInventoryResidualKg, 1.0e-8);
    assertEquals(0.0, tvd.globalInventoryResidualKg, 1.0e-8);
    assertTrue(tvd.minimumMassFraction >= -ConservativeSpeciesTransport.MASS_FRACTION_TOLERANCE);
    assertTrue(tvd.maximumMassFraction <= 1.0 + ConservativeSpeciesTransport.MASS_FRACTION_TOLERANCE);
    assertTrue(tvd.peakOutletMassFraction > implicit.peakOutletMassFraction + 0.15,
        "Expected less pulse attenuation: implicit=" + implicit.peakOutletMassFraction + ", TVD="
            + tvd.peakOutletMassFraction);
    assertTrue(tvd.l1Error < 0.75 * implicit.l1Error,
        "Expected smaller L1 error: implicit=" + implicit.l1Error + ", TVD=" + tvd.l1Error);
    assertTrue(tvd.l2Error < 0.75 * implicit.l2Error,
        "Expected smaller L2 error: implicit=" + implicit.l2Error + ", TVD=" + tvd.l2Error);
    assertEquals(1, tvd.maximumSubsteps);
    assertTrue(tvd.maximumFirstOrderDispersionM2PerSecond > 7.0e4);
  }

  @Test
  void tvdPulseConvergesUnderJointGridAndTimeRefinement() {
    SchemePulseResult coarse = runSchemePulse(SpeciesAdvectionScheme.TVD_VAN_LEER_SSP_RK2, 12, 120.0, 3600.0, 900.0,
        10800.0, 12000.0);
    SchemePulseResult medium = runSchemePulse(SpeciesAdvectionScheme.TVD_VAN_LEER_SSP_RK2, 24, 60.0, 3600.0, 900.0,
        10800.0, 12000.0);
    SchemePulseResult fine = runSchemePulse(SpeciesAdvectionScheme.TVD_VAN_LEER_SSP_RK2, 48, 30.0, 3600.0, 900.0,
        10800.0, 12000.0);

    assertTrue(medium.l1Error < coarse.l1Error,
        "Expected medium grid to improve L1 error: coarse=" + coarse.l1Error + ", medium=" + medium.l1Error);
    assertTrue(fine.l1Error < medium.l1Error,
        "Expected fine grid to improve L1 error: medium=" + medium.l1Error + ", fine=" + fine.l1Error);
  }

  @Test
  void repeatedStepMatchesAnalyticalProfileAndMassResidenceTime() {
    double[] firstProfile = runRepeatedStep(2.5, 36);
    double[] repeatedProfile = runRepeatedStep(2.5, 36);
    assertArrayEquals(firstProfile, repeatedProfile, 0.0, "Repeated runs must be deterministic");

    assertEquals(120.0, calculateResidenceTime(2.5), 2.0e-10);
    assertEquals(120.0, calculateResidenceTime(5.0), 2.0e-10);
  }

  private static double[] runRepeatedStep(double timeStepSeconds, int steps) {
    int cells = 12;
    double cellMassKg = 10.0;
    double massFlowKgPerSecond = 1.0;
    double[][] profile = uniformInitialProfile(cells);
    double[] cellMass = constantArray(cells, cellMassKg);
    double[] faceFlow = constantArray(cells + 1, massFlowKgPerSecond);

    for (int step = 1; step <= steps; step++) {
      OnePhaseSpeciesConservationReport report = ConservativeSpeciesTransport.solve(
          new String[] { "carrier", "tracer" }, profile, new double[] { 0.0, 1.0 }, cellMass, cellMass, faceFlow,
          timeStepSeconds);
      assertTrue(report.isConverged(), report.getMessage());
      assertTrue(report.getMaximumRelativeInventoryResidual() < 1.0e-13, report.getMessage());
      assertEquals(0.0, report.getMaximumMassFractionSumError(), 0.0);
      profile = report.getMassFractionProfile();
      for (int cell = 0; cell < cells; cell++) {
        assertEquals(analyticalTracer(step, cell, cellMassKg, massFlowKgPerSecond, timeStepSeconds), profile[1][cell],
            5.0e-14);
      }
    }
    return profile[1];
  }

  private static double calculateResidenceTime(double timeStepSeconds) {
    int cells = 12;
    double cellMassKg = 10.0;
    double massFlowKgPerSecond = 1.0;
    double[][] profile = uniformInitialProfile(cells);
    double[] cellMass = constantArray(cells, cellMassKg);
    double[] faceFlow = constantArray(cells + 1, massFlowKgPerSecond);
    double previousOutlet = 0.0;
    double responseMass = 0.0;
    double responseFirstMoment = 0.0;

    for (int step = 0; step < 500; step++) {
      OnePhaseSpeciesConservationReport report = ConservativeSpeciesTransport.solve(
          new String[] { "carrier", "tracer" }, profile, new double[] { 0.0, 1.0 }, cellMass, cellMass, faceFlow,
          timeStepSeconds);
      assertTrue(report.isConverged(), report.getMessage());
      assertTrue(report.getMaximumRelativeInventoryResidual() < 1.0e-13, report.getMessage());
      assertEquals(0.0, report.getMaximumMassFractionSumError(), 0.0);
      profile = report.getMassFractionProfile();
      double outlet = profile[1][cells - 1];
      double responseIncrement = outlet - previousOutlet;
      responseMass += responseIncrement;
      responseFirstMoment += (step + 1.0) * timeStepSeconds * responseIncrement;
      previousOutlet = outlet;
    }
    assertEquals(1.0, responseMass, 1.0e-14, "Step-response increments must integrate to one");
    double inletEventFirstMoment = timeStepSeconds;
    return responseFirstMoment / responseMass - inletEventFirstMoment;
  }

  private static double analyticalTracer(int steps, int cell, double cellMassKg, double massFlowKgPerSecond,
      double timeStepSeconds) {
    double denominator = cellMassKg + timeStepSeconds * massFlowKgPerSecond;
    double spatialFactor = timeStepSeconds * massFlowKgPerSecond / denominator;
    double temporalFactor = cellMassKg / denominator;
    double term = Math.pow(temporalFactor, steps);
    double carrier = term;
    for (int offset = 1; offset <= cell; offset++) {
      term *= spatialFactor * (steps + offset - 1.0) / offset;
      carrier += term;
    }
    return 1.0 - carrier;
  }

  private static double[][] uniformInitialProfile(int cells) {
    double[][] profile = new double[][] { new double[cells], new double[cells] };
    Arrays.fill(profile[0], 1.0);
    return profile;
  }

  private static double[] constantArray(int length, double value) {
    double[] values = new double[length];
    Arrays.fill(values, value);
    return values;
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

  private static SchemePulseResult runSchemePulse(SpeciesAdvectionScheme scheme, int cells, double timeStepSeconds,
      double residenceTimeSeconds, double pulseDurationSeconds, double totalTimeSeconds, double pipeLengthM) {
    double massFlowKgPerSecond = 1.0;
    double cellMassKg = residenceTimeSeconds * massFlowKgPerSecond / cells;
    int pulseSteps = (int) Math.round(pulseDurationSeconds / timeStepSeconds);
    int totalSteps = (int) Math.round(totalTimeSeconds / timeStepSeconds);
    double[][] profile = uniformProfile(cells, 0.0);
    double[] cellMasses = filled(cells, cellMassKg);
    double[] faceFlows = filled(cells + 1, massFlowKgPerSecond);
    double[] cellLengths = filled(cells, pipeLengthM / cells);
    double cumulativeInletTracerKg = 0.0;
    double cumulativeOutletTracerKg = 0.0;
    double peakOutletMassFraction = 0.0;
    double l1ErrorSeconds = 0.0;
    double l2ErrorSeconds = 0.0;
    double minimumMassFraction = Double.POSITIVE_INFINITY;
    double maximumMassFraction = Double.NEGATIVE_INFINITY;
    int maximumSubsteps = 0;
    double maximumFirstOrderDispersion = 0.0;

    for (int step = 1; step <= totalSteps; step++) {
      double tracerInlet = step <= pulseSteps ? 1.0 : 0.0;
      OnePhaseSpeciesConservationReport report = ConservativeSpeciesTransport.solve(
          new String[] { "carrier", "tracer" }, profile, new double[] { 1.0 - tracerInlet, tracerInlet }, cellMasses,
          cellMasses, faceFlows, timeStepSeconds, scheme, cellLengths);
      assertTrue(report.isConverged(), "scheme=" + scheme + ", step=" + step + ": " + report.getMessage());
      profile = report.getMassFractionProfile();
      double outlet = profile[1][cells - 1];
      double lagTimeSeconds = (step - 1) * timeStepSeconds;
      double idealOutlet = lagTimeSeconds >= residenceTimeSeconds
          && lagTimeSeconds < residenceTimeSeconds + pulseDurationSeconds ? 1.0 : 0.0;
      double error = outlet - idealOutlet;
      l1ErrorSeconds += Math.abs(error) * timeStepSeconds;
      l2ErrorSeconds += error * error * timeStepSeconds;
      peakOutletMassFraction = Math.max(peakOutletMassFraction, outlet);
      minimumMassFraction = Math.min(minimumMassFraction, report.getMinimumMassFraction());
      maximumMassFraction = Math.max(maximumMassFraction, report.getMaximumMassFraction());
      cumulativeInletTracerKg += report.getInletBoundaryMassKg()[1];
      cumulativeOutletTracerKg += report.getOutletBoundaryMassKg()[1];
      maximumSubsteps = Math.max(maximumSubsteps, report.getTransportDiagnostics().getSubsteps());
      maximumFirstOrderDispersion = Math.max(maximumFirstOrderDispersion,
          report.getTransportDiagnostics().getMaximumFirstOrderImplicitNumericalDispersionM2PerSecond());
    }

    double finalTracerInventoryKg = 0.0;
    for (int cell = 0; cell < cells; cell++) {
      finalTracerInventoryKg += cellMasses[cell] * profile[1][cell];
    }
    double globalResidualKg = finalTracerInventoryKg - cumulativeInletTracerKg + cumulativeOutletTracerKg;
    return new SchemePulseResult(peakOutletMassFraction, l1ErrorSeconds / pulseDurationSeconds,
        Math.sqrt(l2ErrorSeconds / pulseDurationSeconds), minimumMassFraction, maximumMassFraction, globalResidualKg,
        maximumSubsteps, maximumFirstOrderDispersion);
  }

  private static final class SchemePulseResult {
    private final double peakOutletMassFraction;
    private final double l1Error;
    private final double l2Error;
    private final double minimumMassFraction;
    private final double maximumMassFraction;
    private final double globalInventoryResidualKg;
    private final int maximumSubsteps;
    private final double maximumFirstOrderDispersionM2PerSecond;

    private SchemePulseResult(double peakOutletMassFraction, double l1Error, double l2Error, double minimumMassFraction,
        double maximumMassFraction, double globalInventoryResidualKg, int maximumSubsteps,
        double maximumFirstOrderDispersionM2PerSecond) {
      this.peakOutletMassFraction = peakOutletMassFraction;
      this.l1Error = l1Error;
      this.l2Error = l2Error;
      this.minimumMassFraction = minimumMassFraction;
      this.maximumMassFraction = maximumMassFraction;
      this.globalInventoryResidualKg = globalInventoryResidualKg;
      this.maximumSubsteps = maximumSubsteps;
      this.maximumFirstOrderDispersionM2PerSecond = maximumFirstOrderDispersionM2PerSecond;
    }
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
