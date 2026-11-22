package neqsim.process.logic.action;

import neqsim.process.equipment.ProcessEquipmentInterface;

import java.util.List;

import java.util.Collections;

import java.io.Serializable;

import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.logic.LogicAction;

/**
 * Action to set valve opening percentage.
 *
 * @author ESOL
 * @version 1.0
 */
public class SetValveOpeningAction implements LogicAction, Serializable {
  private static final long serialVersionUID = 1L;
  private final ThrottlingValve valve;
  private final double targetOpening;
  private boolean executed = false;

  /**
   * Creates a set valve opening action.
   *
   * @param valve valve to control
   * @param targetOpening target opening percentage (0-100)
   */
  public SetValveOpeningAction(ThrottlingValve valve, double targetOpening) {
    this.valve = valve;
    this.targetOpening = Math.max(0.0, Math.min(100.0, targetOpening));
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
    return valve == null ? Collections.<ProcessEquipmentInterface>emptyList()
        : Collections.<ProcessEquipmentInterface>singletonList(valve);
  }

  /**
   * Reports whether all configured physical target identities are available.
   *
   * @return true when no target is opaque or missing
   */
  @Override
  public boolean isTargetEquipmentIdentityComplete() {
    return valve != null;
  }

  @Override
  public void execute() {
    if (!executed) {
      valve.setPercentValveOpening(targetOpening);
      executed = true;
    }
  }

  @Override
  public String getDescription() {
    return String.format("Set valve %s to %.1f%% opening", valve.getName(), targetOpening);
  }

  @Override
  public boolean isComplete() {
    if (!executed) {
      return false;
    }

    // Consider complete when within 1% of target
    double currentOpening = valve.getPercentValveOpening();
    return Math.abs(currentOpening - targetOpening) <= 1.0;
  }

  @Override
  public String getTargetName() {
    return valve.getName();
  }

  /**
   * Gets the target opening percentage.
   *
   * @return target opening (0-100)
   */
  public double getTargetOpening() {
    return targetOpening;
  }
}
