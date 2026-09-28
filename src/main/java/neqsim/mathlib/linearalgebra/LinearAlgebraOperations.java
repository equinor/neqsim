package neqsim.mathlib.linearalgebra;

/**
 * Dense linear-algebra operations, independent of the library that performs them.
 *
 * <p>
 * NeqSim depends on four linear-algebra libraries (JAMA, EJML, Apache Commons Math and MTJ) and repeats the same dense
 * Gaussian elimination in many private helper methods. This interface is the single contract those implementations are
 * held to, so a caller can select a backend without changing any calling code.
 * </p>
 *
 * <p>
 * Matrices are row-major {@code double[][]}, where {@code matrix[row][column]} is the entry at that position and every
 * row has the same length. No library type ever appears in a signature; an implementation converts on entry and on
 * exit.
 * </p>
 *
 * <p>
 * Implementations must never modify their arguments, must return freshly allocated arrays, and must be stateless and
 * safe for concurrent use. Every failure is reported as a {@link LinearAlgebraException}, including inconsistent
 * dimensions, non-finite input and singular or rank-deficient systems, so callers do not have to distinguish the
 * failure signalling of the individual backends.
 * </p>
 *
 * <p>
 * Structured systems are deliberately out of scope. Use {@link neqsim.mathlib.generalmath.TDMAsolve} for tridiagonal
 * systems and {@link neqsim.mathlib.generalmath.BandedLinearSystemSolver} for banded systems, both of which exploit a
 * storage format that a dense interface would discard.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface LinearAlgebraOperations {
  /**
   * Name of the backend performing the operations, for logging and benchmark reporting.
   *
   * @return a short backend name such as {@code EJML}
   */
  String getName();

  /**
   * Solve the square system {@code A x = b}.
   *
   * @param matrixA square coefficient matrix
   * @param vectorB right-hand-side vector with one entry per row of {@code matrixA}
   * @return the solution vector
   * @throws LinearAlgebraException if the matrix is not square, the dimensions disagree, any entry is non-finite, or
   * the system is singular or too ill-conditioned to solve
   */
  double[] solve(double[][] matrixA, double[] vectorB);

  /**
   * Solve {@code A x = b} in the least-squares sense, minimising the Euclidean norm of the residual.
   *
   * <p>
   * Use this for over-determined systems from parameter fitting and for systems whose square solve is expected to be
   * rank-deficient. For a well-conditioned square system it returns the same answer as
   * {@link #solve(double[][], double[])} but generally costs more.
   * </p>
   *
   * @param matrixA coefficient matrix with at least as many rows as columns
   * @param vectorB right-hand-side vector with one entry per row of {@code matrixA}
   * @return the least-squares solution, one entry per column of {@code matrixA}
   * @throws LinearAlgebraException if the dimensions disagree, any entry is non-finite, or the matrix does not have
   * full column rank
   */
  double[] solveLeastSquares(double[][] matrixA, double[] vectorB);

  /**
   * Compute the inverse of a square matrix.
   *
   * <p>
   * Prefer {@link #solve(double[][], double[])} when the inverse is only needed to multiply a right-hand side; an
   * explicit inverse is both slower and numerically weaker. Use this only when the inverse itself is the result, as in
   * a covariance or sensitivity matrix.
   * </p>
   *
   * @param matrix square matrix to invert
   * @return the inverse matrix
   * @throws LinearAlgebraException if the matrix is not square, any entry is non-finite, or the matrix is singular
   */
  double[][] invert(double[][] matrix);

  /**
   * Multiply two matrices.
   *
   * @param matrixA left operand with dimensions m by n
   * @param matrixB right operand with dimensions n by p
   * @return the product with dimensions m by p
   * @throws LinearAlgebraException if the inner dimensions disagree or any entry is non-finite
   */
  double[][] multiply(double[][] matrixA, double[][] matrixB);

  /**
   * Multiply a matrix by a vector.
   *
   * @param matrixA matrix with dimensions m by n
   * @param vector vector of length n
   * @return the product vector of length m
   * @throws LinearAlgebraException if the dimensions disagree or any entry is non-finite
   */
  double[] multiply(double[][] matrixA, double[] vector);

  /**
   * Add two matrices of the same shape.
   *
   * @param matrixA left operand
   * @param matrixB right operand with the same dimensions as {@code matrixA}
   * @return the sum
   * @throws LinearAlgebraException if the shapes disagree or any entry is non-finite
   */
  double[][] add(double[][] matrixA, double[][] matrixB);

  /**
   * Subtract one matrix from another of the same shape.
   *
   * @param matrixA matrix to subtract from
   * @param matrixB matrix to subtract, with the same dimensions as {@code matrixA}
   * @return the difference {@code matrixA - matrixB}
   * @throws LinearAlgebraException if the shapes disagree or any entry is non-finite
   */
  double[][] subtract(double[][] matrixA, double[][] matrixB);

  /**
   * Add two vectors of the same length.
   *
   * @param vectorA left operand
   * @param vectorB right operand with the same length as {@code vectorA}
   * @return the sum
   * @throws LinearAlgebraException if the lengths disagree or any entry is non-finite
   */
  double[] add(double[] vectorA, double[] vectorB);

  /**
   * Subtract one vector from another of the same length.
   *
   * <p>
   * With {@link #scale(double[], double)} this expresses the damped Newton update {@code x = x - damping * dx} on the
   * vectors returned by {@link #solve(double[][], double[])}.
   * </p>
   *
   * @param vectorA vector to subtract from
   * @param vectorB vector to subtract, with the same length as {@code vectorA}
   * @return the difference {@code vectorA - vectorB}
   * @throws LinearAlgebraException if the lengths disagree or any entry is non-finite
   */
  double[] subtract(double[] vectorA, double[] vectorB);

  /**
   * Multiply every entry of a matrix by a scalar.
   *
   * @param matrix matrix to scale
   * @param factor scalar factor
   * @return the scaled matrix
   * @throws LinearAlgebraException if any entry or the factor is non-finite
   */
  double[][] scale(double[][] matrix, double factor);

  /**
   * Multiply every entry of a vector by a scalar.
   *
   * <p>
   * This is the counterpart of {@link #scale(double[][], double)} for the vectors returned by
   * {@link #solve(double[][], double[])}, where a Newton step is commonly negated before it is applied.
   * </p>
   *
   * @param vector vector to scale
   * @param factor scalar factor
   * @return the scaled vector
   * @throws LinearAlgebraException if any entry or the factor is non-finite
   */
  double[] scale(double[] vector, double factor);

  /**
   * Build a square identity matrix.
   *
   * @param size number of rows and columns
   * @return a matrix with ones on the diagonal and zeros elsewhere
   * @throws LinearAlgebraException if the size is not positive
   */
  double[][] identity(int size);

  /**
   * Transpose a matrix.
   *
   * @param matrix matrix with dimensions m by n
   * @return a new matrix with dimensions n by m
   * @throws LinearAlgebraException if the rows have unequal length or any entry is non-finite
   */
  double[][] transpose(double[][] matrix);

  /**
   * Determine the numerical rank of a matrix.
   *
   * <p>
   * The rank is the number of singular values above a tolerance scaled by the largest singular value and the matrix
   * dimensions, so a matrix that is singular only to within rounding error is reported as rank deficient.
   * </p>
   *
   * @param matrix matrix of any shape
   * @return the numerical rank, between zero and the smaller of the two dimensions
   * @throws LinearAlgebraException if the rows have unequal length or any entry is non-finite
   */
  int rank(double[][] matrix);

  /**
   * Compute the two-norm condition number, the ratio of the largest to the smallest singular value.
   *
   * <p>
   * A value near one indicates a well-conditioned matrix. A value comparable with the reciprocal of machine epsilon,
   * about 1.0e16 in double precision, means a solution carries no significant digits.
   * </p>
   *
   * @param matrix matrix of any shape
   * @return the condition number, or {@link Double#POSITIVE_INFINITY} if the matrix is numerically rank deficient,
   * using the same tolerance as {@link #rank(double[][])}
   * @throws LinearAlgebraException if the rows have unequal length or any entry is non-finite
   */
  double conditionNumber(double[][] matrix);

  /**
   * Compute the determinant of a square matrix.
   *
   * <p>
   * A determinant is a poor test for singularity because it scales with the size of the entries; prefer
   * {@link #conditionNumber(double[][])} or {@link #rank(double[][])} for that. Use this where the determinant itself
   * is the quantity of interest.
   * </p>
   *
   * @param matrix square matrix
   * @return the determinant
   * @throws LinearAlgebraException if the matrix is not square or any entry is non-finite
   */
  double determinant(double[][] matrix);

  /**
   * Compute the Euclidean length of a vector, the square root of the sum of the squared entries.
   *
   * <p>
   * This is the norm wanted by an iterative solver testing a residual or a step for convergence.
   * </p>
   *
   * @param vector vector to measure
   * @return the Euclidean norm
   * @throws LinearAlgebraException if the vector is empty or any entry is non-finite
   */
  double euclideanNorm(double[] vector);

  /**
   * Compute the induced one-norm of a matrix, the largest absolute column sum.
   *
   * @param matrix matrix to measure
   * @return the one-norm
   * @throws LinearAlgebraException if the rows have unequal length or any entry is non-finite
   */
  double oneNorm(double[][] matrix);

  /**
   * Compute the induced two-norm of a matrix, which is its largest singular value.
   *
   * <p>
   * This requires a singular value decomposition and so costs far more than {@link #oneNorm(double[][])}. For a
   * single-column matrix it equals the Euclidean length, but {@link #euclideanNorm(double[])} computes that directly
   * and should be preferred inside an iteration.
   * </p>
   *
   * @param matrix matrix to measure
   * @return the spectral norm
   * @throws LinearAlgebraException if the rows have unequal length or any entry is non-finite
   */
  double spectralNorm(double[][] matrix);

  /**
   * Extract a rectangular block of a matrix.
   *
   * <p>
   * Both bounds are inclusive, so {@code submatrix(m, 0, 1, 0, 1)} returns the leading two by two block.
   * </p>
   *
   * @param matrix matrix to read from
   * @param firstRow index of the first row to include
   * @param lastRow index of the last row to include
   * @param firstColumn index of the first column to include
   * @param lastColumn index of the last column to include
   * @return the extracted block
   * @throws LinearAlgebraException if the matrix is invalid or any bound lies outside it or is out of order
   */
  double[][] submatrix(double[][] matrix, int firstRow, int lastRow, int firstColumn, int lastColumn);

  /**
   * Copy a matrix with a rectangular block overwritten.
   *
   * <p>
   * The argument is left untouched, unlike the in-place block assignment the backends offer. The extent written is
   * given by the dimensions of {@code block}, whose top-left corner lands on {@code firstRow, firstColumn}.
   * </p>
   *
   * @param matrix matrix to copy
   * @param firstRow row index where the block starts
   * @param firstColumn column index where the block starts
   * @param block values to write
   * @return a new matrix carrying the block
   * @throws LinearAlgebraException if either matrix is invalid or the block does not fit at the given corner
   */
  double[][] withSubmatrix(double[][] matrix, int firstRow, int firstColumn, double[][] block);
}
