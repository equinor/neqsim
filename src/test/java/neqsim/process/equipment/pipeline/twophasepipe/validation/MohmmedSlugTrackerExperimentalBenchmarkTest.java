package neqsim.process.equipment.pipeline.twophasepipe.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.LagrangianSlugTracker;
import neqsim.process.equipment.pipeline.twophasepipe.LagrangianSlugTracker.SlugBubbleUnit;
import neqsim.process.equipment.pipeline.twophasepipe.LiquidAccumulationTracker.SlugCharacteristics;
import neqsim.process.equipment.pipeline.twophasepipe.TwoFluidSection;
import neqsim.process.equipment.pipeline.twophasepipe.validation.MohmmedSlugFlowBenchmark.ComparisonRow;
import neqsim.process.equipment.pipeline.twophasepipe.validation.MohmmedSlugFlowBenchmark.Point;

/**
 * Experimental screening of the tracked Taylor-bubble/tail translation closure against all 15 measured speeds.
 *
 * <p>
 * This is a controlled marker experiment in prescribed flow, not a TwoFluidPipe transient validation. The source does
 * not unambiguously distinguish liquid-front, centroid, and tail velocity. Tail translation is an explicit provisional
 * comparison convention; front translation is reported alongside it. No measured outcome supplies an initialization
 * parameter. The unknown inlet slug and film distributions are not inferred from measured lengths.
 * </p>
 *
 * <p>
 * Run deliberately with {@code -DexcludedTestGroups= -Dtest=MohmmedSlugTrackerExperimentalBenchmarkTest}. A failed
 * predeclared 20% engineering target remains a reported experimental-comparison failure; it must not be repaired by
 * fitting the same observations or widening tolerance. The benchmark tag excludes this open qualification gate from
 * default unit-test runs.
 * </p>
 */
@Tag("benchmark")
class MohmmedSlugTrackerExperimentalBenchmarkTest {
  private static final Logger logger = LogManager.getLogger(MohmmedSlugTrackerExperimentalBenchmarkTest.class);
  private static final double SAMPLE_DURATION = 0.10;

  @Test
  void comparesAllMeasuredTranslationsUsingAdvancedMarkerGeometry() throws Exception {
    List<Point> speedPoints = new ArrayList<Point>();
    String resource = "/neqsim/process/equipment/pipeline/twophasepipe/validation/"
        + "mohmmed_2018_slug_measurements.csv";
    try (InputStream input = getClass().getResourceAsStream(resource)) {
      assertNotNull(input);
      for (Point point : MohmmedSlugFlowBenchmark.readCsv(new InputStreamReader(input, StandardCharsets.UTF_8))) {
        if ("translation_velocity".equals(point.quantity)) {
          speedPoints.add(point);
        }
      }
    }
    assertEquals(15, speedPoints.size());
    Map<String, Double> predictions = new HashMap<String, Double>();
    StringBuilder refinementFailures = new StringBuilder();
    logger.info("Controlled tracker closure comparison; source speed definition is uncertain; all speeds in m/s");
    logger.info("point,jG,jL,measured,tail_dt_0.001,tail_dt_0.0005,front_dt_0.0005");
    for (Point point : speedPoints) {
      Transit coarse = advanceMarker(point, 0.001);
      Transit fine = advanceMarker(point, 0.0005);
      predictions.put(point.id, fine.tailSpeed);
      logger.info("{},{},{},{},{},{},{}", point.id, point.superficialGasVelocity, point.superficialLiquidVelocity,
          point.measuredValue, coarse.tailSpeed, fine.tailSpeed, fine.frontSpeed);
      if (!Double.isFinite(coarse.tailSpeed) || !Double.isFinite(fine.tailSpeed) || fine.tailSpeed <= 0.0
          || Math.abs(coarse.tailSpeed - fine.tailSpeed) > 0.01 * Math.abs(fine.tailSpeed)) {
        refinementFailures.append(point.id).append(" has missing or >1% time-refinement difference; ");
      }
    }
    List<ComparisonRow> rows = MohmmedSlugFlowBenchmark.compare(speedPoints, predictions);
    StringBuilder experimentalFailures = new StringBuilder();
    double absoluteRelativeErrorSum = 0.0;
    int failed = 0;
    for (ComparisonRow row : rows) {
      absoluteRelativeErrorSum += row.absoluteRelativeError;
      if (!row.withinEngineeringTolerance) {
        failed++;
        experimentalFailures.append(row.point.id).append(": predicted=").append(row.predictedValue)
            .append(", measured=").append(row.point.measuredValue).append(", relativeError=")
            .append(row.absoluteRelativeError).append("; ");
      }
    }
    logger.info("All 15 observations: mean absolute relative error={}, target failures={}",
        absoluteRelativeErrorSum / rows.size(), failed);
    assertTrue(refinementFailures.length() == 0 && experimentalFailures.length() == 0,
        "Controlled tail/translation screening only; source-definition and station limits remain. " + refinementFailures
            + experimentalFailures);
  }

  private Transit advanceMarker(Point point, double dt) {
    // Homogeneous bulk holdup and representative 24 C air/water properties are declared assumptions.
    // Neither the source speed nor any measured length/frequency is used in this experiment.
    double mixtureVelocity = point.superficialGasVelocity + point.superficialLiquidVelocity;
    double liquidHoldup = point.superficialLiquidVelocity / mixtureVelocity;
    TwoFluidSection[] sections = new TwoFluidSection[80];
    for (int index = 0; index < sections.length; index++) {
      TwoFluidSection section = new TwoFluidSection();
      section.setDiameter(point.diameter);
      section.setLength(0.1);
      section.setPosition((index + 0.5) * 0.1);
      section.setInclination(0.0);
      section.setPressure(point.pressure);
      section.setTemperature(point.temperature);
      section.setLiquidDensity(997.3);
      section.setWaterDensity(997.3);
      section.setGasDensity(1.188);
      section.setLiquidViscosity(0.00091);
      section.setWaterViscosity(0.00091);
      section.setGasViscosity(0.0000184);
      section.setLiquidHoldup(liquidHoldup);
      section.setGasHoldup(1.0 - liquidHoldup);
      section.setWaterCut(1.0);
      section.setGasVelocity(mixtureVelocity);
      section.setLiquidVelocity(mixtureVelocity);
      section.updateDerivedQuantities();
      section.updateConservativeVariables();
      sections[index] = section;
    }
    LagrangianSlugTracker tracker = new LagrangianSlugTracker(20260905L);
    tracker.setEnableInletSlugGeneration(false);
    tracker.setEnableStochasticInitiation(false);
    tracker.setEnableWakeEffects(false);
    tracker.setConservativeFilmCouplingEnabled(true);
    SlugCharacteristics marker = new SlugCharacteristics();
    marker.frontPosition = 4.0;
    marker.length = 10.0 * point.diameter;
    marker.tailPosition = marker.frontPosition - marker.length;
    marker.velocity = mixtureVelocity;
    marker.holdup = 0.9;
    marker.volume = marker.length * sections[0].getArea() * marker.holdup;
    SlugBubbleUnit slug = tracker.initializeTerrainSlug(marker, sections);
    double initialFront = slug.frontPosition;
    double initialTail = slug.tailPosition;
    int steps = (int) Math.round(SAMPLE_DURATION / dt);
    for (int step = 0; step < steps; step++) {
      tracker.advanceTimeStep(sections, dt);
      if (slug.hasExited || !tracker.getSlugs().contains(slug)) {
        return new Transit(Double.NaN, Double.NaN);
      }
    }
    double elapsed = steps * dt;
    return new Transit((slug.frontPosition - initialFront) / elapsed, (slug.tailPosition - initialTail) / elapsed);
  }

  private static final class Transit {
    private final double frontSpeed;
    private final double tailSpeed;

    private Transit(double frontSpeed, double tailSpeed) {
      this.frontSpeed = frontSpeed;
      this.tailSpeed = tailSpeed;
    }
  }
}
