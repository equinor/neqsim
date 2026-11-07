package neqsim.process.logic.esd;

import java.io.ObjectOutputStream;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.logic.LogicAction;
import neqsim.process.logic.LogicState;
import neqsim.process.logic.action.ParallelActionGroup;
import neqsim.process.logic.action.ConditionalAction;
import neqsim.process.logic.action.TripValveAction;
import neqsim.process.logic.action.TripCompressorAction;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.ShutdownProfile;
import neqsim.process.equipment.valve.ESDValve;
import neqsim.process.logic.ProcessLogic;

/**
 * Simplified ESD (Emergency Shutdown) logic implementation.
 *
 * <p>
 * This class manages a sequence of actions that should be executed when an ESD is triggered. The actions are executed
 * in order with configurable delays between steps.
 *
 * <p>
 * Example usage:
 *
 * <pre>
 * ESDLogic esdLogic = new ESDLogic("ESD Level 1");
 * esdLogic.addAction(new TripValveAction(esdValve), 0.0); // Immediate
 * esdLogic.addAction(new ActivateBlowdownAction(bdValve), 0.5); // After 0.5s
 * esdLogic.addAction(new SetSplitterAction(splitter, new double[] {0.0, 1.0}), 0.5);
 *
 * // In simulation loop:
 * esdLogic.activate(); // Trigger ESD
 * while (!esdLogic.isComplete()) {
 *   esdLogic.execute(timeStep);
 *   // Run equipment transients...
 * }
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class ESDLogic implements ProcessLogic {
  private static final long serialVersionUID = 1L;
  private final String name;
  private LogicState state = LogicState.IDLE;
  private final List<ActionWithDelay> actions = new ArrayList<>();
  private int currentActionIndex = 0;
  private double elapsedTime = 0.0;
  private double currentDelay = 0.0;

  /**
   * Creates a new ESD logic instance.
   *
   * @param name name of the ESD logic
   */
  public ESDLogic(String name) {
    this.name = name;
  }

  /**
   * Adds an action to the ESD sequence.
   *
   * @param action action to execute
   * @param delay delay in seconds before executing this action (relative to previous action)
   * @throws IllegalArgumentException if the action is null or the delay is negative or nonfinite
   */
  public void addAction(LogicAction action, double delay) {
    if (action == null || !Double.isFinite(delay) || delay < 0.0) {
      throw new IllegalArgumentException("ESD action requires a non-null operation and finite nonnegative delay");
    }
    actions.add(new ActionWithDelay(action, delay));
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
    if (state == LogicState.PAUSED) {
      state = LogicState.RUNNING;
      return;
    }
    if (state == LogicState.IDLE || state == LogicState.COMPLETED || state == LogicState.FAILED) {
      resetActions();
      state = LogicState.RUNNING;
      currentActionIndex = 0;
      elapsedTime = 0.0;
      currentDelay = 0.0;
    }
  }

  /**
   * Clears action bookkeeping for a new demand without resetting physical equipment.
   */
  private void resetActions() {
    for (ActionWithDelay actionWithDelay : actions) {
      actionWithDelay.action.reset();
    }
  }

  @Override
  public void deactivate() {
    if (state == LogicState.RUNNING) {
      state = LogicState.PAUSED;
    }
  }

  @Override
  public boolean reset() {
    resetActions();
    state = LogicState.IDLE;
    currentActionIndex = 0;
    elapsedTime = 0.0;
    currentDelay = 0.0;
    return true;
  }

  /**
   * Advances the active sequence by a finite nonnegative interval.
   *
   * @param timeStep physical interval in seconds; zero permits an instantaneous demand evaluation
   * @throws IllegalArgumentException if the interval is negative or nonfinite
   */
  @Override
  public void execute(double timeStep) {
    if (!Double.isFinite(timeStep) || timeStep < 0.0) {
      throw new IllegalArgumentException("ESD time step must be finite and nonnegative");
    }
    if (state != LogicState.RUNNING) {
      return;
    }

    elapsedTime += timeStep;

    // Process current action
    if (currentActionIndex < actions.size()) {
      ActionWithDelay currentActionWithDelay = actions.get(currentActionIndex);

      // Wait for delay
      if (currentDelay < currentActionWithDelay.delay) {
        currentDelay += timeStep;
        return;
      }

      // Execute action if not yet executed
      currentActionWithDelay.action.execute();

      // Check if action is complete
      if (currentActionWithDelay.action.isComplete()) {
        // Move to next action
        currentActionIndex++;
        currentDelay = 0.0;

        if (currentActionIndex >= actions.size()) {
          state = LogicState.COMPLETED;
        }
      }
    } else {
      state = LogicState.COMPLETED;
    }
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
    if (hasNestedActionCycle()) {
      return Collections.emptyList();
    }
    List<ProcessEquipmentInterface> targets = new ArrayList<ProcessEquipmentInterface>();
    Set<ProcessEquipmentInterface> seen = Collections
        .newSetFromMap(new IdentityHashMap<ProcessEquipmentInterface, Boolean>());
    for (ActionWithDelay step : actions) {
      for (ProcessEquipmentInterface target : step.action.getTargetEquipment()) {
        if (target != null && seen.add(target)) {
          targets.add(target);
        }
      }
    }
    return Collections.unmodifiableList(targets);
  }

  /**
   * Reports incomplete action-target identity separately from any partial target list.
   *
   * @return null for complete native action coverage, otherwise an actionable finding
   */
  public String getTargetEquipmentCoverageIssue() {
    if (hasNestedActionCycle()) {
      return "Cyclic native action graph has no complete physical target identity";
    }
    for (int index = 0; index < actions.size(); index++) {
      LogicAction action = actions.get(index).action;
      if (!action.isTargetEquipmentIdentityComplete()) {
        return "ESD action " + index + " (" + action.getClass().getName()
            + ") does not expose complete physical target identity";
      }
    }
    return null;
  }

  /**
   * Checks known composite action structure before invoking recursive target getters.
   *
   * @return true when a native conditional/parallel branch contains an ancestor action
   */
  private boolean hasNestedActionCycle() {
    for (ActionWithDelay step : actions) {
      if (hasNestedActionCycle(step.action, new IdentityHashMap<LogicAction, Boolean>())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Detects cycles without executing actions or inspecting user callback state.
   *
   * @param action action being inspected
   * @param ancestors identities on the current branch
   * @return true if the native composite structure repeats an ancestor
   */
  private static boolean hasNestedActionCycle(LogicAction action, IdentityHashMap<LogicAction, Boolean> ancestors) {
    if (action == null) {
      return false;
    }
    if (ancestors.put(action, Boolean.TRUE) != null) {
      return true;
    }
    boolean cyclic = false;
    if (action.getClass() == ParallelActionGroup.class) {
      for (LogicAction child : ((ParallelActionGroup) action).getActions()) {
        cyclic |= hasNestedActionCycle(child, ancestors);
      }
    } else if (action.getClass() == ConditionalAction.class) {
      cyclic = hasNestedActionCycle(((ConditionalAction) action).getPrimaryAction(), ancestors)
          || hasNestedActionCycle(((ConditionalAction) action).getAlternativeAction(), ancestors);
    }
    ancestors.remove(action);
    return cyclic;
  }

  /**
   * Reads the ordered native trip-action definition without issuing any equipment commands.
   *
   * <p>
   * The bounded supported policy contains exact native trip actions and exact parallel groups. Other actions,
   * overriding subclasses, empty groups and cycles are explicitly unsupported. This does not prevent their legacy
   * execution, but they cannot acquire qualified closure semantics from a matching name/class/target list. Actual
   * reference membership and actuator settings must also be validated separately against the configured process.
   * </p>
   *
   * @return deterministic action tree, delays, physical target identifiers and policy coverage
   */
  public Map<String, Object> getTripActionConfiguration() {
    Map<String, Object> configuration = new LinkedHashMap<String, Object>();
    configuration.put("schemaVersion", "neqsim.esd_trip_action_configuration.v1");
    configuration.put("logicClass", getClass().getName());
    configuration.put("logicName", name);
    List<Map<String, Object>> steps = new ArrayList<Map<String, Object>>();
    boolean supported = getClass() == ESDLogic.class && !actions.isEmpty();
    for (ActionWithDelay step : actions) {
      Map<String, Object> definition = new LinkedHashMap<String, Object>();
      definition.put("delaySeconds", Double.valueOf(step.delay));
      Map<String, Object> action = describeTripAction(step.action, new IdentityHashMap<LogicAction, Boolean>());
      definition.put("action", action);
      supported &= Double.isFinite(step.delay) && step.delay >= 0.0
          && Boolean.TRUE.equals(action.get("supportedTripPolicy"));
      steps.add(definition);
    }
    configuration.put("steps", steps);
    configuration.put("supportedTripPolicy", Boolean.valueOf(supported));
    return configuration;
  }

  /**
   * Describes the supported native closure subset, preserving parallel structure and avoiding cycles.
   *
   * @param action configured action
   * @param ancestors action identities on the current recursion path
   * @return action definition with explicit support status
   */
  private static Map<String, Object> describeTripAction(LogicAction action,
      IdentityHashMap<LogicAction, Boolean> ancestors) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("actionClass", action == null ? "MISSING" : action.getClass().getName());
    boolean supported = false;
    if (action != null && ancestors.put(action, Boolean.TRUE) == null) {
      if (action.getClass() == TripValveAction.class) {
        List<ProcessEquipmentInterface> targets = action.getTargetEquipment();
        List<Map<String, Object>> identities = new ArrayList<Map<String, Object>>();
        for (ProcessEquipmentInterface target : targets) {
          Map<String, Object> identity = new LinkedHashMap<String, Object>();
          identity.put("tag", target.getName());
          identity.put("equipmentClass", target.getClass().getName());
          if (target.getClass() == ESDValve.class) {
            identity.put("strokeTimeSeconds", Double.valueOf(((ESDValve) target).getStrokeTime()));
            identity.put("failSafePositionPercent", Double.valueOf(((ESDValve) target).getFailSafePosition()));
            identity.put("tripEffect", "DEENERGIZE_TO_CONFIGURED_FAIL_SAFE_POSITION");
          }
          identities.add(identity);
        }
        result.put("targets", identities);
        supported = targets.size() == 1 && targets.get(0).getClass() == ESDValve.class
            && action.isTargetEquipmentIdentityComplete();
      } else if (action.getClass() == TripCompressorAction.class) {
        TripCompressorAction stop = (TripCompressorAction) action;
        Compressor compressor = stop.getCompressor();
        List<Map<String, Object>> identities = new ArrayList<Map<String, Object>>();
        Map<String, Object> identity = new LinkedHashMap<String, Object>();
        identity.put("tag", compressor.getName());
        identity.put("equipmentClass", compressor.getClass().getName());
        identity.put("tripEffect", "NATIVE_COMPRESSOR_STOP_PROFILE");
        identity.put("shutdownType", stop.getShutdownType().name());
        identity.put("stoppedSpeedRpm", Double.valueOf(stop.getStoppedSpeedRpm()));
        identity.put("commandAvailable", Boolean.valueOf(stop.isCommandAvailable()));
        identity.put("transientCalculationMode", compressor.getTransientCalculationMode().name());
        identity.put("maxDecelerationRpmPerSecond", Double.valueOf(compressor.getMaxDecelerationRate()));
        identity.put("rotationalInertiaKgM2", Double.valueOf(compressor.getRotationalInertia()));
        identity.put("driverModel",
            compressor.getDriver() == null ? "NONE_PROFILE_RATE_ONLY" : compressor.getDriver().getClass().getName());
        ShutdownProfile profile = compressor.getShutdownProfile();
        if (profile == null) {
          profile = new ShutdownProfile(stop.getShutdownType(), compressor.getSpeed());
        }
        Map<String, Object> profileValues = new LinkedHashMap<String, Object>();
        profileValues.put("normalRampRpmPerSecond", Double.valueOf(profile.getNormalRampRate()));
        profileValues.put("rapidRampRpmPerSecond", Double.valueOf(profile.getRapidRampRate()));
        profileValues.put("emergencyRampRpmPerSecond", Double.valueOf(profile.getEmergencyRampRate()));
        profileValues.put("coastdownSeconds", Double.valueOf(profile.getCoastdownTime()));
        profileValues.put("idleSpeedRpm", Double.valueOf(profile.getMinimumIdleSpeed()));
        profileValues.put("idleRundownSeconds", Double.valueOf(profile.getIdleRundownTime()));
        identity.put("nativeShutdownProfile", profileValues);
        identities.add(identity);
        result.put("targets", identities);
        supported = compressor.getClass() == Compressor.class && stop.isTargetEquipmentIdentityComplete()
            && Double.isFinite(stop.getStoppedSpeedRpm()) && stop.getStoppedSpeedRpm() >= 0.0;
      } else if (action.getClass() == ParallelActionGroup.class) {
        List<Map<String, Object>> children = new ArrayList<Map<String, Object>>();
        supported = !((ParallelActionGroup) action).getActions().isEmpty();
        for (LogicAction child : ((ParallelActionGroup) action).getActions()) {
          Map<String, Object> definition = describeTripAction(child, ancestors);
          children.add(definition);
          supported &= Boolean.TRUE.equals(definition.get("supportedTripPolicy"));
        }
        result.put("children", children);
      }
      ancestors.remove(action);
    } else if (action != null) {
      result.put("cycleDetected", Boolean.TRUE);
    }
    result.put("supportedTripPolicy", Boolean.valueOf(supported));
    return result;
  }

  @Override
  public String getStatusDescription() {
    if (state == LogicState.IDLE) {
      return name + " - IDLE (not triggered)";
    } else if (state == LogicState.RUNNING) {
      if (currentActionIndex < actions.size()) {
        ActionWithDelay current = actions.get(currentActionIndex);
        return String.format("%s - RUNNING (Step %d/%d: %s, delay: %.1fs)", name, currentActionIndex + 1,
            actions.size(), current.action.getDescription(), Math.max(0, current.delay - currentDelay));
      } else {
        return name + " - RUNNING (finalizing)";
      }
    } else if (state == LogicState.COMPLETED) {
      return String.format("%s - COMPLETED (%.1fs)", name, elapsedTime);
    } else {
      return name + " - " + state.toString();
    }
  }

  /**
   * Gets the total number of actions in this ESD logic.
   *
   * @return number of actions
   */
  public int getActionCount() {
    return actions.size();
  }

  /**
   * Gets the current action index (0-based).
   *
   * @return current action index, or -1 if not running
   */
  public int getCurrentActionIndex() {
    return state == LogicState.RUNNING ? currentActionIndex : -1;
  }

  /**
   * Gets the total elapsed time since activation.
   *
   * @return elapsed time in seconds
   */
  public double getElapsedTime() {
    return elapsedTime;
  }

  /**
   * Internal class to store an action with its delay.
   *
   * @author ESOL
   * @version 1.0
   */
  private static class ActionWithDelay implements Serializable {
    private static final long serialVersionUID = 1L;
    private transient LogicAction action;
    final double delay;

    /**
     * Creates one serializable sequence step.
     *
     * @param action operation to execute
     * @param delay delay relative to the previous completed action, in seconds
     */
    ActionWithDelay(LogicAction action, double delay) {
      this.action = action;
      this.delay = delay;
    }

    /**
     * Writes the complete callback graph explicitly; unsupported payloads fail at write time.
     *
     * @param output destination stream
     * @throws IOException if a callback or payload cannot be serialized
     */
    private void writeObject(ObjectOutputStream output) throws IOException {
      output.defaultWriteObject();
      output.writeObject(action);
    }

    /**
     * Restores the actual action reference with graph aliases preserved.
     *
     * @param input source stream
     * @throws IOException if the action cannot be read or is missing
     * @throws ClassNotFoundException if an action class is unavailable
     */
    private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
      input.defaultReadObject();
      Object restored = input.readObject();
      if (!(restored instanceof LogicAction)) {
        throw new InvalidObjectException("ESD action payload is missing or invalid");
      }
      action = (LogicAction) restored;
    }

  }

  /**
   * Restores the complete action graph or rejects legacy streams that discarded that graph.
   *
   * <p>
   * Earlier serialized versions used a transient action list. Such a stream cannot reconstruct the safety sequence and
   * must not be accepted as an empty successful sequence. Custom action graphs must themselves be serializable;
   * ordinary Java serialization rejects unsupported payloads at write time.
   * </p>
   *
   * @param input serialized logic state
   * @throws IOException if the stream cannot be read or has no action definition
   * @throws ClassNotFoundException if an action type is unavailable
   */
  private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
    input.defaultReadObject();
    if (actions == null) {
      throw new InvalidObjectException(
          "ESD action definitions are absent; reconstruct legacy logic from its governed configuration");
    }
  }
}
