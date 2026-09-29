package neqsim.mathlib.linearalgebra;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.NormOps_DDRM;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.interfaces.decomposition.SingularValueDecomposition_F64;
import org.ejml.interfaces.linsol.LinearSolverDense;

/**
 * {@link LinearAlgebraOperations} backed by EJML.
 *
 * <p>
 * Square solves and inversion use LU with partial pivoting, least squares uses QR, and rank and conditioning come from
 * a singular value decomposition. Backend performance depends on matrix size, operation and runtime; select a backend
 * using representative benchmarks.
 * </p>
 *
 * <p>
 * A failure reported by {@code LinearSolverDense.setA} and any non-finite solve or inverse result are mapped to a
 * {@link LinearAlgebraException}. LU solves and inversion do not apply a numerical rank or conditioning threshold. An
 * ill-conditioned system can therefore return a finite result with poor accuracy; callers must assess conditioning and
 * residuals when accuracy matters. Least squares explicitly checks numerical column rank before solving.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public final class EjmlLinearAlgebra implements LinearAlgebraOperations {
  /** Backend name reported by {@link #getName()}. */
  public static final String NAME = "EJML";

  private static final double MACHINE_EPSILON = Math.ulp(1.0);

  /**
   * Constructor for EjmlLinearAlgebra.
   */
  public EjmlLinearAlgebra() {
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

    int size = matrixA.length;
    LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.lu(size);
    double[] solution = applySolver(solver, matrixA, vectorB, size, "LU solve");
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

    // The QR solver accepts a rank-deficient matrix and returns a meaningless answer, so rank is checked up front.
    if (rank(matrixA) < columns) {
      throw new LinearAlgebraException(
          "Least-squares solve failed: the " + rows + " by " + columns + " matrix does not have full column rank.");
    }

    LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.leastSquares(rows, columns);
    double[] solution = applySolver(solver, matrixA, vectorB, columns, "Least-squares solve");
    LinearAlgebraValidation.requireFiniteResult(solution, "Least-squares solve");
    return solution;
  }

  /** {@inheritDoc} */
  @Override
  public double[][] invert(double[][] matrix) {
    LinearAlgebraValidation.requireSquareMatrix(matrix, "Matrix to invert");

    int size = matrix.length;
    DMatrixRMaj source = toDense(matrix);
    LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.lu(size);
    if (!solver.setA(source)) {
      throw new LinearAlgebraException("Matrix of size " + size + " is singular and cannot be inverted.");
    }

    DMatrixRMaj inverse = new DMatrixRMaj(size, size);
    solver.invert(inverse);
    LinearAlgebraValidation.requireFiniteResult(inverse.getData(), "Inversion");
    return toArray(inverse);
  }

  /** {@inheritDoc} */
  @Override
  public double[][] multiply(double[][] matrixA, double[][] matrixB) {
    LinearAlgebraValidation.requireMatrix(matrixA, "Left operand");
    LinearAlgebraValidation.requireMatrix(matrixB, "Right operand");
    LinearAlgebraValidation.requireDimension(matrixB.length, matrixA[0].length,
        "Right-operand row count must match the left-operand column count");

    DMatrixRMaj product = new DMatrixRMaj(matrixA.length, matrixB[0].length);
    CommonOps_DDRM.mult(toDense(matrixA), toDense(matrixB), product);
    return toArray(product);
  }

  /** {@inheritDoc} */
  @Override
  public double[] multiply(double[][] matrixA, double[] vector) {
    LinearAlgebraValidation.requireMatrix(matrixA, "Matrix operand");
    LinearAlgebraValidation.requireVector(vector, "Vector operand");
    LinearAlgebraValidation.requireDimension(vector.length, matrixA[0].length,
        "Vector length must match the matrix column count");

    DMatrixRMaj product = new DMatrixRMaj(matrixA.length, 1);
    CommonOps_DDRM.mult(toDense(matrixA), new DMatrixRMaj(vector.length, 1, true, vector), product);
    return product.getData().clone();
  }

  /** {@inheritDoc} */
  @Override
  public double[][] add(double[][] matrixA, double[][] matrixB) {
    LinearAlgebraValidation.requireMatrix(matrixA, "Left operand");
    LinearAlgebraValidation.requireMatrix(matrixB, "Right operand");
    LinearAlgebraValidation.requireSameShape(matrixA, matrixB, "Addition");

    DMatrixRMaj sum = new DMatrixRMaj(matrixA.length, matrixA[0].length);
    CommonOps_DDRM.add(toDense(matrixA), toDense(matrixB), sum);
    return toArray(sum);
  }

  /** {@inheritDoc} */
  @Override
  public double[][] subtract(double[][] matrixA, double[][] matrixB) {
    LinearAlgebraValidation.requireMatrix(matrixA, "Left operand");
    LinearAlgebraValidation.requireMatrix(matrixB, "Right operand");
    LinearAlgebraValidation.requireSameShape(matrixA, matrixB, "Subtraction");

    DMatrixRMaj difference = new DMatrixRMaj(matrixA.length, matrixA[0].length);
    CommonOps_DDRM.subtract(toDense(matrixA), toDense(matrixB), difference);
    return toArray(difference);
  }

  /** {@inheritDoc} */
  @Override
  public double[] add(double[] vectorA, double[] vectorB) {
    LinearAlgebraValidation.requireVector(vectorA, "Left operand");
    LinearAlgebraValidation.requireVector(vectorB, "Right operand");
    LinearAlgebraValidation.requireDimension(vectorB.length, vectorA.length,
        "Operands of an addition must have the same length");

    DMatrixRMaj sum = new DMatrixRMaj(vectorA.length, 1);
    CommonOps_DDRM.add(toColumn(vectorA), toColumn(vectorB), sum);
    return sum.getData().clone();
  }

  /** {@inheritDoc} */
  @Override
  public double[] subtract(double[] vectorA, double[] vectorB) {
    LinearAlgebraValidation.requireVector(vectorA, "Left operand");
    LinearAlgebraValidation.requireVector(vectorB, "Right operand");
    LinearAlgebraValidation.requireDimension(vectorB.length, vectorA.length,
        "Operands of a subtraction must have the same length");

    DMatrixRMaj difference = new DMatrixRMaj(vectorA.length, 1);
    CommonOps_DDRM.subtract(toColumn(vectorA), toColumn(vectorB), difference);
    return difference.getData().clone();
  }

  /** {@inheritDoc} */
  @Override
  public double[][] scale(double[][] matrix, double factor) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix to scale");
    LinearAlgebraValidation.requireFiniteScalar(factor, "Scale factor");

    DMatrixRMaj scaled = new DMatrixRMaj(matrix.length, matrix[0].length);
    CommonOps_DDRM.scale(factor, toDense(matrix), scaled);
    return toArray(scaled);
  }

  /** {@inheritDoc} */
  @Override
  public double[] scale(double[] vector, double factor) {
    LinearAlgebraValidation.requireVector(vector, "Vector to scale");
    LinearAlgebraValidation.requireFiniteScalar(factor, "Scale factor");

    DMatrixRMaj scaled = new DMatrixRMaj(vector.length, 1);
    CommonOps_DDRM.scale(factor, new DMatrixRMaj(vector.length, 1, true, vector), scaled);
    return scaled.getData().clone();
  }

  /** {@inheritDoc} */
  @Override
  public double[][] identity(int size) {
    LinearAlgebraValidation.requirePositiveSize(size, "Identity matrix size");

    return toArray(CommonOps_DDRM.identity(size));
  }

  /** {@inheritDoc} */
  @Override
  public double[][] transpose(double[][] matrix) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix to transpose");

    DMatrixRMaj transposed = new DMatrixRMaj(matrix[0].length, matrix.length);
    CommonOps_DDRM.transpose(toDense(matrix), transposed);
    return toArray(transposed);
  }

  /** {@inheritDoc} */
  @Override
  public int rank(double[][] matrix) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix");

    double[] singularValues = singularValues(matrix);
    double tolerance = rankTolerance(matrix, maximum(singularValues));
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
    double largest = maximum(singularValues);
    double smallest = minimum(singularValues);
    if (smallest <= rankTolerance(matrix, largest)) {
      return Double.POSITIVE_INFINITY;
    }
    return largest / smallest;
  }

  /** {@inheritDoc} */
  @Override
  public double determinant(double[][] matrix) {
    LinearAlgebraValidation.requireSquareMatrix(matrix, "Matrix");

    return CommonOps_DDRM.det(toDense(matrix));
  }

  /** {@inheritDoc} */
  @Override
  public double euclideanNorm(double[] vector) {
    LinearAlgebraValidation.requireVector(vector, "Vector");

    return NormOps_DDRM.normF(new DMatrixRMaj(vector.length, 1, true, vector));
  }

  /** {@inheritDoc} */
  @Override
  public double oneNorm(double[][] matrix) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix");

    return NormOps_DDRM.inducedP1(toDense(matrix));
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

    // EJML takes an exclusive upper bound where the interface, following JAMA, takes an inclusive one.
    return toArray(CommonOps_DDRM.extract(toDense(matrix), firstRow, lastRow + 1, firstColumn, lastColumn + 1));
  }

  /** {@inheritDoc} */
  @Override
  public double[][] withSubmatrix(double[][] matrix, int firstRow, int firstColumn, double[][] block) {
    LinearAlgebraValidation.requireMatrix(matrix, "Matrix");
    LinearAlgebraValidation.requireMatrix(block, "Block");
    LinearAlgebraValidation.requireBlockFits(matrix, firstRow, firstColumn, block);

    DMatrixRMaj result = toDense(matrix);
    CommonOps_DDRM.insert(toDense(block), result, firstRow, firstColumn);
    return toArray(result);
  }

  /**
   * Factor the matrix, solve for the given right-hand side and return the solution as a plain array.
   *
   * @param solver a configured EJML solver
   * @param matrixA coefficient matrix
   * @param vectorB right-hand-side vector
   * @param solutionLength number of unknowns, which is the column count of {@code matrixA}
   * @param operation operation name used in the failure message
   * @return the solution vector
   * @throws LinearAlgebraException if the factorisation reports the system as unsolvable
   */
  private double[] applySolver(LinearSolverDense<DMatrixRMaj> solver, double[][] matrixA, double[] vectorB,
      int solutionLength, String operation) {
    if (!solver.setA(toDense(matrixA))) {
      throw new LinearAlgebraException(operation + " failed: the " + matrixA.length + " by " + matrixA[0].length
          + " matrix is singular or rank deficient.");
    }

    DMatrixRMaj solution = new DMatrixRMaj(solutionLength, 1);
    solver.solve(new DMatrixRMaj(vectorB.length, 1, true, vectorB), solution);
    return solution.getData().clone();
  }

  /**
   * Compute the singular values of a matrix.
   *
   * <p>
   * EJML does not guarantee the values are ordered, so callers must scan rather than index the ends.
   * </p>
   *
   * @param matrix matrix to decompose
   * @return the singular values, one per {@code min(rows, columns)}
   * @throws LinearAlgebraException if the decomposition does not converge
   */
  private double[] singularValues(double[][] matrix) {
    int rows = matrix.length;
    int columns = matrix[0].length;
    SingularValueDecomposition_F64<DMatrixRMaj> svd = DecompositionFactory_DDRM.svd(rows, columns, false, false, true);
    if (!svd.decompose(toDense(matrix))) {
      throw new LinearAlgebraException(
          "Singular value decomposition of the " + rows + " by " + columns + " matrix did not converge.");
    }

    int count = svd.numberOfSingularValues();
    double[] values = new double[count];
    System.arraycopy(svd.getSingularValues(), 0, values, 0, count);
    return values;
  }

  /**
   * Tolerance below which a singular value counts as zero.
   *
   * @param matrix the matrix being examined
   * @param largestSingularValue the largest singular value found
   * @return the rank tolerance
   */
  private double rankTolerance(double[][] matrix, double largestSingularValue) {
    return Math.max(matrix.length, matrix[0].length) * MACHINE_EPSILON * largestSingularValue;
  }

  /**
   * Largest entry of an array.
   *
   * @param values values to scan
   * @return the maximum value
   */
  private double maximum(double[] values) {
    double largest = values[0];
    for (int index = 1; index < values.length; index++) {
      largest = Math.max(largest, values[index]);
    }
    return largest;
  }

  /**
   * Smallest entry of an array.
   *
   * @param values values to scan
   * @return the minimum value
   */
  private double minimum(double[] values) {
    double smallest = values[0];
    for (int index = 1; index < values.length; index++) {
      smallest = Math.min(smallest, values[index]);
    }
    return smallest;
  }

  /**
   * Copy a row-major array into an EJML matrix.
   *
   * @param matrix validated rectangular matrix
   * @return an EJML matrix holding a copy of the data
   */
  private DMatrixRMaj toDense(double[][] matrix) {
    return new DMatrixRMaj(matrix);
  }

  /**
   * Copy a vector into a single-column EJML matrix.
   *
   * @param vector validated vector
   * @return an EJML matrix with one column holding a copy of the data
   */
  private DMatrixRMaj toColumn(double[] vector) {
    return new DMatrixRMaj(vector.length, 1, true, vector);
  }

  /**
   * Copy an EJML matrix back into a row-major array.
   *
   * @param matrix EJML matrix to convert
   * @return a newly allocated row-major array
   */
  private double[][] toArray(DMatrixRMaj matrix) {
    int rows = matrix.getNumRows();
    int columns = matrix.getNumCols();
    double[][] result = new double[rows][columns];
    for (int row = 0; row < rows; row++) {
      System.arraycopy(matrix.getData(), row * columns, result[row], 0, columns);
    }
    return result;
  }
}
