package neqsim.mathlib.linearalgebra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * Behaviour specific to the JAMA backend. The shared behaviour is verified by
 * {@link LinearAlgebraOperationsContractTest}.
 */
class JamaLinearAlgebraTest {
  private final LinearAlgebraOperations algebra = new JamaLinearAlgebra();

  @Test
  void reportsBackendName() {
    assertEquals("JAMA", algebra.getName());
  }

  @Test
  void decomposesWideMatricesByTransposingThem() {
    // JAMA's own SVD is unreliable for rows < columns, so the backend transposes first; results must still agree.
    double[][] wide = {{1.0, 2.0, 3.0}, {4.0, 5.0, 6.0}};

    assertEquals(algebra.spectralNorm(algebra.transpose(wide)), algebra.spectralNorm(wide), 1.0e-10);
    assertEquals(algebra.rank(algebra.transpose(wide)), algebra.rank(wide));
  }
}
