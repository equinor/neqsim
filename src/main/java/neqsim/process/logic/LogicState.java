package neqsim.process.logic;

/**
 * Represents the execution state of process logic.
 *
 * @author ESOL
 * @version 1.0
 */
public enum LogicState {
  /**
   * Logic is idle and not executing.
   */
  IDLE,

  /**
   * Logic is actively running through its steps.
   */
  RUNNING,

  /**
   * Logic execution is paused.
   */
  PAUSED,

  /**
   * Logic has completed successfully.
   */
  COMPLETED,

  /**
   * Logic execution failed due to timeout, condition failure, or error.
   */
  FAILED,

  /**
   * Logic is waiting for permissive conditions before proceeding.
   */
  WAITING_PERMISSIVES
}
