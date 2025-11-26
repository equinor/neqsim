package neqsim.process.alarm;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable alarm state tracking activation, acknowledgement and pending transitions.
 */
public class AlarmState implements Serializable {
  private static final long serialVersionUID = 1000L;

  private AlarmLevel activeLevel;
  private boolean acknowledged;
  private AlarmLevel pendingLevel;
  private double pendingTimer;
  private double lastValue = Double.NaN;
  private double lastUpdateTime = Double.NaN;

  /**
   * Evaluates the alarm state using the supplied configuration and measurement.
   *
   * @param config alarm configuration
   * @param value measured value
   * @param dt simulation time step
   * @param currentTime simulation time of the evaluation
   * @param source originating measurement name
   * @return list of events produced during the evaluation
   */
  public List<AlarmEvent> evaluate(AlarmConfig config, double value, double dt, double currentTime,
      String source) {
    updateLast(value, currentTime);

    if (config == null) {
      reset();
      return Collections.emptyList();
    }

    List<AlarmEvent> events = new ArrayList<>();
    AlarmLevel candidate = determineCandidate(config, value);

    if (candidate == null) {
      pendingLevel = null;
      pendingTimer = 0.0;
      if (activeLevel != null && isClearedByValue(config, value, activeLevel)) {
        events.add(AlarmEvent.cleared(source, activeLevel, currentTime, value));
        clearActiveInternal();
      }
      return events;
    }

    if (activeLevel == null) {
      handlePending(config, candidate, dt, currentTime, source, events);
      return events;
    }

    if (candidate == activeLevel) {
      pendingLevel = null;
      pendingTimer = 0.0;
      return events;
    }

    int comparison = Integer.compare(candidate.getPriority(), activeLevel.getPriority());
    if (comparison > 0) {
      // Escalating to a more severe level
      handlePending(config, candidate, dt, currentTime, source, events);
    } else if (comparison < 0) {
      // Attempting to transition to a lower severity
      if (isClearedByValue(config, value, activeLevel)) {
        events.add(AlarmEvent.cleared(source, activeLevel, currentTime, value));
        clearActiveInternal();
        handlePending(config, candidate, dt, currentTime, source, events);
      } else {
        pendingLevel = null;
        pendingTimer = 0.0;
      }
    } else {
      pendingLevel = null;
      pendingTimer = 0.0;
    }

    return events;
  }

  private void handlePending(AlarmConfig config, AlarmLevel candidate, double dt, double currentTime,
      String source, List<AlarmEvent> events) {
    if (pendingLevel != candidate) {
      pendingLevel = candidate;
      pendingTimer = 0.0;
    }
    pendingTimer += Math.max(0.0, dt);
    if (pendingTimer >= config.getDelay()) {
      if (activeLevel != null) {
        events.add(AlarmEvent.cleared(source, activeLevel, currentTime, lastValue));
      }
      activeLevel = candidate;
      acknowledged = false;
      events.add(AlarmEvent.activated(source, candidate, currentTime, lastValue));
      pendingLevel = null;
      pendingTimer = 0.0;
    }
  }

  private AlarmLevel determineCandidate(AlarmConfig config, double value) {
    if (config.getHighHighLimit() != null && value >= config.getHighHighLimit()) {
      return AlarmLevel.HIHI;
    }
    if (config.getHighLimit() != null && value >= config.getHighLimit()) {
      return AlarmLevel.HI;
    }
    if (config.getLowLowLimit() != null && value <= config.getLowLowLimit()) {
      return AlarmLevel.LOLO;
    }
    if (config.getLowLimit() != null && value <= config.getLowLimit()) {
      return AlarmLevel.LO;
    }
    return null;
  }

  private boolean isClearedByValue(AlarmConfig config, double value, AlarmLevel level) {
    double deadband = config.getDeadband();
    switch (level) {
      case HIHI:
        return config.getHighHighLimit() == null || value <= config.getHighHighLimit() - deadband;
      case HI:
        return config.getHighLimit() == null || value <= config.getHighLimit() - deadband;
      case LOLO:
        return config.getLowLowLimit() == null || value >= config.getLowLowLimit() + deadband;
      case LO:
        return config.getLowLimit() == null || value >= config.getLowLimit() + deadband;
      default:
        return true;
    }
  }

  private void clearActiveInternal() {
    activeLevel = null;
    acknowledged = false;
    pendingLevel = null;
    pendingTimer = 0.0;
  }

  private void updateLast(double value, double time) {
    lastValue = value;
    lastUpdateTime = time;
  }

  /**
   * Resets the alarm state to its initial normal condition.
   */
  public void reset() {
    clearActiveInternal();
    lastValue = Double.NaN;
    lastUpdateTime = Double.NaN;
  }

  /**
   * Returns the currently active alarm level, or {@code null} if no alarm is active.
   *
   * @return active alarm level
   */
  public AlarmLevel getActiveLevel() {
    return activeLevel;
  }

  /**
   * Indicates whether an alarm is currently active.
   *
   * @return {@code true} if an alarm is active
   */
  public boolean isActive() {
    return activeLevel != null;
  }

  /**
   * Indicates whether the active alarm has been acknowledged.
   *
   * @return {@code true} if the alarm is acknowledged
   */
  public boolean isAcknowledged() {
    return acknowledged;
  }

  /**
   * Returns the last measured value supplied to {@link #evaluate(AlarmConfig, double, double, double, String)}.
   *
   * @return last measured value
   */
  public double getLastValue() {
    return lastValue;
  }

  /**
   * Returns the simulation time of the last evaluation.
   *
   * @return time of last update
   */
  public double getLastUpdateTime() {
    return lastUpdateTime;
  }

  /**
   * Acknowledges the active alarm if one exists.
   *
   * @param source name of the originating measurement
   * @param currentTime simulation time
   * @return acknowledgement event, or {@code null} if nothing was acknowledged
   */
  public AlarmEvent acknowledge(String source, double currentTime) {
    if (activeLevel != null && !acknowledged) {
      acknowledged = true;
      return AlarmEvent.acknowledged(source, activeLevel, currentTime, lastValue);
    }
    return null;
  }

  /**
   * Creates a snapshot description of the currently active alarm, or {@code null} if there is no
   * active alarm.
   *
   * @param source name of originating measurement
   * @return snapshot or {@code null}
   */
  public AlarmStatusSnapshot snapshot(String source) {
    if (!isActive()) {
      return null;
    }
    return new AlarmStatusSnapshot(source, activeLevel, acknowledged, lastValue, lastUpdateTime);
  }
}
