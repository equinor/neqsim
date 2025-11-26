package neqsim.process.logic.action;

import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.logic.LogicAction;

/**
 * Action to fully close a valve (0% opening).
 *
 * @author ESOL
 * @version 1.0
 */
public class CloseValveAction implements LogicAction {
  private final ThrottlingValve valve;
  private boolean executed = false;

  /**
   * Creates a close valve action.
   *
   * @param valve valve to close
   */
  public CloseValveAction(ThrottlingValve valve) {
    this.valve = valve;
  }

  @Override
  public void execute() {
    if (!executed) {
      valve.setPercentValveOpening(0.0);
      executed = true;
    }
  }

  @Override
  public String getDescription() {
    return "Close valve " + valve.getName();
  }

  @Override
  public boolean isComplete() {
    if (!executed) {
      return false;
    }

    // Consider complete when valve is <5% open
    return valve.getPercentValveOpening() < 5.0;
  }

  @Override
  public String getTargetName() {
    return valve.getName();
  }
}
