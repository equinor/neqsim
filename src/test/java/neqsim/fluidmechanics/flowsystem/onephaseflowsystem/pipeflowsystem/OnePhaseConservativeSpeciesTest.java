package neqsim.fluidmechanics.flowsystem.onephaseflowsystem.pipeflowsystem;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.fluidmechanics.flowsolver.onephaseflowsolver.onephasepipeflowsolver.OnePhaseSpeciesConservationReport;
import neqsim.fluidmechanics.geometrydefinitions.GeometryDefinitionInterface;
import neqsim.fluidmechanics.geometrydefinitions.pipe.PipeData;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests conservative one-phase species transport coupled to hydraulic/EOS state. */
class OnePhaseConservativeSpeciesTest extends neqsim.NeqSimTest {
  private static final double TEMPERATURE_K = 288.15;
  private static final double PRESSURE_BARA = 70.0;
  private static final double MASS_FLOW_KG_PER_SECOND = 50.0;

  @Test
  void compositionStepClosesEveryComponentAndSynchronizesThermodynamics() {
    PipeFlowSystem pipe = runCompositionStep(40, 30.0);
    OnePhaseSpeciesConservationReport report = pipe.getSpeciesConservationReport();

    assertEquals(OnePhaseSpeciesConservationReport.ConservationReason.CONVERGED, report.getReason(),
        report.getMessage());
    assertTrue(pipe.getConvergenceReport().isConverged(), pipe.getConvergenceReport().getMessage());
    assertTrue(pipe.getConvergenceReport().getMaximumRelativeDensityResidual() <= pipe.getConvergenceReport()
        .getDensityRelativeTolerance());
    assertTrue(report.getMaximumRelativeInventoryResidual() < 1.0e-8, report.getMessage());
    assertTrue(report.getMinimumMassFraction() >= 0.0, report.getMessage());
    assertTrue(report.getMaximumMassFraction() <= 1.0, report.getMessage());
    assertTrue(report.getMaximumMassFractionSumError() <= 1.0e-12, report.getMessage());
    assertTrue(report.getMaximumThermodynamicMassFractionError() <= 1.0e-10, report.getMessage());
    assertTrue(report.getCouplingIterations() > 0, report.getMessage());
    assertEquals(report.getCouplingIterations(), report.getMaximumMassFractionChangeHistory().length);
    assertEquals(report.getCouplingIterations(), report.getDensityResidualHistory().length);
    assertTrue(report.getMaximumMassFractionChangeHistory()[report.getCouplingIterations() - 1] <= 1.0e-10,
        report.getMessage());
    assertTrue(report.getDensityResidualHistory()[report.getCouplingIterations() - 1] <= pipe.getConvergenceReport()
        .getDensityRelativeTolerance(), report.getMessage());

    int nitrogen = 1;
    assertEquals(report.getFinalInventoryKg()[nitrogen] - report.getInitialInventoryKg()[nitrogen],
        report.getInletBoundaryMassKg()[nitrogen] - report.getOutletBoundaryMassKg()[nitrogen],
        Math.max(1.0, report.getInitialInventoryKg()[nitrogen]) * 1.0e-8);
    double firstCellNitrogen = pipe.getNode(1).getBulkSystem().getPhase(0).getComponent(nitrogen).getx();
    assertTrue(firstCellNitrogen > 0.05 && firstCellNitrogen < 0.20,
        "The bounded inlet front must enter the first physical cell: " + firstCellNitrogen);
  }

  @Test
  void conservativeSpeciesStepIsDeterministicAndTimestepSensitive() {
    PipeFlowSystem thirtySecondA = runCompositionStep(12, 30.0);
    PipeFlowSystem thirtySecondB = runCompositionStep(12, 30.0);
    PipeFlowSystem fifteenSecond = runCompositionStep(12, 15.0);

    OnePhaseSpeciesConservationReport reportA = thirtySecondA.getSpeciesConservationReport();
    OnePhaseSpeciesConservationReport reportB = thirtySecondB.getSpeciesConservationReport();
    assertArrayEquals(reportA.getFinalInventoryKg(), reportB.getFinalInventoryKg(), 0.0);
    assertArrayEquals(reportA.getInventoryResidualKg(), reportB.getInventoryResidualKg(), 0.0);
    assertArrayEquals(reportA.getMassFractionProfile()[1], reportB.getMassFractionProfile()[1], 0.0);

    double thirtySecondFront = thirtySecondA.getNode(1).getBulkSystem().getPhase(0).getComponent(1).getx();
    double fifteenSecondFront = fifteenSecond.getNode(1).getBulkSystem().getPhase(0).getComponent(1).getx();
    assertTrue(thirtySecondFront > fifteenSecondFront,
        "A longer first-order implicit step must advance more inlet tracer mass.");
    assertTrue(fifteenSecond.getSpeciesConservationReport().isConverged());
  }

  @Test
  void optInSpeciesTransportFailsLoudlyForReversedFlowWithoutLegacyStrictFlag() {
    PipeFlowSystem pipe = createInitializedPipe(3);
    pipe.setConservativeSpeciesTransport(true);
    pipe.getTimeSeries().setTimes(new double[] { 0.0, 30.0 });
    pipe.getTimeSeries().setInletThermoSystems(new SystemInterface[] { createGas(0.80, 0.20) });
    pipe.getTimeSeries().setNumberOfTimeStepsInInterval(1);
    pipe.getNode(2).setVelocityIn(-Math.abs(pipe.getNode(2).getVelocityIn().doubleValue()));

    IllegalStateException exception = assertThrows(IllegalStateException.class, () -> pipe.solveTransient(1));
    assertTrue(exception.getMessage().contains("supports positive flow only"));
  }

  @Test
  void reportDoesNotRemainStaleAfterNonConservativeSolverPath() {
    PipeFlowSystem pipe = runCompositionStep(3, 30.0);
    assertTrue(pipe.getSpeciesConservationReport().isConverged());

    pipe.solveTransient(0);

    assertEquals(OnePhaseSpeciesConservationReport.ConservationReason.NOT_RUN,
        pipe.getSpeciesConservationReport().getReason());
  }

  @Test
  @Tag("slow")
  void thirtyMinutePulseBreaksThroughRecoversAndClosesCumulativeInventory() {
    IntegratedPulseResult pulse = runIntegratedPulse();
    IntegratedPulseResult repeatedPulse = runIntegratedPulse();

    assertRawBitsEqual(pulse.outletNitrogenHistory, repeatedPulse.outletNitrogenHistory,
        "Independent coupled pulse runs must have bit-identical outlet histories.");
    assertRawBitsEqual(pulse.densityResidualHistory, repeatedPulse.densityResidualHistory,
        "Independent coupled pulse runs must have bit-identical EOS/FV density residuals.");
    assertArrayEquals(pulse.couplingIterationHistory, repeatedPulse.couplingIterationHistory,
        "Independent coupled pulse runs must perform identical fixed-point work.");
    assertRawBitsEqual(pulse.finalInventoryKg, repeatedPulse.finalInventoryKg,
        "Independent coupled pulse runs must have bit-identical final inventories.");
    assertEquals(pulse.finalMassFractionProfile.length, repeatedPulse.finalMassFractionProfile.length);
    for (int component = 0; component < pulse.finalMassFractionProfile.length; component++) {
      assertRawBitsEqual(pulse.finalMassFractionProfile[component], repeatedPulse.finalMassFractionProfile[component],
          "Independent coupled pulse runs must have bit-identical final profiles.");
    }
    assertRawBitsEqual(pulse.cumulativeInletKg, repeatedPulse.cumulativeInletKg,
        "Independent coupled pulse runs must have bit-identical cumulative inlet mass.");
    assertRawBitsEqual(pulse.cumulativeOutletKg, repeatedPulse.cumulativeOutletKg,
        "Independent coupled pulse runs must have bit-identical cumulative outlet mass.");

    double eventAmplitude = pulse.pulseInletMassFraction - pulse.baselineOutlet;
    double cumulativeScaleKg = Math.max(Math.max(pulse.initialInventoryKg, pulse.cumulativeInletKg), 1.0);

    assertTrue(eventAmplitude > 0.0);
    assertTrue(pulse.pulseEndOutlet > pulse.baselineOutlet + 0.70 * eventAmplitude,
        "The 30-minute event must break through before the inlet returns to baseline.");
    assertTrue(pulse.peakOutlet > pulse.baselineOutlet + 0.70 * eventAmplitude);
    assertEquals(pulse.baselineOutlet, pulse.recoveredOutlet, 2.0e-3 * eventAmplitude,
        "The outlet composition must recover after purging the pulse.");
    assertEquals(0.0, pulse.cumulativeResidualKg, cumulativeScaleKg * 1.0e-7,
        "Cumulative nitrogen inventory must equal integrated inlet minus outlet mass.");
  }

  @Test
  @Tag("slow")
  void coupledPulseGridAndTimestepRefinementReducesOutletDifference() {
    IntegratedPulseResult coarse = runIntegratedPulse(6, 120.0);
    IntegratedPulseResult medium = runIntegratedPulse(12, 60.0);
    IntegratedPulseResult fine = runIntegratedPulse(24, 30.0);

    assertPulseEngineeringGates(coarse);
    assertPulseEngineeringGates(medium);
    assertPulseEngineeringGates(fine);

    double coarseToMedium = commonTimeMeanAbsoluteDifference(coarse.outletNitrogenHistory, 120.0,
        medium.outletNitrogenHistory, 60.0, 120.0);
    double mediumToFine = commonTimeMeanAbsoluteDifference(medium.outletNitrogenHistory, 60.0,
        fine.outletNitrogenHistory, 30.0, 120.0);

    assertTrue(coarseToMedium > 0.0, "The refinement study must resolve a non-zero discretization difference.");
    assertTrue(mediumToFine < coarseToMedium,
        "Joint grid/timestep refinement must reduce the common-time outlet difference: coarse-to-medium="
            + coarseToMedium + ", medium-to-fine=" + mediumToFine);
  }

  private static IntegratedPulseResult runIntegratedPulse() {
    return runIntegratedPulse(12, 60.0);
  }

  private static IntegratedPulseResult runIntegratedPulse(int nodes, double timeStepSeconds) {
    int nitrogen = 1;
    int pulseSteps = exactStepCount(1800.0, timeStepSeconds);
    int recoverySteps = exactStepCount(3600.0, timeStepSeconds);
    PipeFlowSystem pipe = createInitializedPipe(nodes, 3000.0);
    pipe.setConservativeSpeciesTransport(true);
    pipe.setFailOnNonConvergence(true);
    SystemInterface baselineGas = createGas(0.95, 0.05);
    SystemInterface pulseGas = createGas(0.80, 0.20);

    OnePhaseSpeciesConservationReport baseline = runTransientStepWithContext(pipe, baselineGas.clone(), timeStepSeconds,
        nodes, -1, false);
    assertTrue(baseline.isConverged(), baseline.getMessage());
    double baselineOutlet = last(baseline.getMassFractionProfile()[nitrogen]);
    double initialInventoryKg = baseline.getFinalInventoryKg()[nitrogen];
    double cumulativeInletKg = 0.0;
    double cumulativeOutletKg = 0.0;
    double pulseInletMassFraction = Double.NaN;
    double pulseEndOutlet = Double.NaN;
    double peakOutlet = baselineOutlet;
    double[] outletNitrogenHistory = new double[pulseSteps + recoverySteps];
    double[] densityResidualHistory = new double[pulseSteps + recoverySteps];
    int[] couplingIterationHistory = new int[pulseSteps + recoverySteps];
    OnePhaseSpeciesConservationReport report = baseline;

    for (int step = 0; step < pulseSteps + recoverySteps; step++) {
      boolean pulseActive = step < pulseSteps;
      SystemInterface inlet = pulseActive ? pulseGas.clone() : baselineGas.clone();
      report = runTransientStepWithContext(pipe, inlet, timeStepSeconds, nodes, step, pulseActive);

      assertTrue(report.isConverged(), report.getMessage());
      assertTrue(pipe.getConvergenceReport().isConverged(), pipe.getConvergenceReport().getMessage());
      assertTrue(report.getMaximumRelativeInventoryResidual() <= 1.0e-8, report.getMessage());
      assertTrue(report.getMaximumThermodynamicMassFractionError() <= 1.0e-10, report.getMessage());
      cumulativeInletKg += report.getInletBoundaryMassKg()[nitrogen];
      cumulativeOutletKg += report.getOutletBoundaryMassKg()[nitrogen];
      double outlet = last(report.getMassFractionProfile()[nitrogen]);
      outletNitrogenHistory[step] = outlet;
      densityResidualHistory[step] = pipe.getConvergenceReport().getMaximumRelativeDensityResidual();
      couplingIterationHistory[step] = report.getCouplingIterations();
      peakOutlet = Math.max(peakOutlet, outlet);
      if (step == 0) {
        assertEquals(initialInventoryKg, report.getInitialInventoryKg()[nitrogen],
            Math.max(1.0, initialInventoryKg) * 1.0e-10);
        pulseInletMassFraction = report.getInletBoundaryMassKg()[nitrogen] / sum(report.getInletBoundaryMassKg());
      }
      if (step == pulseSteps - 1) {
        pulseEndOutlet = outlet;
      }
    }

    double finalInventoryKg = report.getFinalInventoryKg()[nitrogen];
    double cumulativeResidualKg = finalInventoryKg - initialInventoryKg - cumulativeInletKg + cumulativeOutletKg;
    double recoveredOutlet = last(report.getMassFractionProfile()[nitrogen]);
    return new IntegratedPulseResult(baselineOutlet, initialInventoryKg, cumulativeInletKg, cumulativeOutletKg,
        pulseInletMassFraction, pulseEndOutlet, peakOutlet, recoveredOutlet, cumulativeResidualKg,
        outletNitrogenHistory, densityResidualHistory, couplingIterationHistory, report.getFinalInventoryKg(),
        report.getMassFractionProfile());
  }

  static PipeFlowSystem runCompositionStep(int nodes, double timeStep) {
    PipeFlowSystem pipe = createInitializedPipe(nodes);
    pipe.setConservativeSpeciesTransport(true);
    pipe.setFailOnNonConvergence(true);
    pipe.getTimeSeries().setTimes(new double[] { 0.0, timeStep });
    pipe.getTimeSeries().setInletThermoSystems(new SystemInterface[] { createGas(0.80, 0.20) });
    pipe.getTimeSeries().setNumberOfTimeStepsInInterval(1);

    assertDoesNotThrow(() -> pipe.solveTransient(1));
    return pipe;
  }

  private static OnePhaseSpeciesConservationReport runTransientStepWithContext(PipeFlowSystem pipe,
      SystemInterface inlet, double timeStepSeconds, int nodes, int step, boolean pulseActive) {
    try {
      return runTransientStep(pipe, inlet, timeStepSeconds);
    } catch (AssertionError exception) {
      String phase = step < 0 ? "baseline" : (pulseActive ? "pulse" : "recovery");
      throw new AssertionError("Coupled pulse failed for nodes=" + nodes + ", timestep=" + timeStepSeconds
          + " s, phase=" + phase + ", event step=" + step + ": " + exception.getMessage(), exception);
    }
  }

  private static OnePhaseSpeciesConservationReport runTransientStep(PipeFlowSystem pipe, SystemInterface inlet,
      double timeStepSeconds) {
    pipe.getTimeSeries().setTimes(new double[] { 0.0, timeStepSeconds });
    pipe.getTimeSeries().setInletThermoSystems(new SystemInterface[] { inlet });
    pipe.getTimeSeries().setNumberOfTimeStepsInInterval(1);
    assertDoesNotThrow(() -> pipe.solveTransient(1));
    return pipe.getSpeciesConservationReport();
  }

  private static PipeFlowSystem createInitializedPipe(int nodes) {
    return createInitializedPipe(nodes, 15000.0);
  }

  private static PipeFlowSystem createInitializedPipe(int nodes, double lengthMeters) {
    PipeFlowSystem pipe = new PipeFlowSystem();
    pipe.setInletThermoSystem(createGas(0.95, 0.05));
    pipe.setNumberOfLegs(1);
    pipe.setNumberOfNodesInLeg(nodes);
    GeometryDefinitionInterface[] geometry = { new PipeData(), new PipeData() };
    for (GeometryDefinitionInterface section : geometry) {
      section.setDiameter(0.5);
      section.setInnerSurfaceRoughness(1.0e-5);
    }
    pipe.setEquipmentGeometry(geometry);
    pipe.setLegHeights(new double[] { 0.0, 0.0 });
    pipe.setLegPositions(new double[] { 0.0, lengthMeters });
    pipe.setLegOuterTemperatures(new double[] { TEMPERATURE_K, TEMPERATURE_K });
    pipe.setLegWallHeatTransferCoefficients(new double[] { 0.0, 0.0 });
    pipe.setLegOuterHeatTransferCoefficients(new double[] { 0.0, 0.0 });
    pipe.createSystem();
    pipe.init();
    pipe.solveSteadyState(1);
    assertTrue(pipe.getConvergenceReport().isConverged());
    return pipe;
  }

  private static void assertPulseEngineeringGates(IntegratedPulseResult pulse) {
    double eventAmplitude = pulse.pulseInletMassFraction - pulse.baselineOutlet;
    double cumulativeScaleKg = Math.max(Math.max(pulse.initialInventoryKg, pulse.cumulativeInletKg), 1.0);

    assertTrue(eventAmplitude > 0.0);
    assertTrue(pulse.pulseEndOutlet > pulse.baselineOutlet + 0.70 * eventAmplitude,
        "The 30-minute event must break through before the inlet returns to baseline.");
    assertTrue(pulse.peakOutlet > pulse.baselineOutlet + 0.70 * eventAmplitude);
    assertEquals(pulse.baselineOutlet, pulse.recoveredOutlet, 2.0e-3 * eventAmplitude,
        "The outlet composition must recover after purging the pulse.");
    assertEquals(0.0, pulse.cumulativeResidualKg, cumulativeScaleKg * 1.0e-7,
        "Cumulative nitrogen inventory must equal integrated inlet minus outlet mass.");
  }

  private static int exactStepCount(double durationSeconds, double timeStepSeconds) {
    int steps = (int) Math.round(durationSeconds / timeStepSeconds);
    assertEquals(durationSeconds, steps * timeStepSeconds, 1.0e-12,
        "Event durations must contain an integer number of timesteps.");
    return steps;
  }

  private static double commonTimeMeanAbsoluteDifference(double[] first, double firstTimeStepSeconds, double[] second,
      double secondTimeStepSeconds, double sampleIntervalSeconds) {
    int samples = (int) Math.round(first.length * firstTimeStepSeconds / sampleIntervalSeconds);
    assertEquals(samples, (int) Math.round(second.length * secondTimeStepSeconds / sampleIntervalSeconds));
    double difference = 0.0;
    for (int sample = 1; sample <= samples; sample++) {
      int firstIndex = (int) Math.round(sample * sampleIntervalSeconds / firstTimeStepSeconds) - 1;
      int secondIndex = (int) Math.round(sample * sampleIntervalSeconds / secondTimeStepSeconds) - 1;
      difference += Math.abs(first[firstIndex] - second[secondIndex]);
    }
    return difference / samples;
  }

  private static double last(double[] values) {
    return values[values.length - 1];
  }

  private static double sum(double[] values) {
    double result = 0.0;
    for (double value : values) {
      result += value;
    }
    return result;
  }

  private static void assertRawBitsEqual(double expected, double actual, String message) {
    assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual), message);
  }

  private static void assertRawBitsEqual(double[] expected, double[] actual, String message) {
    assertEquals(expected.length, actual.length, message);
    for (int index = 0; index < expected.length; index++) {
      assertRawBitsEqual(expected[index], actual[index], message + " index=" + index);
    }
  }

  private static final class IntegratedPulseResult {
    private final double baselineOutlet;
    private final double initialInventoryKg;
    private final double cumulativeInletKg;
    private final double cumulativeOutletKg;
    private final double pulseInletMassFraction;
    private final double pulseEndOutlet;
    private final double peakOutlet;
    private final double recoveredOutlet;
    private final double cumulativeResidualKg;
    private final double[] outletNitrogenHistory;
    private final double[] densityResidualHistory;
    private final int[] couplingIterationHistory;
    private final double[] finalInventoryKg;
    private final double[][] finalMassFractionProfile;

    private IntegratedPulseResult(double baselineOutlet, double initialInventoryKg, double cumulativeInletKg,
        double cumulativeOutletKg, double pulseInletMassFraction, double pulseEndOutlet, double peakOutlet,
        double recoveredOutlet, double cumulativeResidualKg, double[] outletNitrogenHistory,
        double[] densityResidualHistory, int[] couplingIterationHistory, double[] finalInventoryKg,
        double[][] finalMassFractionProfile) {
      this.baselineOutlet = baselineOutlet;
      this.initialInventoryKg = initialInventoryKg;
      this.cumulativeInletKg = cumulativeInletKg;
      this.cumulativeOutletKg = cumulativeOutletKg;
      this.pulseInletMassFraction = pulseInletMassFraction;
      this.pulseEndOutlet = pulseEndOutlet;
      this.peakOutlet = peakOutlet;
      this.recoveredOutlet = recoveredOutlet;
      this.cumulativeResidualKg = cumulativeResidualKg;
      this.outletNitrogenHistory = outletNitrogenHistory;
      this.densityResidualHistory = densityResidualHistory;
      this.couplingIterationHistory = couplingIterationHistory;
      this.finalInventoryKg = finalInventoryKg;
      this.finalMassFractionProfile = finalMassFractionProfile;
    }
  }

  private static SystemInterface createGas(double methane, double nitrogen) {
    SystemInterface gas = new SystemSrkEos(TEMPERATURE_K, PRESSURE_BARA);
    gas.addComponent("methane", methane);
    gas.addComponent("nitrogen", nitrogen);
    gas.createDatabase(true);
    gas.setMixingRule("classic");
    gas.init(0);
    gas.init(3);
    gas.initPhysicalProperties();
    gas.setTotalFlowRate(MASS_FLOW_KG_PER_SECOND, "kg/sec");
    return gas;
  }
}
