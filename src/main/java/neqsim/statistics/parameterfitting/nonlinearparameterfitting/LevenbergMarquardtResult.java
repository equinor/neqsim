package neqsim.statistics.parameterfitting.nonlinearparameterfitting;

import java.io.Serializable;
import Jama.Matrix;
import neqsim.util.util.ArrayUtils;

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
  public LevenbergMarquardtResult(ConvergenceReason convergenceReason, int iterations, double finalChiSquare,
      double gradientNorm, double[][] covarianceMatrix, double[][] correlationMatrix,
      double[] parameterStandardErrors) {
    this.convergenceReason = convergenceReason;
    this.iterations = iterations;
    this.finalChiSquare = finalChiSquare;
    this.gradientNorm = gradientNorm;
    this.covarianceMatrix = ArrayUtils.copyMatrixArray(covarianceMatrix);
    this.correlationMatrix = ArrayUtils.copyMatrixArray(correlationMatrix);
    this.parameterStandardErrors = ArrayUtils.copyArray(parameterStandardErrors);
  }

  /**
   * Creates a result from legacy matrix inputs, preserving the original constructor descriptor.
   *
   * @param convergenceReason reason why the optimizer stopped
   * @param iterations number of solver iterations performed
   * @param finalChiSquare final weighted chi-square value
   * @param gradientNorm final norm of the weighted gradient vector
   * @param covarianceMatrix fitted-parameter covariance matrix, or null if unavailable
   * @param correlationMatrix fitted-parameter correlation matrix, or null if unavailable
   * @param parameterStandardErrors fitted-parameter standard errors, or null if unavailable
   * @deprecated Use {@link #fromArrays(ConvergenceReason, int, double, double, double[][], double[][], double[])}.
   */
  @Deprecated
  public LevenbergMarquardtResult(ConvergenceReason convergenceReason, int iterations, double finalChiSquare,
      double gradientNorm, Matrix covarianceMatrix, Matrix correlationMatrix, double[] parameterStandardErrors) {
    this(convergenceReason, iterations, finalChiSquare, gradientNorm,
        covarianceMatrix == null ? (double[][]) null : covarianceMatrix.getArray(),
        correlationMatrix == null ? (double[][]) null : correlationMatrix.getArray(), parameterStandardErrors);
  }

  /**
   * Creates a result from primitive arrays without ambiguous null matrix arguments.
   *
   * @param convergenceReason reason why the optimizer stopped
   * @param iterations number of solver iterations performed
   * @param finalChiSquare final weighted chi-square value
   * @param gradientNorm final norm of the weighted gradient vector
   * @param covarianceMatrix fitted-parameter covariance matrix, or null if unavailable
   * @param correlationMatrix fitted-parameter correlation matrix, or null if unavailable
   * @param parameterStandardErrors fitted-parameter standard errors, or null if unavailable
   * @return immutable result with independent copies of the supplied data
   */
  public static LevenbergMarquardtResult fromArrays(ConvergenceReason convergenceReason, int iterations,
      double finalChiSquare, double gradientNorm, double[][] covarianceMatrix, double[][] correlationMatrix,
      double[] parameterStandardErrors) {
    return new LevenbergMarquardtResult(convergenceReason, iterations, finalChiSquare, gradientNorm, covarianceMatrix,
        correlationMatrix, parameterStandardErrors);
  }

  /**
   * Creates an initial result for an optimizer that has not run.
   *
   * @return a result with {@link ConvergenceReason#NOT_RUN}
   */
  public static LevenbergMarquardtResult notRun() {
    return fromArrays(ConvergenceReason.NOT_RUN, 0, Double.NaN, Double.NaN, null, null, null);
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
   * Returns the fitted-parameter covariance matrix as a Jama compatibility copy.
   *
   * @return covariance matrix, or null if unavailable
   * @deprecated Use {@link #getCovarianceMatrixArray()}.
   */
  @Deprecated
  public Matrix getCovarianceMatrix() {
    return covarianceMatrix == null ? null : new Matrix(ArrayUtils.copyMatrixArray(covarianceMatrix));
  }

  /**
   * Returns the fitted-parameter correlation matrix as a Jama compatibility copy.
   *
   * @return correlation matrix, or null if unavailable
   * @deprecated Use {@link #getCorrelationMatrixArray()}.
   */
  @Deprecated
  public Matrix getCorrelationMatrix() {
    return correlationMatrix == null ? null : new Matrix(ArrayUtils.copyMatrixArray(correlationMatrix));
  }

  /**
   * Returns a copy of the fitted-parameter covariance matrix array.
   *
   * @return covariance matrix values, or null if unavailable
   */
  public double[][] getCovarianceMatrixArray() {
    return ArrayUtils.copyMatrixArray(covarianceMatrix);
  }

  /**
   * Returns a copy of the fitted-parameter correlation matrix array.
   *
   * @return correlation matrix values, or null if unavailable
   */
  public double[][] getCorrelationMatrixArray() {
    return ArrayUtils.copyMatrixArray(correlationMatrix);
  }

  /**
   * Returns fitted-parameter standard errors.
   *
   * @return parameter standard errors, or null if unavailable
   */
  public double[] getParameterStandardErrors() {
    return ArrayUtils.copyArray(parameterStandardErrors);
  }
}
