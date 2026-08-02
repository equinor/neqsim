package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for fixed-liquid condenser product accounting in MESH diagnostics.
 *
 * @author esol
 * @version 1.0
 */
public class FixedLiquidRefluxMeshBalanceTest {
  /** Feed mass rate used by the industrial-style fractionator cases in kg/hr. */
  private static final double FEED_FLOW_KG_PER_HOUR = 5000.0;

  /**
   * The condenser liquid product is an external outlet and must be included in the top-stage material equations.
   */
  @Test
  public void fixedLiquidProductClosesTopStageMaterialBalance() {
    assertFixedLiquidRefluxCase(100.0);
    assertFixedLiquidRefluxCase(120.0);
  }

  /**
   * Build and validate one nearby fixed-liquid-reflux operating point.
   *
   * @param refluxFlowKgPerHour specified liquid return in kg/hr
   */
  private static void assertFixedLiquidRefluxCase(double refluxFlowKgPerHour) {
    ColumnCase columnCase = buildColumn(refluxFlowKgPerHour);
    DistillationColumn column = columnCase.column;
    column.run();

    StreamInterface overhead = column.getGasOutStream();
    StreamInterface bottoms = column.getLiquidOutStream();
    StreamInterface condenserLiquidProduct = column.getCondenser().getLiquidProductStream();
    assertNotNull(condenserLiquidProduct, "fixed-liquid mode must expose the separated liquid product");
    assertEquals(refluxFlowKgPerHour, column.getCondenser().getLiquidOutStream().getFlowRate("kg/hr"), 1.0e-6,
        "condenser liquid return must satisfy its fixed flow specification");

    assertTrue(column.getLastTrayMaterialBalanceError() <= column.getTrayMaterialBalanceTolerance(),
        column.getConvergenceDiagnostics());
    assertTrue(column.getLastMeshMaterialResidualNorm() <= column.getTrayMaterialBalanceTolerance(),
        column.getConvergenceDiagnostics());
    assertTrue(column.getLastEnergyResidual() <= column.getEnthalpyBalanceTolerance(),
        column.getConvergenceDiagnostics());
    assertTrue(column.solved(), column.getConvergenceDiagnostics());

    assertThreeProductBalance(columnCase.feed, overhead, bottoms, condenserLiquidProduct);
    assertPhysicalStream(overhead);
    assertPhysicalStream(bottoms);
    assertPhysicalStream(condenserLiquidProduct);
    assertEquals(20.0, columnCase.feed.getTemperature("C"), 1.0e-9,
        "column iteration must preserve the caller-owned feed state");

    double overheadFlow = overhead.getFlowRate("mol/hr");
    double bottomsFlow = bottoms.getFlowRate("mol/hr");
    double condenserProductFlow = condenserLiquidProduct.getFlowRate("mol/hr");
    double[] overheadComposition = overhead.getThermoSystem().getMolarComposition().clone();
    double[] bottomsComposition = bottoms.getThermoSystem().getMolarComposition().clone();
    double[] condenserProductComposition =
        condenserLiquidProduct.getThermoSystem().getMolarComposition().clone();

    column.run();

    assertTrue(column.solved(), column.getConvergenceDiagnostics());
    assertEquals(overheadFlow, column.getGasOutStream().getFlowRate("mol/hr"),
        Math.max(1.0e-9, Math.abs(overheadFlow) * 1.0e-8), "unchanged overhead flow must repeat");
    assertEquals(bottomsFlow, column.getLiquidOutStream().getFlowRate("mol/hr"),
        Math.max(1.0e-9, Math.abs(bottomsFlow) * 1.0e-8), "unchanged bottoms flow must repeat");
    assertEquals(condenserProductFlow, column.getCondenser().getLiquidProductStream().getFlowRate("mol/hr"),
        Math.max(1.0e-9, Math.abs(condenserProductFlow) * 1.0e-8),
        "unchanged condenser liquid product must repeat");
    assertCompositionEquals(overheadComposition,
        column.getGasOutStream().getThermoSystem().getMolarComposition());
    assertCompositionEquals(bottomsComposition,
        column.getLiquidOutStream().getThermoSystem().getMolarComposition());
    assertCompositionEquals(condenserProductComposition,
        column.getCondenser().getLiquidProductStream().getThermoSystem().getMolarComposition());
    assertThreeProductBalance(columnCase.feed, column.getGasOutStream(), column.getLiquidOutStream(),
        column.getCondenser().getLiquidProductStream());
  }

  /**
   * Build a six-stage SRK hydrocarbon column with a partial condenser and fixed liquid return.
   *
   * @param refluxFlowKgPerHour specified liquid return in kg/hr
   * @return feed and unrun column
   */
  private static ColumnCase buildColumn(double refluxFlowKgPerHour) {
    SystemInterface fluid = new SystemSrkEos(293.15, 10.0);
    fluid.addComponent("propane", 40.0);
    fluid.addComponent("n-butane", 30.0);
    fluid.addComponent("n-pentane", 30.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("fixed liquid reflux feed", fluid);
    feed.setFlowRate(FEED_FLOW_KG_PER_HOUR, "kg/hr");
    feed.setTemperature(20.0, "C");
    feed.setPressure(10.0, "bara");
    feed.run();

    DistillationColumn column = new DistillationColumn("fixed liquid reflux balance column", 6, true, true);
    column.addFeedStream(feed, 3);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.5);
    column.getCondenser().setOutTemperature(293.15);
    column.getReboiler().setOutTemperature(353.15);
    column.getCondenser().setSeparation_with_liquid_reflux(true, refluxFlowKgPerHour, "kg/hr");
    column.setSolverType(DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
    column.setRelaxationFactor(0.2);
    column.setMaxNumberOfIterations(120, true);
    return new ColumnCase(feed, column);
  }

  /**
   * Verify total and component molar closure across overhead, bottoms and condenser liquid product.
   *
   * @param feed column feed
   * @param overhead overhead product
   * @param bottoms bottoms product
   * @param condenserLiquidProduct condenser liquid product
   */
  private static void assertThreeProductBalance(StreamInterface feed, StreamInterface overhead,
      StreamInterface bottoms, StreamInterface condenserLiquidProduct) {
    double feedFlow = feed.getFlowRate("mol/hr");
    double productFlow = overhead.getFlowRate("mol/hr") + bottoms.getFlowRate("mol/hr")
        + condenserLiquidProduct.getFlowRate("mol/hr");
    assertEquals(feedFlow, productFlow, Math.max(1.0e-6, Math.abs(feedFlow) * 5.0e-3),
        "three external products must close total molar flow");

    String[] componentNames = feed.getThermoSystem().getComponentNames();
    double[] feedComposition = feed.getThermoSystem().getMolarComposition();
    for (int componentIndex = 0; componentIndex < componentNames.length; componentIndex++) {
      double feedComponentFlow = feedFlow * feedComposition[componentIndex];
      double productComponentFlow = componentFlow(overhead, componentNames[componentIndex])
          + componentFlow(bottoms, componentNames[componentIndex])
          + componentFlow(condenserLiquidProduct, componentNames[componentIndex]);
      assertEquals(feedComponentFlow, productComponentFlow,
          Math.max(1.0e-6, Math.abs(feedComponentFlow) * 5.0e-3),
          "component balance must close for " + componentNames[componentIndex]);
    }
  }

  /**
   * Get one component molar flow.
   *
   * @param stream stream to inspect
   * @param componentName component name
   * @return component flow in mol/hr
   */
  private static double componentFlow(StreamInterface stream, String componentName) {
    return stream.getFluid().getComponent(componentName).getTotalFlowRate("mol/hr");
  }

  /**
   * Verify finite physical bounds for a product stream.
   *
   * @param stream stream to inspect
   */
  private static void assertPhysicalStream(StreamInterface stream) {
    double flow = stream.getFlowRate("mol/hr");
    assertTrue(Double.isFinite(flow) && flow >= 0.0, "product flow must be finite and non-negative");
    assertTrue(Double.isFinite(stream.getPressure("bara")) && stream.getPressure("bara") > 0.0,
        "product pressure must be physical");
    if (flow > 0.0) {
      assertTrue(Double.isFinite(stream.getTemperature("K")) && stream.getTemperature("K") > 150.0
          && stream.getTemperature("K") < 800.0, "flowing product temperature must be physical");
    }
    double compositionSum = 0.0;
    for (double moleFraction : stream.getThermoSystem().getMolarComposition()) {
      assertTrue(Double.isFinite(moleFraction) && moleFraction >= -1.0e-12
          && moleFraction <= 1.0 + 1.0e-12, "product composition must be finite and bounded");
      compositionSum += moleFraction;
    }
    assertEquals(1.0, compositionSum, 1.0e-8, "product composition must be normalized");
  }

  /**
   * Verify deterministic composition repeatability.
   *
   * @param expected expected composition
   * @param actual actual composition
   */
  private static void assertCompositionEquals(double[] expected, double[] actual) {
    assertEquals(expected.length, actual.length, "component count must remain unchanged");
    for (int componentIndex = 0; componentIndex < expected.length; componentIndex++) {
      assertEquals(expected[componentIndex], actual[componentIndex], 1.0e-8,
          "component composition must repeat at index " + componentIndex);
    }
  }

  /** Feed and column references for one regression case. */
  private static final class ColumnCase {
    /** Caller-owned feed. */
    private final Stream feed;
    /** Configured column. */
    private final DistillationColumn column;

    /**
     * Create a case.
     *
     * @param feed caller-owned feed
     * @param column configured column
     */
    private ColumnCase(Stream feed, DistillationColumn column) {
      this.feed = feed;
      this.column = column;
    }
  }
}
