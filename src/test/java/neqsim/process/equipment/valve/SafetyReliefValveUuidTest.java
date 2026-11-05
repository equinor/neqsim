package neqsim.process.equipment.valve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Native entry-point and capacity regressions for actual pressure-relief response.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class SafetyReliefValveUuidTest {
  /** The UUID entry point used by ProcessSystem must advance actual PSV lift. */
  @Test
  void nativeUuidTransientExecutesPressureResponse() {
    SafetyReliefValve valve = valve(12.0);
    valve.runTransient(0.1, UUID.randomUUID());
    assertTrue(valve.getOpenFraction() > 0.0);
  }

  /** The UUID steady-state path must evaluate pressure rather than retain a default opening. */
  @Test
  void nativeUuidSteadyClosesBelowSetPressure() {
    SafetyReliefValve valve = valve(5.0);
    valve.run(UUID.randomUUID());
    assertEquals(0.0, valve.getPercentValveOpening());
    assertEquals(0.0, valve.getInletStream().getFlowRate("kg/sec"));
    assertEquals(0.0, valve.getOutletStream().getFlowRate("kg/sec"));
  }

  /** At identical pressures, a linear half-lift valve passes half full-lift flow, not one quarter. */
  @Test
  void partialLiftCapacityIsAppliedExactlyOnce() {
    SafetyReliefValve full = valve(12.0);
    full.setTauOpenSec(0.0);
    full.runTransient(0.1);
    SafetyReliefValve half = valve(12.0);
    half.setTauOpenSec(0.1 / Math.log(2.0));
    half.runTransient(0.1);
    assertEquals(0.5, half.getOpenFraction(), 1.0e-12);
    assertEquals(0.5, half.getOutletStream().getFlowRate("kg/sec") / full.getOutletStream().getFlowRate("kg/sec"),
        1.0e-10);
  }

  /** A SNAP valve pops at its declared set pressure; overpressure defines the full-lift basis. */
  @Test
  void snapDemandBeginsAtSetPressure() {
    SafetyReliefValve valve = valve(10.0);
    valve.setTauOpenSec(0.0);
    valve.runTransient(0.1);
    assertEquals(1.0, valve.getOpenFraction());
  }

  /** The modulating falling leg remains open above the declared reseat pressure. */
  @Test
  void modulatingHysteresisDoesNotCloseAtSetPressure() {
    SafetyReliefValve valve = valve(12.0);
    valve.setOpeningLaw(SafetyReliefValve.OpeningLaw.MODULATING);
    valve.setTauOpenSec(0.0);
    valve.setTauCloseSec(0.0);
    valve.runTransient(0.1);
    valve.getInletStream().setPressure(9.5, "bara");
    valve.runTransient(0.1);
    assertTrue(valve.getOpenFraction() > 0.0);
  }

  /** Snapshot support must explicitly include the relief valve's subclass-owned response state. */
  @Test
  void concreteReliefValveHasCompleteOwnedSnapshot() {
    SafetyReliefValve valve = valve(12.0);
    valve.runTransient(0.1, UUID.randomUUID());
    assertNull(valve.getTransientStateCoverageIssue());
  }

  /** Repeated UUIDs and zero dt reevaluate flow without counting another physical response interval. */
  @Test
  void repeatedUuidAndZeroDtDoNotAdvanceLiftDwellOrCounters() {
    SafetyReliefValve valve = valve(12.0);
    UUID id = UUID.randomUUID();
    valve.runTransient(0.1, id);
    double lift = valve.getOpenFraction();
    double elapsed = valve.getResponseElapsedSeconds();
    double dwell = valve.getTimeSinceOpenSec();
    valve.runTransient(4.0, id);
    assertEquals(lift, valve.getOpenFraction());
    assertEquals(elapsed, valve.getResponseElapsedSeconds());
    assertEquals(dwell, valve.getTimeSinceOpenSec());
    assertEquals(1L, valve.getOpeningCycleCount());
    valve.runTransient(0.0, UUID.randomUUID());
    assertEquals(lift, valve.getOpenFraction());
    assertEquals(elapsed, valve.getResponseElapsedSeconds());
    assertEquals(id, valve.getLastResponseStepId());
  }

  /** The steady flag retains algebraic lift while positive physical time advances once for one UUID. */
  @Test
  void steadyModePhysicalClockAdvancesOncePerUuid() {
    SafetyReliefValve valve = valve(12.0);
    valve.setCalculateSteadyState(true);
    UUID id = UUID.randomUUID();
    valve.runTransient(0.1, id);
    valve.runTransient(9.0, id);
    assertEquals(0.1, valve.getTime(), 1.0e-15);
    assertEquals(0.1, valve.getResponseElapsedSeconds(), 1.0e-15);
    assertEquals(1.0, valve.getOpenFraction());
  }

  /** Invalid scalar configuration or dt cannot poison accepted response state. */
  @Test
  void finiteDomainsAndNegativeDtFailBeforeResponseMutation() {
    SafetyReliefValve valve = valve(12.0);
    Map<String, Object> before = valve.getPressureReliefConfiguration();
    assertThrows(IllegalArgumentException.class, () -> valve.runTransient(-0.1, UUID.randomUUID()));
    assertThrows(IllegalArgumentException.class, () -> valve.runTransient(Double.NaN, UUID.randomUUID()));
    assertThrows(IllegalArgumentException.class, () -> valve.setSetPressureBar(Double.POSITIVE_INFINITY));
    assertThrows(IllegalArgumentException.class, () -> valve.setRatedCv(-1.0));
    assertThrows(IllegalArgumentException.class, () -> valve.setBlowdownFrac(1.0));
    assertThrows(IllegalArgumentException.class, () -> valve.setKd(Double.NaN));
    assertThrows(IllegalArgumentException.class, () -> valve.initializeLiftState(1.1));
    assertEquals(before, valve.getPressureReliefConfiguration());
    assertEquals(0.0, valve.getResponseElapsedSeconds());
    assertEquals(0.0, valve.getOpenFraction());
    assertThrows(UnsupportedOperationException.class, () -> before.clear());
  }

  /** A minimum stable command cannot jump the actual lift through its physical rate limit. */
  @Test
  void stableCommandFloorRespectsActualRateLimit() {
    SafetyReliefValve valve = valve(12.0);
    valve.setTauOpenSec(0.0);
    valve.setMinStableOpenFrac(0.5);
    valve.setMaxLiftRatePerSec(0.01);
    valve.runTransient(0.1, UUID.randomUUID());
    assertEquals(0.001, valve.getOpenFraction(), 1.0e-15);
    assertEquals(1.0, valve.getLastCommandedLiftFraction());
  }

  /** Positive stable-command floors do not prevent exact seating after a genuine below-reseat demand. */
  @Test
  void closureWithStableFloorSeatsExactlyAndCanReopen() {
    SafetyReliefValve valve = valve(12.0);
    valve.setTauOpenSec(0.0);
    valve.setMinStableOpenFrac(0.03);
    valve.runTransient(0.1, UUID.randomUUID());
    valve.getInletStream().setPressure(8.0, "bara");
    for (int i = 0; i < 100; i++) {
      valve.runTransient(0.1, UUID.randomUUID());
    }
    assertEquals(0.0, valve.getOpenFraction());
    assertEquals(0.0, valve.getOutletStream().getFlowRate("kg/sec"));
    assertEquals(1L, valve.getReseatCycleCount());
    valve.getInletStream().setPressure(12.0, "bara");
    valve.runTransient(0.1, UUID.randomUUID());
    assertEquals(1.0, valve.getOpenFraction());
    assertEquals(2L, valve.getOpeningCycleCount());
    assertTrue(valve.getOutletStream().getFlowRate("kg/sec") > 0.0);
  }

  /** Mechanical availability freezes actual closed or open lift, rather than fabricating commanded success. */
  @Test
  void unavailableLiftSupportsExplicitFailedClosedAndStuckOpenProfiles() {
    SafetyReliefValve closed = valve(12.0);
    closed.initializeLiftState(0.0);
    closed.setLiftMotionAvailable(false);
    closed.runTransient(0.1, UUID.randomUUID());
    assertEquals(0.0, closed.getOpenFraction());
    assertEquals(0.0, closed.getOutletStream().getFlowRate("kg/sec"));
    assertTrue(closed.isPressureDemandActive());
    SafetyReliefValve open = valve(5.0);
    open.initializeLiftState(1.0);
    open.setLiftMotionAvailable(false);
    open.runTransient(0.1, UUID.randomUUID());
    assertEquals(1.0, open.getOpenFraction());
    assertTrue(open.getOutletStream().getFlowRate("kg/sec") > 0.0);
    assertFalse(open.isPressureDemandActive());
  }

  /** Equal or greater backpressure permits no forward discharge and does not collapse upstream pressure. */
  @Test
  void blockedBackpressureGivesZeroFlowAndRecoversForwardFlow() {
    SafetyReliefValve valve = valve(12.0);
    valve.setTauOpenSec(0.0);
    valve.setOutletPressure(13.0, "bara");
    valve.runTransient(0.1, UUID.randomUUID());
    assertEquals(1.0, valve.getOpenFraction());
    assertEquals(0.0, valve.getInletStream().getFlowRate("kg/sec"));
    assertEquals(0.0, valve.getOutletStream().getFlowRate("kg/sec"));
    assertEquals(12.0, valve.getInletStream().getPressure("bara"));
    valve.setOutletPressure(1.1, "bara");
    valve.runTransient(0.1, UUID.randomUUID());
    assertTrue(valve.getOutletStream().getFlowRate("kg/sec") > 0.0);
  }

  /** An initially zero-demand pressure boundary calculates real native capacity and can close and reopen exactly. */
  @Test
  void initiallyZeroPressureBoundaryOpensSeatsAndReopensWithActualFlow() {
    SafetyReliefValve valve = valve(12.0);
    valve.setTauOpenSec(0.0);
    valve.setTauCloseSec(0.0);
    valve.getInletStream().getFluid().setTotalNumberOfMoles(0.0);
    valve.runTransient(0.1, UUID.randomUUID());
    assertEquals(1.0, valve.getOpenFraction());
    assertTrue(valve.getOutletStream().getFlowRate("kg/sec") > 0.0);
    assertEquals(valve.getInletStream().getFlowRate("kg/sec"), valve.getOutletStream().getFlowRate("kg/sec"), 1.0e-12);
    valve.getInletStream().setPressure(8.0, "bara");
    valve.runTransient(0.1, UUID.randomUUID());
    assertEquals(0.0, valve.getOpenFraction());
    assertEquals(0.0, valve.getInletStream().getFlowRate("mole/sec"));
    assertEquals(0.0, valve.getOutletStream().getFlowRate("mole/sec"));
    valve.getInletStream().setPressure(12.0, "bara");
    valve.runTransient(0.1, UUID.randomUUID());
    assertTrue(valve.getOutletStream().getFlowRate("kg/sec") > 0.0);
    assertEquals(2L, valve.getOpeningCycleCount());
    assertEquals(1L, valve.getReseatCycleCount());
  }

  /**
   * Valid identical intensive states give the same native pressure/Cv capacity for zero and differing positive seeds.
   */
  @Test
  void pressureBoundaryCapacityDoesNotDependOnInitialThroughputSeed() {
    double reference = Double.NaN;
    for (double seed : new double[] {0.0, 1.0e-50, 1.0, 100.0, 10000.0}) {
      SafetyReliefValve valve = valve(12.0);
      valve.setTauOpenSec(0.0);
      valve.getInletStream().getFluid().setTotalNumberOfMoles(seed);
      valve.runTransient(0.1, UUID.randomUUID());
      double actual = valve.getOutletStream().getFlowRate("kg/sec");
      assertTrue(actual > 0.0);
      if (!Double.isNaN(reference)) {
        assertEquals(reference, actual, 1.0e-10);
      }
      reference = actual;
    }
  }

  /** Invalid upstream phase flags fail before lift, clocks or capacity settings can be changed. */
  @Test
  void invalidPhaseFractionsAreRejectedBeforeResponseMutation() {
    for (double beta : new double[] {-0.1, 0.5, Double.NaN}) {
      SafetyReliefValve valve = valve(12.0);
      valve.getInletStream().getFluid().getPhase(0).setBeta(beta);
      Map<String, Object> before = valve.getPressureReliefConfiguration();
      assertThrows(IllegalStateException.class, () -> valve.runTransient(0.1, UUID.randomUUID()));
      assertEquals(0.0, valve.getOpenFraction());
      assertEquals(0.0, valve.getResponseElapsedSeconds());
      assertEquals(before, valve.getPressureReliefConfiguration());
    }
    SafetyReliefValve duplicated = valve(12.0);
    duplicated.getInletStream().getFluid().setNumberOfPhases(2);
    duplicated.getInletStream().getFluid().getPhase(0).setBeta(1.0);
    duplicated.getInletStream().getFluid().getPhase(1).setBeta(1.0);
    assertThrows(IllegalStateException.class, () -> duplicated.runTransient(0.1, UUID.randomUUID()));
  }

  /** Opting in for PSV pressure demand does not turn an ordinary cold zero-throughput valve into a source. */
  @Test
  void ordinaryValveZeroFeedRemainsZero() {
    SafetyReliefValve donor = valve(12.0);
    donor.getInletStream().getFluid().setTotalNumberOfMoles(0.0);
    ThrottlingValve ordinary = new ThrottlingValve("ORDINARY", donor.getInletStream());
    ordinary.setCv(10.0);
    ordinary.setPercentValveOpening(100.0);
    ordinary.setOutletPressure(1.1, "bara");
    ordinary.setCalculateSteadyState(false);
    ordinary.runTransient(0.1, UUID.randomUUID());
    assertEquals(0.0, ordinary.getInletStream().getFlowRate("mole/sec"));
    assertEquals(0.0, ordinary.getOutletStream().getFlowRate("mole/sec"));
  }

  /** A failed native capacity solve never leaves normalization moles as physical inlet or outlet flow. */
  @Test
  void zeroBoundaryWorkspaceIsRemovedOnHydraulicFailure() {
    SafetyReliefValve donor = valve(12.0);
    donor.getInletStream().getFluid().setTotalNumberOfMoles(0.0);
    SafetyReliefValve failed = new FailingReliefValve(donor.getInletStream());
    failed.configureConventionalSnap(10.0, 0.1, 0.03, 10.0);
    failed.setOutletPressure(1.1, "bara");
    failed.setCalculateSteadyState(false);
    assertThrows(IllegalStateException.class, () -> failed.runTransient(0.1, UUID.randomUUID()));
    assertEquals(0.0, failed.getInletStream().getFlowRate("mole/sec"));
    assertEquals(0.0, failed.getOutletStream().getFlowRate("mole/sec"));
    assertEquals(0.0, failed.getOpenFraction());
    assertEquals(0.0, failed.getResponseElapsedSeconds());
  }

  /** Native pressures above 1000 bara retain their explicit unit rather than a magnitude heuristic. */
  @Test
  void highAbsolutePressureIsNotMistakenForPascals() {
    SafetyReliefValve valve = valve(1200.0);
    valve.setSetPressureBar(1000.0);
    valve.setTauOpenSec(0.0);
    valve.setRatedCv(0.001);
    valve.runTransient(0.1, UUID.randomUUID());
    assertEquals(1.0, valve.getOpenFraction());
  }

  /** Parent actuator or outlet-pressure solving cannot silently override a self-acting relief response. */
  @Test
  void competingParentResponseIsRejected() {
    SafetyReliefValve valve = valve(12.0);
    valve.setTravelModel(ValveTravelModel.FIRST_ORDER_LAG);
    assertThrows(IllegalStateException.class, () -> valve.runTransient(0.1, UUID.randomUUID()));
    valve.setTravelModel(ValveTravelModel.NONE);
    valve.setIsCalcOutPressure(true);
    assertThrows(IllegalStateException.class, () -> valve.runTransient(0.1, UUID.randomUUID()));
  }

  /** Owned snapshot plus an independent inlet snapshot restores rejected-trial response and exact replay. */
  @Test
  void snapshotRestoresConfigurationMotionCountersAndStepIdentity() {
    SafetyReliefValve valve = valve(12.0);
    valve.runTransient(0.1, UUID.randomUUID());
    ThrottlingValve.TransientState snapshot = valve.captureTransientState();
    Stream.TransientState inlet = ((Stream) valve.getInletStream()).captureTransientState();
    Map<String, Object> config = valve.getPressureReliefConfiguration();
    UUID replay = UUID.randomUUID();
    valve.runTransient(0.1, replay);
    double expectedLift = valve.getOpenFraction();
    double expectedFlow = valve.getOutletStream().getFlowRate("kg/sec");
    valve.setLiftMotionAvailable(false);
    valve.setRatedCv(1.0);
    valve.initializeLiftState(1.0);
    valve.restoreTransientState(snapshot);
    ((Stream) valve.getInletStream()).restoreTransientState(inlet);
    assertEquals(config, valve.getPressureReliefConfiguration());
    valve.runTransient(0.1, replay);
    assertEquals(expectedLift, valve.getOpenFraction());
    assertEquals(expectedFlow, valve.getOutletStream().getFlowRate("kg/sec"));
    assertEquals(1L, valve.getOpeningCycleCount());
    assertEquals(0.2, valve.getResponseElapsedSeconds(), 1.0e-15);
  }

  /** Current-schema serialized graph restart retains actual fault/lift/idempotent state and stream aliases. */
  @Test
  void currentSerializationRetainsOwnedState() throws Exception {
    SafetyReliefValve valve = valve(12.0);
    UUID id = UUID.randomUUID();
    valve.runTransient(0.1, id);
    valve.setLiftMotionAvailable(false);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(valve);
    }
    SafetyReliefValve copy;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      copy = (SafetyReliefValve) input.readObject();
    }
    assertEquals(valve.getPressureReliefConfiguration(), copy.getPressureReliefConfiguration());
    assertEquals(id, copy.getLastResponseStepId());
    assertEquals(valve.getOpenFraction(), copy.getOpenFraction());
    copy.runTransient(3.0, id);
    assertEquals(valve.getResponseElapsedSeconds(), copy.getResponseElapsedSeconds());
    assertFalse(copy.isLiftMotionAvailable());
  }

  /** UID1 legacy streams explicitly migrate new defaults instead of silently becoming mechanically unavailable. */
  @Test
  void legacySerializationMigratesNewResponseDefaults() throws Exception {
    byte[] encoded;
    try (InputStream resource = getClass()
        .getResourceAsStream("/neqsim/process/equipment/valve/safety-relief-valve-v1.ser.gz.base64")) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int count;
      while ((count = resource.read(buffer)) >= 0) {
        bytes.write(buffer, 0, count);
      }
      encoded = Base64.getMimeDecoder().decode(bytes.toByteArray());
    }
    SafetyReliefValve migrated;
    try (ObjectInputStream input = new ObjectInputStream(new GZIPInputStream(new ByteArrayInputStream(encoded)))) {
      migrated = (SafetyReliefValve) input.readObject();
    }
    assertTrue(migrated.isLiftMotionAvailable());
    assertEquals(0.3, migrated.getOpenFraction(), 1.0e-14);
    assertEquals(1.0e-8, migrated.getSeatToleranceFraction());
    assertEquals(0.0, migrated.getResponseElapsedSeconds());
    assertEquals(0L, migrated.getOpeningCycleCount());
    migrated.runTransient(0.1, UUID.randomUUID());
    assertTrue(migrated.getOpenFraction() > 0.3);
  }

  /**
   * Initializes an actual native gas valve with independent streams and a linear trim.
   *
   * @param pressure imposed upstream pressure, bara
   * @return configured dynamic valve
   */
  private static SafetyReliefValve valve(double pressure) {
    SystemInterface gas = new SystemSrkEos(300.0, pressure);
    gas.addComponent("methane", 1.0);
    gas.setMixingRule("classic");
    gas.setTotalFlowRate(10000.0, "kg/hr");
    Stream inlet = new Stream("V101-GAS", gas);
    inlet.run();
    SafetyReliefValve valve = new SafetyReliefValve("PSV-101", inlet);
    valve.configureConventionalSnap(10.0, 0.10, 0.07, 10.0);
    valve.setKd(1.0);
    valve.setBackpressureSensitivity(0.0);
    valve.setMinStableOpenFrac(0.0);
    valve.setMinOpenTimeSec(0.0);
    valve.setMinCloseTimeSec(0.0);
    valve.setMaxLiftRatePerSec(0.0);
    valve.setOutletPressure(1.1, "bara");
    valve.setCalculateSteadyState(false);
    valve.getMechanicalDesign().setValveCharacterization("linear");
    return valve;
  }

  /**
   * Deliberately fails the native capacity boundary to test cleanup, without claiming subclass snapshot coverage.
   * 
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class FailingReliefValve extends SafetyReliefValve {
    private static final long serialVersionUID = 1L;

    /** @param inlet actual initialized gas boundary */
    private FailingReliefValve(neqsim.process.equipment.stream.StreamInterface inlet) {
      super("FAILED-CAPACITY", inlet);
    }

    /** {@inheritDoc} */
    @Override
    public double calculateMolarFlow() {
      throw new IllegalStateException("Deliberate native hydraulic solve failure");
    }
  }
}
