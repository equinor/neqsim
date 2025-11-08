package neqsim.process.logic.startup;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.logic.LogicAction;
import neqsim.process.logic.LogicCondition;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.ProcessLogic;

/**
 * Startup logic with permissive checks and sequential action execution.
 * 
 * <p>
 * Startup sequences verify that required conditions (permissives) are met before proceeding with
 * equipment startup. This follows industry best practices for safe process startup.
 * 
 * <p>
 * Key features:
 * <ul>
 * <li>Permissive checks (temperature, pressure, level, etc.)</li>
 * <li>Sequential action execution with timing</li>
 * <li>Automatic abort if permissives lost during startup</li>
 * <li>Configurable timeout for permissive waiting</li>
 * <li>Detailed status reporting</li>
 * </ul>
 * 
 * <p>
 * Example usage:
 * 
 * <pre>
 * StartupLogic startup = new StartupLogic("Compressor Startup");
 * 
 * // Add permissives (must all be true before starting)
 * startup.addPermissive(new TemperatureCondition(cooler, 50.0, "<")); // Cooled
 * startup.addPermissive(new PressureCondition(suction, 3.0, ">")); // Min pressure
 * startup.addPermissive(new TimerCondition(60.0)); // Min 60s warm-up
 * 
 * // Add startup actions
 * startup.addAction(new OpenValveAction(suctionValve), 0.0); // Immediate
 * startup.addAction(new StartPumpAction(lubePump), 2.0); // After 2s
 * startup.addAction(new StartCompressorAction(compressor), 10.0); // After 10s
 * 
 * // In control loop
 * startup.activate();
 * while (!startup.isComplete()) {
 *   startup.execute(timeStep);
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class StartupLogic implements ProcessLogic {
  private final String name;
  private final List<LogicCondition> permissives = new ArrayList<>();
  private final List<ActionWithDelay> actions = new ArrayList<>();

  private LogicState state = LogicState.IDLE;
  private int currentActionIndex = 0;
  private double elapsedTime = 0.0;
  private double currentDelay = 0.0;
  private double permissiveWaitTime = 0.0;
  private double maxPermissiveWaitTime = 300.0; // 5 minutes default timeout

  private boolean aborted = false;
  private String abortReason = "";

  /**
   * Creates a startup logic sequence.
   *
   * @param name name of the startup sequence
   */
  public StartupLogic(String name) {
    this.name = name;
  }

  /**
   * Adds a permissive condition that must be met before startup can proceed.
   * 
   * <p>
   * All permissives must be true simultaneously before the first action executes.
   * </p>
   *
   * @param permissive condition to check
   */
  public void addPermissive(LogicCondition permissive) {
    permissives.add(permissive);
  }

  /**
   * Adds an action to the startup sequence.
   *
   * @param action action to execute
   * @param delay delay in seconds before executing (relative to previous action completion)
   */
  public void addAction(LogicAction action, double delay) {
    actions.add(new ActionWithDelay(action, delay));
  }

  /**
   * Sets the maximum time to wait for permissives to be met.
   *
   * @param timeout timeout in seconds (default 300s)
   */
  public void setPermissiveTimeout(double timeout) {
    this.maxPermissiveWaitTime = timeout;
  }

  @Override
  public void activate() {
    if (state == LogicState.IDLE || state == LogicState.COMPLETED || state == LogicState.FAILED) {
      state = LogicState.WAITING_PERMISSIVES;
      currentActionIndex = 0;
      elapsedTime = 0.0;
      currentDelay = 0.0;
      permissiveWaitTime = 0.0;
      aborted = false;
      abortReason = "";
    }
  }

  @Override
  public void deactivate() {
    if (state != LogicState.IDLE && state != LogicState.COMPLETED) {
      state = LogicState.PAUSED;
    }
  }

  @Override
  public boolean reset() {
    state = LogicState.IDLE;
    currentActionIndex = 0;
    elapsedTime = 0.0;
    currentDelay = 0.0;
    permissiveWaitTime = 0.0;
    aborted = false;
    abortReason = "";
    return true;
  }

  @Override
  public void execute(double timeStep) {
    if (state == LogicState.WAITING_PERMISSIVES) {
      executePermissiveWait(timeStep);
    } else if (state == LogicState.RUNNING) {
      executeActions(timeStep);
    }
  }

  private void executePermissiveWait(double timeStep) {
    permissiveWaitTime += timeStep;
    elapsedTime += timeStep;

    // Update timer conditions
    for (LogicCondition permissive : permissives) {
      if (permissive instanceof neqsim.process.logic.condition.TimerCondition) {
        ((neqsim.process.logic.condition.TimerCondition) permissive).update(timeStep);
      }
    }

    // Check if all permissives are met
    boolean allMet = true;
    for (LogicCondition permissive : permissives) {
      if (!permissive.evaluate()) {
        allMet = false;
        break;
      }
    }

    if (allMet) {
      // All permissives met - proceed to action execution
      state = LogicState.RUNNING;
      permissiveWaitTime = 0.0;
    } else if (permissiveWaitTime >= maxPermissiveWaitTime) {
      // Timeout waiting for permissives
      state = LogicState.FAILED;
      aborted = true;
      abortReason = "Timeout waiting for permissives";
    }
  }

  private void executeActions(double timeStep) {
    elapsedTime += timeStep;

    // Check if permissives are still met (abort if lost)
    for (LogicCondition permissive : permissives) {
      if (!permissive.evaluate()) {
        state = LogicState.FAILED;
        aborted = true;
        abortReason = "Permissive lost: " + permissive.getDescription();
        return;
      }
    }

    // Process current action
    if (currentActionIndex < actions.size()) {
      ActionWithDelay currentActionWithDelay = actions.get(currentActionIndex);

      // Wait for delay
      if (currentDelay < currentActionWithDelay.delay) {
        currentDelay += timeStep;
        return;
      }

      // Execute action
      currentActionWithDelay.action.execute();

      // Check if action is complete
      if (currentActionWithDelay.action.isComplete()) {
        currentActionIndex++;
        currentDelay = 0.0;

        if (currentActionIndex >= actions.size()) {
          state = LogicState.COMPLETED;
        }
      }
    } else {
      state = LogicState.COMPLETED;
    }
  }

  /**
   * Checks if startup was aborted.
   *
   * @return true if aborted
   */
  public boolean isAborted() {
    return aborted;
  }

  /**
   * Gets the reason for abort.
   *
   * @return abort reason, or empty string if not aborted
   */
  public String getAbortReason() {
    return abortReason;
  }

  /**
   * Gets the time spent waiting for permissives.
   *
   * @return wait time in seconds
   */
  public double getPermissiveWaitTime() {
    return permissiveWaitTime;
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
    return state == LogicState.RUNNING || state == LogicState.WAITING_PERMISSIVES;
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

    if (state == LogicState.IDLE) {
      sb.append("IDLE");
    } else if (state == LogicState.WAITING_PERMISSIVES) {
      sb.append(String.format("WAITING FOR PERMISSIVES (%.1fs / %.1fs)", permissiveWaitTime,
          maxPermissiveWaitTime));

      // Show which permissives are not met
      sb.append("\n  Permissives:");
      for (LogicCondition permissive : permissives) {
        boolean met = permissive.evaluate();
        sb.append(String.format("\n    %s %s: %s (current: %s)", met ? "✓" : "✗",
            permissive.getDescription(), met ? "MET" : "NOT MET", permissive.getCurrentValue()));
      }
    } else if (state == LogicState.RUNNING) {
      if (currentActionIndex < actions.size()) {
        ActionWithDelay current = actions.get(currentActionIndex);
        sb.append(String.format("RUNNING (Step %d/%d: %s, delay: %.1fs)", currentActionIndex + 1,
            actions.size(), current.action.getDescription(),
            Math.max(0, current.delay - currentDelay)));
      } else {
        sb.append("RUNNING (finalizing)");
      }
    } else if (state == LogicState.COMPLETED) {
      sb.append(String.format("COMPLETED (%.1fs)", elapsedTime));
    } else if (state == LogicState.FAILED) {
      sb.append("FAILED");
      if (aborted) {
        sb.append(" - ").append(abortReason);
      }
    } else {
      sb.append(state.toString());
    }

    return sb.toString();
  }

  /**
   * Gets all permissive conditions.
   *
   * @return list of permissives
   */
  public List<LogicCondition> getPermissives() {
    return new ArrayList<>(permissives);
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
