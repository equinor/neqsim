package neqsim.process.alarm;

/**
 * Enumerates the discrete alarm levels used when evaluating measurement values.
 */
public enum AlarmLevel {
  /** Low-low alarm limit exceeded. */
  LOLO(-2, Direction.LOW),
  /** Low alarm limit exceeded. */
  LO(-1, Direction.LOW),
  /** High alarm limit exceeded. */
  HI(1, Direction.HIGH),
  /** High-high alarm limit exceeded. */
  HIHI(2, Direction.HIGH);

  /** Direction of the alarm (high or low). */
  public enum Direction {
    /** Alarm is triggered when the value becomes too low. */
    LOW,
    /** Alarm is triggered when the value becomes too high. */
    HIGH;
  }

  private final int priority;
  private final Direction direction;

  AlarmLevel(int priority, Direction direction) {
    this.priority = priority;
    this.direction = direction;
  }

  /**
   * Returns a relative priority used when comparing alarm severities.
   *
   * @return priority where higher magnitude indicates a more severe alarm
   */
  public int getPriority() {
    return priority;
  }

  /**
   * Returns the direction of the alarm.
   *
   * @return HIGH if the alarm is triggered by high values, otherwise LOW
   */
  public Direction getDirection() {
    return direction;
  }
}
