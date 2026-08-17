package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression coverage for columns whose external feeds expose different component subsets.
 *
 * @author esol
 * @version 1.0
 */
public class DistillationColumnHeterogeneousFeedTest {
  /**
   * Verify that feed component inventory is accumulated by name on the combined column basis.
   *
   * @throws Exception if the reconciliation helper cannot be inspected
   */
  @Test
  public void feedInventoryUsesCombinedNamedComponentBasis() throws Exception {
    Stream primaryFeed = createPrimaryFeed("named-basis primary");
    Stream sideFeed = createSideFeed("named-basis side");

    DistillationColumn column = createColumn("named-basis column", primaryFeed, sideFeed);
    Method feedInventoryMethod = DistillationColumn.class.getDeclaredMethod("getFeedComponentMoles");
    feedInventoryMethod.setAccessible(true);
    double[] actualComponentMoles = (double[]) feedInventoryMethod.invoke(column);
    String[] componentNames = column.getGasOutStream().getThermoSystem().getComponentNames();

    assertEquals(componentNames.length, actualComponentMoles.length);
    assertTrue(componentNames.length > sideFeed.getThermoSystem().getNumberOfComponents());
    for (int componentIndex = 0; componentIndex < componentNames.length; componentIndex++) {
      double expectedComponentAmount = getComponentAmount(primaryFeed, componentNames[componentIndex])
          + getComponentAmount(sideFeed, componentNames[componentIndex]);
      assertEquals(expectedComponentAmount, actualComponentMoles[componentIndex],
          Math.max(1.0e-12, 1.0e-10 * expectedComponentAmount));
    }
  }

  /**
   * Qualify total-condenser operation with heterogeneous feeds and an external liquid side draw.
   */
  @Test
  public void totalCondenserWithHeterogeneousFeedsAndSideDrawRemainsConservative() {
    Stream primaryFeed = createPrimaryFeed("total primary");
    Stream sideFeed = createSideFeed("total side");
    DistillationColumn column = createColumn("heterogeneous total column", primaryFeed, sideFeed);

    column.setCondenserMode(DistillationColumn.CondenserMode.TOTAL);
    column.setCondenserRefluxRatio(1.8);
    column.setLiquidSideDrawFraction(2, 0.05);

    column.run(UUID.randomUUID());
    assertAcceptedAndBalanced(column, primaryFeed, sideFeed);
    double initialTopFlow = column.getGasOutStream().getFlowRate("mol/hr");

    column.run(UUID.randomUUID());
    assertAcceptedAndBalanced(column, primaryFeed, sideFeed);
    assertTrue(column.wasSequentialWarmStateReused(), column.getConvergenceDiagnostics());
    assertEquals(initialTopFlow, column.getGasOutStream().getFlowRate("mol/hr"),
        Math.max(1.0e-8, 5.0e-5 * initialTopFlow));

    sideFeed.setFlowRate(55.0, "kg/hr");
    sideFeed.run();
    column.run(UUID.randomUUID());

    assertAcceptedAndBalanced(column, primaryFeed, sideFeed);
    assertTrue(!column.wasSequentialWarmStateReused(), column.getConvergenceDiagnostics());
    assertNotEquals(initialTopFlow, column.getGasOutStream().getFlowRate("mol/hr"), 1.0e-8);
  }

  /**
   * Create the main C3-C5 feed.
   *
   * @param name stream name
   * @return configured feed
   */
  private Stream createPrimaryFeed(String name) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 45.0, 10.0);
    fluid.addComponent("propane", 0.35);
    fluid.addComponent("n-butane", 0.45);
    fluid.addComponent("n-pentane", 0.20);
    fluid.setMixingRule("classic");

    Stream feed = new Stream(name, fluid);
    feed.setFlowRate(250.0, "kg/hr");
    feed.run();
    return feed;
  }

  /**
   * Create a cooler side feed with a strict subset of the main component basis.
   *
   * @param name stream name
   * @return configured feed
   */
  private Stream createSideFeed(String name) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 35.0, 10.0);
    fluid.addComponent("propane", 0.60);
    fluid.addComponent("n-butane", 0.40);
    fluid.setMixingRule("classic");

    Stream feed = new Stream(name, fluid);
    feed.setFlowRate(50.0, "kg/hr");
    feed.run();
    return feed;
  }

  /**
   * Create a realistic multifeed fractionator on the established terminal-mode conditions.
   *
   * @param name column name
   * @param primaryFeed main feed
   * @param sideFeed secondary feed
   * @return configured column
   */
  private DistillationColumn createColumn(String name, Stream primaryFeed, Stream sideFeed) {
    DistillationColumn column = new DistillationColumn(name, 6, true, true);
    column.addFeedStream(primaryFeed, 3);
    column.addFeedStream(sideFeed, 5);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.2);
    column.getCondenser().setOutTemperature(273.15 + 30.0);
    column.getReboiler().setOutTemperature(273.15 + 90.0);
    column.setSolverType(DistillationColumn.SolverType.MESH_RESIDUAL);
    column.setMaxNumberOfIterations(80);
    column.setTemperatureTolerance(1.0e-1);
    column.setMassBalanceTolerance(2.0e-1);
    column.setEnthalpyBalanceTolerance(2.0e-1);
    return column;
  }

  /**
   * Verify status, physical bounds, energy diagnostics, and named component closure.
   *
   * @param column solved column
   * @param feeds external feeds
   */
  private void assertAcceptedAndBalanced(DistillationColumn column, StreamInterface... feeds) {
    String diagnostics = column.getConvergenceDiagnostics();
    assertTrue(column.solved(), diagnostics);
    assertNotEquals(DistillationColumn.SolveStatus.FALLBACK_PRODUCTS, column.getLastSolveStatus());
    assertEquals(DistillationColumn.CondenserMode.TOTAL, column.getCondenserMode());
    assertTrue(Double.isFinite(column.getEnergyBalanceError()), diagnostics);
    assertTrue(Double.isFinite(column.getLastMeshResidualNorm()), diagnostics);

    StreamInterface topProduct = column.getGasOutStream();
    StreamInterface bottomProduct = column.getLiquidOutStream();
    StreamInterface sideDraw = column.getSideDrawStream(2, DistillationColumn.SideDrawPhase.LIQUID);
    assertTrue(topProduct.getFlowRate("mol/hr") >= 0.0);
    assertTrue(bottomProduct.getFlowRate("mol/hr") >= 0.0);
    assertTrue(sideDraw.getFlowRate("mol/hr") >= 0.0);
    assertTrue(topProduct.getTemperature("K") > 0.0);
    assertTrue(bottomProduct.getTemperature("K") > 0.0);

    String[] componentNames = topProduct.getThermoSystem().getComponentNames();
    for (String componentName : componentNames) {
      double feedComponentFlow = 0.0;
      for (StreamInterface feed : feeds) {
        feedComponentFlow += getComponentMolarFlow(feed, componentName);
      }
      double productComponentFlow = getComponentMolarFlow(topProduct, componentName)
          + getComponentMolarFlow(bottomProduct, componentName) + getComponentMolarFlow(sideDraw, componentName);
      assertEquals(feedComponentFlow, productComponentFlow, Math.max(1.0e-8, 5.0e-3 * Math.abs(feedComponentFlow)),
          componentName + " balance");
    }
  }

  /**
   * Get the component amount on the thermodynamic system's internal stream basis.
   *
   * @param stream stream to inspect
   * @param componentName component name
   * @return component amount summed over phases
   */
  private double getComponentAmount(StreamInterface stream, String componentName) {
    SystemInterface system = stream.getThermoSystem();
    String[] componentNames = system.getComponentNames();
    for (String candidateName : componentNames) {
      if (!componentName.equals(candidateName)) {
        continue;
      }
      double componentAmount = 0.0;
      for (int phaseIndex = 0; phaseIndex < system.getNumberOfPhases(); phaseIndex++) {
        componentAmount += system.getPhase(phaseIndex).getComponent(componentName).getNumberOfMolesInPhase();
      }
      if (componentAmount <= 0.0 && system.getNumberOfPhases() > 0) {
        componentAmount = system.getPhase(0).getComponent(componentName).getNumberOfmoles();
      }
      return componentAmount;
    }
    return 0.0;
  }

  /**
   * Get component molar flow, returning zero when a stream does not contain the component.
   *
   * @param stream stream to inspect
   * @param componentName component name
   * @return component flow in mol/hr
   */
  private double getComponentMolarFlow(StreamInterface stream, String componentName) {
    String[] componentNames = stream.getThermoSystem().getComponentNames();
    for (String candidateName : componentNames) {
      if (componentName.equals(candidateName)) {
        return stream.getFluid().getComponent(componentName).getTotalFlowRate("mol/hr");
      }
    }
    return 0.0;
  }
}
