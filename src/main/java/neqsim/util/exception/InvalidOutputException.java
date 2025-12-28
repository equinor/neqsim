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

  private final String outputName;

  /**
   * Constructs an <code>InvalidOutputException</code> with a default message.
   *
   * @param className Class that exception is raised from
   * @param methodName Method that exception is raised from
   * @param outputName Name of invalid output
   */
  public InvalidOutputException(String className, String methodName, String outputName) {
    super(className, methodName, "output " + outputName + " was invalid.");
    this.outputName = outputName;
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
    this.outputName = outputName;
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

  /**
   * Get remediation advice for this exception.
   * 
   * <p>
   * Returns a hint on how to fix invalid output issues. AI agents can use this to self-correct.
   * </p>
   * 
   * @return remediation advice string
   */
  public String getRemediation() {
    String hint = "Output '" + outputName + "' is invalid. Try:\n";

    if (outputName.toLowerCase().contains("temperature")) {
      return hint + "1. Check if temperature is above absolute zero\n"
          + "2. Verify phase equilibrium was calculated correctly\n"
          + "3. Check for valid flash calculation";
    } else if (outputName.toLowerCase().contains("pressure")) {
      return hint + "1. Check if pressure is positive\n"
          + "2. Verify compressibility factor calculation\n"
          + "3. Check for valid vapor pressure calculation";
    } else if (outputName.toLowerCase().contains("density")) {
      return hint + "1. Check if density is positive\n" + "2. Verify volume calculation\n"
          + "3. Check equation of state parameters";
    } else if (outputName.toLowerCase().contains("enthalpy")
        || outputName.toLowerCase().contains("entropy")) {
      return hint + "1. Check if reference state is set correctly\n"
          + "2. Verify thermodynamic consistency\n" + "3. Check for valid Cp/Cv values";
    }

    return hint + "1. Verify input conditions are physically reasonable\n"
        + "2. Check that all required calculations have been performed\n"
        + "3. Review equation of state applicability for this system";
  }
}

