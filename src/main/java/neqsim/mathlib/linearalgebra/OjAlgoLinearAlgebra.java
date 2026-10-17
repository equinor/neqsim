package neqsim.mathlib.linearalgebra;

import org.ojalgo.array.Array1D;
import org.ojalgo.matrix.Primitive64Matrix;
import org.ojalgo.matrix.decomposition.SingularValue;

/**
 * {@link LinearAlgebraOperations} backed by ojAlgo.
 *
 * <p>
 * Matrix solves, inversion, arithmetic and singular-value metrics use ojAlgo's primitive double matrix implementation.
 * Least-squares inputs are checked for numerical full column rank before solving, while direct LU operations do not
 * apply a rank threshold.
 * </p>
 *
 * @author NeqSim Development Team
 * @version $Id: $Id
 */
public final class OjAlgoLinearAlgebra implements LinearAlgebraOperations {
  /** Backend name reported by {@link #getName()}. */
  public static final String NAME = "ojAlgo";

  private static final double MACHINE_EPSILON = Math.ulp(1.0);

  /**
   * Creates the stateless ojAlgo backend.
   */
  public OjAlgoLinearAlgebra() {
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

    Primitive64Matrix coefficients = toMatrix(matrixA);
    requireNonsingular(coefficients, "LU solve");
    try {
      return toVector(coefficients.solve(toColumn(vectorB)), "LU solve");
    } catch (RuntimeException ex) {
      throw new LinearAlgebraException("LU solve failed for a " + matrixA.length + " by " + matrixA.length + " matrix.",
          ex);
    }
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
    if (rank(matrixA) < columns) {
      throw new LinearAlgebraException(
          "Least-squares solve failed: the " + rows + " by " + columns + " matrix does not have full column rank.");
    }

    try {
      return toVector(toMatrix(matrixA).solve(toColumn(vectorB)), "Least-squares solve");
    } catch (RuntimeException ex) {
      throw new LinearAlgebraException("Least-squares solve failed for a " + rows + " by " + columns + " matrix.", ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double[][] invert(double[][] matrix) {
    LinearAlgebraValidation.requireSquareMatrix(matrix, "Matrix to invert");

    Primitive64Matrix source = toMatrix(matrix);
    requireNonsingular(source, "Inversion");
    try {
      return toArray(source.invert(), "Inversion");
    } catch (RuntimeException ex) {
      throw new LinearAlgebraException("Inversion failed for a " + matrix.length + " by " + matrix.length + " matrix.",
          ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double[][] multiply(double[][] matrixA, double[][] matrixB) {
    LinearAlgebraValidation.requireMatrix(matrixA, "Left operand");
    LinearAlgebraValidation.requireMatrix(matrixB, "Right operand");
    LinearAlgebraValidation.requireDimension(matrixB.length, matrixA[0].length,
        "Right-operand row count must match the left-operand column count");

    try {
      return toArray(toMatrix(matrixA).multiply(toMatrix(matrixB)), "Matrix multiplication");
    } catch (RuntimeException ex) {
      throw new LinearAlgebraException("Matrix multiplication failed.", ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double[] multiply(double[][] matrixA, double[] vector) {
    LinearAlgebraValidation.requireMatrix(matrixA, "Matrix operand");
    LinearAlgebraValidation.requireVector(vector, "Vector operand");
    LinearAlgebraValidation.requireDimension(vector.length, matrixA[0].length,
        "Vector length must match the matrix column count");

    try {
      return toVector(toMatrix(matrixA).multiply(toColumn(vector)), "Matrix-vector multiplication");
    } catch (RuntimeException ex) {
      throw new LinearAlgebraException("Matrix-vector multiplication failed.", ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double[][] add(double[][] matrixA, double[][] matrixB) {
    LinearAlgebraValidation.requireMatrix(matrixA, "Left operand");
    LinearAlgebraValidation.requireMatrix(matrixB, "Right operand");
    LinearAlgebraValidation.requireSameShape(matrixA, matrixB, "Addition");

    return toArray(toMatrix(matrixA).add(toMatrix(matrixB)), "Addition");
  }

  /** {@inheritDoc} */
  @Override
  public double[][] subtract(double[][] matrixA, double[][] matrixB) {
    LinearAlgebraValidation.requireMatrix(matrixA, "Left operand");
    LinearAlgebraValidation.requireMatrix(matrixB, "Right operand");
    LinearAlgebraValidation.requireSameShape(matrixA, matrixB, "Subtraction");

    return toArray(toMatrix(matrixA).subtract(toMatrix(matrixB)), "Subtraction");
  }

  /** {@inheritDoc} */
  @Override
  public double[] add(double[] vectorA, double[] vectorB) {
    LinearAlgebraValidation.requireVector(vectorA, "Left operand");
    LinearAlgebraValidation.requireVector(vectorB, "Right operand");
    LinearAlgebraValidation.requireDimension(vectorB.length, vectorA.length,
        "Operands of an addition must have the same length");

    double[] sum = new double[vectorA.length];
    for (int index = 0; index < vectorA.length; index++) {
      sum[index] = vectorA[index] + vectorB[index];
    }
    LinearAlgebraValidation.requireFiniteResult(sum, "Addition");
    return sum;
  }

  /** {@inheritDoc} */
  @Override
  public double[] subtract(double[] vectorA, double[] vectorB) {
    LinearAlgebraValidation.requireVector(vectorA, "Left operand");
    LinearAlgebraValidation.requireVector(vectorB, "Right operand");
    LinearAlgebraValidation.requireDimension(vectorB.length, vectorA.length,
        "Operands of a subtraction must have the same length");

    double[] difference = new double[vectorA.length];
    for (int index = 0; index < vectorA.length; index++) {
      difference[index] = vectorA[index] - vectorB[index];
    }
    LinearAlgebraValidation.requireFiniteResult(difference, "Subtraction");
    return difference;
  }

  /** {@inheritDoc} */
  @Override
  public double[][] scale(double[][] matrix, double factor) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix to scale");
    LinearAlgebraValidation.requireFiniteScalar(factor, "Scale factor");

    return toArray(toMatrix(matrix).multiply(factor), "Scaling");
  }

  /** {@inheritDoc} */
  @Override
  public double[] scale(double[] vector, double factor) {
    LinearAlgebraValidation.requireVector(vector, "Vector to scale");
    LinearAlgebraValidation.requireFiniteScalar(factor, "Scale factor");

    double[] scaled = new double[vector.length];
    for (int index = 0; index < vector.length; index++) {
      scaled[index] = vector[index] * factor;
    }
    LinearAlgebraValidation.requireFiniteResult(scaled, "Scaling");
    return scaled;
  }

  /** {@inheritDoc} */
  @Override
  public double[][] identity(int size) {
    LinearAlgebraValidation.requirePositiveSize(size, "Identity matrix size");

    return toArray(Primitive64Matrix.FACTORY.makeEye(size, size), "Identity matrix creation");
  }

  /** {@inheritDoc} */
  @Override
  public double[][] transpose(double[][] matrix) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix to transpose");

    return toArray(toMatrix(matrix).transpose(), "Transpose");
  }

  /** {@inheritDoc} */
  @Override
  public int rank(double[][] matrix) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix");

    double[] values = singularValues(matrix);
    double tolerance = rankTolerance(matrix, maximum(values));
    int numericalRank = 0;
    for (int index = 0; index < values.length; index++) {
      if (values[index] > tolerance) {
        numericalRank++;
      }
    }
    return numericalRank;
  }

  /** {@inheritDoc} */
  @Override
  public double conditionNumber(double[][] matrix) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix");

    double[] values = singularValues(matrix);
    double largest = maximum(values);
    double smallest = minimum(values);
    if (smallest <= rankTolerance(matrix, largest)) {
      return Double.POSITIVE_INFINITY;
    }
    return largest / smallest;
  }

  /** {@inheritDoc} */
  @Override
  public double determinant(double[][] matrix) {
    LinearAlgebraValidation.requireSquareMatrix(matrix, "Matrix");

    try {
      return toMatrix(matrix).getDeterminant().doubleValue();
    } catch (RuntimeException ex) {
      throw new LinearAlgebraException(
          "Determinant calculation failed for a " + matrix.length + " by " + matrix.length + " matrix.", ex);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double euclideanNorm(double[] vector) {
    LinearAlgebraValidation.requireVector(vector, "Vector");

    double norm = toColumn(vector).norm();
    LinearAlgebraValidation.requireFiniteScalar(norm, "Euclidean norm");
    return norm;
  }

  /** {@inheritDoc} */
  @Override
  public double oneNorm(double[][] matrix) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix");

    double largestColumnSum = 0.0;
    for (int column = 0; column < matrix[0].length; column++) {
      double columnSum = 0.0;
      for (int row = 0; row < matrix.length; row++) {
        columnSum += Math.abs(matrix[row][column]);
      }
      largestColumnSum = Math.max(largestColumnSum, columnSum);
    }
    LinearAlgebraValidation.requireFiniteScalar(largestColumnSum, "Matrix one-norm");
    return largestColumnSum;
  }

  /** {@inheritDoc} */
  @Override
  public double spectralNorm(double[][] matrix) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix");

    return maximum(singularValues(matrix));
  }

  /** {@inheritDoc} */
  @Override
  public double[][] submatrix(double[][] matrix, int firstRow, int lastRow, int firstColumn, int lastColumn) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix");
    LinearAlgebraValidation.requireIndexRange(firstRow, lastRow, matrix.length, "row");
    LinearAlgebraValidation.requireIndexRange(firstColumn, lastColumn, matrix[0].length, "column");

    int rows = lastRow - firstRow + 1;
    int columns = lastColumn - firstColumn + 1;
    double[][] result = new double[rows][columns];
    for (int row = 0; row < rows; row++) {
      System.arraycopy(matrix[firstRow + row], firstColumn, result[row], 0, columns);
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public double[][] withSubmatrix(double[][] matrix, int firstRow, int firstColumn, double[][] block) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix");
    LinearAlgebraValidation.requireMatrix(block, "Block");
    LinearAlgebraValidation.requireBlockFits(matrix, firstRow, firstColumn, block);

    double[][] result = new double[matrix.length][matrix[0].length];
    for (int row = 0; row < matrix.length; row++) {
      System.arraycopy(matrix[row], 0, result[row], 0, matrix[row].length);
    }
    for (int row = 0; row < block.length; row++) {
      System.arraycopy(block[row], 0, result[firstRow + row], firstColumn, block[row].length);
    }
    return result;
  }

  /**
   * Convert a validated row-major array to an ojAlgo matrix.
   *
   * @param matrix validated input matrix
   * @return a new ojAlgo matrix containing a copy of the input
   */
  private Primitive64Matrix toMatrix(double[][] matrix) {
    return Primitive64Matrix.FACTORY.rows(matrix);
  }

  /**
   * Convert a validated vector to an ojAlgo column matrix.
   *
   * @param vector validated input vector
   * @return a new ojAlgo matrix containing a copy of the vector
   */
  private Primitive64Matrix toColumn(double[] vector) {
    return Primitive64Matrix.FACTORY.columns(vector);
  }

  /**
   * Copy an ojAlgo matrix into a row-major array and reject non-finite results.
   *
   * @param matrix ojAlgo matrix to copy
   * @param operation operation name used in a failure message
   * @return a new row-major matrix
   */
  private double[][] toArray(Primitive64Matrix matrix, String operation) {
    int rows = (int) matrix.countRows();
    int columns = (int) matrix.countColumns();
    double[][] result = new double[rows][columns];
    for (int row = 0; row < rows; row++) {
      for (int column = 0; column < columns; column++) {
        result[row][column] = matrix.doubleValue(row, column);
      }
    }
    requireFiniteResult(result, operation);
    return result;
  }

  /**
   * Copy an ojAlgo column matrix into a vector and reject non-finite results.
   *
   * @param matrix ojAlgo column matrix to copy
   * @param operation operation name used in a failure message
   * @return a new vector
   */
  private double[] toVector(Primitive64Matrix matrix, String operation) {
    double[] result = new double[(int) matrix.countRows()];
    for (int row = 0; row < result.length; row++) {
      result[row] = matrix.doubleValue(row, 0);
    }
    LinearAlgebraValidation.requireFiniteResult(result, operation);
    return result;
  }

  /**
   * Compute all singular values using ojAlgo's primitive SVD implementation.
   *
   * @param matrix validated row-major matrix
   * @return singular values in descending order
   * @throws LinearAlgebraException if the decomposition does not converge
   */
  private double[] singularValues(double[][] matrix) {
    Primitive64Matrix source = toMatrix(matrix);
    try {
      SingularValue<Double> decomposition = SingularValue.PRIMITIVE.make(source, false);
      if (!decomposition.decompose(source)) {
        throw new LinearAlgebraException("Singular value decomposition did not converge for a " + matrix.length + " by "
            + matrix[0].length + " matrix.");
      }
      Array1D<Double> values = decomposition.getSingularValues();
      double[] result = new double[(int) values.count()];
      for (int index = 0; index < result.length; index++) {
        result[index] = values.doubleValue(index);
      }
      return result;
    } catch (LinearAlgebraException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new LinearAlgebraException("Singular value decomposition failed.", ex);
    }
  }

  /**
   * Compute the rank tolerance used for singular values.
   *
   * @param matrix validated matrix
   * @param largestSingularValue largest singular value
   * @return rank tolerance scaled by dimensions and machine precision
   */
  private double rankTolerance(double[][] matrix, double largestSingularValue) {
    return Math.max(matrix.length, matrix[0].length) * MACHINE_EPSILON * largestSingularValue;
  }

  /**
   * Return the largest value in a non-empty array.
   *
   * @param values values to scan
   * @return largest value
   */
  private double maximum(double[] values) {
    double largest = values[0];
    for (int index = 1; index < values.length; index++) {
      largest = Math.max(largest, values[index]);
    }
    return largest;
  }

  /**
   * Return the smallest value in a non-empty array.
   *
   * @param values values to scan
   * @return smallest value
   */
  private double minimum(double[] values) {
    double smallest = values[0];
    for (int index = 1; index < values.length; index++) {
      smallest = Math.min(smallest, values[index]);
    }
    return smallest;
  }

  /**
   * Reject a singular matrix without applying a numerical conditioning threshold.
   *
   * @param matrix ojAlgo matrix to check
   * @param operation operation name used in a failure message
   * @throws LinearAlgebraException if the determinant is exactly zero or cannot be evaluated
   */
  private void requireNonsingular(Primitive64Matrix matrix, String operation) {
    try {
      if (matrix.getDeterminant().doubleValue() == 0.0) {
        throw new LinearAlgebraException(operation + " failed: the matrix is singular.");
      }
    } catch (LinearAlgebraException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new LinearAlgebraException(operation + " failed: the matrix determinant could not be evaluated.", ex);
    }
  }

  /**
   * Reject a matrix result containing a non-finite entry.
   *
   * @param matrix computed matrix
   * @param operation operation name used in a failure message
   * @throws LinearAlgebraException if any result entry is non-finite
   */
  private void requireFiniteResult(double[][] matrix, String operation) {
    for (int row = 0; row < matrix.length; row++) {
      LinearAlgebraValidation.requireFiniteResult(matrix[row], operation);
    }
  }
}