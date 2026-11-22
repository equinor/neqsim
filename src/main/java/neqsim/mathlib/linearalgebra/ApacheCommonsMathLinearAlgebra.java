package neqsim.mathlib.linearalgebra;

import org.apache.commons.math3.exception.MathArithmeticException;
import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.SingularMatrixException;
import org.apache.commons.math3.linear.SingularValueDecomposition;

/**
 * {@link LinearAlgebraOperations} backed by Apache Commons Math.
 *
 * <p>
 * Square solves and inversion use LU decomposition, least squares uses QR decomposition, and rank and conditioning use
 * a singular value decomposition. Backend performance depends on matrix size, operation and runtime; select a backend
 * using representative benchmarks.
 * </p>
 *
 * @author Åsmund Våge Fannemel
 * @version $Id: $Id
 */
public final class ApacheCommonsMathLinearAlgebra implements LinearAlgebraOperations {
  /** Backend name reported by {@link #getName()}. */
  public static final String NAME = "Apache Commons Math";

  private static final double MACHINE_EPSILON = Math.ulp(1.0);

  /**
   * Constructor for ApacheCommonsMathLinearAlgebra.
   */
  public ApacheCommonsMathLinearAlgebra() {
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

    DecompositionSolver solver = new LUDecomposition(toMatrix(matrixA), 0.0).getSolver();
    if (!solver.isNonSingular()) {
      throw new LinearAlgebraException(
          "LU solve failed: the " + matrixA.length + " by " + matrixA[0].length + " matrix is singular.");
    }
    double[] solution;
    try {
      solution = solver.solve(toVector(vectorB)).toArray();
    } catch (SingularMatrixException exception) {
      throw new LinearAlgebraException("LU solve failed: the coefficient matrix is singular.", exception);
    }
    LinearAlgebraValidation.requireFiniteResult(solution, "LU solve");
    return solution;
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

    DecompositionSolver solver = new QRDecomposition(toMatrix(matrixA), 0.0).getSolver();
    if (!solver.isNonSingular()) {
      throw new LinearAlgebraException("Least-squares solve failed: the coefficient matrix is rank deficient.");
    }
    double[] solution;
    try {
      solution = solver.solve(toVector(vectorB)).toArray();
    } catch (SingularMatrixException exception) {
      throw new LinearAlgebraException("Least-squares solve failed: the coefficient matrix is rank deficient.",
          exception);
    }
    LinearAlgebraValidation.requireFiniteResult(solution, "Least-squares solve");
    return solution;
  }

  /** {@inheritDoc} */
  @Override
  public double[][] invert(double[][] matrix) {
    LinearAlgebraValidation.requireSquareMatrix(matrix, "Matrix to invert");

    DecompositionSolver solver = new LUDecomposition(toMatrix(matrix), 0.0).getSolver();
    if (!solver.isNonSingular()) {
      throw new LinearAlgebraException("Matrix of size " + matrix.length + " is singular and cannot be inverted.");
    }
    RealMatrix inverse;
    try {
      inverse = solver.getInverse();
    } catch (SingularMatrixException exception) {
      throw new LinearAlgebraException("Matrix of size " + matrix.length + " is singular and cannot be inverted.",
          exception);
    }
    double[][] result = inverse.getData();
    LinearAlgebraValidation.requireFiniteResult(flatten(result), "Inversion");
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public double[][] multiply(double[][] matrixA, double[][] matrixB) {
    LinearAlgebraValidation.requireMatrix(matrixA, "Left operand");
    LinearAlgebraValidation.requireMatrix(matrixB, "Right operand");
    LinearAlgebraValidation.requireDimension(matrixB.length, matrixA[0].length,
        "Right-operand row count must match the left-operand column count");

    return toMatrix(matrixA).multiply(toMatrix(matrixB)).getData();
  }

  /** {@inheritDoc} */
  @Override
  public double[] multiply(double[][] matrixA, double[] vector) {
    LinearAlgebraValidation.requireMatrix(matrixA, "Matrix operand");
    LinearAlgebraValidation.requireVector(vector, "Vector operand");
    LinearAlgebraValidation.requireDimension(vector.length, matrixA[0].length,
        "Vector length must match the matrix column count");

    return toMatrix(matrixA).operate(toVector(vector)).toArray();
  }

  /** {@inheritDoc} */
  @Override
  public double[][] add(double[][] matrixA, double[][] matrixB) {
    LinearAlgebraValidation.requireMatrix(matrixA, "Left operand");
    LinearAlgebraValidation.requireMatrix(matrixB, "Right operand");
    LinearAlgebraValidation.requireSameShape(matrixA, matrixB, "Addition");

    return toMatrix(matrixA).add(toMatrix(matrixB)).getData();
  }

  /** {@inheritDoc} */
  @Override
  public double[] add(double[] vectorA, double[] vectorB) {
    LinearAlgebraValidation.requireVector(vectorA, "Left operand");
    LinearAlgebraValidation.requireVector(vectorB, "Right operand");
    LinearAlgebraValidation.requireDimension(vectorB.length, vectorA.length,
        "Operands of an addition must have the same length");

    return toVector(vectorA).add(toVector(vectorB)).toArray();
  }

  /** {@inheritDoc} */
  @Override
  public double[][] subtract(double[][] matrixA, double[][] matrixB) {
    LinearAlgebraValidation.requireMatrix(matrixA, "Left operand");
    LinearAlgebraValidation.requireMatrix(matrixB, "Right operand");
    LinearAlgebraValidation.requireSameShape(matrixA, matrixB, "Subtraction");

    return toMatrix(matrixA).subtract(toMatrix(matrixB)).getData();
  }

  /** {@inheritDoc} */
  @Override
  public double[] subtract(double[] vectorA, double[] vectorB) {
    LinearAlgebraValidation.requireVector(vectorA, "Left operand");
    LinearAlgebraValidation.requireVector(vectorB, "Right operand");
    LinearAlgebraValidation.requireDimension(vectorB.length, vectorA.length,
        "Operands of a subtraction must have the same length");

    return toVector(vectorA).subtract(toVector(vectorB)).toArray();
  }

  /** {@inheritDoc} */
  @Override
  public double[][] scale(double[][] matrix, double factor) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix to scale");
    LinearAlgebraValidation.requireFiniteScalar(factor, "Scale factor");

    return toMatrix(matrix).scalarMultiply(factor).getData();
  }

  /** {@inheritDoc} */
  @Override
  public double[] scale(double[] vector, double factor) {
    LinearAlgebraValidation.requireVector(vector, "Vector to scale");
    LinearAlgebraValidation.requireFiniteScalar(factor, "Scale factor");

    return toVector(vector).mapMultiply(factor).toArray();
  }

  /** {@inheritDoc} */
  @Override
  public double[][] identity(int size) {
    LinearAlgebraValidation.requirePositiveSize(size, "Identity matrix size");

    return MatrixUtils.createRealIdentityMatrix(size).getData();
  }

  /** {@inheritDoc} */
  @Override
  public double[][] transpose(double[][] matrix) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix to transpose");

    return toMatrix(matrix).transpose().getData();
  }

  /** {@inheritDoc} */
  @Override
  public int rank(double[][] matrix) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix");

    double[] singularValues = singularValues(matrix);
    double tolerance = rankTolerance(matrix, singularValues[0]);
    int rank = 0;
    for (int index = 0; index < singularValues.length; index++) {
      if (singularValues[index] > tolerance) {
        rank++;
      }
    }
    return rank;
  }

  /** {@inheritDoc} */
  @Override
  public double conditionNumber(double[][] matrix) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix");

    double[] singularValues = singularValues(matrix);
    double largest = singularValues[0];
    double smallest = singularValues[singularValues.length - 1];
    if (smallest <= rankTolerance(matrix, largest)) {
      return Double.POSITIVE_INFINITY;
    }
    return largest / smallest;
  }

  /** {@inheritDoc} */
  @Override
  public double determinant(double[][] matrix) {
    LinearAlgebraValidation.requireSquareMatrix(matrix, "Matrix");

    return new LUDecomposition(toMatrix(matrix), 0.0).getDeterminant();
  }

  /** {@inheritDoc} */
  @Override
  public double euclideanNorm(double[] vector) {
    LinearAlgebraValidation.requireVector(vector, "Vector");

    return toVector(vector).getNorm();
  }

  /** {@inheritDoc} */
  @Override
  public double oneNorm(double[][] matrix) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix");

    return toMatrix(matrix).getNorm();
  }

  /** {@inheritDoc} */
  @Override
  public double spectralNorm(double[][] matrix) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix");

    return singularValues(matrix)[0];
  }

  /** {@inheritDoc} */
  @Override
  public RealEigenDecomposition eigenDecomposition(double[][] matrix) {
    RealBlockEigenDecomposition decomposition = realBlockEigenDecomposition(matrix);
    if (decomposition.hasComplexEigenvalues()) {
      throw new LinearAlgebraException("Real eigendecomposition requires a real spectrum.");
    }
    return new RealEigenDecomposition(decomposition.getRealParts(), decomposition.getVectors());
  }

  /**
   * Compute the real block representation of a real matrix with a real or complex spectrum.
   *
   * <p>
   * This Commons Math extension retains imaginary parts and the associated real block basis. Unlike the strict
   * {@link #eigenDecomposition(double[][])} operation, it supports conjugate eigenpairs. Callers must explicitly decide
   * how to use the two-by-two blocks; the basis columns are not individual real eigenvectors for such pairs.
   *
   * @param matrix finite square matrix
   * @return immutable real and imaginary eigenvalue parts and real block basis satisfying {@code A V = V D}
   * @throws LinearAlgebraException if input is invalid or decomposition fails
   */
  public RealBlockEigenDecomposition realBlockEigenDecomposition(double[][] matrix) {
    LinearAlgebraValidation.requireSquareMatrix(matrix, "Matrix");
    try {
      EigenDecomposition decomposition = new EigenDecomposition(toMatrix(matrix));
      return new RealBlockEigenDecomposition(decomposition.getRealEigenvalues(), decomposition.getImagEigenvalues(),
          decomposition.getV().getData());
    } catch (MaxCountExceededException exception) {
      throw new LinearAlgebraException("Eigenvalue decomposition did not converge.", exception);
    } catch (MathArithmeticException exception) {
      throw new LinearAlgebraException("Eigenvalue decomposition failed to compute an eigensystem.", exception);
    }
  }

  /** {@inheritDoc} */
  @Override
  public SingularValueDecompositionResult singularValueDecomposition(double[][] matrix) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix");
    try {
      SingularValueDecomposition decomposition = new SingularValueDecomposition(toMatrix(matrix));
      return new SingularValueDecompositionResult(decomposition.getSingularValues(), decomposition.getU().getData(),
          decomposition.getV().getData());
    } catch (MaxCountExceededException exception) {
      throw new LinearAlgebraException("Singular value decomposition did not converge.", exception);
    }
  }

  /** {@inheritDoc} */
  @Override
  public double[][] submatrix(double[][] matrix, int firstRow, int lastRow, int firstColumn, int lastColumn) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix");
    LinearAlgebraValidation.requireIndexRange(firstRow, lastRow, matrix.length, "row");
    LinearAlgebraValidation.requireIndexRange(firstColumn, lastColumn, matrix[0].length, "column");

    return toMatrix(matrix).getSubMatrix(firstRow, lastRow, firstColumn, lastColumn).getData();
  }

  /** {@inheritDoc} */
  @Override
  public double[][] withSubmatrix(double[][] matrix, int firstRow, int firstColumn, double[][] block) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix");
    LinearAlgebraValidation.requireMatrix(block, "Block");
    LinearAlgebraValidation.requireBlockFits(matrix, firstRow, firstColumn, block);

    RealMatrix result = toMatrix(matrix);
    result.setSubMatrix(block, firstRow, firstColumn);
    return result.getData();
  }

  /**
   * Compute the singular values of a matrix in descending order.
   *
   * @param matrix matrix to decompose
   * @return the singular values, one per {@code min(rows, columns)}
   */
  private double[] singularValues(double[][] matrix) {
    try {
      return new SingularValueDecomposition(toMatrix(matrix)).getSingularValues();
    } catch (MaxCountExceededException exception) {
      throw new LinearAlgebraException("Singular value decomposition did not converge.", exception);
    }
  }

  /**
   * Compute the tolerance below which a singular value counts as zero.
   *
   * @param matrix matrix being examined
   * @param largestSingularValue largest singular value found
   * @return the rank tolerance
   */
  private double rankTolerance(double[][] matrix, double largestSingularValue) {
    return Math.max(matrix.length, matrix[0].length) * MACHINE_EPSILON * largestSingularValue;
  }

  /**
   * Convert a validated array to a Commons Math matrix without sharing its storage.
   *
   * @param matrix validated rectangular matrix
   * @return a Commons Math matrix holding a copy of the data
   */
  private RealMatrix toMatrix(double[][] matrix) {
    return new Array2DRowRealMatrix(matrix, true);
  }

  /**
   * Convert a validated array to a Commons Math vector without sharing its storage.
   *
   * @param vector validated vector
   * @return a Commons Math vector holding a copy of the data
   */
  private RealVector toVector(double[] vector) {
    return new ArrayRealVector(vector, true);
  }

  /**
   * Flatten a matrix into a vector for finite-result validation.
   *
   * @param matrix matrix to flatten
   * @return a row-major array containing every matrix entry
   */
  private double[] flatten(double[][] matrix) {
    int columns = matrix[0].length;
    double[] result = new double[matrix.length * columns];
    for (int row = 0; row < matrix.length; row++) {
      System.arraycopy(matrix[row], 0, result, row * columns, columns);
    }
    return result;
  }
}
