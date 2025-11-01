package neqsim.process.alarm;

import java.io.Serializable;

/**
 * Immutable snapshot of an alarm currently active in the system.
 */
public final class AlarmStatusSnapshot implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final String source;
  private final AlarmLevel level;
  private final boolean acknowledged;
  private final double value;
  private final double timestamp;

  public AlarmStatusSnapshot(String source, AlarmLevel level, boolean acknowledged, double value,
      double timestamp) {
    this.source = source;
    this.level = level;
    this.acknowledged = acknowledged;
    this.value = value;
    this.timestamp = timestamp;
  }

  public String getSource() {
    return source;
  }

  public AlarmLevel getLevel() {
    return level;
  }

  public boolean isAcknowledged() {
    return acknowledged;
  }

  public double getValue() {
    return value;
  }

  public double getTimestamp() {
    return timestamp;
  }
}
