package neqsim.util.exception;

/**
 * <p>
 * IsNaNException class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class IsNaNException extends neqsim.util.exception.ThermoException {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  private final String paramName;

  /**
   * Constructs an <code>IsNaNException</code> with a detailed message.
   *
   * @param className Class that exception is raised from
   * @param methodName Method that exception is raised from
   * @param msg detailed message
   */
  public IsNaNException(String className, String methodName, String msg) {
    super(className, methodName, msg);
    this.paramName = msg;
  }

  /**
   * Constructs an <code>IsNaNException</code> with a default detail message.
   *
   * @param obj object that exception is raised from
   * @param methodName Method that exception is raised from
   * @param param the parameter that is NaN
   */
  public IsNaNException(Object obj, String methodName, String param) {
    this(obj.getClass().getSimpleName(), methodName, "Variable " + param + " is NaN");
  }

  /**
   * Get remediation advice for this exception.
   * 
   * <p>
   * Returns a hint on how to fix NaN calculation issues. AI agents can use this to self-correct.
   * </p>
   * 
   * @return remediation advice string
   */
  public String getRemediation() {
    return "Calculation produced NaN for: " + paramName + ". Try:\n"
        + "1. Check for division by zero (ensure non-zero denominators)\n"
        + "2. Verify input values are physically reasonable\n"
        + "3. Check temperature > 0 K and pressure > 0\n" + "4. Ensure mole fractions sum to 1.0\n"
        + "5. Use a more stable equation of state for extreme conditions";
  }
}
