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
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for NotInitializedException.
     * </p>
     */
    public NotInitializedException() {
    }

    /**
     * Constructs an <code>NotInitializedException</code> with the specified detail
     * message.
     *
     * @param msg the detail message.
     */
    public NotInitializedException(String msg) {
        super(msg);
    }

    /**
     * Constructs an <code>NotInitializedException</code> with default detail
     * message.
     *
     * @param parameter
     * @param initMethod
     */
    public NotInitializedException(String parameter, String initMethod) {
        this("Parameter " + parameter + " not initialized. Method " + initMethod + " must be called.");
    }
}
