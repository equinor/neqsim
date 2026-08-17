package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Qualification coverage for coordinated external and internal column flows.
 *
 * @author esol
 * @version 1.0
 */
public class DistillationColumnCoordinatedFlowTest {
  /**
   * Qualify heterogeneous feeds, terminal ratios, an external side draw, and a cooled pumparound together.
   */
  @Test
  public void partialCondenserMultifeedSideDrawAndPumparoundRemainConservative() {
    Stream primaryFeed = createPrimaryFeed();
    Stream sideFeed = createSideFeed();
    DistillationColumn column = createColumn(primaryFeed, sideFeed);
    DistillationColumn.ColumnPumparound pumparound = column.addLiquidPumparound("coordinated PA", 3, 5, 0.02, 4.0);

    column.run(UUID.randomUUID());

    assertAcceptedAndBalanced(column, pumparound, primaryFeed, sideFeed);
    double initialTopFlow = column.getGasOutStream().getFlowRate("mol/hr");
    double initialBottomFlow = column.getLiquidOutStream().getFlowRate("mol/hr");
    double initialSideDrawFlow = column.getSideDrawStream(2, DistillationColumn.SideDrawPhase.LIQUID)
        .getFlowRate("mol/hr");
    double initialPumparoundFlow = pumparound.getReturnStream().getFlowRate("kg/hr");
    double initialPumparoundDuty = pumparound.getDuty();

    column.run(UUID.randomUUID());

    assertAcceptedAndBalanced(column, pumparound, primaryFeed, sideFeed);
    assertFalse(column.wasSequentialWarmStateReused(), column.getConvergenceDiagnostics());
    assertEquals(initialTopFlow, column.getGasOutStream().getFlowRate("mol/hr"),
        Math.max(1.0e-8, 5.0e-5 * initialTopFlow));
    assertEquals(initialBottomFlow, column.getLiquidOutStream().getFlowRate("mol/hr"),
        Math.max(1.0e-8, 5.0e-5 * initialBottomFlow));
    assertEquals(initialSideDrawFlow,
        column.getSideDrawStream(2, DistillationColumn.SideDrawPhase.LIQUID).getFlowRate("mol/hr"),
        Math.max(1.0e-8, 5.0e-5 * initialSideDrawFlow));
    assertEquals(initialPumparoundFlow, pumparound.getReturnStream().getFlowRate("kg/hr"),
        Math.max(1.0e-8, 5.0e-5 * initialPumparoundFlow));
    assertEquals(initialPumparoundDuty, pumparound.getDuty(),
        Math.max(1.0e-8, 5.0e-5 * Math.abs(initialPumparoundDuty)));

    sideFeed.setFlowRate(55.0, "kg/hr");
    sideFeed.run();
    column.run(UUID.randomUUID());

    assertAcceptedAndBalanced(column, pumparound, primaryFeed, sideFeed);
    assertFalse(column.wasSequentialWarmStateReused(), column.getConvergenceDiagnostics());
    assertNotEquals(initialTopFlow, column.getGasOutStream().getFlowRate("mol/hr"), 1.0e-8);
    assertNotEquals(initialPumparoundDuty, pumparound.getDuty(), 1.0e-8);
  }

  /**
   * Create the C3-C5 main feed.
   *
   * @return initialized main feed
   */
  private Stream createPrimaryFeed() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 45.0, 10.0);
    fluid.addComponent("propane", 0.35);
    fluid.addComponent("n-butane", 0.45);
    fluid.addComponent("n-pentane", 0.20);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("coordinated primary feed", fluid);
    feed.setFlowRate(250.0, "kg/hr");
    feed.run();
    return feed;
  }

  /**
   * Create the cooler C3-C4 side feed on a strict subset of the main component basis.
   *
   * @return initialized side feed
   */
  private Stream createSideFeed() {
    SystemInterface fluid = new SystemSrkEos(273.15 + 35.0, 10.0);
    fluid.addComponent("propane", 0.60);
    fluid.addComponent("n-butane", 0.40);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("coordinated side feed", fluid);
    feed.setFlowRate(50.0, "kg/hr");
    feed.run();
    return feed;
  }

  /**
   * Create a ratio-controlled full fractionator with a fixed external liquid side draw.
   *
   * @param primaryFeed main feed
   * @param sideFeed secondary feed
   * @return configured column
   */
  private DistillationColumn createColumn(Stream primaryFeed, Stream sideFeed) {
    DistillationColumn column = new DistillationColumn("coordinated multifeed column", 6, true, true);
    column.addFeedStream(primaryFeed, 3);
    column.addFeedStream(sideFeed, 5);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.2);
    column.getCondenser().setOutTemperature(273.15 + 30.0);
    column.getReboiler().setOutTemperature(273.15 + 90.0);
    column.setCondenserMode(DistillationColumn.CondenserMode.PARTIAL);
    column.setCondenserRefluxRatio(1.8);
    column.setReboilerVaporBoilupRatio(1.20);
    column.setLiquidSideDrawFraction(2, 0.03);
    // Active pumparound returns are assembled by the outer tear; use the residual-monitored
    // MESH solver for the coupled inner column rather than direct substitution.
    column.setSolverType(DistillationColumn.SolverType.MESH_RESIDUAL);
    column.setMaxNumberOfIterations(80);
    column.setTemperatureTolerance(1.0e-1);
    column.setMassBalanceTolerance(2.0e-1);
    column.setEnthalpyBalanceTolerance(2.0e-1);
    column.setMaxPumparoundIterations(12);
    column.setPumparoundTolerance(1.0e-4);
    return column;
  }

  /**
   * Verify status, terminal specifications, outer-tear convergence, energy, and component closure.
   *
   * @param column solved column
   * @param pumparound configured pumparound
   * @param feeds external feeds
   */
  private void assertAcceptedAndBalanced(DistillationColumn column, DistillationColumn.ColumnPumparound pumparound,
      StreamInterface... feeds) {
    String diagnostics = column.getConvergenceDiagnostics();
    assertTrue(column.solved(), diagnostics);
    assertNotEquals(DistillationColumn.SolveStatus.FALLBACK_PRODUCTS, column.getLastSolveStatus());
    assertEquals(DistillationColumn.CondenserMode.PARTIAL, column.getCondenserMode());
    assertEquals(DistillationColumn.ReboilerMode.VAPOR_BOILUP_RATIO, column.getReboilerMode());
    assertTrue(column.isLastColumnTearConverged(), diagnostics);
    assertTrue(column.getLastColumnTearResidual() <= 1.0e-4, diagnostics);
    assertTrue(Double.isFinite(column.getLastSpecificationResidual()), diagnostics);
    assertTrue(column.getLastSpecificationResidual() <= column.getTopSpecification().getTolerance(), diagnostics);
    assertTrue(Double.isFinite(column.getEnergyBalanceError()), diagnostics);
    if (column.isEnforceEnergyBalanceTolerance()) {
      assertTrue(column.getEnergyBalanceError() <= column.getEnthalpyBalanceTolerance(), diagnostics);
    }
    assertTrue(column.isEnforceMeshResidualTolerance(), diagnostics);
    assertTrue(Double.isFinite(column.getLastMeshResidualNorm()), diagnostics);
    assertTrue(column.getLastMeshResidualNorm() <= column.getMeshResidualTolerance() + 1.0e-12, diagnostics);
    assertTrue(Double.isFinite(column.getLastMeshEnergyResidualNorm()), diagnostics);

    assertNotNull(pumparound.getDrawStream());
    assertNotNull(pumparound.getReturnStream());
    assertTrue(pumparound.getReturnStream().getFlowRate("kg/hr") > 0.0);
    assertTrue(Double.isFinite(pumparound.getDuty()) && pumparound.getDuty() < 0.0);
    assertEquals(4.0, pumparound.getDrawStream().getTemperature() - pumparound.getReturnStream().getTemperature(),
        1.0e-9);

    StreamInterface topProduct = column.getGasOutStream();
    StreamInterface bottomProduct = column.getLiquidOutStream();
    StreamInterface sideDraw = column.getSideDrawStream(2, DistillationColumn.SideDrawPhase.LIQUID);
    double totalFeedFlow = 0.0;
    for (StreamInterface feed : feeds) {
      totalFeedFlow += feed.getFlowRate("mol/hr");
    }
    double totalProductFlow = topProduct.getFlowRate("mol/hr") + bottomProduct.getFlowRate("mol/hr")
        + sideDraw.getFlowRate("mol/hr");
    assertTrue(topProduct.getFlowRate("mol/hr") > 0.0);
    assertTrue(bottomProduct.getFlowRate("mol/hr") > 0.0);
    assertTrue(sideDraw.getFlowRate("mol/hr") >= 0.0);
    assertTrue(topProduct.getTemperature("K") > 0.0);
    assertTrue(bottomProduct.getTemperature("K") > 0.0);
    assertEquals(totalFeedFlow, totalProductFlow, Math.max(1.0e-8, 5.0e-3 * totalFeedFlow));

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
