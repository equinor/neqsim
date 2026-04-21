package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.process.alarm.AlarmEvent;

/**
 * Immutable record of a process trip event.
 *
 * <p>
 * Captures the timestamp, initiating equipment, trip type, severity, and the sequence of alarm
 * events that led to or accompanied the trip. Instances are created via the {@link Builder}.
 * </p>
 *
 * <p>
 * TripEvent objects are produced by {@link TripEventDetector} during dynamic simulation and
 * consumed by {@link RootCauseAnalyzer} for hypothesis evaluation.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public final class TripEvent implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private final String eventId;
  private final double timestamp;
  private final String initiatingEquipment;
  private final TripType tripType;
  private final Severity severity;
  private final String description;
  private final List<AlarmEvent> associatedAlarms;
  private final Map<String, Double> equipmentValues;

  /**
   * Trip event severity levels.
   */
  public enum Severity {
    /** Low severity — single equipment affected. */
    LOW("Low"),
    /** Medium severity — multiple equipment affected. */
    MEDIUM("Medium"),
    /** High severity — plant-wide impact. */
    HIGH("High"),
    /** Critical severity — safety system activated. */
    CRITICAL("Critical");

    private final String displayName;

    /**
     * Constructs a Severity enum constant.
     *
     * @param displayName human-readable name
     */
    Severity(String displayName) {
      this.displayName = displayName;
    }

    /**
     * Returns the display name.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }

  /**
   * Private constructor — use {@link Builder}.
   *
   * @param builder the builder with populated fields
   */
  private TripEvent(Builder builder) {
    this.eventId = builder.eventId;
    this.timestamp = builder.timestamp;
    this.initiatingEquipment = builder.initiatingEquipment;
    this.tripType = builder.tripType;
    this.severity = builder.severity;
    this.description = builder.description;
    this.associatedAlarms = Collections.unmodifiableList(new ArrayList<>(builder.associatedAlarms));
    this.equipmentValues =
        Collections.unmodifiableMap(new LinkedHashMap<>(builder.equipmentValues));
  }

  /**
   * Returns a unique event identifier.
   *
   * @return event ID string
   */
  public String getEventId() {
    return eventId;
  }

  /**
   * Returns the simulation time (seconds) at which the trip occurred.
   *
   * @return timestamp in seconds
   */
  public double getTimestamp() {
    return timestamp;
  }

  /**
   * Returns the name of the equipment that initiated the trip.
   *
   * @return equipment name
   */
  public String getInitiatingEquipment() {
    return initiatingEquipment;
  }

  /**
   * Returns the trip type category.
   *
   * @return trip type enum value
   */
  public TripType getTripType() {
    return tripType;
  }

  /**
   * Returns the severity of the trip event.
   *
   * @return severity enum value
   */
  public Severity getSeverity() {
    return severity;
  }

  /**
   * Returns a human-readable description of the trip.
   *
   * @return description string
   */
  public String getDescription() {
    return description;
  }

  /**
   * Returns an unmodifiable list of alarm events associated with this trip.
   *
   * @return list of alarm events
   */
  public List<AlarmEvent> getAssociatedAlarms() {
    return associatedAlarms;
  }

  /**
   * Returns an unmodifiable map of key equipment variable values at the time of trip.
   *
   * <p>
   * Keys are automation-style addresses (e.g. "Compressor.surgeMargin"), values are the readings at
   * trip time.
   * </p>
   *
   * @return map of variable addresses to values
   */
  public Map<String, Double> getEquipmentValues() {
    return equipmentValues;
  }

  /**
   * Serialises this trip event to a JSON string.
   *
   * @return JSON representation
   */
  public String toJson() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("eventId", eventId);
    map.put("timestamp", timestamp);
    map.put("initiatingEquipment", initiatingEquipment);
    map.put("tripType", tripType.name());
    map.put("severity", severity.name());
    map.put("description", description);
    map.put("alarmCount", associatedAlarms.size());
    map.put("equipmentValues", equipmentValues);
    return GSON.toJson(map);
  }

  @Override
  public String toString() {
    return "TripEvent{id=" + eventId + ", t=" + timestamp + "s, equipment='" + initiatingEquipment
        + "', type=" + tripType + ", severity=" + severity + "}";
  }

  /**
   * Builder for constructing {@link TripEvent} instances.
   *
   * @author esol
   * @version 1.0
   */
  public static class Builder {
    private String eventId;
    private double timestamp;
    private String initiatingEquipment = "";
    private TripType tripType = TripType.UNKNOWN;
    private Severity severity = Severity.MEDIUM;
    private String description = "";
    private final List<AlarmEvent> associatedAlarms = new ArrayList<>();
    private final Map<String, Double> equipmentValues = new LinkedHashMap<>();

    /**
     * Creates a new Builder with a generated event ID.
     */
    public Builder() {
      this.eventId = "TRIP-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 1000);
    }

    /**
     * Sets the event ID.
     *
     * @param eventId unique identifier
     * @return this builder
     */
    public Builder eventId(String eventId) {
      this.eventId = Objects.requireNonNull(eventId, "eventId");
      return this;
    }

    /**
     * Sets the simulation timestamp in seconds.
     *
     * @param timestamp simulation time in seconds
     * @return this builder
     */
    public Builder timestamp(double timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    /**
     * Sets the name of the equipment that initiated the trip.
     *
     * @param equipment equipment name
     * @return this builder
     */
    public Builder initiatingEquipment(String equipment) {
      this.initiatingEquipment = Objects.requireNonNull(equipment, "equipment");
      return this;
    }

    /**
     * Sets the trip type category.
     *
     * @param tripType the trip type
     * @return this builder
     */
    public Builder tripType(TripType tripType) {
      this.tripType = Objects.requireNonNull(tripType, "tripType");
      return this;
    }

    /**
     * Sets the severity level.
     *
     * @param severity severity level
     * @return this builder
     */
    public Builder severity(Severity severity) {
      this.severity = Objects.requireNonNull(severity, "severity");
      return this;
    }

    /**
     * Sets the description.
     *
     * @param description human-readable description
     * @return this builder
     */
    public Builder description(String description) {
      this.description = Objects.requireNonNull(description, "description");
      return this;
    }

    /**
     * Adds an associated alarm event.
     *
     * @param alarm the alarm event
     * @return this builder
     */
    public Builder addAlarm(AlarmEvent alarm) {
      this.associatedAlarms.add(Objects.requireNonNull(alarm, "alarm"));
      return this;
    }

    /**
     * Adds all alarm events from the given list.
     *
     * @param alarms list of alarm events
     * @return this builder
     */
    public Builder addAlarms(List<AlarmEvent> alarms) {
      for (AlarmEvent a : alarms) {
        addAlarm(a);
      }
      return this;
    }

    /**
     * Records an equipment variable value at trip time.
     *
     * @param address automation-style variable address
     * @param value the value at trip time
     * @return this builder
     */
    public Builder addEquipmentValue(String address, double value) {
      this.equipmentValues.put(Objects.requireNonNull(address, "address"), value);
      return this;
    }

    /**
     * Builds an immutable {@link TripEvent}.
     *
     * @return a new TripEvent instance
     */
    public TripEvent build() {
      return new TripEvent(this);
    }
  }
}
