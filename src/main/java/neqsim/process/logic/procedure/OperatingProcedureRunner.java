package neqsim.process.logic.procedure;

import neqsim.process.dynamics.TransientStepIdentifier;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Executes an operating procedure against deterministic native process timesteps.
 *
 * @author ESOL
 * @version 1.0
 */
public final class OperatingProcedureRunner {
  /** Prevents utility-class construction. */
  private OperatingProcedureRunner() {
  }

  /**
   * Runs one procedure from the caller-supplied process state.
   *
   * <p>
   * The initial zero-duration scan issues a first command without advancing process time. Every later physical interval
   * has a deterministic identifier. Repeating the same procedure against an equivalent restored process state therefore
   * preserves scan ordering and procedure evidence.
   * </p>
   *
   * @param process process model to advance
   * @param procedure configured operating procedure
   * @param timeStepSeconds nominal physical scan interval in seconds
   * @param maximumDurationSeconds maximum execution horizon in seconds
   * @param replayScope stable scope used to derive physical-step identities
   * @return immutable procedure result
   */
  public static OperatingProcedure.ExecutionResult run(ProcessSystem process, OperatingProcedure procedure,
      double timeStepSeconds, double maximumDurationSeconds, String replayScope) {
    if (process == null || procedure == null) {
      throw new IllegalArgumentException("Procedure runner requires process and procedure");
    }
    if (!Double.isFinite(timeStepSeconds) || timeStepSeconds <= 0.0 || !Double.isFinite(maximumDurationSeconds)
        || maximumDurationSeconds <= 0.0) {
      throw new IllegalArgumentException("Procedure runner durations must be finite and positive");
    }
    if (replayScope == null || replayScope.trim().isEmpty()) {
      throw new IllegalArgumentException("Procedure replay scope cannot be blank");
    }
    procedure.activate();
    procedure.execute(0.0);
    double time = 0.0;
    long physicalStepIndex = 0L;
    while (procedure.isActive() && time < maximumDurationSeconds) {
      double step = Math.min(timeStepSeconds, maximumDurationSeconds - time);
      try {
        process.runTransient(step, TransientStepIdentifier.deterministicPhysicalStep(replayScope, physicalStepIndex));
      } catch (RuntimeException exception) {
        procedure.abort("Process transient failed: " + exception.getMessage());
        break;
      }
      time += step;
      physicalStepIndex++;
      procedure.execute(step);
    }
    if (procedure.isActive()) {
      procedure.abort("Procedure exceeded runner horizon of " + maximumDurationSeconds + " s");
    }
    return procedure.snapshot();
  }
}
