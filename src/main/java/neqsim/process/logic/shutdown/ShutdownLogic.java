package neqsim.process.logic.shutdown;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.logic.LogicAction;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.ProcessLogic;

/**
 * Shutdown logic with controlled ramp-down of equipment.
 * 
 * <p>
 * Shutdown sequences gradually reduce equipment operation to prevent thermal shock, pressure
 * surges, or other process upsets. This follows industry best practices for safe process shutdown.
 * 
 * <p>
 * Key features:
 * <ul>
 * <li>Sequential action execution with timing</li>
 * <li>Rate-limited valve closure (gradual ramp-down)</li>
 * <li>Equipment cool-down periods</li>
 * <li>Configurable ramp rates</li>
 * <li>Emergency vs. controlled shutdown modes</li>
 * </ul>
 * 
 * <p>
 * Example usage:
 * 
 * <pre>
 * ShutdownLogic shutdown = new ShutdownLogic("Reactor Shutdown");
 * shutdown.setRampDownTime(600.0); // 10 minutes normal shutdown
 * 
 * // Add shutdown actions in sequence
 * shutdown.addAction(new ReduceFeedAction(feedValve, 50.0), 0.0); // Reduce to 50%
 * shutdown.addAction(new ReduceFeedAction(feedValve, 0.0), 60.0); // Close after 60s
 * shutdown.addAction(new StopHeaterAction(heater), 120.0); // Stop heater
 * shutdown.addAction(new ActivateCoolingAction(cooler), 180.0); // Start cooling
 * shutdown.addAction(new WaitForTempAction(reactor, 50.0), 300.0); // Wait to cool
 * shutdown.addAction(new StopAgitatorAction(agitator), 600.0); // Finally stop mixing
 * 
 * // Controlled shutdown
 * shutdown.activate();
 * while (!shutdown.isComplete()) {
 *   shutdown.execute(timeStep);
 * }
 * 
 * // Emergency shutdown (faster)
 * shutdown.setEmergencyMode(true);
 * shutdown.activate();
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class ShutdownLogic implements ProcessLogic {
  private final String name;
  private final List<ActionWithDelay> actions = new ArrayList<>();

  private LogicState state = LogicState.IDLE;
  private int currentActionIndex = 0;
  private double elapsedTime = 0.0;
  private double currentDelay = 0.0;

  private boolean emergencyMode = false;
  private double rampDownTime = 300.0; // Default 5 minutes for controlled shutdown
  private double emergencyShutdownTime = 30.0; // 30 seconds for emergency

  /**
   * Creates a shutdown logic sequence.
   *
   * @param name name of the shutdown sequence
   */
  public ShutdownLogic(String name) {
    this.name = name;
  }

  /**
   * Adds an action to the shutdown sequence.
   *
   * @param action action to execute
   * @param delay delay in seconds before executing (relative to shutdown start)
   */
  public void addAction(LogicAction action, double delay) {
    actions.add(new ActionWithDelay(action, delay));
  }

  /**
   * Sets whether this is an emergency shutdown (faster) or controlled shutdown.
   *
   * @param emergency true for emergency mode (accelerated timing)
   */
  public void setEmergencyMode(boolean emergency) {
    this.emergencyMode = emergency;
  }

  /**
   * Sets the total ramp-down time for controlled shutdown.
   *
   * @param rampTime time in seconds (default 300s)
   */
  public void setRampDownTime(double rampTime) {
    this.rampDownTime = rampTime;
  }

  /**
   * Sets the emergency shutdown time (faster than controlled).
   *
   * @param emergencyTime time in seconds (default 30s)
   */
  public void setEmergencyShutdownTime(double emergencyTime) {
    this.emergencyShutdownTime = emergencyTime;
  }

  /**
   * Gets the effective shutdown time based on mode.
   *
   * @return shutdown time in seconds
   */
  public double getEffectiveShutdownTime() {
    return emergencyMode ? emergencyShutdownTime : rampDownTime;
  }

  /**
   * Gets the configured ramp-down time.
   *
   * @return ramp-down time in seconds
   */
  public double getRampDownTime() {
    return rampDownTime;
  }

  /**
   * Gets the configured emergency shutdown time.
   *
   * @return emergency shutdown time in seconds
   */
  public double getEmergencyShutdownTime() {
    return emergencyShutdownTime;
  }

  @Override
  public void activate() {
    if (state == LogicState.IDLE || state == LogicState.COMPLETED || state == LogicState.FAILED) {
      state = LogicState.RUNNING;
      currentActionIndex = 0;
      elapsedTime = 0.0;
      currentDelay = 0.0;
    }
  }

  @Override
  public void deactivate() {
    if (state == LogicState.RUNNING) {
      state = LogicState.PAUSED;
    }
  }

  @Override
  public boolean reset() {
    state = LogicState.IDLE;
    currentActionIndex = 0;
    elapsedTime = 0.0;
    currentDelay = 0.0;
    emergencyMode = false;
    return true;
  }

  @Override
  public void execute(double timeStep) {
    if (state != LogicState.RUNNING) {
      return;
    }

    elapsedTime += timeStep;

    // Scale time if in emergency mode
    double effectiveTime =
        emergencyMode ? elapsedTime * (rampDownTime / emergencyShutdownTime) : elapsedTime;

    // Execute all actions that should have started by now
    for (int i = currentActionIndex; i < actions.size(); i++) {
      ActionWithDelay actionWithDelay = actions.get(i);

      if (effectiveTime >= actionWithDelay.delay) {
        // Time to execute this action
        actionWithDelay.action.execute();

        if (actionWithDelay.action.isComplete()) {
          currentActionIndex = i + 1;
        }
      } else {
        // Haven't reached this action's delay yet
        break;
      }
    }

    // Check if all actions complete
    if (currentActionIndex >= actions.size()) {
      boolean allComplete = true;
      for (ActionWithDelay actionWithDelay : actions) {
        if (!actionWithDelay.action.isComplete()) {
          allComplete = false;
          break;
        }
      }

      if (allComplete) {
        state = LogicState.COMPLETED;
      }
    }
  }

  /**
   * Checks if shutdown is in emergency mode.
   *
   * @return true if emergency shutdown
   */
  public boolean isEmergencyMode() {
    return emergencyMode;
  }

  /**
   * Gets the shutdown progress as a percentage.
   *
   * @return progress 0-100%
   */
  public double getProgress() {
    double targetTime = getEffectiveShutdownTime();
    return Math.min(100.0, 100.0 * elapsedTime / targetTime);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public LogicState getState() {
    return state;
  }

  @Override
  public boolean isActive() {
    return state == LogicState.RUNNING;
  }

  @Override
  public boolean isComplete() {
    return state == LogicState.COMPLETED;
  }

  @Override
  public List<ProcessEquipmentInterface> getTargetEquipment() {
    return new ArrayList<>();
  }

  @Override
  public String getStatusDescription() {
    StringBuilder sb = new StringBuilder();
    sb.append(name).append(" - ");

    if (emergencyMode) {
      sb.append("[EMERGENCY] ");
    }

    if (state == LogicState.IDLE) {
      sb.append("IDLE");
    } else if (state == LogicState.RUNNING) {
      sb.append(String.format("RUNNING (%.1f%% complete, %.1fs / %.1fs)", getProgress(),
          elapsedTime, getEffectiveShutdownTime()));

      // Show current action
      if (currentActionIndex < actions.size()) {
        ActionWithDelay current = actions.get(currentActionIndex);
        sb.append(String.format("\n  Current: %s", current.action.getDescription()));
      }

      // Show completed actions
      if (currentActionIndex > 0) {
        sb.append(
            String.format("\n  Completed: %d/%d actions", currentActionIndex, actions.size()));
      }
    } else if (state == LogicState.COMPLETED) {
      sb.append(String.format("COMPLETED (%.1fs)", elapsedTime));
    } else {
      sb.append(state.toString());
    }

    return sb.toString();
  }

  /**
   * Gets the number of actions in the sequence.
   *
   * @return action count
   */
  public int getActionCount() {
    return actions.size();
  }

  /**
   * Gets the number of completed actions.
   *
   * @return completed count
   */
  public int getCompletedActionCount() {
    return currentActionIndex;
  }

  /**
   * Internal class to store an action with its delay.
   */
  private static class ActionWithDelay {
    final LogicAction action;
    final double delay;

    ActionWithDelay(LogicAction action, double delay) {
      this.action = action;
      this.delay = delay;
    }
  }
}
