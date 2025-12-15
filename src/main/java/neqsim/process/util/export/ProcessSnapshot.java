package neqsim.process.util.export;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a snapshot of a process system state at a point in time.
 *
 * <p>
 * Snapshots capture the complete state of measurement devices and key process variables, enabling
 * checkpointing, comparison, and state restoration.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ProcessSnapshot implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String snapshotId;
  private final Instant timestamp;
  private final Map<String, Double> measurementValues;
  private final Map<String, String> measurementUnits;
  private final Map<String, Object> additionalState;
  private String description;

  /**
   * Creates a new process snapshot.
   *
   * @param snapshotId unique identifier for this snapshot
   */
  public ProcessSnapshot(String snapshotId) {
    this.snapshotId = snapshotId;
    this.timestamp = Instant.now();
    this.measurementValues = new HashMap<>();
    this.measurementUnits = new HashMap<>();
    this.additionalState = new HashMap<>();
  }

  /**
   * Creates a snapshot with a specific timestamp.
   *
   * @param snapshotId unique identifier
   * @param timestamp the snapshot timestamp
   */
  public ProcessSnapshot(String snapshotId, Instant timestamp) {
    this.snapshotId = snapshotId;
    this.timestamp = timestamp;
    this.measurementValues = new HashMap<>();
    this.measurementUnits = new HashMap<>();
    this.additionalState = new HashMap<>();
  }

  /**
   * Stores a measurement value.
   *
   * @param name measurement name
   * @param value measurement value
   * @param unit measurement unit
   */
  public void setMeasurement(String name, double value, String unit) {
    measurementValues.put(name, value);
    measurementUnits.put(name, unit);
  }

  /**
   * Gets a measurement value.
   *
   * @param name measurement name
   * @return the value or NaN if not found
   */
  public double getMeasurement(String name) {
    return measurementValues.getOrDefault(name, Double.NaN);
  }

  /**
   * Gets a measurement unit.
   *
   * @param name measurement name
   * @return the unit or null if not found
   */
  public String getMeasurementUnit(String name) {
    return measurementUnits.get(name);
  }

  /**
   * Gets all measurement names.
   *
   * @return array of measurement names
   */
  public String[] getMeasurementNames() {
    return measurementValues.keySet().toArray(new String[0]);
  }

  /**
   * Stores additional state information.
   *
   * @param key state key
   * @param value state value
   */
  public void setState(String key, Object value) {
    additionalState.put(key, value);
  }

  /**
   * Gets additional state information.
   *
   * @param key state key
   * @return the value or null
   */
  public Object getState(String key) {
    return additionalState.get(key);
  }

  /**
   * Gets the snapshot ID.
   *
   * @return snapshot ID
   */
  public String getSnapshotId() {
    return snapshotId;
  }

  /**
   * Gets the snapshot timestamp.
   *
   * @return timestamp
   */
  public Instant getTimestamp() {
    return timestamp;
  }

  /**
   * Gets the description.
   *
   * @return description or null
   */
  public String getDescription() {
    return description;
  }

  /**
   * Sets the description.
   *
   * @param description snapshot description
   */
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * Gets all measurements as a map.
   *
   * @return map of measurement names to values
   */
  public Map<String, Double> getAllMeasurements() {
    return new HashMap<>(measurementValues);
  }

  /**
   * Calculates the difference from another snapshot.
   *
   * @param other the other snapshot
   * @return ProcessDelta representing the changes
   */
  public ProcessDelta diff(ProcessSnapshot other) {
    return new ProcessDelta(this, other);
  }

  @Override
  public String toString() {
    return String.format("ProcessSnapshot[%s @ %s, %d measurements]", snapshotId, timestamp,
        measurementValues.size());
  }
}
