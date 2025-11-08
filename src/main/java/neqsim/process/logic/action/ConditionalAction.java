package neqsim.process.logic.action;

import neqsim.process.logic.LogicAction;
import neqsim.process.logic.LogicCondition;

/**
 * Executes an action conditionally based on a runtime condition.
 * 
 * <p>
 * Conditional actions provide if-then-else logic within process sequences:
 * <ul>
 * <li>If condition is true, execute primary action</li>
 * <li>If condition is false, execute alternative action (optional)</li>
 * <li>Enables dynamic decision-making in sequences</li>
 * </ul>
 * 
 * <p>
 * Example usage:
 * 
 * <pre>
 * // If temperature > 100°C, open cooling valve; else open bypass valve
 * LogicCondition highTemp = new TemperatureCondition(reactor, 100.0, ">");
 * LogicAction openCooling = new OpenValveAction(coolingValve);
 * LogicAction openBypass = new OpenValveAction(bypassValve);
 * 
 * ConditionalAction conditional =
 *     new ConditionalAction(highTemp, openCooling, openBypass, "Temperature Control");
 * 
 * // In sequence
 * startupLogic.addAction(conditional, 0.0);
 * 
 * // Execute - checks condition at runtime
 * conditional.execute(); // Opens appropriate valve based on current temperature
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class ConditionalAction implements LogicAction {
  private final LogicCondition condition;
  private final LogicAction primaryAction; // If condition true
  private final LogicAction alternativeAction; // If condition false (optional)
  private final String description;

  private boolean evaluated = false;
  private LogicAction selectedAction = null;

  /**
   * Creates a conditional action with primary action only (no alternative).
   *
   * @param condition condition to evaluate
   * @param primaryAction action to execute if condition is true
   * @param description description of this conditional
   */
  public ConditionalAction(LogicCondition condition, LogicAction primaryAction,
      String description) {
    this(condition, primaryAction, null, description);
  }

  /**
   * Creates a conditional action with both primary and alternative actions.
   *
   * @param condition condition to evaluate
   * @param primaryAction action to execute if condition is true
   * @param alternativeAction action to execute if condition is false (can be null)
   * @param description description of this conditional
   */
  public ConditionalAction(LogicCondition condition, LogicAction primaryAction,
      LogicAction alternativeAction, String description) {
    this.condition = condition;
    this.primaryAction = primaryAction;
    this.alternativeAction = alternativeAction;
    this.description = description;
  }

  @Override
  public void execute() {
    if (!evaluated) {
      // Evaluate condition and select action
      boolean conditionMet = condition.evaluate();
      selectedAction = conditionMet ? primaryAction : alternativeAction;
      evaluated = true;
    }

    // Execute selected action
    if (selectedAction != null) {
      selectedAction.execute();
    }
  }

  @Override
  public boolean isComplete() {
    if (!evaluated) {
      return false;
    }

    // If no action selected (condition false and no alternative), considered complete
    if (selectedAction == null) {
      return true;
    }

    return selectedAction.isComplete();
  }

  @Override
  public String getDescription() {
    StringBuilder sb = new StringBuilder();
    sb.append(description);

    if (evaluated && selectedAction != null) {
      boolean conditionMet = (selectedAction == primaryAction);
      sb.append(String.format(" [%s → %s]", condition.getDescription(),
          conditionMet ? "PRIMARY" : "ALTERNATIVE"));
    } else {
      sb.append(String.format(" [IF: %s]", condition.getDescription()));
    }

    return sb.toString();
  }

  @Override
  public String getTargetName() {
    if (evaluated && selectedAction != null) {
      return selectedAction.getTargetName();
    }
    return "conditional";
  }

  /**
   * Gets the condition being evaluated.
   *
   * @return condition
   */
  public LogicCondition getCondition() {
    return condition;
  }

  /**
   * Gets the primary action (executed if condition true).
   *
   * @return primary action
   */
  public LogicAction getPrimaryAction() {
    return primaryAction;
  }

  /**
   * Gets the alternative action (executed if condition false).
   *
   * @return alternative action, or null if none
   */
  public LogicAction getAlternativeAction() {
    return alternativeAction;
  }

  /**
   * Gets the action that was selected after evaluation.
   *
   * @return selected action, or null if not yet evaluated
   */
  public LogicAction getSelectedAction() {
    return selectedAction;
  }

  /**
   * Checks if the condition has been evaluated.
   *
   * @return true if evaluated
   */
  public boolean isEvaluated() {
    return evaluated;
  }

  /**
   * Resets the conditional for re-evaluation.
   */
  public void reset() {
    evaluated = false;
    selectedAction = null;
  }
}
