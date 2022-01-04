package neqsim.util.exception;

/**
 * <p>
 * TooManyIterationsException class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TooManyIterationsException extends neqsim.util.exception.ThermoException {

    private static final long serialVersionUID = 1000;

    /**
     * <p>Constructor for TooManyIterationsException.</p>
     */
    public TooManyIterationsException() {}

    /**
     * Constructs an <code>IterationException</code> with the specified detail message.
     *
     * @param msg the detail message.
     */
    public TooManyIterationsException(String msg) {
        super(msg);
    }
}
