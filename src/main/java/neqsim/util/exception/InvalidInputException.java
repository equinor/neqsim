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
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for InvalidInputException.
     * </p>
     */
    public InvalidInputException() {}

    /**
     * Constructs an <code>InvalidInputException</code> with the specified detail message.
     *
     * @param msg the detail message.
     */
    public InvalidInputException(String msg) {
        super(msg);
    }

    /**
     * Constructs an <code>InvalidInputException</code> with the specified detail message.
     * 
     * @param className Class that exception is raised from
     * @param methodName Method that exception is raised from
     * @param msg the detail message.
     */
    public InvalidInputException(String className, String methodName, String msg) {
        super(className + ":" + methodName + " - " + msg);
    }
}
