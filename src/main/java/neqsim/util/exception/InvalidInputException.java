/*
 * VolumeIsNaNException.java
 *
 * Created on 1. mai 2001, 13:19
 */

package neqsim.util.exception;

/**
 *
 * @author  Even Solbraa
 * @version 
 */
public class InvalidInputException extends neqsim.util.exception.ThermoException {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new <code>VolumeIsNaNException</code> without detail message.
     */
    public InvalidInputException() {
    }


    /**
     * Constructs an <code>VolumeIsNaNException</code> with the specified detail message.
     * @param msg the detail message.
     */
    public InvalidInputException(String msg) {
        super(msg);
    }
}


