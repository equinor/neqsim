package neqsim.process.equipment.pipeline.twophasepipe.closure;

/**
 * One remembered result of a closure calculation that depends on its numerical arguments alone.
 *
 * <p>
 * The multiphase pipe closures solve the same nonlinear balance more than once for one cell: the flow-regime
 * classification and its transition weights both need the equilibrium stratified level, and the holdup closure needs
 * the stratified and slug-unit solutions for several regime shares. Each of those solves iterates over a transcendental
 * residual, so repeating it is the dominant closure cost. This entry lets the caller reuse the previous solution when,
 * and only when, every argument has the same bit pattern as before. The reused value is therefore the value the solver
 * would have returned, bit for bit; no tolerance is involved.
 * </p>
 *
 * <p>
 * The entry is immutable. Callers keep it in a {@code transient} field and replace the whole entry after a miss, so a
 * reader on another thread sees either the old or the new entry and never a half-written one.
 * </p>
 *
 * @param <T> type of the remembered result
 * @author Even Solbraa
 * @version 1.0
 */
public final class ExactArgumentMemo<T> {
  /** Arguments of the remembered calculation. */
  private final double[] arguments;

  /** Result of the remembered calculation. */
  private final T value;

  /**
   * Remember one result.
   *
   * @param value result of the calculation
   * @param arguments every numerical input the result depends on, in a fixed order
   */
  public ExactArgumentMemo(T value, double... arguments) {
    this.value = value;
    this.arguments = arguments.clone();
  }

  /**
   * Check whether a new call has exactly the remembered arguments.
   *
   * <p>
   * Arguments are compared by bit pattern. Positive and negative zero are different arguments, and a NaN matches only
   * the same NaN, so a match guarantees the calculation would repeat the remembered arithmetic.
   * </p>
   *
   * @param candidate arguments of the new call, in the same order as when the result was remembered
   * @return true when the remembered result is the result of the new call
   */
  public boolean matches(double... candidate) {
    if (candidate.length != arguments.length) {
      return false;
    }
    for (int index = 0; index < candidate.length; index++) {
      if (Double.doubleToRawLongBits(candidate[index]) != Double.doubleToRawLongBits(arguments[index])) {
        return false;
      }
    }
    return true;
  }

  /**
   * Get the remembered result.
   *
   * @return result stored when this entry was created
   */
  public T getValue() {
    return value;
  }
}
