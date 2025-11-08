package neqsim.process.logic.action;

import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.logic.LogicAction;

/**
 * Action to trip (de-energize) an ESD valve, causing fail-safe closure.
 *
 * @author ESOL
 * @version 1.0
 */
public class TripValveAction implements LogicAction {
  private final ESDValve valve;
  private boolean executed = false;

  /**
   * Creates a trip valve action.
   *
   * @param valve ESD valve to trip
   */
  public TripValveAction(ESDValve valve) {
    this.valve = valve;
  }

  @Override
  public void execute() {
    if (!executed) {
      valve.trip();
      executed = true;
    }
  }

  @Override
  public String getDescription() {
    return "Trip ESD valve " + valve.getName();
  }

  @Override
  public boolean isComplete() {
    return executed && valve.hasTripCompleted();
  }

  @Override
  public String getTargetName() {
    return valve.getName();
  }
}
