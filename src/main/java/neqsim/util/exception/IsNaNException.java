/*
 * VolumeIsNaNException.java
 *
 * Created on 1. mai 2001, 13:19
 */

package neqsim.util.exception;

/**
 * <p>IsNaNException class.</p>
 *
 * @author Even Solbraa
 */
public class IsNaNException extends neqsim.util.exception.ThermoException {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new <code>VolumeIsNaNException</code> without detail message.
     */
    public IsNaNException() {
    }

    /**
     * Constructs an <code>VolumeIsNaNException</code> with the specified detail
     * message.
     *
     * @param msg the detail message.
     */
    public IsNaNException(String msg) {
        super(msg);
    }
}
