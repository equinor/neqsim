package neqsim.process.logic.action;

import neqsim.process.equipment.ProcessEquipmentInterface;

import java.util.List;

import java.util.Collections;

import java.io.Serializable;

import neqsim.process.equipment.valve.BlowdownValve;
import neqsim.process.logic.LogicAction;

/**
 * Action to activate a blowdown valve.
 *
 * @author ESOL
 * @version 1.0
 */
public class ActivateBlowdownAction implements LogicAction, Serializable {
  private static final long serialVersionUID = 1L;
  private final BlowdownValve valve;
  private boolean executed = false;

  /**
   * Creates an activate blowdown action.
   *
   * @param valve blowdown valve to activate
   */
  public ActivateBlowdownAction(BlowdownValve valve) {
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
      valve.activate();
      executed = true;
    }
  }

  @Override
  public String getDescription() {
    return "Activate blowdown valve " + valve.getName();
  }

  @Override
  public boolean isComplete() {
    // Complete when valve is fully open (>90%)
    return executed && valve.isActivated() && valve.getPercentValveOpening() > 90.0;
  }

  @Override
  public String getTargetName() {
    return valve.getName();
  }
}
