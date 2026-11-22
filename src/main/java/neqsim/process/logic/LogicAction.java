package neqsim.process.logic;

import java.util.Collections;
import java.util.List;
import neqsim.process.equipment.ProcessEquipmentInterface;

/**
 * Represents an action that can be executed as part of process logic.
 *
 * <p>
 * Actions encapsulate specific operations on process equipment such as:
 * <ul>
 * <li>Opening or closing valves</li>
 * <li>Starting or stopping pumps/compressors</li>
 * <li>Setting controller setpoints</li>
 * <li>Switching equipment modes</li>
 * <li>Raising alarms</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public interface LogicAction {

  /**
   * Executes the action.
   *
   * <p>
   * This method performs the actual operation on the target equipment.
   * </p>
   */
  void execute();

  /**
   * Re-arms the action for a new execution without changing equipment state.
   *
   * <p>
   * Stateless actions need no work. Stateful custom actions must override this hook to clear cached execution or
   * branch-selection state. Resetting an action does not energize, reopen or reset the target equipment and does not
   * establish a restart permissive.
   * </p>
   */
  default void reset() {
    // Backwards-compatible default for stateless custom actions.
  }

  /**
   * Gets a human-readable description of the action.
   *
   * @return action description
   */
  String getDescription();

  /**
   * Checks if the action has completed.
   *
   * <p>
   * Some actions are instantaneous (return true immediately), while others may take time to complete (e.g., valve
   * stroke).
   * </p>
   *
   * @return true if action is complete
   */
  boolean isComplete();

  /**
   * Gets the name of the target equipment.
   *
   * @return equipment name
   */
  String getTargetName();

  /**
   * Returns actual equipment references configured for this action.
   *
   * <p>
   * The default exposes no identity for opaque legacy actions. A declared target name is not resolved heuristically to
   * a physical object.
   * </p>
   *
   * @return configured physical targets, empty when unknown or targetless
   */
  default List<ProcessEquipmentInterface> getTargetEquipment() {
    return Collections.emptyList();
  }

  /**
   * Indicates whether all possible equipment targets are exposed through actual references.
   *
   * @return true only when the action supplies a complete physical-target identity contract
   */
  default boolean isTargetEquipmentIdentityComplete() {
    return false;
  }
}
