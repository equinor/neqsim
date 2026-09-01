package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests vapor and liquid side-draw split handling on distillation trays and columns.
 *
 * @author esol
 * @version 1.0
 */
@Tag("slow")
public class SimpleTraySideDrawTest {

  /** Column whose copied candidates can be forced to fail before solver telemetry is reset. */
  private static final class RejectingCandidateColumn extends DistillationColumn {
    private static final long serialVersionUID = 1000L;
    private boolean rejectDuringInitialization = false;

    private RejectingCandidateColumn(String name, int numberOfTrays, boolean hasReboiler, boolean hasCondenser) {
      super(name, numberOfTrays, hasReboiler, hasCondenser);
    }

    @Override
    public void init() {
      if (rejectDuringInitialization) {
        throw new IllegalStateException("Deterministic candidate rejection before solver work");
      }
      super.init();
    }

    private void rejectDuringInitialization() {
      rejectDuringInitialization = true;
    }
  }

  /**
   * Test that vapor side draws remove flow from internal tray traffic while conserving phase flow.
   */
  @Test
  public void gasSideDrawSplitsTrayOutletFlow() {
    Stream feed = createMethaneFeed("side draw feed");

    SimpleTray referenceTray = new SimpleTray("reference tray");
    referenceTray.addStream(feed);
    referenceTray.run(UUID.randomUUID());
    double referenceGasFlow = referenceTray.getGasOutStream().getFlowRate("kg/hr");

    SimpleTray sideDrawTray = new SimpleTray("side draw tray");
    sideDrawTray.addStream(feed);
    sideDrawTray.setGasSideDrawFraction(0.25);
    sideDrawTray.run(UUID.randomUUID());

    double internalGasFlow = sideDrawTray.getGasOutStream().getFlowRate("kg/hr");
    double sideDrawFlow = sideDrawTray.getGasSideDrawStream().getFlowRate("kg/hr");

    assertEquals(referenceGasFlow, internalGasFlow + sideDrawFlow, referenceGasFlow * 1.0e-8);
    assertEquals(0.25 * referenceGasFlow, sideDrawFlow, referenceGasFlow * 1.0e-8);
  }

  /**
   * Test that column side draws are exposed as outlet streams.
   */
  @Test
  public void columnReportsSideDrawAsOutletStream() {
    Stream feed = createMethaneFeed("column side draw feed");
    DistillationColumn column = new DistillationColumn("SideDrawColumn", 1, false, false);
    column.addFeedStream(feed, 0);
    column.setGasSideDrawFraction(0, 0.10);
    column.run(UUID.randomUUID());

    StreamInterface sideDrawStream = column.getSideDrawStream(0, DistillationColumn.SideDrawPhase.GAS);
    List<StreamInterface> outlets = column.getOutletStreams();

    assertTrue(sideDrawStream.getFlowRate("kg/hr") > 0.0);
    assertTrue(outlets.contains(sideDrawStream));
    assertEquals(0.0, column.getMassBalance("kg/hr"), feed.getFlowRate("kg/hr") * 1.0e-6);
  }

  /** Test that energy diagnostics include gas and liquid side draws and ignore empty phase templates. */
  @Test
  public void columnEnergyBalanceIncludesSideDrawStreams() {
    Stream gasFeed = createMethaneFeed("gas energy-balance feed");
    DistillationColumn gasColumn = new DistillationColumn("GasEnergyBalanceColumn", 1, false, false);
    gasColumn.addFeedStream(gasFeed, 0);
    gasColumn.setGasSideDrawFraction(0, 0.25);
    gasColumn.run(UUID.randomUUID());

    assertEquals(DistillationColumn.SolveStatus.RIGOROUS_CONVERGED, gasColumn.getLastSolveStatus());
    assertEquals(25.0, gasColumn.getSideDrawStream(0, DistillationColumn.SideDrawPhase.GAS).getFlowRate("kg/hr"),
        25.0e-8);
    assertEquals(0.0, gasColumn.getEnergyBalanceError(), 1.0e-12);

    SystemSrkEos liquidFluid = new SystemSrkEos(300.0, 2.0);
    liquidFluid.addComponent("n-pentane", 1.0);
    liquidFluid.setMixingRule("classic");
    Stream liquidFeed = new Stream("liquid energy-balance feed", liquidFluid);
    liquidFeed.setFlowRate(100.0, "kg/hr");
    liquidFeed.run();
    DistillationColumn liquidColumn = new DistillationColumn("LiquidEnergyBalanceColumn", 1, false, false);
    liquidColumn.addFeedStream(liquidFeed, 0);
    liquidColumn.setLiquidSideDrawFraction(0, 0.25);
    liquidColumn.run(UUID.randomUUID());

    assertEquals(DistillationColumn.SolveStatus.RIGOROUS_CONVERGED, liquidColumn.getLastSolveStatus());
    assertEquals(25.0, liquidColumn.getSideDrawStream(0, DistillationColumn.SideDrawPhase.LIQUID).getFlowRate("kg/hr"),
        25.0e-8);
    assertEquals(0.0, liquidColumn.getEnergyBalanceError(), 1.0e-12);
  }

  /**
   * Test that a side-draw flow specification adjusts the draw fraction to meet target flow.
   */
  @Test
  public void sideDrawFlowSpecificationClosesProductFlow() {
    Stream feed = createMethaneFeed("specified side draw feed");
    DistillationColumn column = new DistillationColumn("SpecifiedSideDrawColumn", 1, false, false);
    column.addFeedStream(feed, 0);

    DistillationColumn.ColumnSideDrawSpecification specification = column.addSideDrawFlowSpecification(0,
        DistillationColumn.SideDrawPhase.GAS, 25.0, "kg/hr");
    specification.setTolerance(1.0e-5);
    column.run(UUID.randomUUID());

    StreamInterface sideDrawStream = column.getSideDrawStream(0, DistillationColumn.SideDrawPhase.GAS);

    assertEquals(25.0, sideDrawStream.getFlowRate("kg/hr"), 25.0e-4);
    assertTrue(specification.getLastRelativeResidual() < 1.0e-5);
    assertTrue(column.isLastColumnTearConverged());
    assertTrue(column.getLastColumnTearIterationCount() > 0);
    assertEquals(0.0, column.getMassBalance("kg/hr"), feed.getFlowRate("kg/hr") * 1.0e-6);
  }

  /**
   * Test that a multistage liquid side-draw flow specification retains only accepted inner solves.
   */
  @Test
  public void multistageLiquidSideDrawFlowSpecificationConverges() {
    Stream feed = createFractionatorFeed("multistage specified side draw feed");
    feed.setTemperature(338.15, "K");
    feed.run();
    DistillationColumn column = createFractionatorColumn("MultistageSpecifiedSideDrawColumn", feed);

    DistillationColumn.ColumnSideDrawSpecification specification = column.addSideDrawFlowSpecification(3,
        DistillationColumn.SideDrawPhase.LIQUID, 20.160854543137464, "kg/hr");
    specification.setTolerance(1.0e-5);
    column.setMaxColumnTearIterations(30);

    column.run(UUID.randomUUID());

    assertEquals(DistillationColumn.SolveStatus.RIGOROUS_CONVERGED, column.getLastSolveStatus(),
        column.getConvergenceDiagnostics());
    assertTrue(column.isLastColumnTearConverged(), column.getConvergenceDiagnostics());
    assertTrue(specification.getLastRelativeResidual() <= specification.getTolerance(),
        column.getConvergenceDiagnostics());
    assertEquals(specification.getTargetFlowRate(), specification.getLastActualFlowRate(),
        specification.getTargetFlowRate() * specification.getTolerance());
    assertEquals(0.0, column.getMassBalance("kg/hr"), 1.0e-6, column.getConvergenceDiagnostics());
    assertTrue(column.getEnergyBalanceError() < 1.0e-2, column.getConvergenceDiagnostics());
    assertTrue(column.getLastColumnTearRejectedCandidateCount() > 0,
        "the regression should exercise rejected-candidate isolation");
    assertTrue(column.getLastColumnTearRollbackCount() <= column.getLastColumnTearRejectedCandidateCount(),
        "only rejections after an accepted state exists can require rollback");
    assertTrue(column.getLastColumnTearCandidateHistory().contains("FALLBACK_PRODUCTS"));
    assertComponentMassBalance(feed, column.getSideDrawStream(3, DistillationColumn.SideDrawPhase.LIQUID), column);

    double firstActualFlow = specification.getLastActualFlowRate();
    column.run(UUID.randomUUID());
    assertEquals(DistillationColumn.SolveStatus.RIGOROUS_CONVERGED, column.getLastSolveStatus(),
        column.getConvergenceDiagnostics());
    assertEquals(firstActualFlow, specification.getLastActualFlowRate(), 1.0e-10,
        "an identical repeated solve should retain the same accepted product flow");

    DistillationColumn copiedColumn = (DistillationColumn) column.copy();
    DistillationColumn.ColumnSideDrawSpecification copiedSpecification = copiedColumn.getSideDrawSpecifications()
        .get(0);
    assertEquals(0, copiedColumn.getLastColumnTearRejectedCandidateCount(),
        "transient controller counters should reset when the column is copied");
    assertEquals("", copiedColumn.getLastColumnTearCandidateHistory(),
        "transient candidate history should reset when the column is copied");
    copiedColumn.run(UUID.randomUUID());
    assertEquals(DistillationColumn.SolveStatus.RIGOROUS_CONVERGED, copiedColumn.getLastSolveStatus(),
        copiedColumn.getConvergenceDiagnostics());
    assertEquals(firstActualFlow, copiedSpecification.getLastActualFlowRate(), 1.0e-10);
  }

  /**
   * Test the more severe positive-control target generated by a fixed liquid draw fraction of 0.08.
   */
  @Test
  public void highMultistageLiquidSideDrawFlowSpecificationConverges() {
    Stream feed = createFractionatorFeed("high multistage specified side draw feed");
    feed.setTemperature(338.15, "K");
    feed.run();
    DistillationColumn column = createFractionatorColumn("HighMultistageSpecifiedSideDrawColumn", feed);
    DistillationColumn.ColumnSideDrawSpecification specification = column.addSideDrawFlowSpecification(3,
        DistillationColumn.SideDrawPhase.LIQUID, 32.60437100137601, "kg/hr");
    specification.setTolerance(1.0e-5);
    column.setMaxColumnTearIterations(30);

    column.run(UUID.randomUUID());

    assertEquals(DistillationColumn.SolveStatus.RIGOROUS_CONVERGED, column.getLastSolveStatus(),
        column.getConvergenceDiagnostics());
    assertTrue(column.isLastColumnTearConverged(), column.getConvergenceDiagnostics());
    assertTrue(specification.getLastRelativeResidual() <= specification.getTolerance(),
        column.getConvergenceDiagnostics());
    assertEquals(specification.getTargetFlowRate(), specification.getLastActualFlowRate(),
        specification.getTargetFlowRate() * specification.getTolerance());
    assertEquals(0.0, column.getMassBalance("kg/hr"), 1.0e-6, column.getConvergenceDiagnostics());
    assertTrue(column.getLastColumnTearRejectedCandidateCount() > 0,
        "the controller should reject the invalid large multiplicative candidate");
    assertTrue(column.getLastColumnTearRollbackCount() > 0,
        "the rejected cold candidate should retain the previously accepted state");
    assertTrue(column.getLastColumnTearRollbackCount() <= column.getLastColumnTearRejectedCandidateCount(),
        "rollback count cannot exceed the rejected candidate count");
    assertTrue(column.getLastColumnTearCandidateHistory().contains("continuation fraction="),
        "the rejected cold candidate should be retried from an accepted state");
    assertComponentMassBalance(feed, column.getSideDrawStream(3, DistillationColumn.SideDrawPhase.LIQUID), column);
  }

  /** Test a nearby 260 kg/h feed point using the accepted fixed-fraction target at that flow. */
  @Test
  public void nearbyFlowLiquidSideDrawFlowSpecificationConverges() {
    Stream feed = createFractionatorFeed("nearby-flow specified side draw feed");
    feed.setTemperature(338.15, "K");
    feed.setFlowRate(260.0, "kg/hr");
    feed.run();
    DistillationColumn column = createFractionatorColumn("NearbyFlowSpecifiedSideDrawColumn", feed);
    column.setLiquidSideDrawFraction(3, 0.09);
    DistillationColumn.ColumnSideDrawSpecification specification = column.addSideDrawFlowSpecification(3,
        DistillationColumn.SideDrawPhase.LIQUID, 25.14627680712515, "kg/hr");
    specification.setTolerance(1.0e-5);

    column.run(UUID.randomUUID());

    assertEquals(DistillationColumn.SolveStatus.RIGOROUS_CONVERGED, column.getLastSolveStatus(),
        column.getConvergenceDiagnostics());
    assertTrue(column.isLastColumnTearConverged(), column.getConvergenceDiagnostics());
    assertTrue(specification.getLastRelativeResidual() <= specification.getTolerance(),
        column.getConvergenceDiagnostics());
    assertEquals(0.0, column.getMassBalance("kg/hr"), 1.0e-6, column.getConvergenceDiagnostics());
    assertComponentMassBalance(feed, column.getSideDrawStream(3, DistillationColumn.SideDrawPhase.LIQUID), column);
  }

  /** Test that rejected copied candidates do not report inherited inner iterations. */
  @Test
  public void rejectedCandidatesDoNotInheritStaleInnerIterationTelemetry() throws ReflectiveOperationException {
    Stream feed = createFractionatorFeed("stale candidate telemetry feed");
    feed.setTemperature(338.15, "K");
    feed.run();
    RejectingCandidateColumn column = createRejectingCandidateColumn("StaleCandidateTelemetryColumn", feed);
    DistillationColumn.ColumnSideDrawSpecification specification = column.addSideDrawFlowSpecification(3,
        DistillationColumn.SideDrawPhase.LIQUID, 20.160854543137464, "kg/hr");
    specification.setTolerance(1.0e-5);
    // The setup solve establishes a valid template; its convergence rate is not under test here.
    specification.setMaxIterations(30);
    column.setMaxColumnTearIterations(30);

    column.run(UUID.randomUUID());
    assertTrue(column.isLastColumnTearConverged(), column.getConvergenceDiagnostics());

    // Keep the deterministic rejection phase bounded to the telemetry count asserted below.
    specification.setMaxIterations(12);
    column.setMaxColumnTearIterations(12);
    RejectingCandidateColumn template = (RejectingCandidateColumn) getPrivateField(column,
        "singleSideDrawCandidateTemplate");
    setPrivateInt(template, "lastIterationCount", 777);
    template.rejectDuringInitialization();

    column.run(UUID.randomUUID());

    assertEquals(12, column.getLastColumnTearRejectedCandidateCount());
    assertEquals(0, column.getLastColumnTearRollbackCount());
    assertEquals(0, column.getLastColumnTearInnerIterationCount(),
        "candidates rejected before solver work must not inherit copied iteration telemetry");
    assertTrue(column.getLastColumnTearCandidateHistory().contains("#1 fraction="));
    assertTrue(column.getLastColumnTearCandidateHistory().contains("#12 fraction="));
    assertTrue(column.getLastColumnTearCandidateHistory().contains("status=FAILED, accepted=false"));
    assertFalse(column.getLastColumnTearCandidateHistory().contains("accepted=true"));
  }

  /**
   * Test that side-draw stream caches are refreshed when a tray is rerun.
   */
  @Test
  public void sideDrawCacheRefreshesAfterTrayRerun() {
    Stream feed = createMethaneFeed("cache refresh feed");
    SimpleTray tray = new SimpleTray("cache refresh tray");
    tray.addStream(feed);
    tray.setGasSideDrawFraction(0.25);
    tray.run(UUID.randomUUID());
    assertEquals(25.0, tray.getGasSideDrawStream().getFlowRate("kg/hr"), 25.0e-4);

    feed.setFlowRate(200.0, "kg/hr");
    feed.run();
    tray.run(UUID.randomUUID());

    assertEquals(50.0, tray.getGasSideDrawStream().getFlowRate("kg/hr"), 50.0e-4);
  }

  /**
   * Test that impossible side-draw specs are bounded and reported as not converged.
   */
  @Test
  public void impossibleSideDrawFlowSpecIsBoundedAndReportsNonConvergence() {
    Stream feed = createMethaneFeed("impossible side draw feed");
    SimpleTray maxDrawTray = new SimpleTray("maximum gas draw tray");
    maxDrawTray.addStream(feed);
    maxDrawTray.setGasSideDrawFraction(1.0);
    maxDrawTray.run(UUID.randomUUID());
    double maximumGasDrawFlow = maxDrawTray.getGasSideDrawStream().getFlowRate("kg/hr");

    DistillationColumn column = new DistillationColumn("ImpossibleSideDrawColumn", 1, false, false);
    column.addFeedStream(feed, 0);
    column.addSideDrawFlowSpecification(0, DistillationColumn.SideDrawPhase.GAS, maximumGasDrawFlow * 2.0, "kg/hr");

    column.run(UUID.randomUUID());

    assertFalse(column.isLastColumnTearConverged());
    assertTrue(column.getTray(0).getGasSideDrawFraction() <= 1.0);
    assertTrue(column.getSideDrawStream(0, DistillationColumn.SideDrawPhase.GAS)
        .getFlowRate("kg/hr") <= maximumGasDrawFlow + 1.0e-8);
  }

  /**
   * Test that duplicate flow targets for one tray-phase side draw are rejected at registration.
   */
  @Test
  public void duplicateSideDrawFlowSpecificationsAreRejectedAtRegistration() {
    Stream feed = createFractionatorFeed("duplicate side draw feed");
    DistillationColumn column = new DistillationColumn("DuplicateSideDrawColumn", 5, true, true);
    column.addFeedStream(feed, 3);
    column.setTopPressure(8.0);
    column.setBottomPressure(8.3);
    column.setCondenserTemperature(303.15);
    column.setReboilerTemperature(383.15);
    column.setCondenserRefluxRatio(1.5);

    column.addSideDrawFlowSpecification(3, DistillationColumn.SideDrawPhase.LIQUID, 25.0, "kg/hr");
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> column.addSideDrawFlowSpecification(3, DistillationColumn.SideDrawPhase.LIQUID, 30.0, "kg/hr"));

    String message = String.valueOf(exception.getMessage()).toLowerCase(Locale.ROOT);
    assertTrue(message.contains("tray 3"));
    assertTrue(message.contains("liquid"));
    assertEquals(1, column.getSideDrawSpecifications().size());

    column.addSideDrawFlowSpecification(3, DistillationColumn.SideDrawPhase.GAS, 5.0, "kg/hr");
    column.addSideDrawFlowSpecification(4, DistillationColumn.SideDrawPhase.LIQUID, 5.0, "kg/hr");
    assertEquals(3, column.getSideDrawSpecifications().size());
  }

  /**
   * Test that invalid side-draw fractions are rejected.
   */
  @Test
  public void invalidSideDrawFractionThrows() {
    SimpleTray tray = new SimpleTray("validation tray");
    assertThrows(IllegalArgumentException.class, () -> tray.setGasSideDrawFraction(-0.1));
    assertThrows(IllegalArgumentException.class, () -> tray.setLiquidSideDrawFraction(1.1));
  }

  /**
   * Test that liquid side product and pumparound fractions cannot exceed all liquid traffic.
   */
  @Test
  public void liquidSideDrawAndPumparoundFractionsAreBoundedTogether() {
    SimpleTray tray = new SimpleTray("liquid split validation tray");
    tray.setLiquidSideDrawFraction(0.60);
    assertThrows(IllegalArgumentException.class, () -> tray.setLiquidPumparoundDrawFraction(0.50));
  }

  /**
   * Create a multicomponent hydrocarbon feed for fractionator configuration tests.
   *
   * @param name stream name
   * @return initialized fractionator feed
   */
  private Stream createFractionatorFeed(String name) {
    SystemSrkEos fluid = new SystemSrkEos(333.15, 8.0);
    fluid.addComponent("propane", 0.20);
    fluid.addComponent("n-butane", 0.35);
    fluid.addComponent("n-pentane", 0.30);
    fluid.addComponent("n-hexane", 0.15);
    fluid.setMixingRule("classic");
    Stream feed = new Stream(name, fluid);
    feed.setFlowRate(250.0, "kg/hr");
    feed.run();
    return feed;
  }

  /**
   * Configure the deterministic multistage fractionator used by side-draw controller regressions.
   *
   * @param name column name
   * @param feed initialized feed stream
   * @return configured unsolved column
   */
  private DistillationColumn createFractionatorColumn(String name, Stream feed) {
    DistillationColumn column = new DistillationColumn(name, 5, true, true);
    column.addFeedStream(feed, 3);
    column.setTopPressure(8.0);
    column.setBottomPressure(8.3);
    column.setCondenserTemperature(303.15);
    column.setReboilerTemperature(383.15);
    column.setCondenserRefluxRatio(1.5);
    return column;
  }

  /** Configure the fractionator variant used to reject copied candidates deterministically. */
  private RejectingCandidateColumn createRejectingCandidateColumn(String name, Stream feed) {
    RejectingCandidateColumn column = new RejectingCandidateColumn(name, 5, true, true);
    column.addFeedStream(feed, 3);
    column.setTopPressure(8.0);
    column.setBottomPressure(8.3);
    column.setCondenserTemperature(303.15);
    column.setReboilerTemperature(383.15);
    column.setCondenserRefluxRatio(1.5);
    return column;
  }

  /** Read one private column field for deterministic failure-path setup. */
  private Object getPrivateField(DistillationColumn column, String fieldName) throws ReflectiveOperationException {
    Field field = DistillationColumn.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(column);
  }

  /** Set one private integer telemetry field for deterministic failure-path setup. */
  private void setPrivateInt(DistillationColumn column, String fieldName, int value)
      throws ReflectiveOperationException {
    Field field = DistillationColumn.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.setInt(column, value);
  }

  /**
   * Assert per-component mass closure for the terminal and liquid side products.
   *
   * @param feed external column feed
   * @param sideDraw accepted side-product stream
   * @param column solved column
   */
  private void assertComponentMassBalance(StreamInterface feed, StreamInterface sideDraw, DistillationColumn column) {
    SystemInterface feedFluid = feed.getThermoSystem();
    SystemInterface gasProductFluid = column.getGasOutStream().getThermoSystem();
    SystemInterface liquidProductFluid = column.getLiquidOutStream().getThermoSystem();
    SystemInterface sideDrawFluid = sideDraw.getThermoSystem();
    for (int componentIndex = 0; componentIndex < feedFluid.getNumberOfComponents(); componentIndex++) {
      double componentIn = getComponentMassFlowKgPerHour(feedFluid, componentIndex);
      double componentOut = getComponentMassFlowKgPerHour(gasProductFluid, componentIndex)
          + getComponentMassFlowKgPerHour(liquidProductFluid, componentIndex)
          + getComponentMassFlowKgPerHour(sideDrawFluid, componentIndex);
      assertEquals(componentIn, componentOut, 1.0e-6,
          "side-draw component mass balance mismatch for " + feedFluid.getComponent(componentIndex).getName());
    }
  }

  /**
   * Calculate one component mass flow from total molar flow, composition, and molar mass.
   *
   * @param fluid thermodynamic system containing the component
   * @param componentIndex component index
   * @return component mass flow in kg/h
   */
  private double getComponentMassFlowKgPerHour(SystemInterface fluid, int componentIndex) {
    return fluid.getFlowRate("mole/hr") * fluid.getComponent(componentIndex).getMolarMass()
        * fluid.getMolarComposition()[componentIndex];
  }

  /**
   * Create a gas feed for side-draw split tests.
   *
   * @param name stream name
   * @return initialized methane stream
   */
  private Stream createMethaneFeed(String name) {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 10.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream(name, fluid);
    feed.setFlowRate(100.0, "kg/hr");
    feed.run();
    return feed;
  }
}
