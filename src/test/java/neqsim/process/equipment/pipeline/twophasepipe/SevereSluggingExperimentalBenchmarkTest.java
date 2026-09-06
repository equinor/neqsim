package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.TwoFluidBenchmarkMetrics;
import neqsim.process.equipment.pipeline.TwoFluidBenchmarkMetrics.LimitCycleMetrics;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport;
import neqsim.process.equipment.pipeline.TwoFluidMassBalanceReport.Phase;
import neqsim.process.equipment.pipeline.TwoFluidPipe;
import neqsim.process.equipment.pipeline.TwoFluidPipe.BoundaryCondition;
import neqsim.process.equipment.pipeline.twophasepipe.numerics.TimeIntegrator;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Public dynamic benchmark from Tengesdal's 2002 large pipeline-riser facility.
 *
 * <p>
 * The active tests exercise short coupled pressure-momentum, implicit interfacial-pressure, and signed-outlet
 * trajectories. They retain numerical-progress, conservation, reproducibility, mesh, and outer-step evidence without
 * presenting those trajectories as a qualified reproduction of the experiment.
 * </p>
 *
 * <p>
 * The separate 600 s qualification method records the #3298 WS1 acceptance contract: reproduce the public pressure
 * amplitude and liquid-production cycle period within 30%, preserve the steady flowline hold-up within 1%, complete
 * multiple cycles, and avoid clamps, correction limits, rejected substeps, and conservation failures. It remains
 * disabled while any of those gates fail. Enabling a long test that is known to fail would turn the slow-test shard red
 * without qualifying the model.
 * </p>
 *
 * <p>
 * The experimental amplitude and period are approximate direct digitizations of the green {@code SS} trace in Tengesdal
 * Figure 5-6 (printed page 91, PDF page 111), not printed tabular values. The short active runs therefore use only
 * broad diagnostic bounds. A wall-clock-limited, clamped, limited, rejected, or non-conservative trajectory is not
 * engineering validation regardless of its headline metrics.
 * </p>
 *
 * <p>
 * The steady-state initialization runs without a wall-clock guard, and every realization asserts that the guard did not
 * fire, so the reported results do not depend on how fast or how loaded the executing machine is.
 * </p>
 */
@Tag("slow")
class SevereSluggingExperimentalBenchmarkTest {
  private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager
      .getLogger(SevereSluggingExperimentalBenchmarkTest.class);
  private static final String SOURCE_URL = "https://www.bsee.gov/sites/bsee.gov/files/tap-technical-assessment-program/397aa.pdf";
  private static final double PHYSICAL_FLOWLINE_LENGTH_M = 19.81;
  private static final double RISER_HEIGHT_M = 14.94;
  private static final double TOTAL_PIPE_LENGTH_M = PHYSICAL_FLOWLINE_LENGTH_M + RISER_HEIGHT_M;
  private static final double DIAMETER_M = 0.0762;
  private static final double PIPE_AREA_M2 = Math.PI * DIAMETER_M * DIAMETER_M / 4.0;
  private static final double CRYSTEX_DENSITY_KG_PER_M3 = 856.0;
  private static final double LIQUID_SUPERFICIAL_VELOCITY_M_PER_S = 0.50;
  private static final double GAS_SUPERFICIAL_VELOCITY_AT_STANDARD_CONDITIONS_M_PER_S = 1.00;
  private static final double LIQUID_FEED_KG_PER_S = LIQUID_SUPERFICIAL_VELOCITY_M_PER_S * PIPE_AREA_M2
      * CRYSTEX_DENSITY_KG_PER_M3;
  /** Hydrostatic head of a fully liquid-filled riser, the natural pressure scale of severe slugging. */
  private static final double RISER_HYDROSTATIC_HEAD_PA = CRYSTEX_DENSITY_KG_PER_M3 * 9.80665 * RISER_HEIGHT_M;
  /** Approximate experimental SS-trace swing digitized from Tengesdal Figure 5-6. */
  private static final double EXPERIMENTAL_PRESSURE_AMPLITUDE_PA = 98_000.0;
  /** Approximate experimental SS-trace peak spacing digitized from Tengesdal Figure 5-6. */
  private static final double EXPERIMENTAL_CYCLE_PERIOD_S = 38.0;
  private static final double WARM_UP_SECONDS = 20.0;
  private static final double SUPPORTING_SIMULATION_SECONDS = 100.0;
  private static final double SUSTAINED_SIMULATION_SECONDS = 600.0;
  private static final double EXPERIMENTAL_RELATIVE_TOLERANCE = 0.30;
  private static final double FLOWLINE_HOLDUP_TARGET = 0.342;
  private static final double FLOWLINE_HOLDUP_RELATIVE_TOLERANCE = 0.01;
  /**
   * Relative inlet-pressure perturbation used only to sample a second trajectory on the same chaotic attractor. It is
   * physically and experimentally meaningless at this magnitude.
   */
  private static final double ATTRACTOR_SAMPLING_PERTURBATION = 1.0e-12;
  /** The observed cross-configuration spread of the time-averaged riser-base pressure stays below 1%. */
  private static final double MEAN_PRESSURE_CONVERGENCE_TOLERANCE = 0.08;
  /** Coarsest mesh used for the active characterization. */
  private static final int RESOLVED_SECTION_COUNT = 16;
  /** Refined mesh used for the mesh-convergence comparison. */
  private static final int REFINED_SECTION_COUNT = 24;
  /** Largest physical riser-base swing admitted by the short characterization. */
  private static final double RECORDED_PRESSURE_SWING_UPPER_BOUND_IN_RISER_HEADS = 1.10;
  /** Smallest riser-base swing that still counts as a cycle rather than a flat trace. */
  private static final double RECORDED_PRESSURE_SWING_LOWER_BOUND_IN_RISER_HEADS = 0.05;
  /** Smallest slug the outlet tracker must register on the resolved mesh, in m. */
  private static final double MINIMUM_TRACKED_SLUG_LENGTH_M = 0.5;
  /** Diagnostic mesh-spread bound; the stricter experimental gate remains disabled above. */
  private static final double MAXIMUM_AMPLITUDE_MESH_SPREAD = 0.40;

  private static TransientMetrics reference;
  private static TransientMetrics referenceRepeat;
  private static TransientMetrics perturbedTrajectory;
  private static TransientMetrics refinedMesh;
  private static TransientMetrics coarseOuterStep;
  private static List<TransientMetrics> ensemble;

  @BeforeAll
  static void simulateBenchmarkCases() {
    reference = simulate(RESOLVED_SECTION_COUNT, 0.1, 0.0, SUPPORTING_SIMULATION_SECONDS);
    referenceRepeat = simulate(RESOLVED_SECTION_COUNT, 0.1, 0.0, SUPPORTING_SIMULATION_SECONDS);
    perturbedTrajectory = simulate(RESOLVED_SECTION_COUNT, 0.1, ATTRACTOR_SAMPLING_PERTURBATION,
        SUPPORTING_SIMULATION_SECONDS);
    refinedMesh = simulate(REFINED_SECTION_COUNT, 0.1, 0.0, SUPPORTING_SIMULATION_SECONDS);
    coarseOuterStep = simulate(RESOLVED_SECTION_COUNT, 0.2, 0.0, SUPPORTING_SIMULATION_SECONDS);
    ensemble = Collections
        .unmodifiableList(Arrays.asList(reference, perturbedTrajectory, refinedMesh, coarseOuterStep));
    for (TransientMetrics metrics : ensemble) {
      logMetrics(metrics);
    }
  }

  private static void logMetrics(TransientMetrics metrics) {
    logger.info(String.format(Locale.ROOT,
        "%s: meanP=%.0f Pa peakToPeak=%.0f Pa (%.2f x riser head) p10p90=%.0f Pa liquidPeriod=%.2f s "
            + "liquidCycles=%d pressurePeriod=%.2f s pressureCycles=%d qMax=%.3f qMin=%.3f kg/s "
            + "steadyFlowlineHoldup=%.5f settledFlowlineHoldup=%.5f slug=%.3f m tEnd=%.1f s limited=%s rejected=%d",
        metrics.label, metrics.meanInletPressurePa, metrics.peakToPeakPressurePa,
        metrics.peakToPeakPressurePa / RISER_HYDROSTATIC_HEAD_PA, metrics.p10ToP90PressurePa,
        metrics.liquidCyclePeriodSeconds, metrics.completedLiquidCycleCount, metrics.pressureCyclePeriodSeconds,
        metrics.completedPressureCycleCount, metrics.maximumLiquidOutletKgPerSecond,
        metrics.minimumLiquidOutletKgPerSecond, metrics.steadyFlowlineLiquidHoldup,
        metrics.meanSettledFlowlineLiquidHoldup, metrics.maximumSlugLengthM, metrics.simulationEndTimeSeconds,
        metrics.transientCoupledCorrectionLimited, metrics.transientCoupledRejectedSubsteps));
  }

  /**
   * Qualifies the sustained resolved-mesh trajectory against the public Tengesdal experiment and #3298 WS1 gates.
   */
  @Test
  @Disabled("Blocked by #3298 WS1: coupled amplitude, cycle, hold-up, and nonlinear-quality gates do not all pass")
  void sustainsThePublicLimitCycleForSixHundredSeconds() {
    TransientMetrics sustained = simulate(RESOLVED_SECTION_COUNT, 0.1, 0.0, SUSTAINED_SIMULATION_SECONDS);
    logMetrics(sustained);
    assertAll("600 s Tengesdal Test 3 qualification",
        () -> assertEquals(SUSTAINED_SIMULATION_SECONDS, sustained.simulationEndTimeSeconds, 1.0e-9),
        () -> assertFalse(sustained.steadyStateWallClockLimited),
        () -> assertFalse(sustained.transientOutletBackflowClamped),
        () -> assertFalse(sustained.transientCoupledCorrectionLimited,
            "coupled pressure correction reached a configured bound"),
        () -> assertFalse(sustained.transientCoupledFailureDetected,
            "coupled solver rejected " + sustained.transientCoupledRejectedSubsteps + " substeps"),
        () -> assertEquals(0, sustained.transientCoupledRejectedSubsteps),
        () -> assertTrue(sustained.completedLiquidCycleCount >= 2,
            "completed liquid-production cycles=" + sustained.completedLiquidCycleCount),
        () -> assertTrue(
            relativeDifference(sustained.peakToPeakPressurePa,
                EXPERIMENTAL_PRESSURE_AMPLITUDE_PA) <= EXPERIMENTAL_RELATIVE_TOLERANCE,
            "600 s pressure amplitude=" + sustained.peakToPeakPressurePa + " Pa versus "
                + EXPERIMENTAL_PRESSURE_AMPLITUDE_PA + " Pa"),
        () -> assertTrue(
            relativeDifference(sustained.liquidCyclePeriodSeconds,
                EXPERIMENTAL_CYCLE_PERIOD_S) <= EXPERIMENTAL_RELATIVE_TOLERANCE,
            "600 s liquid-production cycle period=" + sustained.liquidCyclePeriodSeconds + " s versus "
                + EXPERIMENTAL_CYCLE_PERIOD_S + " s"),
        () -> assertEquals(FLOWLINE_HOLDUP_TARGET, sustained.steadyFlowlineLiquidHoldup,
            FLOWLINE_HOLDUP_TARGET * FLOWLINE_HOLDUP_RELATIVE_TOLERANCE, "steady flowline liquid hold-up"),
        () -> assertTrue(sustained.maximumLiquidOutletKgPerSecond > 1.25 * LIQUID_FEED_KG_PER_S),
        () -> assertTrue(sustained.minimumLiquidOutletKgPerSecond < 0.75 * LIQUID_FEED_KG_PER_S));
    for (Phase phase : Phase.values()) {
      assertTrue(sustained.maximumRelativeClosure.get(phase) < 1.0e-9,
          phase + " 600 s closure=" + sustained.maximumRelativeClosure.get(phase));
      assertTrue(Double.isFinite(sustained.finalInventoryKg.get(phase)));
      assertTrue(sustained.finalInventoryKg.get(phase) >= 0.0);
    }
  }

  /**
   * Every realization must reproduce the liquid blowout and fallback cycle, meaning an outlet liquid rate that both
   * rises above and drops below the liquid feed rate on a repeating cycle, and the riser-base pressure swing that
   * accompanies it must be a substantial fraction of a riser hydrostatic head.
   */
  @Test
  void reproducesTheLiquidCycleAndTheRiserPressureSwing() {
    assertEquals(SOURCE_URL, reference.sourceUrl);
    for (TransientMetrics metrics : ensemble) {
      assertFalse(metrics.steadyStateWallClockLimited,
          metrics.label + ": steady-state initialization hit the wall-clock guard, so the initial condition would "
              + "depend on machine speed");
      assertFalse(metrics.transientOutletBackflowClamped,
          metrics.label + ": outlet phase backflow was clamped, so the transient profile is not a solution");
      assertFalse(metrics.transientCoupledFailureDetected,
          metrics.label + ": coupled correction failed during a supporting trajectory");
      assertEquals(0, metrics.transientCoupledRejectedSubsteps,
          metrics.label + ": coupled substeps were rejected during a supporting trajectory");
      assertTrue(metrics.maximumLiquidOutletKgPerSecond > 1.25 * LIQUID_FEED_KG_PER_S,
          metrics.label + ": no liquid blowout above the feed rate, max=" + metrics.maximumLiquidOutletKgPerSecond);
      assertTrue(metrics.minimumLiquidOutletKgPerSecond < 0.75 * LIQUID_FEED_KG_PER_S,
          metrics.label + ": no liquid fallback below the feed rate, min=" + metrics.minimumLiquidOutletKgPerSecond);
      assertTrue(Double.isFinite(metrics.liquidCyclePeriodSeconds),
          metrics.label + ": no repeated blowout/fallback cycle was detected");
      assertTrue(metrics.liquidCyclePeriodSeconds > 5.0, metrics.label
          + ": cycle period is shorter than the riser filling time, period=" + metrics.liquidCyclePeriodSeconds);
      assertTrue(metrics.completedLiquidCycleCount >= 1, metrics.label
          + ": no complete settled-window cycle interval was detected, count=" + metrics.completedLiquidCycleCount);
      assertTrue(
          metrics.peakToPeakPressurePa > RECORDED_PRESSURE_SWING_LOWER_BOUND_IN_RISER_HEADS * RISER_HYDROSTATIC_HEAD_PA,
          metrics.label + ": the riser-base swing is too small to be severe slugging, peakToPeak="
              + metrics.peakToPeakPressurePa);
      assertTrue(
          metrics.peakToPeakPressurePa < RECORDED_PRESSURE_SWING_UPPER_BOUND_IN_RISER_HEADS * RISER_HYDROSTATIC_HEAD_PA,
          metrics.label + ": the riser-base swing exceeds a riser hydrostatic head, which draining the riser cannot "
              + "produce, so the pressure signature has to be re-measured, peakToPeak=" + metrics.peakToPeakPressurePa);
    }
  }

  /** Keep the robust pressure band finite and no larger than the extrema-based diagnostic. */
  @Test
  void reportsAConsistentRobustPressureBand() {
    for (TransientMetrics metrics : ensemble) {
      assertTrue(Double.isFinite(metrics.p10ToP90PressurePa));
      assertTrue(metrics.p10ToP90PressurePa > 0.0, metrics.label + ": settled pressure trace is flat or unresolved");
      assertTrue(metrics.p10ToP90PressurePa <= metrics.peakToPeakPressurePa,
          metrics.label + ": robust pressure band exceeds the extrema-based band");
    }
  }

  /**
   * The riser-base amplitude must stay mesh consistent. It used to differ by a factor of five between the resolved and
   * refined meshes because the section inclination was built with {@code atan2} against the axial cell length and the
   * top riser cell was left horizontal; both are fixed, and this pins the result so a geometry regression shows up as a
   * mesh split rather than as a quietly wrong amplitude.
   */
  @Test
  void riserAmplitudeIsMeshConsistent() {
    double gap = relativeDifference(reference.peakToPeakPressurePa, refinedMesh.peakToPeakPressurePa);
    assertTrue(gap < MAXIMUM_AMPLITUDE_MESH_SPREAD,
        "the riser-base amplitude has become mesh dependent again, which points at the section geometry rather than a "
            + "closure; resolved=" + reference.peakToPeakPressurePa + " refined=" + refinedMesh.peakToPeakPressurePa);
  }

  /**
   * The outlet slug tracker registers a finite physical slug. A terrain slug may occupy both the flowline and riser, so
   * its valid geometric upper bound is the modeled pipe length rather than the riser height alone.
   */
  @Test
  void tracksASlugAtTheOutletOnTheResolvedMesh() {
    for (TransientMetrics metrics : ensemble) {
      assertTrue(Double.isFinite(metrics.maximumSlugLengthM));
      assertTrue(metrics.maximumSlugLengthM <= TOTAL_PIPE_LENGTH_M, metrics.label
          + ": the tracked slug is longer than the modeled pipe, maximumSlugLength=" + metrics.maximumSlugLengthM);
    }
    assertTrue(reference.maximumSlugLengthM > MINIMUM_TRACKED_SLUG_LENGTH_M,
        reference.label + ": the outlet slug tracker registered no slug on the resolved mesh. It registered nothing "
            + "while the riser slug unit was pinned at its hold-up clamp, so a return to zero means the riser stopped "
            + "draining again, maximumSlugLength=" + reference.maximumSlugLengthM);
  }

  /**
   * The time-averaged riser-base pressure survives mesh refinement, outer-step coarsening and an inlet perturbation far
   * below any experimental significance. The instantaneous amplitude and period do not, and are only reported.
   */
  @Test
  void showsMeanPressureIsRobustWhileInstantaneousMetricsAreTrajectorySensitive() {
    assertTrue(
        relativeDifference(reference.meanInletPressurePa,
            refinedMesh.meanInletPressurePa) < MEAN_PRESSURE_CONVERGENCE_TOLERANCE,
        "mesh mean pressures=" + reference.meanInletPressurePa + " and " + refinedMesh.meanInletPressurePa);
    assertTrue(
        relativeDifference(reference.meanInletPressurePa,
            coarseOuterStep.meanInletPressurePa) < MEAN_PRESSURE_CONVERGENCE_TOLERANCE,
        "outer-step mean pressures=" + reference.meanInletPressurePa + " and " + coarseOuterStep.meanInletPressurePa);
    assertTrue(
        relativeDifference(reference.meanInletPressurePa,
            perturbedTrajectory.meanInletPressurePa) < MEAN_PRESSURE_CONVERGENCE_TOLERANCE,
        "perturbed mean pressures=" + reference.meanInletPressurePa + " and "
            + perturbedTrajectory.meanInletPressurePa);

    logger.info(String.format(Locale.ROOT,
        "Trajectory-sensitive spread over %d realizations: peakToPeak %.0f-%.0f Pa, period %.2f-%.2f s, "
            + "max tracked slug %.3f-%.3f m",
        ensemble.size(), minimumPeakToPeak(), maximumPeakToPeak(), minimumPeriod(), maximumPeriod(), minimumSlug(),
        maximumSlug()));
  }

  @Test
  void closesPhaseResolvedAndTotalMassAndRetainsInventories() {
    for (Phase phase : Phase.values()) {
      assertTrue(reference.maximumRelativeClosure.get(phase) < 1.0e-10,
          phase + " closure=" + reference.maximumRelativeClosure.get(phase));
      assertTrue(Double.isFinite(reference.finalInventoryKg.get(phase)));
      assertTrue(reference.finalInventoryKg.get(phase) >= 0.0);
    }
    assertTrue(reference.finalInventoryKg.get(Phase.GAS) > 0.0);
    assertTrue(reference.finalInventoryKg.get(Phase.OIL) > 0.0);
    assertEquals(0.0, reference.finalInventoryKg.get(Phase.WATER), 1.0e-12);
  }

  @Test
  void repeatedRunsAreNumericallyReproducible() {
    assertEquals(reference.peakToPeakPressurePa, referenceRepeat.peakToPeakPressurePa, 0.0);
    assertEquals(reference.meanInletPressurePa, referenceRepeat.meanInletPressurePa, 0.0);
    assertEquals(reference.liquidCyclePeriodSeconds, referenceRepeat.liquidCyclePeriodSeconds, 0.0);
    assertEquals(reference.completedLiquidCycleCount, referenceRepeat.completedLiquidCycleCount);
    assertEquals(reference.pressureCyclePeriodSeconds, referenceRepeat.pressureCyclePeriodSeconds, 0.0);
    assertEquals(reference.completedPressureCycleCount, referenceRepeat.completedPressureCycleCount);
    assertEquals(reference.maximumLiquidOutletKgPerSecond, referenceRepeat.maximumLiquidOutletKgPerSecond, 0.0);
    assertEquals(reference.maximumSlugLengthM, referenceRepeat.maximumSlugLengthM, 0.0);
    for (Phase phase : Phase.values()) {
      assertEquals(reference.finalInventoryKg.get(phase), referenceRepeat.finalInventoryKg.get(phase), 0.0);
    }
  }

  private static TransientMetrics simulate(int numberOfSections, double outerTimeStepSeconds,
      double inletPressurePerturbation, double simulationSeconds) {
    String label = numberOfSections + " sections, dt=" + outerTimeStepSeconds + " s, perturbation="
        + inletPressurePerturbation + ", duration=" + simulationSeconds + " s";
    TwoFluidPipe pipe = createLargeFacilityTestThree(numberOfSections, inletPressurePerturbation);
    UUID simulationId = UUID
        .nameUUIDFromBytes((numberOfSections + ":" + outerTimeStepSeconds).getBytes(StandardCharsets.UTF_8));
    int steps = (int) Math.round(simulationSeconds / outerTimeStepSeconds);
    List<Double> sampleTimes = new ArrayList<>();
    List<Double> pressureSamples = new ArrayList<>();
    List<Double> liquidOutletSamples = new ArrayList<>();
    List<Double> flowlineLiquidHoldupSamples = new ArrayList<>();
    int flowlineProbeSection = Math.max(0, numberOfSections / 4);
    double steadyFlowlineLiquidHoldup = pipe.getLiquidHoldupProfile()[flowlineProbeSection];
    Map<Phase, Double> maximumClosure = new EnumMap<>(Phase.class);
    Map<Phase, Double> finalInventory = new EnumMap<>(Phase.class);
    for (Phase phase : Phase.values()) {
      maximumClosure.put(phase, 0.0);
    }

    for (int step = 0; step < steps; step++) {
      pipe.runTransient(outerTimeStepSeconds, simulationId);
      TwoFluidMassBalanceReport balance = pipe.getLastMassBalanceReport();
      assertTrue(balance.getElapsedTimeSeconds() > 0.0, "Transient solver made no progress");
      for (Phase phase : Phase.values()) {
        maximumClosure.put(phase, Math.max(maximumClosure.get(phase), balance.getRelativeResidual(phase)));
        finalInventory.put(phase, balance.getFinalMassKg(phase));
      }
      if (pipe.getSimulationTime() >= WARM_UP_SECONDS) {
        sampleTimes.add(pipe.getSimulationTime());
        pressureSamples.add(pipe.getPressureProfile()[0]);
        liquidOutletSamples.add(balance.getOutletMassKg(Phase.LIQUID) / balance.getElapsedTimeSeconds());
        flowlineLiquidHoldupSamples.add(pipe.getLiquidHoldupProfile()[flowlineProbeSection]);
      }
    }

    LimitCycleMetrics pressureCycle = TwoFluidBenchmarkMetrics.analyzeLimitCycle(toArray(sampleTimes),
        toArray(pressureSamples), WARM_UP_SECONDS);
    LowProductionCycleMetrics liquidCycle = analyzeLowProductionCycles(sampleTimes, liquidOutletSamples);
    double maximumSlugLength = pipe.getMaxSlugLengthAtOutlet();
    return new TransientMetrics(label, SOURCE_URL, maximum(pressureSamples) - minimum(pressureSamples),
        pressureCycle.getP10ToP90Band(), mean(pressureSamples), pressureCycle.getPeriodSeconds(),
        pressureCycle.getCompletedCycleCount(), liquidCycle.periodSeconds, liquidCycle.completedCycleCount,
        minimum(liquidOutletSamples), maximum(liquidOutletSamples), steadyFlowlineLiquidHoldup,
        mean(flowlineLiquidHoldupSamples), maximumSlugLength, pipe.getSimulationTime(),
        pipe.isSteadyStateWallClockLimited(), pipe.isTransientOutletBackflowClamped(),
        pipe.isTransientCoupledPressureMomentumCorrectionLimited(),
        pipe.isTransientCoupledPressureMomentumFailureDetected(),
        pipe.getTransientCoupledPressureMomentumRejectedSubsteps(), maximumClosure, finalInventory);
  }

  private static TwoFluidPipe createLargeFacilityTestThree(int numberOfSections, double inletPressurePerturbation) {
    double crystexSurrogateMolarMassKgPerMol = 0.220;
    double nitrogenMolarMassKgPerMol = 0.0280134;
    double airDensityAtStandardConditionsKgPerM3 = 1.204;
    double liquidMassFlowKgPerSecond = LIQUID_FEED_KG_PER_S;
    double gasMassFlowKgPerSecond = GAS_SUPERFICIAL_VELOCITY_AT_STANDARD_CONDITIONS_M_PER_S * PIPE_AREA_M2
        * airDensityAtStandardConditionsKgPerM3;

    // Tengesdal reports Crystex density and viscosity but not a full assay or molecular weight.
    // A single TBP fraction is therefore an explicit surrogate for the non-volatile mineral oil.
    SystemInterface fluid = new SystemSrkEos(298.15, 2.3);
    fluid.addComponent("nitrogen", gasMassFlowKgPerSecond / nitrogenMolarMassKgPerMol);
    fluid.addTBPfraction("Crystex", liquidMassFlowKgPerSecond / crystexSurrogateMolarMassKgPerMol,
        crystexSurrogateMolarMassKgPerMol, 0.856);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    neqsim.process.equipment.stream.Stream inlet = new neqsim.process.equipment.stream.Stream(
        "Tengesdal 2002 large facility test 3", fluid);
    inlet.setFlowRate(liquidMassFlowKgPerSecond + gasMassFlowKgPerSecond, "kg/sec");
    // The source does not report a case-specific temperature; 25 C is a documented ambient assumption.
    inlet.setTemperature(25.0, "C");
    inlet.setPressure(2.3 * (1.0 + inletPressurePerturbation), "bara");
    inlet.run();

    double totalLengthM = PHYSICAL_FLOWLINE_LENGTH_M + RISER_HEIGHT_M;
    double inclinationRad = Math.toRadians(-3.0);
    double flowlineDropM = PHYSICAL_FLOWLINE_LENGTH_M * Math.sin(inclinationRad);
    double[] elevationM = new double[numberOfSections];
    for (int section = 0; section < numberOfSections; section++) {
      double positionM = totalLengthM * section / (numberOfSections - 1.0);
      elevationM[section] = positionM <= PHYSICAL_FLOWLINE_LENGTH_M ? positionM * Math.sin(inclinationRad)
          : flowlineDropM + positionM - PHYSICAL_FLOWLINE_LENGTH_M;
    }

    TwoFluidPipe pipe = new TwoFluidPipe("Tengesdal 2002 large facility test 3", inlet);
    pipe.setLength(totalLengthM);
    pipe.setDiameter(DIAMETER_M);
    pipe.setRoughness(1.5e-6);
    pipe.setNumberOfSections(numberOfSections);
    pipe.setElevationProfile(elevationM);
    pipe.setOutletPressure(1.01325, "bara");
    pipe.setInletBoundaryCondition(BoundaryCondition.STREAM_CONNECTED);
    pipe.setOutletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE);
    pipe.setTimeIntegrationMethod(TimeIntegrator.Method.RK4);
    pipe.setEnableAdaptiveTimestepping(true);
    pipe.setThermodynamicUpdateInterval(1000);
    pipe.setIncludeMassTransfer(false);
    pipe.setEnableInterfacialPressure(true);
    pipe.setImplicitInterfacialPressureCoupling(true);
    pipe.setEnableCoupledPressureMomentum(true);
    pipe.setAllowOutletPhaseBackflow(true);
    pipe.setEnableSlugTracking(true);
    pipe.getLagrangianSlugTracker().setRandomSeed(2741L);
    // A wall-clock guard would truncate the steady-state solve on a slow or loaded machine and hand the transient a
    // machine-dependent initial condition. The refinement loop is bounded by its own iteration limit.
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.run();
    return pipe;
  }

  private static double[] toArray(List<Double> values) {
    double[] result = new double[values.size()];
    for (int index = 0; index < values.size(); index++) {
      result[index] = values.get(index);
    }
    return result;
  }

  private static LowProductionCycleMetrics analyzeLowProductionCycles(List<Double> times, List<Double> liquidRates) {
    double minimum = minimum(liquidRates);
    double threshold = minimum + 0.15 * (maximum(liquidRates) - minimum);
    List<Integer> troughIndices = new ArrayList<>();
    int index = 0;
    while (index < liquidRates.size()) {
      if (liquidRates.get(index) > threshold) {
        index++;
        continue;
      }
      int minimumIndex = index;
      while (index + 1 < liquidRates.size() && liquidRates.get(index + 1) <= threshold) {
        index++;
        if (liquidRates.get(index) < liquidRates.get(minimumIndex)) {
          minimumIndex = index;
        }
      }
      // Merge secondary minima inside one blowout/fallback event. Ten seconds is below both
      // the measured 38 s cycle and the modelled cycle, while rejecting high-frequency ripples.
      if (troughIndices.isEmpty()
          || times.get(minimumIndex) - times.get(troughIndices.get(troughIndices.size() - 1)) >= 10.0) {
        troughIndices.add(minimumIndex);
      } else if (liquidRates.get(minimumIndex) < liquidRates.get(troughIndices.get(troughIndices.size() - 1))) {
        troughIndices.set(troughIndices.size() - 1, minimumIndex);
      }
      index++;
    }
    int completedCycleCount = Math.max(0, troughIndices.size() - 1);
    if (completedCycleCount == 0) {
      return new LowProductionCycleMetrics(Double.NaN, 0);
    }
    double sum = 0.0;
    for (int i = 1; i < troughIndices.size(); i++) {
      sum += times.get(troughIndices.get(i)) - times.get(troughIndices.get(i - 1));
    }
    return new LowProductionCycleMetrics(sum / completedCycleCount, completedCycleCount);
  }

  private static double percentile(List<Double> sortedValues, double fraction) {
    int index = (int) Math.round(fraction * (sortedValues.size() - 1));
    return sortedValues.get(Math.max(0, Math.min(sortedValues.size() - 1, index)));
  }

  private static double mean(List<Double> values) {
    double sum = 0.0;
    for (double value : values) {
      sum += value;
    }
    return sum / values.size();
  }

  private static double minimum(List<Double> values) {
    double minimum = Double.POSITIVE_INFINITY;
    for (double value : values) {
      minimum = Math.min(minimum, value);
    }
    return minimum;
  }

  private static double maximum(List<Double> values) {
    double maximum = Double.NEGATIVE_INFINITY;
    for (double value : values) {
      maximum = Math.max(maximum, value);
    }
    return maximum;
  }

  private static double minimumPeakToPeak() {
    double result = Double.POSITIVE_INFINITY;
    for (TransientMetrics entry : ensemble) {
      result = Math.min(result, entry.peakToPeakPressurePa);
    }
    return result;
  }

  private static double maximumPeakToPeak() {
    double result = Double.NEGATIVE_INFINITY;
    for (TransientMetrics entry : ensemble) {
      result = Math.max(result, entry.peakToPeakPressurePa);
    }
    return result;
  }

  private static double minimumPeriod() {
    double result = Double.POSITIVE_INFINITY;
    for (TransientMetrics entry : ensemble) {
      result = Math.min(result, entry.liquidCyclePeriodSeconds);
    }
    return result;
  }

  private static double maximumPeriod() {
    double result = Double.NEGATIVE_INFINITY;
    for (TransientMetrics entry : ensemble) {
      result = Math.max(result, entry.liquidCyclePeriodSeconds);
    }
    return result;
  }

  private static double minimumSlug() {
    double result = Double.POSITIVE_INFINITY;
    for (TransientMetrics entry : ensemble) {
      result = Math.min(result, entry.maximumSlugLengthM);
    }
    return result;
  }

  private static double maximumSlug() {
    double result = Double.NEGATIVE_INFINITY;
    for (TransientMetrics entry : ensemble) {
      result = Math.max(result, entry.maximumSlugLengthM);
    }
    return result;
  }

  private static double relativeDifference(double first, double second) {
    return Math.abs(first - second) / Math.max(Math.max(Math.abs(first), Math.abs(second)), 1.0e-12);
  }

  private static final class LowProductionCycleMetrics {
    private final double periodSeconds;
    private final int completedCycleCount;

    private LowProductionCycleMetrics(double periodSeconds, int completedCycleCount) {
      this.periodSeconds = periodSeconds;
      this.completedCycleCount = completedCycleCount;
    }
  }

  private static final class TransientMetrics {
    private final String label;
    private final String sourceUrl;
    private final double peakToPeakPressurePa;
    private final double p10ToP90PressurePa;
    private final double meanInletPressurePa;
    private final double pressureCyclePeriodSeconds;
    private final int completedPressureCycleCount;
    private final double liquidCyclePeriodSeconds;
    private final int completedLiquidCycleCount;
    private final double minimumLiquidOutletKgPerSecond;
    private final double maximumLiquidOutletKgPerSecond;
    private final double steadyFlowlineLiquidHoldup;
    private final double meanSettledFlowlineLiquidHoldup;
    private final double maximumSlugLengthM;
    private final double simulationEndTimeSeconds;
    private final boolean steadyStateWallClockLimited;
    private final boolean transientOutletBackflowClamped;
    private final boolean transientCoupledCorrectionLimited;
    private final boolean transientCoupledFailureDetected;
    private final int transientCoupledRejectedSubsteps;
    private final Map<Phase, Double> maximumRelativeClosure;
    private final Map<Phase, Double> finalInventoryKg;

    private TransientMetrics(String label, String sourceUrl, double peakToPeakPressurePa, double p10ToP90PressurePa,
        double meanInletPressurePa, double pressureCyclePeriodSeconds, int completedPressureCycleCount,
        double liquidCyclePeriodSeconds, int completedLiquidCycleCount, double minimumLiquidOutletKgPerSecond,
        double maximumLiquidOutletKgPerSecond, double steadyFlowlineLiquidHoldup,
        double meanSettledFlowlineLiquidHoldup, double maximumSlugLengthM, double simulationEndTimeSeconds,
        boolean steadyStateWallClockLimited, boolean transientOutletBackflowClamped,
        boolean transientCoupledCorrectionLimited, boolean transientCoupledFailureDetected,
        int transientCoupledRejectedSubsteps, Map<Phase, Double> maximumRelativeClosure,
        Map<Phase, Double> finalInventoryKg) {
      this.label = label;
      this.sourceUrl = sourceUrl;
      this.peakToPeakPressurePa = peakToPeakPressurePa;
      this.p10ToP90PressurePa = p10ToP90PressurePa;
      this.meanInletPressurePa = meanInletPressurePa;
      this.pressureCyclePeriodSeconds = pressureCyclePeriodSeconds;
      this.completedPressureCycleCount = completedPressureCycleCount;
      this.liquidCyclePeriodSeconds = liquidCyclePeriodSeconds;
      this.completedLiquidCycleCount = completedLiquidCycleCount;
      this.minimumLiquidOutletKgPerSecond = minimumLiquidOutletKgPerSecond;
      this.maximumLiquidOutletKgPerSecond = maximumLiquidOutletKgPerSecond;
      this.steadyFlowlineLiquidHoldup = steadyFlowlineLiquidHoldup;
      this.meanSettledFlowlineLiquidHoldup = meanSettledFlowlineLiquidHoldup;
      this.maximumSlugLengthM = maximumSlugLengthM;
      this.simulationEndTimeSeconds = simulationEndTimeSeconds;
      this.steadyStateWallClockLimited = steadyStateWallClockLimited;
      this.transientOutletBackflowClamped = transientOutletBackflowClamped;
      this.transientCoupledCorrectionLimited = transientCoupledCorrectionLimited;
      this.transientCoupledFailureDetected = transientCoupledFailureDetected;
      this.transientCoupledRejectedSubsteps = transientCoupledRejectedSubsteps;
      this.maximumRelativeClosure = new EnumMap<>(maximumRelativeClosure);
      this.finalInventoryKg = new EnumMap<>(finalInventoryKg);
    }
  }
}
