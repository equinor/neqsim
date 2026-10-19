package neqsim.mathlib.linearalgebra;

import org.ejml.simple.SimpleMatrix;

/**
 * Defensive conversions at EJML matrix and primitive-array API boundaries, including EJML 0.41 on Java 8.
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class EjmlMatrixUtils {
  /** Prevents construction of this utility class. */
  private EjmlMatrixUtils() {
  }

  /**
   * Copies an EJML matrix into independent row-major arrays.
   *
   * @param matrix source matrix
   * @return independent values indexed by row and column
   */
  public static double[][] toArray(SimpleMatrix matrix) {
    double[][] values = new double[matrix.numRows()][matrix.numCols()];
    for (int row = 0; row < values.length; row++) {
      for (int column = 0; column < values[row].length; column++) {
        values[row][column] = matrix.get(row, column);
      }
    }
    return values;
  }
}
