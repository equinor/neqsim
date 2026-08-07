package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
   * Test that missing hardware is reported clearly.
   */
  @Test
  public void missingHardwareThrowsForModeWrappers() {
    DistillationColumn column = new DistillationColumn("bare column", 2, false, false);

    assertThrows(IllegalStateException.class, () -> column.getCondenserMode());
    assertThrows(IllegalStateException.class, () -> column.getReboilerMode());
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

      double duty = pumparound.getReturnStream().getFluid().getEnthalpy()
          - pumparound.getDrawStream().getFluid().getEnthalpy();
      double returnFlow = pumparound.getReturnStream().getFlowRate("kg/hr");
      assertTrue(column.solved(), column.getConvergenceDiagnostics());
      assertTrue(column.isLastColumnTearConverged());
      assertEquals(temperatureDrop,
          pumparound.getDrawStream().getTemperature() - pumparound.getReturnStream().getTemperature(), 1.0e-9);
      assertTrue(returnFlow > 0.0 && returnFlow < 10000.0);
      assertTrue(duty < 0.0);
      assertTrue(Math.abs(column.getMassBalance("kg/hr")) < 1.0e-8);
      assertPumparoundProductsPhysicalAndBalanced(column);

      column.run(UUID.randomUUID());
      double repeatedDuty = pumparound.getReturnStream().getFluid().getEnthalpy()
          - pumparound.getDrawStream().getFluid().getEnthalpy();
      assertEquals(temperatureDrop,
          pumparound.getDrawStream().getTemperature() - pumparound.getReturnStream().getTemperature(), 1.0e-9);
      assertEquals(returnFlow, pumparound.getReturnStream().getFlowRate("kg/hr"), 5.0e-5 * returnFlow);
      assertEquals(duty, repeatedDuty, 5.0e-5 * Math.abs(duty));
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
      assertEquals(feedComponentFlow, productComponentFlow,
          Math.max(1.0e-6, 5.0e-3 * Math.abs(feedComponentFlow)));
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