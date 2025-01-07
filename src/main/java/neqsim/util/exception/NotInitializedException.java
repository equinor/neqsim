package neqsim.util.exception;

/**
 * <p>
 * NotInitializedException class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class NotInitializedException extends neqsim.util.exception.ThermoException {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructs an <code>NotInitializedException</code> with the specified detail message.
   *
   * @param className Class exception is raised from
   * @param methodName Method exception is raised from
   * @param msg Detailed error message
   */
  public NotInitializedException(String className, String methodName, String msg) {
    super(className, methodName, msg);
  }

  /**
   * Constructs an <code>NotInitializedException</code> with default detail message.
   *
   * @param className Class exception is raised from
   * @param methodName Method exception is raised from
   * @param parameter Parameter not initialized
   * @param initMethod Method to call to initialize parameter
   */
  public NotInitializedException(String className, String methodName, String parameter,
      String initMethod) {
    this(className, methodName,
        "Parameter " + parameter + " not initialized. Method " + initMethod + " must be called.");
  }

  /**
   * Constructs an <code>NotInitializedException</code> with the specified detail message.
   *
   * @param obj Object exception is raised from
   * @param methodName Method exception is raised from
   * @param msg Detailed error message
   */
  public NotInitializedException(Object obj, String methodName, String msg) {
    this(obj.getClass().getSimpleName(), methodName, msg);
  }

  /**
   * Constructs an <code>NotInitializedException</code> with default detail message.
   *
   * @param obj Object exception is raised from
   * @param methodName Method exception is raised from
   * @param parameter Parameter not initialized
   * @param initMethod Method to call to initialize parameter
   */
  public NotInitializedException(Object obj, String methodName, String parameter,
      String initMethod) {
    this(obj.getClass().getSimpleName(), methodName, parameter, initMethod);
  }
}
