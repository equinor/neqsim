package neqsim.process.equipment.distillation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    StreamInterface gas = columnCase.column.getGasOutStream();
    StreamInterface liquid = columnCase.column.getLiquidOutStream();
    String[] feedComponents = columnCase.feed.getThermoSystem().getComponentNames();
    assertArrayEquals(feedComponents, gas.getThermoSystem().getComponentNames(),
        "overhead must use the current feed component identities");
    assertArrayEquals(feedComponents, liquid.getThermoSystem().getComponentNames(),
        "bottoms must use the current feed component identities");

    double feedFlow = columnCase.feed.getFlowRate("mol/hr");
    double gasFlow = gas.getFlowRate("mol/hr");
    double liquidFlow = liquid.getFlowRate("mol/hr");
    assertTrue(Double.isFinite(gasFlow) && gasFlow > 0.0, "overhead flow must be finite and positive");
    assertTrue(Double.isFinite(liquidFlow) && liquidFlow > 0.0, "bottoms flow must be finite and positive");
    assertEquals(feedFlow, gasFlow + liquidFlow, 5.0e-3 * feedFlow, "total product flow must close the feed");

    double[] feedComposition = columnCase.feed.getThermoSystem().getMolarComposition();
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
   * Verify physical temperature, pressure, and composition bounds.
   *
   * @param stream product stream
   */
  private static void assertPhysicalStream(StreamInterface stream) {
    assertTrue(Double.isFinite(stream.getTemperature("K")) && stream.getTemperature("K") > 150.0
        && stream.getTemperature("K") < 800.0, "product temperature must be physical");
    assertTrue(Double.isFinite(stream.getPressure("bara")) && stream.getPressure("bara") > 0.0,
        "product pressure must be physical");
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
