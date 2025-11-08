package neqsim.process.logic;

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
   * Gets a human-readable description of the action.
   *
   * @return action description
   */
  String getDescription();

  /**
   * Checks if the action has completed.
   * 
   * <p>
   * Some actions are instantaneous (return true immediately), while others may take time to
   * complete (e.g., valve stroke).
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
}
