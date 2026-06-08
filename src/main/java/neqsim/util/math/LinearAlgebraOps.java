package neqsim.util.math;

import org.ojalgo.matrix.decomposition.LU;
import org.ojalgo.matrix.decomposition.SingularValue;
import org.ojalgo.matrix.store.MatrixStore;
import org.ojalgo.matrix.store.Primitive64Store;

/**
 * Shared linear algebra utility methods used across NeqSim solvers.
 *
 * <p>
 * This class centralizes dense linear algebra operations that are reused by multiple thermodynamic
 * and process modules, so domain classes can focus on model-specific logic.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class LinearAlgebraOps {
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
  private LinearAlgebraOps() {}

  /**
   * Decomposes a square matrix into a provided LU factorization using callback-based element
   * access.
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
   * Solves {@code A X = B} from a previously decomposed LU solver using callback-based matrix
   * access.
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
   * Solves {@code A x = b} using LU decomposition.
   *
   * @param matrix coefficient matrix {@code A} with shape {@code n x n}
   * @param rhs right-hand side vector {@code b} with length {@code n}
   * @param solution output vector {@code x} with length {@code n}
   * @return {@code true} if LU decomposition succeeded and {@code solution} was written,
   *         {@code false} otherwise
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
   * Solves {@code A X = B} for matrix right-hand sides using LU decomposition.
   *
   * @param matrix coefficient matrix {@code A}
   * @param rhs right-hand side matrix {@code B}
   * @return solution matrix {@code X}
   * @throws IllegalStateException if LU decomposition fails
   */
  public static MatrixStore<Double> solveLinearSystem(MatrixStore<Double> matrix,
      MatrixStore<Double> rhs) {
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
   * @return {@code true} if decomposition succeeded and {@code solution} was written, {@code false}
   *         otherwise
   */
  public static boolean solveLinearSystem(Primitive64Store matrix, Primitive64Store rhs,
      double[] solution, LU<Double> solver) {
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
   * @return {@code true} if SVD decomposition succeeded and {@code solution} was written,
   *         {@code false} otherwise
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
}
