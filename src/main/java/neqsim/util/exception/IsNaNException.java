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
     * <p>
     * Constructor for IsNaNException.
     * </p>
     */
    public IsNaNException() {}

    /**
     * Constructs an <code>VolumeIsNaNException</code> with the specified detail message.
     *
     * @param msg the detail message.
     */
    public IsNaNException(String msg) {
        super(msg);
    }
}
