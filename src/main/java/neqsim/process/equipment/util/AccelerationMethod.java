package neqsim.process.equipment.util;

/**
 * Enumeration of convergence acceleration methods for recycle calculations.
 *
 * <p>
 * These methods control how the recycle tear stream values are updated between iterations to
 * improve convergence speed and stability.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public enum AccelerationMethod {
  /**
   * Direct substitution (successive substitution). Uses the output of the current iteration
   * directly as input for the next iteration. Simple but can be slow or unstable for
   * tightly-coupled systems.
   *
   * <p>
   * Formula: x_{n+1} = g(x_n)
   */
  DIRECT_SUBSTITUTION,

  /**
   * Wegstein acceleration method. A single-variable acceleration technique that uses the slope
   * between consecutive iterations to extrapolate towards the solution.
   *
   * <p>
   * Formula: x_{n+1} = q * g(x_n) + (1-q) * x_n where q = s/(s-1) and s is the slope estimate
   *
   * <p>
   * Particularly effective for single recycle loops and provides bounded acceleration to prevent
   * oscillation.
   */
  WEGSTEIN,

  /**
   * Broyden's quasi-Newton method. A multi-variable acceleration technique that approximates the
   * Jacobian matrix and updates it using rank-one corrections.
   *
   * <p>
   * Best suited for systems with multiple coupled recycle streams. More sophisticated than Wegstein
   * but requires more memory for storing the Jacobian approximation.
   */
  BROYDEN
}
