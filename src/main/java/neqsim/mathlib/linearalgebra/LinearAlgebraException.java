package neqsim.mathlib.linearalgebra;

/**
 * Signals that a dense linear-algebra operation could not be completed.
 *
 * <p>
 * Every {@link LinearAlgebraOperations} backend maps its own failure signalling onto this single type, so callers do
 * not have to know whether the underlying library throws, returns a status flag, or silently produces a meaningless
 * result.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class LinearAlgebraException extends RuntimeException {
  private static final long serialVersionUID = 1000L;

  /**
   * Constructor for LinearAlgebraException.
   *
   * @param message description of the failure
   */
  public LinearAlgebraException(String message) {
    super(message);
  }

  /**
   * Constructor for LinearAlgebraException.
   *
   * @param message description of the failure
   * @param cause the backend exception that triggered the failure
   */
  public LinearAlgebraException(String message, Throwable cause) {
    super(message, cause);
  }
}
