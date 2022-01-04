/*
 * VolumeIsNaNException.java
 *
 * Created on 1. mai 2001, 13:19
 */

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
     * Constructs an <code>VolumeIsNaNException</code> with the specified detail message.
     *
     * @param msg the detail message.
     */
    public InvalidInputException(String msg) {
        super(msg);
    }
}
