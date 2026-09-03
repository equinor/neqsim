package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.characterization.DoeBigHillSweetAssay;
import neqsim.thermo.characterization.OilAssayCharacterisation;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Complete-slate atmospheric-column integration qualification for the public DOE Big Hill Sweet modeled assay.
 *
 * <p>
 * The feed is built exclusively through {@link DoeBigHillSweetAssay}, preserving the public 2021 DOE
 * comprehensive-assay and PIANO provenance, terminal-cut assumptions, and mass basis already qualified by the refinery
 * campaign. This test extends the earlier bounded-cut benchmark by exercising all four standard light-end components
 * and all eight petroleum pseudo-components in one rigorous column solve.
 * </p>
 *
 * <p>
 * This is an integration, conservation, separation-direction, convergence, and repeatability qualification. It is not a
 * plant-yield validation because the public assay does not define a matching atmospheric-column design, pressure
 * profile, heat duties, reflux, steam, or product specifications.
 * </p>
 */
public class DoeBigHillCompleteAtmosphericFractionationTest {
  private static final int STANDARD_COMPONENT_COUNT = 4;
  private static final int TOTAL_COMPONENT_COUNT = 12;
  private static final int SIDE_DRAW_TRAY = 4;
  private static final double BALANCE_TOLERANCE = 5.0e-2;
  private static final double REPEAT_TOLERANCE = 1.0e-2;

  /**
   * Require the complete public modeled slate to form conservative, ordered, repeatable products.
   */
  @Test
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  public void completeModeledSlateRunsReproduciblyThroughAtmosphericColumn() {
    Stream feed = createCompleteDoeFeed();
    DistillationColumn column = createAtmosphericColumn(feed);

    long startNanos = System.nanoTime();
    column.run(UUID.randomUUID());
    double firstRunSeconds = (System.nanoTime() - startNanos) / 1.0e9;

    assertAcceptedSolve(column);
    assertTrue(Double.isFinite(firstRunSeconds) && firstRunSeconds >= 0.0);
    assertPhysicalProductsAndBalances(column, feed);

    StreamInterface overhead = column.getGasOutStream();
    StreamInterface sideDraw = column.getSideDrawStream(SIDE_DRAW_TRAY, DistillationColumn.SideDrawPhase.LIQUID);
    StreamInterface bottoms = column.getLiquidOutStream();

    double firstOverheadMassFlow = overhead.getFlowRate("kg/hr");
    double firstSideDrawMassFlow = sideDraw.getFlowRate("kg/hr");
    double firstBottomsMassFlow = bottoms.getFlowRate("kg/hr");
    double firstOverheadMeanBoilingPoint = meanNormalBoilingPoint(overhead);
    double firstSideDrawMeanBoilingPoint = meanNormalBoilingPoint(sideDraw);
    double firstBottomsMeanBoilingPoint = meanNormalBoilingPoint(bottoms);

    column.run(UUID.randomUUID());

    assertAcceptedSolve(column);
    assertPhysicalProductsAndBalances(column, feed);
    assertRelativeRepeat(firstOverheadMassFlow, overhead.getFlowRate("kg/hr"));
    assertRelativeRepeat(firstSideDrawMassFlow, sideDraw.getFlowRate("kg/hr"));
    assertRelativeRepeat(firstBottomsMassFlow, bottoms.getFlowRate("kg/hr"));
    assertRelativeRepeat(firstOverheadMeanBoilingPoint, meanNormalBoilingPoint(overhead));
    assertRelativeRepeat(firstSideDrawMeanBoilingPoint, meanNormalBoilingPoint(sideDraw));
    assertRelativeRepeat(firstBottomsMeanBoilingPoint, meanNormalBoilingPoint(bottoms));
  }

  private static Stream createCompleteDoeFeed() {
    SystemInterface crude = new SystemSrkEos(550.0, 1.5);
    OilAssayCharacterisation assay = DoeBigHillSweetAssay.create(crude);
    assay.apply();
    crude.setMixingRule("classic");

    assertEquals(TOTAL_COMPONENT_COUNT, crude.getNumberOfComponents());
    Stream feed = new Stream("DOE Big Hill complete modeled assay feed", crude);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.setTemperature(550.0, "K");
    feed.setPressure(1.5, "bara");
    feed.run();
    return feed;
  }

  private static DistillationColumn createAtmosphericColumn(Stream feed) {
    DistillationColumn column = new DistillationColumn("DOE Big Hill complete atmospheric benchmark", 8, true, true);
    column.addFeedStream(feed, 4);
    column.setTopPressure(1.2);
    column.setBottomPressure(1.5);
    column.setCondenserMode(DistillationColumn.CondenserMode.PARTIAL);
    column.getReboiler().setOutTemperature(650.0);
    column.setCondenserRefluxRatio(1.0);
    column.setLiquidSideDrawFraction(SIDE_DRAW_TRAY, 0.10);
    column.setSolverType(DistillationColumn.SolverType.MESH_RESIDUAL);
    column.setMaxNumberOfIterations(300);
    column.setTemperatureTolerance(0.20);
    column.setMassBalanceTolerance(BALANCE_TOLERANCE);
    column.setEnthalpyBalanceTolerance(BALANCE_TOLERANCE);
    column.setEnforceEnergyBalanceTolerance(true);
    return column;
  }

  private static void assertAcceptedSolve(DistillationColumn column) {
    String diagnostics = column.getConvergenceDiagnostics();
    assertTrue(column.solved(), diagnostics);
    assertEquals(DistillationColumn.SolverType.MESH_RESIDUAL, column.getLastSolverTypeUsed(), diagnostics);
    assertNotEquals(DistillationColumn.SolveStatus.FALLBACK_PRODUCTS, column.getLastSolveStatus(), diagnostics);
    assertTrue(column.getLastMeshResidualNorm() <= column.getMeshResidualTolerance(), diagnostics);
  }

  private static void assertPhysicalProductsAndBalances(DistillationColumn column, Stream feed) {
    StreamInterface overhead = column.getGasOutStream();
    StreamInterface sideDraw = column.getSideDrawStream(SIDE_DRAW_TRAY, DistillationColumn.SideDrawPhase.LIQUID);
    StreamInterface bottoms = column.getLiquidOutStream();

    double feedMassFlow = feed.getFlowRate("kg/hr");
    double overheadMassFlow = overhead.getFlowRate("kg/hr");
    double sideDrawMassFlow = sideDraw.getFlowRate("kg/hr");
    double bottomsMassFlow = bottoms.getFlowRate("kg/hr");

    assertPositiveFinite(overheadMassFlow);
    assertPositiveFinite(sideDrawMassFlow);
    assertPositiveFinite(bottomsMassFlow);
    assertEquals(feedMassFlow, overheadMassFlow + sideDrawMassFlow + bottomsMassFlow, BALANCE_TOLERANCE * feedMassFlow);
    assertTrue(Double.isFinite(column.getMassBalanceError()));
    assertTrue(column.getMassBalanceError() <= BALANCE_TOLERANCE, column.getConvergenceDiagnostics());
    assertTrue(Double.isFinite(column.getEnergyBalanceError()));
    assertTrue(column.getEnergyBalanceError() <= BALANCE_TOLERANCE, column.getConvergenceDiagnostics());
    assertTrue(column.getLastTrayMaterialBalanceError() <= column.getTrayMaterialBalanceTolerance(),
        column.getConvergenceDiagnostics());

    assertComponentMolarBalance(feed, overhead, sideDraw, bottoms);

    double[] overheadComposition = overhead.getThermoSystem().getMolarComposition();
    double[] sideDrawComposition = sideDraw.getThermoSystem().getMolarComposition();
    double[] bottomsComposition = bottoms.getThermoSystem().getMolarComposition();
    assertEquals(TOTAL_COMPONENT_COUNT, overheadComposition.length);
    assertEquals(TOTAL_COMPONENT_COUNT, sideDrawComposition.length);
    assertEquals(TOTAL_COMPONENT_COUNT, bottomsComposition.length);

    assertTrue(
        sum(overheadComposition, 0, STANDARD_COMPONENT_COUNT) > sum(bottomsComposition, 0, STANDARD_COMPONENT_COUNT),
        "The modeled C2-C4 light ends should be enriched in overhead relative to bottoms");
    assertTrue(bottomsComposition[TOTAL_COMPONENT_COUNT - 1] > overheadComposition[TOTAL_COMPONENT_COUNT - 1],
        "The 1050 degF+ residue should be enriched in bottoms relative to overhead");

    double overheadMeanBoilingPoint = meanNormalBoilingPoint(overhead);
    double sideDrawMeanBoilingPoint = meanNormalBoilingPoint(sideDraw);
    double bottomsMeanBoilingPoint = meanNormalBoilingPoint(bottoms);
    assertTrue(overheadMeanBoilingPoint < sideDrawMeanBoilingPoint,
        "Overhead should have a lower mean normal boiling point than the liquid side draw");
    assertTrue(sideDrawMeanBoilingPoint < bottomsMeanBoilingPoint,
        "The liquid side draw should have a lower mean normal boiling point than bottoms");
    assertNotEquals(DistillationColumn.SolveStatus.FAILED, column.getLastSolveStatus());
  }

  private static void assertComponentMolarBalance(Stream feed, StreamInterface overhead, StreamInterface sideDraw,
      StreamInterface bottoms) {
    double feedFlow = feed.getFlowRate("mol/hr");
    double overheadFlow = overhead.getFlowRate("mol/hr");
    double sideDrawFlow = sideDraw.getFlowRate("mol/hr");
    double bottomsFlow = bottoms.getFlowRate("mol/hr");
    double[] feedComposition = feed.getThermoSystem().getMolarComposition();
    double[] overheadComposition = overhead.getThermoSystem().getMolarComposition();
    double[] sideDrawComposition = sideDraw.getThermoSystem().getMolarComposition();
    double[] bottomsComposition = bottoms.getThermoSystem().getMolarComposition();

    for (int componentIndex = 0; componentIndex < feedComposition.length; componentIndex++) {
      double feedComponentFlow = feedFlow * feedComposition[componentIndex];
      double productComponentFlow = overheadFlow * overheadComposition[componentIndex]
          + sideDrawFlow * sideDrawComposition[componentIndex] + bottomsFlow * bottomsComposition[componentIndex];
      assertEquals(feedComponentFlow, productComponentFlow,
          Math.max(1.0e-7, BALANCE_TOLERANCE * Math.abs(feedComponentFlow)));
    }
  }

  private static double meanNormalBoilingPoint(StreamInterface stream) {
    double[] composition = stream.getThermoSystem().getMolarComposition();
    double[] normalBoilingPoints = stream.getThermoSystem().getNormalBoilingPointTemperatures();
    double weightedBoilingPoint = 0.0;
    for (int i = 0; i < composition.length; i++) {
      assertTrue(Double.isFinite(normalBoilingPoints[i]) && normalBoilingPoints[i] > 0.0);
      weightedBoilingPoint += composition[i] * normalBoilingPoints[i];
    }
    return weightedBoilingPoint;
  }

  private static double sum(double[] values, int start, int end) {
    double total = 0.0;
    for (int i = start; i < end; i++) {
      total += values[i];
    }
    return total;
  }

  private static void assertPositiveFinite(double value) {
    assertTrue(Double.isFinite(value) && value > 0.0);
  }

  private static void assertRelativeRepeat(double expected, double actual) {
    assertTrue(Double.isFinite(expected));
    assertTrue(Double.isFinite(actual));
    assertEquals(expected, actual, Math.max(1.0e-8, REPEAT_TOLERANCE * Math.abs(expected)));
  }
}
