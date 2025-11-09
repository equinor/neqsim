package neqsim.process.logic.action;

import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.logic.LogicAction;

/**
 * Action to energize an ESD valve and open it to a specified position.
 * 
 * <p>
 * This action is commonly used in blowdown sequences where an ESD valve needs to be energized
 * (powered) and then opened to allow emergency depressurization through a flare system.
 * 
 * <p>
 * Usage example:
 * 
 * <pre>
 * ESDValve blowdownValve = new ESDValve("BD-001", stream);
 * blowdownValve.deEnergize(); // Start closed for safety
 * 
 * ESDLogic esdLogic = new ESDLogic("Emergency Shutdown");
 * esdLogic.addAction(new EnergizeESDValveAction(blowdownValve, 100.0), 0.5);
 * </pre>
 * 
 * @author ESOL
 * @version 1.0
 */
public class EnergizeESDValveAction implements LogicAction {

  private final ESDValve valve;
  private final double targetOpening;
  private boolean executed = false;

  /**
   * Creates an action to energize and open an ESD valve.
   * 
   * @param valve The ESD valve to energize and open
   * @param targetOpening The target valve opening percentage (0-100)
   */
  public EnergizeESDValveAction(ESDValve valve, double targetOpening) {
    if (valve == null) {
      throw new IllegalArgumentException("Valve cannot be null");
    }
    if (targetOpening < 0.0 || targetOpening > 100.0) {
      throw new IllegalArgumentException("Target opening must be between 0 and 100");
    }
    this.valve = valve;
    this.targetOpening = targetOpening;
  }

  /**
   * Creates an action to energize and fully open an ESD valve.
   * 
   * @param valve The ESD valve to energize and open
   */
  public EnergizeESDValveAction(ESDValve valve) {
    this(valve, 100.0);
  }

  @Override
  public void execute() {
    if (!executed) {
      valve.energize(); // Energize to allow opening
      valve.setPercentValveOpening(targetOpening); // Set target opening
      executed = true;
    }
  }

  @Override
  public String getDescription() {
    return String.format("Energize and open ESD valve %s to %.1f%%", valve.getName(),
        targetOpening);
  }

  @Override
  public boolean isComplete() {
    // Consider complete when executed, energized, and valve is within 5% of target
    return executed && valve.isEnergized()
        && Math.abs(valve.getPercentValveOpening() - targetOpening) < 5.0;
  }

  @Override
  public String getTargetName() {
    return valve.getName();
  }
}
