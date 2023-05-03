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
  private static final long serialVersionUID = 1000;

  /**
   * Constructs an <code>IsNaNException</code> with the specified detail message.
   *
   * @param msg the detail message.
   */
  @Deprecated
  public IsNaNException(String msg) {
    super(msg);
  }

  /**
   * Constructs an <code>IsNaNException</code> with a detailed message.
   *
   * @param className Class that exception is raised from
   * @param methodName Method that exception is raised from
   * @param msg detailed message
   */
  public IsNaNException(String className, String methodName, String msg) {
    super(className, methodName, "Variable " + msg);
  }

  /**
   * Constructs an <code>IsNaNException</code> with a default detail message.
   *
   * @param obj object that exception is raised from
   * @param methodName Method that exception is raised from
   * @param param the parameter that is NaN
   */
  public IsNaNException(Object obj, String methodName, String param) {
    this(obj.getClass().getSimpleName(), methodName, param + " is NaN");
  }
}
