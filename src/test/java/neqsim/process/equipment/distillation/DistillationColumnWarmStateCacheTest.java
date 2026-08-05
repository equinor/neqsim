package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression tests for column warm-state compatibility and cache invalidation.
 *
 * <p>
 * The cache reuses an accepted tray solution when a fingerprint of the solver inputs is unchanged. The fingerprint
 * originally covered only the feed streams and the optional top/bottom column specifications, so a change to column
 * pressure or to a reboiler/condenser temperature - neither of which marks the column for re-initialization - returned
 * the previous solution bit for bit. These tests pin the column configuration into the fingerprint.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class DistillationColumnWarmStateCacheTest {

  /**
   * Builds a small stripper solved with the simultaneous-correction solver.
   *
   * @return an unrun column configured for Naphtali-Sandholm
   */
  private static DistillationColumn buildColumn() {
    SystemSrkEos fluid = new SystemSrkEos(273.15 + 20.0, 10.0);
    fluid.addComponent("propane", 40.0);
    fluid.addComponent("n-butane", 30.0);
    fluid.addComponent("n-pentane", 30.0);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(5000.0, "kg/hr");
    feed.setTemperature(20.0, "C");
    feed.setPressure(10.0, "bara");
    feed.run();

    DistillationColumn column = new DistillationColumn("warm cache column", 6, true, false);
    column.addFeedStream(feed, 3);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.5);
    column.getReboiler().setOutTemperature(273.15 + 80.0);
    column.setSolverType(DistillationColumn.SolverType.NAPHTALI_SANDHOLM);
    return column;
  }

  /**
   * Builds the repository's proven compact binary damped column with both terminal stages.
   *
   * @return an unrun column configured for exact sequential-state reuse
   */
  private static DistillationColumn buildCondenserColumn() {
    SystemSrkEos fluid = new SystemSrkEos(298.15, 5.0);
    fluid.addComponent("methane", 1.0);
    fluid.addComponent("ethane", 1.0);
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("binary condenser feed", fluid);
    feed.setFlowRate(2.0, "mol/sec");
    feed.run();

    DistillationColumn column = new DistillationColumn("condenser cache column", 1, true, true);
    column.addFeedStream(feed, 1);
    column.setSolverType(DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
    column.setRelaxationFactor(0.5);
    return column;
  }

  /**
   * A reboiler temperature change must invalidate the warm state. {@code Reboiler.setOutTemperature} does not mark the
   * column for re-initialization, so the fingerprint is the only thing that can catch it.
   */
  @Test
  public void reboilerTemperatureChangeInvalidatesWarmState() {
    DistillationColumn column = buildColumn();
    column.run();
    double firstGasFlow = column.getGasOutStream().getFlowRate("kg/hr");
    double firstBottomFlow = column.getLiquidOutStream().getFlowRate("kg/hr");
    assertTrue(firstGasFlow > 0.0, "the first solve should produce overhead flow");

    column.getReboiler().setOutTemperature(273.15 + 110.0);
    column.run();

    assertNotEquals(firstGasFlow, column.getGasOutStream().getFlowRate("kg/hr"), 1.0,
        "a 30 K reboiler temperature change must change the overhead flow instead of reusing the warm state");
    assertNotEquals(firstBottomFlow, column.getLiquidOutStream().getFlowRate("kg/hr"), 1.0,
        "a 30 K reboiler temperature change must change the bottoms flow instead of reusing the warm state");
  }

  /**
   * Changing only the active reboiler operating mode must invalidate an otherwise identical warm state.
   *
   * <p>
   * The legacy tray API stores a default reflux ratio even while ratio control is inactive. Activating that same
   * numeric ratio changes the reboiler from temperature/equilibrium operation to a PV reflux flash, so the mode flag is
   * part of the mathematical problem even though the stored ratio value is unchanged.
   * </p>
   */
  @Test
  public void reboilerModeChangeWithUnchangedStoredRatioInvalidatesWarmState() {
    DistillationColumn column = buildColumn();
    column.run();
    double firstGasFlow = column.getGasOutStream().getFlowRate("kg/hr");
    double firstBottomFlow = column.getLiquidOutStream().getFlowRate("kg/hr");
    double storedRatio = column.getReboiler().getRefluxRatio();
    assertFalse(column.getReboiler().isRefluxSet(), "the baseline should use temperature/equilibrium operation");

    column.getReboiler().setRefluxRatio(storedRatio);
    column.run();

    assertFalse(column.wasNaphtaliSandholmWarmStateReused(),
        "activating ratio mode must solve the changed reboiler equations instead of reusing the old state");
    assertNotEquals(firstGasFlow, column.getGasOutStream().getFlowRate("kg/hr"), 1.0,
        "activating ratio mode must update the overhead flow");
    assertNotEquals(firstBottomFlow, column.getLiquidOutStream().getFlowRate("kg/hr"), 1.0,
        "activating ratio mode must update the bottoms flow");
    assertTrue(column.solved(), column.getConvergenceDiagnostics());
    assertPhysicalAndBalanced(column.getFeedStreams(3).get(0), column);
  }

  /**
   * Activating the condenser's stored reflux ratio must invalidate an otherwise identical warm state.
   *
   * <p>
   * The legacy tray API stores a default reflux ratio even while ratio control is inactive. Activating that same
   * numeric value changes the condenser from equilibrium operation to a PV reflux flash while leaving the reflux ratio
   * and total-condenser flag unchanged.
   * </p>
   */
  @Test
  public void condenserRatioModeWithUnchangedStoredRatioInvalidatesWarmState() {
    DistillationColumn column = buildCondenserColumn();
    column.run();
    assertTrue(column.solved(), column.getConvergenceDiagnostics());
    double storedRatio = column.getCondenser().getRefluxRatio();

    column.run();
    assertTrue(column.wasSequentialWarmStateReused(),
        "an unchanged condenser case must be eligible for exact sequential-state reuse");
    assertEquals(0, column.getLastIterationCount(), "unchanged exact reuse must execute zero tray iterations");

    column.getCondenser().setRefluxRatio(storedRatio);
    column.run();

    assertFalse(column.wasSequentialWarmStateReused(),
        "activating ratio mode must solve the changed condenser equations instead of reusing equilibrium products");
    assertTrue(column.getLastIterationCount() > 0, "the changed condenser equations must execute tray iterations");
    assertTrue(column.solved(), column.getConvergenceDiagnostics());
    assertPhysicalAndBalancedAllowingZeroProduct(column.getFeedStreams(1).get(0), column);
  }

  /**
   * Switching between the two active reflux equation sets must invalidate an otherwise identical warm state.
   *
   * <p>
   * Both ratio-controlled PV flashing and fixed liquid separation set the legacy reflux-active flag. The fixed split
   * therefore needs its own configuration identity even when the stored ratio and total-condenser flag do not change.
   * </p>
   */
  @Test
  public void fixedLiquidRefluxModeInvalidatesRatioWarmState() {
    DistillationColumn column = buildCondenserColumn();
    double storedRatio = column.getCondenser().getRefluxRatio();
    column.getCondenser().setRefluxRatio(storedRatio);
    column.run();
    assertTrue(column.solved(), column.getConvergenceDiagnostics());

    column.run();
    assertTrue(column.wasSequentialWarmStateReused(),
        "an unchanged ratio-controlled condenser must be eligible for exact sequential-state reuse");
    assertEquals(0, column.getLastIterationCount(), "unchanged exact reuse must execute zero tray iterations");

    column.getCondenser().setSeparation_with_liquid_reflux(true, 0.0, "kg/hr");
    column.run();

    assertFalse(column.wasSequentialWarmStateReused(),
        "fixed liquid separation must solve its own equations instead of reusing ratio-controlled products");
    assertTrue(column.getLastIterationCount() > 0, "the changed condenser equations must execute tray iterations");
    assertTrue(column.solved(), column.getConvergenceDiagnostics());
    assertPhysicalAndBalancedWithCondenserProduct(column.getFeedStreams(1).get(0), column);
  }

  /**
   * A column pressure change must invalidate the warm state. {@code setTopPressure} and {@code setBottomPressure} do
   * not mark the column for re-initialization either.
   */
  @Test
  public void columnPressureChangeInvalidatesWarmState() {
    DistillationColumn column = buildColumn();
    column.run();
    double firstGasFlow = column.getGasOutStream().getFlowRate("kg/hr");

    column.setTopPressure(5.0);
    column.setBottomPressure(5.5);
    column.run();

    assertNotEquals(firstGasFlow, column.getGasOutStream().getFlowRate("kg/hr"), 1.0,
        "halving the column pressure must change the product split instead of reusing the warm state");
  }

  /**
   * Re-running an unchanged column must still hit the cache - the fix must not disable the speed-up it guards.
   */
  @Test
  public void unchangedColumnStillReusesWarmState() {
    DistillationColumn column = buildColumn();
    column.run();
    double firstGasFlow = column.getGasOutStream().getFlowRate("kg/hr");

    column.run();

    assertEquals(firstGasFlow, column.getGasOutStream().getFlowRate("kg/hr"), 1.0e-9,
        "an unchanged column must return the same solution");
    assertTrue(column.getLastSolveStatusReason().contains("Reused"),
        "an unchanged column should reuse the accepted warm state, reason was " + column.getLastSolveStatusReason());
  }

  /**
   * Tightening an active convergence gate must invalidate an otherwise exact Naphtali-Sandholm cache hit.
   *
   * <p>
   * Solver inputs are unchanged, but the previously accepted MESH residual no longer satisfies the caller's current
   * contract. Returning that state with zero iterations would make the invocation non-converged without giving the
   * solver an opportunity to improve or fail explicitly.
   * </p>
   */
  @Test
  public void tightenedConvergenceGateInvalidatesNaphtaliExactReuse() {
    DistillationColumn column = buildColumn();
    column.run();
    assertTrue(column.solved(), column.getConvergenceDiagnostics());

    double acceptedMeshResidual = column.getLastMeshResidualNorm();
    assertTrue(Double.isFinite(acceptedMeshResidual) && acceptedMeshResidual > 0.0,
        "the regression needs a positive finite accepted MESH residual");
    assertTrue(acceptedMeshResidual < column.getMeshResidualTolerance(),
        "the baseline result must satisfy the original MESH gate");

    column.run();
    assertTrue(column.wasNaphtaliSandholmWarmStateReused(),
        "an unchanged accepted case must retain exact zero-iteration reuse");
    assertEquals(0, column.getLastIterationCount());

    column.setMeshResidualTolerance(Math.nextDown(acceptedMeshResidual));
    assertFalse(column.solved(), "the stored state must reflect the newly tightened convergence contract");
    assertFalse(column.willReuseNaphtaliSandholmWarmState(),
        "a state outside the current convergence gates must not be predicted as an exact cache hit");

    column.run();

    assertFalse(column.wasNaphtaliSandholmWarmStateReused(),
        "the tightened gate must execute the solver path instead of returning the stale accepted state");
    assertPhysicalAndBalanced(column.getFeedStreams(3).get(0), column);
  }

  /**
   * Changing inactive convergence tolerances must not invalidate an otherwise exact cache hit.
   */
  @Test
  public void inactiveToleranceChangesRetainNaphtaliExactReuse() {
    for (double feedTemperatureC : new double[] { 20.0, 25.0 }) {
      DistillationColumn column = buildColumn();
      StreamInterface feed = column.getFeedStreams(3).get(0);
      feed.setTemperature(feedTemperatureC, "C");
      feed.run();
      column.setEnforceMeshResidualTolerance(false);
      column.setEnforceEnergyBalanceTolerance(false);
      column.run();
      assertTrue(column.solved(), column.getConvergenceDiagnostics());

      column.run();
      assertTrue(column.wasNaphtaliSandholmWarmStateReused());
      assertEquals(0, column.getLastIterationCount());
      double gasFlow = column.getGasOutStream().getFlowRate("kg/hr");
      double liquidFlow = column.getLiquidOutStream().getFlowRate("kg/hr");

      column.setMeshResidualTolerance(column.getMeshResidualTolerance() * 0.5);
      column.setTrayMaterialBalanceTolerance(column.getTrayMaterialBalanceTolerance() * 0.5);
      column.setMeshProductDrawResidualTolerance(column.getMeshProductDrawResidualTolerance() * 0.5);
      column.setEnthalpyBalanceTolerance(column.getEnthalpyBalanceTolerance() * 0.5);
      column.setColumnTearTolerance(5.0e-5);
      column.setPumparoundTolerance(5.0e-5);

      assertTrue(column.willReuseNaphtaliSandholmWarmState(),
          "inactive tolerances are not part of the active convergence contract");
      column.run();

      assertTrue(column.wasNaphtaliSandholmWarmStateReused());
      assertEquals(0, column.getLastIterationCount());
      assertEquals(gasFlow, column.getGasOutStream().getFlowRate("kg/hr"), 1.0e-9);
      assertEquals(liquidFlow, column.getLiquidOutStream().getFlowRate("kg/hr"), 1.0e-9);
      assertPhysicalAndBalanced(feed, column);
    }
  }

  /** Internal pumparound returns must never be captured as legacy external tray feeds. */
  @Test
  public void pumparoundReturnIsNotCapturedAsExternalFeed() {
    for (double feedTemperatureC : new double[] { 20.0, 25.0 }) {
      DistillationColumn column = buildColumn();
      StreamInterface feed = column.getFeedStreams(3).get(0);
      feed.setTemperature(feedTemperatureC, "C");
      feed.run();
      column.addLiquidPumparound("PA-1", 2, 4, 0.01, 2.0);
      column.setPumparoundTolerance(1.0e-3);
      column.run();

      assertTrue(column.solved(), column.getConvergenceDiagnostics());
      assertEquals(1, column.getInletStreams().size(),
          "an internal pumparound return must not be exposed or balanced as a second external feed");
      assertSame(feed, column.getInletStreams().get(0));
      assertPhysicalAndBalanced(feed, column);

      double gasFlow = column.getGasOutStream().getFlowRate("kg/hr");
      double liquidFlow = column.getLiquidOutStream().getFlowRate("kg/hr");
      column.run();

      assertTrue(column.solved(), column.getConvergenceDiagnostics());
      assertEquals(1, column.getInletStreams().size(),
          "repeated runs must not accumulate the internal return in the external-feed registry");
      assertSame(feed, column.getInletStreams().get(0));
      assertEquals(gasFlow, column.getGasOutStream().getFlowRate("kg/hr"), Math.max(1.0e-6, gasFlow * 5.0e-3));
      assertEquals(liquidFlow, column.getLiquidOutStream().getFlowRate("kg/hr"), Math.max(1.0e-6, liquidFlow * 5.0e-3));
      assertPhysicalAndBalanced(feed, column);
    }
  }

  /** Molar feed rate used to make identity-only input changes collide with the legacy fingerprint. */
  private static final double IDENTITY_TEST_FLOW_MOL_PER_HOUR = 100000.0;

  /**
   * Column subclass that records real initialization passes without changing solver behavior.
   */
  private static final class TrackingDistillationColumn extends DistillationColumn {
    private int initializationCount = 0;

    private TrackingDistillationColumn(String name, int numberOfTrays, boolean hasReboiler, boolean hasCondenser) {
      super(name, numberOfTrays, hasReboiler, hasCondenser);
    }

    /** {@inheritDoc} */
    @Override
    public void init() {
      boolean initializationRequested = isDoInitializion();
      super.init();
      if (initializationRequested) {
        initializationCount++;
      }
    }

    private int getInitializationCount() {
      return initializationCount;
    }
  }

  /** Feed and column references used when replacing a solved feed thermodynamic system. */
  private static final class ColumnCase {
    private final Stream feed;
    private final TrackingDistillationColumn column;

    private ColumnCase(Stream feed, TrackingDistillationColumn column) {
      this.feed = feed;
      this.column = column;
    }
  }

  /** Registered feed, legacy direct side feed, and column used by direct-feed compatibility tests. */
  private static final class DirectFeedColumnCase {
    private final Stream registeredFeed;
    private final Stream directFeed;
    private final TrackingDistillationColumn column;

    private DirectFeedColumnCase(Stream registeredFeed, Stream directFeed, TrackingDistillationColumn column) {
      this.registeredFeed = registeredFeed;
      this.directFeed = directFeed;
      this.column = column;
    }
  }

  /**
   * Build a hydrocarbon fluid whose numeric composition can remain unchanged while identity changes.
   *
   * @param usePengRobinson whether to use PR instead of SRK
   * @param middleComponent component at numeric composition index one
   * @param mixingRule integer mixing-rule identifier
   * @return configured fluid
   */
  private static SystemInterface createIdentityTestFluid(boolean usePengRobinson, String middleComponent,
      int mixingRule) {
    SystemInterface fluid;
    if (usePengRobinson) {
      fluid = new SystemPrEos(273.15 + 20.0, 10.0);
    } else {
      fluid = new SystemSrkEos(273.15 + 20.0, 10.0);
    }
    fluid.addComponent("propane", 40.0);
    fluid.addComponent(middleComponent, 30.0);
    fluid.addComponent("n-pentane", 30.0);
    fluid.setMixingRule(mixingRule);
    return fluid;
  }

  /**
   * Build a small identity-regression column at a fixed molar feed rate.
   *
   * @param fluid feed thermodynamic system
   * @return feed and column references
   */
  private static ColumnCase buildIdentityColumnCase(SystemInterface fluid) {
    Stream feed = new Stream("identity feed", fluid);
    configureIdentityFeed(feed);

    TrackingDistillationColumn column = new TrackingDistillationColumn("identity cache column", 6, true, false);
    column.addFeedStream(feed, 3);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.5);
    column.getReboiler().setOutTemperature(273.15 + 80.0);
    column.setSolverType(DistillationColumn.SolverType.NAPHTALI_SANDHOLM);
    return new ColumnCase(feed, column);
  }

  /**
   * Build a column with one registered feed and one legacy side feed connected directly to a tray.
   *
   * @param directFluid thermodynamic system for the direct side feed
   * @return direct-feed column case
   */
  private static DirectFeedColumnCase buildDirectFeedColumnCase(SystemInterface directFluid) {
    ColumnCase registeredCase = buildIdentityColumnCase(createIdentityTestFluid(false, "n-butane", 2));
    Stream directFeed = new Stream("legacy direct side feed", directFluid);
    configureDirectFeed(directFeed);
    registeredCase.column.getTray(2).addStream(directFeed);
    configureDampedSubstitution(registeredCase.column);
    return new DirectFeedColumnCase(registeredCase.feed, directFeed, registeredCase.column);
  }

  /**
   * Apply conditions that deliberately keep every legacy numeric fingerprint input unchanged.
   *
   * @param feed feed to configure
   */
  private static void configureIdentityFeed(Stream feed) {
    feed.setFlowRate(IDENTITY_TEST_FLOW_MOL_PER_HOUR, "mol/hr");
    feed.setTemperature(20.0, "C");
    feed.setPressure(10.0, "bara");
    feed.run();
  }

  /**
   * Apply fixed operating conditions to a legacy direct side feed.
   *
   * @param feed direct side feed to configure
   */
  private static void configureDirectFeed(Stream feed) {
    feed.setFlowRate(0.2 * IDENTITY_TEST_FLOW_MOL_PER_HOUR, "mol/hr");
    feed.setTemperature(30.0, "C");
    feed.setPressure(10.0, "bara");
    feed.run();
  }

  /**
   * Replace a solved feed with a thermodynamically different system at identical numeric inputs.
   *
   * @param columnCase solved column case
   * @param replacementFluid replacement thermodynamic system
   */
  private static void replaceFeedFluid(ColumnCase columnCase, SystemInterface replacementFluid) {
    columnCase.feed.setThermoSystem(replacementFluid);
    configureIdentityFeed(columnCase.feed);
  }

  /**
   * Replace the direct side feed at unchanged numeric operating conditions.
   *
   * @param columnCase direct-feed case to mutate
   * @param replacementFluid replacement thermodynamic system
   */
  private static void replaceDirectFeedFluid(DirectFeedColumnCase columnCase, SystemInterface replacementFluid) {
    columnCase.directFeed.setThermoSystem(replacementFluid);
    configureDirectFeed(columnCase.directFeed);
  }

  /**
   * Verify finite, physical product states and component/total molar closure.
   *
   * @param columnCase solved column case
   */
  private static void assertPhysicalAndBalanced(ColumnCase columnCase) {
    assertPhysicalAndBalanced(columnCase.feed, columnCase.column);
  }

  /**
   * Verify finite, physical products and component/total molar closure for general stream and column types.
   *
   * @param feed column feed
   * @param column solved column
   */
  private static void assertPhysicalAndBalanced(StreamInterface feed, DistillationColumn column) {
    StreamInterface gas = column.getGasOutStream();
    StreamInterface liquid = column.getLiquidOutStream();
    String[] feedComponents = feed.getThermoSystem().getComponentNames();
    assertArrayEquals(feedComponents, gas.getThermoSystem().getComponentNames(),
        "overhead must use the current feed component identities");
    assertArrayEquals(feedComponents, liquid.getThermoSystem().getComponentNames(),
        "bottoms must use the current feed component identities");

    double feedFlow = feed.getFlowRate("mol/hr");
    double gasFlow = gas.getFlowRate("mol/hr");
    double liquidFlow = liquid.getFlowRate("mol/hr");
    assertTrue(Double.isFinite(gasFlow) && gasFlow > 0.0, "overhead flow must be finite and positive");
    assertTrue(Double.isFinite(liquidFlow) && liquidFlow > 0.0, "bottoms flow must be finite and positive");
    assertEquals(feedFlow, gasFlow + liquidFlow, 5.0e-3 * feedFlow, "total product flow must close the feed");

    double[] feedComposition = feed.getThermoSystem().getMolarComposition();
    double[] gasComposition = gas.getThermoSystem().getMolarComposition();
    double[] liquidComposition = liquid.getThermoSystem().getMolarComposition();
    for (int componentIndex = 0; componentIndex < feedComponents.length; componentIndex++) {
      double feedComponentFlow = feedFlow * feedComposition[componentIndex];
      double productComponentFlow = gasFlow * gasComposition[componentIndex]
          + liquidFlow * liquidComposition[componentIndex];
      assertEquals(feedComponentFlow, productComponentFlow, Math.max(1.0e-6, 5.0e-3 * Math.abs(feedComponentFlow)),
          "component balance must close for " + feedComponents[componentIndex]);
    }

    assertPhysicalStream(gas);
    assertPhysicalStream(liquid);
  }

  /**
   * Verify physical products and component/total closure while allowing a phase-boundary product to have zero flow.
   *
   * @param feed column feed
   * @param column solved column
   */
  private static void assertPhysicalAndBalancedAllowingZeroProduct(StreamInterface feed, DistillationColumn column) {
    StreamInterface gas = column.getGasOutStream();
    StreamInterface liquid = column.getLiquidOutStream();
    double feedFlow = feed.getFlowRate("mol/hr");
    double gasFlow = gas.getFlowRate("mol/hr");
    double liquidFlow = liquid.getFlowRate("mol/hr");

    assertTrue(Double.isFinite(gasFlow) && gasFlow >= 0.0, "overhead flow must be finite and non-negative");
    assertTrue(Double.isFinite(liquidFlow) && liquidFlow >= 0.0, "bottoms flow must be finite and non-negative");
    assertTrue(gasFlow + liquidFlow > 0.0, "at least one product must carry flow");
    assertEquals(feedFlow, gasFlow + liquidFlow, 5.0e-3 * feedFlow, "total product flow must close the feed");

    String[] feedComponents = feed.getThermoSystem().getComponentNames();
    double[] feedComposition = feed.getThermoSystem().getMolarComposition();
    double[] gasComposition = gas.getThermoSystem().getMolarComposition();
    double[] liquidComposition = liquid.getThermoSystem().getMolarComposition();
    assertArrayEquals(feedComponents, gas.getThermoSystem().getComponentNames());
    assertArrayEquals(feedComponents, liquid.getThermoSystem().getComponentNames());
    for (int componentIndex = 0; componentIndex < feedComponents.length; componentIndex++) {
      double feedComponentFlow = feedFlow * feedComposition[componentIndex];
      double productComponentFlow = gasFlow * gasComposition[componentIndex]
          + liquidFlow * liquidComposition[componentIndex];
      assertEquals(feedComponentFlow, productComponentFlow, Math.max(1.0e-6, 5.0e-3 * Math.abs(feedComponentFlow)),
          "component balance must close for " + feedComponents[componentIndex]);
    }

    assertBoundedComposition(gas);
    assertBoundedComposition(liquid);
    if (gasFlow > 0.0) {
      assertPhysicalStream(gas);
    }
    if (liquidFlow > 0.0) {
      assertPhysicalStream(liquid);
    }
  }

  /**
   * Verify physical products and component/total closure when fixed liquid separation adds a third product.
   *
   * @param feed column feed
   * @param column solved column
   */
  private static void assertPhysicalAndBalancedWithCondenserProduct(StreamInterface feed, DistillationColumn column) {
    StreamInterface gas = column.getGasOutStream();
    StreamInterface liquid = column.getLiquidOutStream();
    StreamInterface condenserLiquid = column.getCondenser().getLiquidProductStream();
    assertTrue(condenserLiquid != null, "fixed liquid separation must expose its liquid product");

    double feedFlow = feed.getFlowRate("mol/hr");
    double gasFlow = gas.getFlowRate("mol/hr");
    double liquidFlow = liquid.getFlowRate("mol/hr");
    double condenserLiquidFlow = condenserLiquid.getFlowRate("mol/hr");
    assertTrue(Double.isFinite(gasFlow) && gasFlow >= 0.0, "overhead flow must be finite and non-negative");
    assertTrue(Double.isFinite(liquidFlow) && liquidFlow >= 0.0, "bottoms flow must be finite and non-negative");
    assertTrue(Double.isFinite(condenserLiquidFlow) && condenserLiquidFlow >= 0.0,
        "condenser liquid product flow must be finite and non-negative");
    assertTrue(gasFlow + liquidFlow + condenserLiquidFlow > 0.0, "at least one product must carry flow");
    assertEquals(feedFlow, gasFlow + liquidFlow + condenserLiquidFlow, 5.0e-3 * feedFlow,
        "all three product flows must close the feed");

    String[] feedComponents = feed.getThermoSystem().getComponentNames();
    assertArrayEquals(feedComponents, gas.getThermoSystem().getComponentNames());
    assertArrayEquals(feedComponents, liquid.getThermoSystem().getComponentNames());
    assertArrayEquals(feedComponents, condenserLiquid.getThermoSystem().getComponentNames());
    double[] feedComposition = feed.getThermoSystem().getMolarComposition();
    double[] gasComposition = gas.getThermoSystem().getMolarComposition();
    double[] liquidComposition = liquid.getThermoSystem().getMolarComposition();
    double[] condenserLiquidComposition = condenserLiquid.getThermoSystem().getMolarComposition();
    for (int componentIndex = 0; componentIndex < feedComponents.length; componentIndex++) {
      double feedComponentFlow = feedFlow * feedComposition[componentIndex];
      double productComponentFlow = gasFlow * gasComposition[componentIndex]
          + liquidFlow * liquidComposition[componentIndex]
          + condenserLiquidFlow * condenserLiquidComposition[componentIndex];
      assertEquals(feedComponentFlow, productComponentFlow, Math.max(1.0e-6, 5.0e-3 * Math.abs(feedComponentFlow)),
          "component balance must close for " + feedComponents[componentIndex]);
    }

    assertBoundedComposition(gas);
    assertBoundedComposition(liquid);
    assertBoundedComposition(condenserLiquid);
    if (gasFlow > 0.0) {
      assertPhysicalStream(gas);
    }
    if (liquidFlow > 0.0) {
      assertPhysicalStream(liquid);
    }
    if (condenserLiquidFlow > 0.0) {
      assertPhysicalStream(condenserLiquid);
    }
  }

  /**
   * Verify physical temperature, pressure, and composition bounds.
   *
   * @param stream product stream
   */
  private static void assertPhysicalStream(StreamInterface stream) {
    assertTrue(Double.isFinite(stream.getTemperature("K")) && stream.getTemperature("K") > 150.0
        && stream.getTemperature("K") < 800.0, "product temperature must be physical");
    assertTrue(Double.isFinite(stream.getPressure("bara")) && stream.getPressure("bara") > 0.0,
        "product pressure must be physical");
    assertBoundedComposition(stream);
  }

  /**
   * Verify finite, normalized, and bounded product composition independently of product flow.
   *
   * @param stream product stream
   */
  private static void assertBoundedComposition(StreamInterface stream) {
    double compositionSum = 0.0;
    for (double moleFraction : stream.getThermoSystem().getMolarComposition()) {
      assertTrue(Double.isFinite(moleFraction) && moleFraction >= -1.0e-12 && moleFraction <= 1.0 + 1.0e-12,
          "product mole fractions must stay bounded");
      compositionSum += moleFraction;
    }
    assertEquals(1.0, compositionSum, 1.0e-8, "product mole fractions must sum to one");
  }

  /**
   * Compare a re-solved mutated column with a newly constructed cold-reference column.
   *
   * @param expected fresh cold-reference column
   * @param actual mutated and re-solved column
   */
  private static void assertColdReferenceEquivalent(DistillationColumn expected, DistillationColumn actual) {
    assertStreamEquivalent(expected.getGasOutStream(), actual.getGasOutStream());
    assertStreamEquivalent(expected.getLiquidOutStream(), actual.getLiquidOutStream());
  }

  /**
   * Compare product identity, flow, temperature, pressure, and composition.
   *
   * <p>
   * The tolerances confirm that the mutated column produced the replacement fluid's answer rather than a stale cached
   * one; a stale answer differs by orders of magnitude, not by parts per million. They are not bit-for-bit equality
   * checks: {@code init()} on an existing tray network seeds the condenser temperature from the linked top tray while a
   * freshly built column seeds it from the feed tray, so the two runs enter the solver on different temperature
   * profiles and converge to the same solution only to within the solver tolerance.
   * </p>
   *
   * @param expected cold-reference stream
   * @param actual re-solved stream
   */
  private static void assertStreamEquivalent(StreamInterface expected, StreamInterface actual) {
    assertArrayEquals(expected.getThermoSystem().getComponentNames(), actual.getThermoSystem().getComponentNames());
    assertEquals(expected.getThermoSystem().getModelName(), actual.getThermoSystem().getModelName());
    assertEquals(expected.getThermoSystem().getMixingRuleName(), actual.getThermoSystem().getMixingRuleName());

    double expectedFlow = expected.getFlowRate("mol/hr");
    assertEquals(expectedFlow, actual.getFlowRate("mol/hr"), Math.max(1.0e-6, Math.abs(expectedFlow) * 1.0e-3));
    assertEquals(expected.getTemperature("K"), actual.getTemperature("K"), 5.0e-2);
    assertEquals(expected.getPressure("bara"), actual.getPressure("bara"), 1.0e-6);

    double[] expectedComposition = expected.getThermoSystem().getMolarComposition();
    double[] actualComposition = actual.getThermoSystem().getMolarComposition();
    assertEquals(expectedComposition.length, actualComposition.length);
    for (int componentIndex = 0; componentIndex < expectedComposition.length; componentIndex++) {
      assertEquals(expectedComposition[componentIndex], actualComposition[componentIndex], 1.0e-4);
    }
  }

  /**
   * Verify that the newly solved state is stable on an unchanged re-run.
   *
   * <p>
   * When the previous solve was actually answered by the simultaneous-correction solver, the re-run must be an exact
   * zero-iteration cache hit that neither touches the tray network nor moves the products.
   * </p>
   *
   * <p>
   * A rebuilt column may fall back to damped substitution instead: the Naphtali cache is deliberately not armed for a
   * state produced by another strategy. That path re-initializes on every call, and because
   * {@code solveDampedFallbackFromFreshInitialization} re-initializes on top of the tray temperatures left behind by
   * the rejected accelerator rather than from a clean profile, consecutive identical runs settle on slightly different
   * points. The loose bound below documents that known drift while still catching a column that wanders.
   * </p>
   *
   * @param columnCase solved column case
   */
  private static void assertNextRunReusesExactly(ColumnCase columnCase) {
    boolean naphtaliAnsweredPreviousSolve = columnCase.column
        .getLastSolverTypeUsed() == DistillationColumn.SolverType.NAPHTALI_SANDHOLM;
    int initializationCount = columnCase.column.getInitializationCount();
    double gasFlow = columnCase.column.getGasOutStream().getFlowRate("mol/hr");
    double liquidFlow = columnCase.column.getLiquidOutStream().getFlowRate("mol/hr");

    columnCase.column.run();

    double flowTolerance = naphtaliAnsweredPreviousSolve ? 1.0e-6 : 2.0e-2;
    if (naphtaliAnsweredPreviousSolve) {
      assertTrue(columnCase.column.wasNaphtaliSandholmWarmStateReused(),
          "an unchanged Naphtali-Sandholm state must become exactly reusable, but the re-run ended as "
              + columnCase.column.getLastSolverTypeUsed() + "/" + columnCase.column.getLastSolveStatus() + " ("
              + columnCase.column.getLastSolveStatusReason() + ")");
      assertEquals(initializationCount, columnCase.column.getInitializationCount(),
          "exact reuse must not initialize the column");
    }
    assertEquals(gasFlow, columnCase.column.getGasOutStream().getFlowRate("mol/hr"),
        Math.max(1.0e-9, Math.abs(gasFlow) * flowTolerance), "an unchanged re-run must reproduce the overhead flow");
    assertEquals(liquidFlow, columnCase.column.getLiquidOutStream().getFlowRate("mol/hr"),
        Math.max(1.0e-9, Math.abs(liquidFlow) * flowTolerance), "an unchanged re-run must reproduce the bottoms flow");
  }

  /**
   * A component-name change with identical numeric mole fractions must invalidate exact reuse and rebuild tray fluids.
   */
  @Test
  public void componentIdentityChangeForcesColdInitialization() {
    ColumnCase mutated = buildIdentityColumnCase(createIdentityTestFluid(false, "n-butane", 2));
    mutated.column.run();
    int initialInitializationCount = mutated.column.getInitializationCount();

    replaceFeedFluid(mutated, createIdentityTestFluid(false, "i-butane", 2));
    mutated.column.run();

    assertFalse(mutated.column.wasNaphtaliSandholmWarmStateReused(),
        "different component identities must not reuse the previous products");
    assertTrue(mutated.column.getInitializationCount() > initialInitializationCount,
        "different component identities must force column initialization");

    ColumnCase coldReference = buildIdentityColumnCase(createIdentityTestFluid(false, "i-butane", 2));
    coldReference.column.run();
    assertColdReferenceEquivalent(coldReference.column, mutated.column);
    assertPhysicalAndBalanced(mutated);
    assertNextRunReusesExactly(mutated);
  }

  /**
   * Changing from SRK to PR at identical feed numbers must invalidate exact reuse and rebuild tray fluids.
   */
  @Test
  public void equationOfStateChangeForcesColdInitialization() {
    ColumnCase mutated = buildIdentityColumnCase(createIdentityTestFluid(false, "n-butane", 2));
    mutated.column.run();
    int initialInitializationCount = mutated.column.getInitializationCount();

    replaceFeedFluid(mutated, createIdentityTestFluid(true, "n-butane", 2));
    mutated.column.run();

    assertFalse(mutated.column.wasNaphtaliSandholmWarmStateReused(),
        "a different equation of state must not reuse the previous products");
    assertTrue(mutated.column.getInitializationCount() > initialInitializationCount,
        "a different equation of state must force column initialization");

    ColumnCase coldReference = buildIdentityColumnCase(createIdentityTestFluid(true, "n-butane", 2));
    coldReference.column.run();
    assertColdReferenceEquivalent(coldReference.column, mutated.column);
    assertPhysicalAndBalanced(mutated);
    assertNextRunReusesExactly(mutated);

    int initializationCountBeforeNearbyPoint = mutated.column.getInitializationCount();
    mutated.column.getReboiler().setOutTemperature(273.15 + 82.0);
    mutated.column.run();
    assertFalse(mutated.column.wasNaphtaliSandholmWarmStateReused(),
        "a nearby operating point must be solved rather than exactly reused");
    assertEquals(initializationCountBeforeNearbyPoint, mutated.column.getInitializationCount(),
        "an unchanged thermodynamic identity should preserve the iterative warm start");

    ColumnCase nearbyColdReference = buildIdentityColumnCase(createIdentityTestFluid(true, "n-butane", 2));
    nearbyColdReference.column.getReboiler().setOutTemperature(273.15 + 82.0);
    nearbyColdReference.column.run();
    assertColdReferenceEquivalent(nearbyColdReference.column, mutated.column);
    assertPhysicalAndBalanced(mutated);
  }

  /**
   * Changing the mixing rule at identical feed numbers must invalidate exact reuse and rebuild tray fluids.
   */
  @Test
  public void mixingRuleChangeForcesColdInitialization() {
    ColumnCase mutated = buildIdentityColumnCase(createIdentityTestFluid(false, "n-butane", 2));
    mutated.column.run();
    int initialInitializationCount = mutated.column.getInitializationCount();

    replaceFeedFluid(mutated, createIdentityTestFluid(false, "n-butane", 1));
    mutated.column.run();

    assertFalse(mutated.column.wasNaphtaliSandholmWarmStateReused(),
        "a different mixing rule must not reuse the previous products");
    assertTrue(mutated.column.getInitializationCount() > initialInitializationCount,
        "a different mixing rule must force column initialization");

    ColumnCase coldReference = buildIdentityColumnCase(createIdentityTestFluid(false, "n-butane", 1));
    coldReference.column.run();
    assertColdReferenceEquivalent(coldReference.column, mutated.column);
    assertPhysicalAndBalanced(mutated);
    assertNextRunReusesExactly(mutated);
  }

  /**
   * Cold initialization must preserve caller-owned feeds when direct and registered feeds were attached in reverse
   * order.
   */
  @Test
  public void coldInitializationPreservesReverseAttachedFeedIdentity() {
    Stream directFeed = new Stream("reverse-order direct feed", createIdentityTestFluid(false, "n-butane", 2));
    configureDirectFeed(directFeed);
    Stream registeredFeed = new Stream("reverse-order registered feed", createIdentityTestFluid(false, "n-butane", 2));
    configureIdentityFeed(registeredFeed);
    SystemInterface directFeedSystem = directFeed.getThermoSystem();
    SystemInterface registeredFeedSystem = registeredFeed.getThermoSystem();

    TrackingDistillationColumn column = new TrackingDistillationColumn("reverse-order feed column", 6, true, false);
    column.getTray(2).addStream(directFeed);
    column.addFeedStream(registeredFeed, 2);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.5);
    column.getReboiler().setOutTemperature(273.15 + 80.0);
    configureDampedSubstitution(column);

    column.run();

    assertTrue(column.solved(), column.getConvergenceDiagnostics());
    assertSame(directFeedSystem, directFeed.getThermoSystem(),
        "cold preparation must not replace the caller-owned direct-feed system");
    assertSame(registeredFeedSystem, registeredFeed.getThermoSystem(),
        "cold preparation must not replace the caller-owned registered-feed system");
    assertEquals(30.0, directFeed.getTemperature("C"), 1.0e-9,
        "cold preparation must preserve the direct-feed temperature");
    assertEquals(20.0, registeredFeed.getTemperature("C"), 1.0e-9,
        "cold preparation must preserve the registered-feed temperature");

    double totalFeedFlow = directFeed.getFlowRate("mol/hr") + registeredFeed.getFlowRate("mol/hr");
    double totalProductFlow = column.getGasOutStream().getFlowRate("mol/hr")
        + column.getLiquidOutStream().getFlowRate("mol/hr");
    assertEquals(totalFeedFlow, totalProductFlow, 5.0e-3 * totalFeedFlow,
        "reverse-order registered and direct feeds must close the total molar balance");
    assertPhysicalStream(column.getGasOutStream());
    assertPhysicalStream(column.getLiquidOutStream());
  }

  /**
   * A direct-feed-only sequential column must scale its divergence guard from the physical feed flow.
   *
   * <p>
   * Legacy direct tray feeds are valid external feeds. Ignoring them when setting the internal-traffic threshold leaves
   * the threshold at the 1000 kg/h floor and rejects this otherwise ordinary multicomponent stripper before it can
   * converge.
   * </p>
   */
  @Test
  public void directOnlySequentialColumnUsesExternalFeedScaleForDivergenceGuard() {
    Stream directFeed = new Stream("direct-only feed", createIdentityTestFluid(false, "n-butane", 2));
    configureIdentityFeed(directFeed);

    TrackingDistillationColumn column = new TrackingDistillationColumn("direct-only sequential column", 6, true, false);
    column.getTray(3).addStream(directFeed);
    column.setTopPressure(10.0);
    column.setBottomPressure(10.5);
    column.getReboiler().setOutTemperature(273.15 + 80.0);
    configureDampedSubstitution(column);

    column.run();

    assertTrue(column.solved(), column.getConvergenceDiagnostics());
    assertTrue(column.getLastIterationCount() <= 120,
        "the direct-feed-only solve must remain inside the deterministic hard cap");
    assertEquals(20.0, directFeed.getTemperature("C"), 1.0e-9,
        "direct-feed handling must preserve the caller-owned feed temperature");
    assertPhysicalAndBalanced(new ColumnCase(directFeed, column));
  }

  /**
   * A legacy direct tray feed participates in the same thermodynamic-identity gate as registered feeds.
   */
  @Test
  public void directTrayFeedIdentityChangeForcesColdInitialization() {
    DirectFeedColumnCase mutated = buildDirectFeedColumnCase(createIdentityTestFluid(false, "n-butane", 2));
    mutated.column.run();
    assertTrue(mutated.column.solved(), mutated.column.getConvergenceDiagnostics());
    int initialInitializationCount = mutated.column.getInitializationCount();

    replaceDirectFeedFluid(mutated, createIdentityTestFluid(false, "i-butane", 2));
    mutated.column.run();

    assertTrue(mutated.column.solved(), mutated.column.getConvergenceDiagnostics());
    assertEquals(initialInitializationCount + 1, mutated.column.getInitializationCount(),
        "a direct side-feed identity change must rebuild the sequential initialization exactly once");
    assertEquals(20.0, mutated.registeredFeed.getTemperature("C"), 1.0e-9,
        "direct-feed handling must not mutate the registered caller-owned feed");
    assertEquals(30.0, mutated.directFeed.getTemperature("C"), 1.0e-9,
        "direct-feed handling must not mutate the direct caller-owned feed");

    DirectFeedColumnCase coldReference = buildDirectFeedColumnCase(createIdentityTestFluid(false, "i-butane", 2));
    coldReference.column.run();
    assertTrue(coldReference.column.solved(), coldReference.column.getConvergenceDiagnostics());
    assertColdReferenceEquivalent(coldReference.column, mutated.column);
    assertPhysicalStream(mutated.column.getGasOutStream());
    assertPhysicalStream(mutated.column.getLiquidOutStream());
  }

  /**
   * A direct side-feed operating-point change must invalidate exact Naphtali-Sandholm reuse.
   */
  @Test
  public void directTrayFeedOperatingPointChangeInvalidatesNaphtaliReuse() {
    DirectFeedColumnCase mutated = buildDirectFeedColumnCase(createIdentityTestFluid(false, "n-butane", 2));
    mutated.column.setSolverType(DistillationColumn.SolverType.NAPHTALI_SANDHOLM);
    mutated.column.run();
    assertTrue(mutated.column.solved(), mutated.column.getConvergenceDiagnostics());

    mutated.column.run();
    assertTrue(mutated.column.wasNaphtaliSandholmWarmStateReused(),
        "an unchanged direct-feed case must reuse the accepted simultaneous solution");
    double originalOverheadFlow = mutated.column.getGasOutStream().getFlowRate("mol/hr");

    mutated.directFeed.setFlowRate(0.22 * IDENTITY_TEST_FLOW_MOL_PER_HOUR, "mol/hr");
    mutated.directFeed.run();

    assertFalse(mutated.column.willReuseNaphtaliSandholmWarmState(),
        "a direct side-feed flow change must invalidate exact simultaneous-solver reuse");
    mutated.column.run();

    assertTrue(mutated.column.solved(), mutated.column.getConvergenceDiagnostics());
    assertFalse(mutated.column.wasNaphtaliSandholmWarmStateReused(),
        "the changed direct feed must be solved rather than answered from the old cache");
    assertNotEquals(originalOverheadFlow, mutated.column.getGasOutStream().getFlowRate("mol/hr"), 1.0,
        "a 10 percent direct-feed flow change must alter the product flow");
    assertEquals(20.0, mutated.registeredFeed.getTemperature("C"), 1.0e-9,
        "direct-feed cache checks must not mutate the registered caller-owned feed");
    assertEquals(30.0, mutated.directFeed.getTemperature("C"), 1.0e-9,
        "direct-feed cache checks must not mutate the direct caller-owned feed");
    assertPhysicalStream(mutated.column.getGasOutStream());
    assertPhysicalStream(mutated.column.getLiquidOutStream());
  }

  /**
   * An accepted damped candidate must retain ownership of its exact sequential reuse fingerprint.
   */
  @Test
  public void acceptedSequentialCandidateRetainsExactReuseState() {
    ColumnCase live = buildIdentityColumnCase(createIdentityTestFluid(false, "n-butane", 2));
    configureDampedSubstitution(live.column);
    ColumnCase candidate = buildIdentityColumnCase(createIdentityTestFluid(false, "n-butane", 2));
    configureDampedSubstitution(candidate.column);

    candidate.column.run();

    assertTrue(candidate.column.solved(), candidate.column.getConvergenceDiagnostics());
    double acceptedGasFlow = candidate.column.getGasOutStream().getFlowRate("mol/hr");
    double acceptedLiquidFlow = candidate.column.getLiquidOutStream().getFlowRate("mol/hr");
    live.column.acceptDampedFallbackCandidate(candidate.column, "accepted sequential candidate");

    live.column.run();

    assertTrue(live.column.solved(), live.column.getConvergenceDiagnostics());
    assertTrue(live.column.wasSequentialWarmStateReused(),
        "an adopted accepted sequential candidate must remain exactly reusable");
    assertEquals(0, live.column.getLastIterationCount(),
        "exact reuse of an adopted candidate must require no new tray iterations");
    assertEquals(acceptedGasFlow, live.column.getGasOutStream().getFlowRate("mol/hr"),
        Math.max(1.0e-9, Math.abs(acceptedGasFlow) * 1.0e-5), "adopted exact reuse must preserve overhead flow");
    assertEquals(acceptedLiquidFlow, live.column.getLiquidOutStream().getFlowRate("mol/hr"),
        Math.max(1.0e-9, Math.abs(acceptedLiquidFlow) * 1.0e-5), "adopted exact reuse must preserve bottoms flow");
    assertEquals(20.0, live.feed.getTemperature("C"), 1.0e-9,
        "candidate adoption and exact reuse must preserve the caller-owned feed");
    assertPhysicalAndBalanced(live);
  }

  /**
   * An adjustable product specification must not suppress invalidation of other fixed column inputs.
   *
   * <p>
   * The bottom recovery target manipulates the reboiler temperature internally. A nearby pressure change is independent
   * of that manipulated degree of freedom and must rebuild the sequential tray initialization before solving the same
   * recovery target. The rebuilt solution must agree with a fresh column at the changed pressure.
   * </p>
   */
  @Test
  public void adjustableSpecificationStillInvalidatesFixedPressureChange() {
    ColumnCase calibration = buildIdentityColumnCase(createIdentityTestFluid(false, "n-butane", 2));
    configureDampedSubstitution(calibration.column);
    calibration.column.setTopPressure(9.0);
    calibration.column.setBottomPressure(9.5);
    calibration.column.run();
    assertTrue(calibration.column.solved(), calibration.column.getConvergenceDiagnostics());
    double targetRecovery = getBottomComponentRecovery(calibration, "n-pentane");
    assertTrue(targetRecovery > 0.0 && targetRecovery < 1.0, "calibrated recovery must be physical");

    ColumnCase warmCase = buildIdentityColumnCase(createIdentityTestFluid(false, "n-butane", 2));
    configureDampedSubstitution(warmCase.column);
    warmCase.column.run();
    assertTrue(warmCase.column.solved(), warmCase.column.getConvergenceDiagnostics());
    assertNotEquals(targetRecovery, getBottomComponentRecovery(warmCase, "n-pentane"), 1.0e-3,
        "the nearby pressure point must exercise a distinct recovery state");
    int baselineInitializationCount = warmCase.column.getInitializationCount();

    configureBottomPentaneRecoverySpecification(warmCase.column, targetRecovery);
    warmCase.column.setTopPressure(9.0);
    warmCase.column.setBottomPressure(9.5);
    warmCase.column.run();

    assertTrue(warmCase.column.solved(), warmCase.column.getConvergenceDiagnostics());
    assertEquals(baselineInitializationCount + 1, warmCase.column.getInitializationCount(),
        "a fixed pressure change must rebuild even while bottom recovery adjusts reboiler temperature");
    assertEquals(0.0, warmCase.column.getLastBottomSpecificationResidual(),
        warmCase.column.getBottomSpecification().getTolerance(),
        "changed-pressure recovery specification must converge");
    assertPhysicalAndBalanced(warmCase);

    ColumnCase coldReference = buildIdentityColumnCase(createIdentityTestFluid(false, "n-butane", 2));
    configureDampedSubstitution(coldReference.column);
    configureBottomPentaneRecoverySpecification(coldReference.column, targetRecovery);
    coldReference.column.setTopPressure(9.0);
    coldReference.column.setBottomPressure(9.5);
    coldReference.column.run();

    assertTrue(coldReference.column.solved(), coldReference.column.getConvergenceDiagnostics());
    assertEquals(0.0, coldReference.column.getLastBottomSpecificationResidual(),
        coldReference.column.getBottomSpecification().getTolerance(),
        "cold-reference recovery specification must converge");
    assertColdReferenceEquivalent(coldReference.column, warmCase.column);
    assertPhysicalAndBalanced(coldReference);
  }

  /**
   * Calculate component recovery in the bottom product.
   *
   * @param columnCase solved column case
   * @param componentName component to evaluate
   * @return bottom recovery fraction
   */
  private static double getBottomComponentRecovery(ColumnCase columnCase, String componentName) {
    double feedComponentFlow = columnCase.feed.getFlowRate("mol/hr")
        * columnCase.feed.getThermoSystem().getComponent(componentName).getz();
    double bottomComponentFlow = columnCase.column.getLiquidOutStream().getFlowRate("mol/hr")
        * columnCase.column.getLiquidOutStream().getThermoSystem().getComponent(componentName).getz();
    return bottomComponentFlow / feedComponentFlow;
  }

  /**
   * Configure an adjustable n-pentane bottom-recovery specification.
   *
   * @param column column to configure
   * @param targetRecovery target bottom recovery fraction
   */
  private static void configureBottomPentaneRecoverySpecification(DistillationColumn column, double targetRecovery) {
    column.setBottomComponentRecovery("n-pentane", targetRecovery);
    column.getBottomSpecification().setTolerance(5.0e-3);
    column.getBottomSpecification().setMaxIterations(15);
  }

  /**
   * Sequential warm starts must be invalidated when a fixed reboiler temperature changes.
   *
   * <p>
   * The 90 C case previously converged to a path-dependent product split, while the 100 C case reached the internal
   * traffic guard. Both targets are feasible from a cold initialization. After the configuration change is detected,
   * the sequential solver must rebuild once, agree with the cold reference, and retain the new state on an unchanged
   * re-run.
   * </p>
   */
  @Test
  public void sequentialSpecificationChangeMatchesColdReferenceAndIsRepeatable() {
    assertSequentialSpecificationChange(90.0);
    assertSequentialSpecificationChange(100.0);
  }

  /**
   * Verify one changed fixed-temperature target against a cold reference and an unchanged re-run.
   *
   * @param targetTemperatureC changed reboiler temperature in degrees Celsius
   */
  private static void assertSequentialSpecificationChange(double targetTemperatureC) {
    ColumnCase warmCase = buildIdentityColumnCase(createIdentityTestFluid(false, "n-butane", 2));
    configureDampedSubstitution(warmCase.column);
    double inletTemperature = warmCase.feed.getTemperature("K");
    warmCase.column.run();
    assertTrue(warmCase.column.solved(), warmCase.column.getConvergenceDiagnostics());
    assertEquals(inletTemperature, warmCase.feed.getTemperature("K"), 1.0e-9,
        "column initialization must not change the caller-owned feed temperature");
    int baselineInitializationCount = warmCase.column.getInitializationCount();

    warmCase.column.getReboiler().setOutTemperature(273.15 + targetTemperatureC);
    warmCase.column.run();

    assertTrue(warmCase.column.solved(), warmCase.column.getConvergenceDiagnostics());
    assertEquals(inletTemperature, warmCase.feed.getTemperature("K"), 1.0e-9,
        "column reinitialization must not change the caller-owned feed temperature");
    assertEquals(baselineInitializationCount + 1, warmCase.column.getInitializationCount(),
        "a changed fixed reboiler temperature must rebuild the sequential initialization exactly once");
    assertTrue(warmCase.column.getLastIterationCount() <= 30,
        "the rebuilt solve should remain within the established cold-reference iteration budget");
    assertPhysicalAndBalanced(warmCase);

    ColumnCase coldReference = buildIdentityColumnCase(createIdentityTestFluid(false, "n-butane", 2));
    configureDampedSubstitution(coldReference.column);
    coldReference.column.getReboiler().setOutTemperature(273.15 + targetTemperatureC);
    coldReference.column.run();

    assertTrue(coldReference.column.solved(), coldReference.column.getConvergenceDiagnostics());
    assertColdReferenceEquivalent(coldReference.column, warmCase.column);
    assertPhysicalAndBalanced(coldReference);

    int acceptedInitializationCount = warmCase.column.getInitializationCount();
    double gasFlow = warmCase.column.getGasOutStream().getFlowRate("mol/hr");
    double liquidFlow = warmCase.column.getLiquidOutStream().getFlowRate("mol/hr");
    double[] gasComposition = warmCase.column.getGasOutStream().getThermoSystem().getMolarComposition().clone();
    double[] liquidComposition = warmCase.column.getLiquidOutStream().getThermoSystem().getMolarComposition().clone();

    warmCase.column.run();

    assertTrue(warmCase.column.solved(), warmCase.column.getConvergenceDiagnostics());
    assertTrue(warmCase.column.wasSequentialWarmStateReused(),
        "an unchanged accepted sequential case must use exact input reuse");
    assertEquals(0, warmCase.column.getLastIterationCount(),
        "exact sequential reuse must require no new tray iterations");
    assertEquals(acceptedInitializationCount, warmCase.column.getInitializationCount(),
        "an unchanged re-run must retain the accepted sequential warm state");
    assertEquals(gasFlow, warmCase.column.getGasOutStream().getFlowRate("mol/hr"),
        Math.max(1.0e-9, Math.abs(gasFlow) * 1.0e-5), "an unchanged re-run must reproduce overhead flow");
    assertEquals(liquidFlow, warmCase.column.getLiquidOutStream().getFlowRate("mol/hr"),
        Math.max(1.0e-9, Math.abs(liquidFlow) * 1.0e-5), "an unchanged re-run must reproduce bottoms flow");
    assertArrayEquals(gasComposition, warmCase.column.getGasOutStream().getThermoSystem().getMolarComposition(), 1.0e-7,
        "an unchanged re-run must reproduce overhead composition");
    assertArrayEquals(liquidComposition, warmCase.column.getLiquidOutStream().getThermoSystem().getMolarComposition(),
        1.0e-7, "an unchanged re-run must reproduce bottoms composition");
    assertPhysicalAndBalanced(warmCase);
  }

  /**
   * Configure the deterministic low-relaxation sequential regression solver.
   *
   * @param column column to configure
   */
  private static void configureDampedSubstitution(DistillationColumn column) {
    column.setSolverType(DistillationColumn.SolverType.DAMPED_SUBSTITUTION);
    column.setRelaxationFactor(0.2);
    column.setMaxNumberOfIterations(120, true);
  }

}
