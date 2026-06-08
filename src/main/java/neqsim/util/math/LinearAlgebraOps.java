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
  /** Utility class: no instances. */
  private LinearAlgebraOps() {}

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
