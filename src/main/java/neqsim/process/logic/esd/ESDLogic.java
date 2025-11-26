package neqsim.process.logic.esd;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.logic.LogicAction;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.ProcessLogic;

/**
 * Simplified ESD (Emergency Shutdown) logic implementation.
 * 
 * <p>
 * This class manages a sequence of actions that should be executed when an ESD is triggered. The
 * actions are executed in order with configurable delays between steps.
 * 
 * <p>
 * Example usage:
 * 
 * <pre>
 * ESDLogic esdLogic = new ESDLogic("ESD Level 1");
 * esdLogic.addAction(new TripValveAction(esdValve), 0.0); // Immediate
 * esdLogic.addAction(new ActivateBlowdownAction(bdValve), 0.5); // After 0.5s
 * esdLogic.addAction(new SetSplitterAction(splitter, new double[] {0.0, 1.0}), 0.5);
 * 
 * // In simulation loop:
 * esdLogic.activate(); // Trigger ESD
 * while (!esdLogic.isComplete()) {
 *   esdLogic.execute(timeStep);
 *   // Run equipment transients...
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class ESDLogic implements ProcessLogic {
  private final String name;
  private LogicState state = LogicState.IDLE;
  private final List<ActionWithDelay> actions = new ArrayList<>();
  private int currentActionIndex = 0;
  private double elapsedTime = 0.0;
  private double currentDelay = 0.0;

  /**
   * Creates a new ESD logic instance.
   *
   * @param name name of the ESD logic
   */
  public ESDLogic(String name) {
    this.name = name;
  }

  /**
   * Adds an action to the ESD sequence.
   *
   * @param action action to execute
   * @param delay delay in seconds before executing this action (relative to previous action)
   */
  public void addAction(LogicAction action, double delay) {
    actions.add(new ActionWithDelay(action, delay));
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
  public void activate() {
    if (state == LogicState.IDLE || state == LogicState.COMPLETED || state == LogicState.FAILED) {
      state = LogicState.RUNNING;
      currentActionIndex = 0;
      elapsedTime = 0.0;
      currentDelay = 0.0;

      // Reset all actions
      for (ActionWithDelay actionWithDelay : actions) {
        if (actionWithDelay.action instanceof ResettableAction) {
          ((ResettableAction) actionWithDelay.action).reset();
        }
      }
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
    return true;
  }

  @Override
  public void execute(double timeStep) {
    if (state != LogicState.RUNNING) {
      return;
    }

    elapsedTime += timeStep;

    // Process current action
    if (currentActionIndex < actions.size()) {
      ActionWithDelay currentActionWithDelay = actions.get(currentActionIndex);

      // Wait for delay
      if (currentDelay < currentActionWithDelay.delay) {
        currentDelay += timeStep;
        return;
      }

      // Execute action if not yet executed
      currentActionWithDelay.action.execute();

      // Check if action is complete
      if (currentActionWithDelay.action.isComplete()) {
        // Move to next action
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
    // Not implemented in simplified version
    return new ArrayList<>();
  }

  @Override
  public String getStatusDescription() {
    if (state == LogicState.IDLE) {
      return name + " - IDLE (not triggered)";
    } else if (state == LogicState.RUNNING) {
      if (currentActionIndex < actions.size()) {
        ActionWithDelay current = actions.get(currentActionIndex);
        return String.format("%s - RUNNING (Step %d/%d: %s, delay: %.1fs)", name,
            currentActionIndex + 1, actions.size(), current.action.getDescription(),
            Math.max(0, current.delay - currentDelay));
      } else {
        return name + " - RUNNING (finalizing)";
      }
    } else if (state == LogicState.COMPLETED) {
      return String.format("%s - COMPLETED (%.1fs)", name, elapsedTime);
    } else {
      return name + " - " + state.toString();
    }
  }

  /**
   * Gets the total number of actions in this ESD logic.
   *
   * @return number of actions
   */
  public int getActionCount() {
    return actions.size();
  }

  /**
   * Gets the current action index (0-based).
   *
   * @return current action index, or -1 if not running
   */
  public int getCurrentActionIndex() {
    return state == LogicState.RUNNING ? currentActionIndex : -1;
  }

  /**
   * Gets the total elapsed time since activation.
   *
   * @return elapsed time in seconds
   */
  public double getElapsedTime() {
    return elapsedTime;
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

  /**
   * Marker interface for actions that can be reset.
   */
  private interface ResettableAction {
    void reset();
  }
}
