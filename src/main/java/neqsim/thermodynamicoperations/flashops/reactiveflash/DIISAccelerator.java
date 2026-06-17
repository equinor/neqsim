package neqsim.thermodynamicoperations.flashops.reactiveflash;

/**
 * DIIS (Direct Inversion in the Iterative Subspace) accelerator for fixed-point iterations.
 *
 * <p>
 * Implements the Pulay (1980) DIIS extrapolation to accelerate convergence of iterative solvers.
 * Stores a rolling history of iterate-residual pairs and finds the optimal linear combination
 * c_1,...,c_m that minimizes the squared residual norm subject to the constraint sum(c_i) = 1. The
 * extrapolated iterate is x_new = sum(c_i * x_i).
 * </p>
 *
 * <p>
 * The algorithm solves the augmented Pulay system:
 * </p>
 *
 * <table>
 * <caption>Pulay augmented linear system for DIIS coefficients</caption>
 * <tr>
 * <td>[B -1] [c] = [ 0]</td>
 * </tr>
 * <tr>
 * <td>[-1 0] [mu] = [-1]</td>
 * </tr>
 * </table>
 *
 * <p>
 * where B_ij = r_i dot r_j is the residual overlap matrix.
 * </p>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Pulay (1980) Chem. Phys. Lett. 73, 393-398</li>
 * <li>Pulay (1982) J. Comp. Chem. 3, 556-560</li>
 * <li>Anderson (1965) J. ACM 12, 547-560 (equivalent Anderson mixing)</li>
 * </ul>
 *
 * @author copilot
 * @version 1.0
 */
public class DIISAccelerator implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Maximum number of stored iterate-residual pairs. */
  private final int maxHistory;

  /** Length of each iterate and residual vector. */
  private final int vectorLength;

  /** Circular buffer of iterate vectors. */
  private final double[][] iterateHistory;

  /** Circular buffer of residual vectors. */
  private final double[][] residualHistory;

  /** Number of entries currently stored (up to maxHistory). */
  private int count;

  /** Index of the next slot to write in the circular buffer. */
  private int nextSlot;

  /**
   * Construct a DIIS accelerator.
   *
   * @param vectorLength length of each iterate and residual vector
   * @param maxHistory maximum number of stored pairs (typically 5-8)
   */
  public DIISAccelerator(int vectorLength, int maxHistory) {
    this.vectorLength = vectorLength;
    this.maxHistory = maxHistory;
    this.iterateHistory = new double[maxHistory][vectorLength];
    this.residualHistory = new double[maxHistory][vectorLength];
    this.count = 0;
    this.nextSlot = 0;
  }

  /**
   * Store a new iterate-residual pair in the circular buffer.
   *
   * <p>
   * The iterate and residual arrays are copied internally. When the buffer is full, the oldest
   * entry is overwritten.
   * </p>
   *
   * @param iterate the current iterate vector (will be copied)
   * @param residual the residual vector at this iterate (will be copied)
   */
  public void addEntry(double[] iterate, double[] residual) {
    System.arraycopy(iterate, 0, iterateHistory[nextSlot], 0, vectorLength);
    System.arraycopy(residual, 0, residualHistory[nextSlot], 0, vectorLength);
    nextSlot = (nextSlot + 1) % maxHistory;
    if (count < maxHistory) {
      count++;
    }
  }

  /**
   * Check if DIIS extrapolation can be performed.
   *
   * @return true if at least 2 entries have been stored
   */
  public boolean canExtrapolate() {
    return count >= 2;
  }

  /**
   * Compute the DIIS-extrapolated iterate.
   *
   * <p>
   * Solves the augmented Pulay system for optimal coefficients c_i that minimize the squared
   * residual norm ||sum(c_i * r_i)||^2 subject to sum(c_i) = 1, then returns x_new = sum(c_i *
   * x_i). Returns null if the Pulay matrix is singular or if fewer than 2 entries are stored.
   * </p>
   *
   * @return extrapolated iterate vector, or null if extrapolation failed
   */
  public double[] extrapolate() {
    if (count < 2) {
      return null;
    }

    int m = count;
    int dim = m + 1;

    // Build augmented Pulay matrix: [B, -1; -1^T, 0] and RHS [0; -1]
    double[][] aug = new double[dim][dim + 1];
    for (int i = 0; i < m; i++) {
      int ii = bufferIndex(i);
      for (int j = 0; j < m; j++) {
        int jj = bufferIndex(j);
        double dot = 0.0;
        for (int k = 0; k < vectorLength; k++) {
          dot += residualHistory[ii][k] * residualHistory[jj][k];
        }
        aug[i][j] = dot;
      }
      aug[i][m] = -1.0;
      aug[m][i] = -1.0;
      aug[i][dim] = 0.0;
    }
    aug[m][m] = 0.0;
    aug[m][dim] = -1.0;

    // Solve by Gaussian elimination with partial pivoting
    for (int c = 0; c < dim; c++) {
      int pr = c;
      double mx = Math.abs(aug[c][c]);
      for (int r = c + 1; r < dim; r++) {
        if (Math.abs(aug[r][c]) > mx) {
          mx = Math.abs(aug[r][c]);
          pr = r;
        }
      }
      if (mx < 1.0e-30) {
        return null;
      }
      if (pr != c) {
        double[] tmp = aug[c];
        aug[c] = aug[pr];
        aug[pr] = tmp;
      }
      for (int r = c + 1; r < dim; r++) {
        double f = aug[r][c] / aug[c][c];
        for (int jj = c; jj <= dim; jj++) {
          aug[r][jj] -= f * aug[c][jj];
        }
      }
    }

    double[] sol = new double[dim];
    for (int i = dim - 1; i >= 0; i--) {
      sol[i] = aug[i][dim];
      for (int j = i + 1; j < dim; j++) {
        sol[i] -= aug[i][j] * sol[j];
      }
      if (Math.abs(aug[i][i]) < 1.0e-30) {
        return null;
      }
      sol[i] /= aug[i][i];
    }

    // Extrapolated iterate = sum_i c_i * x_i
    double[] result = new double[vectorLength];
    for (int i = 0; i < m; i++) {
      int ii = bufferIndex(i);
      double ci = sol[i];
      for (int k = 0; k < vectorLength; k++) {
        result[k] += ci * iterateHistory[ii][k];
      }
    }

    return result;
  }

  /**
   * Reset the DIIS history, discarding all stored entries.
   */
  public void reset() {
    count = 0;
    nextSlot = 0;
  }

  /**
   * Get the number of currently stored entries.
   *
   * @return entry count (0 to maxHistory)
   */
  public int getCount() {
    return count;
  }

  /**
   * Map a logical index (0 = oldest stored) to the actual buffer position.
   *
   * @param logicalIndex zero-based logical index where 0 is the oldest entry
   * @return actual circular buffer index
   */
  private int bufferIndex(int logicalIndex) {
    if (count < maxHistory) {
      return logicalIndex;
    }
    return (nextSlot + logicalIndex) % maxHistory;
  }
}
