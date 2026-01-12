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

  /**
   * Get remediation advice for this exception.
   * 
   * <p>
   * Returns a hint on how to properly initialize the system. AI agents can use this to
   * self-correct.
   * </p>
   * 
   * @return remediation advice string
   */
  public String getRemediation() {
    String msg = getMessage();
    if (msg != null && msg.contains("Method ")) {
      // Extract the method name from the message
      int methodIdx = msg.indexOf("Method ");
      String initMethod = msg.substring(methodIdx + 7).replace(" must be called.", "");
      return "System is not properly initialized. Call: " + initMethod
          + "\n\nCommon initialization sequence:\n"
          + "1. system.setMixingRule(\"classic\") or system.setMixingRule(2)\n"
          + "2. ThermodynamicOperations ops = new ThermodynamicOperations(system)\n"
          + "3. ops.TPflash() or ops.PVTsimulation()";
    }
    return "System is not properly initialized. Try:\n"
        + "1. Ensure all components are added with addComponent()\n"
        + "2. Call setMixingRule() before calculations\n"
        + "3. Call init(0) then init(1) for thermodynamic properties\n"
        + "4. For streams: call run() before accessing properties";
  }
}
