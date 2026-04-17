package neqsim.process.controllerdevice;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Sequential Function Chart (SFC) implementation following the IEC 61131-3 standard concept. An SFC
 * models a sequence of discrete steps connected by transitions with guard conditions. Each step can
 * have entry, active, and exit actions. The chart advances when the active step's outgoing
 * transition guard evaluates to true.
 *
 * <p>
 * Typical usage in NeqSim dynamic simulation:
 * </p>
 * <ul>
 * <li>Model startup/shutdown sequences for compressors, separators, columns</li>
 * <li>Implement emergency shutdown (ESD) logic</li>
 * <li>Control batch operations with defined phases</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public class SequentialFunctionChart implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(SequentialFunctionChart.class);

  private final String name;
  private final Map<String, SfcStep> steps = new LinkedHashMap<>();
  private final List<SfcTransition> transitions = new ArrayList<>();
  private String activeStepName;
  private String initialStepName;
  private boolean running = false;
  private double elapsedTimeInStep = 0.0;
  private double totalElapsedTime = 0.0;
  private final List<String> eventHistory = new ArrayList<>();

  /**
   * Constructs a new SFC with the given name.
   *
   * @param name the chart name
   */
  public SequentialFunctionChart(String name) {
    this.name = name;
  }

  /**
   * Adds a step to the SFC.
   *
   * @param step the step to add
   */
  public void addStep(SfcStep step) {
    steps.put(step.getName(), step);
    if (initialStepName == null) {
      initialStepName = step.getName();
    }
  }

  /**
   * Adds a transition between two steps.
   *
   * @param fromStep source step name
   * @param toStep target step name
   * @param guard the boolean condition that must be true to advance
   */
  public void addTransition(String fromStep, String toStep, BooleanSupplier guard) {
    transitions.add(new SfcTransition(fromStep, toStep, guard));
  }

  /**
   * Adds a timed transition that fires after the specified duration in the source step.
   *
   * @param fromStep source step name
   * @param toStep target step name
   * @param delaySeconds time in seconds the step must be active before transition fires
   */
  public void addTimedTransition(String fromStep, String toStep, double delaySeconds) {
    final double delay = delaySeconds;
    transitions.add(new SfcTransition(fromStep, toStep, new BooleanSupplier() {
      @Override
      public boolean getAsBoolean() {
        return elapsedTimeInStep >= delay;
      }
    }));
  }

  /**
   * Sets the initial step by name.
   *
   * @param stepName the initial step name (must have been added via addStep)
   */
  public void setInitialStep(String stepName) {
    if (steps.containsKey(stepName)) {
      this.initialStepName = stepName;
    }
  }

  /**
   * Starts or restarts the SFC from the initial step.
   */
  public void start() {
    if (initialStepName == null || !steps.containsKey(initialStepName)) {
      logger.warn("SFC {} has no valid initial step", name);
      return;
    }
    activeStepName = initialStepName;
    elapsedTimeInStep = 0.0;
    totalElapsedTime = 0.0;
    running = true;
    eventHistory.clear();
    logEvent("STARTED in step " + activeStepName);

    SfcStep activeStep = steps.get(activeStepName);
    if (activeStep != null && activeStep.getEntryAction() != null) {
      activeStep.getEntryAction().run();
    }
  }

  /**
   * Stops the SFC.
   */
  public void stop() {
    if (running && activeStepName != null) {
      SfcStep current = steps.get(activeStepName);
      if (current != null && current.getExitAction() != null) {
        current.getExitAction().run();
      }
    }
    running = false;
    logEvent("STOPPED");
  }

  /**
   * Advances the SFC by one timestep. Evaluates transitions and executes actions.
   *
   * @param dt timestep in seconds
   */
  public void runStep(double dt) {
    if (!running || activeStepName == null) {
      return;
    }
    elapsedTimeInStep += dt;
    totalElapsedTime += dt;

    // Execute active action of current step
    SfcStep currentStep = steps.get(activeStepName);
    if (currentStep != null && currentStep.getActiveAction() != null) {
      currentStep.getActiveAction().run();
    }

    // Check transitions
    for (SfcTransition transition : transitions) {
      if (transition.getFromStep().equals(activeStepName)) {
        try {
          if (transition.getGuard().getAsBoolean()) {
            advanceTo(transition.getToStep());
            break;
          }
        } catch (Exception e) {
          logger.warn("SFC {} transition guard evaluation failed: {}", name, e.getMessage());
        }
      }
    }
  }

  /**
   * Advances from the current step to the target step.
   *
   * @param targetStepName target step name
   */
  private void advanceTo(String targetStepName) {
    SfcStep current = steps.get(activeStepName);
    if (current != null && current.getExitAction() != null) {
      current.getExitAction().run();
    }

    logEvent(activeStepName + " -> " + targetStepName);
    activeStepName = targetStepName;
    elapsedTimeInStep = 0.0;

    SfcStep next = steps.get(activeStepName);
    if (next != null && next.getEntryAction() != null) {
      next.getEntryAction().run();
    }
  }

  /**
   * Gets the name of the currently active step.
   *
   * @return the active step name, or null if not running
   */
  public String getActiveStepName() {
    return activeStepName;
  }

  /**
   * Returns whether the SFC is currently running.
   *
   * @return true if running
   */
  public boolean isRunning() {
    return running;
  }

  /**
   * Gets the time elapsed in the current step.
   *
   * @return elapsed time in seconds
   */
  public double getElapsedTimeInStep() {
    return elapsedTimeInStep;
  }

  /**
   * Gets the total elapsed time since start.
   *
   * @return total elapsed time in seconds
   */
  public double getTotalElapsedTime() {
    return totalElapsedTime;
  }

  /**
   * Gets the SFC name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the event history log.
   *
   * @return list of event strings
   */
  public List<String> getEventHistory() {
    return eventHistory;
  }

  /**
   * Gets all step names.
   *
   * @return list of step names in insertion order
   */
  public List<String> getStepNames() {
    return new ArrayList<>(steps.keySet());
  }

  private void logEvent(String message) {
    String entry = String.format("t=%.1f: %s", totalElapsedTime, message);
    eventHistory.add(entry);
    logger.debug("SFC [{}] {}", name, entry);
  }

  /**
   * A step in the Sequential Function Chart. Each step has a name and optional entry, active, and
   * exit actions following IEC 61131-3 conventions.
   */
  public static class SfcStep implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private transient Runnable entryAction;
    private transient Runnable activeAction;
    private transient Runnable exitAction;

    /**
     * Constructs a step with the given name.
     *
     * @param name the step name
     */
    public SfcStep(String name) {
      this.name = name;
    }

    /**
     * Gets the step name.
     *
     * @return the name
     */
    public String getName() {
      return name;
    }

    /**
     * Sets the entry action executed once when transitioning into this step.
     *
     * @param action the entry action
     */
    public void setEntryAction(Runnable action) {
      this.entryAction = action;
    }

    /**
     * Gets the entry action.
     *
     * @return the entry action or null
     */
    public Runnable getEntryAction() {
      return entryAction;
    }

    /**
     * Sets the active action executed every timestep while this step is active.
     *
     * @param action the active action
     */
    public void setActiveAction(Runnable action) {
      this.activeAction = action;
    }

    /**
     * Gets the active action.
     *
     * @return the active action or null
     */
    public Runnable getActiveAction() {
      return activeAction;
    }

    /**
     * Sets the exit action executed once when leaving this step.
     *
     * @param action the exit action
     */
    public void setExitAction(Runnable action) {
      this.exitAction = action;
    }

    /**
     * Gets the exit action.
     *
     * @return the exit action or null
     */
    public Runnable getExitAction() {
      return exitAction;
    }
  }

  /**
   * A transition between two steps in the SFC. A transition has a source step, target step, and a
   * guard condition.
   */
  public static class SfcTransition implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String fromStep;
    private final String toStep;
    private transient BooleanSupplier guard;

    /**
     * Constructs a transition.
     *
     * @param fromStep source step name
     * @param toStep target step name
     * @param guard the guard condition
     */
    public SfcTransition(String fromStep, String toStep, BooleanSupplier guard) {
      this.fromStep = fromStep;
      this.toStep = toStep;
      this.guard = guard;
    }

    /**
     * Gets the source step name.
     *
     * @return the source step name
     */
    public String getFromStep() {
      return fromStep;
    }

    /**
     * Gets the target step name.
     *
     * @return the target step name
     */
    public String getToStep() {
      return toStep;
    }

    /**
     * Gets the guard condition.
     *
     * @return the guard supplier
     */
    public BooleanSupplier getGuard() {
      return guard;
    }
  }
}
