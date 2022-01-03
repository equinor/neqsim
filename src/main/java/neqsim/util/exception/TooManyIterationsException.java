package neqsim.util.exception;

/**
 * <p>TooManyIterationsException class.</p>
 *
 * @author Even Solbraa
 */
public class TooManyIterationsException extends neqsim.util.exception.ThermoException {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new <code>IterationException</code> without detail message.
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
