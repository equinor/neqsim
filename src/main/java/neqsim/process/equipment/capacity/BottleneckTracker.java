package neqsim.process.equipment.capacity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Records how the binding capacity bottleneck of a process changes over time.
 *
 * <p>
 * A single {@link BottleneckResult} answers <em>what limits the plant right now</em>. During a field-life depletion, a
 * dynamic transient, or any rate/allocation sweep the limiting constraint typically <em>migrates</em> — for example
 * from a topside separator early in life to a subsurface well-drawdown limit late in life. {@code BottleneckTracker}
 * captures that temporal dimension as a first-class object: the caller records a {@link BottleneckResult} at each step
 * (year, second, iteration, ...) and the tracker surfaces the migration events, the peak loading, and a JSON timeline
 * that an agent can hand off downstream.
 * </p>
 *
 * <p>
 * The tracker deliberately depends only on {@link BottleneckResult} (not on {@code ProcessModel} or
 * {@code ProcessSystem}) so it stays in the leaf {@code capacity} package with no circular dependency. Typical usage:
 * </p>
 *
 * <pre>
 * BottleneckTracker tracker = new BottleneckTracker();
 * for (int year = 0; year &lt;= nYears; year++) {
 *   plant.run();
 *   tracker.record(year, "Year " + year, plant.findBottleneck());
 *   reservoir.runTransient(secondsPerYear);
 * }
 * for (BottleneckTracker.Snapshot ev : tracker.getMigrationEvents()) {
 *   System.out.println(ev);
 * }
 * String json = tracker.toJson();
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class BottleneckTracker implements Serializable {

  /** Serialization version identifier. */
  private static final long serialVersionUID = 1000L;

  /** Schema version of the {@link #toJson()} output. */
  public static final String SCHEMA_VERSION = "1.0";

  /**
   * Immutable snapshot of the binding bottleneck at one point in time.
   */
  public static class Snapshot implements Serializable {

    /** Serialization version identifier. */
    private static final long serialVersionUID = 1000L;

    /** The time stamp (year, second, iteration, ... — caller defined). */
    private final double time;

    /** Optional human-readable label for the step (may be empty, never null). */
    private final String label;

    /** Name of the bottleneck equipment, or {@code "None"} if unconstrained. */
    private final String equipmentName;

    /** Name of the binding constraint, or {@code "None"} if unconstrained. */
    private final String constraintName;

    /** Utilization of the binding constraint in percent (100.0 = 100%). */
    private final double utilizationPercent;

    /** Whether the binding constraint is exceeded (utilization above 100%). */
    private final boolean exceeded;

    /** Whether a bottleneck (equipment and constraint) was present. */
    private final boolean hasBottleneck;

    /**
     * Creates an immutable bottleneck snapshot.
     *
     * @param time               the time stamp (caller-defined units)
     * @param label              optional human-readable label; null is stored as an empty string
     * @param equipmentName      the bottleneck equipment name
     * @param constraintName     the binding constraint name
     * @param utilizationPercent the utilization in percent (100.0 = 100%)
     * @param exceeded           true if the constraint is exceeded
     * @param hasBottleneck      true if a bottleneck equipment/constraint was present
     */
    public Snapshot(double time, String label, String equipmentName, String constraintName, double utilizationPercent,
	boolean exceeded, boolean hasBottleneck) {
      this.time = time;
      this.label = label != null ? label : "";
      this.equipmentName = equipmentName;
      this.constraintName = constraintName;
      this.utilizationPercent = utilizationPercent;
      this.exceeded = exceeded;
      this.hasBottleneck = hasBottleneck;
    }

    /**
     * Gets the time stamp of this snapshot.
     *
     * @return the time stamp in caller-defined units
     */
    public double getTime() {
      return time;
    }

    /**
     * Gets the human-readable label for this snapshot.
     *
     * @return the label, or an empty string if none was supplied
     */
    public String getLabel() {
      return label;
    }

    /**
     * Gets the name of the bottleneck equipment.
     *
     * @return the equipment name, or {@code "None"} if unconstrained
     */
    public String getEquipmentName() {
      return equipmentName;
    }

    /**
     * Gets the name of the binding constraint.
     *
     * @return the constraint name, or {@code "None"} if unconstrained
     */
    public String getConstraintName() {
      return constraintName;
    }

    /**
     * Gets the utilization of the binding constraint.
     *
     * @return the utilization in percent (100.0 = 100%)
     */
    public double getUtilizationPercent() {
      return utilizationPercent;
    }

    /**
     * Checks whether the binding constraint is exceeded.
     *
     * @return true if the utilization is above 100%
     */
    public boolean isExceeded() {
      return exceeded;
    }

    /**
     * Checks whether a bottleneck equipment/constraint was present.
     *
     * @return true if a bottleneck was found at this step
     */
    public boolean hasBottleneck() {
      return hasBottleneck;
    }

    /**
     * Gets the {@code "equipment::constraint"} identity key used for migration detection.
     *
     * @return the identity key combining equipment and constraint names
     */
    public String getIdentity() {
      return equipmentName + "::" + constraintName;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
      String prefix = label.isEmpty() ? String.format(Locale.ROOT, "t=%.3g", time) : label;
      return String.format(Locale.ROOT, "%s: %s -> %s @ %.1f%%%s", prefix, equipmentName, constraintName,
	  utilizationPercent, exceeded ? " [EXCEEDED]" : "");
    }
  }

  /** Ordered list of recorded snapshots. */
  private final List<Snapshot> snapshots = new ArrayList<Snapshot>();

  /**
   * Records the binding bottleneck at a time step.
   *
   * @param time   the time stamp (caller-defined units)
   * @param result the bottleneck result to record; an empty result is recorded as an unconstrained snapshot
   * @return the snapshot that was created and stored
   */
  public Snapshot record(double time, BottleneckResult result) {
    return record(time, "", result);
  }

  /**
   * Records the binding bottleneck at a labelled time step.
   *
   * @param time   the time stamp (caller-defined units)
   * @param label  a human-readable label for the step (may be null)
   * @param result the bottleneck result to record; if null or empty an unconstrained snapshot is recorded
   * @return the snapshot that was created and stored
   */
  public Snapshot record(double time, String label, BottleneckResult result) {
    Snapshot snapshot;
    if (result == null || !result.hasBottleneck()) {
      snapshot = new Snapshot(time, label, "None", "None", 0.0, false, false);
    } else {
      snapshot = new Snapshot(time, label, result.getEquipmentName(), result.getConstraintName(),
	  result.getUtilizationPercent(), result.isExceeded(), true);
    }
    snapshots.add(snapshot);
    return snapshot;
  }

  /**
   * Gets all recorded snapshots in chronological order.
   *
   * @return an unmodifiable-style copy of the recorded snapshots
   */
  public List<Snapshot> getSnapshots() {
    return new ArrayList<Snapshot>(snapshots);
  }

  /**
   * Gets the most recently recorded snapshot.
   *
   * @return the latest snapshot, or null if nothing has been recorded
   */
  public Snapshot getLatest() {
    return snapshots.isEmpty() ? null : snapshots.get(snapshots.size() - 1);
  }

  /**
   * Gets the number of recorded snapshots.
   *
   * @return the snapshot count
   */
  public int size() {
    return snapshots.size();
  }

  /**
   * Checks whether any snapshots have been recorded.
   *
   * @return true if no snapshots have been recorded
   */
  public boolean isEmpty() {
    return snapshots.isEmpty();
  }

  /**
   * Clears all recorded snapshots.
   */
  public void clear() {
    snapshots.clear();
  }

  /**
   * Returns the snapshots at which the binding bottleneck changed identity.
   *
   * <p>
   * The first recorded snapshot is always treated as a migration event (the initial bottleneck). Thereafter a snapshot
   * is a migration event when its {@code "equipment::constraint"} identity differs from the previous snapshot. This
   * pinpoints exactly when — and to what — the limiting constraint handed over, e.g. topside separator to subsurface
   * well drawdown.
   * </p>
   *
   * @return the list of migration-event snapshots in chronological order
   */
  public List<Snapshot> getMigrationEvents() {
    List<Snapshot> events = new ArrayList<Snapshot>();
    String previousIdentity = null;
    for (Snapshot snapshot : snapshots) {
      String identity = snapshot.getIdentity();
      if (previousIdentity == null || !identity.equals(previousIdentity)) {
	events.add(snapshot);
      }
      previousIdentity = identity;
    }
    return events;
  }

  /**
   * Gets the number of times the binding bottleneck changed identity after the first snapshot.
   *
   * @return the number of migration transitions (0 if the bottleneck never changed)
   */
  public int getMigrationCount() {
    int events = getMigrationEvents().size();
    return events > 0 ? events - 1 : 0;
  }

  /**
   * Gets the distinct bottleneck equipment names in first-appearance order.
   *
   * @return the list of distinct equipment that was ever the bottleneck
   */
  public List<String> getDistinctBottleneckEquipment() {
    List<String> distinct = new ArrayList<String>();
    for (Snapshot snapshot : snapshots) {
      String name = snapshot.getEquipmentName();
      if (!distinct.contains(name)) {
	distinct.add(name);
      }
    }
    return distinct;
  }

  /**
   * Gets the snapshot with the highest binding-constraint utilization.
   *
   * @return the peak-utilization snapshot, or null if nothing has been recorded
   */
  public Snapshot getPeakSnapshot() {
    Snapshot peak = null;
    for (Snapshot snapshot : snapshots) {
      if (peak == null || snapshot.getUtilizationPercent() > peak.getUtilizationPercent()) {
	peak = snapshot;
      }
    }
    return peak;
  }

  /**
   * Gets the highest binding-constraint utilization recorded.
   *
   * @return the peak utilization in percent, or 0.0 if nothing has been recorded
   */
  public double getPeakUtilizationPercent() {
    Snapshot peak = getPeakSnapshot();
    return peak != null ? peak.getUtilizationPercent() : 0.0;
  }

  /**
   * Builds a human-readable timeline of the bottleneck migration.
   *
   * @return a multi-line summary listing each migration event
   */
  public String getTimelineSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("Bottleneck migration timeline (").append(getMigrationCount()).append(" transition(s) over ")
	.append(size()).append(" steps):");
    for (Snapshot event : getMigrationEvents()) {
      sb.append(System.lineSeparator()).append("  ").append(event.toString());
    }
    return sb.toString();
  }

  /**
   * Serializes the recorded timeline to a schema-versioned JSON string.
   *
   * <p>
   * The JSON contains the full snapshot list, the migration-event list, the peak utilization and the distinct
   * bottleneck equipment, so an agent can reason about the temporal capacity behaviour without re-running the study.
   * </p>
   *
   * @return a JSON document describing the bottleneck timeline
   */
  public String toJson() {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"schemaVersion\":\"").append(SCHEMA_VERSION).append("\"");
    sb.append(",\"snapshotCount\":").append(size());
    sb.append(",\"migrationCount\":").append(getMigrationCount());
    sb.append(",\"peakUtilizationPercent\":").append(formatNumber(getPeakUtilizationPercent()));
    sb.append(",\"distinctBottleneckEquipment\":[");
    List<String> distinct = getDistinctBottleneckEquipment();
    for (int i = 0; i < distinct.size(); i++) {
      if (i > 0) {
	sb.append(",");
      }
      sb.append("\"").append(escape(distinct.get(i))).append("\"");
    }
    sb.append("]");
    sb.append(",\"snapshots\":");
    appendSnapshotArray(sb, snapshots);
    sb.append(",\"migrations\":");
    appendSnapshotArray(sb, getMigrationEvents());
    sb.append("}");
    return sb.toString();
  }

  /**
   * Appends a JSON array of snapshots to the supplied builder.
   *
   * @param sb    the builder to append to
   * @param items the snapshots to serialize
   */
  private void appendSnapshotArray(StringBuilder sb, List<Snapshot> items) {
    sb.append("[");
    for (int i = 0; i < items.size(); i++) {
      if (i > 0) {
	sb.append(",");
      }
      Snapshot s = items.get(i);
      sb.append("{\"time\":").append(formatNumber(s.getTime()));
      sb.append(",\"label\":\"").append(escape(s.getLabel())).append("\"");
      sb.append(",\"equipment\":\"").append(escape(s.getEquipmentName())).append("\"");
      sb.append(",\"constraint\":\"").append(escape(s.getConstraintName())).append("\"");
      sb.append(",\"utilizationPercent\":").append(formatNumber(s.getUtilizationPercent()));
      sb.append(",\"exceeded\":").append(s.isExceeded());
      sb.append(",\"hasBottleneck\":").append(s.hasBottleneck());
      sb.append("}");
    }
    sb.append("]");
  }

  /**
   * Formats a double for JSON output, emitting {@code null} for non-finite values.
   *
   * @param value the value to format
   * @return the JSON number literal, or {@code "null"} if the value is not finite
   */
  private static String formatNumber(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return "null";
    }
    return String.format(Locale.ROOT, "%.6g", value);
  }

  /**
   * Escapes a string for safe inclusion in a JSON document.
   *
   * @param value the string to escape (may be null)
   * @return the escaped string, never null
   */
  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
      case '\\':
	sb.append("\\\\");
	break;
      case '"':
	sb.append("\\\"");
	break;
      case '\n':
	sb.append("\\n");
	break;
      case '\r':
	sb.append("\\r");
	break;
      case '\t':
	sb.append("\\t");
	break;
      default:
	sb.append(c);
	break;
      }
    }
    return sb.toString();
  }
}
