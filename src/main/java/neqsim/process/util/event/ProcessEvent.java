package neqsim.process.util.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents an event in the process simulation.
 *
 * <p>
 * Events can represent state changes, alarms, threshold crossings, or any significant occurrence
 * that external systems may want to react to.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ProcessEvent implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String eventId;
  private final EventType type;
  private final String source;
  private final Instant timestamp;
  private final String description;
  private final Map<String, Object> properties;
  private final Severity severity;

  /**
   * Event types.
   */
  public enum EventType {
    /** Value crossed a threshold */
    THRESHOLD_CROSSED,
    /** Equipment state changed */
    STATE_CHANGE,
    /** Alarm triggered */
    ALARM,
    /** Calibration event */
    CALIBRATION,
    /** Simulation completed */
    SIMULATION_COMPLETE,
    /** Error occurred */
    ERROR,
    /** Warning condition */
    WARNING,
    /** Informational event */
    INFO,
    /** Measurement updated */
    MEASUREMENT_UPDATE,
    /** Model deviation detected */
    MODEL_DEVIATION
  }

  /**
   * Event severity levels.
   */
  public enum Severity {
    DEBUG, INFO, WARNING, ERROR, CRITICAL
  }

  /**
   * Creates a process event.
   *
   * @param eventId unique event identifier
   * @param type event type
   * @param source source equipment/component name
   * @param description human-readable description
   * @param severity event severity
   */
  public ProcessEvent(String eventId, EventType type, String source, String description,
      Severity severity) {
    this.eventId = eventId;
    this.type = type;
    this.source = source;
    this.timestamp = Instant.now();
    this.description = description;
    this.severity = severity;
    this.properties = new HashMap<>();
  }

  /**
   * Creates an info event.
   *
   * @param source source name
   * @param description description
   * @return info event
   */
  public static ProcessEvent info(String source, String description) {
    return new ProcessEvent(generateId(), EventType.INFO, source, description, Severity.INFO);
  }

  /**
   * Creates a warning event.
   *
   * @param source source name
   * @param description description
   * @return warning event
   */
  public static ProcessEvent warning(String source, String description) {
    return new ProcessEvent(generateId(), EventType.WARNING, source, description, Severity.WARNING);
  }

  /**
   * Creates an alarm event.
   *
   * @param source source name
   * @param description description
   * @return alarm event
   */
  public static ProcessEvent alarm(String source, String description) {
    return new ProcessEvent(generateId(), EventType.ALARM, source, description, Severity.ERROR);
  }

  /**
   * Creates a threshold crossed event.
   *
   * @param source source name
   * @param variable variable name
   * @param value current value
   * @param threshold threshold value
   * @param above true if crossed above threshold
   * @return threshold event
   */
  public static ProcessEvent thresholdCrossed(String source, String variable, double value,
      double threshold, boolean above) {
    ProcessEvent event =
        new ProcessEvent(generateId(), EventType.THRESHOLD_CROSSED, source,
            String.format("%s %s threshold: %.4f %s %.4f", variable,
                above ? "exceeded" : "fell below", value, above ? ">" : "<", threshold),
            above ? Severity.WARNING : Severity.INFO);
    event.setProperty("variable", variable);
    event.setProperty("value", value);
    event.setProperty("threshold", threshold);
    event.setProperty("above", above);
    return event;
  }

  /**
   * Creates a model deviation event.
   *
   * @param source source name
   * @param variable variable name
   * @param measured measured value
   * @param predicted predicted value
   * @return deviation event
   */
  public static ProcessEvent modelDeviation(String source, String variable, double measured,
      double predicted) {
    double deviation =
        (Math.abs(measured) > 1e-10) ? Math.abs(measured - predicted) / Math.abs(measured) : 0;
    ProcessEvent event = new ProcessEvent(generateId(), EventType.MODEL_DEVIATION, source,
        String.format("Model deviation for %s: %.2f%% (measured=%.4f, predicted=%.4f)", variable,
            deviation * 100, measured, predicted),
        deviation > 0.1 ? Severity.WARNING : Severity.INFO);
    event.setProperty("variable", variable);
    event.setProperty("measured", measured);
    event.setProperty("predicted", predicted);
    event.setProperty("deviation", deviation);
    return event;
  }

  private static String generateId() {
    return "EVT-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 1000);
  }

  /**
   * Sets a custom property on the event.
   *
   * @param key property key
   * @param value property value
   * @return this event for chaining
   */
  public ProcessEvent setProperty(String key, Object value) {
    properties.put(key, value);
    return this;
  }

  /**
   * Gets a property value.
   *
   * @param key property key
   * @return value or null
   */
  public Object getProperty(String key) {
    return properties.get(key);
  }

  /**
   * Gets a typed property value.
   *
   * @param <T> value type
   * @param key property key
   * @param type value class
   * @return typed value or null
   */
  @SuppressWarnings("unchecked")
  public <T> T getProperty(String key, Class<T> type) {
    Object value = properties.get(key);
    if (value != null && type.isInstance(value)) {
      return (T) value;
    }
    return null;
  }

  /**
   * Gets the event ID.
   *
   * @return event ID
   */
  public String getEventId() {
    return eventId;
  }

  /**
   * Gets the event type.
   *
   * @return event type
   */
  public EventType getType() {
    return type;
  }

  /**
   * Gets the source name.
   *
   * @return source
   */
  public String getSource() {
    return source;
  }

  /**
   * Gets the event timestamp.
   *
   * @return timestamp
   */
  public Instant getTimestamp() {
    return timestamp;
  }

  /**
   * Gets the description.
   *
   * @return description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Gets the severity.
   *
   * @return severity level
   */
  public Severity getSeverity() {
    return severity;
  }

  /**
   * Gets all properties.
   *
   * @return properties map
   */
  public Map<String, Object> getProperties() {
    return new HashMap<>(properties);
  }

  @Override
  public String toString() {
    return String.format("[%s] %s %s: %s (%s)", timestamp, severity, type, description, source);
  }
}
