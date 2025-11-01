package neqsim.process.alarm;

/**
 * Type of event produced during alarm evaluation.
 */
public enum AlarmEventType {
  /** Alarm becomes active. */
  ACTIVATED,
  /** Alarm returns to normal. */
  CLEARED,
  /** Alarm was acknowledged by an operator or automation logic. */
  ACKNOWLEDGED;
}
