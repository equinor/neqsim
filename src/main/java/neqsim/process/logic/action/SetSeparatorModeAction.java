package neqsim.process.logic.action;

import neqsim.process.equipment.ProcessEquipmentInterface;

import java.util.List;

import java.util.Collections;

import java.io.Serializable;

import neqsim.process.equipment.separator.Separator;
import neqsim.process.logic.LogicAction;

/**
 * Action to set separator calculation mode (steady-state vs transient).
 *
 * @author ESOL
 * @version 1.0
 */
public class SetSeparatorModeAction implements LogicAction, Serializable {
  private static final long serialVersionUID = 1L;
  private final Separator separator;
  private final boolean steadyState;
  private boolean executed = false;

  /**
   * Creates a set separator mode action.
   *
   * @param separator separator to configure
   * @param steadyState true for steady-state, false for transient
   */
  public SetSeparatorModeAction(Separator separator, boolean steadyState) {
    this.separator = separator;
    this.steadyState = steadyState;
  }

  /**
   * Re-arms execution bookkeeping without changing the target equipment.
   */
  @Override
  public void reset() {
    executed = false;
  }

  /**
   * Returns actual references for every configured target, without issuing equipment commands.
   *
   * @return configured physical targets
   */
  @Override
  public List<ProcessEquipmentInterface> getTargetEquipment() {
    return separator == null ? Collections.<ProcessEquipmentInterface>emptyList()
        : Collections.<ProcessEquipmentInterface>singletonList(separator);
  }

  /**
   * Reports whether all configured physical target identities are available.
   *
   * @return true when no target is opaque or missing
   */
  @Override
  public boolean isTargetEquipmentIdentityComplete() {
    return separator != null;
  }

  @Override
  public void execute() {
    if (!executed) {
      separator.setCalculateSteadyState(steadyState);
      executed = true;
    }
  }

  @Override
  public String getDescription() {
    return String.format("Set separator %s to %s mode", separator.getName(),
        steadyState ? "steady-state" : "transient");
  }

  @Override
  public boolean isComplete() {
    return executed; // Instantaneous action
  }

  @Override
  public String getTargetName() {
    return separator.getName();
  }

  /**
   * Gets the target mode.
   *
   * @return true for steady-state, false for transient
   */
  public boolean isSteadyState() {
    return steadyState;
  }
}
