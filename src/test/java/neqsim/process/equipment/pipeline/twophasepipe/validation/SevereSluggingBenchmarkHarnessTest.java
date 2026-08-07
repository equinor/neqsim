package neqsim.process.equipment.pipeline.twophasepipe.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.SevereSluggingSystemDiagnostic;
import neqsim.process.equipment.pipeline.twophasepipe.SevereSluggingSystemDiagnostic.Input;
import neqsim.process.equipment.pipeline.twophasepipe.validation.SevereSluggingBenchmarkHarness.ConfusionMatrix;
import neqsim.process.equipment.pipeline.twophasepipe.validation.SevereSluggingBenchmarkHarness.FlowMapPoint;
import neqsim.process.equipment.pipeline.twophasepipe.validation.SevereSluggingBenchmarkHarness.ObservedRegime;
import neqsim.process.equipment.pipeline.twophasepipe.validation.SevereSluggingBenchmarkHarness.Prediction;

class SevereSluggingBenchmarkHarnessTest {
  private static final double PIPE_DIAMETER_M = 0.0762;
  private static final double EFFECTIVE_FLOWLINE_LENGTH_M = 85.344;
  private static final double RISER_HEIGHT_M = 14.94;
  private static final double CRYSTEX_DENSITY_KG_PER_M3 = 856.0;
  private static final double SEPARATOR_PRESSURE_PA = 101_325.0;
  private static final double TAITEL_AIR_WATER_GAS_CAP_VOID_FRACTION = 0.89;

  @Test
  void readsTengesdalFlowMapWithMeasurementAndDigitizationUncertainty() throws Exception {
    List<FlowMapPoint> points = readTengesdalFlowMap();

    assertEquals(55, points.size());
    assertEquals(26, count(points, ObservedRegime.SEVERE_SLUG));
    assertEquals(14, count(points, ObservedRegime.TRANSITION));
    assertEquals(15, count(points, ObservedRegime.STABLE));
    assertTrue(points.stream().allMatch(point -> point.getSuperficialLiquidVelocityUncertaintyMPerS() > 0.0));
    assertTrue(points.stream().allMatch(point -> point.getSuperficialGasVelocityUncertaintyMPerS() > 0.0));
    assertTrue(points.stream().allMatch(point -> !point.getClassificationDigitizationUncertainty().isEmpty()));
    assertTrue(points.stream().allMatch(point -> point.getSource().contains("Tengesdal_2002")));
  }

  @Test
  void reportsTaitelScreenConfusionMatrixAcrossPublishedVelocityMap() throws Exception {
    List<FlowMapPoint> points = readTengesdalFlowMap();

    ConfusionMatrix matrix = SevereSluggingBenchmarkHarness.compare(points, this::classifyWithTaitelScreen);

    assertEquals(22, matrix.getTruePositive());
    assertEquals(4, matrix.getFalseNegative());
    assertEquals(8, matrix.getFalsePositive());
    assertEquals(7, matrix.getTrueNegative());
    assertEquals(6, matrix.getTransitionPredictedSevere());
    assertEquals(8, matrix.getTransitionPredictedStable());
    assertEquals(41, matrix.getScoredCount());
    assertEquals(14, matrix.getTransitionCount());
    assertEquals(29.0 / 41.0, matrix.getAccuracy(), 1.0e-12);
    assertEquals(22.0 / 26.0, matrix.getSevereSlugRecall(), 1.0e-12);
    assertEquals(7.0 / 15.0, matrix.getStableRecall(), 1.0e-12);
    assertFalse(matrix.getAccuracy() > 0.75,
        "The benchmark must expose the screen's limited specificity instead of claiming dynamic validation");
  }

  private Prediction classifyWithTaitelScreen(FlowMapPoint point) {
    double vsl = point.getSuperficialLiquidVelocityMPerS();
    double vsg = point.getSuperficialGasVelocityAtStandardConditionsMPerS();
    double homogeneousGasVoidFraction = vsg / (vsg + vsl);
    double homogeneousRiserLiquidHoldup = vsl / (vsg + vsl);
    double areaM2 = Math.PI * PIPE_DIAMETER_M * PIPE_DIAMETER_M / 4.0;

    Input input = Input.builder().upstreamGasVolumeM3(areaM2 * EFFECTIVE_FLOWLINE_LENGTH_M * homogeneousGasVoidFraction)
        .riserAreaM2(areaM2).riserHeightM(RISER_HEIGHT_M).separatorPressurePa(SEPARATOR_PRESSURE_PA)
        .liquidDensityKgPerM3(CRYSTEX_DENSITY_KG_PER_M3).riserLiquidHoldup(homogeneousRiserLiquidHoldup)
        .gasCapVoidFraction(TAITEL_AIR_WATER_GAS_CAP_VOID_FRACTION).validFlowlineRiserTopology(true)
        .flowlineStratified(true).flowlineContainsGasAndLiquid(true).build();

    return SevereSluggingSystemDiagnostic.evaluate(input).isSevereSluggingPossible() ? Prediction.SEVERE_SLUG
        : Prediction.STABLE;
  }

  private static int count(List<FlowMapPoint> points, ObservedRegime regime) {
    int count = 0;
    for (FlowMapPoint point : points) {
      if (point.getObservedRegime() == regime) {
        count++;
      }
    }
    return count;
  }

  private static List<FlowMapPoint> readTengesdalFlowMap() throws Exception {
    return SevereSluggingBenchmarkHarness.readCsv(
        resourcePath("neqsim/process/equipment/pipeline/twophasepipe/validation/tengesdal_2002_3deg_flow_map.csv"));
  }

  private static Path resourcePath(String resource) throws URISyntaxException {
    return Paths.get(SevereSluggingBenchmarkHarnessTest.class.getClassLoader().getResource(resource).toURI());
  }
}
