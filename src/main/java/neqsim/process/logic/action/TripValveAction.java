package neqsim.process.logic.action;

import neqsim.process.equipment.ProcessEquipmentInterface;

import java.util.List;

import java.util.Collections;

import java.io.Serializable;

import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.logic.LogicAction;

/**
 * Action to trip (de-energize) an ESD valve, causing fail-safe closure.
 *
 * @author ESOL
 * @version 1.0
 */
public class TripValveAction implements LogicAction, Serializable {
  private static final long serialVersionUID = 1L;
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
