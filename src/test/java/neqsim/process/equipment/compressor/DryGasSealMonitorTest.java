package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.DryGasSealMonitor.*;

/**
 * Fault injection and state-replay tests for seal support-system advisory monitoring.
 *
 * @author NeqSim
 * @version 1.0
 */
class DryGasSealMonitorTest {
  /**
   * Creates illustrative limits, not OEM design recommendations.
   *
   * @return limits
   */
  private Limits limits() {
    return new Limits(2, 100, 3, 10, 0.1, 2, 3);
  }

  /**
   * Creates one synthetic scan with a chosen vent flow.
   *
   * @param ventFlow NL/min
   * @return scan
   */
  private Sample scan(double ventFlow) {
    return new Sample(82, 80, ventFlow, 2, 10, 0.1, 0, true, true);
  }

  /** Verifies the documented API, inclusive limits and confirmation delay. */
  @Test
  void delayRecoveryLatchAndReset() {
    DryGasSealMonitor monitor = new DryGasSealMonitor(limits());
    assertFalse(monitor.evaluate(scan(100), 1).isTripRecommended());
    assertFalse(monitor.evaluate(scan(101), 1).isTripRecommended());
    monitor.evaluate(scan(100), 1); // Fault recovery resets continuous duration.
    Result first = monitor.evaluate(scan(101), 2);
    assertFalse(first.isTripRecommended());
    Result confirmed = monitor.evaluate(scan(101), 1);
    assertTrue(confirmed.isTripRecommended());
    assertTrue(confirmed.getConfirmedFaults().contains(Fault.HIGH_PRIMARY_VENT_FLOW));
    assertFalse(first.isTripRecommended()); // Snapshots do not change with the monitor.
    assertTrue(monitor.evaluate(scan(100), 1).isTripRecommended());
    monitor.reset();
    assertFalse(monitor.evaluate(scan(100), 1).isTripRecommended());
    assertThrows(UnsupportedOperationException.class, () -> confirmed.getActiveFaults().clear());
  }

  /** Verifies simultaneous faults use the correct pressure reference and gas circuits. */
  @Test
  void faultsDistinguishPressureBarriersAndGasCircuits() {
    DryGasSealMonitor monitor = new DryGasSealMonitor(limits());
    Sample faulty = new Sample(79, 80, 150, 4, 5, -0.2, 0, true, false);
    Result result = monitor.evaluate(faulty, 3);
    assertTrue(result.isDataValid());
    assertEquals(6, result.getConfirmedFaults().size());
    assertFalse(result.getActiveFaults().contains(Fault.INVALID_DATA));
    assertTrue(result.getActiveFaults().contains(Fault.LOW_SUPPLY_DIFFERENTIAL_PRESSURE));
    assertTrue(result.getActiveFaults().contains(Fault.LOW_SEPARATION_DIFFERENTIAL_PRESSURE));
  }

  /** Verifies bad and stale data immediately produce unresolved evidence recommendations. */
  @Test
  void invalidDataNeverMeansHealthy() {
    DryGasSealMonitor monitor = new DryGasSealMonitor(limits());
    assertFalse(monitor.evaluate(null, 1).isDataValid());
    assertTrue(monitor.evaluate(scan(Double.NaN), 1).isTripRecommended());
    monitor.reset();
    Sample stale = new Sample(82, 80, 50, 2, 10, 0.1, 3, true, true);
    assertEquals(1, monitor.evaluate(stale, 1).getActiveFaults().size());
    assertTrue(monitor.evaluate(stale, 1).getConfirmedFaults().contains(Fault.INVALID_DATA));
    assertThrows(IllegalArgumentException.class, () -> monitor.evaluate(scan(50), 0));
    assertThrows(IllegalArgumentException.class, () -> new Limits(2, 100, 3, 10, 0.1, Double.NaN, 3));
  }

  /** Verifies interrupted physical evidence is not accumulated through an invalid scan. */
  @Test
  void invalidScanBreaksContinuousPhysicalFaultEvidence() {
    DryGasSealMonitor monitor = new DryGasSealMonitor(limits());
    monitor.evaluate(scan(101), 2);
    monitor.evaluate(null, 1);
    Result result = monitor.evaluate(scan(101), 1);
    assertFalse(result.getConfirmedFaults().contains(Fault.HIGH_PRIMARY_VENT_FLOW));
    assertEquals(1.0, result.getElapsedSeconds().get(Fault.HIGH_PRIMARY_VENT_FLOW), 0.0);
  }

  /**
   * Verifies serialized state preserves the delay and latch on replay.
   *
   * @throws Exception on reflection or serialization failure
   */
  @Test
  void serializationPreservesTimers() throws Exception {
    DryGasSealMonitor monitor = new DryGasSealMonitor(limits());
    monitor.evaluate(scan(101), 2);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(buffer)) {
      out.writeObject(monitor);
    }
    DryGasSealMonitor copy;
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()))) {
      copy = (DryGasSealMonitor) in.readObject();
    }
    assertTrue(copy.evaluate(scan(101), 1).isTripRecommended());
    assertFalse(monitor.evaluate(scan(100), 1).isTripRecommended());
  }
}
