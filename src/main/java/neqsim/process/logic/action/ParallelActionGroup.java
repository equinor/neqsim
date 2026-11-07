package neqsim.process.logic.action;

import java.io.ObjectOutputStream;

import java.io.ObjectInputStream;

import java.io.IOException;

import neqsim.process.equipment.ProcessEquipmentInterface;

import java.util.Collections;

import java.io.Serializable;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.logic.LogicAction;

/**
 * Executes multiple actions in parallel and tracks completion.
 *
 * <p>
 * Parallel action groups allow simultaneous execution of multiple operations, which is useful for:
 * <ul>
 * <li>Opening multiple valves at once</li>
 * <li>Starting multiple pumps simultaneously</li>
 * <li>Coordinated equipment activation</li>
 * <li>Reducing total sequence time</li>
 * </ul>
 *
 * <p>
 * The parallel group is considered complete only when ALL actions have completed.
 *
 * <p>
 * Example usage:
 *
 * <pre>
 * // Create parallel group to open multiple valves at once
 * ParallelActionGroup parallelOpen = new ParallelActionGroup("Open All Valves");
 * parallelOpen.addAction(new OpenValveAction(valve1));
 * parallelOpen.addAction(new OpenValveAction(valve2));
 * parallelOpen.addAction(new OpenValveAction(valve3));
 *
 * // Add to sequence
 * startupLogic.addAction(parallelOpen, 0.0);
 *
 * // Execute - all valves open simultaneously
 * parallelOpen.execute();
 * if (parallelOpen.isComplete()) {
 *   // All valves fully open
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class ParallelActionGroup implements LogicAction, Serializable {
  private static final long serialVersionUID = 1L;
  private final String description;
  private transient List<LogicAction> actions = new ArrayList<>();
  private boolean executed = false;

  /**
   * Creates a parallel action group.
   *
   * @param description description of this parallel group
   */
  public ParallelActionGroup(String description) {
    this.description = description;
  }

  /**
   * Adds an action to execute in parallel.
   *
   * @param action action to add
   */
  public void addAction(LogicAction action) {
    actions.add(action);
  }

  /**
   * Re-arms execution bookkeeping without changing the target equipment.
   */
  @Override
  public void reset() {
    executed = false;
    for (LogicAction action : actions) {
      action.reset();
    }
  }

  /**
   * Returns actual references for every configured target, without issuing equipment commands.
   *
   * @return configured physical targets
   */
  @Override
  public List<ProcessEquipmentInterface> getTargetEquipment() {
    List<ProcessEquipmentInterface> targets = new ArrayList<ProcessEquipmentInterface>();
    for (LogicAction action : actions) {
      if (action != null) {
        targets.addAll(action.getTargetEquipment());
      }
    }
    return Collections.unmodifiableList(targets);
  }

  /**
   * Reports whether all configured physical target identities are available.
   *
   * @return true when no target is opaque or missing
   */
  @Override
  public boolean isTargetEquipmentIdentityComplete() {
    for (LogicAction action : actions) {
      if (action == null || !action.isTargetEquipmentIdentityComplete()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void execute() {
    // Execute all actions in parallel
    for (LogicAction action : actions) {
      action.execute();
    }
    executed = true;
  }

  @Override
  public boolean isComplete() {
    if (!executed) {
      return false;
    }

    // Complete only when ALL actions are complete
    for (LogicAction action : actions) {
      if (!action.isComplete()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String getDescription() {
    return description + " (" + actions.size() + " parallel actions)";
  }

  @Override
  public String getTargetName() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < actions.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(actions.get(i).getTargetName());
    }
    return sb.toString();
  }

  /**
   * Gets all actions in this parallel group.
   *
   * @return list of actions
   */
  public List<LogicAction> getActions() {
    return new ArrayList<>(actions);
  }

  /**
   * Gets the number of completed actions.
   *
   * @return completed count
   */
  public int getCompletedCount() {
    int count = 0;
    for (LogicAction action : actions) {
      if (action.isComplete()) {
        count++;
      }
    }
    return count;
  }

  /**
   * Gets the total number of actions.
   *
   * @return total action count
   */
  public int getTotalCount() {
    return actions.size();
  }

  /**
   * Gets the completion percentage.
   *
   * @return percentage 0-100
   */
  public double getCompletionPercentage() {
    if (actions.isEmpty()) {
      return 100.0;
    }
    return 100.0 * getCompletedCount() / getTotalCount();
  }

  @Override
  public String toString() {
    return String.format("%s - %d/%d complete (%.0f%%)", description, getCompletedCount(), getTotalCount(),
        getCompletionPercentage());
  }

  /**
   * Writes the child graph explicitly, preserving shared equipment and action references.
   *
   * @param output destination stream
   * @throws IOException if a child or captured payload is not serializable
   */
  private void writeObject(ObjectOutputStream output) throws IOException {
    output.defaultWriteObject();
    output.writeObject(actions);
  }

  /**
   * Restores the child graph without executing any action.
   *
   * @param input source stream
   * @throws IOException if the child list is absent or contains invalid actions
   * @throws ClassNotFoundException if a child class is unavailable
   */
  @SuppressWarnings("unchecked")
  private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
    input.defaultReadObject();
    Object restored = input.readObject();
    if (!(restored instanceof List<?>)) {
      throw new IOException("Parallel action definitions are missing");
    }
    for (Object child : (List<?>) restored) {
      if (!(child instanceof LogicAction)) {
        throw new IOException("Parallel action payload is missing or invalid");
      }
    }
    actions = (List<LogicAction>) restored;
  }
}
