package neqsim.mathlib.generalmath;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

/** Tests compact banded Gaussian elimination. */
class BandedLinearSystemSolverTest {
  @Test
  void solvesDiagonallyDominantPentadiagonalSystem() {
    double[][] dense = { { 8.0, -2.0, 0.5, 0.0, 0.0, 0.0 }, { -1.0, 9.0, -2.0, 0.25, 0.0, 0.0 },
        { 0.5, -1.0, 10.0, -2.0, 0.5, 0.0 }, { 0.0, 0.25, -1.0, 10.0, -2.0, 0.5 }, { 0.0, 0.0, 0.5, -1.0, 9.0, -2.0 },
        { 0.0, 0.0, 0.0, 0.5, -1.0, 8.0 } };
    double[] expected = { 1.0, -2.0, 3.0, -4.0, 5.0, -6.0 };
    double[] rightHandSide = multiply(dense, expected);

    double[] actual = BandedLinearSystemSolver.solve(compact(dense, 2), 2, 2, rightHandSide);

    assertArrayEquals(expected, actual, 1.0e-12);
  }

  @Test
  void rejectsUnusablePivotWithRowDiagnostic() {
    double[][] bands = { { 0.0, 0.0, 1.0 }, { 0.0, 2.0, 0.0 } };
    assertThrows(IllegalStateException.class,
        () -> BandedLinearSystemSolver.solve(bands, 1, 1, new double[] { 1.0, 2.0 }));
  }

  @Test
  void rejectsNonFiniteMatrixEntry() {
    double[][] bands = { { 0.0, 2.0, Double.NaN }, { 0.0, 2.0, 0.0 } };
    assertThrows(IllegalArgumentException.class,
        () -> BandedLinearSystemSolver.solve(bands, 1, 1, new double[] { 1.0, 2.0 }));
  }

  @Test
  void solvesLargeCompactSystemWithoutDenseStorage() {
    int size = 10000;
    double[][] bands = new double[size][5];
    double[] expected = new double[size];
    for (int row = 0; row < size; row++) {
      bands[row][2] = 8.0;
      if (row > 0) {
        bands[row][1] = -1.0;
      }
      if (row + 1 < size) {
        bands[row][3] = -1.0;
      }
      if (row > 1) {
        bands[row][0] = 0.25;
      }
      if (row + 2 < size) {
        bands[row][4] = 0.25;
      }
      expected[row] = (row % 7 - 3.0) / 3.0;
    }
    double[] rightHandSide = multiplyCompact(bands, expected, 2);

    double[] actual = BandedLinearSystemSolver.solve(bands, 2, 2, rightHandSide);

    assertArrayEquals(expected, actual, 1.0e-12);
  }

  private static double[][] compact(double[][] dense, int halfBandwidth) {
    double[][] bands = new double[dense.length][2 * halfBandwidth + 1];
    for (int row = 0; row < dense.length; row++) {
      int firstColumn = Math.max(0, row - halfBandwidth);
      int lastColumn = Math.min(dense.length - 1, row + halfBandwidth);
      for (int column = firstColumn; column <= lastColumn; column++) {
        bands[row][column - row + halfBandwidth] = dense[row][column];
      }
    }
    return bands;
  }

  private static double[] multiply(double[][] matrix, double[] vector) {
    double[] result = new double[vector.length];
    for (int row = 0; row < matrix.length; row++) {
      for (int column = 0; column < vector.length; column++) {
        result[row] += matrix[row][column] * vector[column];
      }
    }
    return result;
  }

  private static double[] multiplyCompact(double[][] bands, double[] vector, int halfBandwidth) {
    double[] result = new double[vector.length];
    for (int row = 0; row < bands.length; row++) {
      int firstColumn = Math.max(0, row - halfBandwidth);
      int lastColumn = Math.min(vector.length - 1, row + halfBandwidth);
      for (int column = firstColumn; column <= lastColumn; column++) {
        result[row] += bands[row][column - row + halfBandwidth] * vector[column];
      }
    }
    return result;
  }
}
