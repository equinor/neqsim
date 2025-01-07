package neqsim.util.exception;

/**
 * <p>
 * InvalidInputException class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class InvalidInputException extends neqsim.util.exception.ThermoException {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructs an <code>InvalidInputException</code> with a default message like:
   *
   * Input " + inputName + " was invalid.
   *
   * @param className Class that exception is raised from
   * @param methodName Method that exception is raised from
   * @param inputName Name of invalid input
   */
  public InvalidInputException(String className, String methodName, String inputName) {
    super(className, methodName, "Input " + inputName + " was invalid.");
  }

  /**
   * Constructs an <code>InvalidInputException</code> with a message like:
   *
   * "Input " + inputName + " " + msg
   *
   * @param className Class that exception is raised from
   * @param methodName Method that exception is raised from
   * @param inputName Name of invalid input
   * @param msg error message detailing input problem
   */
  public InvalidInputException(String className, String methodName, String inputName, String msg) {
    super(className, methodName, "Input " + inputName + " " + msg);
  }

  /**
   * Constructs an <code>InvalidInputException</code> with a default message.
   *
   * @param obj Object that exception is raised from
   * @param methodName Method that exception is raised from
   * @param inputName Name of invalid input
   */
  public InvalidInputException(Object obj, String methodName, String inputName) {
    this(obj.getClass().getSimpleName(), methodName, inputName);
  }

  /**
   * Constructs an <code>InvalidInputException</code> with a default message.
   *
   * @param obj Object that exception is raised from
   * @param methodName Method that exception is raised from
   * @param inputName Name of invalid input
   * @param msg error message detailing input problem
   */
  public InvalidInputException(Object obj, String methodName, String inputName, String msg) {
    this(obj.getClass().getSimpleName(), methodName, inputName, msg);
  }
}
