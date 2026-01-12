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

  private final long maxIterations;

  /**
   * Constructs a <code>TooManyIterationsException</code> with a standard error message.
   *
   * @param className Class that exception is raised from
   * @param methodName Method that exception is raised from
   * @param maxIterations the maximum number of iterations
   */
  public TooManyIterationsException(String className, String methodName, long maxIterations) {
    super(className, methodName, "Exceeded maximum iterations " + maxIterations);
    this.maxIterations = maxIterations;
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

  /**
   * Get remediation advice for this exception.
   * 
   * <p>
   * Returns a hint on how to fix convergence issues. AI agents can use this to self-correct.
   * </p>
   * 
   * @return remediation advice string
   */
  public String getRemediation() {
    return "Solver did not converge within " + maxIterations + " iterations. Try:\n"
        + "1. Check initial conditions are physically reasonable\n"
        + "2. Simplify the fluid composition (fewer components)\n"
        + "3. Use a different equation of state\n"
        + "4. Increase max iterations if close to convergence\n"
        + "5. For distillation: use DAMPED solver instead of SEQUENTIAL";
  }
}
