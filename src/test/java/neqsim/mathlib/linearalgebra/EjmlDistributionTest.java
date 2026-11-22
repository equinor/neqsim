package neqsim.mathlib.linearalgebra;

import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

/**
 * Guards the EJML-only distribution against accidental restoration of the retired matrix dependency.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class EjmlDistributionTest {
  /** Verifies that neither direct nor transitive test dependencies provide the retired implementation. */
  @Test
  void retiredMatrixImplementationIsAbsent() {
    assertNull(EjmlLinearAlgebra.class.getClassLoader().getResource("Jama/Matrix.class"));
  }
}
