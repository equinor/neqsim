package neqsim.mathlib.linearalgebra;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
