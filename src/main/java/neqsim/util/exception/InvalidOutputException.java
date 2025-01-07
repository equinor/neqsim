package neqsim.util.exception;

/**
 * <p>
 * InvalidOutputException class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class InvalidOutputException extends neqsim.util.exception.ThermoException {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructs an <code>InvalidOutputException</code> with a default message.
   *
   * @param className Class that exception is raised from
   * @param methodName Method that exception is raised from
   * @param outputName Name of invalid output
   */
  public InvalidOutputException(String className, String methodName, String outputName) {
    super(className, methodName, "output " + outputName + " was invalid.");
  }

  /**
   * Constructs an <code>InvalidOutputException</code> with the specified detail message.
   *
   * @param className Class that exception is raised from
   * @param methodName Method that exception is raised from
   * @param outputName Name of invalid output
   * @param msg error message detailing output problem
   */
  public InvalidOutputException(String className, String methodName, String outputName,
      String msg) {
    super(className, methodName, "output " + outputName + " " + msg);
  }

  /**
   * Constructs an <code>InvalidOutputException</code> with a default message.
   *
   * @param obj Object that exception is raised from
   * @param methodName Method that exception is raised from
   * @param outputName Name of invalid output
   */
  public InvalidOutputException(Object obj, String methodName, String outputName) {
    this(obj.getClass().getSimpleName(), methodName, outputName);
  }

  /**
   * Constructs an <code>InvalidOutputException</code> with a default message.
   *
   * @param obj Object that exception is raised from
   * @param methodName Method that exception is raised from
   * @param outputName Name of invalid output
   * @param msg error message detailing output problem
   */
  public InvalidOutputException(Object obj, String methodName, String outputName, String msg) {
    this(obj.getClass().getSimpleName(), methodName, outputName, msg);
  }
}
