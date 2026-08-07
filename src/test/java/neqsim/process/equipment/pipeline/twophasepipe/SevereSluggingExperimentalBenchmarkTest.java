package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
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
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Public dynamic benchmark from Tengesdal's 2002 large pipeline-riser facility. */
@Tag("slow")
class SevereSluggingExperimentalBenchmarkTest {
  private static final String SOURCE_URL = "https://www.bsee.gov/sites/bsee.gov/files/tap-technical-assessment-program/397aa.pdf";
  private static final double PHYSICAL_FLOWLINE_LENGTH_M = 19.81;
  private static final double RISER_HEIGHT_M = 14.94;
  private static final double DIAMETER_M = 0.0762;
  private static final double LIQUID_SUPERFICIAL_VELOCITY_M_PER_S = 0.50;
  private static final double GAS_SUPERFICIAL_VELOCITY_AT_STANDARD_CONDITIONS_M_PER_S = 1.00;
  private static final double EXPERIMENTAL_PRESSURE_AMPLITUDE_PA = 98_000.0;
  private static final double EXPERIMENTAL_PRESSURE_AMPLITUDE_DIGITIZATION_UNCERTAINTY_PA = 5_000.0;
  private static final double EXPERIMENTAL_CYCLE_PERIOD_S = 38.0;
  private static final double EXPERIMENTAL_CYCLE_PERIOD_DIGITIZATION_UNCERTAINTY_S = 2.0;
  private static final double WARM_UP_SECONDS = 20.0;
  private static final double SIMULATION_SECONDS = 100.0;

  private static TransientMetrics fine;
  private static TransientMetrics refinedMesh;
  private static TransientMetrics coarseOuterStep;
  private static TransientMetrics repeated;

  @BeforeAll
  static void simulateBenchmarkCases() {
    fine = simulate(12, 0.1, true);
    repeated = simulate(12, 0.1, true);
    refinedMesh = simulate(16, 0.1, true);
    coarseOuterStep = simulate(12, 0.2, true);
  }

  @Test
  void reproducesPressureAmplitudeAndLiquidCyclingButExposesShortPeriod() {
    assertEquals(SOURCE_URL, fine.sourceUrl);
    assertTrue(fine.pressureAmplitudePa > EXPERIMENTAL_PRESSURE_AMPLITUDE_DIGITIZATION_UNCERTAINTY_PA);
    assertTrue(fine.maximumLiquidOutletKgPerSecond > 5.0 * fine.minimumLiquidOutletKgPerSecond);
    assertTrue(
        relativeError(fine.pressureAmplitudePa, EXPERIMENTAL_PRESSURE_AMPLITUDE_PA) <= 0.15
            + EXPERIMENTAL_PRESSURE_AMPLITUDE_DIGITIZATION_UNCERTAINTY_PA / EXPERIMENTAL_PRESSURE_AMPLITUDE_PA,
        "pressure amplitude=" + fine.pressureAmplitudePa);
    assertTrue(Double.isFinite(fine.cyclePeriodSeconds));
    assertTrue(
        fine.cyclePeriodSeconds < EXPERIMENTAL_CYCLE_PERIOD_S - EXPERIMENTAL_CYCLE_PERIOD_DIGITIZATION_UNCERTAINTY_S,
        "cycle period=" + fine.cyclePeriodSeconds);
    assertTrue(fine.cyclePeriodSeconds <= 0.75 * EXPERIMENTAL_CYCLE_PERIOD_S,
        "The known short-period limitation must stay visible until the model or benchmark is updated; period="
            + fine.cyclePeriodSeconds);
  }

  @Test
  void reportsSlugLengthRelativeToRiserWithoutClaimingQuantitativeValidation() {
    assertTrue(Double.isFinite(fine.maximumSlugLengthM));
    assertTrue(fine.maximumSlugLengthM > 0.0);
    assertTrue(fine.maximumSlugLengthToRiserHeightRatio > 0.0);
    assertTrue(fine.maximumSlugLengthToRiserHeightRatio < 1.0,
        "The current outlet tracker underpredicts the experimental severe-slug definition; ratio="
            + fine.maximumSlugLengthToRiserHeightRatio);
  }

  @Test
  void demonstratesMeshAndOuterTimestepSensitivity() {
    assertTrue(relativeDifference(fine.pressureAmplitudePa, refinedMesh.pressureAmplitudePa) < 0.20,
        "mesh pressure amplitudes=" + fine.pressureAmplitudePa + " and " + refinedMesh.pressureAmplitudePa);
    assertTrue(relativeDifference(fine.cyclePeriodSeconds, refinedMesh.cyclePeriodSeconds) < 0.30,
        "mesh periods=" + fine.cyclePeriodSeconds + " and " + refinedMesh.cyclePeriodSeconds);
    assertTrue(relativeDifference(fine.pressureAmplitudePa, coarseOuterStep.pressureAmplitudePa) < 0.20,
        "outer-step pressure-amplitude difference");
    assertTrue(relativeDifference(fine.cyclePeriodSeconds, coarseOuterStep.cyclePeriodSeconds) < 0.20,
        "outer-step period difference");
  }

  @Test
  void closesPhaseResolvedAndTotalMassAndRetainsInventories() {
    for (Phase phase : Phase.values()) {
      assertTrue(fine.maximumRelativeClosure.get(phase) < 1.0e-10,
          phase + " closure=" + fine.maximumRelativeClosure.get(phase));
      assertTrue(Double.isFinite(fine.finalInventoryKg.get(phase)));
      assertTrue(fine.finalInventoryKg.get(phase) >= 0.0);
    }
    assertTrue(fine.finalInventoryKg.get(Phase.GAS) > 0.0);
    assertTrue(fine.finalInventoryKg.get(Phase.OIL) > 0.0);
    assertEquals(0.0, fine.finalInventoryKg.get(Phase.WATER), 1.0e-12);
  }

  @Test
  void repeatedRunsAreNumericallyReproducible() {
    assertEquals(fine.pressureAmplitudePa, repeated.pressureAmplitudePa, 0.0);
    assertEquals(fine.cyclePeriodSeconds, repeated.cyclePeriodSeconds, 0.0);
    assertEquals(fine.maximumLiquidOutletKgPerSecond, repeated.maximumLiquidOutletKgPerSecond, 0.0);
    assertEquals(fine.maximumSlugLengthM, repeated.maximumSlugLengthM, 0.0);
    for (Phase phase : Phase.values()) {
      assertEquals(fine.finalInventoryKg.get(phase), repeated.finalInventoryKg.get(phase), 0.0);
    }
  }

  private static TransientMetrics simulate(int numberOfSections, double outerTimeStepSeconds,
      boolean enableSlugTracking) {
    TwoFluidPipe pipe = createLargeFacilityTestThree(numberOfSections, enableSlugTracking);
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

    double pressureAmplitude = maximum(pressureSamples) - minimum(pressureSamples);
    double minimumLiquidOutlet = minimum(liquidOutletSamples);
    double maximumLiquidOutlet = maximum(liquidOutletSamples);
    double cyclePeriod = estimateLowProductionCyclePeriod(sampleTimes, liquidOutletSamples);
    double maximumSlugLength = pipe.getMaxSlugLengthAtOutlet();
    return new TransientMetrics(SOURCE_URL, pressureAmplitude, cyclePeriod, minimumLiquidOutlet, maximumLiquidOutlet,
        maximumSlugLength, maximumSlugLength / RISER_HEIGHT_M, maximumClosure, finalInventory);
  }

  private static TwoFluidPipe createLargeFacilityTestThree(int numberOfSections, boolean enableSlugTracking) {
    double areaM2 = Math.PI * DIAMETER_M * DIAMETER_M / 4.0;
    double crystexDensityKgPerM3 = 856.0;
    double airDensityAtStandardConditionsKgPerM3 = 1.204;
    double crystexSurrogateMolarMassKgPerMol = 0.220;
    double nitrogenMolarMassKgPerMol = 0.0280134;
    double liquidMassFlowKgPerSecond = LIQUID_SUPERFICIAL_VELOCITY_M_PER_S * areaM2 * crystexDensityKgPerM3;
    double gasMassFlowKgPerSecond = GAS_SUPERFICIAL_VELOCITY_AT_STANDARD_CONDITIONS_M_PER_S * areaM2
        * airDensityAtStandardConditionsKgPerM3;

    // Tengesdal reports Crystex density and viscosity but not a full assay or molecular weight.
    // A single TBP fraction is therefore an explicit surrogate for the non-volatile mineral oil.
    SystemInterface fluid = new SystemSrkEos(298.15, 2.3);
    fluid.addComponent("nitrogen", gasMassFlowKgPerSecond / nitrogenMolarMassKgPerMol);
    fluid.addTBPfraction("Crystex", liquidMassFlowKgPerSecond / crystexSurrogateMolarMassKgPerMol,
        crystexSurrogateMolarMassKgPerMol, 0.856);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    Stream inlet = new Stream("Tengesdal 2002 large facility test 3", fluid);
    inlet.setFlowRate(liquidMassFlowKgPerSecond + gasMassFlowKgPerSecond, "kg/sec");
    // The source does not report a case-specific temperature; 25 C is a documented ambient assumption.
    inlet.setTemperature(25.0, "C");
    inlet.setPressure(2.3, "bara");
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
    pipe.setEnableSlugTracking(enableSlugTracking);
    pipe.getLagrangianSlugTracker().setRandomSeed(2741L);
    pipe.setSteadyStateMaxWallClockTime(60.0);
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

  private static double relativeError(double actual, double reference) {
    return Math.abs(actual - reference) / Math.abs(reference);
  }

  private static double relativeDifference(double first, double second) {
    return Math.abs(first - second) / Math.max(Math.max(Math.abs(first), Math.abs(second)), 1.0e-12);
  }

  private static final class TransientMetrics {
    private final String sourceUrl;
    private final double pressureAmplitudePa;
    private final double cyclePeriodSeconds;
    private final double minimumLiquidOutletKgPerSecond;
    private final double maximumLiquidOutletKgPerSecond;
    private final double maximumSlugLengthM;
    private final double maximumSlugLengthToRiserHeightRatio;
    private final Map<Phase, Double> maximumRelativeClosure;
    private final Map<Phase, Double> finalInventoryKg;

    private TransientMetrics(String sourceUrl, double pressureAmplitudePa, double cyclePeriodSeconds,
        double minimumLiquidOutletKgPerSecond, double maximumLiquidOutletKgPerSecond, double maximumSlugLengthM,
        double maximumSlugLengthToRiserHeightRatio, Map<Phase, Double> maximumRelativeClosure,
        Map<Phase, Double> finalInventoryKg) {
      this.sourceUrl = sourceUrl;
      this.pressureAmplitudePa = pressureAmplitudePa;
      this.cyclePeriodSeconds = cyclePeriodSeconds;
      this.minimumLiquidOutletKgPerSecond = minimumLiquidOutletKgPerSecond;
      this.maximumLiquidOutletKgPerSecond = maximumLiquidOutletKgPerSecond;
      this.maximumSlugLengthM = maximumSlugLengthM;
      this.maximumSlugLengthToRiserHeightRatio = maximumSlugLengthToRiserHeightRatio;
      this.maximumRelativeClosure = new EnumMap<>(maximumRelativeClosure);
      this.finalInventoryKg = new EnumMap<>(finalInventoryKg);
    }
  }
}
