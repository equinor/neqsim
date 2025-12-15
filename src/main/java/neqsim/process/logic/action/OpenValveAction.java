package neqsim.process.logic.action;

import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.logic.LogicAction;

/**
 * Action to fully open a valve (100% opening).
 *
 * @author ESOL
 * @version 1.0
 */
public class OpenValveAction implements LogicAction {
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
