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
import neqsim.thermo.characterization.OilAssayCharacterisation;
import neqsim.thermo.characterization.OilAssayCharacterisation.AssayCut;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Atmospheric-column integration qualification using the public DOE Big Hill Sweet assay basis.
 *
 * <p>
 * The source values are the bounded 175-1050 degF distillate slice from U.S. Department of Energy Strategic Petroleum
 * Reserve Big Hill Sweet sample MLI 009 (1998-05-04). The same frozen values are independently qualified in
 * {@code OilAssayCharacterisationDoeBigHillTest}. This test deliberately excludes the unbounded light and residue tails
 * rather than inventing boiling limits for them.
 * </p>
 *
 * <p>
 * This is an integration and numerical-robustness benchmark. It qualifies that the public assay basis can be turned
 * into pseudo-components and separated through NeqSim's rigorous column while preserving material and energy closure,
 * boiling-order separation, and repeatability. It is not an independent validation of atmospheric crude-column product
 * yields.
 * </p>
 */
public class DoeBigHillAtmosphericFractionationTest {
  private static final double[] LOWER_BOUNDARY_F = { 175.0, 250.0, 375.0, 530.0, 650.0 };
  private static final double[] UPPER_BOUNDARY_F = { 250.0, 375.0, 530.0, 650.0, 1050.0 };
  private static final double[] WEIGHT_PERCENT = { 8.6, 15.2, 15.2, 11.1, 30.3 };
  private static final double[] SPECIFIC_GRAVITY = { 0.7815, 0.8305, 0.8623, 0.9226, 0.9477 };
  private static final int SIDE_DRAW_TRAY = 5;
  private static final double BALANCE_TOLERANCE = 5.0e-2;
  private static final double REPEAT_TOLERANCE = 1.0e-2;

  /**
   * Run a bounded public refinery-assay slate through an atmospheric fractionator and require physical, conservative,
   * reproducible products.
   */
  @Test
  @Timeout(value = 120, unit = TimeUnit.SECONDS)
  public void doeBoundedDistillateSliceRunsReproduciblyThroughAtmosphericColumn() {
    Stream feed = createDoeFeed();
    DistillationColumn column = createAtmosphericColumn(feed);

    long startNanos = System.nanoTime();
    column.run(UUID.randomUUID());
    double firstRunSeconds = (System.nanoTime() - startNanos) / 1.0e9;

    String firstRunDiagnostics = column.getConvergenceDiagnostics();
    assertTrue(column.solved(), firstRunDiagnostics);
    assertEquals(DistillationColumn.SolverType.MESH_RESIDUAL, column.getLastSolverTypeUsed(), firstRunDiagnostics);
    assertNotEquals(DistillationColumn.SolveStatus.FALLBACK_PRODUCTS, column.getLastSolveStatus(), firstRunDiagnostics);
    assertTrue(column.getLastMeshResidualNorm() <= column.getMeshResidualTolerance(), firstRunDiagnostics);
    assertTrue(Double.isFinite(firstRunSeconds) && firstRunSeconds >= 0.0);
    assertPhysicalProductsAndBalances(column, feed);

    StreamInterface overhead = column.getGasOutStream();
    StreamInterface sideDraw = column.getSideDrawStream(SIDE_DRAW_TRAY, DistillationColumn.SideDrawPhase.LIQUID);
    StreamInterface bottoms = column.getLiquidOutStream();

    double firstOverheadMassFlow = overhead.getFlowRate("kg/hr");
    double firstSideDrawMassFlow = sideDraw.getFlowRate("kg/hr");
    double firstBottomsMassFlow = bottoms.getFlowRate("kg/hr");
    double firstOverheadMeanBoilingPoint = meanRepresentativeBoilingPoint(overhead);
    double firstSideDrawMeanBoilingPoint = meanRepresentativeBoilingPoint(sideDraw);
    double firstBottomsMeanBoilingPoint = meanRepresentativeBoilingPoint(bottoms);

    column.run(UUID.randomUUID());

    String repeatedRunDiagnostics = column.getConvergenceDiagnostics();
    assertTrue(column.solved(), repeatedRunDiagnostics);
    assertEquals(DistillationColumn.SolverType.MESH_RESIDUAL, column.getLastSolverTypeUsed(), repeatedRunDiagnostics);
    assertNotEquals(DistillationColumn.SolveStatus.FALLBACK_PRODUCTS, column.getLastSolveStatus(),
        repeatedRunDiagnostics);
    assertTrue(column.getLastMeshResidualNorm() <= column.getMeshResidualTolerance(), repeatedRunDiagnostics);
    assertPhysicalProductsAndBalances(column, feed);
    assertRelativeRepeat(firstOverheadMassFlow, overhead.getFlowRate("kg/hr"));
    assertRelativeRepeat(firstSideDrawMassFlow, sideDraw.getFlowRate("kg/hr"));
    assertRelativeRepeat(firstBottomsMassFlow, bottoms.getFlowRate("kg/hr"));
    assertRelativeRepeat(firstOverheadMeanBoilingPoint, meanRepresentativeBoilingPoint(overhead));
    assertRelativeRepeat(firstSideDrawMeanBoilingPoint, meanRepresentativeBoilingPoint(sideDraw));
    assertRelativeRepeat(firstBottomsMeanBoilingPoint, meanRepresentativeBoilingPoint(bottoms));
  }

  private static Stream createDoeFeed() {
    SystemInterface crude = new SystemSrkEos(550.0, 1.5);
    OilAssayCharacterisation assay = crude.getOilAssayCharacterisation();
    assay.clearCuts();
    assay.setTotalAssayMass(1.0);

    double weightSum = sum(WEIGHT_PERCENT);
    for (int i = 0; i < WEIGHT_PERCENT.length; i++) {
      assay.addCut(new AssayCut("DOE_BH_" + (i + 2)).withMassFraction(WEIGHT_PERCENT[i] / weightSum)
          .withSpecificGravity(SPECIFIC_GRAVITY[i])
          .withBoilingRangeCelsius(fahrenheitToCelsius(LOWER_BOUNDARY_F[i]), fahrenheitToCelsius(UPPER_BOUNDARY_F[i])));
    }
    assay.apply();
    crude.setMixingRule("classic");

    Stream feed = new Stream("DOE Big Hill bounded assay feed", crude);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.setTemperature(550.0, "K");
    feed.setPressure(1.5, "bara");
    feed.run();
    return feed;
  }

  private static DistillationColumn createAtmosphericColumn(Stream feed) {
    DistillationColumn column = new DistillationColumn("DOE Big Hill atmospheric benchmark", 8, true, true);
    column.addFeedStream(feed, 4);
    column.setTopPressure(1.2);
    column.setBottomPressure(1.5);
    column.getCondenser().setOutTemperature(420.0);
    column.setCondenserMode(DistillationColumn.CondenserMode.TOTAL);
    column.getReboiler().setOutTemperature(690.0);
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

  private static void assertPhysicalProductsAndBalances(DistillationColumn column, Stream feed) {
    StreamInterface overhead = column.getGasOutStream();
    StreamInterface sideDraw = column.getSideDrawStream(SIDE_DRAW_TRAY, DistillationColumn.SideDrawPhase.LIQUID);
    StreamInterface bottoms = column.getLiquidOutStream();

    double feedMassFlow = feed.getFlowRate("kg/hr");
    double overheadMassFlow = overhead.getFlowRate("kg/hr");
    double sideDrawMassFlow = sideDraw.getFlowRate("kg/hr");
    double bottomsMassFlow = bottoms.getFlowRate("kg/hr");

    assertTrue(Double.isFinite(overheadMassFlow) && overheadMassFlow > 0.0);
    assertTrue(Double.isFinite(sideDrawMassFlow) && sideDrawMassFlow > 0.0);
    assertTrue(Double.isFinite(bottomsMassFlow) && bottomsMassFlow > 0.0);
    assertEquals(feedMassFlow, overheadMassFlow + sideDrawMassFlow + bottomsMassFlow, BALANCE_TOLERANCE * feedMassFlow);
    assertTrue(Double.isFinite(column.getMassBalanceError()));
    assertTrue(column.getMassBalanceError() <= BALANCE_TOLERANCE, column.getConvergenceDiagnostics());
    assertTrue(Double.isFinite(column.getEnergyBalanceError()));
    assertTrue(column.getEnergyBalanceError() <= BALANCE_TOLERANCE, column.getConvergenceDiagnostics());

    assertComponentMolarBalance(feed, overhead, sideDraw, bottoms);

    double[] overheadComposition = overhead.getThermoSystem().getMolarComposition();
    double[] sideDrawComposition = sideDraw.getThermoSystem().getMolarComposition();
    double[] bottomsComposition = bottoms.getThermoSystem().getMolarComposition();
    assertEquals(WEIGHT_PERCENT.length, overheadComposition.length);
    assertEquals(WEIGHT_PERCENT.length, sideDrawComposition.length);
    assertEquals(WEIGHT_PERCENT.length, bottomsComposition.length);

    assertTrue(overheadComposition[0] > bottomsComposition[0],
        "The lightest bounded DOE cut should be enriched in the overhead relative to bottoms");
    assertTrue(bottomsComposition[WEIGHT_PERCENT.length - 1] > overheadComposition[WEIGHT_PERCENT.length - 1],
        "The heaviest bounded DOE cut should be enriched in bottoms relative to overhead");

    double overheadMeanBoilingPoint = meanRepresentativeBoilingPoint(overhead);
    double sideDrawMeanBoilingPoint = meanRepresentativeBoilingPoint(sideDraw);
    double bottomsMeanBoilingPoint = meanRepresentativeBoilingPoint(bottoms);
    assertTrue(overheadMeanBoilingPoint < sideDrawMeanBoilingPoint,
        "Overhead should have a lower representative boiling point than the liquid side draw");
    assertTrue(sideDrawMeanBoilingPoint < bottomsMeanBoilingPoint,
        "The liquid side draw should have a lower representative boiling point than bottoms");
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

  private static double meanRepresentativeBoilingPoint(StreamInterface stream) {
    double[] composition = stream.getThermoSystem().getMolarComposition();
    double weightedBoilingPoint = 0.0;
    for (int i = 0; i < composition.length; i++) {
      double midpointFahrenheit = 0.5 * (LOWER_BOUNDARY_F[i] + UPPER_BOUNDARY_F[i]);
      weightedBoilingPoint += composition[i] * fahrenheitToKelvin(midpointFahrenheit);
    }
    return weightedBoilingPoint;
  }

  private static void assertRelativeRepeat(double expected, double actual) {
    assertTrue(Double.isFinite(expected));
    assertTrue(Double.isFinite(actual));
    assertEquals(expected, actual, Math.max(1.0e-8, REPEAT_TOLERANCE * Math.abs(expected)));
  }

  private static double sum(double[] values) {
    double sum = 0.0;
    for (double value : values) {
      sum += value;
    }
    return sum;
  }

  private static double fahrenheitToCelsius(double fahrenheit) {
    return (fahrenheit - 32.0) * 5.0 / 9.0;
  }

  private static double fahrenheitToKelvin(double fahrenheit) {
    return fahrenheitToCelsius(fahrenheit) + 273.15;
  }
}
