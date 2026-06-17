package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.ejml.simple.SimpleMatrix;
import org.junit.jupiter.api.Test;

/**
 * Test Gaussian elimination solver against EJML for matrix inversion.
 */
public class GaussianEliminationTest {

  /**
   * Solve Ax=b in-place via GE with partial pivoting. Same as PhaseSrkCPAfullyImplicit.
   */
  private static boolean solveLinearSystem(double[][] a, double[] b, int n) {
    for (int col = 0; col < n; col++) {
      int maxRow = col;
      double maxVal = Math.abs(a[col][col]);
      for (int row = col + 1; row < n; row++) {
        double val = Math.abs(a[row][col]);
        if (val > maxVal) {
          maxVal = val;
          maxRow = row;
        }
      }
      if (maxVal < 1.0e-30) {
        return false;
      }
      if (maxRow != col) {
        double[] tempRow = a[col];
        a[col] = a[maxRow];
        a[maxRow] = tempRow;
        double tempB = b[col];
        b[col] = b[maxRow];
        b[maxRow] = tempB;
      }
      double pivot = a[col][col];
      for (int row = col + 1; row < n; row++) {
        double factor = a[row][col] / pivot;
        for (int k = col + 1; k < n; k++) {
          a[row][k] -= factor * a[col][k];
        }
        b[row] -= factor * b[col];
      }
    }
    for (int row = n - 1; row >= 0; row--) {
      double sum = b[row];
      for (int k = row + 1; k < n; k++) {
        sum -= a[row][k] * b[k];
      }
      b[row] = sum / a[row][row];
    }
    return true;
  }

  /**
   * Compute full inverse of nxn matrix using column-by-column GE.
   */
  private static double[][] invertGE(double[][] mat, int n) {
    double[][] inv = new double[n][n];
    for (int col = 0; col < n; col++) {
      double[][] copy = new double[n][n];
      double[] rhs = new double[n];
      for (int i = 0; i < n; i++) {
        System.arraycopy(mat[i], 0, copy[i], 0, n);
        rhs[i] = (i == col) ? 1.0 : 0.0;
      }
      solveLinearSystem(copy, rhs, n);
      for (int i = 0; i < n; i++) {
        inv[i][col] = rhs[i];
      }
    }
    return inv;
  }

  @Test
  public void testSimple2x2() {
    // A = [[4, 7], [2, 6]], inv = [[0.6, -0.7], [-0.2, 0.4]]
    double[][] a = {{4, 7}, {2, 6}};
    double[][] invGE = invertGE(a, 2);

    SimpleMatrix sm = new SimpleMatrix(new double[][] {{4, 7}, {2, 6}});
    SimpleMatrix invEJML = sm.invert();

    for (int i = 0; i < 2; i++) {
      for (int j = 0; j < 2; j++) {
        double diff = Math.abs(invGE[i][j] - invEJML.get(i, j));
        System.out.printf("inv[%d][%d]: GE=%.15e  EJML=%.15e  diff=%.4e%n", i, j, invGE[i][j],
            invEJML.get(i, j), diff);
        assertTrue(diff < 1e-12, "2x2 inv mismatch at [" + i + "][" + j + "]");
      }
    }
  }

  @Test
  public void testCPALikeHessian4x4() {
    // Simulate CPA Hessian: H[i][j] = -m/X^2 * kron(i,j) - Klk[i][j]
    // For water with 4 sites (2ed + 2ea), m = 5.55 mol, X ~ 0.3
    double m = 5.55;
    double x = 0.3;
    double klk_cross = 0.5; // non-zero for ed-ea cross associations
    double klk_same = 0.0;

    // delta pattern: sites 0,1 = ed; sites 2,3 = ea
    double[][] klk = new double[4][4];
    klk[0][2] = klk_cross;
    klk[2][0] = klk_cross;
    klk[0][3] = klk_cross;
    klk[3][0] = klk_cross;
    klk[1][2] = klk_cross;
    klk[2][1] = klk_cross;
    klk[1][3] = klk_cross;
    klk[3][1] = klk_cross;

    double[][] hess = new double[4][4];
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 4; j++) {
        double kron = (i == j) ? -m / (x * x) : 0.0;
        hess[i][j] = kron - klk[i][j];
      }
    }

    System.out.println("Hessian matrix:");
    for (int i = 0; i < 4; i++) {
      System.out.printf("  [%.6f, %.6f, %.6f, %.6f]%n", hess[i][0], hess[i][1], hess[i][2],
          hess[i][3]);
    }

    // Compute inverse with both methods
    double[][] hessCopy = new double[4][4];
    for (int i = 0; i < 4; i++) {
      System.arraycopy(hess[i], 0, hessCopy[i], 0, 4);
    }
    double[][] invGE = invertGE(hessCopy, 4);

    SimpleMatrix sm = new SimpleMatrix(hess);
    SimpleMatrix invEJML = sm.invert();

    System.out.println("\nInverse comparison:");
    double maxDiff = 0;
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 4; j++) {
        double diff = Math.abs(invGE[i][j] - invEJML.get(i, j));
        maxDiff = Math.max(maxDiff, diff);
        System.out.printf("inv[%d][%d]: GE=%.15e  EJML=%.15e  diff=%.4e%n", i, j, invGE[i][j],
            invEJML.get(i, j), diff);
      }
    }
    System.out.printf("Max difference: %.4e%n", maxDiff);
    assertTrue(maxDiff < 1e-12, "4x4 CPA Hessian inverse mismatch, max diff = " + maxDiff);
  }

  @Test
  public void testSingleRHSSolve() {
    // Test solving H * xv = rhs (the actual use case in initCPAMatrix)
    double m = 5.55;
    double x = 0.3;
    double klk_cross = 0.5;

    double[][] hess = new double[4][4];
    double[][] klk = new double[4][4];
    klk[0][2] = klk_cross;
    klk[2][0] = klk_cross;
    klk[0][3] = klk_cross;
    klk[3][0] = klk_cross;
    klk[1][2] = klk_cross;
    klk[2][1] = klk_cross;
    klk[1][3] = klk_cross;
    klk[3][1] = klk_cross;

    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 4; j++) {
        hess[i][j] = ((i == j) ? -m / (x * x) : 0.0) - klk[i][j];
      }
    }

    // RHS = KlkV * ksi (some test values)
    double[] rhs = {0.1, 0.2, -0.15, -0.25};

    // GE solve
    double[][] hessCopy = new double[4][4];
    double[] rhsCopy = new double[4];
    for (int i = 0; i < 4; i++) {
      System.arraycopy(hess[i], 0, hessCopy[i], 0, 4);
      rhsCopy[i] = rhs[i];
    }
    solveLinearSystem(hessCopy, rhsCopy, 4);

    // EJML solve via inverse
    SimpleMatrix sm = new SimpleMatrix(hess);
    SimpleMatrix rhsSM = new SimpleMatrix(new double[][] {{0.1}, {0.2}, {-0.15}, {-0.25}});
    SimpleMatrix xvEJML = sm.invert().mult(rhsSM);

    System.out.println("Single RHS solve comparison:");
    for (int i = 0; i < 4; i++) {
      double diff = Math.abs(rhsCopy[i] - xvEJML.get(i, 0));
      System.out.printf("  xv[%d]: GE=%.15e  EJML=%.15e  diff=%.4e%n", i, rhsCopy[i],
          xvEJML.get(i, 0), diff);
      assertTrue(diff < 1e-12, "Single RHS solve mismatch at [" + i + "]");
    }
  }
}
