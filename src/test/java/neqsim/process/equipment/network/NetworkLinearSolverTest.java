package neqsim.process.equipment.network;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link NetworkLinearSolver}.
 *
 * <p>
 * Verifies sparse, dense, and Gaussian methods produce consistent results.
 * </p>
 */
class NetworkLinearSolverTest {

  @Test
  void testSolveIdentity() {
    // Identity matrix: Ix = b => x = b
    double[][] a = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
    double[] b = {3.0, 5.0, 7.0};
    double[] x = NetworkLinearSolver.solve(a, b, 3);
    assertEquals(3.0, x[0], 1e-10);
    assertEquals(5.0, x[1], 1e-10);
    assertEquals(7.0, x[2], 1e-10);
  }

  @Test
  void testSolveDiagonal() {
    double[][] a = {{2, 0, 0}, {0, 4, 0}, {0, 0, 5}};
    double[] b = {6.0, 12.0, 25.0};
    double[] x = NetworkLinearSolver.solve(a, b, 3);
    assertEquals(3.0, x[0], 1e-10);
    assertEquals(3.0, x[1], 1e-10);
    assertEquals(5.0, x[2], 1e-10);
  }

  @Test
  void testSolveTridiagonal() {
    // Typical pipe network Schur complement pattern
    double[][] a = {{4, -1, 0, 0}, {-1, 4, -1, 0}, {0, -1, 4, -1}, {0, 0, -1, 4}};
    double[] b = {1, 2, 3, 4};
    double[] x = NetworkLinearSolver.solve(a, b, 4);
    // Verify Ax = b
    for (int i = 0; i < 4; i++) {
      double sum = 0;
      for (int j = 0; j < 4; j++) {
        sum += a[i][j] * x[j];
      }
      assertEquals(b[i], sum, 1e-8, "Row " + i + " of Ax != b");
    }
  }

  @Test
  void testSparseVsDenseConsistency() {
    int n = 20;
    double[][] a = new double[n][n];
    double[] b = new double[n];
    for (int i = 0; i < n; i++) {
      a[i][i] = 4.0 + i * 0.1;
      if (i > 0) {
        a[i][i - 1] = -1.0;
      }
      if (i < n - 1) {
        a[i][i + 1] = -1.0;
      }
      b[i] = 1.0 + 0.1 * i;
    }

    double[] xSparse = NetworkLinearSolver.solveSparse(a, b, n);
    double[] xDense = NetworkLinearSolver.solveDense(a, b, n);
    double[] xGauss = NetworkLinearSolver.solveGaussian(a, b, n);

    for (int i = 0; i < n; i++) {
      assertEquals(xGauss[i], xDense[i], 1e-8, "Dense vs Gauss at index " + i);
      assertEquals(xGauss[i], xSparse[i], 1e-8, "Sparse vs Gauss at index " + i);
    }
  }

  @Test
  void testEstimateSparsity() {
    double[] result = NetworkLinearSolver.estimateSparsity(100, 300);
    double density = result[0];
    assertTrue(density > 0.0 && density <= 1.0, "Density should be in (0,1]");
    // For 100 nodes and 300 pipes, each pipe creates ~4 entries => 1200 / 10000 = 12%
    assertTrue(density < 0.5, "Expected sparse for typical pipe network");
  }

  @Test
  void testSolveAutoSelectsDenseForSmallSystems() {
    // n=5 < threshold of 30, should use dense LU
    double[][] a =
        {{5, 1, 0, 0, 0}, {1, 5, 1, 0, 0}, {0, 1, 5, 1, 0}, {0, 0, 1, 5, 1}, {0, 0, 0, 1, 5}};
    double[] b = {1, 1, 1, 1, 1};
    double[] x = NetworkLinearSolver.solve(a, b, 5);
    // Verify solution
    for (int i = 0; i < 5; i++) {
      double sum = 0;
      for (int j = 0; j < 5; j++) {
        sum += a[i][j] * x[j];
      }
      assertEquals(b[i], sum, 1e-8);
    }
  }
}
