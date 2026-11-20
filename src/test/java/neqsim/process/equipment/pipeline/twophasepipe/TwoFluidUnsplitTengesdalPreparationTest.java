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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import neqsim.process.equipment.pipeline.TwoFluidBenchmarkMetrics;
import neqsim.process.equipment.pipeline.TwoFluidBenchmarkMetrics.LimitCycleMetrics;
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
 * The short handoff checks conservative preparation and isolation. The explicit opt-in five-second gate retains known
 * negative evidence and must complete before the longer slug characterization sequence is attempted. Neither test
 * qualifies experimental amplitude or period; no prepared state is published to the pipe.
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

  /** Qualify the published unsplit trajectory against the unchanged public experimental gates. */
  @Tag("slow")
  @Test
  void sustainedPublishedTrajectoryMatchesPublicLimitCycle() {
    final int count = 16;
    final double maximumStep = 0.1;
    final double outerStep = 1.0;
    final double duration = 600.0;
    final double warmUp = 20.0;
    final double holdupTarget = 0.342;
    final double holdupTolerance = 0.01 * holdupTarget;
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
    UUID id = UUID.nameUUIDFromBytes("published-tengesdal-experiment".getBytes(StandardCharsets.UTF_8));
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
      if (pipe.getSimulationTime() >= warmUp) {
        times.add(pipe.getSimulationTime());
        pressures.add(pipe.getPressureProfile()[0]);
        liquidRates.add(report.getOutletMassKg(Phase.LIQUID) / report.getElapsedTimeSeconds());
      }
    }
    LimitCycleMetrics pressureCycle = TwoFluidBenchmarkMetrics.analyzeLimitCycle(toArray(times), toArray(pressures),
        warmUp);
    SevereSluggingExperimentalBenchmarkTest.LowProductionCycleMetrics liquidCycle = SevereSluggingExperimentalBenchmarkTest
        .analyzeLowProductionCycles(times, liquidRates);
    double pressureAmplitude = maximum(pressures) - minimum(pressures);
    double minimumLiquidRate = minimum(liquidRates);
    double maximumLiquidRate = maximum(liquidRates);
    double meanLiquidFeed = inletLiquidMass / duration;
    final int totalRejectedAttempts = rejectedAttempts;
    logger.info(
        "Published unsplit Tengesdal experiment: amplitude={} Pa, pressurePeriod={} s, pressureCycles={}, "
            + "liquidPeriod={} s, liquidCycles={}, qMin={}, qMax={}, meanFeed={}, steadyHoldup={}, rejects={}, "
            + "evaluations={}, gasClosure={}, oilClosure={}, waterClosure={}",
        pressureAmplitude, pressureCycle.getPeriodSeconds(), pressureCycle.getCompletedCycleCount(),
        liquidCycle.periodSeconds, liquidCycle.completedCycleCount, minimumLiquidRate, maximumLiquidRate,
        meanLiquidFeed, steadyFlowlineHoldup, rejectedAttempts, modelEvaluations, maximumClosure.get(Phase.GAS),
        maximumClosure.get(Phase.OIL), maximumClosure.get(Phase.WATER));
    assertAll("600 s published unsplit Tengesdal qualification",
        () -> assertEquals(duration, pipe.getSimulationTime(), 0.0),
        () -> assertEquals(0, totalRejectedAttempts, "rejected nonlinear attempts"),
        () -> assertTrue(liquidCycle.completedCycleCount >= 2,
            "completed liquid-production cycles=" + liquidCycle.completedCycleCount),
        () -> SevereSluggingExperimentalBenchmarkTest.assertExperimentalAgreement(pressureAmplitude,
            liquidCycle.periodSeconds),
        () -> assertEquals(holdupTarget, steadyFlowlineHoldup, holdupTolerance, "steady flowline liquid hold-up"),
        () -> assertTrue(maximumLiquidRate > 1.25 * meanLiquidFeed, "no liquid blowout above the mean feed"),
        () -> assertTrue(minimumLiquidRate < 0.75 * meanLiquidFeed, "no liquid fallback below the mean feed"));
    for (Phase phase : Phase.values()) {
      assertTrue(maximumClosure.get(phase) < 1.0e-9, phase + " 600 s closure=" + maximumClosure.get(phase));
      assertTrue(Double.isFinite(finalInventory.get(phase)));
      assertTrue(finalInventory.get(phase) >= 0.0);
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
    logger.info(
        "Published unsplit Tengesdal interval: cells={}, maximumStep={}, duration={}, substeps={}, gasClosure={}, oilClosure={}, waterClosure={}, outletFlow={}",
        count, maximumStep, duration, report.getAcceptedSubsteps(), report.getRelativeResidual(Phase.GAS),
        report.getRelativeResidual(Phase.OIL), report.getRelativeResidual(Phase.WATER),
        pipe.getOutletStream().getFlowRate("kg/sec"));
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
