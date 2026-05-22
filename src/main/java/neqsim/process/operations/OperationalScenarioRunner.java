package neqsim.process.operations;

import java.util.UUID;
import neqsim.process.automation.ProcessAutomation;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.pipeline.WaterHammerPipe;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.logic.action.SetValveOpeningAction;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Executes plant-agnostic operational scenarios on a NeqSim {@link ProcessSystem}.
 *
 * <p>
 * The runner reuses existing automation, process logic actions, and transient simulation instead
 * of introducing new equipment or controller abstractions.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class OperationalScenarioRunner {

  /**
   * Private constructor for utility class.
   */
  private OperationalScenarioRunner() {}

  /**
   * Runs an operational scenario against a process system.
   *
   * @param process process system to manipulate
   * @param scenario ordered scenario to execute
   * @return execution result with logs and before/after values
   */
  public static OperationalScenarioResult run(ProcessSystem process, OperationalScenario scenario) {
    if (process == null) {
      throw new IllegalArgumentException("process must not be null");
    }
    if (scenario == null) {
      throw new IllegalArgumentException("scenario must not be null");
    }

    OperationalScenarioResult result = new OperationalScenarioResult(scenario.getName());
    for (OperationalAction action : scenario.getActions()) {
      executeAction(process, action, result);
    }
    return result;
  }

  /**
   * Executes one action and records its result.
   *
   * @param process process system
   * @param action action to execute
   * @param result result object to update
   */
  private static void executeAction(ProcessSystem process, OperationalAction action,
      OperationalScenarioResult result) {
    try {
      String observedAddress = observedAddress(action);
      captureValue(process, observedAddress, action.getUnit(), true, result);
      switch (action.getType()) {
        case SET_VARIABLE:
          process.getAutomation().setVariableValue(action.getTarget(), action.getValue(),
              action.getUnit());
          break;
        case SET_VALVE_OPENING:
          executeValveOpening(process, action);
          break;
        case APPLY_FIELD_INPUTS:
          process.applyFieldInputs();
          break;
        case RUN_STEADY_STATE:
          process.run();
          break;
        case RUN_TRANSIENT:
          runTransient(process, action);
          break;
        default:
          result.addWarning("Unsupported action type: " + action.getType());
          break;
      }
      captureValue(process, observedAddress, action.getUnit(), false, result);
      result.addActionLog(action.getDescription());
    } catch (RuntimeException ex) {
      result.addError(action.getDescription() + " failed: " + ex.getMessage());
    }
  }

  /**
   * Executes a valve-opening action using existing valve logic when possible.
   *
   * @param process process system
   * @param action valve action
   */
  private static void executeValveOpening(ProcessSystem process, OperationalAction action) {
    ProcessEquipmentInterface unit = process.getUnit(action.getTarget());
    if (unit instanceof ThrottlingValve) {
      SetValveOpeningAction valveAction = new SetValveOpeningAction((ThrottlingValve) unit,
          action.getValue());
      valveAction.execute();
    } else if (unit instanceof WaterHammerPipe) {
      ((WaterHammerPipe) unit).setValveOpeningPercent(action.getValue());
    } else {
      process.getAutomation().setVariableValue(action.getTarget() + ".percentValveOpening",
          action.getValue(), "%");
    }
  }

  /**
   * Runs transient steps for an action.
   *
   * @param process process system
   * @param action transient action containing duration and time step
   */
  private static void runTransient(ProcessSystem process, OperationalAction action) {
    if (action.getDurationSeconds() <= 0.0) {
      throw new IllegalArgumentException("Transient duration must be positive");
    }
    if (action.getTimeStepSeconds() <= 0.0) {
      throw new IllegalArgumentException("Transient time step must be positive");
    }
    double remaining = action.getDurationSeconds();
    while (remaining > 1.0e-12) {
      double step = Math.min(action.getTimeStepSeconds(), remaining);
      process.runTransient(step, UUID.randomUUID());
      remaining -= step;
    }
  }

  /**
   * Returns the manipulated address observed for before and after values.
   *
   * @param action action to inspect
   * @return automation address or empty string
   */
  private static String observedAddress(OperationalAction action) {
    if (action.getType() == OperationalAction.ActionType.SET_VARIABLE) {
      return action.getTarget();
    }
    if (action.getType() == OperationalAction.ActionType.SET_VALVE_OPENING) {
      return action.getTarget() + ".percentValveOpening";
    }
    return "";
  }

  /**
   * Captures an automation value when the observed address is available.
   *
   * @param process process system
   * @param address automation address
   * @param unit unit of measure
   * @param before true for before-action storage, false for after-action storage
   * @param result result object to update
   */
  private static void captureValue(ProcessSystem process, String address, String unit,
      boolean before, OperationalScenarioResult result) {
    if (address == null || address.isEmpty()) {
      return;
    }
    ProcessAutomation automation = process.getAutomation();
    double value = automation.getVariableValue(address, unit);
    if (before) {
      result.putBeforeValue(address, value);
    } else {
      result.putAfterValue(address, value);
    }
  }
}