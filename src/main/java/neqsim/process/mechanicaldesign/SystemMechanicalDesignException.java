package neqsim.process.mechanicaldesign;

/**
 * Signals a fail-fast system mechanical design failure while preserving the structured partial result.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class SystemMechanicalDesignException extends RuntimeException {
  private static final long serialVersionUID = 1000L;

  private final SystemMechanicalDesignResult partialResult;

  /**
   * Create a system design exception.
   *
   * @param message failure description
   * @param cause original equipment calculation exception
   * @param partialResult structured result including calculated, failed, and skipped equipment
   */
  public SystemMechanicalDesignException(String message, Throwable cause, SystemMechanicalDesignResult partialResult) {
    super(message, cause);
    this.partialResult = partialResult;
  }

  /** @return structured result available when fail-fast execution stopped */
  public SystemMechanicalDesignResult getPartialResult() {
    return partialResult;
  }
}
