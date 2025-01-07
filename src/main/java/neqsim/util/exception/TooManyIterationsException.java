package neqsim.util.exception;

/**
 * <p>
 * TooManyIterationsException class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TooManyIterationsException extends neqsim.util.exception.ThermoException {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructs a <code>TooManyIterationsException</code> with a standard error message.
   *
   * @param className Class that exception is raised from
   * @param methodName Method that exception is raised from
   * @param maxIterations the maximum number of iterations
   */
  public TooManyIterationsException(String className, String methodName, long maxIterations) {
    super(className, methodName, "Exceeded maximum iterations " + maxIterations);
  }

  /**
   * Constructs a <code>TooManyIterationsException</code> with a standard error message.
   *
   * @param obj object that exception is raised from
   * @param methodName method that exception is raised from
   * @param maxIterations the maximum number of iterations
   */
  public TooManyIterationsException(Object obj, String methodName, long maxIterations) {
    this(obj.getClass().getSimpleName(), methodName, maxIterations);
  }
}
