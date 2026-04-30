package neqsim.statistics.parameterfitting;

import java.io.Serializable;

/**
 * Objective function type used by high-level parameter fitting studies.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public enum ObjectiveFunctionType implements Serializable {
  /** Weighted least squares using the experimental standard deviations. */
  WEIGHTED_LEAST_SQUARES,
  /** Iteratively reweighted least squares approximation of least absolute deviation. */
  ABSOLUTE_DEVIATION,
  /** Huber robust loss with quadratic center and linear tails. */
  HUBER,
  /** Cauchy robust loss with slowly increasing tails. */
  CAUCHY,
  /** Tukey biweight loss with hard rejection of large residuals. */
  TUKEY_BIWEIGHT;

  /**
   * Returns whether the objective requires iteratively reweighted fitting.
   *
   * @return true for robust objectives
   */
  public boolean isRobust() {
    return this != WEIGHTED_LEAST_SQUARES;
  }
}
