package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.process.equipment.pipeline.TwoFluidBenchmarkMetrics;
import neqsim.process.equipment.pipeline.TwoFluidBenchmarkMetrics.LimitCycleMetrics;
import neqsim.process.equipment.pipeline.TwoFluidBenchmarkMetrics.PhaseLockMetrics;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.TwoFluidPipe.BoundaryCondition;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitIntegrator.PreparedInterval;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TwoFluidUnsplitModelAdapter.PreparedStep;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.UnsplitTransientSolver;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Actual frozen-phase EOS handoff for the public Tengesdal 2002 large-facility test 3 fixture.
 *
 * <p>
 * The short handoff checks conservative preparation and isolation. Explicit opt-in gates cover five-second preparation,
 * atomic publication, and the sustained 600 s public-experiment matrix. The sustained gate evaluates complete settled
 * cycles on two meshes and two time steps; it remains qualification evidence rather than a default unit test.
 * </p>
 */
class TwoFluidUnsplitTengesdalPreparationTest {
  private static final Logger logger = LogManager.getLogger(TwoFluidUnsplitTengesdalPreparationTest.class);

  @ParameterizedTest
  @CsvSource({"16,0.1", "16,0.05", "24,0.05"})
  void shortRiserHandoffPreparesConservativelyWithoutPublishing(int cells, double maximumStep) {
    verifyPreparation(cells, maximumStep, 0.1);
  }

  @ParameterizedTest
  @CsvSource({"16,0.1", "16,0.05", "24,0.05"})
  void shortPressureInterpolatedRiserHandoffPreparesConservatively(int cells, double maximumStep) {
    verifyPreparation(cells, maximumStep, 0.1, UnsplitTransientSolver.TimeIntegrationMethod.BACKWARD_EULER, true);
  }

  @ParameterizedTest
  @CsvSource({"16,0.1", "16,0.05", "24,0.05"})
  void shortTrackedSubcellForceRiserHandoffIsConservative(int cells, double maximumStep) {
    verifyTrackedSubcellForces(cells, maximumStep, 0.1);
  }

  /** Require the tracked-force five-second mesh and time-step matrix to complete conservatively. */
  @Tag("slow")
  @EnabledIfSystemProperty(named = "neqsim.unsplit.tengesdal.tracked-force-qualification", matches = "true")
  @ParameterizedTest
  @CsvSource({"16,0.1", "16,0.05", "24,0.05"})
  void fiveSecondTrackedSubcellForceRiserQualificationMustCompleteTheWholeInterval(int cells, double maximumStep) {
    verifyTrackedSubcellForces(cells, maximumStep, 5.0);
  }

  /** Require the tracked-force 180 s mesh and time-step matrix to remain conservative. */
  @Tag("slow")
  @EnabledIfSystemProperty(named = "neqsim.unsplit.tengesdal.tracked-force-long-qualification", matches = "true")
  @Test
  void trackedSubcellForceLongIntervalMustCompleteConservatively() {
    verifyTrackedSubcellForceMatrix(180.0);
  }

  /** Require the tracked-force 600 s mesh and time-step matrix to remain conservative. */
  @Tag("slow")
  @EnabledIfSystemProperty(named = "neqsim.unsplit.tengesdal.tracked-force-sustained-qualification", matches = "true")
  @Test
  void trackedSubcellForceSustainedIntervalMustCompleteConservatively() {
    verifyTrackedSubcellForceMatrix(600.0);
  }

  /** Run and compare the declared tracked-force mesh and time-step matrix. */
  private static void verifyTrackedSubcellForceMatrix(double duration) {
    int[] cells = {16, 16, 24};
    double[] maximumSteps = {0.1, 0.05, 0.05};
    List<TrackedRun> runs = new ArrayList<TrackedRun>();
    for (int index = 0; index < cells.length; index++) {
      runs.add(simulateTrackedSubcellForceRun(cells[index], maximumSteps[index], duration));
    }
    TrackedRun reference = runs.get(0);
    List<Executable> gates = new ArrayList<Executable>();
    for (TrackedRun run : runs) {
      gates.add(() -> assertTrackedRun(run, duration));
    }
    for (int index = 1; index < runs.size(); index++) {
      TrackedRun refined = runs.get(index);
      gates.add(() -> assertTrackedDiscretizationAgreement(reference, refined));
    }
    assertAll(duration + " s tracked-force mesh and time-step qualification", gates.stream());
  }

  /** Run one tracked-force realization and retain its end-state and interval ledgers. */
  private static TrackedRun simulateTrackedSubcellForceRun(int cells, double maximumStep, double duration) {
    TwoFluidPipe pipe = createPipe(cells);
    pipe.setBlendInclinedAnnularSlugTransitions(true);
    pipe.setSignedDonorTransportEnabled(true);
    pipe.setSlugTrackingMode(TwoFluidPipe.SlugTrackingMode.CONSERVATIVE_LAGRANGIAN);
    pipe.configureLagrangianSlugTracking(false, true, false);
    LiquidAccumulationTracker.SlugCharacteristics characteristics = new LiquidAccumulationTracker.SlugCharacteristics();
    characteristics.tailPosition = 18.0;
    characteristics.frontPosition = 24.0;
    characteristics.length = characteristics.frontPosition - characteristics.tailPosition;
    characteristics.holdup = 0.9;
    characteristics.volume = characteristics.length * pipe.getSectionSnapshots()[0].getArea() * characteristics.holdup;
    LagrangianSlugTracker.SlugBubbleUnit original = pipe.getLagrangianSlugTracker()
        .initializeTerrainSlug(characteristics, pipe.getSectionSnapshots());
    assertTrue(original != null);
    pipe.getLagrangianSlugTracker().setEnableTerrainSlugGeneration(false);
    pipe.setSharedSlugForceBalanceEnabled(true);
    pipe.setConservativeSlugForceIntegrationEnabled(true);
    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    solver.setRelativeTolerance(1.0e-9);
    solver.setTimeIntegrationMethod(UnsplitTransientSolver.TimeIntegrationMethod.BACKWARD_EULER);
    pipe.setUnsplitTransientSolver(solver, maximumStep);

    UUID id = UUID.nameUUIDFromBytes(
        ("tracked-force-tengesdal:" + cells + ":" + maximumStep + ":" + duration).getBytes(StandardCharsets.UTF_8));
    pipe.runTransient(duration, id);
    TwoFluidMassBalanceReport report = assertPublishedInterval(pipe, id, maximumStep, duration);
    TrackedRun run = new TrackedRun(cells + " cells / " + maximumStep + " s", duration, pipe.getPressureProfile()[0],
        report.getOutletMassKg(Phase.TOTAL), report.getFinalMassKg(Phase.GAS), report.getFinalMassKg(Phase.LIQUID),
        report.getRejectedAttempts());
    logger.info(
        "Tracked-force Tengesdal interval: label={}, duration={}, inletPressure={}, outletMass={}, gasInventory={}, liquidInventory={}, rejects={}",
        run.label, duration, run.inletPressurePa, run.outletMassKg, run.finalGasInventoryKg, run.finalLiquidInventoryKg,
        run.rejectedAttempts);
    return run;
  }

  /** Apply physical endpoint and nonlinear-progress gates to one tracked-force realization. */
  private static void assertTrackedRun(TrackedRun run, double duration) {
    assertAll("tracked-force endpoint for " + run.label, () -> assertEquals(duration, run.durationSeconds, 0.0),
        () -> assertEquals(0, run.rejectedAttempts, "rejected nonlinear attempts"),
        () -> assertTrue(Double.isFinite(run.inletPressurePa) && run.inletPressurePa > 0.0),
        () -> assertTrue(Double.isFinite(run.outletMassKg) && run.outletMassKg > 0.0),
        () -> assertTrue(Double.isFinite(run.finalGasInventoryKg) && run.finalGasInventoryKg > 0.0),
        () -> assertTrue(Double.isFinite(run.finalLiquidInventoryKg) && run.finalLiquidInventoryKg > 0.0));
  }

  /** Apply the predeclared 15% end-state spread bound to a refined tracked-force realization. */
  private static void assertTrackedDiscretizationAgreement(TrackedRun reference, TrackedRun refined) {
    assertAll("tracked-force discretization agreement for " + refined.label,
        () -> assertTrue(finiteRelativeSpread(reference.inletPressurePa, refined.inletPressurePa) <= 0.15,
            "inlet-pressure spread exceeds 15%"),
        () -> assertTrue(finiteRelativeSpread(reference.outletMassKg, refined.outletMassKg) <= 0.15,
            "through-flow spread exceeds 15%"),
        () -> assertTrue(finiteRelativeSpread(reference.finalGasInventoryKg, refined.finalGasInventoryKg) <= 0.15,
            "gas-inventory spread exceeds 15%"),
        () -> assertTrue(finiteRelativeSpread(reference.finalLiquidInventoryKg, refined.finalLiquidInventoryKg) <= 0.15,
            "liquid-inventory spread exceeds 15%"));
  }

  @Tag("slow")
  @EnabledIfSystemProperty(named = "neqsim.unsplit.tengesdal.qualification", matches = "true")
  @ParameterizedTest
  @CsvSource({"16,0.1", "16,0.05", "24,0.05"})
  void fiveSecondRiserQualificationMustCompleteTheWholeInterval(int cells, double maximumStep) {
    verifyPreparation(cells, maximumStep, 5.0);
  }

  @Tag("slow")
  @EnabledIfSystemProperty(named = "neqsim.unsplit.tengesdal.qualification", matches = "true")
  @ParameterizedTest
  @CsvSource({"16,0.1", "16,0.05", "24,0.05"})
  void fiveSecondPressureInterpolatedRiserQualificationMustCompleteTheWholeInterval(int cells, double maximumStep) {
    verifyPreparation(cells, maximumStep, 5.0, UnsplitTransientSolver.TimeIntegrationMethod.BACKWARD_EULER, true);
  }

  @Tag("slow")
  @EnabledIfSystemProperty(named = "neqsim.unsplit.tengesdal.publication-qualification", matches = "true")
  @ParameterizedTest
  @CsvSource({"16,0.1", "16,0.05", "24,0.05"})
  void fiveSecondPressureInterpolatedRiserQualificationMustPublishAtomically(int cells, double maximumStep) {
    verifyPublication(cells, maximumStep, 5.0);
  }

  @Tag("slow")
  @EnabledIfSystemProperty(named = "neqsim.unsplit.tengesdal.long-publication-qualification", matches = "true")
  @Test
  void supportingPressureInterpolatedRiserQualificationMustPublishAtomically() {
    verifyPublication(16, 0.1, 180.0);
  }

  @Tag("slow")
  @EnabledIfSystemProperty(named = "neqsim.unsplit.tengesdal.sustained-publication-qualification", matches = "true")
  @Test
  void sustainedPressureInterpolatedRiserQualificationMustPublishAtomically() {
    verifyPublication(16, 0.1, 600.0);
  }

  /**
   * Qualify sustained settled cycles and discretization sensitivity against the unchanged public experiment.
   *
   * <p>
   * The final 300 s is the declared settled window. Amplitude is the median peak-to-peak range of complete pressure
   * cycles, not a whole-run extremum. The 38 s source target is pressure peak spacing. Complete-cycle amplitude and
   * period ranges must each remain within 20% of their medians, and pressure/liquid median-upcrossing phase drift must
   * stay within 0.10 cycle. The 16-cell/0.1 s result is compared with both a halved step and a refined mesh using a 15%
   * predeclared spread bound. These numerical gates supplement, rather than replace, experimental agreement.
   * </p>
   */
  @Tag("slow")
  @EnabledIfSystemProperty(named = "neqsim.unsplit.tengesdal.experimental-qualification", matches = "true")
  @Test
  void sustainedPublishedTrajectoryMatchesPublicLimitCycle() {
    int[] cells = {16, 16, 24};
    double[] maximumSteps = {0.1, 0.05, 0.05};
    List<ExperimentalRun> runs = new ArrayList<ExperimentalRun>();
    for (int index = 0; index < cells.length; index++) {
      ExperimentalRun run = simulateExperimentalRun(cells[index], maximumSteps[index]);
      runs.add(run);
    }
    ExperimentalRun reference = runs.get(0);
    List<Executable> gates = new ArrayList<Executable>();
    for (ExperimentalRun run : runs) {
      gates.add(() -> assertExperimentalRun(run));
    }
    for (int index = 1; index < runs.size(); index++) {
      ExperimentalRun refined = runs.get(index);
      double amplitudeSpread = finiteRelativeSpread(reference.pressureAmplitudePa, refined.pressureAmplitudePa);
      double periodSpread = finiteRelativeSpread(reference.pressureCycle.getPeriodSeconds(),
          refined.pressureCycle.getPeriodSeconds());
      double holdupSpread = finiteRelativeSpread(reference.steadyFlowlineHoldup, refined.steadyFlowlineHoldup);
      logger.info("600 s discretization comparison {}: pressureAmplitudeSpread={}, pressurePeriodSpread={}, "
          + "steadyHoldupSpread={}", refined.label, amplitudeSpread, periodSpread, holdupSpread);
      gates.add(() -> assertDiscretizationAgreement(reference, refined));
    }
    assertAll("600 s settled-cycle and discretization qualification", gates.stream());
  }

  /** Run one complete experimental realization without changing thresholds from its discretization. */
  private static ExperimentalRun simulateExperimentalRun(int count, double maximumStep) {
    final double outerStep = 1.0;
    final double duration = 600.0;
    final double settledWindowStart = 300.0;
    TwoFluidPipe pipe = createPipe(count);
    pipe.setUnsplitPressureInterpolationEnabled(true);
    pipe.setBlendInclinedAnnularSlugTransitions(true);
    pipe.setSignedDonorTransportEnabled(true);
    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    solver.setRelativeTolerance(1.0e-9);
    solver.setTimeIntegrationMethod(UnsplitTransientSolver.TimeIntegrationMethod.BACKWARD_EULER);
    pipe.setUnsplitTransientSolver(solver, maximumStep);
    double steadyFlowlineHoldup = pipe.getLiquidHoldupProfile()[count / 4];
    List<Double> times = new ArrayList<Double>();
    List<Double> pressures = new ArrayList<Double>();
    List<Double> liquidRates = new ArrayList<Double>();
    Map<Phase, Double> maximumClosure = new EnumMap<Phase, Double>(Phase.class);
    Map<Phase, Double> finalInventory = new EnumMap<Phase, Double>(Phase.class);
    for (Phase phase : Phase.values()) {
      maximumClosure.put(phase, 0.0);
    }
    int rejectedAttempts = 0;
    long modelEvaluations = 0L;
    double inletLiquidMass = 0.0;
    UUID id = UUID.nameUUIDFromBytes(
        ("published-tengesdal-experiment:" + count + ":" + maximumStep).getBytes(StandardCharsets.UTF_8));
    int steps = (int) Math.round(duration / outerStep);
    for (int step = 0; step < steps; step++) {
      pipe.runTransient(outerStep, id);
      TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
      assertEquals(outerStep, report.getElapsedTimeSeconds(), 0.0);
      assertTrue(report.getRejectedAttempts() >= 0);
      assertTrue(report.getModelEvaluations() >= report.getAcceptedSubsteps());
      rejectedAttempts += report.getRejectedAttempts();
      modelEvaluations += report.getModelEvaluations();
      inletLiquidMass += report.getInletMassKg(Phase.LIQUID);
      for (Phase phase : Phase.values()) {
        maximumClosure.put(phase, Math.max(maximumClosure.get(phase), report.getRelativeResidual(phase)));
        finalInventory.put(phase, report.getFinalMassKg(phase));
      }
      if (pipe.getSimulationTime() >= settledWindowStart) {
        times.add(pipe.getSimulationTime());
        pressures.add(pipe.getPressureProfile()[0]);
        liquidRates.add(report.getOutletMassKg(Phase.LIQUID) / report.getElapsedTimeSeconds());
      }
    }
    LimitCycleMetrics pressureCycle = TwoFluidBenchmarkMetrics.analyzeLimitCycle(toArray(times), toArray(pressures),
        settledWindowStart);
    LimitCycleMetrics liquidCycle = TwoFluidBenchmarkMetrics.analyzeLimitCycle(toArray(times), toArray(liquidRates),
        settledWindowStart);
    PhaseLockMetrics phaseLock = TwoFluidBenchmarkMetrics.analyzePhaseLock(pressureCycle, liquidCycle);
    double pressureAmplitude = pressureCycle.getMedianCyclePeakToPeak();
    double minimumLiquidRate = minimum(liquidRates);
    double maximumLiquidRate = maximum(liquidRates);
    double meanLiquidFeed = inletLiquidMass / duration;
    logger.info(
        "Published unsplit Tengesdal experiment: cells={}, maximumStep={}, settledAmplitude={} Pa, "
            + "pressurePeriod={} s, pressureCycles={}, pressureAmplitudeSpread={}, pressurePeriodSpread={}, "
            + "liquidPeriod={} s, liquidCycles={}, phaseOffset={}, phaseDrift={}, qMin={}, qMax={}, meanFeed={}, "
            + "steadyHoldup={}, rejects={}, evaluations={}, gasClosure={}, oilClosure={}, waterClosure={}",
        count, maximumStep, pressureAmplitude, pressureCycle.getPeriodSeconds(), pressureCycle.getCompletedCycleCount(),
        pressureCycle.getRelativeCycleAmplitudeSpread(), pressureCycle.getRelativeCyclePeriodSpread(),
        liquidCycle.getPeriodSeconds(), liquidCycle.getCompletedCycleCount(), phaseLock.getMeanPhaseOffsetCycles(),
        phaseLock.getMaximumPhaseDeviationCycles(), minimumLiquidRate, maximumLiquidRate, meanLiquidFeed,
        steadyFlowlineHoldup, rejectedAttempts, modelEvaluations, maximumClosure.get(Phase.GAS),
        maximumClosure.get(Phase.OIL), maximumClosure.get(Phase.WATER));
    return new ExperimentalRun(count + " cells / " + maximumStep + " s", pipe.getSimulationTime(), pressureAmplitude,
        pressureCycle, liquidCycle, phaseLock, minimumLiquidRate, maximumLiquidRate, meanLiquidFeed,
        steadyFlowlineHoldup, rejectedAttempts, maximumClosure, finalInventory);
  }

  /** Apply the experimental, conservation and repeated-cycle gates to one realization. */
  private static void assertExperimentalRun(ExperimentalRun run) {
    final double holdupTarget = 0.342;
    final double holdupTolerance = 0.01 * holdupTarget;
    assertAll("600 s published unsplit Tengesdal qualification for " + run.label,
        () -> assertEquals(600.0, run.simulationEndSeconds, 0.0),
        () -> assertEquals(0, run.rejectedAttempts, "rejected nonlinear attempts"),
        () -> assertTrue(run.pressureCycle.getCompletedCycleCount() >= 2,
            "completed pressure cycles=" + run.pressureCycle.getCompletedCycleCount() + ", settled p10-p90 band="
                + run.pressureCycle.getP10ToP90Band() + " Pa"),
        () -> assertTrue(run.liquidCycle.getCompletedCycleCount() >= 2,
            "completed liquid-production cycles=" + run.liquidCycle.getCompletedCycleCount()),
        () -> assertTrue(run.pressureCycle.getRelativeCycleAmplitudeSpread() <= 0.20,
            "cycle-amplitude spread=" + run.pressureCycle.getRelativeCycleAmplitudeSpread()),
        () -> assertTrue(run.pressureCycle.getRelativeCyclePeriodSpread() <= 0.20,
            "cycle-period spread=" + run.pressureCycle.getRelativeCyclePeriodSpread()),
        () -> assertTrue(run.liquidCycle.getRelativeCycleAmplitudeSpread() <= 0.20,
            "liquid cycle-amplitude spread=" + run.liquidCycle.getRelativeCycleAmplitudeSpread()),
        () -> assertTrue(run.liquidCycle.getRelativeCyclePeriodSpread() <= 0.20,
            "liquid cycle-period spread=" + run.liquidCycle.getRelativeCyclePeriodSpread()),
        () -> assertTrue(
            finiteRelativeSpread(run.pressureCycle.getPeriodSeconds(), run.liquidCycle.getPeriodSeconds()) <= 0.10,
            "pressure/liquid period mismatch: pressure=" + run.pressureCycle.getPeriodSeconds() + ", liquid="
                + run.liquidCycle.getPeriodSeconds()),
        () -> assertTrue(run.phaseLock.hasRepeatedPhaseLock(),
            "matched pressure/liquid crossings=" + run.phaseLock.getMatchedCrossings()),
        () -> assertTrue(
            run.phaseLock.getMatchedCrossings() >= Math.max(run.pressureCycle.getMedianUpcrossingTimesSeconds().length,
                run.liquidCycle.getMedianUpcrossingTimesSeconds().length) - 1,
            "phase-lock crossing coverage=" + run.phaseLock.getMatchedCrossings() + ", pressure crossings="
                + run.pressureCycle.getMedianUpcrossingTimesSeconds().length + ", liquid crossings="
                + run.liquidCycle.getMedianUpcrossingTimesSeconds().length),
        () -> assertTrue(run.phaseLock.getMaximumPhaseDeviationCycles() <= 0.10,
            "pressure/liquid phase drift=" + run.phaseLock.getMaximumPhaseDeviationCycles()),
        () -> assertTrue(run.phaseLock.getCircularResultant() >= 0.95,
            "pressure/liquid circular concentration=" + run.phaseLock.getCircularResultant()),
        () -> SevereSluggingExperimentalBenchmarkTest.assertExperimentalAgreement(run.pressureAmplitudePa,
            run.pressureCycle.getPeriodSeconds()),
        () -> assertEquals(holdupTarget, run.steadyFlowlineHoldup, holdupTolerance, "steady flowline liquid hold-up"),
        () -> assertTrue(run.maximumLiquidRateKgPerSecond > 1.25 * run.meanLiquidFeedKgPerSecond,
            "no liquid blowout above the mean feed: maximum=" + run.maximumLiquidRateKgPerSecond + ", mean="
                + run.meanLiquidFeedKgPerSecond),
        () -> assertTrue(run.minimumLiquidRateKgPerSecond < 0.75 * run.meanLiquidFeedKgPerSecond,
            "no liquid fallback below the mean feed: minimum=" + run.minimumLiquidRateKgPerSecond + ", mean="
                + run.meanLiquidFeedKgPerSecond));
    for (Phase phase : Phase.values()) {
      assertTrue(run.maximumClosure.get(phase) < 1.0e-9, phase + " 600 s closure=" + run.maximumClosure.get(phase));
      assertTrue(Double.isFinite(run.finalInventory.get(phase)));
      assertTrue(run.finalInventory.get(phase) >= 0.0);
    }
  }

  /** Apply predeclared mesh and time-step agreement bounds at matched mean production. */
  private static void assertDiscretizationAgreement(ExperimentalRun reference, ExperimentalRun refined) {
    assertAll("600 s discretization agreement for " + refined.label,
        () -> assertTrue(finiteRelativeSpread(reference.pressureAmplitudePa, refined.pressureAmplitudePa) <= 0.15,
            "pressure-amplitude spread exceeds 15%: reference=" + reference.pressureAmplitudePa + ", refined="
                + refined.pressureAmplitudePa),
        () -> assertTrue(
            finiteRelativeSpread(reference.pressureCycle.getPeriodSeconds(),
                refined.pressureCycle.getPeriodSeconds()) <= 0.15,
            "pressure-period spread exceeds 15%: reference=" + reference.pressureCycle.getPeriodSeconds() + ", refined="
                + refined.pressureCycle.getPeriodSeconds()),
        () -> assertTrue(finiteRelativeSpread(reference.steadyFlowlineHoldup, refined.steadyFlowlineHoldup) <= 0.02,
            "steady flowline hold-up spread exceeds 2%: reference=" + reference.steadyFlowlineHoldup + ", refined="
                + refined.steadyFlowlineHoldup),
        () -> assertEquals(reference.meanLiquidFeedKgPerSecond, refined.meanLiquidFeedKgPerSecond,
            1.0e-12 * Math.max(1.0, reference.meanLiquidFeedKgPerSecond), "matched mean liquid production"));
  }

  /** Return a refinement spread or NaN when either realization did not publish a finite metric. */
  private static double finiteRelativeSpread(double reference, double refined) {
    if (!Double.isFinite(reference) || !Double.isFinite(refined)) {
      return Double.NaN;
    }
    return TwoFluidBenchmarkMetrics.relativeMeshSpread(reference, refined);
  }

  /**
   * Verify the startup ledger for reconstructed slug friction with the compatible face operator.
   *
   * @param count number of pipe sections
   * @param maximumStep maximum nominal substep in seconds
   * @param duration complete prepared interval in seconds
   */
  private static void verifyTrackedSubcellForces(int count, double maximumStep, double duration) {
    TwoFluidPipe pipe = createPipe(count);
    pipe.setBlendInclinedAnnularSlugTransitions(true);
    pipe.setSignedDonorTransportEnabled(true);
    pipe.setSlugTrackingMode(TwoFluidPipe.SlugTrackingMode.CONSERVATIVE_LAGRANGIAN);
    pipe.configureLagrangianSlugTracking(false, true, false);
    LiquidAccumulationTracker.SlugCharacteristics characteristics = new LiquidAccumulationTracker.SlugCharacteristics();
    characteristics.tailPosition = 18.0;
    characteristics.frontPosition = 24.0;
    characteristics.length = characteristics.frontPosition - characteristics.tailPosition;
    characteristics.holdup = 0.9;
    characteristics.volume = characteristics.length * pipe.getSectionSnapshots()[0].getArea() * characteristics.holdup;
    LagrangianSlugTracker.SlugBubbleUnit original = pipe.getLagrangianSlugTracker()
        .initializeTerrainSlug(characteristics, pipe.getSectionSnapshots());
    assertTrue(original != null);
    pipe.getLagrangianSlugTracker().setEnableTerrainSlugGeneration(false);
    pipe.setSharedSlugForceBalanceEnabled(true);
    pipe.setConservativeSlugForceIntegrationEnabled(true);

    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    solver.setRelativeTolerance(1.0e-9);
    solver.setTimeIntegrationMethod(UnsplitTransientSolver.TimeIntegrationMethod.BACKWARD_EULER);
    PreparedInterval interval = pipe.prepareUnsplitTransient(duration, maximumStep, solver,
        pipe.createUnsplitDensityModel());
    assertEquals(duration, interval.getEndTimeSeconds(), 0.0);
    for (PreparedStep step : interval.getSubsteps()) {
      assertEquals(0.0, step.getPressureInterpolationTimeScale(), 0.0);
    }
    assertEquals(0.0, pipe.getSimulationTime(), 0.0);
    assertEquals(0.0, original.age, 0.0);
    LagrangianSlugTracker accepted = interval.getAcceptedSlugTracker();
    assertTrue(accepted != null);
    assertEquals(1, accepted.getSlugCount());
    assertEquals(duration, accepted.getSlugs().get(0).age, 1.0e-12 * Math.max(1.0, duration));
    double impulse = 0.0;
    for (double[] cellImpulse : interval.getConservativeFrictionImpulsePerLength()) {
      for (double phaseImpulse : cellImpulse) {
        assertTrue(Double.isFinite(phaseImpulse));
        impulse += Math.abs(phaseImpulse);
      }
    }
    assertTrue(impulse > 0.0);
    for (double residual : interval.getMassResidualKg()) {
      assertEquals(0.0, residual, 1.0e-8);
    }
  }

  private static void verifyPreparation(int count, double maximumStep, double duration) {
    verifyPreparation(count, maximumStep, duration, UnsplitTransientSolver.TimeIntegrationMethod.IMPLICIT_MIDPOINT,
        false);
  }

  private static void verifyPreparation(int count, double maximumStep, double duration,
      UnsplitTransientSolver.TimeIntegrationMethod method, boolean pressureInterpolation) {
    TwoFluidPipe pipe = createPipe(count);
    pipe.setUnsplitPressureInterpolationEnabled(pressureInterpolation);
    if (pressureInterpolation) {
      // Exercise the opt-in continuous closure and signed donors in the backward-Euler qualification lane that
      // exposed the countercurrent oil-flow reversal. The midpoint compatibility lane remains unchanged.
      pipe.setBlendInclinedAnnularSlugTransitions(true);
      pipe.setSignedDonorTransportEnabled(true);
    }
    TwoFluidSection[] initial = pipe.getSectionSnapshots();
    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    solver.setRelativeTolerance(1.0e-9);
    solver.setTimeIntegrationMethod(method);
    solver.setDirectionalComplementarityEnabled(Boolean.getBoolean("neqsim.unsplit.directional-complementarity"));
    PreparedInterval interval = pipe.prepareUnsplitTransient(duration, maximumStep, solver,
        pipe.createUnsplitDensityModel());
    assertEquals(duration, interval.getEndTimeSeconds(), 0.0);
    assertEquals(0.0, pipe.getSimulationTime(), 0.0);
    assertEquals(0.0, pipe.getTime(), 0.0);
    assertTrue(interval.getMaximumScaledResidual() <= 1.0e-8);
    double maximumVelocity = 0.0;
    double maximumPressureDeparture = 0.0;
    double minimumStep = maximumStep;
    double[] initialMass = interval.getSubsteps().get(0).getInitialMassKg();
    for (int phase = 0; phase < 3; phase++) {
      assertEquals(0.0, interval.getMassResidualKg()[phase] / Math.max(1.0, initialMass[phase]), 1.0e-8);
    }
    assertTrue(initialMass[0] > 0.0 && initialMass[1] > 0.0);
    assertEquals(0.0, initialMass[2], 0.0);
    for (PreparedStep step : interval.getSubsteps()) {
      assertEquals(pressureInterpolation ? step.getTimeIntegrationWeight() * step.getTimeStepSeconds() : 0.0,
          step.getPressureInterpolationTimeScale(), 0.0);
      minimumStep = Math.min(minimumStep, step.getTimeStepSeconds());
      assertTrue(step.getMaximumScaledResidual() <= 1.0e-8);
      TwoFluidSection[] candidate = step.getEndpointSections();
      for (int cell = 0; cell < count; cell++) {
        maximumVelocity = Math.max(maximumVelocity,
            Math.max(Math.abs(candidate[cell].getGasVelocity()), Math.abs(candidate[cell].getOilVelocity())));
        maximumPressureDeparture = Math.max(maximumPressureDeparture,
            Math.abs(candidate[cell].getPressure() - initial[cell].getPressure()));
        assertTrue(Double.isFinite(candidate[cell].getPressure()) && candidate[cell].getPressure() > 0.0);
        assertEquals(1.0,
            candidate[cell].getGasHoldup() + candidate[cell].getOilHoldup() + candidate[cell].getWaterHoldup(), 1.0e-8);
      }
    }
    assertTrue(Double.isFinite(maximumVelocity));
    TwoFluidSection[] unchanged = pipe.getSectionSnapshots();
    for (int cell = 0; cell < count; cell++) {
      assertArrayEquals(initial[cell].getStateVector(), unchanged[cell].getStateVector(), 0.0);
      assertEquals(initial[cell].getPressure(), unchanged[cell].getPressure(), 0.0);
    }
    logger.info(
        "Unsplit Tengesdal handoff: method={}, pressureInterpolation={}, cells={}, maximumStep={}, duration={}, substeps={}, rejects={}, "
            + "evaluations={}, minimumStep={}, maximumSpeed={}, maximumPressureDeparture={}, scaledResidual={}",
        method, pressureInterpolation, count, maximumStep, duration, interval.getSubsteps().size(),
        interval.getRejectedAttempts(), interval.getModelEvaluations(), minimumStep, maximumVelocity,
        maximumPressureDeparture, interval.getMaximumScaledResidual());
  }

  private static void verifyPublication(int count, double maximumStep, double duration) {
    TwoFluidPipe pipe = createPipe(count);
    pipe.setUnsplitPressureInterpolationEnabled(true);
    pipe.setBlendInclinedAnnularSlugTransitions(true);
    pipe.setSignedDonorTransportEnabled(true);
    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    solver.setRelativeTolerance(1.0e-9);
    solver.setTimeIntegrationMethod(UnsplitTransientSolver.TimeIntegrationMethod.BACKWARD_EULER);
    pipe.setUnsplitTransientSolver(solver, maximumStep);
    UUID id = UUID.nameUUIDFromBytes(
        ("published-tengesdal:" + count + ":" + maximumStep + ":" + duration).getBytes(StandardCharsets.UTF_8));

    pipe.runTransient(duration, id);

    TwoFluidMassBalanceReport report = assertPublishedInterval(pipe, id, maximumStep, duration);
    logger.info(
        "Published unsplit Tengesdal interval: cells={}, maximumStep={}, duration={}, substeps={}, gasClosure={}, oilClosure={}, waterClosure={}, outletFlow={}",
        count, maximumStep, duration, report.getAcceptedSubsteps(), report.getRelativeResidual(Phase.GAS),
        report.getRelativeResidual(Phase.OIL), report.getRelativeResidual(Phase.WATER),
        pipe.getOutletStream().getFlowRate("kg/sec"));
  }

  /**
   * Check the committed clock, full-interval mass balance and physical endpoint for a published riser run.
   *
   * @param pipe pipe that published the interval
   * @param id interval calculation identifier
   * @param maximumStep nominal step bound in seconds
   * @param duration published duration in seconds
   * @return the verified mass balance report
   */
  private static TwoFluidMassBalanceReport assertPublishedInterval(TwoFluidPipe pipe, UUID id, double maximumStep,
      double duration) {
    assertEquals(duration, pipe.getSimulationTime(), 0.0);
    assertEquals(duration, pipe.getTime(), 0.0);
    assertEquals(id, pipe.getCalculationIdentifier());
    TwoFluidMassBalanceReport report = pipe.getLastMassBalanceReport();
    assertEquals(duration, report.getElapsedTimeSeconds(), 0.0);
    assertTrue(report.getAcceptedSubsteps() >= Math.ceil(duration / maximumStep));
    for (Phase phase : Phase.values()) {
      assertTrue(Double.isFinite(report.getFinalMassKg(phase)));
      assertTrue(report.getFinalMassKg(phase) >= 0.0);
      assertEquals(0.0, report.getResidualKg(phase) / Math.max(1.0, report.getInitialMassKg(phase)), 1.0e-8);
    }
    assertTrue(pipe.getOutletStream().getFlowRate("kg/sec") > 0.0);
    for (TwoFluidSection section : pipe.getSectionSnapshots()) {
      assertTrue(Double.isFinite(section.getPressure()) && section.getPressure() > 0.0);
      assertEquals(1.0, section.getGasHoldup() + section.getOilHoldup() + section.getWaterHoldup(), 1.0e-8);
    }
    return report;
  }

  /**
   * Convert boxed samples to a primitive array.
   *
   * @param values finite samples
   * @return primitive copy
   */
  private static double[] toArray(List<Double> values) {
    double[] result = new double[values.size()];
    for (int index = 0; index < values.size(); index++) {
      result[index] = values.get(index);
    }
    return result;
  }

  /**
   * Return the minimum sample.
   *
   * @param values non-empty finite samples
   * @return minimum value
   */
  private static double minimum(List<Double> values) {
    double result = Double.POSITIVE_INFINITY;
    for (double value : values) {
      result = Math.min(result, value);
    }
    return result;
  }

  /**
   * Return the maximum sample.
   *
   * @param values non-empty finite samples
   * @return maximum value
   */
  private static double maximum(List<Double> values) {
    double result = Double.NEGATIVE_INFINITY;
    for (double value : values) {
      result = Math.max(result, value);
    }
    return result;
  }

  /** Immutable result from one 600 s discretization. */
  private static final class ExperimentalRun {
    private final String label;
    private final double simulationEndSeconds;
    private final double pressureAmplitudePa;
    private final LimitCycleMetrics pressureCycle;
    private final LimitCycleMetrics liquidCycle;
    private final PhaseLockMetrics phaseLock;
    private final double minimumLiquidRateKgPerSecond;
    private final double maximumLiquidRateKgPerSecond;
    private final double meanLiquidFeedKgPerSecond;
    private final double steadyFlowlineHoldup;
    private final int rejectedAttempts;
    private final Map<Phase, Double> maximumClosure;
    private final Map<Phase, Double> finalInventory;

    /**
     * Create a defensive summary.
     *
     * @param label discretization label
     * @param simulationEndSeconds accepted end time in s
     * @param pressureAmplitudePa settled median complete-cycle pressure amplitude in Pa
     * @param pressureCycle pressure cycle metrics
     * @param liquidCycle liquid-production cycle metrics
     * @param phaseLock pressure/liquid phase-lock metrics
     * @param minimumLiquidRateKgPerSecond settled minimum outlet liquid rate in kg/s
     * @param maximumLiquidRateKgPerSecond settled maximum outlet liquid rate in kg/s
     * @param meanLiquidFeedKgPerSecond complete-run mean inlet liquid rate in kg/s
     * @param steadyFlowlineHoldup initialized flowline liquid hold-up
     * @param rejectedAttempts rejected nonlinear attempt count
     * @param maximumClosure maximum relative phase/total conservation residuals
     * @param finalInventory final phase/total inventories in kg
     */
    private ExperimentalRun(String label, double simulationEndSeconds, double pressureAmplitudePa,
        LimitCycleMetrics pressureCycle, LimitCycleMetrics liquidCycle, PhaseLockMetrics phaseLock,
        double minimumLiquidRateKgPerSecond, double maximumLiquidRateKgPerSecond, double meanLiquidFeedKgPerSecond,
        double steadyFlowlineHoldup, int rejectedAttempts, Map<Phase, Double> maximumClosure,
        Map<Phase, Double> finalInventory) {
      this.label = label;
      this.simulationEndSeconds = simulationEndSeconds;
      this.pressureAmplitudePa = pressureAmplitudePa;
      this.pressureCycle = pressureCycle;
      this.liquidCycle = liquidCycle;
      this.phaseLock = phaseLock;
      this.minimumLiquidRateKgPerSecond = minimumLiquidRateKgPerSecond;
      this.maximumLiquidRateKgPerSecond = maximumLiquidRateKgPerSecond;
      this.meanLiquidFeedKgPerSecond = meanLiquidFeedKgPerSecond;
      this.steadyFlowlineHoldup = steadyFlowlineHoldup;
      this.rejectedAttempts = rejectedAttempts;
      this.maximumClosure = new EnumMap<Phase, Double>(maximumClosure);
      this.finalInventory = new EnumMap<Phase, Double>(finalInventory);
    }
  }

  /** Immutable numerical summary from one tracked-force long-interval realization. */
  private static final class TrackedRun {
    private final String label;
    private final double durationSeconds;
    private final double inletPressurePa;
    private final double outletMassKg;
    private final double finalGasInventoryKg;
    private final double finalLiquidInventoryKg;
    private final int rejectedAttempts;

    /**
     * Create a tracked-force summary.
     *
     * @param label discretization label
     * @param durationSeconds accepted interval duration in s
     * @param inletPressurePa final inlet-cell pressure in Pa
     * @param outletMassKg cumulative total outlet mass in kg
     * @param finalGasInventoryKg final gas inventory in kg
     * @param finalLiquidInventoryKg final combined-liquid inventory in kg
     * @param rejectedAttempts rejected nonlinear attempt count
     */
    private TrackedRun(String label, double durationSeconds, double inletPressurePa, double outletMassKg,
        double finalGasInventoryKg, double finalLiquidInventoryKg, int rejectedAttempts) {
      this.label = label;
      this.durationSeconds = durationSeconds;
      this.inletPressurePa = inletPressurePa;
      this.outletMassKg = outletMassKg;
      this.finalGasInventoryKg = finalGasInventoryKg;
      this.finalLiquidInventoryKg = finalLiquidInventoryKg;
      this.rejectedAttempts = rejectedAttempts;
    }
  }

  static TwoFluidPipe createPipe(int count) {
    double diameter = 0.0762;
    double area = Math.PI * diameter * diameter / 4.0;
    double oilFlow = 0.50 * area * 856.0;
    double gasFlow = area * 1.204;
    SystemInterface fluid = new SystemSrkEos(298.15, 2.3);
    fluid.addComponent("nitrogen", gasFlow / 0.0280134);
    fluid.addTBPfraction("Crystex", oilFlow / 0.220, 0.220, 0.856);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream inlet = new Stream("Tengesdal 2002 test 3 unsplit feed", fluid);
    inlet.setFlowRate(gasFlow + oilFlow, "kg/sec");
    inlet.run();
    double length = 19.81 + 14.94;
    double angle = Math.toRadians(-3.0);
    double[] elevation = new double[count];
    for (int cell = 0; cell < count; cell++) {
      double position = length * cell / (count - 1.0);
      elevation[cell] = position <= 19.81 ? position * Math.sin(angle) : 19.81 * Math.sin(angle) + position - 19.81;
    }
    TwoFluidPipe pipe = new TwoFluidPipe("Unsplit Tengesdal handoff", inlet);
    pipe.setLength(length);
    pipe.setDiameter(diameter);
    pipe.setRoughness(1.5e-6);
    pipe.setNumberOfSections(count);
    pipe.setElevationProfile(elevation);
    pipe.setOutletPressure(1.01325, "bara");
    pipe.setInletBoundaryCondition(BoundaryCondition.STREAM_CONNECTED);
    pipe.setOutletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE);
    pipe.setIncludeMassTransfer(false);
    pipe.setIncludeEnergyEquation(false);
    pipe.setEnableSlugTracking(false);
    pipe.setEnableInterfacialPressure(true);
    pipe.setImplicitInterfacialPressureCoupling(true);
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setOutletBoundaryFluid(inlet.getFluid());
    pipe.setAllowOutletPhaseBackflow(true);
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.run();
    return pipe;
  }
}
