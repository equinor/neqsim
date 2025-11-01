package neqsim.process.alarm;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents an alarm life-cycle event such as activation, acknowledgement or clearance.
 */
public final class AlarmEvent implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String source;
  private final AlarmLevel level;
  private final AlarmEventType type;
  private final double timestamp;
  private final double value;

  private AlarmEvent(String source, AlarmLevel level, AlarmEventType type, double timestamp,
      double value) {
    this.source = Objects.requireNonNull(source, "source");
    this.level = Objects.requireNonNull(level, "level");
    this.type = Objects.requireNonNull(type, "type");
    this.timestamp = timestamp;
    this.value = value;
  }

  /**
   * Creates an alarm activation event.
   *
   * @param source name of the originating measurement
   * @param level alarm level becoming active
   * @param timestamp simulation time of the event
   * @param value measured value triggering the event
   * @return activation event
   */
  public static AlarmEvent activated(String source, AlarmLevel level, double timestamp,
      double value) {
    return new AlarmEvent(source, level, AlarmEventType.ACTIVATED, timestamp, value);
  }

  /**
   * Creates an alarm clearance event.
   *
   * @param source name of the originating measurement
   * @param level alarm level being cleared
   * @param timestamp simulation time of the event
   * @param value measured value when the alarm cleared
   * @return clearance event
   */
  public static AlarmEvent cleared(String source, AlarmLevel level, double timestamp,
      double value) {
    return new AlarmEvent(source, level, AlarmEventType.CLEARED, timestamp, value);
  }

  /**
   * Creates an alarm acknowledgement event.
   *
   * @param source name of the originating measurement
   * @param level alarm level being acknowledged
   * @param timestamp simulation time of the acknowledgement
   * @param value measured value at acknowledgement time
   * @return acknowledgement event
   */
  public static AlarmEvent acknowledged(String source, AlarmLevel level, double timestamp,
      double value) {
    return new AlarmEvent(source, level, AlarmEventType.ACKNOWLEDGED, timestamp, value);
  }

  public String getSource() {
    return source;
  }

  public AlarmLevel getLevel() {
    return level;
  }

  public AlarmEventType getType() {
    return type;
  }

  public double getTimestamp() {
    return timestamp;
  }

  public double getValue() {
    return value;
  }

  @Override
  public String toString() {
    return "AlarmEvent{" + "source='" + source + '\'' + ", level=" + level + ", type=" + type
        + ", timestamp=" + timestamp + ", value=" + value + '}';
  }
}
