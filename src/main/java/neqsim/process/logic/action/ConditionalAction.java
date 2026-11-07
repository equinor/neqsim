package neqsim.process.logic.action;

import java.io.ObjectOutputStream;

import java.io.ObjectInputStream;

import java.io.IOException;

import neqsim.process.equipment.ProcessEquipmentInterface;

import java.util.List;

import java.util.Collections;

import java.util.ArrayList;

import java.io.Serializable;

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
 * // If temperature &gt; 100°C, open cooling valve; else open bypass valve
 * LogicCondition highTemp = new TemperatureCondition(reactor, 100.0, "&gt;");
 * LogicAction openCooling = new OpenValveAction(coolingValve);
 * LogicAction openBypass = new OpenValveAction(bypassValve);
 *
 * ConditionalAction conditional = new ConditionalAction(highTemp, openCooling, openBypass, "Temperature Control");
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
public class ConditionalAction implements LogicAction, Serializable {
  private static final long serialVersionUID = 1L;
  private transient LogicCondition condition;
  private transient LogicAction primaryAction; // If condition true
  private transient LogicAction alternativeAction; // If condition false (optional)
  private final String description;

  private boolean evaluated = false;
  private transient LogicAction selectedAction = null;

  /**
   * Creates a conditional action with primary action only (no alternative).
   *
   * @param condition condition to evaluate
   * @param primaryAction action to execute if condition is true
   * @param description description of this conditional
   */
  public ConditionalAction(LogicCondition condition, LogicAction primaryAction, String description) {
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
  public ConditionalAction(LogicCondition condition, LogicAction primaryAction, LogicAction alternativeAction,
      String description) {
    this.condition = condition;
    this.primaryAction = primaryAction;
    this.alternativeAction = alternativeAction;
    this.description = description;
  }

  /**
   * Returns actual references for every configured target, without issuing equipment commands.
   *
   * @return configured physical targets
   */
  @Override
  public List<ProcessEquipmentInterface> getTargetEquipment() {
    List<ProcessEquipmentInterface> targets = new ArrayList<ProcessEquipmentInterface>();
    if (primaryAction != null) {
      targets.addAll(primaryAction.getTargetEquipment());
    }
    if (alternativeAction != null) {
      targets.addAll(alternativeAction.getTargetEquipment());
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
    return primaryAction != null && primaryAction.isTargetEquipmentIdentityComplete()
        && (alternativeAction == null || alternativeAction.isTargetEquipmentIdentityComplete());
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
      sb.append(String.format(" [%s → %s]", condition.getDescription(), conditionMet ? "PRIMARY" : "ALTERNATIVE"));
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
  @Override
  public void reset() {
    evaluated = false;
    selectedAction = null;
    primaryAction.reset();
    if (alternativeAction != null) {
      alternativeAction.reset();
    }
  }

  /**
   * Persists callback references explicitly, retaining shared child-action identity.
   *
   * @param output destination stream
   * @throws IOException if a callback or captured payload is not serializable
   */
  private void writeObject(ObjectOutputStream output) throws IOException {
    output.defaultWriteObject();
    output.writeObject(condition);
    output.writeObject(primaryAction);
    output.writeObject(alternativeAction);
    output.writeObject(selectedAction);
  }

  /**
   * Restores callbacks without evaluating the condition or commanding equipment.
   *
   * @param input source stream
   * @throws IOException if the graph cannot be read
   * @throws ClassNotFoundException if a callback class is unavailable
   */
  private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
    input.defaultReadObject();
    condition = (LogicCondition) input.readObject();
    primaryAction = (LogicAction) input.readObject();
    alternativeAction = (LogicAction) input.readObject();
    selectedAction = (LogicAction) input.readObject();
  }
}
