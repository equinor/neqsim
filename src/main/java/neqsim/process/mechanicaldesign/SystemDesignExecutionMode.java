package neqsim.process.mechanicaldesign;

/**
 * Controls how a whole-process mechanical design calculation handles equipment failures.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public enum SystemDesignExecutionMode {
  /** Continue with independent equipment and return a structured partial result. */
  BEST_EFFORT,

  /** Stop after the first failed equipment calculation and throw an exception with the partial result. */
  FAIL_FAST
}
