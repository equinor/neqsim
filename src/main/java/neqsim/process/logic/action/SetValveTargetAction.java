package neqsim.process.logic.action;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.logic.LogicAction;

/**
 * Commands a valve target while retaining measured position as the completion criterion.
 *
 * <p>
 * Unlike {@link SetValveOpeningAction}, this action does not overwrite the measured stem position. The configured valve
 * travel model, lag, rate limit, deadband, stiction and hysteresis therefore remain active during transient execution.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class SetValveTargetAction implements LogicAction, Serializable {
  private static final long serialVersionUID = 1L;
  private final ThrottlingValve valve;
  private final double targetOpening;
  private final double completionTolerance;
  private boolean commanded;

  /**
   * Creates a physical valve target command.
   *
   * @param valve valve whose actuator target is changed
   * @param targetOpening requested opening in percent
   * @param completionTolerance absolute measured-position tolerance in percentage points
   */
  @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Command and measured completion must use the same connected native valve; copying would detach the procedure from the process model")
  public SetValveTargetAction(ThrottlingValve valve, double targetOpening, double completionTolerance) {
    if (valve == null) {
      throw new IllegalArgumentException("Valve target action requires a valve");
    }
    if (!Double.isFinite(targetOpening) || targetOpening < 0.0 || targetOpening > 100.0) {
      throw new IllegalArgumentException("Valve target opening must be finite and within 0-100 percent");
    }
    if (!Double.isFinite(completionTolerance) || completionTolerance < 0.0) {
      throw new IllegalArgumentException("Valve completion tolerance must be finite and nonnegative");
    }
    this.valve = valve;
    this.targetOpening = targetOpening;
    this.completionTolerance = completionTolerance;
  }

  /** {@inheritDoc} */
  @Override
  public void execute() {
    valve.setTargetPercentValveOpening(targetOpening);
    commanded = true;
  }

  /** {@inheritDoc} */
  @Override
  public void reset() {
    commanded = false;
  }

  /** {@inheritDoc} */
  @Override
  public String getDescription() {
    return String.format("Command valve %s target to %.1f%%", valve.getName(), targetOpening);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isComplete() {
    return commanded && Math.abs(valve.getPercentValveOpening() - targetOpening) <= completionTolerance;
  }

  /** {@inheritDoc} */
  @Override
  public String getTargetName() {
    return valve.getName();
  }

  /** {@inheritDoc} */
  @Override
  public List<ProcessEquipmentInterface> getTargetEquipment() {
    return Collections.<ProcessEquipmentInterface>singletonList(valve);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isTargetEquipmentIdentityComplete() {
    return true;
  }

  /**
   * Gets the requested valve position.
   *
   * @return target opening in percent
   */
  public double getTargetOpening() {
    return targetOpening;
  }

  /**
   * Gets the completion tolerance applied to measured position.
   *
   * @return absolute tolerance in percentage points
   */
  public double getCompletionTolerance() {
    return completionTolerance;
  }
}
