package neqsim.process.equipment.adsorber;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Controller for adsorption/desorption cycle scheduling.
 *
 * <p>
 * Manages the sequence of cycle phases for pressure-swing adsorption (PSA), temperature-swing
 * adsorption (TSA), and vacuum-swing adsorption (VSA) processes. Provides automatic phase
 * transitions based on time or breakthrough detection.
 * </p>
 *
 * <p>
 * A typical PSA cycle consists of:
 * </p>
 * <ol>
 * <li><strong>Adsorption</strong> — feed gas passes through the bed at high pressure; target
 * species are adsorbed.</li>
 * <li><strong>Blowdown</strong> — bed pressure is reduced to desorb the adsorbed species (PSA) or
 * bed temperature is raised (TSA).</li>
 * <li><strong>Purge</strong> — clean gas sweeps the bed to remove remaining adsorbate.</li>
 * <li><strong>Re-pressurisation</strong> — bed pressure is brought back to adsorption
 * pressure.</li>
 * </ol>
 *
 * <p>
 * Multi-bed configurations can be coordinated by assigning a separate controller to each bed and
 * staggering their phase schedules.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class AdsorptionCycleController implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1002L;

  /** Logger for this class. */
  private static final Logger logger = LogManager.getLogger(AdsorptionCycleController.class);

  /**
   * Enumeration of the possible phases in an adsorption/desorption cycle.
   */
  public enum CyclePhase {
    /** Normal adsorption at high pressure / low temperature. */
    ADSORPTION,
    /** Co-current depressurisation (optional). */
    COCURRENT_DEPRESSURISATION,
    /** Counter-current blowdown to low pressure. */
    BLOWDOWN,
    /** Purge with clean gas at low pressure. */
    PURGE,
    /** Re-pressurisation with product or feed gas. */
    REPRESSURISATION,
    /** General desorption (TSA heating or VSA vacuum). */
    DESORPTION,
    /** Cooling step after TSA heating. */
    COOLING,
    /** Bed on standby (isolated). */
    STANDBY
  }

  /**
   * Defines a single phase within a cycle schedule.
   */
  public static class PhaseStep implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1003L;

    /** The cycle phase. */
    private final CyclePhase phase;

    /** Duration of this phase step (seconds). */
    private final double duration;

    /** Target pressure for this step (bara), or -1 if not applicable. */
    private final double targetPressure;

    /** Target temperature for this step (K), or -1 if not applicable. */
    private final double targetTemperature;

    /**
     * Create a phase step with a duration.
     *
     * @param phase the cycle phase
     * @param duration duration in seconds
     */
    public PhaseStep(CyclePhase phase, double duration) {
      this(phase, duration, -1.0, -1.0);
    }

    /**
     * Create a phase step with a duration and target pressure.
     *
     * @param phase the cycle phase
     * @param duration duration in seconds
     * @param targetPressure target pressure (bara)
     * @param targetTemperature target temperature (K)
     */
    public PhaseStep(CyclePhase phase, double duration, double targetPressure,
        double targetTemperature) {
      this.phase = phase;
      this.duration = duration;
      this.targetPressure = targetPressure;
      this.targetTemperature = targetTemperature;
    }

    /**
     * Get the cycle phase.
     *
     * @return the cycle phase
     */
    public CyclePhase getPhase() {
      return phase;
    }

    /**
     * Get the step duration.
     *
     * @return duration in seconds
     */
    public double getDuration() {
      return duration;
    }

    /**
     * Get the target pressure.
     *
     * @return target pressure in bara, or -1 if not set
     */
    public double getTargetPressure() {
      return targetPressure;
    }

    /**
     * Get the target temperature.
     *
     * @return target temperature in K, or -1 if not set
     */
    public double getTargetTemperature() {
      return targetTemperature;
    }
  }

  /** The cycle schedule: an ordered list of phase steps. */
  private final List<PhaseStep> schedule = new ArrayList<PhaseStep>();

  /** Index of the current step in the schedule. */
  private int currentStepIndex = 0;

  /** Time elapsed within the current step (s). */
  private double timeInCurrentStep = 0.0;

  /** Total number of completed full cycles. */
  private int completedCycles = 0;

  /** Whether the cycle loops automatically. */
  private boolean autoLoop = true;

  /** Whether to transition on breakthrough detection instead of time. */
  private boolean transitionOnBreakthrough = false;

  /** The adsorption bed being controlled. */
  private transient AdsorptionBed bed;

  /**
   * Create a cycle controller.
   *
   * @param bed the adsorption bed to control
   */
  public AdsorptionCycleController(AdsorptionBed bed) {
    this.bed = bed;
  }

  /**
   * Create a default PSA cycle schedule.
   *
   * @param adsorptionTime adsorption phase duration (s)
   * @param blowdownTime blowdown duration (s)
   * @param purgeTime purge duration (s)
   * @param repressTime re-pressurisation duration (s)
   * @param lowPressure blowdown target pressure (bara)
   * @return this controller for chaining
   */
  public AdsorptionCycleController configurePSA(double adsorptionTime, double blowdownTime,
      double purgeTime, double repressTime, double lowPressure) {
    schedule.clear();
    schedule.add(new PhaseStep(CyclePhase.ADSORPTION, adsorptionTime));
    schedule.add(new PhaseStep(CyclePhase.BLOWDOWN, blowdownTime, lowPressure, -1.0));
    schedule.add(new PhaseStep(CyclePhase.PURGE, purgeTime, lowPressure, -1.0));
    schedule.add(new PhaseStep(CyclePhase.REPRESSURISATION, repressTime));
    currentStepIndex = 0;
    timeInCurrentStep = 0.0;
    logger.info("PSA cycle configured: " + schedule.size() + " steps");
    return this;
  }

  /**
   * Create a default TSA cycle schedule.
   *
   * @param adsorptionTime adsorption phase duration (s)
   * @param heatingTime heating/desorption duration (s)
   * @param coolingTime cooling duration (s)
   * @param desorptionTemperature regeneration temperature (K)
   * @return this controller for chaining
   */
  public AdsorptionCycleController configureTSA(double adsorptionTime, double heatingTime,
      double coolingTime, double desorptionTemperature) {
    schedule.clear();
    schedule.add(new PhaseStep(CyclePhase.ADSORPTION, adsorptionTime));
    schedule.add(new PhaseStep(CyclePhase.DESORPTION, heatingTime, -1.0, desorptionTemperature));
    schedule.add(new PhaseStep(CyclePhase.COOLING, coolingTime));
    currentStepIndex = 0;
    timeInCurrentStep = 0.0;
    logger.info("TSA cycle configured: " + schedule.size() + " steps");
    return this;
  }

  /**
   * Add a custom phase step to the schedule.
   *
   * @param step the phase step
   * @return this controller for chaining
   */
  public AdsorptionCycleController addStep(PhaseStep step) {
    schedule.add(step);
    return this;
  }

  /**
   * Advance the cycle by a time step. Checks whether the current step has elapsed and transitions
   * to the next phase if needed. Applies the current phase's operating conditions to the bed.
   *
   * @param dt time step size (seconds)
   * @param id calculation identifier
   */
  public void advance(double dt, UUID id) {
    if (schedule.isEmpty()) {
      return;
    }

    timeInCurrentStep += dt;

    PhaseStep currentStep = schedule.get(currentStepIndex);

    // Check if time to transition
    boolean shouldTransition = timeInCurrentStep >= currentStep.getDuration();
    if (transitionOnBreakthrough && currentStep.getPhase() == CyclePhase.ADSORPTION) {
      shouldTransition = shouldTransition || bed.isBreakthroughOccurred();
    }

    if (shouldTransition) {
      transitionToNextStep();
    }

    // Apply current step conditions to the bed
    applyStepConditions();
  }

  /**
   * Transition to the next step in the schedule.
   */
  private void transitionToNextStep() {
    currentStepIndex++;
    timeInCurrentStep = 0.0;

    if (currentStepIndex >= schedule.size()) {
      currentStepIndex = 0;
      completedCycles++;
      if (autoLoop) {
        logger.info("Cycle " + completedCycles + " completed, starting new cycle");
        // Reset breakthrough flag for next adsorption phase
        bed.resetBed();
        bed.initialiseTransientGrid();
      }
    }

    PhaseStep newStep = schedule.get(currentStepIndex);
    logger.info("Transitioning to phase: " + newStep.getPhase() + " (step " + (currentStepIndex + 1)
        + "/" + schedule.size() + ")");
  }

  /**
   * Apply the current step's operating conditions to the bed.
   */
  private void applyStepConditions() {
    PhaseStep step = schedule.get(currentStepIndex);

    switch (step.getPhase()) {
      case ADSORPTION:
        bed.setDesorptionMode(false);
        break;
      case BLOWDOWN:
      case DESORPTION:
      case PURGE:
        bed.setDesorptionMode(true);
        if (step.getTargetPressure() > 0) {
          bed.setDesorptionPressure(step.getTargetPressure());
        }
        if (step.getTargetTemperature() > 0) {
          bed.setDesorptionTemperature(step.getTargetTemperature());
        }
        break;
      case REPRESSURISATION:
      case COOLING:
        bed.setDesorptionMode(false);
        break;
      case STANDBY:
        // No flow through the bed
        break;
      default:
        break;
    }
  }

  /**
   * Get the current cycle phase.
   *
   * @return the current cycle phase
   */
  public CyclePhase getCurrentPhase() {
    if (schedule.isEmpty()) {
      return CyclePhase.ADSORPTION;
    }
    return schedule.get(currentStepIndex).getPhase();
  }

  /**
   * Get the number of completed cycles.
   *
   * @return completed cycle count
   */
  public int getCompletedCycles() {
    return completedCycles;
  }

  /**
   * Get the time elapsed in the current step.
   *
   * @return time in seconds
   */
  public double getTimeInCurrentStep() {
    return timeInCurrentStep;
  }

  /**
   * Set whether the cycle should loop automatically.
   *
   * @param autoLoop true to auto-loop
   */
  public void setAutoLoop(boolean autoLoop) {
    this.autoLoop = autoLoop;
  }

  /**
   * Set whether to transition out of adsorption phase on breakthrough detection.
   *
   * @param transition true to enable
   */
  public void setTransitionOnBreakthrough(boolean transition) {
    this.transitionOnBreakthrough = transition;
  }

  /**
   * Reset the cycle controller to the first step.
   */
  public void reset() {
    currentStepIndex = 0;
    timeInCurrentStep = 0.0;
    completedCycles = 0;
  }

  /**
   * Get the schedule.
   *
   * @return list of phase steps
   */
  public List<PhaseStep> getSchedule() {
    return new ArrayList<PhaseStep>(schedule);
  }
}
