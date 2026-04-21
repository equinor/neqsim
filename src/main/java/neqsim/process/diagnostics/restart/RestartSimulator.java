package neqsim.process.diagnostics.restart;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorState;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Simulates a restart sequence on a NeqSim process system using dynamic (transient) simulation.
 *
 * <p>
 * Executes the {@link RestartSequence} step by step, advancing the process simulation with
 * {@code ProcessSystem.runTransient()} at each timestep. Monitors process stability and records the
 * time-to-stable (MTTR).
 * </p>
 *
 * <p>
 * For operator actions and verification steps, the simulator assumes instant completion and moves
 * to the next step.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class RestartSimulator implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final ProcessSystem processSystem;

  /** Time step for dynamic simulation (seconds). */
  private double timeStep = 1.0;

  /** Maximum time to simulate before declaring failure (seconds). */
  private double maxSimulationTime = 3600.0;

  /** Number of consecutive stable timesteps required to declare stable operation. */
  private int stabilityWindowSteps = 30;

  /** Relative tolerance for stability detection. */
  private double stabilityTolerance = 0.01;

  /**
   * Constructs a restart simulator for a process system.
   *
   * @param processSystem the process system to simulate restart on
   */
  public RestartSimulator(ProcessSystem processSystem) {
    this.processSystem = Objects.requireNonNull(processSystem, "processSystem must not be null");
  }

  /**
   * Sets the dynamic simulation time step.
   *
   * @param dt time step in seconds
   */
  public void setTimeStep(double dt) {
    this.timeStep = dt;
  }

  /**
   * Sets the maximum simulation time.
   *
   * @param maxSeconds maximum time in seconds (default 3600)
   */
  public void setMaxSimulationTime(double maxSeconds) {
    this.maxSimulationTime = maxSeconds;
  }

  /**
   * Sets the stability detection parameters.
   *
   * @param windowSteps number of consecutive stable steps required
   * @param tolerance relative tolerance for stability
   */
  public void setStabilityParameters(int windowSteps, double tolerance) {
    this.stabilityWindowSteps = windowSteps;
    this.stabilityTolerance = tolerance;
  }

  /**
   * Simulates the restart sequence and returns the result.
   *
   * @param sequence the restart sequence to simulate
   * @return the simulation result with MTTR and step timings
   */
  public RestartSimulationResult simulate(RestartSequence sequence) {
    Objects.requireNonNull(sequence, "sequence must not be null");

    RestartSimulationResult result = new RestartSimulationResult();
    double currentTime = processSystem.getTime();
    double startTime = currentTime;
    UUID simId = UUID.randomUUID();

    // Execute each step
    for (RestartStep step : sequence.getSteps()) {
      double stepStartTime = currentTime;

      try {
        currentTime = executeStep(step, currentTime, simId);

        result.addStepRecord(new RestartSimulationResult.StepRecord(step.getStepNumber(),
            step.getDescription(), stepStartTime, currentTime, true, ""));
        step.markCompleted();
      } catch (Exception e) {
        result.addStepRecord(new RestartSimulationResult.StepRecord(step.getStepNumber(),
            step.getDescription(), stepStartTime, currentTime, false, e.getMessage()));
        result.addIssue("Step " + step.getStepNumber() + " failed: " + e.getMessage());

        // Check if a re-trip occurred
        if (hasReTripped()) {
          result.setOutcome(RestartSimulationResult.Outcome.RE_TRIPPED);
          result.addIssue("Process re-tripped during restart at step " + step.getStepNumber());
          result.setTotalTimeSeconds(currentTime - startTime);
          result.setTimeToStableSeconds(currentTime - startTime);
          return result;
        }
      }

      // Safety: don't exceed max simulation time
      if ((currentTime - startTime) > maxSimulationTime) {
        result.setOutcome(RestartSimulationResult.Outcome.FAILED);
        result.addIssue("Maximum simulation time exceeded (" + maxSimulationTime + " s)");
        result.setTotalTimeSeconds(currentTime - startTime);
        result.setTimeToStableSeconds(currentTime - startTime);
        return result;
      }
    }

    // Monitor for stability after all steps
    double stabilityStartTime = currentTime;
    boolean stable = monitorStability(currentTime, simId);
    currentTime = processSystem.getTime();

    result.setTotalTimeSeconds(currentTime - startTime);
    result.setTimeToStableSeconds(currentTime - startTime);

    if (stable) {
      result.setOutcome(RestartSimulationResult.Outcome.SUCCESS);
    } else {
      result.setOutcome(RestartSimulationResult.Outcome.PARTIAL_SUCCESS);
      result.addIssue("Process did not reach full stability within monitoring window");
    }

    // Record final process values
    recordFinalValues(result);

    return result;
  }

  /**
   * Executes a single restart step using dynamic simulation.
   *
   * @param step the step to execute
   * @param currentTime current simulation time
   * @param simId simulation UUID
   * @return the time after executing the step
   */
  private double executeStep(RestartStep step, double currentTime, UUID simId) {
    double stepDuration = step.getDurationSeconds();

    switch (step.getActionType()) {
      case WAIT_DURATION:
        return advanceTime(currentTime, stepDuration > 0 ? stepDuration : 60.0, simId);

      case COMPRESSOR_START:
        startCompressor(step.getTargetEquipment());
        return advanceTime(currentTime, 10.0, simId); // 10s for startup initiation

      case COMPRESSOR_RAMP:
        double rampTime =
            step.getRampRate() > 0 ? Math.abs(step.getTargetValue()) / step.getRampRate() : 120.0;
        return advanceTime(currentTime, rampTime, simId);

      case VALVE_ACTION:
        return advanceTime(currentTime, stepDuration > 0 ? stepDuration : 30.0, simId);

      case SETPOINT_CHANGE:
        return advanceTime(currentTime, stepDuration > 0 ? stepDuration : 10.0, simId);

      case WAIT_CONDITION:
        return advanceTime(currentTime, stepDuration > 0 ? stepDuration : 120.0, simId);

      case OPERATOR_ACTION:
      case VERIFICATION:
        // Instant completion in simulation (assumed already done)
        return advanceTime(currentTime, 5.0, simId);

      default:
        return advanceTime(currentTime, 30.0, simId);
    }
  }

  /**
   * Advances the process simulation by a specified duration.
   *
   * @param currentTime current simulation time
   * @param duration duration to advance in seconds
   * @param simId simulation UUID
   * @return the new current time
   */
  private double advanceTime(double currentTime, double duration, UUID simId) {
    int nSteps = Math.max(1, (int) (duration / timeStep));
    for (int i = 0; i < nSteps; i++) {
      processSystem.runTransient(timeStep, simId);
    }
    return currentTime + nSteps * timeStep;
  }

  /**
   * Starts a compressor by name.
   *
   * @param compressorName name of the compressor to start
   */
  private void startCompressor(String compressorName) {
    for (ProcessEquipmentInterface eq : processSystem.getUnitOperations()) {
      if (eq instanceof Compressor && eq.getName().equals(compressorName)) {
        Compressor comp = (Compressor) eq;
        if (comp.getOperatingState() == CompressorState.STOPPED
            || comp.getOperatingState() == CompressorState.STANDBY) {
          comp.setOperatingState(CompressorState.STARTING);
        }
        return;
      }
    }
  }

  /**
   * Monitors the process for stability after all restart steps are complete.
   *
   * @param startTime monitoring start time
   * @param simId simulation UUID
   * @return true if stable operation was achieved
   */
  private boolean monitorStability(double startTime, UUID simId) {
    int stableCount = 0;
    double prevPressure = 0.0;
    boolean firstStep = true;

    for (int i = 0; i < stabilityWindowSteps * 3; i++) {
      processSystem.runTransient(timeStep, simId);

      // Simple stability check based on outlet pressure variation
      double currentPressure = getRepresentativePressure();
      if (!firstStep && Math.abs(currentPressure) > 1e-10) {
        double relChange = Math.abs(currentPressure - prevPressure) / Math.abs(currentPressure);
        if (relChange < stabilityTolerance) {
          stableCount++;
        } else {
          stableCount = 0;
        }
      }
      prevPressure = currentPressure;
      firstStep = false;

      if (stableCount >= stabilityWindowSteps) {
        return true;
      }
    }
    return false;
  }

  /**
   * Gets a representative pressure value for stability checking.
   *
   * @return a representative pressure in bar
   */
  private double getRepresentativePressure() {
    for (ProcessEquipmentInterface eq : processSystem.getUnitOperations()) {
      try {
        if (eq.getOutletStreams() != null && !eq.getOutletStreams().isEmpty()) {
          return eq.getOutletStreams().get(0).getPressure();
        }
      } catch (Exception e) {
        // Skip
      }
    }
    return 0.0;
  }

  /**
   * Checks if a re-trip occurred during restart.
   *
   * @return true if any compressor is in TRIPPED state
   */
  private boolean hasReTripped() {
    for (ProcessEquipmentInterface eq : processSystem.getUnitOperations()) {
      if (eq instanceof Compressor) {
        Compressor comp = (Compressor) eq;
        if (comp.getOperatingState() == CompressorState.TRIPPED) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Records final process values after restart simulation.
   *
   * @param result the result to add values to
   */
  private void recordFinalValues(RestartSimulationResult result) {
    for (ProcessEquipmentInterface eq : processSystem.getUnitOperations()) {
      try {
        if (eq.getOutletStreams() != null && !eq.getOutletStreams().isEmpty()) {
          result.addFinalProcessValue(eq.getName() + ".outletPressure",
              eq.getOutletStreams().get(0).getPressure());
          result.addFinalProcessValue(eq.getName() + ".outletTemperature",
              eq.getOutletStreams().get(0).getTemperature());
        }
      } catch (Exception e) {
        // Skip equipment that can't report values
      }
    }
  }
}
