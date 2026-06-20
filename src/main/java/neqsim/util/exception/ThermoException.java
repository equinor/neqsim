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
public abstract class ThermoException extends java.lang.Exception {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Constructs an <code>ThermoException</code> with the specified detail message.
   *
   * @param msg the detail message.
   */
  public ThermoException(String msg) {
    super(msg);
  }

  /**
   * Constructs an <code>ThermoException</code> with the specified detail message.
   *
   * @param className Class that exception is raised from
   * @param methodName Method that exception is raised from
   * @param msg specific error message
   */
  public ThermoException(String className, String methodName, String msg) {
    super(className + ":" + methodName + " - " + msg);
  }
}
