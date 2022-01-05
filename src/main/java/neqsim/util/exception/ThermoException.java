/*
 * ThermoException.java
 *
 * Created on 1. mai 2001, 12:47
 */
package neqsim.util.exception;

/**
 * <p>
 * ThermoException class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class ThermoException extends java.lang.Exception {
    private static final long serialVersionUID = 1000;

    /**
     * <p>
     * Constructor for ThermoException.
     * </p>
     */
    public ThermoException() {}

    /**
     * Constructs an <code>ThermoException</code> with the specified detail message.
     *
     * @param msg the detail message.
     */
    public ThermoException(String msg) {
        super(msg);
    }
}
