package neqsim.mathlib.linearalgebra;

import Jama.Matrix;
import Jama.QRDecomposition;
import Jama.SingularValueDecomposition;

/**
 * {@link LinearAlgebraOperations} backed by JAMA.
 *
 * <p>
 * JAMA is the backend most NeqSim code already uses directly, through {@code Jama.Matrix} in the flash solvers, the
 * chemical-equilibrium routines and the parameter-fitting classes. This implementation exists so that code can move to
 * the backend-independent interface without changing which library does the arithmetic, and so a benchmark can compare
 * JAMA against the other backends on identical input.
 * </p>
 *
 * <p>
 * Square solves and inversion use LU with partial pivoting, least squares uses QR, and rank and conditioning come from
 * a singular value decomposition. JAMA signals a singular or rank-deficient system by throwing a plain
 * {@link RuntimeException}, which is caught for LU solves and inversion and re-thrown as a
 * {@link LinearAlgebraException}. Non-finite solve and inverse results are also rejected. LU solves and inversion do
 * not apply a numerical rank or conditioning threshold: an ill-conditioned system can return a finite result with poor
 * accuracy. Callers must assess conditioning and residuals when accuracy matters. Least squares explicitly checks
 * numerical column rank before solving.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public final class JamaLinearAlgebra implements LinearAlgebraOperations {
  /** Backend name reported by {@link #getName()}. */
  public static final String NAME = "JAMA";

  private static final double MACHINE_EPSILON = Math.ulp(1.0);

  /**
   * Constructor for JamaLinearAlgebra.
   */
  public JamaLinearAlgebra() {
  }

  /** {@inheritDoc} */
  @Override
  public String getName() {
    return NAME;
  }

  /** {@inheritDoc} */
  @Override
  public double[] solve(double[][] matrixA, double[] vectorB) {
    LinearAlgebraValidation.requireSquareMatrix(matrixA, "Coefficient matrix");
    LinearAlgebraValidation.requireVector(vectorB, "Right-hand side");
    LinearAlgebraValidation.requireDimension(vectorB.length, matrixA.length,
        "Right-hand-side length must match the coefficient matrix row count");

    Matrix solution;
    try {
      solution = toMatrix(matrixA).solve(toColumn(vectorB));
    } catch (RuntimeException ex) {
      throw new LinearAlgebraException(
          "LU solve failed: the " + matrixA.length + " by " + matrixA.length + " matrix is singular.", ex);
    }

    double[] values = solution.getColumnPackedCopy();
    LinearAlgebraValidation.requireFiniteResult(values, "LU solve");
    return values;
  }

  /** {@inheritDoc} */
  @Override
  public double[] solveLeastSquares(double[][] matrixA, double[] vectorB) {
    LinearAlgebraValidation.requireMatrix(matrixA, "Coefficient matrix");
    LinearAlgebraValidation.requireVector(vectorB, "Right-hand side");
    LinearAlgebraValidation.requireDimension(vectorB.length, matrixA.length,
        "Right-hand-side length must match the coefficient matrix row count");

    int rows = matrixA.length;
    int columns = matrixA[0].length;
    if (rows < columns) {
      throw new LinearAlgebraException("Least-squares solve needs at least as many rows as columns, but found " + rows
          + " rows and " + columns + " columns.");
    }

    // JAMA's own isFullRank only rejects an exactly zero diagonal, so rank comes from the singular values instead.
    if (decompose(matrixA).rank() < columns) {
      throw new LinearAlgebraException(
          "Least-squares solve failed: the " + rows + " by " + columns + " matrix does not have full column rank.");
    }

    double[] values = new QRDecomposition(toMatrix(matrixA)).solve(toColumn(vectorB)).getColumnPackedCopy();
    LinearAlgebraValidation.requireFiniteResult(values, "Least-squares solve");
    return values;
  }

  /** {@inheritDoc} */
  @Override
  public double[][] invert(double[][] matrix) {
    LinearAlgebraValidation.requireSquareMatrix(matrix, "Matrix to invert");

    int size = matrix.length;
    Matrix inverse;
    try {
      inverse = toMatrix(matrix).inverse();
    } catch (RuntimeException ex) {
      throw new LinearAlgebraException("Matrix of size " + size + " is singular and cannot be inverted.", ex);
    }

    double[][] result = inverse.getArray();
    for (int row = 0; row < size; row++) {
      LinearAlgebraValidation.requireFiniteResult(result[row], "Inversion");
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public double[][] multiply(double[][] matrixA, double[][] matrixB) {
    LinearAlgebraValidation.requireMatrix(matrixA, "Left operand");
    LinearAlgebraValidation.requireMatrix(matrixB, "Right operand");
    LinearAlgebraValidation.requireDimension(matrixB.length, matrixA[0].length,
        "Right-operand row count must match the left-operand column count");

    return toMatrix(matrixA).times(toMatrix(matrixB)).getArray();
  }

  /** {@inheritDoc} */
  @Override
  public double[] multiply(double[][] matrixA, double[] vector) {
    LinearAlgebraValidation.requireMatrix(matrixA, "Matrix operand");
    LinearAlgebraValidation.requireVector(vector, "Vector operand");
    LinearAlgebraValidation.requireDimension(vector.length, matrixA[0].length,
        "Vector length must match the matrix column count");

    return toMatrix(matrixA).times(toColumn(vector)).getColumnPackedCopy();
  }

  /** {@inheritDoc} */
  @Override
  public double[][] add(double[][] matrixA, double[][] matrixB) {
    LinearAlgebraValidation.requireMatrix(matrixA, "Left operand");
    LinearAlgebraValidation.requireMatrix(matrixB, "Right operand");
    LinearAlgebraValidation.requireSameShape(matrixA, matrixB, "Addition");

    return toMatrix(matrixA).plus(toMatrix(matrixB)).getArray();
  }

  /** {@inheritDoc} */
  @Override
  public double[][] subtract(double[][] matrixA, double[][] matrixB) {
    LinearAlgebraValidation.requireMatrix(matrixA, "Left operand");
    LinearAlgebraValidation.requireMatrix(matrixB, "Right operand");
    LinearAlgebraValidation.requireSameShape(matrixA, matrixB, "Subtraction");

    return toMatrix(matrixA).minus(toMatrix(matrixB)).getArray();
  }

  /** {@inheritDoc} */
  @Override
  public double[] add(double[] vectorA, double[] vectorB) {
    LinearAlgebraValidation.requireVector(vectorA, "Left operand");
    LinearAlgebraValidation.requireVector(vectorB, "Right operand");
    LinearAlgebraValidation.requireDimension(vectorB.length, vectorA.length,
        "Operands of an addition must have the same length");

    return toColumn(vectorA).plus(toColumn(vectorB)).getColumnPackedCopy();
  }

  /** {@inheritDoc} */
  @Override
  public double[] subtract(double[] vectorA, double[] vectorB) {
    LinearAlgebraValidation.requireVector(vectorA, "Left operand");
    LinearAlgebraValidation.requireVector(vectorB, "Right operand");
    LinearAlgebraValidation.requireDimension(vectorB.length, vectorA.length,
        "Operands of a subtraction must have the same length");

    return toColumn(vectorA).minus(toColumn(vectorB)).getColumnPackedCopy();
  }

  /** {@inheritDoc} */
  @Override
  public double[][] scale(double[][] matrix, double factor) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix to scale");
    LinearAlgebraValidation.requireFiniteScalar(factor, "Scale factor");

    return toMatrix(matrix).times(factor).getArray();
  }

  /** {@inheritDoc} */
  @Override
  public double[] scale(double[] vector, double factor) {
    LinearAlgebraValidation.requireVector(vector, "Vector to scale");
    LinearAlgebraValidation.requireFiniteScalar(factor, "Scale factor");

    return toColumn(vector).times(factor).getColumnPackedCopy();
  }

  /** {@inheritDoc} */
  @Override
  public double[][] identity(int size) {
    LinearAlgebraValidation.requirePositiveSize(size, "Identity matrix size");

    return Matrix.identity(size, size).getArray();
  }

  /** {@inheritDoc} */
  @Override
  public double[][] transpose(double[][] matrix) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix to transpose");

    return toMatrix(matrix).transpose().getArray();
  }

  /** {@inheritDoc} */
  @Override
  public int rank(double[][] matrix) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix");

    return decompose(matrix).rank();
  }

  /** {@inheritDoc} */
  @Override
  public double conditionNumber(double[][] matrix) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix");

    double[] singularValues = decompose(matrix).getSingularValues();
    double smallest = singularValues[singularValues.length - 1];
    if (smallest <= rankTolerance(matrix, singularValues[0])) {
      return Double.POSITIVE_INFINITY;
    }
    return singularValues[0] / smallest;
  }

  /** {@inheritDoc} */
  @Override
  public double determinant(double[][] matrix) {
    LinearAlgebraValidation.requireSquareMatrix(matrix, "Matrix");

    return toMatrix(matrix).det();
  }

  /** {@inheritDoc} */
  @Override
  public double euclideanNorm(double[] vector) {
    LinearAlgebraValidation.requireVector(vector, "Vector");

    // The Frobenius norm of a single-column matrix is its Euclidean length, and avoids the SVD that norm2 would run.
    return toColumn(vector).normF();
  }

  /** {@inheritDoc} */
  @Override
  public double oneNorm(double[][] matrix) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix");

    return toMatrix(matrix).norm1();
  }

  /** {@inheritDoc} */
  @Override
  public double spectralNorm(double[][] matrix) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix");

    return decompose(matrix).getSingularValues()[0];
  }

  /** {@inheritDoc} */
  @Override
  public double[][] submatrix(double[][] matrix, int firstRow, int lastRow, int firstColumn, int lastColumn) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix");
    LinearAlgebraValidation.requireIndexRange(firstRow, lastRow, matrix.length, "row");
    LinearAlgebraValidation.requireIndexRange(firstColumn, lastColumn, matrix[0].length, "column");

    return toMatrix(matrix).getMatrix(firstRow, lastRow, firstColumn, lastColumn).getArray();
  }

  /** {@inheritDoc} */
  @Override
  public double[][] withSubmatrix(double[][] matrix, int firstRow, int firstColumn, double[][] block) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix");
    LinearAlgebraValidation.requireMatrix(block, "Block");
    LinearAlgebraValidation.requireBlockFits(matrix, firstRow, firstColumn, block);

    Matrix result = toMatrix(matrix);
    result.setMatrix(firstRow, firstRow + block.length - 1, firstColumn, firstColumn + block[0].length - 1,
        toMatrix(block));
    return result.getArray();
  }

  /**
   * Decompose a matrix into its singular values.
   *
   * <p>
   * JAMA's decomposition is only reliable when the matrix has at least as many rows as columns, so a wide matrix is
   * transposed first. That leaves the singular values unchanged, which is all this method's callers use.
   * </p>
   *
   * @param matrix validated rectangular matrix
   * @return the decomposition, whose singular values are in descending order
   */
  private SingularValueDecomposition decompose(double[][] matrix) {
    Matrix source = toMatrix(matrix);
    if (matrix.length < matrix[0].length) {
      source = source.transpose();
    }
    return source.svd();
  }

  /**
   * Tolerance below which a singular value counts as zero.
   *
   * <p>
   * This is the tolerance JAMA itself applies in {@code SingularValueDecomposition.rank()}, repeated here so that a
   * matrix reported as rank deficient also reports an infinite condition number.
   * </p>
   *
   * @param matrix the matrix being examined
   * @param largestSingularValue the largest singular value found
   * @return the rank tolerance
   */
  private double rankTolerance(double[][] matrix, double largestSingularValue) {
    return Math.max(matrix.length, matrix[0].length) * MACHINE_EPSILON * largestSingularValue;
  }

  /**
   * Copy a row-major array into a JAMA matrix.
   *
   * @param matrix validated rectangular matrix
   * @return a JAMA matrix holding a copy of the data
   */
  private Matrix toMatrix(double[][] matrix) {
    return Matrix.constructWithCopy(matrix);
  }

  /**
   * Copy a vector into a single-column JAMA matrix.
   *
   * @param vector validated vector
   * @return a JAMA matrix with one column holding a copy of the data
   */
  private Matrix toColumn(double[] vector) {
    return new Matrix(vector, vector.length);
  }
}
