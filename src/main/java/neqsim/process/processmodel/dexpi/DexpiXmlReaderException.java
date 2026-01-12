package neqsim.process.processmodel.dexpi;

/**
 * Exception thrown when there is an error reading a DEXPI XML file.
 *
 * @author NeqSim
 * @version 1.0
 */
public class DexpiXmlReaderException extends Exception {

  private static final long serialVersionUID = 1L;

  /**
   * Creates a new exception with the specified message.
   *
   * @param message the error message
   */
  public DexpiXmlReaderException(String message) {
    super(message);
  }

  /**
   * Creates a new exception with the specified message and cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public DexpiXmlReaderException(String message, Throwable cause) {
    super(message, cause);
  }
}
