package neqsim.mathlib.linearalgebra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests the Commons Math real block extension without weakening the strict real-spectrum contract.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ApacheCommonsMathLinearAlgebraTest {
  /**
   * Verify conjugate-pair information and the real block residual for a nonsymmetric matrix.
   */
  @Test
  void complexSpectrumRetainsPairedPartsAndBlockBasis() {
    ApacheCommonsMathLinearAlgebra algebra = new ApacheCommonsMathLinearAlgebra();
    double[][] matrix = {{2.0, -2.0}, {0.5, 2.0}};
    RealBlockEigenDecomposition result = algebra.realBlockEigenDecomposition(matrix);
    assertTrue(result.hasComplexEigenvalues());
    double[] real = result.getRealParts();
    double[] imaginary = result.getImaginaryParts();
    assertArrayEquals(new double[] {2.0, 2.0}, real, 1.0e-12);
    assertArrayEquals(new double[] {1.0, -1.0}, imaginary, 1.0e-12);
    double[][] block = {{real[0], imaginary[0]}, {imaginary[1], real[1]}};
    double[][] vectors = result.getVectors();
    double[][] left = algebra.multiply(matrix, vectors);
    double[][] right = algebra.multiply(vectors, block);
    for (int row = 0; row < matrix.length; row++) {
      assertArrayEquals(left[row], right[row], 1.0e-12);
    }
    assertArrayEquals(new double[] {2.0, -2.0}, matrix[0], 0.0);
    assertArrayEquals(new double[] {0.5, 2.0}, matrix[1], 0.0);
    assertThrows(LinearAlgebraException.class, () -> algebra.eigenDecomposition(matrix));
    real[0] = Double.NaN;
    imaginary[0] = Double.NaN;
    vectors[0][0] = Double.NaN;
    assertEquals(2.0, result.getRealParts()[0], 1.0e-12);
    assertEquals(1.0, result.getImaginaryParts()[0], 1.0e-12);
    assertTrue(Double.isFinite(result.getVectors()[0][0]));
  }

  /**
   * Verify real-only input still has the same ordered eigenpairs as the strict operation.
   */
  @Test
  void realSpectrumMatchesStrictDecomposition() {
    ApacheCommonsMathLinearAlgebra algebra = new ApacheCommonsMathLinearAlgebra();
    double[][] matrix = {{2.0, 1.0}, {1.0, 2.0}};
    RealBlockEigenDecomposition block = algebra.realBlockEigenDecomposition(matrix);
    RealEigenDecomposition strict = algebra.eigenDecomposition(matrix);
    assertFalse(block.hasComplexEigenvalues());
    assertArrayEquals(strict.getEigenvalues(), block.getRealParts(), 0.0);
    assertArrayEquals(new double[] {0.0, 0.0}, block.getImaginaryParts(), 0.0);
    for (int row = 0; row < matrix.length; row++) {
      assertArrayEquals(strict.getEigenvectors()[row], block.getVectors()[row], 0.0);
    }
  }

  /**
   * Verify constructor ownership and rejection of invalid conjugate pairs or input matrices.
   */
  @Test
  void blockResultValidatesAndCopiesItsInputs() {
    double[] real = {2.0, 2.0};
    double[] imaginary = {1.0, -1.0};
    double[][] vectors = {{1.0, 0.0}, {0.0, 1.0}};
    RealBlockEigenDecomposition result = new RealBlockEigenDecomposition(real, imaginary, vectors);
    real[0] = Double.NaN;
    imaginary[0] = Double.NaN;
    vectors[0][0] = Double.NaN;
    assertEquals(2.0, result.getRealParts()[0], 0.0);
    assertEquals(1.0, result.getImaginaryParts()[0], 0.0);
    assertEquals(1.0, result.getVectors()[0][0], 0.0);
    ApacheCommonsMathLinearAlgebra algebra = new ApacheCommonsMathLinearAlgebra();
    assertThrows(LinearAlgebraException.class, () -> algebra.realBlockEigenDecomposition(null));
    assertThrows(LinearAlgebraException.class, () -> algebra.realBlockEigenDecomposition(new double[][] {{1.0, 2.0}}));
    assertThrows(LinearAlgebraException.class,
        () -> algebra.realBlockEigenDecomposition(new double[][] {{Double.NaN}}));
    assertThrows(LinearAlgebraException.class,
        () -> new RealBlockEigenDecomposition(new double[] {1.0}, new double[] {1.0}, new double[][] {{1.0}}));
    assertThrows(LinearAlgebraException.class,
        () -> new RealBlockEigenDecomposition(new double[] {1.0}, new double[] {0.0, 0.0}, new double[][] {{1.0}}));
  }
}
