package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Regression coverage for exact unchanged-input reuse by the native sum-rates solver. */
public class SumRatesExactReuseTest {
  private static final String[] COMPONENTS = { "methane", "ethane", "propane", "n-butane", "nC10" };

  private static SystemInterface createFluid(double temperature, double[] moles) {
    SystemInterface fluid = new SystemSrkEos(temperature, 30.0);
    for (int componentIndex = 0; componentIndex < COMPONENTS.length; componentIndex++) {
      fluid.addComponent(COMPONENTS[componentIndex], moles[componentIndex]);
    }
    fluid.setMixingRule("classic");
    return fluid;
  }

  private static Stream createFeed(String name, SystemInterface fluid, double flowRate) {
    Stream feed = new Stream(name, fluid);
    feed.setFlowRate(flowRate, "kg/hr");
    feed.setPressure(30.0, "bara");
    feed.run();
    return feed;
  }

  private static DistillationColumn createAbsorber(Stream gasFeed, double solventFlowRate) {
    Stream solventFeed = createFeed("lean solvent",
        createFluid(298.15, new double[] { 1.0e-10, 1.0e-10, 1.0e-10, 1.0e-10, 1.0 }), solventFlowRate);
    DistillationColumn column = new DistillationColumn("sum-rates absorber", 10, false, false);
    column.addFeedStream(gasFeed, 0);
    column.addFeedStream(solventFeed, column.getNumberOfTrays() - 1);
    column.setTopPressure(30.0);
    column.setBottomPressure(30.0);
    column.setMaxNumberOfIterations(200);
    column.setTemperatureTolerance(1.0e-3);
    column.setMassBalanceTolerance(1.0e-1);
    column.setEnthalpyBalanceTolerance(10.0);
    column.setSolverType(DistillationColumn.SolverType.SUM_RATES);
    return column;
  }

  /** Exact reuse must be lossless, while a changed feed must execute the solver. */
  @Test
  public void unchangedInputReusesAcceptedStateAndChangedInputInvalidatesIt() {
    double[][] operatingPoints = { { 313.15, 1200.0 }, { 318.15, 1300.0 } };
    for (int pointIndex = 0; pointIndex < operatingPoints.length; pointIndex++) {
      Stream gasFeed = createFeed("rich gas " + pointIndex,
          createFluid(operatingPoints[pointIndex][0], new double[] { 0.70, 0.15, 0.10, 0.05, 1.0e-10 }), 1000.0);
      DistillationColumn column = createAbsorber(gasFeed, operatingPoints[pointIndex][1]);

      column.run();
      assertTrue(column.solved(), column.getConvergenceDiagnostics());
      assertTrue(column.getLastIterationCount() > 0);
      assertFalse(column.wasSequentialWarmStateReused());
      StreamInterface gasProduct = column.getGasOutStream();
      StreamInterface liquidProduct = column.getLiquidOutStream();
      double gasFlow = gasProduct.getFlowRate("kg/hr");
      double liquidFlow = liquidProduct.getFlowRate("kg/hr");
      double gasTemperature = gasProduct.getTemperature("K");
      double liquidTemperature = liquidProduct.getTemperature("K");

      column.run();
      assertTrue(column.wasSequentialWarmStateReused());
      assertEquals(0, column.getLastIterationCount());
      assertEquals(gasFlow, column.getGasOutStream().getFlowRate("kg/hr"), 0.0);
      assertEquals(liquidFlow, column.getLiquidOutStream().getFlowRate("kg/hr"), 0.0);
      assertEquals(gasTemperature, column.getGasOutStream().getTemperature("K"), 0.0);
      assertEquals(liquidTemperature, column.getLiquidOutStream().getTemperature("K"), 0.0);

      gasFeed.setTemperature(gasFeed.getTemperature("K") + 1.0, "K");
      gasFeed.run();
      column.run();
      assertTrue(column.solved(), column.getConvergenceDiagnostics());
      assertFalse(column.wasSequentialWarmStateReused());
      assertTrue(column.getLastIterationCount() > 0);
      assertNotEquals(gasFlow, column.getGasOutStream().getFlowRate("kg/hr"), 1.0e-6);
      double totalFeedFlow = gasFeed.getFlowRate("kg/hr") + operatingPoints[pointIndex][1];
      double totalProductFlow = column.getGasOutStream().getFlowRate("kg/hr")
          + column.getLiquidOutStream().getFlowRate("kg/hr");
      assertEquals(totalFeedFlow, totalProductFlow, totalFeedFlow * 1.0e-9);
    }
  }
}
