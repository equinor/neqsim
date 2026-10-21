package neqsim.mathlib.linearalgebra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Behaviour specific to the EJML backend. The shared behaviour is verified by
 * {@link LinearAlgebraOperationsContractTest}.
 */
class EjmlLinearAlgebraTest {
  private final LinearAlgebraOperations algebra = new EjmlLinearAlgebra();

  @Test
  void reportsBackendName() {
    assertEquals("EJML", algebra.getName());
  }

  /**
   * Verify EJML retains conjugate-pair information and returns a real block basis.
   */
  @Test
  void complexSpectrumReturnsRealBlockBasis() {
    double[][] matrix = {{2.0, -2.0}, {0.5, 2.0}};
    RealBlockEigenDecomposition result = ((EjmlLinearAlgebra) algebra).realBlockEigenDecomposition(matrix);

    assertTrue(result.hasComplexEigenvalues());
    assertArrayEquals(new double[] {2.0, 2.0}, result.getRealParts(), 1.0e-12);
    assertArrayEquals(new double[] {1.0, -1.0}, result.getImaginaryParts(), 1.0e-12);
    double[][] vectors = result.getVectors();
    double[][] left = algebra.multiply(matrix, vectors);
    double[][] right = algebra.multiply(vectors, new double[][] {{2.0, 1.0}, {-1.0, 2.0}});
    for (int row = 0; row < matrix.length; row++) {
      assertArrayEquals(left[row], right[row], 1.0e-12);
    }
  }
}
