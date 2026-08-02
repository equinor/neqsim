package neqsim.mathlib.generalmath;

/**
 * Solves a compact banded linear system without expanding it to a dense matrix.
 *
 * <p>
 * Row {@code i}, column {@code j} is stored at {@code bands[i][j - i + lowerBandwidth]}. Entries outside the declared
 * lower and upper bandwidths are zero. The implementation uses banded Gaussian elimination without pivoting and fails
 * explicitly when a usable diagonal pivot is absent.
 * </p>
 */
public final class BandedLinearSystemSolver {
  private static final double RELATIVE_PIVOT_TOLERANCE = 1.0e-14;

  private BandedLinearSystemSolver() {
  }

  /**
   * Solve {@code A x = rightHandSide} for a square banded matrix.
   *
   * @param bands row-wise compact band storage
   * @param lowerBandwidth number of stored subdiagonals
   * @param upperBandwidth number of stored superdiagonals
   * @param rightHandSide right-hand-side vector
   * @return solution vector
   * @throws IllegalArgumentException for inconsistent dimensions, bandwidths, or non-finite data
   * @throws IllegalStateException when elimination encounters a singular or unusably small pivot
   */
  public static double[] solve(double[][] bands, int lowerBandwidth, int upperBandwidth, double[] rightHandSide) {
    validate(bands, lowerBandwidth, upperBandwidth, rightHandSide);
    int size = rightHandSide.length;
    int width = lowerBandwidth + upperBandwidth + 1;
    double[][] work = new double[size][width];
    double[] rhs = rightHandSide.clone();
    for (int row = 0; row < size; row++) {
      System.arraycopy(bands[row], 0, work[row], 0, width);
    }

    for (int pivot = 0; pivot < size; pivot++) {
      double pivotValue = get(work, pivot, pivot, lowerBandwidth, upperBandwidth);
      double rowScale = rowMaximum(work[pivot]);
      if (!Double.isFinite(pivotValue) || Math.abs(pivotValue) <= RELATIVE_PIVOT_TOLERANCE * Math.max(rowScale, 1.0)) {
        throw new IllegalStateException("Banded solve encountered an unusable pivot at row " + pivot + ": pivot="
            + pivotValue + ", row scale=" + rowScale + ".");
      }
      int lastRow = Math.min(size - 1, pivot + lowerBandwidth);
      int lastColumn = Math.min(size - 1, pivot + upperBandwidth);
      for (int row = pivot + 1; row <= lastRow; row++) {
        double below = get(work, row, pivot, lowerBandwidth, upperBandwidth);
        if (below == 0.0) {
          continue;
        }
        double factor = below / pivotValue;
        set(work, row, pivot, lowerBandwidth, upperBandwidth, 0.0);
        for (int column = pivot + 1; column <= lastColumn; column++) {
          double updated = get(work, row, column, lowerBandwidth, upperBandwidth)
              - factor * get(work, pivot, column, lowerBandwidth, upperBandwidth);
          set(work, row, column, lowerBandwidth, upperBandwidth, updated);
        }
        rhs[row] -= factor * rhs[pivot];
      }
    }

    double[] solution = new double[size];
    for (int row = size - 1; row >= 0; row--) {
      double value = rhs[row];
      int lastColumn = Math.min(size - 1, row + upperBandwidth);
      for (int column = row + 1; column <= lastColumn; column++) {
        value -= get(work, row, column, lowerBandwidth, upperBandwidth) * solution[column];
      }
      solution[row] = value / get(work, row, row, lowerBandwidth, upperBandwidth);
      if (!Double.isFinite(solution[row])) {
        throw new IllegalStateException("Banded solve produced a non-finite solution at row " + row + ".");
      }
    }
    return solution;
  }

  private static void validate(double[][] bands, int lowerBandwidth, int upperBandwidth, double[] rightHandSide) {
    if (bands == null || rightHandSide == null) {
      throw new IllegalArgumentException("Banded matrix and right-hand side must not be null.");
    }
    if (lowerBandwidth < 0 || upperBandwidth < 0) {
      throw new IllegalArgumentException("Banded matrix bandwidths must be non-negative.");
    }
    if (bands.length != rightHandSide.length) {
      throw new IllegalArgumentException("Banded matrix row count " + bands.length
          + " does not match right-hand-side length " + rightHandSide.length + ".");
    }
    int width = lowerBandwidth + upperBandwidth + 1;
    for (int row = 0; row < bands.length; row++) {
      if (bands[row] == null || bands[row].length != width) {
        throw new IllegalArgumentException("Banded matrix row " + row + " must contain exactly " + width + " entries.");
      }
      for (double value : bands[row]) {
        if (!Double.isFinite(value)) {
          throw new IllegalArgumentException("Banded matrix contains a non-finite value in row " + row + ".");
        }
      }
      if (!Double.isFinite(rightHandSide[row])) {
        throw new IllegalArgumentException("Right-hand side contains a non-finite value in row " + row + ".");
      }
    }
  }

  private static double rowMaximum(double[] row) {
    double maximum = 0.0;
    for (double value : row) {
      maximum = Math.max(maximum, Math.abs(value));
    }
    return maximum;
  }

  private static double get(double[][] bands, int row, int column, int lowerBandwidth, int upperBandwidth) {
    int offset = column - row + lowerBandwidth;
    if (row < 0 || row >= bands.length || column < 0 || column >= bands.length || offset < 0
        || offset > lowerBandwidth + upperBandwidth) {
      return 0.0;
    }
    return bands[row][offset];
  }

  private static void set(double[][] bands, int row, int column, int lowerBandwidth, int upperBandwidth, double value) {
    int offset = column - row + lowerBandwidth;
    if (offset < 0 || offset > lowerBandwidth + upperBandwidth) {
      if (value != 0.0) {
        throw new IllegalStateException("Banded elimination attempted fill outside the declared bandwidth.");
      }
      return;
    }
    bands[row][offset] = value;
  }
}
