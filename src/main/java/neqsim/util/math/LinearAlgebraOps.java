package neqsim.util.math;

import java.util.Map;
import Jama.Matrix;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.ojalgo.matrix.decomposition.LU;
import org.ojalgo.matrix.decomposition.SingularValue;
import org.ojalgo.matrix.store.MatrixStore;
import org.ojalgo.matrix.store.Primitive64Store;

/**
 * Shared linear algebra utility methods used across NeqSim solvers.
 *
 * <p>
 * This class centralizes dense linear algebra operations that are reused by multiple thermodynamic and process modules,
 * so domain classes can focus on model-specific logic.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class LinearAlgebraOps {
  /** Lightweight dense matrix container for small internal linear-algebra kernels. */
  public static final class DenseMatrix {
    public final int numRows;
    public final int numCols;
    private final double[] values;

    /**
     * Creates a dense matrix with zero-initialized values.
     *
     * @param rows number of rows
     * @param cols number of columns
     */
    public DenseMatrix(int rows, int cols) {
      this.numRows = rows;
      this.numCols = cols;
      this.values = new double[rows * cols];
    }

    /**
     * Creates a dense matrix by copying a 2D array.
     *
     * @param matrix source matrix
     */
    public DenseMatrix(double[][] matrix) {
      this(matrix.length, matrix.length == 0 ? 0 : matrix[0].length);
      for (int i = 0; i < numRows; i++) {
        for (int j = 0; j < numCols; j++) {
          unsafe_set(i, j, matrix[i][j]);
        }
      }
    }

    /**
     * Reads a matrix value without bounds checking.
     *
     * @param row row index
     * @param col column index
     * @return value at {@code (row, col)}
     */
    public double unsafe_get(int row, int col) {
      return values[row * numCols + col];
    }

    /**
     * Writes a matrix value without bounds checking.
     *
     * @param row row index
     * @param col column index
     * @param value value to write
     */
    public void unsafe_set(int row, int col, double value) {
      values[row * numCols + col] = value;
    }

    /**
     * Gets the packed row-major matrix data.
     *
     * @return backing data array
     */
    public double[] getData() {
      return values;
    }
  }

  /** Functional getter for matrix elements. */
  public interface MatrixElementGetter {
    /**
     * Reads one element from a matrix-like structure.
     *
     * @param row row index
     * @param col column index
     * @return matrix value at {@code (row, col)}
     */
    double get(int row, int col);
  }

  /** Functional setter for matrix elements. */
  public interface MatrixElementSetter {
    /**
     * Writes one element to a matrix-like structure.
     *
     * @param row row index
     * @param col column index
     * @param value value to write
     */
    void set(int row, int col, double value);
  }

  /** Utility class: no instances. */
  private LinearAlgebraOps() {
  }

  /**
   * Decomposes a square matrix into a provided LU factorization using callback-based element access.
   *
   * @param solver LU solver instance to populate
   * @param size matrix dimension
   * @param matrixGetter callback used to read matrix values
   * @return {@code true} if decomposition succeeded, {@code false} otherwise
   */
  public static boolean decomposeLu(LU<Double> solver, int size, MatrixElementGetter matrixGetter) {
    Primitive64Store store = Primitive64Store.FACTORY.make(size, size);
    for (int i = 0; i < size; i++) {
      for (int j = 0; j < size; j++) {
        store.set(i, j, matrixGetter.get(i, j));
      }
    }
    return solver.decompose(store);
  }

  /**
   * Solves {@code A X = B} from a previously decomposed LU solver using callback-based matrix access.
   *
   * @param solver LU solver with prior successful decomposition
   * @param rows number of rows in {@code B} and {@code X}
   * @param cols number of columns in {@code B} and {@code X}
   * @param rhsGetter callback used to read right-hand-side matrix values
   * @param outSetter callback used to write solution matrix values
   */
  public static void solveLu(LU<Double> solver, int rows, int cols, MatrixElementGetter rhsGetter,
      MatrixElementSetter outSetter) {
    Primitive64Store rhsStore = Primitive64Store.FACTORY.make(rows, cols);
    for (int i = 0; i < rows; i++) {
      for (int j = 0; j < cols; j++) {
        rhsStore.set(i, j, rhsGetter.get(i, j));
      }
    }
    MatrixStore<Double> sol = solver.getSolution(rhsStore);
    for (int i = 0; i < rows; i++) {
      for (int j = 0; j < cols; j++) {
        outSetter.set(i, j, sol.get(i, j));
      }
    }
  }

  /**
   * Builds a dense {@link Matrix} from sparse row/column map storage.
   *
   * @param rows number of matrix rows
   * @param columns number of matrix columns
   * @param sparseValues sparse matrix values keyed by row and then column
   * @return dense matrix representation
   */
  public static Matrix toDenseMatrix(int rows, int columns, Map<Integer, Map<Integer, Double>> sparseValues) {
    double[][] dense = new double[rows][columns];
    for (Map.Entry<Integer, Map<Integer, Double>> rowEntry : sparseValues.entrySet()) {
      int row = rowEntry.getKey().intValue();
      for (Map.Entry<Integer, Double> columnEntry : rowEntry.getValue().entrySet()) {
        dense[row][columnEntry.getKey().intValue()] = columnEntry.getValue().doubleValue();
      }
    }
    return new Matrix(dense);
  }

  /**
   * Dense matrix multiplication: {@code out = a * b}.
   *
   * @param a left matrix
   * @param b right matrix
   * @param out output matrix
   */
  public static void mult(DenseMatrix a, DenseMatrix b, DenseMatrix out) {
    for (int i = 0; i < a.numRows; i++) {
      for (int j = 0; j < b.numCols; j++) {
        double sum = 0.0;
        for (int k = 0; k < a.numCols; k++) {
          sum += a.unsafe_get(i, k) * b.unsafe_get(k, j);
        }
        out.unsafe_set(i, j, sum);
      }
    }
  }

  /**
   * Dense matrix subtraction: {@code out = a - b}.
   *
   * @param a left matrix
   * @param b right matrix
   * @param out output matrix
   */
  public static void subtract(DenseMatrix a, DenseMatrix b, DenseMatrix out) {
    for (int i = 0; i < a.numRows; i++) {
      for (int j = 0; j < a.numCols; j++) {
        out.unsafe_set(i, j, a.unsafe_get(i, j) - b.unsafe_get(i, j));
      }
    }
  }

  /**
   * Frobenius norm of a dense matrix.
   *
   * @param matrix input matrix
   * @return {@code ||matrix||_F}
   */
  public static double normF(DenseMatrix matrix) {
    return vectorNorm(matrix.getData());
  }

  /**
   * Solves {@code A x = b} using LU decomposition.
   *
   * @param matrix coefficient matrix {@code A} with shape {@code n x n}
   * @param rhs right-hand side vector {@code b} with length {@code n}
   * @param solution output vector {@code x} with length {@code n}
   * @return {@code true} if LU decomposition succeeded and {@code solution} was written, {@code false} otherwise
   */
  public static boolean solveLinearSystem(double[][] matrix, double[] rhs, double[] solution) {
    int n = rhs.length;
    Primitive64Store aStore = Primitive64Store.FACTORY.make(n, n);
    Primitive64Store bStore = Primitive64Store.FACTORY.make(n, 1);

    for (int i = 0; i < n; i++) {
      bStore.set(i, 0, rhs[i]);
      for (int j = 0; j < n; j++) {
        aStore.set(i, j, matrix[i][j]);
      }
    }

    LU<Double> lu = LU.PRIMITIVE.make(n, n);
    if (!lu.decompose(aStore)) {
      return false;
    }

    MatrixStore<Double> sol = lu.getSolution(bStore);
    for (int i = 0; i < n; i++) {
      solution[i] = sol.get(i, 0);
    }
    return true;
  }

  /**
   * Solves {@code A x = b} in place using Gaussian elimination with partial pivoting.
   *
   * <p>
   * The coefficient matrix {@code matrix} and right-hand side {@code rhs} are modified in place, and {@code rhs} is
   * overwritten with the solution vector on return.
   * </p>
   *
   * @param matrix coefficient matrix (modified in place)
   * @param rhs right-hand side vector (overwritten with solution)
   * @param n system dimension
   */
  public static void solveLinearSystemInPlace(double[][] matrix, double[] rhs, int n) {
    for (int col = 0; col < n; col++) {
      int maxRow = col;
      double maxVal = Math.abs(matrix[col][col]);
      for (int row = col + 1; row < n; row++) {
        double val = Math.abs(matrix[row][col]);
        if (val > maxVal) {
          maxVal = val;
          maxRow = row;
        }
      }
      if (maxRow != col) {
        double[] tmpRow = matrix[col];
        matrix[col] = matrix[maxRow];
        matrix[maxRow] = tmpRow;
        double tmpVal = rhs[col];
        rhs[col] = rhs[maxRow];
        rhs[maxRow] = tmpVal;
      }
      double pivot = matrix[col][col];
      if (Math.abs(pivot) < 1.0e-30) {
        continue;
      }
      for (int row = col + 1; row < n; row++) {
        double factor = matrix[row][col] / pivot;
        for (int k = col + 1; k < n; k++) {
          matrix[row][k] -= factor * matrix[col][k];
        }
        rhs[row] -= factor * rhs[col];
      }
    }
    for (int row = n - 1; row >= 0; row--) {
      double s = rhs[row];
      for (int k = row + 1; k < n; k++) {
        s -= matrix[row][k] * rhs[k];
      }
      rhs[row] = s / matrix[row][row];
    }
  }

  /**
   * Solves {@code A X = B} for matrix right-hand sides using LU decomposition.
   *
   * @param matrix coefficient matrix {@code A}
   * @param rhs right-hand side matrix {@code B}
   * @return solution matrix {@code X}
   * @throws IllegalStateException if LU decomposition fails
   */
  public static MatrixStore<Double> solveLinearSystem(MatrixStore<Double> matrix, MatrixStore<Double> rhs) {
    int rows = (int) matrix.countRows();
    int cols = (int) matrix.countColumns();
    LU<Double> lu = LU.PRIMITIVE.make(rows, cols);
    if (!lu.decompose(matrix)) {
      throw new IllegalStateException("LU decomposition failed in linear solve");
    }
    return lu.getSolution(rhs);
  }

  /**
   * Solves {@code A x = b} from pre-allocated ojAlgo stores.
   *
   * @param matrix coefficient matrix store {@code A}
   * @param rhs right-hand side store {@code b} with shape {@code n x 1}
   * @param solution output vector {@code x} with length {@code n}
   * @param solver pre-allocated LU solver instance
   * @return {@code true} if decomposition succeeded and {@code solution} was written, {@code false} otherwise
   */
  public static boolean solveLinearSystem(Primitive64Store matrix, Primitive64Store rhs, double[] solution,
      LU<Double> solver) {
    if (!solver.decompose(matrix)) {
      return false;
    }
    MatrixStore<Double> sol = solver.getSolution(rhs);
    for (int i = 0; i < solution.length; i++) {
      solution[i] = sol.get(i, 0);
    }
    return true;
  }

  /**
   * Copies a dense square matrix into a reusable ojAlgo work store.
   *
   * @param source source dense matrix
   * @param target target dense matrix
   * @param size matrix dimension
   */
  public static void copyDenseStore(Primitive64Store source, Primitive64Store target, int size) {
    for (int i = 0; i < size; i++) {
      for (int j = 0; j < size; j++) {
        target.set(i, j, source.get(i, j));
      }
    }
  }

  /**
   * Enforces strict symmetry in a square dense matrix by averaging mirrored off-diagonal entries.
   *
   * @param matrix square dense matrix to symmetrize in place
   */
  public static void symmetriseMmatrix(Primitive64Store matrix) {
    int size = (int) matrix.countRows();
    for (int i = 0; i < size; i++) {
      for (int j = i + 1; j < size; j++) {
        double average = 0.5 * (matrix.doubleValue(i, j) + matrix.doubleValue(j, i));
        matrix.set(i, j, average);
        matrix.set(j, i, average);
      }
    }
  }

  /**
   * Computes determinant of a square matrix from LU decomposition.
   *
   * @param matrix square matrix
   * @return determinant value, or {@code 0.0} if decomposition fails
   */
  public static double determinant(double[][] matrix) {
    int n = matrix.length;
    Primitive64Store store = Primitive64Store.FACTORY.make(n, n);
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        store.set(i, j, matrix[i][j]);
      }
    }
    LU<Double> lu = LU.PRIMITIVE.make(n, n);
    if (!lu.decompose(store)) {
      return 0.0;
    }
    return lu.getDeterminant();
  }

  /**
   * Computes inverse of a square matrix using LU decomposition.
   *
   * @param matrix square matrix
   * @return inverse matrix
   * @throws IllegalStateException if decomposition fails
   */
  public static double[][] inverse(double[][] matrix) {
    int n = matrix.length;
    Primitive64Store store = Primitive64Store.FACTORY.make(n, n);
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        store.set(i, j, matrix[i][j]);
      }
    }

    LU<Double> lu = LU.PRIMITIVE.make(n, n);
    if (!lu.decompose(store)) {
      throw new IllegalStateException("Matrix inversion failed: LU decomposition failed");
    }

    MatrixStore<Double> inv = lu.getInverse();
    double[][] out = new double[n][n];
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        out[i][j] = inv.get(i, j);
      }
    }
    return out;
  }

  /**
   * Computes Moore-Penrose pseudo-inverse of a matrix using SVD.
   *
   * @param matrix input matrix
   * @return pseudo-inverse matrix
   * @throws IllegalStateException if SVD decomposition fails
   */
  public static double[][] pseudoInverse(double[][] matrix) {
    int nRows = matrix.length;
    int nCols = matrix[0].length;
    Primitive64Store store = Primitive64Store.FACTORY.make(nRows, nCols);
    for (int i = 0; i < nRows; i++) {
      for (int j = 0; j < nCols; j++) {
        store.set(i, j, matrix[i][j]);
      }
    }

    SingularValue<Double> svd = SingularValue.PRIMITIVE.make(nRows, nCols);
    if (!svd.decompose(store)) {
      throw new IllegalStateException("Pseudo-inverse failed: SVD decomposition failed");
    }

    MatrixStore<Double> inv = svd.getInverse();
    double[][] out = new double[(int) inv.countRows()][(int) inv.countColumns()];
    for (int i = 0; i < out.length; i++) {
      for (int j = 0; j < out[i].length; j++) {
        out[i][j] = inv.get(i, j);
      }
    }
    return out;
  }

  /**
   * Estimates the 2-norm condition number from SVD singular values.
   *
   * @param matrix input matrix
   * @return estimated condition number, or {@code Double.POSITIVE_INFINITY} if decomposition fails or the minimum
   * singular value is effectively zero
   */
  public static double conditionP2(double[][] matrix) {
    int nRows = matrix.length;
    int nCols = matrix[0].length;
    Primitive64Store store = Primitive64Store.FACTORY.make(nRows, nCols);
    for (int i = 0; i < nRows; i++) {
      for (int j = 0; j < nCols; j++) {
        store.set(i, j, matrix[i][j]);
      }
    }

    SingularValue<Double> svd = SingularValue.PRIMITIVE.make(nRows, nCols);
    if (!svd.decompose(store)) {
      return Double.POSITIVE_INFINITY;
    }
    double maxSingular = svd.getOperatorNorm();
    double minSingular = svd.getFrobeniusNorm() / Math.max(1.0, svd.getRank());
    if (Math.abs(minSingular) < 1.0e-30) {
      return Double.POSITIVE_INFINITY;
    }
    return maxSingular / minSingular;
  }

  /**
   * Multiplies two primitive matrices.
   *
   * @param left left matrix
   * @param right right matrix
   * @return product matrix
   */
  public static double[][] multiply(double[][] left, double[][] right) {
    Primitive64Store leftStore = Primitive64Store.FACTORY.rows(left);
    Primitive64Store rightStore = Primitive64Store.FACTORY.rows(right);
    MatrixStore<Double> product = leftStore.multiply(rightStore);
    int nRows = (int) product.countRows();
    int nCols = (int) product.countColumns();
    double[][] out = new double[nRows][nCols];
    for (int i = 0; i < nRows; i++) {
      for (int j = 0; j < nCols; j++) {
        out[i][j] = product.get(i, j);
      }
    }
    return out;
  }

  /**
   * Multiplies a primitive matrix and vector and scales the result.
   *
   * @param matrix coefficient matrix
   * @param vector right-hand side vector
   * @param scale scaling factor applied to the product
   * @return scaled product vector
   */
  public static double[] multiply(double[][] matrix, double[] vector, double scale) {
    Primitive64Store matrixStore = Primitive64Store.FACTORY.rows(matrix);
    Primitive64Store vectorStore = Primitive64Store.FACTORY.make(vector.length, 1);
    for (int i = 0; i < vector.length; i++) {
      vectorStore.set(i, 0, vector[i]);
    }
    MatrixStore<Double> product = matrixStore.multiply(vectorStore);
    double[] out = new double[(int) product.countRows()];
    for (int i = 0; i < out.length; i++) {
      out[i] = product.get(i, 0) * scale;
    }
    return out;
  }

  /**
   * Calculates Euclidean norm of a vector.
   *
   * @param vector input vector
   * @return {@code ||vector||_2}
   */
  public static double vectorNorm(double[] vector) {
    double sum = 0.0;
    for (int i = 0; i < vector.length; i++) {
      sum += vector[i] * vector[i];
    }
    return Math.sqrt(sum);
  }

  /**
   * Calculates Euclidean norm of a column vector represented as {@code [n][1]}.
   *
   * @param columnVector input column vector
   * @return {@code ||columnVector||_2}
   */
  public static double columnNorm(double[][] columnVector) {
    double sum = 0.0;
    for (int i = 0; i < columnVector.length; i++) {
      sum += columnVector[i][0] * columnVector[i][0];
    }
    return Math.sqrt(sum);
  }

  /**
   * Solves {@code A x = b} using SVD pseudo-inverse as fallback for singular systems.
   *
   * @param matrix coefficient matrix {@code A}
   * @param rhs right-hand side vector {@code b}
   * @param solution output vector {@code x}
   * @return {@code true} if SVD decomposition succeeded and {@code solution} was written, {@code false} otherwise
   */
  public static boolean pseudoInverseSolve(double[][] matrix, double[] rhs, double[] solution) {
    int nRows = matrix.length;
    int nCols = matrix[0].length;
    Primitive64Store jacStore = Primitive64Store.FACTORY.make(nRows, nCols);
    Primitive64Store rhsStore = Primitive64Store.FACTORY.make(nRows, 1);

    for (int i = 0; i < nRows; i++) {
      rhsStore.set(i, 0, rhs[i]);
      for (int j = 0; j < nCols; j++) {
        jacStore.set(i, j, matrix[i][j]);
      }
    }

    SingularValue<Double> svd = SingularValue.PRIMITIVE.make(nRows, nCols);
    if (!svd.decompose(jacStore)) {
      return false;
    }

    MatrixStore<Double> result = svd.getInverse().multiply(rhsStore);
    for (int i = 0; i < solution.length; i++) {
      solution[i] = result.get(i, 0);
    }
    return true;
  }

  /**
   * Solves a least-squares system {@code A X ≈ B} using the SVD pseudo-inverse.
   *
   * <p>
   * This method supports one or more right-hand sides in {@code B} and is robust for rank-deficient or ill-conditioned
   * systems.
   * </p>
   *
   * @param matrix coefficient matrix {@code A} with dimensions {@code m x n}
   * @param rhs right-hand side matrix {@code B} with dimensions {@code m x k}
   * @return solution matrix {@code X} with dimensions {@code n x k}
   * @throws IllegalStateException if SVD decomposition fails
   */
  public static double[][] solveLeastSquares(double[][] matrix, double[][] rhs) {
    int nRows = matrix.length;
    int nCols = matrix[0].length;
    int rhsCols = rhs[0].length;

    Primitive64Store aStore = Primitive64Store.FACTORY.make(nRows, nCols);
    Primitive64Store bStore = Primitive64Store.FACTORY.make(nRows, rhsCols);

    for (int i = 0; i < nRows; i++) {
      for (int j = 0; j < nCols; j++) {
        aStore.set(i, j, matrix[i][j]);
      }
      for (int j = 0; j < rhsCols; j++) {
        bStore.set(i, j, rhs[i][j]);
      }
    }

    SingularValue<Double> svd = SingularValue.PRIMITIVE.make(nRows, nCols);
    if (!svd.decompose(aStore)) {
      throw new IllegalStateException("Least-squares failed: SVD decomposition failed");
    }

    MatrixStore<Double> result = svd.getInverse().multiply(bStore);
    double[][] out = new double[(int) result.countRows()][(int) result.countColumns()];
    for (int i = 0; i < out.length; i++) {
      for (int j = 0; j < out[i].length; j++) {
        out[i][j] = result.get(i, j);
      }
    }
    return out;
  }

  /**
   * Computes an approximate null-space vector from the right singular vector associated with the smallest singular
   * value.
   *
   * @param matrix input Jacobian or coefficient matrix
   * @return right singular vector spanning the approximate null-space
   */
  public static double[] calcNullVector(double[][] matrix) {
    RealMatrix jacobian = new Array2DRowRealMatrix(matrix, false);
    SingularValueDecomposition svd = new SingularValueDecomposition(jacobian);
    RealMatrix v = svd.getV();
    int lastCol = v.getColumnDimension() - 1;
    return v.getColumn(lastCol);
  }
}
