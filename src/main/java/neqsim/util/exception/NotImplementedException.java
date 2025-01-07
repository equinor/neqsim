package neqsim.util.exception;

/**
 * <p>
 * NotImplementedException class.
 * </p>
 *
 * @author Åsmund Våge Fannemel
 */
public class NotImplementedException extends neqsim.util.exception.ThermoException {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructs a <code>NotImplementedException</code> with a standard error message.
   *
   * @param className Class that exception is raised from
   * @param methodName Method that exception is raised from
   */
  public NotImplementedException(String className, String methodName) {
    super(className, methodName, "Function not implemented");
  }

  /**
   * Constructs a <code>NotImplementedException</code> with a standard error message.
   *
   * @param obj object that exception is raised from
   * @param methodName method that exception is raised from
   */
  public NotImplementedException(Object obj, String methodName) {
    this(obj.getClass().getSimpleName(), methodName);
  }
}
