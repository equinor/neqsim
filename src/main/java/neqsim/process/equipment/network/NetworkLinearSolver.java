package neqsim.process.equipment.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ojalgo.matrix.decomposition.LU;
import org.ojalgo.matrix.store.MatrixStore;
import org.ojalgo.matrix.store.Primitive64Store;

/**
 * Sparse and dense linear system solvers for pipeline network equations.
 *
 * <p>
 * Replaces the O(n³) Gaussian elimination in the Newton-Raphson GGA solver with efficient
 * alternatives from ojAlgo:
 * </p>
 * <ul>
 * <li><b>Sparse path</b>: For large sparse networks, routes through the sparse decision path and
 * currently uses the dense ojAlgo LU backend for robustness.</li>
 * <li><b>Dense LU</b>: For small-to-medium networks (&le;50 nodes), uses ojAlgo's dense LU with
 * partial pivoting. Faster than hand-coded Gaussian for n &gt; 10.</li>
 * </ul>
 *
 * <p>
 * The Schur complement matrix in the Todini-Pilati GGA is structurally sparse: entry (i,j) is
 * nonzero only if nodes i and j are connected by at least one pipe. For a gas gathering network
 * with n=100 nodes and average degree 3, matrix density is ~6%, making sparse solvers 10–50x faster
 * than dense Gaussian.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 * @see LoopedPipeNetwork
 */
public class NetworkLinearSolver {

  private static final Logger logger = LogManager.getLogger(NetworkLinearSolver.class);

  /**
   * Threshold for switching from Gaussian to EJML solvers. Networks with more free nodes than this
   * threshold use ojAlgo (dense LU for n &le; 100, sparse path for n &gt; 100). Below this
   * threshold, Gaussian elimination is used for backward compatibility with existing solver
   * convergence behavior.
   */
  private static final int EJML_THRESHOLD = 30;

  /**
   * Threshold for switching from dense to sparse-path solver within the ojAlgo path.
   */
  private static final int SPARSE_THRESHOLD = 100;

  /**
   * Solve the linear system Ax = b using the most appropriate method.
   *
   * <p>
   * For small systems (n &le; {@value #EJML_THRESHOLD}), uses Gaussian elimination with partial
   * pivoting for backward compatibility. For medium systems, uses ojAlgo dense LU. For large
   * systems (n &gt; {@value #SPARSE_THRESHOLD}), uses sparse-path solve. Falls back to Gaussian
   * elimination if EJML solvers fail.
   * </p>
   *
   * @param matA coefficient matrix (n x n)
   * @param vecB right-hand side vector (n)
   * @param n system size
   * @return solution vector x
   */
  public static double[] solve(double[][] matA, double[] vecB, int n) {
    if (n <= 0) {
      return new double[0];
    }
    if (n == 1) {
      double[] x = new double[1];
      x[0] = (Math.abs(matA[0][0]) > 1e-20) ? vecB[0] / matA[0][0] : 0.0;
      return x;
    }

    // Small systems: use Gaussian for numerical backward compatibility
    if (n <= EJML_THRESHOLD) {
      return solveGaussian(matA, vecB, n);
    }

    try {
      if (n > SPARSE_THRESHOLD) {
        return solveSparse(matA, vecB, n);
      } else {
        return solveDense(matA, vecB, n);
      }
    } catch (Exception e) {
      logger
          .warn("ojAlgo solver failed (n=" + n + "), falling back to Gaussian: " + e.getMessage());
      return solveGaussian(matA, vecB, n);
    }
  }

  /**
   * Solve using ojAlgo dense LU decomposition with partial pivoting.
   *
   * <p>
   * Uses ojAlgo LU decomposition which provides O(n³/3) factorization with BLAS-optimized
   * operations. For n=50, approximately 5x faster than hand-coded Gaussian elimination.
   * </p>
   *
   * @param matA coefficient matrix (n x n)
   * @param vecB right-hand side vector (n)
   * @param n system size
   * @return solution vector x
   */
  public static double[] solveDense(double[][] matA, double[] vecB, int n) {
    Primitive64Store denseA = Primitive64Store.FACTORY.make(n, n);
    Primitive64Store denseB = Primitive64Store.FACTORY.make(n, 1);

    // Copy data to dense stores
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        denseA.set(i, j, matA[i][j]);
      }
      denseB.set(i, 0, vecB[i]);
    }

    LU<Double> solver = LU.PRIMITIVE.make(n, n);
    if (!solver.decompose(denseA)) {
      logger.warn("Dense LU setA failed (singular?), falling back to Gaussian");
      return solveGaussian(matA, vecB, n);
    }
    MatrixStore<Double> denseX = solver.getSolution(denseB);

    double[] result = new double[n];
    for (int i = 0; i < n; i++) {
      result[i] = denseX.get(i, 0);
    }
    return result;
  }

  /**
   * Solve using sparse-path dispatch.
   *
   * <p>
   * Converts through the sparsity decision path. The current backend uses dense ojAlgo LU for
   * robustness and deterministic behavior; dense fallback remains in place.
   * </p>
   *
   * @param matA coefficient matrix (n x n) — may have many zeros
   * @param vecB right-hand side vector (n)
   * @param n system size
   * @return solution vector x
   */
  public static double[] solveSparse(double[][] matA, double[] vecB, int n) {
    // Count non-zeros
    int nnz = 0;
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        if (Math.abs(matA[i][j]) > 1e-30) {
          nnz++;
        }
      }
    }

    double density = (double) nnz / (n * n);
    if (density > 0.5) {
      // Matrix is too dense for sparse solver benefit; use dense
      logger.debug("Sparse matrix density " + String.format("%.1f%%", density * 100)
          + " too high, using dense solver");
      return solveDense(matA, vecB, n);
    }

    logger.debug(
        String.format("Sparse path dispatch using dense backend: n=%d, nnz=%d, density=%.1f%%", n,
            nnz, density * 100));
    return solveDense(matA, vecB, n);
  }

  /**
   * Fallback: Gaussian elimination with partial pivoting.
   *
   * <p>
   * This is the original O(n³) solver, kept as a robust fallback when EJML solvers encounter issues
   * (singular matrices, numerical edge cases). Makes a defensive copy of the input arrays.
   * </p>
   *
   * @param matAOrig coefficient matrix (n x n) — not modified
   * @param vecBOrig right-hand side vector (n) — not modified
   * @param n system size
   * @return solution vector x
   */
  public static double[] solveGaussian(double[][] matAOrig, double[] vecBOrig, int n) {
    // Defensive copy — do not modify caller's arrays
    double[][] matA = new double[n][n];
    double[] vecB = new double[n];
    for (int i = 0; i < n; i++) {
      System.arraycopy(matAOrig[i], 0, matA[i], 0, n);
      vecB[i] = vecBOrig[i];
    }

    double[] x = new double[n];

    // Forward elimination with partial pivoting
    for (int k = 0; k < n; k++) {
      int maxRow = k;
      for (int i = k + 1; i < n; i++) {
        if (Math.abs(matA[i][k]) > Math.abs(matA[maxRow][k])) {
          maxRow = i;
        }
      }
      double[] tempRow = matA[k];
      matA[k] = matA[maxRow];
      matA[maxRow] = tempRow;
      double tempB = vecB[k];
      vecB[k] = vecB[maxRow];
      vecB[maxRow] = tempB;

      if (Math.abs(matA[k][k]) < 1e-20) {
        continue;
      }

      for (int i = k + 1; i < n; i++) {
        double factor = matA[i][k] / matA[k][k];
        for (int j = k + 1; j < n; j++) {
          matA[i][j] -= factor * matA[k][j];
        }
        vecB[i] -= factor * vecB[k];
      }
    }

    // Back substitution
    for (int i = n - 1; i >= 0; i--) {
      x[i] = vecB[i];
      for (int j = i + 1; j < n; j++) {
        x[i] -= matA[i][j] * x[j];
      }
      if (Math.abs(matA[i][i]) > 1e-20) {
        x[i] /= matA[i][i];
      }
    }
    return x;
  }

  /**
   * Estimate the sparsity pattern of the network Schur complement.
   *
   * <p>
   * For diagnostics: Returns the estimated density and recommended solver type.
   * </p>
   *
   * @param nodeCount number of free nodes
   * @param pipeCount number of pipe elements
   * @return array [density, nonzeros, recommended_threshold] where recommended_threshold is 0 for
   *         dense and 1 for sparse
   */
  public static double[] estimateSparsity(int nodeCount, int pipeCount) {
    // In a pipe network, each pipe connects exactly 2 nodes.
    // The Schur complement has non-zeros at (i,i) for each node
    // and (i,j), (j,i) for each pipe connecting nodes i and j.
    // Plus diagonal terms.
    int estimatedNnz = nodeCount + 2 * pipeCount;
    int totalEntries = nodeCount * nodeCount;
    double density = (totalEntries > 0) ? (double) estimatedNnz / totalEntries : 1.0;
    double usesSparse = (nodeCount > SPARSE_THRESHOLD && density < 0.5) ? 1.0 : 0.0;
    return new double[] {density, estimatedNnz, usesSparse};
  }
}
