package neqsim.process.equipment.util;

import java.io.Serializable;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Broyden's quasi-Newton acceleration method for multi-variable recycle convergence.
 *
 * <p>
 * This class implements Broyden's "good" method for accelerating the convergence of multi-variable
 * fixed-point iteration problems, particularly suited for process simulations with multiple coupled
 * recycle streams.
 *
 * <p>
 * The method approximates the Jacobian matrix of the fixed-point function and updates it using
 * rank-one corrections based on the secant condition. This avoids the expensive computation of
 * numerical derivatives while still providing Newton-like convergence.
 *
 * <p>
 * Broyden's update formula: B_{k+1} = B_k + (delta_f - B_k * delta_x) * delta_x^T / (delta_x^T *
 * delta_x)
 *
 * <p>
 * Where: - B_k is the current Jacobian approximation (stored as inverse for efficiency) - delta_x =
 * x_k - x_{k-1} (change in input) - delta_f = f(x_k) - f(x_{k-1}) (change in residual)
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class BroydenAccelerator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(BroydenAccelerator.class);

  /** Number of variables being accelerated. */
  private int dimension;

  /** Inverse Jacobian approximation (stored for efficiency using Sherman-Morrison). */
  private double[][] inverseJacobian;

  /** Previous iteration input values. */
  private double[] previousX;

  /** Previous iteration residual values (f(x) = g(x) - x). */
  private double[] previousF;

  /** Number of iterations performed. */
  private int iterationCount;

  /** Minimum number of iterations before applying acceleration. */
  private int delayIterations = 2;

  /** Relaxation factor for damping updates (0 &lt; factor &lt;= 1). */
  private double relaxationFactor = 1.0;

  /** Maximum step size to prevent divergence. */
  private double maxStepSize = Double.MAX_VALUE;

  /** Tolerance for detecting near-zero denominators. */
  private static final double EPSILON = 1e-15;

  /**
   * Creates a new Broyden accelerator.
   */
  public BroydenAccelerator() {
    this.dimension = 0;
    this.iterationCount = 0;
  }

  /**
   * Creates a new Broyden accelerator with specified dimension.
   *
   * @param dimension number of variables to accelerate
   */
  public BroydenAccelerator(int dimension) {
    this.dimension = dimension;
    this.iterationCount = 0;
    initialize(dimension);
  }

  /**
   * Initializes the accelerator for a given dimension. Must be called before first use or when
   * dimension changes.
   *
   * @param dim number of variables
   */
  public void initialize(int dim) {
    this.dimension = dim;
    this.iterationCount = 0;
    this.previousX = null;
    this.previousF = null;

    // Initialize inverse Jacobian as negative identity matrix
    // This corresponds to assuming J ≈ -I initially (typical for convergent iterations)
    this.inverseJacobian = new double[dim][dim];
    for (int i = 0; i < dim; i++) {
      inverseJacobian[i][i] = -1.0;
    }
  }

  /**
   * Resets the accelerator state while keeping the dimension.
   */
  public void reset() {
    if (dimension > 0) {
      initialize(dimension);
    }
  }

  /**
   * Computes the accelerated next iterate using Broyden's method.
   *
   * <p>
   * Given the current iterate x and the fixed-point function output g(x), this method computes an
   * accelerated next iterate that should converge faster than direct substitution (x_{n+1} =
   * g(x_n)).
   *
   * @param currentX current input values (x_n)
   * @param functionOutput output from fixed-point function (g(x_n))
   * @return accelerated next iterate
   */
  public double[] accelerate(double[] currentX, double[] functionOutput) {
    int n = currentX.length;

    // Initialize or resize if needed
    if (dimension != n) {
      initialize(n);
    }

    iterationCount++;

    // Compute residual: f(x) = g(x) - x
    double[] currentF = new double[n];
    for (int i = 0; i < n; i++) {
      currentF[i] = functionOutput[i] - currentX[i];
    }

    // During delay period, use direct substitution
    if (iterationCount <= delayIterations || previousX == null) {
      previousX = currentX.clone();
      previousF = currentF.clone();
      return functionOutput.clone();
    }

    // Compute delta_x and delta_f
    double[] deltaX = new double[n];
    double[] deltaF = new double[n];
    for (int i = 0; i < n; i++) {
      deltaX[i] = currentX[i] - previousX[i];
      deltaF[i] = currentF[i] - previousF[i];
    }

    // Check for sufficient change to update Jacobian
    double deltaXNorm = vectorNorm(deltaX);
    if (deltaXNorm > EPSILON) {
      // Update inverse Jacobian using Sherman-Morrison formula
      updateInverseJacobian(deltaX, deltaF);
    }

    // Compute Newton step: dx = -B^{-1} * f(x)
    double[] step = matrixVectorMultiply(inverseJacobian, currentF);

    // Apply relaxation factor
    for (int i = 0; i < n; i++) {
      step[i] *= relaxationFactor;
    }

    // Limit step size if needed
    double stepNorm = vectorNorm(step);
    if (stepNorm > maxStepSize) {
      double scale = maxStepSize / stepNorm;
      for (int i = 0; i < n; i++) {
        step[i] *= scale;
      }
      logger.debug("Broyden step limited from {} to {}", stepNorm, maxStepSize);
    }

    // Compute next iterate: x_{n+1} = x_n + step
    double[] nextX = new double[n];
    for (int i = 0; i < n; i++) {
      nextX[i] = currentX[i] + step[i];
    }

    // Store for next iteration
    previousX = currentX.clone();
    previousF = currentF.clone();

    return nextX;
  }

  /**
   * Updates the inverse Jacobian approximation using Broyden's "good" method.
   *
   * <p>
   * Uses Sherman-Morrison formula for efficient rank-one update of the inverse: B^{-1}_{k+1} =
   * B^{-1}_k + (delta_x - B^{-1}_k * delta_f) * delta_x^T * B^{-1}_k / (delta_x^T * B^{-1}_k *
   * delta_f)
   *
   * @param deltaX change in input (x_k - x_{k-1})
   * @param deltaF change in residual (f_k - f_{k-1})
   */
  private void updateInverseJacobian(double[] deltaX, double[] deltaF) {
    int n = dimension;

    // Compute B^{-1} * delta_f
    double[] binvDeltaF = matrixVectorMultiply(inverseJacobian, deltaF);

    // Compute delta_x^T * B^{-1} * delta_f (scalar denominator)
    double denom = dotProduct(deltaX, binvDeltaF);

    // Skip update if denominator is too small (prevents numerical instability)
    if (Math.abs(denom) < EPSILON) {
      logger.debug("Broyden update skipped: denominator too small ({})", denom);
      return;
    }

    // Compute numerator vector: delta_x - B^{-1} * delta_f
    double[] numerator = new double[n];
    for (int i = 0; i < n; i++) {
      numerator[i] = deltaX[i] - binvDeltaF[i];
    }

    // Compute delta_x^T * B^{-1} (row vector)
    double[] deltaXTransposeBinv = new double[n];
    for (int j = 0; j < n; j++) {
      deltaXTransposeBinv[j] = 0.0;
      for (int i = 0; i < n; i++) {
        deltaXTransposeBinv[j] += deltaX[i] * inverseJacobian[i][j];
      }
    }

    // Update inverse Jacobian: B^{-1} += (numerator * deltaXTransposeBinv) / denom
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        inverseJacobian[i][j] += numerator[i] * deltaXTransposeBinv[j] / denom;
      }
    }
  }

  /**
   * Multiplies a matrix by a vector.
   *
   * @param matrix the matrix
   * @param vector the vector
   * @return result vector
   */
  private double[] matrixVectorMultiply(double[][] matrix, double[] vector) {
    int n = vector.length;
    double[] result = new double[n];
    for (int i = 0; i < n; i++) {
      result[i] = 0.0;
      for (int j = 0; j < n; j++) {
        result[i] += matrix[i][j] * vector[j];
      }
    }
    return result;
  }

  /**
   * Computes dot product of two vectors.
   *
   * @param a first vector
   * @param b second vector
   * @return dot product
   */
  private double dotProduct(double[] a, double[] b) {
    double sum = 0.0;
    for (int i = 0; i < a.length; i++) {
      sum += a[i] * b[i];
    }
    return sum;
  }

  /**
   * Computes Euclidean norm of a vector.
   *
   * @param v the vector
   * @return norm
   */
  private double vectorNorm(double[] v) {
    return Math.sqrt(dotProduct(v, v));
  }

  /**
   * Gets the current iteration count.
   *
   * @return iteration count
   */
  public int getIterationCount() {
    return iterationCount;
  }

  /**
   * Gets the number of delay iterations before acceleration starts.
   *
   * @return delay iterations
   */
  public int getDelayIterations() {
    return delayIterations;
  }

  /**
   * Sets the number of delay iterations before acceleration starts.
   *
   * @param delayIterations number of iterations to delay
   */
  public void setDelayIterations(int delayIterations) {
    this.delayIterations = delayIterations;
  }

  /**
   * Gets the relaxation factor.
   *
   * @return relaxation factor (0 &lt; factor &lt;= 1)
   */
  public double getRelaxationFactor() {
    return relaxationFactor;
  }

  /**
   * Sets the relaxation factor for damping updates. Values less than 1.0 provide damping for
   * difficult convergence cases.
   *
   * @param relaxationFactor factor between 0 and 1
   */
  public void setRelaxationFactor(double relaxationFactor) {
    if (relaxationFactor <= 0 || relaxationFactor > 1.0) {
      throw new IllegalArgumentException("Relaxation factor must be in (0, 1]");
    }
    this.relaxationFactor = relaxationFactor;
  }

  /**
   * Gets the maximum step size.
   *
   * @return maximum step size
   */
  public double getMaxStepSize() {
    return maxStepSize;
  }

  /**
   * Sets the maximum step size to prevent divergence.
   *
   * @param maxStepSize maximum allowed step norm
   */
  public void setMaxStepSize(double maxStepSize) {
    this.maxStepSize = maxStepSize;
  }

  /**
   * Gets the dimension of the problem.
   *
   * @return number of variables
   */
  public int getDimension() {
    return dimension;
  }

  /**
   * Gets a copy of the current inverse Jacobian approximation.
   *
   * @return copy of inverse Jacobian matrix
   */
  public double[][] getInverseJacobian() {
    if (inverseJacobian == null) {
      return null;
    }
    double[][] copy = new double[dimension][dimension];
    for (int i = 0; i < dimension; i++) {
      copy[i] = Arrays.copyOf(inverseJacobian[i], dimension);
    }
    return copy;
  }

  /**
   * Gets the current residual norm (||f(x)||).
   *
   * @return residual norm, or -1 if not yet computed
   */
  public double getResidualNorm() {
    if (previousF == null) {
      return -1.0;
    }
    return vectorNorm(previousF);
  }
}
