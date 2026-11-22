package neqsim.process.equipment.separator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.separator.SeparatorTransientEnergyBalanceReport.CoverageIssue;
import neqsim.process.equipment.separator.SeparatorTransientEnergyBalanceReport.EnergyVerdict;
import neqsim.process.equipment.separator.SeparatorTransientEnergyBalanceReport.TrackingStatus;
import neqsim.process.equipment.separator.entrainment.SeparatorPerformanceCalculator;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Independent balance, lifecycle, and deterministic replay checks for opt-in separator energy accounting.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class SeparatorTransientEnergyBalanceTest {
  /** Default-disabled and unsupported algorithms must not expose phantom measured energy. */
  @Test
  void availabilityIsExplicitAndUnsupportedAlgorithmsCannotEnableTracking() {
    Separator plain = new Separator("unwired");
    assertFalse(plain.isTransientEnergyBalanceTrackingEnabled());
    assertNull(plain.getLastTransientEnergyBalanceReport());
    assertEquals(TrackingStatus.DISABLED, plain.getTransientEnergyBalanceTrackingStatus());
    assertEquals(EnergyVerdict.DISABLED, plain.assessTransientEnergyBalance(1.0e-5));
    plain.setTransientEnergyBalanceTrackingEnabled(true);
    assertEquals(TrackingStatus.NOT_STARTED, plain.getTransientEnergyBalanceTrackingStatus());
    assertEquals(EnergyVerdict.NOT_STARTED, plain.assessTransientEnergyBalance(1.0e-5));
    assertNull(plain.getLastTransientEnergyBalanceReport());
    ThreePhaseSeparator threePhase = new ThreePhaseSeparator("three-phase");
    assertEquals(TrackingStatus.UNSUPPORTED, threePhase.getTransientEnergyBalanceTrackingStatus());
    assertThrows(UnsupportedOperationException.class, () -> threePhase.setTransientEnergyBalanceTrackingEnabled(true));
    assertEquals(EnergyVerdict.UNSUPPORTED, threePhase.assessTransientEnergyBalance(1.0e-5));
    assertNull(threePhase.getLastTransientEnergyBalanceReport());
  }

  /** Closed adiabatic energy is independently compared with the actual final inventory change. */
  @Test
  void isolatedAdiabaticVesselUsesExactZeroBoundaryEnergy() {
    Fixture f = fixture(true);
    closeAllBoundaries(f);
    f.separator.setTransientEnergyBalanceTrackingEnabled(true);
    double initial = f.separator.getThermoSystem().getInternalEnergy();
    for (int i = 0; i < 5; i++) {
      f.separator.runTransient(0.1, id(i));
    }
    SeparatorTransientEnergyBalanceReport r = f.separator.getLastTransientEnergyBalanceReport();
    assertTrue(r.isComplete());
    assertEquals(5L, r.getAcceptedStepCount());
    assertEquals(0.5, r.getElapsedTimeSeconds(), 1.0e-15);
    assertEquals(0.0, r.getInletEnergyJ());
    assertEquals(0.0, r.getGasOutletEnergyJ());
    assertEquals(0.0, r.getLiquidOutletEnergyJ());
    assertEquals(0.0, r.getHeatInputEnergyJ());
    assertEquals(0.0, r.getNetIntegratedEnergyJ());
    assertEquals(initial, r.getInitialEnergyJ());
    assertEquals(f.separator.getThermoSystem().getInternalEnergy(), r.getPublishedEnergyJ());
    assertEquals(r.getPublishedEnergyJ() - initial, r.getCumulativeDebtJ(), 1.0e-8);
    assertEquals(r.getAcceptedVuEnergyJ() - r.getTargetEnergyJ(), r.getLocalVuDefectJ());
  }

  /** A known signed heat rate is integrated once in time without introducing a separate latent-heat source. */
  @Test
  void closedHeatingAndCoolingMatchAnIndependentRateIntegral() {
    for (double heat : new double[] {1000.0, -1000.0}) {
      Fixture f = fixture(true);
      closeAllBoundaries(f);
      f.separator.setHeatInput(heat);
      f.separator.setTransientEnergyBalanceTrackingEnabled(true);
      double initial = f.separator.getThermoSystem().getInternalEnergy();
      for (int i = 0; i < 4; i++) {
        f.separator.runTransient(0.25, id(i));
      }
      SeparatorTransientEnergyBalanceReport r = f.separator.getLastTransientEnergyBalanceReport();
      assertTrue(r.isComplete());
      assertEquals(heat, r.getHeatInputEnergyJ());
      assertEquals(heat, r.getNetIntegratedEnergyJ());
      assertEquals(r.getBeforeEnergyJ() + 0.25 * heat, r.getTargetEnergyJ());
      double independentDebt = f.separator.getThermoSystem().getInternalEnergy() - initial - heat;
      assertEquals(independentDebt, r.getCumulativeDebtJ(), 1.0e-7);
    }
  }

  /** Actual pre-update liquid withdrawal is retained even though outlet properties are refreshed later. */
  @Test
  void liquidOutflowUsesTheConsumedBoundaryEnthalpy() {
    Fixture f = fixture(true);
    f.feed.setFlowRate(0.0, "kg/sec");
    f.separator.getGasOutStream().setFlowRate(0.0, "kg/sec");
    f.separator.getLiquidOutStream().getFluid().init(2);
    double liquid = f.separator.getLiquidOutStream().getFluid().getEnthalpy();
    double initial = f.separator.getThermoSystem().getInternalEnergy();
    f.separator.setTransientEnergyBalanceTrackingEnabled(true);
    f.separator.runTransient(0.1, id(1));
    SeparatorTransientEnergyBalanceReport r = f.separator.getLastTransientEnergyBalanceReport();
    assertTrue(r.isLiquidOutletTermApplied());
    assertEquals(liquid, r.getLiquidOutletEnthalpyRateW(), 1.0e-7);
    assertEquals(-0.1 * liquid, r.getNetIntegratedEnergyJ(), 1.0e-7);
    assertEquals(r.getPublishedEnergyJ() - initial + 0.1 * liquid, r.getCumulativeDebtJ(), 1.0e-7);
  }

  /** Reading and recording diagnostics cannot change the actual physical trajectory. */
  @Test
  void enabledAndDisabledTrajectoriesAreExactlyEqual() {
    Fixture off = fixture(true);
    Fixture on = fixture(true);
    on.separator.setTransientEnergyBalanceTrackingEnabled(true);
    for (int i = 0; i < 8; i++) {
      off.separator.runTransient(0.1, id(i));
      on.separator.runTransient(0.1, id(i));
      assertPhysicalStateEquals(off.separator, on.separator);
    }
    assertNull(off.separator.getLastTransientEnergyBalanceReport());
    assertNotNull(on.separator.getLastTransientEnergyBalanceReport());
  }

  /** Public snapshots restore the ledger and immutable report along with the owned physical state. */
  @Test
  void snapshotReplayAndSerializationAreDeterministic() throws Exception {
    Fixture f = fixture(true);
    f.separator.setTransientEnergyBalanceTrackingEnabled(true);
    f.separator.runTransient(0.1, id(1));
    Separator.TransientState checkpoint = f.separator.captureTransientState();
    Stream.TransientState feedCheckpoint = f.feed.captureTransientState();
    SeparatorTransientEnergyBalanceReport original = f.separator.getLastTransientEnergyBalanceReport();
    f.separator.runTransient(0.1, id(2));
    byte[] expectedReport = serialize(f.separator.getLastTransientEnergyBalanceReport());
    double expectedU = f.separator.getThermoSystem().getInternalEnergy();
    f.feed.restoreTransientState(feedCheckpoint);
    f.separator.restoreTransientState(checkpoint);
    assertSame(original, f.separator.getLastTransientEnergyBalanceReport());
    f.separator.runTransient(0.1, id(2));
    assertEquals(expectedU, f.separator.getThermoSystem().getInternalEnergy());
    assertArrayEquals(expectedReport, serialize(f.separator.getLastTransientEnergyBalanceReport()));
    Separator copy = (Separator) deserialize(serialize(f.separator));
    assertArrayEquals(expectedReport, serialize(copy.getLastTransientEnergyBalanceReport()));
    copy.resetTransientEnergyBalanceTracking();
    assertNull(copy.getLastTransientEnergyBalanceReport());
    assertArrayEquals(expectedReport, serialize(f.separator.getLastTransientEnergyBalanceReport()));
  }

  /** A later skipped update refreshes current coverage without changing a previously returned immutable report. */
  @Test
  void skippedStepCannotRetainACompleteCurrentReport() {
    Fixture f = fixture(true);
    closeAllBoundaries(f);
    f.separator.setTransientEnergyBalanceTrackingEnabled(true);
    f.separator.runTransient(0.1, id(1));
    SeparatorTransientEnergyBalanceReport previous = f.separator.getLastTransientEnergyBalanceReport();
    f.separator.getThermoSystem().setTotalNumberOfMoles(0.0);
    f.separator.runTransient(0.1, id(2));
    SeparatorTransientEnergyBalanceReport current = f.separator.getLastTransientEnergyBalanceReport();
    assertTrue(previous.isComplete());
    assertFalse(current.isComplete());
    assertEquals(previous.getPublishedEnergyJ(), current.getPublishedEnergyJ());
    assertEquals(1L, current.getAcceptedStepCount());
    assertEquals(2L, current.getAttemptedStepCount());
    assertEquals(1L, current.getSkippedStepCount());
    assertEquals(TrackingStatus.INCOMPLETE, f.separator.getTransientEnergyBalanceTrackingStatus());
    assertEquals(EnergyVerdict.INCOMPLETE, f.separator.assessTransientEnergyBalance(Double.MAX_VALUE));
  }

  /** Supplied liquid flow excluded by the native phase condition is not silently qualified as all-port closure. */
  @Test
  void absentLiquidWithStaleWithdrawalMarksCoverageIncomplete() {
    Fixture f = fixture(false);
    closeAllBoundaries(f);
    f.separator.getLiquidOutStream().setFlowRate(1.0, "kg/hr");
    f.separator.setTransientEnergyBalanceTrackingEnabled(true);
    f.separator.runTransient(0.1, id(1));
    SeparatorTransientEnergyBalanceReport r = f.separator.getLastTransientEnergyBalanceReport();
    assertNotNull(r);
    assertFalse(r.isLiquidOutletTermApplied());
    assertTrue(r.getLiquidOutletMolarRateMolPerSecond() > 0.0);
    assertEquals(0.0, r.getLiquidOutletEnthalpyRateW());
    assertEquals(CoverageIssue.EXCLUDED_NONZERO_LIQUID_OUTLET, r.getCoverageIssue());
    assertEquals(EnergyVerdict.INCOMPLETE, r.assess(1.0));
  }

  /** Steady reevaluation keeps the prior numerical evidence but explicitly breaks dynamic-window coverage. */
  @Test
  void steadyReevaluationCannotPassAnUnmeasuredDynamicWindow() {
    Fixture f = fixture(true);
    f.separator.setTransientEnergyBalanceTrackingEnabled(true);
    f.separator.runTransient(0.1, id(1));
    double prior = f.separator.getLastTransientEnergyBalanceReport().getPublishedEnergyJ();
    f.separator.setCalculateSteadyState(true);
    f.separator.runTransient(0.2, id(2));
    SeparatorTransientEnergyBalanceReport r = f.separator.getLastTransientEnergyBalanceReport();
    assertFalse(r.isComplete());
    assertEquals(prior, r.getPublishedEnergyJ());
    assertEquals(CoverageIssue.STEADY_STATE_REEVALUATION, r.getCoverageIssue());
    assertEquals(2L, r.getAttemptedStepCount());
    assertEquals(1L, r.getSkippedStepCount());
    assertEquals(EnergyVerdict.INCOMPLETE, f.separator.assessTransientEnergyBalance(Double.MAX_VALUE));
    f.separator.setCalculateSteadyState(false);
    f.separator.run(id(3));
    assertEquals(EnergyVerdict.INCOMPLETE, f.separator.assessTransientEnergyBalance(Double.MAX_VALUE));
  }

  /** Explicit reset, explicit inventory initialization and disable/reenable establish separate visible epochs. */
  @Test
  void explicitLifecycleChangesRebaseWithoutStaleEnergy() {
    Fixture f = fixture(true);
    f.separator.setTransientEnergyBalanceTrackingEnabled(true);
    f.separator.runTransient(0.1, id(1));
    long first = f.separator.getLastTransientEnergyBalanceReport().getEpoch();
    f.separator.setTransientEnergyBalanceTrackingEnabled(true);
    assertEquals(first, f.separator.getLastTransientEnergyBalanceReport().getEpoch());
    f.separator.resetTransientEnergyBalanceTracking();
    assertNull(f.separator.getLastTransientEnergyBalanceReport());
    f.separator.runTransient(0.1, id(2));
    assertTrue(f.separator.getLastTransientEnergyBalanceReport().getEpoch() > first);
    f.separator.initializeTransientCalculation();
    assertEquals(TrackingStatus.NOT_STARTED, f.separator.getTransientEnergyBalanceTrackingStatus());
    assertNull(f.separator.getLastTransientEnergyBalanceReport());
    f.separator.setTransientEnergyBalanceTrackingEnabled(false);
    assertEquals(EnergyVerdict.DISABLED, f.separator.assessTransientEnergyBalance(1.0e-5));
    f.separator.setTransientEnergyBalanceTrackingEnabled(true);
    assertEquals(EnergyVerdict.NOT_STARTED, f.separator.assessTransientEnergyBalance(1.0e-5));
  }

  /** Invalid intervals and caller policies are rejected before any tracked physical mutation. */
  @Test
  void invalidIntervalsAndPoliciesDoNotMutateState() {
    Fixture f = fixture(true);
    f.separator.setTransientEnergyBalanceTrackingEnabled(true);
    double initial = f.separator.getThermoSystem().getInternalEnergy();
    for (double dt : new double[] {0.0, -0.1, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> f.separator.runTransient(dt, id(1)));
    }
    for (double limit : new double[] {-1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> f.separator.assessTransientEnergyBalance(limit));
    }
    assertEquals(initial, f.separator.getThermoSystem().getInternalEnergy());
    assertNull(f.separator.getLastTransientEnergyBalanceReport());
    assertEquals(TrackingStatus.NOT_STARTED, f.separator.getTransientEnergyBalanceTrackingStatus());
  }

  /** A genuine postflash production-extension failure rolls back both energy accounting and physical inventory. */
  @Test
  void failureAfterAcceptedVuRollsBackTheEntireLedger() throws Exception {
    Fixture f = fixture(true);
    f.separator.setTransientEnergyBalanceTrackingEnabled(true);
    f.separator.runTransient(0.1, id(1));
    byte[] report = serialize(f.separator.getLastTransientEnergyBalanceReport());
    double energy = f.separator.getThermoSystem().getInternalEnergy();
    double mass = f.separator.getThermoSystem().getMass("kg");
    f.separator.setPerformanceCalculator(new FailingPerformanceCalculator());
    f.separator.setDetailedEntrainmentCalculation(true);
    assertThrows(IllegalStateException.class, () -> f.separator.runTransient(0.1, id(2)));
    assertEquals(energy, f.separator.getThermoSystem().getInternalEnergy());
    assertEquals(mass, f.separator.getThermoSystem().getMass("kg"));
    assertArrayEquals(report, serialize(f.separator.getLastTransientEnergyBalanceReport()));
  }

  /** A genuinely rejected native VU solve cannot accrue attempted energy, maxima, or elapsed time. */
  @Test
  void rejectedVuSolveRestoresThePriorReportAndInventory() throws Exception {
    Fixture f = fixture(true);
    closeAllBoundaries(f);
    f.separator.setTransientEnergyBalanceTrackingEnabled(true);
    f.separator.runTransient(0.1, id(1));
    byte[] report = serialize(f.separator.getLastTransientEnergyBalanceReport());
    double energy = f.separator.getThermoSystem().getInternalEnergy();
    double mass = f.separator.getThermoSystem().getMass("kg");
    f.separator.gasVolume = 1.0e-12;
    f.separator.liquidVolume = 0.0;
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> f.separator.runTransient(0.1, id(2)));
    assertTrue(failure.getMessage().contains("rejected transient VU state"));
    assertEquals(energy, f.separator.getThermoSystem().getInternalEnergy());
    assertEquals(mass, f.separator.getThermoSystem().getMass("kg"));
    assertArrayEquals(report, serialize(f.separator.getLastTransientEnergyBalanceReport()));
  }

  /** Native component clipping and subsequent depletion remain explicit missing coverage, not measured zero debt. */
  @Test
  void depletedInventoryCannotPublishACompleteEnergyWindow() {
    Fixture f = fixture(true);
    closeAllBoundaries(f);
    f.separator.setTransientEnergyBalanceTrackingEnabled(true);
    f.separator.runTransient(0.1, id(1));
    double previousEnergy = f.separator.getLastTransientEnergyBalanceReport().getPublishedEnergyJ();
    f.separator.getGasOutStream().setThermoSystem(f.separator.getThermoSystem().clone());
    f.separator.getGasOutStream().setFlowRate(1.0e10, "kg/hr");
    f.separator.runTransient(1.0, id(2));
    SeparatorTransientEnergyBalanceReport r = f.separator.getLastTransientEnergyBalanceReport();
    assertTrue(f.separator.getThermoSystem().getTotalNumberOfMoles() < 1.0e-10);
    assertFalse(r.isComplete());
    assertEquals(previousEnergy, r.getPublishedEnergyJ());
    assertEquals(1L, r.getSkippedStepCount());
    assertEquals(CoverageIssue.COMPONENT_REMOVAL_LIMITED, r.getCoverageIssue());
    assertEquals(EnergyVerdict.INCOMPLETE, f.separator.assessTransientEnergyBalance(1.0));
  }

  /** A caught native property initialization failure invalidates current coverage after a previous accepted update. */
  @Test
  void caughtPropertyInitializationSkipIsNotAnAcceptedEnergyUpdate() {
    Fixture f = fixture(true, new FailingInitializationSystem());
    closeAllBoundaries(f);
    f.separator.setTransientEnergyBalanceTrackingEnabled(true);
    f.separator.runTransient(0.1, id(1));
    ((FailingInitializationSystem) f.separator.getThermoSystem()).failSecondOrder = true;
    f.separator.runTransient(0.1, id(2));
    SeparatorTransientEnergyBalanceReport r = f.separator.getLastTransientEnergyBalanceReport();
    assertFalse(r.isComplete());
    assertEquals(1L, r.getAcceptedStepCount());
    assertEquals(1L, r.getSkippedStepCount());
    assertEquals(CoverageIssue.SKIPPED_PROPERTY_INITIALIZATION, r.getCoverageIssue());
  }

  /** An observation overflow cannot produce phantom finite balances or a passing criterion. */
  @Test
  void nonfiniteDiagnosticDoesNotPublishPhantomEnergy() {
    SeparatorTransientEnergyBalanceTracker t = new SeparatorTransientEnergyBalanceTracker(1L);
    t.beginAttempt(1.0, id(1), 0.0);
    t.recordEquation(0.0, 0.0, Double.POSITIVE_INFINITY, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, true);
    t.recordAcceptedVu(1.0, 0.0, 1.0);
    t.accept(0.0, 1.0, 0.0);
    assertNull(t.report());
    assertEquals(TrackingStatus.INCOMPLETE, t.status());
    assertEquals(CoverageIssue.NONFINITE_DIAGNOSTIC, t.coverageIssue);
  }

  /** Final cancellation must not erase a horizon failure, and the scale is explicit even near zero U. */
  @Test
  void horizonPolicyDetectsCancellationAndSeparatesLocalFromPublishedEnergy() {
    SeparatorTransientEnergyBalanceTracker t = new SeparatorTransientEnergyBalanceTracker(7L);
    syntheticStep(t, 100.0, 100.02, 100.02, 1);
    syntheticStep(t, 100.02, 100.0, 100.0, 2);
    SeparatorTransientEnergyBalanceReport r = t.report();
    assertEquals(0.0, r.getCumulativeDebtJ());
    assertTrue(r.getMaximumAbsoluteCumulativeDebtJ() > 0.019);
    assertTrue(r.getCumulativeAbsoluteLocalVuDefectJ() > 0.039);
    assertEquals(EnergyVerdict.FAIL, r.assess(1.0e-5));
    syntheticStep(t, 100.0, 100.0, 105.0, 3);
    assertEquals(0.0, t.report().getLocalVuDefectJ());
    assertEquals(100.0, t.report().getAcceptedVuEnergyJ());
    assertEquals(105.0, t.report().getPublishedEnergyJ());
    assertEquals(5.0, t.report().getNonVuEnergyDifferenceJ(), 1.0e-12);
    SeparatorTransientEnergyBalanceTracker zero = new SeparatorTransientEnergyBalanceTracker(1L);
    syntheticStep(zero, 0.0, 0.0, 0.0, 1);
    assertEquals(0.0, zero.report().getAbsoluteInitialEnergyJ());
    assertEquals(1.0, zero.report().getNormalizationScaleJ());
    assertEquals(EnergyVerdict.PASS, zero.report().assess(0.0));
    assertThrows(IllegalArgumentException.class, () -> r.assess(Double.NaN));
  }

  /**
   * Supplies an analytic zero-transfer accepted update to test the public immutable diagnostic contract.
   *
   * @param tracker independent scalar tracker
   * @param before pre-update inventory energy, J
   * @param vu returned VU energy, J
   * @param published final inventory energy, J
   * @param step deterministic calculation identity
   */
  private static void syntheticStep(SeparatorTransientEnergyBalanceTracker tracker, double before, double vu,
      double published, int step) {
    tracker.beginAttempt(1.0, id(step), 0.0);
    tracker.recordEquation(before, before, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, true);
    tracker.recordAcceptedVu(1.0, vu, 1.0);
    tracker.accept(published, 1.0, 0.0);
  }

  /**
   * Builds an independent native separator inventory with declared geometry and simple SRK composition.
   *
   * @param liquid true for a methane/heptane two-phase fixture, false for a gas-only mixture
   * @return fully initialized fixture
   */
  private static Fixture fixture(boolean liquid) {
    return fixture(liquid, new SystemSrkEos(300.0, 40.0));
  }

  /**
   * Builds the same native fixture with a test-selected thermodynamic implementation.
   *
   * @param liquid true to include heptane liquid
   * @param fluid initially empty thermodynamic system
   * @return initialized fixture
   */
  private static Fixture fixture(boolean liquid, SystemInterface fluid) {
    fluid.addComponent("methane", 0.9);
    fluid.addComponent(liquid ? "n-heptane" : "ethane", 0.1);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    feed.run(id(0));
    Separator separator = new Separator("vessel", feed);
    separator.setInternalDiameter(1.0);
    separator.setSeparatorLength(3.0);
    separator.setLiquidLevel(0.5);
    separator.run(id(0));
    separator.setCalculateSteadyState(false);
    separator.initializeTransientCalculation();
    assertEquals(liquid, separator.getThermoSystem().hasPhaseType("oil"));
    return new Fixture(feed, separator);
  }

  /**
   * Sets actual boundary molar flows to exact zero; retained cached properties must not carry energy.
   *
   * @param fixture native inventory fixture
   */
  private static void closeAllBoundaries(Fixture fixture) {
    fixture.feed.setFlowRate(0.0, "kg/sec");
    fixture.separator.getGasOutStream().setFlowRate(0.0, "kg/sec");
    fixture.separator.getLiquidOutStream().setFlowRate(0.0, "kg/sec");
  }

  /**
   * Compares actual inventory and outlet trajectories without reading the diagnostic as an oracle.
   *
   * @param expected reference separator
   * @param actual instrumented separator
   */
  private static void assertPhysicalStateEquals(Separator expected, Separator actual) {
    assertEquals(expected.getThermoSystem().getPressure(), actual.getThermoSystem().getPressure());
    assertEquals(expected.getThermoSystem().getTemperature(), actual.getThermoSystem().getTemperature());
    assertEquals(expected.getThermoSystem().getInternalEnergy(), actual.getThermoSystem().getInternalEnergy());
    assertEquals(expected.getThermoSystem().getMass("kg"), actual.getThermoSystem().getMass("kg"));
    assertEquals(expected.getLiquidLevel(), actual.getLiquidLevel());
    assertEquals(expected.getGasOutStream().getFlowRate("kg/sec"), actual.getGasOutStream().getFlowRate("kg/sec"));
    assertEquals(expected.getLiquidOutStream().getFlowRate("kg/sec"),
        actual.getLiquidOutStream().getFlowRate("kg/sec"));
  }

  /**
   * Serializes a native snapshot or immutable report using an independent object stream.
   *
   * @param value serializable object
   * @return serialized bytes
   * @throws Exception if serialization fails
   */
  private static byte[] serialize(Object value) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream stream = new ObjectOutputStream(bytes)) {
      stream.writeObject(value);
    }
    return bytes.toByteArray();
  }

  /**
   * Restores a serialized native copy.
   *
   * @param bytes serialized object bytes
   * @return independent restored object
   * @throws Exception if restoration fails
   */
  private static Object deserialize(byte[] bytes) throws Exception {
    try (ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      return stream.readObject();
    }
  }

  /**
   * Returns deterministic identifiers which do not introduce process disturbances.
   *
   * @param step physical step index
   * @return stable UUID
   */
  private static UUID id(int step) {
    return new UUID(0L, step);
  }

  /** Native fixture whose feed is an external snapshot participant. */
  private static final class Fixture {
    private final Stream feed;
    private final Separator separator;

    /**
     * Stores native fixture identities.
     *
     * @param feed external source stream
     * @param separator initialized vessel
     */
    private Fixture(Stream feed, Separator separator) {
      this.feed = feed;
      this.separator = separator;
    }
  }

  /** Test-only extension failure at the genuine postflash entrainment calculation point. */
  private static final class FailingPerformanceCalculator extends SeparatorPerformanceCalculator {
    private static final long serialVersionUID = 1L;

    /** {@inheritDoc} */
    @Override
    public void calculate(double gasDensity, double oilDensity, double waterDensity, double gasViscosity,
        double oilViscosity, double waterViscosity, double gasVelocity, double vesselDiameter, double vesselLength,
        String orientation, double liquidLevelFraction) {
      throw new IllegalStateException("test-only post-VU performance failure");
    }
  }

  /** Test-only thermodynamic extension for the existing caught init(2) skip branch. */
  private static final class FailingInitializationSystem extends SystemSrkEos {
    private static final long serialVersionUID = 1L;
    private boolean failSecondOrder;

    /** Creates the same initial SRK P/T basis as the ordinary fixture. */
    private FailingInitializationSystem() {
      super(300.0, 40.0);
    }

    /** {@inheritDoc} */
    @Override
    public void init(int type) {
      if (type == 2 && failSecondOrder) {
        throw new IllegalStateException("test-only init(2) failure");
      }
      super.init(type);
    }
  }
}
