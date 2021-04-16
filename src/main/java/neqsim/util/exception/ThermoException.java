/*
 * ThermoException.java
 *
 * Created on 1. mai 2001, 12:47
 */

package neqsim.util.exception;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class ThermoException extends java.lang.Exception {

    private static final long serialVersionUID = 1000;

    /**
     * Creates new <code>ThermoException</code> without detail message.
     */
    public ThermoException() {
    }

    /**
     * Constructs an <code>ThermoException</code> with the specified detail message.
     * 
     * @param msg the detail message.
     */
    public ThermoException(String msg) {
        super(msg);
    }
}
