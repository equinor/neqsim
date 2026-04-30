package neqsim.statistics.parameterfitting.nonlinearparameterfitting;

import java.io.Serializable;
import Jama.Matrix;

/**
 * Immutable result from a Levenberg-Marquardt parameter fitting run.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class LevenbergMarquardtResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Reason why the optimizer stopped. */
  public enum ConvergenceReason {
    /** The optimizer has not been run yet. */
    NOT_RUN(false),
    /** The weighted chi-square target was reached. */
    CHI_SQUARE_TOLERANCE(true),
    /** The gradient norm target was reached. */
    GRADIENT_TOLERANCE(true),
    /** The maximum iteration count was reached before convergence. */
    MAX_ITERATIONS_REACHED(false),
    /** The normal-equation matrix could not be solved. */
    SINGULAR_MATRIX(false);

    private final boolean converged;

    /**
     * Creates a convergence reason.
     *
     * @param converged true if the reason represents successful convergence
     */
    ConvergenceReason(boolean converged) {
      this.converged = converged;
    }

    /**
     * Returns whether this reason represents successful convergence.
     *
     * @return true if this reason is converged
     */
    public boolean isConverged() {
      return converged;
    }
  }

  private final ConvergenceReason convergenceReason;
  private final int iterations;
  private final double finalChiSquare;
  private final double gradientNorm;
  private final double[][] covarianceMatrix;
  private final double[][] correlationMatrix;
  private final double[] parameterStandardErrors;

  /**
   * Creates a Levenberg-Marquardt result.
   *
   * @param convergenceReason reason why the optimizer stopped
   * @param iterations number of solver iterations performed
   * @param finalChiSquare final weighted chi-square value
   * @param gradientNorm final norm of the weighted gradient vector
   * @param covarianceMatrix fitted-parameter covariance matrix, or null if unavailable
   * @param correlationMatrix fitted-parameter correlation matrix, or null if unavailable
   * @param parameterStandardErrors fitted-parameter standard errors, or null if unavailable
   */
  public LevenbergMarquardtResult(ConvergenceReason convergenceReason, int iterations,
      double finalChiSquare, double gradientNorm, Matrix covarianceMatrix, Matrix correlationMatrix,
      double[] parameterStandardErrors) {
    this.convergenceReason = convergenceReason;
    this.iterations = iterations;
    this.finalChiSquare = finalChiSquare;
    this.gradientNorm = gradientNorm;
    this.covarianceMatrix = matrixToArray(covarianceMatrix);
    this.correlationMatrix = matrixToArray(correlationMatrix);
    this.parameterStandardErrors = copyArray(parameterStandardErrors);
  }

  /**
   * Creates an initial result for an optimizer that has not run.
   *
   * @return a result with {@link ConvergenceReason#NOT_RUN}
   */
  public static LevenbergMarquardtResult notRun() {
    return new LevenbergMarquardtResult(ConvergenceReason.NOT_RUN, 0, Double.NaN, Double.NaN, null,
        null, null);
  }

  /**
   * Returns the convergence reason.
   *
   * @return reason why the optimizer stopped
   */
  public ConvergenceReason getConvergenceReason() {
    return convergenceReason;
  }

  /**
   * Returns whether the optimizer converged successfully.
   *
   * @return true if the convergence reason is successful
   */
  public boolean isConverged() {
    return convergenceReason.isConverged();
  }

  /**
   * Returns the number of solver iterations performed.
   *
   * @return iteration count
   */
  public int getIterations() {
    return iterations;
  }

  /**
   * Returns the final weighted chi-square value.
   *
   * @return final chi-square
   */
  public double getFinalChiSquare() {
    return finalChiSquare;
  }

  /**
   * Returns the final norm of the weighted gradient vector.
   *
   * @return gradient norm
   */
  public double getGradientNorm() {
    return gradientNorm;
  }

  /**
   * Returns a copy of the fitted-parameter covariance matrix.
   *
   * @return covariance matrix, or null if unavailable
   */
  public Matrix getCovarianceMatrix() {
    return covarianceMatrix == null ? null : new Matrix(copyMatrixArray(covarianceMatrix));
  }

  /**
   * Returns a copy of the fitted-parameter correlation matrix.
   *
   * @return correlation matrix, or null if unavailable
   */
  public Matrix getCorrelationMatrix() {
    return correlationMatrix == null ? null : new Matrix(copyMatrixArray(correlationMatrix));
  }

  /**
   * Returns a copy of the fitted-parameter covariance matrix array.
   *
   * @return covariance matrix values, or null if unavailable
   */
  public double[][] getCovarianceMatrixArray() {
    return copyMatrixArray(covarianceMatrix);
  }

  /**
   * Returns a copy of the fitted-parameter correlation matrix array.
   *
   * @return correlation matrix values, or null if unavailable
   */
  public double[][] getCorrelationMatrixArray() {
    return copyMatrixArray(correlationMatrix);
  }

  /**
   * Returns fitted-parameter standard errors.
   *
   * @return parameter standard errors, or null if unavailable
   */
  public double[] getParameterStandardErrors() {
    return copyArray(parameterStandardErrors);
  }

  /**
   * Copies matrix values into an array.
   *
   * @param matrix matrix to copy
   * @return copied matrix values, or null if matrix is null
   */
  private static double[][] matrixToArray(Matrix matrix) {
    return matrix == null ? null : copyMatrixArray(matrix.getArray());
  }

  /**
   * Copies a matrix array.
   *
   * @param matrix matrix array to copy
   * @return copied matrix array, or null if matrix is null
   */
  private static double[][] copyMatrixArray(double[][] matrix) {
    if (matrix == null) {
      return null;
    }
    double[][] copy = new double[matrix.length][];
    for (int i = 0; i < matrix.length; i++) {
      copy[i] = copyArray(matrix[i]);
    }
    return copy;
  }

  /**
   * Copies an array.
   *
   * @param values values to copy
   * @return copied values, or null if values is null
   */
  private static double[] copyArray(double[] values) {
    if (values == null) {
      return null;
    }
    double[] copy = new double[values.length];
    System.arraycopy(values, 0, copy, 0, values.length);
    return copy;
  }
}
