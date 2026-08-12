package neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flowsolver.AxialDispersionBoundaryCondition;
import neqsim.fluidmechanics.flowsolver.ConstantAxialDispersion;
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
    assertEquals("none", report.getTransportDiagnostics().getPhysicalDispersionModelName());
    assertEquals(0.0, report.getTransportDiagnostics().getMaximumPhysicalAxialDispersionM2PerSecond(), 0.0);
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
  void constantPhysicalDispersionIsImplicitConservativeAndExplicitlyDiagnosed() {
    int cells = 8;
    double[][] initialProfile = uniformProfile(cells, 0.0);
    double[] cellMasses = filled(cells, 1.0);
    double[] faceFlows = filled(cells + 1, 0.1);

    OnePhaseSpeciesConservationReport report = ConservativeSpeciesTransport.solve(new String[] { "carrier", "tracer" },
        initialProfile, new double[] { 0.0, 1.0 }, cellMasses, cellMasses, faceFlows, 1.0,
        SpeciesAdvectionScheme.FIRST_ORDER_IMPLICIT, filled(cells, 1.0), new ConstantAxialDispersion(0.2));

    assertTrue(report.isConverged(), report.getMessage());
    assertTrue(report.getMaximumRelativeInventoryResidual() < 1.0e-13, report.getMessage());
    assertTrue(report.getMinimumMassFraction() >= 0.0, report.getMessage());
    assertTrue(report.getMaximumMassFraction() <= 1.0, report.getMessage());
    SpeciesTransportDiagnostics diagnostics = report.getTransportDiagnostics();
    assertTrue(diagnostics.isPhysicalDispersionIncluded());
    assertEquals("constant", diagnostics.getPhysicalDispersionModelName());
    assertArrayEquals(filled(cells, 0.2), diagnostics.getPhysicalAxialDispersionM2PerSecond(), 0.0);
    assertEquals(0.2, diagnostics.getMinimumPhysicalAxialDispersionM2PerSecond(), 0.0);
    assertEquals(0.2, diagnostics.getMaximumPhysicalAxialDispersionM2PerSecond(), 0.0);
    assertEquals(0.5, diagnostics.getCellPecletNumbers()[0], 1.0e-15);
    assertEquals(0.6, diagnostics.getMaximumCellPhysicalDispersionNumber(), 1.0e-15);
    assertEquals(1, diagnostics.getSubsteps(), "First-order advection-dispersion is fully implicit");
    assertEquals(AxialDispersionBoundaryCondition.DIRICHLET_INLET, diagnostics.getInletDispersionBoundaryCondition());
    assertEquals(AxialDispersionBoundaryCondition.ZERO_GRADIENT_OUTLET,
        diagnostics.getOutletDispersionBoundaryCondition());
    assertTrue(report.getInletBoundaryMassKg()[1] > faceFlows[0],
        "Dirichlet inlet must add the physical dispersive tracer flux to convection");
    assertEquals(faceFlows[cells] * report.getMassFractionProfile()[1][cells - 1], report.getOutletBoundaryMassKg()[1],
        1.0e-15, "Zero-gradient outlet must have no physical dispersive tracer flux");
    assertTrue(diagnostics.getMaximumFirstOrderImplicitNumericalDispersionM2PerSecond() > 0.0);
    assertTrue(diagnostics.toJson().contains("\"physicalDispersionModelName\": \"constant\""));
    double[] returnedCoefficients = diagnostics.getPhysicalAxialDispersionM2PerSecond();
    returnedCoefficients[0] = Double.NaN;
    assertEquals(0.2, diagnostics.getPhysicalAxialDispersionM2PerSecond()[0], 0.0);
  }

  @Test
  void physicalFaceConductanceUsesHalfCellSeriesResistanceOnNonuniformGrid() {
    double[][] initialProfile = uniformProfile(2, 0.0);
    double[] cellMasses = { 2.0, 8.0 };
    double[] faceFlows = { 0.1, 0.1, 0.1 };
    double[] cellLengths = { 1.0, 2.0 };

    OnePhaseSpeciesConservationReport report = ConservativeSpeciesTransport.solve(new String[] { "carrier", "tracer" },
        initialProfile, new double[] { 0.0, 1.0 }, cellMasses, cellMasses, faceFlows, 1.0,
        SpeciesAdvectionScheme.FIRST_ORDER_IMPLICIT, cellLengths, new ConstantAxialDispersion(0.5));

    assertTrue(report.isConverged(), report.getMessage());
    assertArrayEquals(new double[] { 1.5, 0.125 }, report.getTransportDiagnostics().getCellPhysicalDispersionNumbers(),
        1.0e-15);
    assertTrue(report.getMaximumRelativeInventoryResidual() < 1.0e-13, report.getMessage());
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
  void tvdSchemeSubstepsCombinedAdvectionAndPhysicalDispersionNumber() {
    int cells = 8;
    double[][] initialProfile = uniformProfile(cells, 0.0);
    double[] cellMasses = filled(cells, 1.0);
    double[] faceFlows = filled(cells + 1, 0.1);

    OnePhaseSpeciesConservationReport report = ConservativeSpeciesTransport.solve(new String[] { "carrier", "tracer" },
        initialProfile, new double[] { 0.0, 1.0 }, cellMasses, cellMasses, faceFlows, 1.0,
        SpeciesAdvectionScheme.TVD_VAN_LEER_SSP_RK2, filled(cells, 1.0), new ConstantAxialDispersion(1.0));

    assertTrue(report.isConverged(), report.getMessage());
    assertEquals(3.0, report.getTransportDiagnostics().getMaximumCellPhysicalDispersionNumber(), 1.0e-15);
    assertEquals(7, report.getTransportDiagnostics().getSubsteps());
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
  void constantPhysicalDispersionMatchesGaussianVarianceAndConvergesWithRefinement() {
    PhysicalDispersionResult coarse = runGaussianDispersion(100);
    PhysicalDispersionResult medium = runGaussianDispersion(200);
    PhysicalDispersionResult fine = runGaussianDispersion(400);
    double analyticalMeanM = 64.0;
    double analyticalVarianceM2 = 36.0;

    assertTrue(Math.abs(medium.varianceM2 - analyticalVarianceM2) < Math.abs(coarse.varianceM2 - analyticalVarianceM2));
    assertTrue(Math.abs(fine.varianceM2 - analyticalVarianceM2) < Math.abs(medium.varianceM2 - analyticalVarianceM2));
    assertEquals(analyticalMeanM, fine.meanM, 0.08);
    assertEquals(analyticalVarianceM2, fine.varianceM2, 0.3);
    assertEquals(0.0, fine.globalInventoryResidualKg, 2.0e-12);
    assertTrue(fine.maximumStepRelativeResidual < 1.0e-12);
    assertTrue(fine.minimumMassFraction >= -ConservativeSpeciesTransport.MASS_FRACTION_TOLERANCE);
    assertTrue(fine.maximumMassFraction <= 1.0 + ConservativeSpeciesTransport.MASS_FRACTION_TOLERANCE);
  }

  @Test
  void constantPhysicalDispersionMatchesFinitePulseSolution() {
    PhysicalDispersionResult result = runFinitePulseDispersion(400);

    assertTrue(result.normalizedL1Error < 0.035,
        "Expected the numerical finite pulse to match the analytical advection-diffusion profile; L1="
            + result.normalizedL1Error);
    assertEquals(0.0, result.globalInventoryResidualKg, 2.0e-12);
    assertTrue(result.maximumStepRelativeResidual < 1.0e-12);
    assertTrue(result.maximumSubsteps >= 1);
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

    OnePhaseSpeciesConservationReport missingLength = ConservativeSpeciesTransport.solve(names, validOld,
        new double[] { 0.8, 0.2 }, new double[] { 10.0 }, new double[] { 10.0 }, new double[] { 1.0, 1.0 }, 1.0,
        SpeciesAdvectionScheme.TVD_VAN_LEER_SSP_RK2, null, new ConstantAxialDispersion(1.0));
    assertEquals(OnePhaseSpeciesConservationReport.ConservationReason.INVALID_STATE, missingLength.getReason());
    assertTrue(missingLength.getMessage().contains("requires one positive length"));
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

  private static PhysicalDispersionResult runGaussianDispersion(int cells) {
    double domainLengthM = 200.0;
    double cellLengthM = domainLengthM / cells;
    double velocityMPerSecond = 0.2;
    double dispersionM2PerSecond = 0.5;
    double totalTimeSeconds = 20.0;
    double timeStepSeconds = 0.1 * cellLengthM * cellLengthM / dispersionM2PerSecond;
    int steps = (int) Math.round(totalTimeSeconds / timeStepSeconds);
    double lineDensityKgPerM = 1.0;
    double cellMassKg = lineDensityKgPerM * cellLengthM;
    double massFlowKgPerSecond = lineDensityKgPerM * velocityMPerSecond;
    double initialCenterM = 60.0;
    double initialVarianceM2 = 16.0;
    double amplitude = 0.2;
    double[][] profile = new double[2][cells];
    for (int cell = 0; cell < cells; cell++) {
      double positionM = (cell + 0.5) * cellLengthM;
      profile[1][cell] = amplitude
          * Math.exp(-0.5 * (positionM - initialCenterM) * (positionM - initialCenterM) / initialVarianceM2);
      profile[0][cell] = 1.0 - profile[1][cell];
    }
    return runPhysicalDispersion(profile, cellLengthM, cellMassKg, massFlowKgPerSecond, timeStepSeconds, steps,
        velocityMPerSecond, dispersionM2PerSecond, Double.NaN, Double.NaN, amplitude);
  }

  private static PhysicalDispersionResult runFinitePulseDispersion(int cells) {
    double domainLengthM = 200.0;
    double cellLengthM = domainLengthM / cells;
    double velocityMPerSecond = 0.2;
    double dispersionM2PerSecond = 0.5;
    double totalTimeSeconds = 20.0;
    double timeStepSeconds = 0.1 * cellLengthM * cellLengthM / dispersionM2PerSecond;
    int steps = (int) Math.round(totalTimeSeconds / timeStepSeconds);
    double lineDensityKgPerM = 1.0;
    double cellMassKg = lineDensityKgPerM * cellLengthM;
    double massFlowKgPerSecond = lineDensityKgPerM * velocityMPerSecond;
    double initialCenterM = 60.0;
    double halfWidthM = 5.0;
    double amplitude = 0.2;
    double[][] profile = new double[2][cells];
    for (int cell = 0; cell < cells; cell++) {
      double positionM = (cell + 0.5) * cellLengthM;
      profile[1][cell] = Math.abs(positionM - initialCenterM) <= halfWidthM ? amplitude : 0.0;
      profile[0][cell] = 1.0 - profile[1][cell];
    }
    return runPhysicalDispersion(profile, cellLengthM, cellMassKg, massFlowKgPerSecond, timeStepSeconds, steps,
        velocityMPerSecond, dispersionM2PerSecond, initialCenterM, halfWidthM, amplitude);
  }

  private static PhysicalDispersionResult runPhysicalDispersion(double[][] initialProfile, double cellLengthM,
      double cellMassKg, double massFlowKgPerSecond, double timeStepSeconds, int steps, double velocityMPerSecond,
      double dispersionM2PerSecond, double pulseCenterM, double pulseHalfWidthM, double pulseAmplitude) {
    int cells = initialProfile[0].length;
    double[][] profile = new double[][] { Arrays.copyOf(initialProfile[0], cells),
        Arrays.copyOf(initialProfile[1], cells) };
    double[] cellMasses = filled(cells, cellMassKg);
    double[] faceFlows = filled(cells + 1, massFlowKgPerSecond);
    double[] cellLengths = filled(cells, cellLengthM);
    ConstantAxialDispersion model = new ConstantAxialDispersion(dispersionM2PerSecond);
    double initialTracerInventoryKg = tracerInventory(profile[1], cellMassKg);
    double cumulativeInletTracerKg = 0.0;
    double cumulativeOutletTracerKg = 0.0;
    double maximumStepRelativeResidual = 0.0;
    double minimumMassFraction = Double.POSITIVE_INFINITY;
    double maximumMassFraction = Double.NEGATIVE_INFINITY;
    int maximumSubsteps = 0;

    for (int step = 0; step < steps; step++) {
      OnePhaseSpeciesConservationReport report = ConservativeSpeciesTransport.solve(
          new String[] { "carrier", "tracer" }, profile, new double[] { 1.0, 0.0 }, cellMasses, cellMasses, faceFlows,
          timeStepSeconds, SpeciesAdvectionScheme.TVD_VAN_LEER_SSP_RK2, cellLengths, model);
      assertTrue(report.isConverged(), "physical-dispersion step=" + step + ": " + report.getMessage());
      profile = report.getMassFractionProfile();
      cumulativeInletTracerKg += report.getInletBoundaryMassKg()[1];
      cumulativeOutletTracerKg += report.getOutletBoundaryMassKg()[1];
      maximumStepRelativeResidual = Math.max(maximumStepRelativeResidual, report.getMaximumRelativeInventoryResidual());
      minimumMassFraction = Math.min(minimumMassFraction, report.getMinimumMassFraction());
      maximumMassFraction = Math.max(maximumMassFraction, report.getMaximumMassFraction());
      maximumSubsteps = Math.max(maximumSubsteps, report.getTransportDiagnostics().getSubsteps());
      assertTrue(report.getTransportDiagnostics().isPhysicalDispersionIncluded());
      assertEquals(dispersionM2PerSecond,
          report.getTransportDiagnostics().getMaximumPhysicalAxialDispersionM2PerSecond(), 0.0);
    }

    double finalTracerInventoryKg = tracerInventory(profile[1], cellMassKg);
    double globalResidualKg = finalTracerInventoryKg - initialTracerInventoryKg - cumulativeInletTracerKg
        + cumulativeOutletTracerKg;
    double totalWeight = 0.0;
    double firstMoment = 0.0;
    for (int cell = 0; cell < cells; cell++) {
      double positionM = (cell + 0.5) * cellLengthM;
      totalWeight += profile[1][cell];
      firstMoment += positionM * profile[1][cell];
    }
    double meanM = firstMoment / totalWeight;
    double varianceM2 = 0.0;
    for (int cell = 0; cell < cells; cell++) {
      double positionM = (cell + 0.5) * cellLengthM;
      varianceM2 += (positionM - meanM) * (positionM - meanM) * profile[1][cell];
    }
    varianceM2 /= totalWeight;

    double normalizedL1Error = Double.NaN;
    if (Double.isFinite(pulseCenterM)) {
      double elapsedTimeSeconds = steps * timeStepSeconds;
      double advectedCenterM = pulseCenterM + velocityMPerSecond * elapsedTimeSeconds;
      double denominator = Math.sqrt(4.0 * dispersionM2PerSecond * elapsedTimeSeconds);
      double absoluteError = 0.0;
      double analyticalMass = 0.0;
      for (int cell = 0; cell < cells; cell++) {
        double positionM = (cell + 0.5) * cellLengthM;
        double analytical = 0.5 * pulseAmplitude
            * (errorFunction((positionM - advectedCenterM + pulseHalfWidthM) / denominator)
                - errorFunction((positionM - advectedCenterM - pulseHalfWidthM) / denominator));
        absoluteError += Math.abs(profile[1][cell] - analytical);
        analyticalMass += analytical;
      }
      normalizedL1Error = absoluteError / analyticalMass;
    }
    return new PhysicalDispersionResult(meanM, varianceM2, normalizedL1Error, globalResidualKg,
        maximumStepRelativeResidual, minimumMassFraction, maximumMassFraction, maximumSubsteps);
  }

  private static double tracerInventory(double[] tracerMassFraction, double cellMassKg) {
    double inventoryKg = 0.0;
    for (double value : tracerMassFraction) {
      inventoryKg += cellMassKg * value;
    }
    return inventoryKg;
  }

  private static double errorFunction(double value) {
    double sign = value < 0.0 ? -1.0 : 1.0;
    double x = Math.abs(value);
    double t = 1.0 / (1.0 + 0.3275911 * x);
    double polynomial = (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592)
        * t;
    return sign * (1.0 - polynomial * Math.exp(-x * x));
  }

  private static final class PhysicalDispersionResult {
    private final double meanM;
    private final double varianceM2;
    private final double normalizedL1Error;
    private final double globalInventoryResidualKg;
    private final double maximumStepRelativeResidual;
    private final double minimumMassFraction;
    private final double maximumMassFraction;
    private final int maximumSubsteps;

    private PhysicalDispersionResult(double meanM, double varianceM2, double normalizedL1Error,
        double globalInventoryResidualKg, double maximumStepRelativeResidual, double minimumMassFraction,
        double maximumMassFraction, int maximumSubsteps) {
      this.meanM = meanM;
      this.varianceM2 = varianceM2;
      this.normalizedL1Error = normalizedL1Error;
      this.globalInventoryResidualKg = globalInventoryResidualKg;
      this.maximumStepRelativeResidual = maximumStepRelativeResidual;
      this.minimumMassFraction = minimumMassFraction;
      this.maximumMassFraction = maximumMassFraction;
      this.maximumSubsteps = maximumSubsteps;
    }
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
