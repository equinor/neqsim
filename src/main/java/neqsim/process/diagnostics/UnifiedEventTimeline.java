package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Chronological timeline merging alarm events, equipment state changes, controller actions, and
 * measurement readings into a single queryable sequence.
 *
 * <p>
 * The timeline is built from events collected during dynamic simulation and provides methods to
 * query events around a timestamp, filter by equipment or event type, and extract escalation
 * chains.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class UnifiedEventTimeline implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private final List<TimelineEntry> entries = new ArrayList<>();

  /**
   * Category of timeline entry.
   */
  public enum EntryType {
    /** Alarm activated or cleared. */
    ALARM,
    /** Equipment operating state changed. */
    STATE_CHANGE,
    /** Controller output changed or mode changed. */
    CONTROLLER_ACTION,
    /** Valve position changed. */
    VALVE_ACTION,
    /** Measurement reading recorded. */
    MEASUREMENT,
    /** Trip event detected. */
    TRIP
  }

  /**
   * A single entry in the unified timeline.
   *
   * @author esol
   * @version 1.0
   */
  public static class TimelineEntry implements Serializable, Comparable<TimelineEntry> {
    private static final long serialVersionUID = 1000L;

    private final double timestamp;
    private final EntryType type;
    private final String equipmentName;
    private final String description;
    private final Map<String, Object> properties;

    /**
     * Constructs a timeline entry.
     *
     * @param timestamp simulation time in seconds
     * @param type entry type category
     * @param equipmentName name of the source equipment
     * @param description human-readable description
     */
    public TimelineEntry(double timestamp, EntryType type, String equipmentName,
        String description) {
      this.timestamp = timestamp;
      this.type = type;
      this.equipmentName = equipmentName;
      this.description = description;
      this.properties = new LinkedHashMap<>();
    }

    /**
     * Returns the simulation timestamp.
     *
     * @return timestamp in seconds
     */
    public double getTimestamp() {
      return timestamp;
    }

    /**
     * Returns the entry type.
     *
     * @return entry type enum value
     */
    public EntryType getType() {
      return type;
    }

    /**
     * Returns the name of the associated equipment.
     *
     * @return equipment name
     */
    public String getEquipmentName() {
      return equipmentName;
    }

    /**
     * Returns the description of this event.
     *
     * @return description text
     */
    public String getDescription() {
      return description;
    }

    /**
     * Returns the properties map for additional data.
     *
     * @return mutable properties map
     */
    public Map<String, Object> getProperties() {
      return properties;
    }

    /**
     * Sets a property value.
     *
     * @param key property key
     * @param value property value
     * @return this entry for chaining
     */
    public TimelineEntry setProperty(String key, Object value) {
      properties.put(key, value);
      return this;
    }

    @Override
    public int compareTo(TimelineEntry other) {
      return Double.compare(this.timestamp, other.timestamp);
    }

    @Override
    public String toString() {
      return String.format("[%.2fs] %s %s: %s", timestamp, type, equipmentName, description);
    }
  }

  /**
   * Constructs an empty timeline.
   */
  public UnifiedEventTimeline() {}

  /**
   * Adds a new entry to the timeline.
   *
   * @param entry the timeline entry to add
   */
  public void addEntry(TimelineEntry entry) {
    entries.add(entry);
  }

  /**
   * Convenience method to create and add an alarm entry.
   *
   * @param timestamp simulation time in seconds
   * @param equipmentName name of alarm source equipment
   * @param description alarm description
   * @return the created entry
   */
  public TimelineEntry addAlarm(double timestamp, String equipmentName, String description) {
    TimelineEntry entry = new TimelineEntry(timestamp, EntryType.ALARM, equipmentName, description);
    entries.add(entry);
    return entry;
  }

  /**
   * Convenience method to create and add a state change entry.
   *
   * @param timestamp simulation time in seconds
   * @param equipmentName name of equipment
   * @param description state change description
   * @return the created entry
   */
  public TimelineEntry addStateChange(double timestamp, String equipmentName, String description) {
    TimelineEntry entry =
        new TimelineEntry(timestamp, EntryType.STATE_CHANGE, equipmentName, description);
    entries.add(entry);
    return entry;
  }

  /**
   * Convenience method to create and add a valve action entry.
   *
   * @param timestamp simulation time in seconds
   * @param equipmentName name of valve
   * @param description action description
   * @return the created entry
   */
  public TimelineEntry addValveAction(double timestamp, String equipmentName, String description) {
    TimelineEntry entry =
        new TimelineEntry(timestamp, EntryType.VALVE_ACTION, equipmentName, description);
    entries.add(entry);
    return entry;
  }

  /**
   * Convenience method to create and add a trip entry.
   *
   * @param timestamp simulation time in seconds
   * @param equipmentName name of tripped equipment
   * @param description trip description
   * @return the created entry
   */
  public TimelineEntry addTrip(double timestamp, String equipmentName, String description) {
    TimelineEntry entry = new TimelineEntry(timestamp, EntryType.TRIP, equipmentName, description);
    entries.add(entry);
    return entry;
  }

  /**
   * Returns a sorted copy of all timeline entries.
   *
   * @return sorted list of entries (earliest first)
   */
  public List<TimelineEntry> getSortedEntries() {
    List<TimelineEntry> sorted = new ArrayList<>(entries);
    Collections.sort(sorted);
    return sorted;
  }

  /**
   * Returns the total number of entries.
   *
   * @return entry count
   */
  public int size() {
    return entries.size();
  }

  /**
   * Returns all entries within a time window around a reference timestamp.
   *
   * @param timestamp reference time in seconds
   * @param windowSeconds half-window size in seconds (entries within +/- this range)
   * @return sorted list of entries within the window
   */
  public List<TimelineEntry> getEventsAround(double timestamp, double windowSeconds) {
    double start = timestamp - windowSeconds;
    double end = timestamp + windowSeconds;
    List<TimelineEntry> result = new ArrayList<>();
    for (TimelineEntry entry : entries) {
      if (entry.getTimestamp() >= start && entry.getTimestamp() <= end) {
        result.add(entry);
      }
    }
    Collections.sort(result);
    return result;
  }

  /**
   * Returns all entries for a specific equipment name.
   *
   * @param equipmentName the equipment name to filter by
   * @return sorted list of entries for that equipment
   */
  public List<TimelineEntry> getEventsForEquipment(String equipmentName) {
    List<TimelineEntry> result = new ArrayList<>();
    for (TimelineEntry entry : entries) {
      if (entry.getEquipmentName().equals(equipmentName)) {
        result.add(entry);
      }
    }
    Collections.sort(result);
    return result;
  }

  /**
   * Returns all entries of a specific type.
   *
   * @param type the entry type to filter by
   * @return sorted list of entries of that type
   */
  public List<TimelineEntry> getEventsByType(EntryType type) {
    List<TimelineEntry> result = new ArrayList<>();
    for (TimelineEntry entry : entries) {
      if (entry.getType() == type) {
        result.add(entry);
      }
    }
    Collections.sort(result);
    return result;
  }

  /**
   * Extracts the escalation chain — the sequence of events from the first anomaly (alarm or state
   * change) up to the trip, ordered chronologically.
   *
   * <p>
   * This is useful for understanding how an initial upset propagated through the process to cause
   * the trip.
   * </p>
   *
   * @return sorted list of non-measurement entries, or empty list if no trip found
   */
  public List<TimelineEntry> getEscalationChain() {
    List<TimelineEntry> sorted = getSortedEntries();
    // Find the last TRIP entry
    TimelineEntry tripEntry = null;
    for (int i = sorted.size() - 1; i >= 0; i--) {
      if (sorted.get(i).getType() == EntryType.TRIP) {
        tripEntry = sorted.get(i);
        break;
      }
    }
    if (tripEntry == null) {
      return Collections.emptyList();
    }

    // Collect all non-measurement events up to and including the trip
    List<TimelineEntry> chain = new ArrayList<>();
    for (TimelineEntry entry : sorted) {
      if (entry.getTimestamp() > tripEntry.getTimestamp()) {
        break;
      }
      if (entry.getType() != EntryType.MEASUREMENT) {
        chain.add(entry);
      }
    }
    return chain;
  }

  /**
   * Serialises the timeline to a JSON string.
   *
   * @return JSON representation of the sorted timeline
   */
  public String toJson() {
    List<TimelineEntry> sorted = getSortedEntries();
    List<Map<String, Object>> list = new ArrayList<>();
    for (TimelineEntry entry : sorted) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("timestamp", entry.getTimestamp());
      map.put("type", entry.getType().name());
      map.put("equipment", entry.getEquipmentName());
      map.put("description", entry.getDescription());
      if (!entry.getProperties().isEmpty()) {
        map.put("properties", entry.getProperties());
      }
      list.add(map);
    }
    return GSON.toJson(list);
  }

  @Override
  public String toString() {
    return "UnifiedEventTimeline{entries=" + entries.size() + "}";
  }
}
