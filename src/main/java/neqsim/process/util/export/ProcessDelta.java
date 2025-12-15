package neqsim.process.util.export;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents the difference between two process snapshots.
 *
 * <p>
 * Process deltas enable efficient synchronization of state changes between NeqSim and external
 * systems, transmitting only changed values rather than full state.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ProcessDelta implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String fromSnapshotId;
  private final String toSnapshotId;
  private final Map<String, Double> changedValues;
  private final Map<String, Double> previousValues;
  private final Map<String, String> units;

  /**
   * Creates a delta between two snapshots.
   *
   * @param from the baseline snapshot
   * @param to the current snapshot
   */
  public ProcessDelta(ProcessSnapshot from, ProcessSnapshot to) {
    this.fromSnapshotId = from.getSnapshotId();
    this.toSnapshotId = to.getSnapshotId();
    this.changedValues = new HashMap<>();
    this.previousValues = new HashMap<>();
    this.units = new HashMap<>();

    computeDifferences(from, to);
  }

  private void computeDifferences(ProcessSnapshot from, ProcessSnapshot to) {
    // Find changed values
    for (String name : to.getMeasurementNames()) {
      double toValue = to.getMeasurement(name);
      double fromValue = from.getMeasurement(name);

      if (Double.isNaN(fromValue) || Math.abs(toValue - fromValue) > 1e-10) {
        changedValues.put(name, toValue);
        previousValues.put(name, fromValue);
        units.put(name, to.getMeasurementUnit(name));
      }
    }
  }

  /**
   * Gets all changed measurement names.
   *
   * @return set of changed measurement names
   */
  public Set<String> getChangedMeasurements() {
    return changedValues.keySet();
  }

  /**
   * Gets the new value for a measurement.
   *
   * @param name measurement name
   * @return new value or NaN if not changed
   */
  public double getNewValue(String name) {
    return changedValues.getOrDefault(name, Double.NaN);
  }

  /**
   * Gets the previous value for a measurement.
   *
   * @param name measurement name
   * @return previous value or NaN if not tracked
   */
  public double getPreviousValue(String name) {
    return previousValues.getOrDefault(name, Double.NaN);
  }

  /**
   * Gets the change (delta) for a measurement.
   *
   * @param name measurement name
   * @return change in value (new - previous) or NaN
   */
  public double getChange(String name) {
    Double newVal = changedValues.get(name);
    Double oldVal = previousValues.get(name);

    if (newVal != null && oldVal != null && !Double.isNaN(oldVal)) {
      return newVal - oldVal;
    }
    return Double.NaN;
  }

  /**
   * Gets the relative change for a measurement.
   *
   * @param name measurement name
   * @return relative change (new - previous) / previous, or NaN
   */
  public double getRelativeChange(String name) {
    Double oldVal = previousValues.get(name);
    if (oldVal != null && Math.abs(oldVal) > 1e-10) {
      double change = getChange(name);
      return change / oldVal;
    }
    return Double.NaN;
  }

  /**
   * Gets the unit for a changed measurement.
   *
   * @param name measurement name
   * @return unit string or null
   */
  public String getUnit(String name) {
    return units.get(name);
  }

  /**
   * Checks if there are any changes.
   *
   * @return true if at least one value changed
   */
  public boolean hasChanges() {
    return !changedValues.isEmpty();
  }

  /**
   * Gets the number of changed measurements.
   *
   * @return count of changes
   */
  public int getChangeCount() {
    return changedValues.size();
  }

  /**
   * Gets all changes as a map.
   *
   * @return map of measurement names to new values
   */
  public Map<String, Double> getAllChanges() {
    return new HashMap<>(changedValues);
  }

  /**
   * Gets the source snapshot ID.
   *
   * @return from snapshot ID
   */
  public String getFromSnapshotId() {
    return fromSnapshotId;
  }

  /**
   * Gets the target snapshot ID.
   *
   * @return to snapshot ID
   */
  public String getToSnapshotId() {
    return toSnapshotId;
  }

  /**
   * Applies this delta to a snapshot to produce a new snapshot.
   *
   * @param base the base snapshot
   * @param newId ID for the new snapshot
   * @return updated snapshot
   */
  public ProcessSnapshot apply(ProcessSnapshot base, String newId) {
    ProcessSnapshot result = new ProcessSnapshot(newId);

    // Copy base values
    for (String name : base.getMeasurementNames()) {
      result.setMeasurement(name, base.getMeasurement(name), base.getMeasurementUnit(name));
    }

    // Apply changes
    for (String name : changedValues.keySet()) {
      result.setMeasurement(name, changedValues.get(name), units.get(name));
    }

    return result;
  }

  @Override
  public String toString() {
    return String.format("ProcessDelta[%s -> %s, %d changes]", fromSnapshotId, toSnapshotId,
        changedValues.size());
  }
}
