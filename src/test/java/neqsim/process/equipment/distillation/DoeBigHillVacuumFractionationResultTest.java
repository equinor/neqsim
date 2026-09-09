package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import neqsim.process.equipment.distillation.DoeBigHillVacuumFractionationCase.OperatingInputs;
import neqsim.process.equipment.distillation.DoeBigHillVacuumFractionationResult.ProductResult;
import neqsim.process.equipment.stream.StreamInterface;

/** Qualification tests for {@link DoeBigHillVacuumFractionationResult}. */
public class DoeBigHillVacuumFractionationResultTest {
  private static final double FEED_MASS_FLOW_KG_PER_HOUR = 1000.0;
  private static final double BALANCE_TOLERANCE = 5.0e-2;
  private static final double REPEAT_TOLERANCE = 1.0e-2;

  /**
   * Require the explicit screening point to converge, conserve, separate, and repeat.
   */
  @Test
  @Timeout(value = 180, unit = TimeUnit.SECONDS)
  public void screeningPointProducesConservativeOrderedRepeatableResults() {
    DoeBigHillVacuumFractionationCase first = createCase("First Big Hill vacuum screen");
    assertThrows(IllegalStateException.class,
        () -> DoeBigHillVacuumFractionationResult.evaluate(first));

    first.getColumn().run(UUID.randomUUID());
    DoeBigHillVacuumFractionationResult firstResult =
        DoeBigHillVacuumFractionationResult.evaluate(first);
    assertQualifiedResult(first, firstResult);

    DoeBigHillVacuumFractionationCase second = createCase("Second Big Hill vacuum screen");
    second.getColumn().run(UUID.randomUUID());
    DoeBigHillVacuumFractionationResult secondResult =
        DoeBigHillVacuumFractionationResult.evaluate(second);
    assertQualifiedResult(second, secondResult);

    ProductResult[] firstProducts = firstResult.getProducts();
    ProductResult[] secondProducts = secondResult.getProducts();
    for (int i = 0; i < firstProducts.length; i++) {
      assertRelativeRepeat(firstProducts[i].getMassFlowKgPerHour(),
          secondProducts[i].getMassFlowKgPerHour());
      assertRelativeRepeat(firstProducts[i].getMeanNormalBoilingPointKelvin(),
          secondProducts[i].getMeanNormalBoilingPointKelvin());
    }
  }

  private static DoeBigHillVacuumFractionationCase createCase(String name) {
    OperatingInputs inputs = new OperatingInputs(12, 4, 640.0, 0.12, 0.08,
        0.16, 700.0, 0.5);
    return DoeBigHillVacuumFractionationCase.create(name,
        FEED_MASS_FLOW_KG_PER_HOUR, inputs);
  }

  private static void assertQualifiedResult(DoeBigHillVacuumFractionationCase model,
      DoeBigHillVacuumFractionationResult result) {
    DistillationColumn column = model.getColumn();
    String diagnostics = column.getConvergenceDiagnostics();
    assertTrue(column.solved(), diagnostics);
    assertEquals(DistillationColumn.SolverType.MESH_RESIDUAL,
        column.getLastSolverTypeUsed(), diagnostics);
    assertTrue(result.getColumnMassBalanceError() <= BALANCE_TOLERANCE, diagnostics);
    assertTrue(result.getColumnEnergyBalanceError() <= BALANCE_TOLERANCE, diagnostics);
    assertTrue(result.getMeshResidualNorm() <= column.getMeshResidualTolerance(), diagnostics);
    assertTrue(result.getMassClosureRelativeError() <= BALANCE_TOLERANCE, diagnostics);
    assertTrue(result.getMaximumComponentMolarClosureRelativeError()
        <= BALANCE_TOLERANCE, diagnostics);
    assertTrue(result.getIterationCount() > 0);
    assertTrue(Double.isFinite(result.getSolveTimeSeconds())
        && result.getSolveTimeSeconds() >= 0.0);

    ProductResult[] products = result.getProducts();
    assertEquals(2, products.length);
    assertEquals("Overhead", products[0].getProductLabel());
    assertEquals("Bottoms", products[1].getProductLabel());
    assertEquals(products[0], result.getProduct("Overhead"));
    assertEquals(products[1], result.getProduct("Bottoms"));
    assertThrows(IllegalArgumentException.class, () -> result.getProduct("VGO"));
    assertNotSame(products, result.getProducts());
    assertTrue(products[0].getMassFlowKgPerHour() > 0.0);
    assertTrue(products[1].getMassFlowKgPerHour() > 0.0);
    assertTrue(products[0].getMeanNormalBoilingPointKelvin()
        < products[1].getMeanNormalBoilingPointKelvin());
    assertEquals(1.0, products[0].getMassFractionOfFeed()
        + products[1].getMassFractionOfFeed(), BALANCE_TOLERANCE);
    assertEquals(FEED_MASS_FLOW_KG_PER_HOUR, result.getFeedMassFlowKgPerHour(), 1.0e-9);
    assertEquals(result.getFeedMassFlowKgPerHour(), result.getProductMassFlowKgPerHour(),
        BALANCE_TOLERANCE * result.getFeedMassFlowKgPerHour());

    assertEquals(3, model.getFeedStream().getThermoSystem().getNumberOfComponents());
    assertEquals("DOE_BH_650_850_PC",
        model.getFeedStream().getThermoSystem().getComponent(0).getComponentName());
    assertEquals("DOE_BH_850_1050_PC",
        model.getFeedStream().getThermoSystem().getComponent(1).getComponentName());
    assertEquals("DOE_BH_1050_PLUS_PC",
        model.getFeedStream().getThermoSystem().getComponent(2).getComponentName());
    assertIndependentComponentBalance(model);
  }

  private static void assertIndependentComponentBalance(
      DoeBigHillVacuumFractionationCase model) {
    StreamInterface feed = model.getFeedStream();
    StreamInterface overhead = model.getColumn().getGasOutStream();
    StreamInterface bottoms = model.getColumn().getLiquidOutStream();
    double[] feedComposition = feed.getThermoSystem().getMolarComposition();
    double[] overheadComposition = overhead.getThermoSystem().getMolarComposition();
    double[] bottomsComposition = bottoms.getThermoSystem().getMolarComposition();
    for (int componentIndex = 0; componentIndex < feedComposition.length;
        componentIndex++) {
      double feedComponentFlow = feed.getFlowRate("mol/hr")
          * feedComposition[componentIndex];
      double productComponentFlow = overhead.getFlowRate("mol/hr")
          * overheadComposition[componentIndex]
          + bottoms.getFlowRate("mol/hr") * bottomsComposition[componentIndex];
      assertEquals(feedComponentFlow, productComponentFlow,
          BALANCE_TOLERANCE * Math.abs(feedComponentFlow));
    }
  }

  private static void assertRelativeRepeat(double expected, double actual) {
    assertTrue(Double.isFinite(expected));
    assertTrue(Double.isFinite(actual));
    assertEquals(expected, actual,
        Math.max(1.0e-8, REPEAT_TOLERANCE * Math.abs(expected)));
  }
}
