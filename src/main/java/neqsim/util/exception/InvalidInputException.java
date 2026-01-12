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

  /**
   * Get remediation advice for this exception.
   * 
   * <p>
   * Returns a hint on how to fix the invalid input. AI agents can use this to self-correct.
   * </p>
   * 
   * @return remediation advice string
   */
  public String getRemediation() {
    String msg = getMessage();
    if (msg == null) {
      return "Check input parameters and try again.";
    }

    // Provide context-specific remediation
    if (msg.contains("totalNumberOfMoles")) {
      return "Add components before calling init(): system.addComponent(\"methane\", 0.5)";
    }
    if (msg.contains("temperature") || msg.contains("Temperature")) {
      return "Set valid temperature (> 0 K): system.setTemperature(298.15)";
    }
    if (msg.contains("pressure") || msg.contains("Pressure")) {
      return "Set valid pressure (> 0 bar): system.setPressure(1.0)";
    }
    if (msg.contains("name can not be null") || msg.contains("name can not be empty")) {
      return "Provide a valid component name: system.addComponent(\"methane\", 0.5)";
    }
    if (msg.contains("composition")) {
      return "Ensure composition sums to 1.0 or use setMolarComposition() with valid values";
    }
    if (msg.contains("compNumber")) {
      return "Check component index is within valid range (0 to numberOfComponents-1)";
    }

    return "Verify input parameters match expected types and ranges.";
  }
}
