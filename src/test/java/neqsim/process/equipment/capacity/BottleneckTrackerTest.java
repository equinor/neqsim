package neqsim.process.equipment.capacity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.reservoir.WellFlow;
import neqsim.process.equipment.separator.Separator;

/**
 * Unit tests for {@link BottleneckTracker}.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class BottleneckTrackerTest {

  /** Separator capacity constraint used to build topside-bottleneck results. */
  private static final CapacityConstraint SEP_CONSTRAINT = new CapacityConstraint("gasLoadFactor", "m/s",
      ConstraintType.SOFT);

  /** Well capacity constraint used to build subsurface-bottleneck results. */
  private static final CapacityConstraint WELL_CONSTRAINT = new CapacityConstraint("well drawdown", "bar",
      ConstraintType.SOFT);

  /** Separator equipment used as a topside bottleneck. */
  private static final Separator SEPARATOR = new Separator("Inlet separator");

  /** Well equipment used as a subsurface bottleneck. */
  private static final WellFlow WELL = new WellFlow("Well-1 IPR");

  /**
   * Builds a separator bottleneck result at the supplied utilization fraction.
   *
   * @param utilization the utilization fraction (1.0 = 100%)
   * @return the bottleneck result
   */
  private static BottleneckResult separator(double utilization) {
    return new BottleneckResult(SEPARATOR, SEP_CONSTRAINT, utilization);
  }

  /**
   * Builds a well bottleneck result at the supplied utilization fraction.
   *
   * @param utilization the utilization fraction (1.0 = 100%)
   * @return the bottleneck result
   */
  private static BottleneckResult well(double utilization) {
    return new BottleneckResult(WELL, WELL_CONSTRAINT, utilization);
  }

  @Test
  void testEmptyTracker() {
    BottleneckTracker tracker = new BottleneckTracker();
    assertTrue(tracker.isEmpty());
    assertEquals(0, tracker.size());
    assertNull(tracker.getLatest());
    assertNull(tracker.getPeakSnapshot());
    assertEquals(0.0, tracker.getPeakUtilizationPercent(), 1e-9);
    assertEquals(0, tracker.getMigrationCount());
    assertTrue(tracker.getMigrationEvents().isEmpty());
  }

  @Test
  void testRecordAndLatest() {
    BottleneckTracker tracker = new BottleneckTracker();
    tracker.record(0.0, "Year 0", separator(0.80));
    BottleneckTracker.Snapshot latest = tracker.record(1.0, "Year 1", well(0.95));

    assertEquals(2, tracker.size());
    assertFalse(tracker.isEmpty());
    assertEquals(latest, tracker.getLatest());
    assertEquals("Well-1 IPR", tracker.getLatest().getEquipmentName());
    assertEquals("well drawdown", tracker.getLatest().getConstraintName());
    assertEquals(95.0, tracker.getLatest().getUtilizationPercent(), 1e-6);
    assertEquals("Year 1", tracker.getLatest().getLabel());
  }

  @Test
  void testEmptyResultRecordedAsUnconstrained() {
    BottleneckTracker tracker = new BottleneckTracker();
    tracker.record(0.0, BottleneckResult.empty());
    tracker.record(1.0, null);

    assertEquals(2, tracker.size());
    for (BottleneckTracker.Snapshot snap : tracker.getSnapshots()) {
      assertFalse(snap.hasBottleneck());
      assertEquals("None", snap.getEquipmentName());
      assertEquals("None", snap.getConstraintName());
    }
  }

  @Test
  void testMigrationDetection() {
    BottleneckTracker tracker = new BottleneckTracker();
    // Topside-bound early, then migrates to the subsurface, then back to topside.
    tracker.record(0.0, separator(0.70));
    tracker.record(1.0, separator(0.78));
    tracker.record(2.0, well(0.92));
    tracker.record(3.0, well(1.05));
    tracker.record(4.0, separator(0.85));

    List<BottleneckTracker.Snapshot> events = tracker.getMigrationEvents();
    // First snapshot + two transitions (separator->well, well->separator) = 3 events.
    assertEquals(3, events.size());
    assertEquals(2, tracker.getMigrationCount());
    assertEquals(0.0, events.get(0).getTime(), 1e-9);
    assertEquals("Inlet separator", events.get(0).getEquipmentName());
    assertEquals(2.0, events.get(1).getTime(), 1e-9);
    assertEquals("Well-1 IPR", events.get(1).getEquipmentName());
    assertEquals(4.0, events.get(2).getTime(), 1e-9);
    assertEquals("Inlet separator", events.get(2).getEquipmentName());
  }

  @Test
  void testNoMigrationWhenStable() {
    BottleneckTracker tracker = new BottleneckTracker();
    tracker.record(0.0, separator(0.50));
    tracker.record(1.0, separator(0.60));
    tracker.record(2.0, separator(0.70));

    // Only the initial event, no transitions.
    assertEquals(1, tracker.getMigrationEvents().size());
    assertEquals(0, tracker.getMigrationCount());
  }

  @Test
  void testPeakAndDistinctEquipment() {
    BottleneckTracker tracker = new BottleneckTracker();
    tracker.record(0.0, separator(0.70));
    tracker.record(1.0, well(1.27));
    tracker.record(2.0, separator(0.85));

    assertEquals(127.0, tracker.getPeakUtilizationPercent(), 1e-6);
    assertEquals(1.0, tracker.getPeakSnapshot().getTime(), 1e-9);
    assertTrue(tracker.getPeakSnapshot().isExceeded());

    List<String> distinct = tracker.getDistinctBottleneckEquipment();
    assertEquals(2, distinct.size());
    assertEquals("Inlet separator", distinct.get(0));
    assertEquals("Well-1 IPR", distinct.get(1));
  }

  @Test
  void testJsonOutput() {
    BottleneckTracker tracker = new BottleneckTracker();
    tracker.record(0.0, "Year 0", separator(0.78));
    tracker.record(1.0, "Year 1", well(1.05));

    String json = tracker.toJson();
    assertTrue(json.contains("\"schemaVersion\":\"1.0\""));
    assertTrue(json.contains("\"snapshotCount\":2"));
    assertTrue(json.contains("\"migrationCount\":1"));
    assertTrue(json.contains("\"Inlet separator\""));
    assertTrue(json.contains("\"Well-1 IPR\""));
    assertTrue(json.contains("\"exceeded\":true"));
    assertTrue(json.contains("\"snapshots\":"));
    assertTrue(json.contains("\"migrations\":"));
  }

  @Test
  void testClear() {
    BottleneckTracker tracker = new BottleneckTracker();
    tracker.record(0.0, separator(0.70));
    tracker.record(1.0, well(0.90));
    tracker.clear();

    assertTrue(tracker.isEmpty());
    assertEquals(0, tracker.size());
  }
}
