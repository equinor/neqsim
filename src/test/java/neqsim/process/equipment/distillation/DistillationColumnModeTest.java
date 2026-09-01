package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.validation.ValidationResult;

/**
 * Tests explicit condenser and reboiler mode configuration on distillation columns.
 *
 * @author esol
 * @version 1.0
 */
public class DistillationColumnModeTest {

  /**
   * Test explicit condenser mode switching.
   */
  @Test
  public void condenserModeCanBeConfiguredAtColumnLevel() {
    DistillationColumn column = new DistillationColumn("mode column", 2, true, true);

    assertEquals(DistillationColumn.CondenserMode.PARTIAL, column.getCondenserMode());
    column.setCondenserMode(DistillationColumn.CondenserMode.TOTAL);
    assertEquals(DistillationColumn.CondenserMode.TOTAL, column.getCondenserMode());
    column.setCondenserLiquidReflux(10.0, "kg/hr");
    assertEquals(DistillationColumn.CondenserMode.LIQUID_REFLUX_SPLIT, column.getCondenserMode());
    column.setCondenserMode(DistillationColumn.CondenserMode.PARTIAL);
    assertEquals(DistillationColumn.CondenserMode.PARTIAL, column.getCondenserMode());
  }

  /**
   * Reject fixed liquid-reflux flow and reflux-ratio specifications that claim the same condenser split.
   */
  @Test
  public void fixedLiquidRefluxAndRatioSpecificationAreMutuallyExclusive() {
    ColumnSpecification refluxRatio = new ColumnSpecification(ColumnSpecification.SpecificationType.REFLUX_RATIO,
        ColumnSpecification.ProductLocation.TOP, 0.8);

    DistillationColumn fixedFirst = new DistillationColumn("fixed-first column", 2, true, true);
    fixedFirst.setCondenserLiquidReflux(100.0, "kg/hr");
    IllegalArgumentException ratioException = assertThrows(IllegalArgumentException.class,
        () -> fixedFirst.setTopSpecification(refluxRatio));
    String ratioMessage = ratioException.getMessage();
    assertNotNull(ratioMessage);
    assertTrue(ratioMessage.contains("fixed liquid-reflux"));
    assertTrue(ratioMessage.contains("reflux-ratio"));
    assertNull(fixedFirst.getTopSpecification());
    assertEquals(DistillationColumn.CondenserMode.LIQUID_REFLUX_SPLIT, fixedFirst.getCondenserMode());

    fixedFirst.setCondenserMode(DistillationColumn.CondenserMode.PARTIAL);
    fixedFirst.setTopSpecification(refluxRatio);
    assertEquals(refluxRatio, fixedFirst.getTopSpecification());

    DistillationColumn ratioFirst = new DistillationColumn("ratio-first column", 2, true, true);
    ratioFirst.setCondenserRefluxRatio(0.8);
    IllegalArgumentException fixedException = assertThrows(IllegalArgumentException.class,
        () -> ratioFirst.setCondenserLiquidReflux(100.0, "kg/hr"));
    String fixedMessage = fixedException.getMessage();
    assertNotNull(fixedMessage);
    assertTrue(fixedMessage.contains("select one condenser reflux control"));
    assertEquals(DistillationColumn.CondenserMode.PARTIAL, ratioFirst.getCondenserMode());
    assertNotNull(ratioFirst.getTopSpecification());
    assertEquals(0.8, ratioFirst.getTopSpecification().getTargetValue(), 0.0);

    double retainedCondenserRatio = ratioFirst.getCondenser().getRefluxRatio();
    ColumnSpecification retainedSpecification = ratioFirst.getTopSpecification();
    assertThrows(IllegalArgumentException.class, () -> ratioFirst.setCondenserRefluxRatio(Double.NaN));
    assertEquals(retainedCondenserRatio, ratioFirst.getCondenser().getRefluxRatio(), 0.0);
    assertEquals(retainedSpecification, ratioFirst.getTopSpecification());

    DistillationColumn legacyConflict = new DistillationColumn("legacy conflict column", 2, true, true);
    legacyConflict.setTopSpecification(refluxRatio);
    legacyConflict.getCondenser().setSeparation_with_liquid_reflux(true, 100.0, "kg/hr");
    ValidationResult validation = legacyConflict.validateSpecifications();
    assertTrue(!validation.isValid());
    assertTrue(validation.getErrors().stream()
        .anyMatch(error -> error.getCategory().equals("specification.degreesOfFreedom")));
    IllegalStateException runException = assertThrows(IllegalStateException.class,
        () -> legacyConflict.run(UUID.randomUUID()));
    assertNotNull(runException.getMessage());
    assertTrue(runException.getMessage().contains("select one condenser reflux control"));
  }

  /**
   * Test explicit reboiler mode switching.
   */
  @Test
  public void reboilerModeCanBeConfiguredAtColumnLevel() {
    DistillationColumn column = new DistillationColumn("reboiler mode column", 2, true, true);

    assertEquals(DistillationColumn.ReboilerMode.EQUILIBRIUM, column.getReboilerMode());
    column.setReboilerBoilupRatio(0.8);
    assertEquals(DistillationColumn.ReboilerMode.VAPOR_BOILUP_RATIO, column.getReboilerMode());
    assertNotNull(column.getBottomSpecification());
    assertEquals(ColumnSpecification.SpecificationType.REFLUX_RATIO, column.getBottomSpecification().getType());

    column.setReboilerMode(DistillationColumn.ReboilerMode.EQUILIBRIUM);
    assertEquals(DistillationColumn.ReboilerMode.EQUILIBRIUM, column.getReboilerMode());
    assertNull(column.getBottomSpecification());

    column.setBottomProductPurity("n-pentane", 0.95);
    ColumnSpecification bottomPurity = column.getBottomSpecification();
    column.setReboilerVaporBoilupRatio(1.0);
    column.setReboilerMode(DistillationColumn.ReboilerMode.EQUILIBRIUM);
    assertSame(bottomPurity, column.getBottomSpecification());
  }

  /**
   * Reject invalid terminal ratios without changing valid direct or stored ratio controls.
   */
  @Test
  public void invalidTerminalRatiosFailAtomically() {
    DistillationColumn column = createTerminalControlColumn("atomic terminal ratio column");
    column.setReboilerBoilupRatio(0.8);

    double condenserRatio = column.getCondenser().getRefluxRatio();
    double reboilerRatio = column.getReboiler().getRefluxRatio();
    ColumnSpecification topSpecification = column.getTopSpecification();
    ColumnSpecification bottomSpecification = column.getBottomSpecification();

    assertThrows(IllegalArgumentException.class, () -> column.getCondenser().setRefluxRatio(-1.0));
    assertThrows(IllegalArgumentException.class, () -> column.getReboiler().setRefluxRatio(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> column.setReboilerBoilupRatio(Double.POSITIVE_INFINITY));

    assertEquals(condenserRatio, column.getCondenser().getRefluxRatio(), 0.0);
    assertEquals(reboilerRatio, column.getReboiler().getRefluxRatio(), 0.0);
    assertSame(topSpecification, column.getTopSpecification());
    assertSame(bottomSpecification, column.getBottomSpecification());
    assertEquals(DistillationColumn.CondenserMode.PARTIAL, column.getCondenserMode());
    assertEquals(DistillationColumn.ReboilerMode.VAPOR_BOILUP_RATIO, column.getReboilerMode());
  }

  /**
   * Qualify partial-condenser and vapor-boilup ratio control across a repeated solve and nearby operating point.
   */
  @Test
  public void partialCondenserAndVaporBoilupRatiosRemainPhysicalAcrossNearbyPoints() {
    DistillationColumn column = createTerminalControlColumn("nearby terminal ratio column");
    column.setReboilerVaporBoilupRatio(1.20);

    column.run(UUID.randomUUID());

    assertTrue(column.solved(), column.getConvergenceDiagnostics());
    assertEquals(DistillationColumn.CondenserMode.PARTIAL, column.getCondenserMode());
    assertEquals(DistillationColumn.ReboilerMode.VAPOR_BOILUP_RATIO, column.getReboilerMode());
    assertTerminalRatioProductsPhysicalAndBalanced(column);
    double firstGasFlow = column.getGasOutStream().getFlowRate("mol/hr");
    double firstLiquidFlow = column.getLiquidOutStream().getFlowRate("mol/hr");

    column.run(UUID.randomUUID());

    assertTrue(column.solved(), column.getConvergenceDiagnostics());
    assertTerminalRatioProductsPhysicalAndBalanced(column);
    assertEquals(firstGasFlow, column.getGasOutStream().getFlowRate("mol/hr"), Math.max(1.0e-8, 5.0e-5 * firstGasFlow));
    assertEquals(firstLiquidFlow, column.getLiquidOutStream().getFlowRate("mol/hr"),
        Math.max(1.0e-8, 5.0e-5 * firstLiquidFlow));

    column.setReboilerVaporBoilupRatio(1.25);
    column.run(UUID.randomUUID());

    assertTrue(column.solved(), column.getConvergenceDiagnostics());
    assertTerminalRatioProductsPhysicalAndBalanced(column);
    double feedFlow = column.getFeedStreams(3).get(0).getFlowRate("mol/hr");
    double nearbyGasFlow = column.getGasOutStream().getFlowRate("mol/hr");
    assertTrue(Math.abs(nearbyGasFlow - firstGasFlow) > 1.0e-8 * feedFlow);
  }

  /**
   * Verify conservation, bounds, diagnostics, and direct specification satisfaction for a terminal-ratio case.
   *
   * @param column solved ratio-controlled column
   */
  private void assertTerminalRatioProductsPhysicalAndBalanced(DistillationColumn column) {
    StreamInterface feed = column.getFeedStreams(3).get(0);
    StreamInterface gas = column.getGasOutStream();
    StreamInterface liquid = column.getLiquidOutStream();
    double feedFlow = feed.getFlowRate("mol/hr");
    double gasFlow = gas.getFlowRate("mol/hr");
    double liquidFlow = liquid.getFlowRate("mol/hr");

    assertTrue(Double.isFinite(gasFlow) && gasFlow > 0.0);
    assertTrue(Double.isFinite(liquidFlow) && liquidFlow > 0.0);
    assertTrue(Double.isFinite(gas.getTemperature()) && gas.getTemperature() > 0.0);
    assertTrue(Double.isFinite(liquid.getTemperature()) && liquid.getTemperature() > 0.0);
    assertEquals(feedFlow, gasFlow + liquidFlow, 5.0e-3 * feedFlow);
    assertTrue(Double.isFinite(column.getEnergyBalanceError()));
    assertTrue(Double.isFinite(column.getLastMeshResidualNorm()));
    assertTrue(Double.isFinite(column.getLastSpecificationResidual()));
    assertTrue(column.getLastSpecificationResidual() <= column.getTopSpecification().getTolerance());

    double[] feedComposition = feed.getThermoSystem().getMolarComposition();
    double[] gasComposition = gas.getThermoSystem().getMolarComposition();
    double[] liquidComposition = liquid.getThermoSystem().getMolarComposition();
    for (int componentIndex = 0; componentIndex < feedComposition.length; componentIndex++) {
      assertTrue(gasComposition[componentIndex] >= 0.0 && gasComposition[componentIndex] <= 1.0);
      assertTrue(liquidComposition[componentIndex] >= 0.0 && liquidComposition[componentIndex] <= 1.0);
      double feedComponentFlow = feedFlow * feedComposition[componentIndex];
      double productComponentFlow = gasFlow * gasComposition[componentIndex]
          + liquidFlow * liquidComposition[componentIndex];
      assertEquals(feedComponentFlow, productComponentFlow, Math.max(1.0e-6, 5.0e-3 * Math.abs(feedComponentFlow)));
    }
  }

  /**
   * Test that missing hardware is reported clearly.
   */
  @Test
  public void missingHardwareThrowsForModeWrappers() {
    DistillationColumn column = new DistillationColumn("bare column", 2, false, false);

    assertThrows(IllegalStateException.class, () -> column.getCondenserMode());
    assertThrows(IllegalStateException.class, () -> column.getReboilerMode());
  }

  /**
   * Preserve ratio control across partial/total mode selection and clear it without discarding an unrelated top spec.
   */
  @Test
  public void condenserRatioControlHasExplicitLifecycle() {
    DistillationColumn column = createTerminalControlColumn("ratio lifecycle column");

    column.setCondenserMode(DistillationColumn.CondenserMode.TOTAL);
    assertEquals(DistillationColumn.CondenserMode.TOTAL, column.getCondenserMode());
    assertTrue(column.getCondenser().isRefluxSet());
    assertEquals(1.8, column.getCondenser().getRefluxRatio(), 0.0);

    column.setTopProductPurity("propane", 0.70);
    ColumnSpecification topPurity = column.getTopSpecification();
    column.clearCondenserRefluxRatio();

    assertFalse(column.getCondenser().isRefluxSet());
    assertSame(topPurity, column.getTopSpecification());
  }

  /**
   * Reject an incomplete total-condenser declaration before a previously accepted tray state can be changed.
   */
  @Test
  public void totalCondenserWithoutRatioFailsBeforeMutatingAcceptedProducts() {
    DistillationColumn column = createTerminalControlColumn("total preflight column");
    column.run(UUID.randomUUID());
    assertTrue(column.solved(), column.getConvergenceDiagnostics());

    double gasFlow = column.getGasOutStream().getFlowRate("mol/hr");
    double liquidFlow = column.getLiquidOutStream().getFlowRate("mol/hr");
    double gasTemperature = column.getGasOutStream().getTemperature();
    double liquidTemperature = column.getLiquidOutStream().getTemperature();

    column.clearCondenserRefluxRatio();
    column.setCondenserMode(DistillationColumn.CondenserMode.TOTAL);
    ValidationResult validation = column.validateSpecifications();

    assertFalse(validation.isValid());
    assertTrue(
        validation.getErrors().stream().anyMatch(error -> error.getCategory().equals("specification.terminalMode")));
    IllegalStateException exception = assertThrows(IllegalStateException.class, () -> column.run(UUID.randomUUID()));
    assertTrue(exception.getMessage().contains("requires an explicit reflux ratio"));
    assertEquals(gasFlow, column.getGasOutStream().getFlowRate("mol/hr"), 0.0);
    assertEquals(liquidFlow, column.getLiquidOutStream().getFlowRate("mol/hr"), 0.0);
    assertEquals(gasTemperature, column.getGasOutStream().getTemperature(), 0.0);
    assertEquals(liquidTemperature, column.getLiquidOutStream().getTemperature(), 0.0);
  }

  /**
   * Reject outer product specifications whose endpoint temperature handle is disabled by active ratio control.
   */
  @Test
  public void ratioControlledEndpointsRejectAdjustableProductSpecifications() {
    DistillationColumn topControlled = createTerminalControlColumn("top ownership column");
    topControlled.setTopProductPurity("propane", 0.70);

    ValidationResult topValidation = topControlled.validateSpecifications();
    assertFalse(topValidation.isValid());
    assertTrue(topValidation.getErrors().stream()
        .anyMatch(error -> error.getCategory().equals("specification.controlOwnership")));
    assertThrows(IllegalStateException.class, () -> topControlled.run(UUID.randomUUID()));

    topControlled.clearCondenserRefluxRatio();
    assertTrue(topControlled.validateSpecifications().isValid());

    DistillationColumn bottomControlled = createTerminalControlColumn("bottom ownership column");
    bottomControlled.setBottomProductPurity("n-pentane", 0.70);
    bottomControlled.setReboilerVaporBoilupRatio(1.2);

    ValidationResult bottomValidation = bottomControlled.validateSpecifications();
    assertFalse(bottomValidation.isValid());
    assertTrue(bottomValidation.getErrors().stream()
        .anyMatch(error -> error.getCategory().equals("specification.controlOwnership")));
    assertThrows(IllegalStateException.class, () -> bottomControlled.run(UUID.randomUUID()));

    bottomControlled.setReboilerMode(DistillationColumn.ReboilerMode.EQUILIBRIUM);
    assertTrue(bottomControlled.validateSpecifications().isValid());
  }

  /**
   * Test liquid pumparound configuration and validation.
   */
  @Test
  public void liquidPumparoundCanBeConfiguredAtColumnLevel() {
    DistillationColumn column = new DistillationColumn("pumparound column", 3, true, true);

    DistillationColumn.ColumnPumparound pumparound = column.addLiquidPumparound("PA-1", 2, 1, 0.15, 10.0);

    assertEquals(1, column.getPumparounds().size());
    assertEquals("PA-1", pumparound.getName());
    assertEquals(2, pumparound.getDrawTrayNumber());
    assertEquals(1, pumparound.getReturnTrayNumber());
    assertEquals(0.15, column.getTray(2).getLiquidPumparoundDrawFraction(), 1.0e-12);
    assertThrows(IllegalArgumentException.class, () -> column.addLiquidPumparound("duplicate", 2, 1, 0.10, 10.0));
  }

  /**
   * Test that a pumparound run updates and re-solves with a final return stream state.
   */
  @Test
  public void liquidPumparoundRunCreatesFinalReturnStream() {
    Stream feed = createLiquidPentaneFeed("pumparound liquid feed");
    DistillationColumn column = new DistillationColumn("running pumparound column", 1, false, false);
    column.addFeedStream(feed, 0);
    DistillationColumn.ColumnPumparound pumparound = column.addLiquidPumparound("PA-run", 0, 0, 0.20, 5.0);

    column.run(UUID.randomUUID());

    assertNotNull(pumparound.getReturnStream());
    assertTrue(pumparound.getReturnStream().getFlowRate("kg/hr") >= 0.0);
    assertTrue(Double.isFinite(pumparound.getDuty()));
    assertTrue(column.getLastColumnTearIterationCount() > 0);
    assertTrue(column.getLastPumparoundRelativeChange() >= 0.0);
  }

  /** Test that reinitialization preserves the configured multistage pumparound return state. */
  @Test
  public void cooledMultistagePumparoundPreservesReturnState() {
    double[] temperatureDrops = { 4.0, 5.0 };
    for (double temperatureDrop : temperatureDrops) {
      DistillationColumn column = createMultistagePumparoundColumn(temperatureDrop);
      DistillationColumn.ColumnPumparound pumparound = column.getPumparounds().get(0);
      column.run(UUID.randomUUID());

      double enthalpyDifference = pumparound.getReturnStream().getFluid().getEnthalpy()
          - pumparound.getDrawStream().getFluid().getEnthalpy();
      double duty = pumparound.getDuty();
      double returnFlow = pumparound.getReturnStream().getFlowRate("kg/hr");
      assertTrue(column.solved(), column.getConvergenceDiagnostics());
      assertTrue(column.isLastColumnTearConverged());
      assertEquals(temperatureDrop,
          pumparound.getDrawStream().getTemperature() - pumparound.getReturnStream().getTemperature(), 1.0e-9);
      assertTrue(returnFlow > 0.0 && returnFlow < 10000.0);
      assertTrue(duty < 0.0);
      assertEquals(enthalpyDifference, duty, Math.max(1.0e-9, 1.0e-12 * Math.abs(duty)));
      assertEquals(duty / 1000.0, pumparound.getDuty("kW"), Math.max(1.0e-12, 1.0e-12 * Math.abs(duty)));
      assertTrue(Math.abs(column.getMassBalance("kg/hr")) < 1.0e-8);
      assertTrue(column.getEnergyBalanceError() <= column.getEnthalpyBalanceTolerance(),
          column.getConvergenceDiagnostics());
      assertPumparoundProductsPhysicalAndBalanced(column);

      column.run(UUID.randomUUID());
      double repeatedEnthalpyDifference = pumparound.getReturnStream().getFluid().getEnthalpy()
          - pumparound.getDrawStream().getFluid().getEnthalpy();
      double repeatedDuty = pumparound.getDuty();
      assertEquals(temperatureDrop,
          pumparound.getDrawStream().getTemperature() - pumparound.getReturnStream().getTemperature(), 1.0e-9);
      assertEquals(returnFlow, pumparound.getReturnStream().getFlowRate("kg/hr"), 5.0e-5 * returnFlow);
      assertEquals(repeatedEnthalpyDifference, repeatedDuty, Math.max(1.0e-9, 1.0e-12 * Math.abs(repeatedDuty)));
      assertEquals(duty, repeatedDuty, 5.0e-5 * Math.abs(duty));
      assertTrue(column.getEnergyBalanceError() <= column.getEnthalpyBalanceTolerance(),
          column.getConvergenceDiagnostics());
      assertPumparoundProductsPhysicalAndBalanced(column);
    }
  }

  /**
   * Test hydraulic pressure-drop coupling API and diagnostics.
   */
  @Test
  public void hydraulicPressureDropCouplingCanBeEnabled() {
    DistillationColumn column = new DistillationColumn("hydraulic coupling column", 2, true, true);

    column.enableHydraulicPressureDropCoupling("sieve");

    assertTrue(column.isHydraulicPressureDropCouplingEnabled());
    assertEquals(0.0, column.getLastHydraulicPressureDropPa(), 1.0e-12);
    assertThrows(IllegalArgumentException.class, () -> column.setHydraulicPressureDropInternalsType(""));
  }

  /**
   * Test that the simplified dynamic model is clearly labelled as experimental.
   */
  @Test
  public void dynamicColumnModelReportsExperimentalStatus() {
    DistillationColumn column = new DistillationColumn("dynamic label column", 2, true, true);
    column.setDynamicColumnEnabled(true);
    ValidationResult result = column.validateSetup();

    assertEquals(DistillationColumn.DynamicColumnModel.EXPERIMENTAL_EULER, column.getDynamicColumnModel());
    assertTrue(column.isDynamicColumnModelExperimental());
    assertTrue(result.hasWarnings());
    assertTrue(result.getWarnings().stream()
        .anyMatch(warning -> warning.getMessage().contains("explicit-Euler holdup screening")));
  }

  /**
   * Test that nonphysical pumparound return temperatures fail explicitly.
   */
  @Test
  public void nonPhysicalPumparoundReturnTemperatureThrows() {
    Stream feed = createLiquidPentaneFeed("cold pumparound feed");
    DistillationColumn column = new DistillationColumn("cold pumparound column", 1, false, false);
    column.addFeedStream(feed, 0);
    column.addLiquidPumparound("PA-cold", 0, 0, 0.20, 400.0);

    assertThrows(IllegalStateException.class, () -> column.run(UUID.randomUUID()));
  }

  /** Verify physical terminal products and per-component material closure for the pumparound case. */
  private void assertPumparoundProductsPhysicalAndBalanced(DistillationColumn column) {
    StreamInterface feed = column.getFeedStreams(3).get(0);
    StreamInterface gas = column.getGasOutStream();
    StreamInterface liquid = column.getLiquidOutStream();
    double feedFlow = feed.getFlowRate("mol/hr");
    double gasFlow = gas.getFlowRate("mol/hr");
    double liquidFlow = liquid.getFlowRate("mol/hr");

    assertTrue(Double.isFinite(gasFlow) && gasFlow >= 0.0);
    assertTrue(Double.isFinite(liquidFlow) && liquidFlow >= 0.0);
    assertTrue(Double.isFinite(gas.getTemperature()) && gas.getTemperature() > 0.0);
    assertTrue(Double.isFinite(liquid.getTemperature()) && liquid.getTemperature() > 0.0);
    assertEquals(feedFlow, gasFlow + liquidFlow, 5.0e-3 * feedFlow);

    double[] feedComposition = feed.getThermoSystem().getMolarComposition();
    double[] gasComposition = gas.getThermoSystem().getMolarComposition();
    double[] liquidComposition = liquid.getThermoSystem().getMolarComposition();
    for (int componentIndex = 0; componentIndex < feedComposition.length; componentIndex++) {
      assertTrue(gasComposition[componentIndex] >= 0.0 && gasComposition[componentIndex] <= 1.0);
      assertTrue(liquidComposition[componentIndex] >= 0.0 && liquidComposition[componentIndex] <= 1.0);
      double feedComponentFlow = feedFlow * feedComposition[componentIndex];
      double productComponentFlow = gasFlow * gasComposition[componentIndex]
          + liquidFlow * liquidComposition[componentIndex];
      assertEquals(feedComponentFlow, productComponentFlow, Math.max(1.0e-6, 5.0e-3 * Math.abs(feedComponentFlow)));
    }
  }

  /**
   * Create the convergent multicomponent reboiler column used by pumparound state tests.
   *
   * @param temperatureDrop configured pumparound cooling in Kelvin
   * @return unrun column with one liquid pumparound
   */
  private DistillationColumn createMultistagePumparoundColumn(double temperatureDrop) {
    SystemSrkEos fluid = new SystemSrkEos(293.15, 10.0);
    fluid.addComponent("propane", 40.0);
    fluid.addComponent("n-butane", 30.0);
    fluid.addComponent("n-pentane", 30.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("fractionator pumparound feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn("fractionator pumparound column", 6, true, false);
    column.addFeedStream(feed, 3);
    column.getReboiler().setOutTemperature(353.15);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.5);
    column.setSolverType(DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
    column.addLiquidPumparound("PA-multistage", 3, 5, 0.02, temperatureDrop);
    column.setMaxPumparoundIterations(12);
    column.setPumparoundTolerance(1.0e-4);
    return column;
  }

  /**
   * Create a realistic three-component fractionator for terminal-control tests.
   *
   * @param name column name
   * @return configured, unrun column with partial-condenser ratio control
   */
  private DistillationColumn createTerminalControlColumn(String name) {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 45.0, 10.0);
    fluid.addComponent("propane", 0.35);
    fluid.addComponent("n-butane", 0.45);
    fluid.addComponent("n-pentane", 0.20);
    fluid.setMixingRule("classic");

    Stream feed = new Stream(name + " feed", fluid);
    feed.setFlowRate(250.0, "kg/hr");
    feed.run();

    DistillationColumn column = new DistillationColumn(name, 6, true, true);
    column.addFeedStream(feed, 3);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.2);
    column.getCondenser().setOutTemperature(273.15 + 30.0);
    column.getReboiler().setOutTemperature(273.15 + 90.0);
    column.setCondenserRefluxRatio(1.8);
    column.setSolverType(DistillationColumn.SolverType.AUTO);
    column.setMaxNumberOfIterations(80);
    column.setTemperatureTolerance(1.0e-1);
    column.setMassBalanceTolerance(2.0e-1);
    column.setEnthalpyBalanceTolerance(2.0e-1);
    return column;
  }

  /**
   * Create a liquid pentane feed for pumparound tests.
   *
   * @param name stream name
   * @return initialized n-pentane stream
   */
  private Stream createLiquidPentaneFeed(String name) {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 2.0);
    fluid.addComponent("n-pentane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream(name, fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();
    return feed;
  }
}