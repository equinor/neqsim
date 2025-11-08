package neqsim.process.logic.action;

import neqsim.process.equipment.valve.BlowdownValve;
import neqsim.process.logic.LogicAction;

/**
 * Action to activate a blowdown valve.
 *
 * @author ESOL
 * @version 1.0
 */
public class ActivateBlowdownAction implements LogicAction {
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
