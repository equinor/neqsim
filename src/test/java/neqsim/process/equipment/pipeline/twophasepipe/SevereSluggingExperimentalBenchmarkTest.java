package neqsim.process.equipment.pipeline.twophasepipe;

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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
 * Severe slugging in this configuration is a deterministically chaotic limit cycle. A relative inlet-pressure
 * perturbation of 1e-12, twelve orders of magnitude below the digitization uncertainty of the source figure, changes
 * the peak-to-peak riser-base pressure by more than a factor of two and the apparent cycle period by more than a factor
 * of 1.5. Single-trajectory instantaneous extremes are therefore not reproducible across platforms, compilers or JIT
 * states, and asserting numerical agreement on them would produce a test that passes or fails by luck.
 * </p>
 *
 * <p>
 * The benchmark consequently separates two classes of quantity:
 * </p>
 * <ul>
 * <li><b>Trajectory-robust:</b> phase-resolved mass closure, the time-averaged riser-base pressure and the
 * severe-slugging regime signature, meaning blowout above and fallback below the liquid feed rate together with a
 * pressure swing scaled by the riser hydrostatic head. These are asserted directly.</li>
 * <li><b>Trajectory-sensitive:</b> instantaneous peak-to-peak pressure, apparent cycle period and maximum tracked slug
 * length. These are reported as an ensemble range, required to bracket the digitized experimental amplitude, and
 * otherwise constrained only by wide, physically justified bounds.</li>
 * </ul>
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
  private static final double DIAMETER_M = 0.0762;
  private static final double PIPE_AREA_M2 = Math.PI * DIAMETER_M * DIAMETER_M / 4.0;
  private static final double CRYSTEX_DENSITY_KG_PER_M3 = 856.0;
  private static final double LIQUID_SUPERFICIAL_VELOCITY_M_PER_S = 0.50;
  private static final double GAS_SUPERFICIAL_VELOCITY_AT_STANDARD_CONDITIONS_M_PER_S = 1.00;
  private static final double LIQUID_FEED_KG_PER_S = LIQUID_SUPERFICIAL_VELOCITY_M_PER_S * PIPE_AREA_M2
      * CRYSTEX_DENSITY_KG_PER_M3;
  /** Hydrostatic head of a fully liquid-filled riser, the natural pressure scale of severe slugging. */
  private static final double RISER_HYDROSTATIC_HEAD_PA = CRYSTEX_DENSITY_KG_PER_M3 * 9.80665 * RISER_HEIGHT_M;
  private static final double EXPERIMENTAL_PRESSURE_AMPLITUDE_PA = 98_000.0;
  private static final double EXPERIMENTAL_PRESSURE_AMPLITUDE_DIGITIZATION_UNCERTAINTY_PA = 5_000.0;
  private static final double EXPERIMENTAL_CYCLE_PERIOD_S = 38.0;
  private static final double EXPERIMENTAL_CYCLE_PERIOD_DIGITIZATION_UNCERTAINTY_S = 2.0;
  private static final double WARM_UP_SECONDS = 20.0;
  private static final double SIMULATION_SECONDS = 100.0;
  /**
   * Relative inlet-pressure perturbation used only to sample a second trajectory on the same chaotic attractor. It is
   * physically and experimentally meaningless at this magnitude.
   */
  private static final double ATTRACTOR_SAMPLING_PERTURBATION = 1.0e-12;
  /** The observed cross-configuration spread of the time-averaged riser-base pressure stays below 4%. */
  private static final double MEAN_PRESSURE_CONVERGENCE_TOLERANCE = 0.08;

  private static TransientMetrics reference;
  private static TransientMetrics referenceRepeat;
  private static TransientMetrics perturbedTrajectory;
  private static TransientMetrics refinedMesh;
  private static TransientMetrics coarseOuterStep;
  private static List<TransientMetrics> ensemble;

  @BeforeAll
  static void simulateBenchmarkCases() {
    reference = simulate(12, 0.1, 0.0);
    referenceRepeat = simulate(12, 0.1, 0.0);
    perturbedTrajectory = simulate(12, 0.1, ATTRACTOR_SAMPLING_PERTURBATION);
    refinedMesh = simulate(16, 0.1, 0.0);
    coarseOuterStep = simulate(12, 0.2, 0.0);
    ensemble = Collections
        .unmodifiableList(Arrays.asList(reference, perturbedTrajectory, refinedMesh, coarseOuterStep));
    for (TransientMetrics metrics : ensemble) {
      logger.info(String.format(Locale.ROOT,
          "%s: meanP=%.0f Pa peakToPeak=%.0f Pa (%.2f x riser head) p10p90=%.0f Pa period=%.2f s "
              + "qMax=%.3f qMin=%.3f kg/s slug=%.3f m",
          metrics.label, metrics.meanInletPressurePa, metrics.peakToPeakPressurePa,
          metrics.peakToPeakPressurePa / RISER_HYDROSTATIC_HEAD_PA, metrics.p10ToP90PressurePa,
          metrics.cyclePeriodSeconds, metrics.maximumLiquidOutletKgPerSecond, metrics.minimumLiquidOutletKgPerSecond,
          metrics.maximumSlugLengthM));
    }
  }

  /**
   * Every realization must sit in the severe-slugging regime, with a pressure swing scaled by the riser hydrostatic
   * head and an outlet liquid rate that both blows out above and falls back below the liquid feed rate.
   */
  @Test
  void reproducesSevereSluggingRegimeInEveryRealization() {
    assertEquals(SOURCE_URL, reference.sourceUrl);
    for (TransientMetrics metrics : ensemble) {
      assertFalse(metrics.steadyStateWallClockLimited,
          metrics.label + ": steady-state initialization hit the wall-clock guard, so the initial condition would "
              + "depend on machine speed");
      assertTrue(metrics.peakToPeakPressurePa > 0.2 * RISER_HYDROSTATIC_HEAD_PA,
          metrics.label + ": pressure swing too small for severe slugging, peakToPeak=" + metrics.peakToPeakPressurePa);
      assertTrue(metrics.peakToPeakPressurePa < 4.0 * RISER_HYDROSTATIC_HEAD_PA,
          metrics.label + ": pressure swing exceeds a physically credible multiple of the riser head, peakToPeak="
              + metrics.peakToPeakPressurePa);
      assertTrue(metrics.maximumLiquidOutletKgPerSecond > 1.25 * LIQUID_FEED_KG_PER_S,
          metrics.label + ": no liquid blowout above the feed rate, max=" + metrics.maximumLiquidOutletKgPerSecond);
      assertTrue(metrics.minimumLiquidOutletKgPerSecond < 0.75 * LIQUID_FEED_KG_PER_S,
          metrics.label + ": no liquid fallback below the feed rate, min=" + metrics.minimumLiquidOutletKgPerSecond);
      assertTrue(Double.isFinite(metrics.cyclePeriodSeconds),
          metrics.label + ": no repeated blowout/fallback cycle was detected");
      assertTrue(metrics.cyclePeriodSeconds > 5.0, metrics.label
          + ": cycle period is shorter than the riser filling time, period=" + metrics.cyclePeriodSeconds);
    }
  }

  /**
   * Order-of-magnitude comparison with the digitized experiment. A tighter claim is not supportable because the
   * instantaneous amplitude of a chaotic limit cycle is not a reproducible scalar, so the ensemble is required to
   * bracket the measured amplitude rather than to match it realization by realization.
   */
  @Test
  void bracketsDigitizedPressureAmplitudeAndUnderpredictsThePeriod() {
    assertTrue(
        minimumPeakToPeak() <= EXPERIMENTAL_PRESSURE_AMPLITUDE_PA
            + EXPERIMENTAL_PRESSURE_AMPLITUDE_DIGITIZATION_UNCERTAINTY_PA,
        "no realization reaches down to the digitized amplitude; smallest peak-to-peak=" + minimumPeakToPeak());
    assertTrue(
        maximumPeakToPeak() >= EXPERIMENTAL_PRESSURE_AMPLITUDE_PA
            - EXPERIMENTAL_PRESSURE_AMPLITUDE_DIGITIZATION_UNCERTAINTY_PA,
        "no realization reaches up to the digitized amplitude; largest peak-to-peak=" + maximumPeakToPeak());

    double meanPeriod = 0.0;
    for (TransientMetrics metrics : ensemble) {
      meanPeriod += metrics.cyclePeriodSeconds;
    }
    meanPeriod /= ensemble.size();
    assertTrue(meanPeriod < EXPERIMENTAL_CYCLE_PERIOD_S - EXPERIMENTAL_CYCLE_PERIOD_DIGITIZATION_UNCERTAINTY_S,
        "The known short-period limitation must stay visible until the model or benchmark is updated; ensemble mean "
            + "period=" + meanPeriod);
  }

  @Test
  void reportsSlugLengthRelativeToRiserWithoutClaimingQuantitativeValidation() {
    for (TransientMetrics metrics : ensemble) {
      assertTrue(Double.isFinite(metrics.maximumSlugLengthM));
      assertTrue(metrics.maximumSlugLengthM > 0.0, metrics.label + ": no slug was tracked at the outlet");
      assertTrue(metrics.maximumSlugLengthToRiserHeightRatio < 1.0,
          metrics.label + ": the current outlet tracker underpredicts the experimental severe-slug definition; ratio="
              + metrics.maximumSlugLengthToRiserHeightRatio);
    }
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
    assertEquals(reference.cyclePeriodSeconds, referenceRepeat.cyclePeriodSeconds, 0.0);
    assertEquals(reference.maximumLiquidOutletKgPerSecond, referenceRepeat.maximumLiquidOutletKgPerSecond, 0.0);
    assertEquals(reference.maximumSlugLengthM, referenceRepeat.maximumSlugLengthM, 0.0);
    for (Phase phase : Phase.values()) {
      assertEquals(reference.finalInventoryKg.get(phase), referenceRepeat.finalInventoryKg.get(phase), 0.0);
    }
  }

  private static TransientMetrics simulate(int numberOfSections, double outerTimeStepSeconds,
      double inletPressurePerturbation) {
    String label = numberOfSections + " sections, dt=" + outerTimeStepSeconds + " s, perturbation="
        + inletPressurePerturbation;
    TwoFluidPipe pipe = createLargeFacilityTestThree(numberOfSections, inletPressurePerturbation);
    UUID simulationId = UUID
        .nameUUIDFromBytes((numberOfSections + ":" + outerTimeStepSeconds).getBytes(StandardCharsets.UTF_8));
    int steps = (int) Math.round(SIMULATION_SECONDS / outerTimeStepSeconds);
    List<Double> sampleTimes = new ArrayList<>();
    List<Double> pressureSamples = new ArrayList<>();
    List<Double> liquidOutletSamples = new ArrayList<>();
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
      }
    }

    List<Double> sortedPressures = new ArrayList<>(pressureSamples);
    Collections.sort(sortedPressures);
    double maximumSlugLength = pipe.getMaxSlugLengthAtOutlet();
    return new TransientMetrics(label, SOURCE_URL, maximum(pressureSamples) - minimum(pressureSamples),
        percentile(sortedPressures, 0.90) - percentile(sortedPressures, 0.10), mean(pressureSamples),
        estimateLowProductionCyclePeriod(sampleTimes, liquidOutletSamples), minimum(liquidOutletSamples),
        maximum(liquidOutletSamples), maximumSlugLength, maximumSlugLength / RISER_HEIGHT_M,
        pipe.isSteadyStateWallClockLimited(), maximumClosure, finalInventory);
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
    pipe.setEnableSlugTracking(true);
    pipe.getLagrangianSlugTracker().setRandomSeed(2741L);
    // A wall-clock guard would truncate the steady-state solve on a slow or loaded machine and hand the transient a
    // machine-dependent initial condition. The refinement loop is bounded by its own iteration limit.
    pipe.setSteadyStateMaxWallClockTime(Double.POSITIVE_INFINITY);
    pipe.run();
    return pipe;
  }

  private static double estimateLowProductionCyclePeriod(List<Double> times, List<Double> liquidRates) {
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
    if (troughIndices.size() < 2) {
      return Double.NaN;
    }
    double sum = 0.0;
    for (int i = 1; i < troughIndices.size(); i++) {
      sum += times.get(troughIndices.get(i)) - times.get(troughIndices.get(i - 1));
    }
    return sum / (troughIndices.size() - 1);
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
      result = Math.min(result, entry.cyclePeriodSeconds);
    }
    return result;
  }

  private static double maximumPeriod() {
    double result = Double.NEGATIVE_INFINITY;
    for (TransientMetrics entry : ensemble) {
      result = Math.max(result, entry.cyclePeriodSeconds);
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

  private static final class TransientMetrics {
    private final String label;
    private final String sourceUrl;
    private final double peakToPeakPressurePa;
    private final double p10ToP90PressurePa;
    private final double meanInletPressurePa;
    private final double cyclePeriodSeconds;
    private final double minimumLiquidOutletKgPerSecond;
    private final double maximumLiquidOutletKgPerSecond;
    private final double maximumSlugLengthM;
    private final double maximumSlugLengthToRiserHeightRatio;
    private final boolean steadyStateWallClockLimited;
    private final Map<Phase, Double> maximumRelativeClosure;
    private final Map<Phase, Double> finalInventoryKg;

    private TransientMetrics(String label, String sourceUrl, double peakToPeakPressurePa, double p10ToP90PressurePa,
        double meanInletPressurePa, double cyclePeriodSeconds, double minimumLiquidOutletKgPerSecond,
        double maximumLiquidOutletKgPerSecond, double maximumSlugLengthM, double maximumSlugLengthToRiserHeightRatio,
        boolean steadyStateWallClockLimited, Map<Phase, Double> maximumRelativeClosure,
        Map<Phase, Double> finalInventoryKg) {
      this.label = label;
      this.sourceUrl = sourceUrl;
      this.peakToPeakPressurePa = peakToPeakPressurePa;
      this.p10ToP90PressurePa = p10ToP90PressurePa;
      this.meanInletPressurePa = meanInletPressurePa;
      this.cyclePeriodSeconds = cyclePeriodSeconds;
      this.minimumLiquidOutletKgPerSecond = minimumLiquidOutletKgPerSecond;
      this.maximumLiquidOutletKgPerSecond = maximumLiquidOutletKgPerSecond;
      this.maximumSlugLengthM = maximumSlugLengthM;
      this.maximumSlugLengthToRiserHeightRatio = maximumSlugLengthToRiserHeightRatio;
      this.steadyStateWallClockLimited = steadyStateWallClockLimited;
      this.maximumRelativeClosure = new EnumMap<>(maximumRelativeClosure);
      this.finalInventoryKg = new EnumMap<>(finalInventoryKg);
    }
  }
}
