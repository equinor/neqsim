package neqsim.process.logic.control;

import java.util.ArrayList;
import java.util.List;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.valve.ControlValve;
import neqsim.process.logic.LogicAction;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.ProcessLogic;
import neqsim.process.logic.action.SetValveOpeningAction;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Process logic for automatic pressure control via valve throttling.
 * 
 * <p>
 * This logic automatically adjusts a control valve opening when activated, typically in response to
 * high pressure alarms. The valve adjustment helps reduce system pressure before safety systems
 * (HIPPS, ESD) need to activate.
 * 
 * <p>
 * Typical usage pattern:
 * 
 * <pre>
 * ControlValve inletValve = (ControlValve) system.getUnit("Inlet Valve");
 * PressureControlLogic throttleLogic =
 *     new PressureControlLogic("Pressure HIHI Auto-Throttle", inletValve, 50.0, system);
 * 
 * // Activate automatically on HIHI alarm
 * alarmManager
 *     .registerActionHandler(AlarmActionHandler.activateLogicOnHIHI("PT-101", throttleLogic));
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class PressureControlLogic implements ProcessLogic {

  private final String name;
  private final ControlValve controlValve;
  private final double targetOpening;
  private final ProcessSystem system;
  private final boolean runSystemAfterChange;
  private LogicState state = LogicState.IDLE;
  private final List<LogicAction> actions = new ArrayList<>();

  /**
   * Creates pressure control logic with system run.
   * 
   * @param name the logic name
   * @param controlValve the valve to throttle
   * @param targetOpening the target valve opening percentage (0-100)
   * @param system the process system to run after valve change
   */
  public PressureControlLogic(String name, ControlValve controlValve, double targetOpening,
      ProcessSystem system) {
    this.name = name;
    this.controlValve = controlValve;
    this.targetOpening = targetOpening;
    this.system = system;
    this.runSystemAfterChange = true;

    // Add action to set valve opening
    this.actions.add(new SetValveOpeningAction(controlValve, targetOpening));
  }

  /**
   * Creates pressure control logic without automatic system run.
   * 
   * @param name the logic name
   * @param controlValve the valve to throttle
   * @param targetOpening the target valve opening percentage (0-100)
   */
  public PressureControlLogic(String name, ControlValve controlValve, double targetOpening) {
    this.name = name;
    this.controlValve = controlValve;
    this.targetOpening = targetOpening;
    this.system = null;
    this.runSystemAfterChange = false;

    // Add action to set valve opening
    this.actions.add(new SetValveOpeningAction(controlValve, targetOpening));
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public LogicState getState() {
    return state;
  }

  @Override
  public void activate() {
    if (state == LogicState.IDLE || state == LogicState.COMPLETED || state == LogicState.FAILED) {
      state = LogicState.RUNNING;

      // Execute all actions immediately
      for (LogicAction action : actions) {
        action.execute();
      }

      state = LogicState.COMPLETED;

      // Optionally run system after valve change to update pressures
      if (runSystemAfterChange && system != null) {
        system.run();
      }
    }
  }

  @Override
  public void deactivate() {
    state = LogicState.IDLE;
  }

  @Override
  public boolean reset() {
    state = LogicState.IDLE;
    return true;
  }

  @Override
  public void execute(double timeStep) {
    // Nothing to execute in time steps - action happens immediately on activation
  }

  @Override
  public boolean isActive() {
    return state == LogicState.RUNNING;
  }

  @Override
  public boolean isComplete() {
    return state == LogicState.COMPLETED;
  }

  @Override
  public List<ProcessEquipmentInterface> getTargetEquipment() {
    List<ProcessEquipmentInterface> equipment = new ArrayList<>();
    equipment.add(controlValve);
    return equipment;
  }

  @Override
  public String getStatusDescription() {
    return String.format("%s [%s]: Throttle %s to %.1f%%", name, state, controlValve.getName(),
        targetOpening);
  }

  /**
   * Gets the control valve being manipulated.
   * 
   * @return the control valve
   */
  public ControlValve getControlValve() {
    return controlValve;
  }

  /**
   * Gets the target valve opening percentage.
   * 
   * @return target opening (0-100%)
   */
  public double getTargetOpening() {
    return targetOpening;
  }

  @Override
  public String toString() {
    return getStatusDescription();
  }
}
