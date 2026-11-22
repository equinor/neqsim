package neqsim.process.logic.action;

import neqsim.process.equipment.ProcessEquipmentInterface;

import java.util.List;

import java.util.Collections;

import java.io.Serializable;

import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.logic.LogicAction;

/**
 * Action to fully open a valve (100% opening).
 *
 * @author ESOL
 * @version 1.0
 */
public class OpenValveAction implements LogicAction, Serializable {
  private static final long serialVersionUID = 1L;
  private final ThrottlingValve valve;
  private boolean executed = false;

  /**
   * Creates an open valve action.
   *
   * @param valve valve to open
   */
  public OpenValveAction(ThrottlingValve valve) {
    this.valve = valve;
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
      valve.setPercentValveOpening(100.0);
      executed = true;
    }
  }

  @Override
  public String getDescription() {
    return "Open valve " + valve.getName();
  }

  @Override
  public boolean isComplete() {
    if (!executed) {
      return false;
    }

    // Consider complete when valve is >95% open
    return valve.getPercentValveOpening() > 95.0;
  }

  @Override
  public String getTargetName() {
    return valve.getName();
  }
}
