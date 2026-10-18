package neqsim.chemicalreactions.chemicalequilibrium;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.ejml.data.DMatrixRMaj;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression coverage for finite chemical-equilibrium steps after the EJML migration.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class ChemicalEquilibriumLinearSolveTest {
  /**
   * Exercises the existing SVD fallback through the full-M-matrix chemical solve.
   */
  @Test
  void singularFullMMatrixUsesFiniteFallback() {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 10.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("water", 0.5);
    fluid.setMixingRule("classic");
    fluid.init(0);
    fluid.setNumberOfPhases(1);
    fluid.init(1);
    ChemicalEquilibrium equilibrium = new ChemicalEquilibrium(new double[][] {{1.0, 1.0}}, new double[] {1.0}, fluid,
        Arrays.copyOf(fluid.getPhase(0).getComponents(), fluid.getNumberOfComponents()), 0);
    equilibrium.setUseFullMMatrix(true);

    equilibrium.chemSolve();

    // The full ideal M matrix has the mole vector in its nullspace. Its LU solve must use the SVD fallback.
    assertArrayEquals(new double[] {1.0, -1.0}, equilibrium.M_matrix[0], 1e-14);
    assertArrayEquals(new double[] {-1.0, 1.0}, equilibrium.M_matrix[1], 1e-14);
    for (double step : equilibrium.d_n) {
      assertTrue(Double.isFinite(step), "The SVD fallback must keep every mole update finite");
    }
  }

  /**
   * Verifies rejection of a singular factorization and of overflow from a finite input system.
   *
   * @throws Exception if reflection fails
   */
  @Test
  void denseSolveRejectsSingularAndOverflowingResults() throws Exception {
    assertThrows(IllegalStateException.class,
        () -> solveDense(new double[][] {{1.0, -1.0}, {-1.0, 1.0}}, new double[][] {{1.0}, {1.0}}));
    assertThrows(IllegalStateException.class,
        () -> solveDense(new double[][] {{Double.MIN_VALUE}}, new double[][] {{2.0}}));
  }

  /**
   * Checks nonsymmetric coefficients, multiple right-hand sides and input ownership.
   *
   * @throws Exception if reflection fails
   */
  @Test
  void denseSolvePreservesMultipleRightHandSidesAndInputs() throws Exception {
    double[][] coefficients = {{3.0, 1.0}, {2.0, 2.0}};
    double[][] rightHandSides = {{9.0, 0.0}, {10.0, 4.0}};

    DMatrixRMaj solution = solveDense(coefficients, rightHandSides);

    assertArrayEquals(new double[] {2.0, -1.0, 3.0, 3.0}, solution.getData(), 1e-14);
    assertArrayEquals(new double[] {3.0, 1.0}, coefficients[0]);
    assertArrayEquals(new double[] {2.0, 2.0}, coefficients[1]);
    assertArrayEquals(new double[] {9.0, 0.0}, rightHandSides[0]);
    assertArrayEquals(new double[] {10.0, 4.0}, rightHandSides[1]);
  }

  /**
   * Calls the private multi-RHS kernel without adding a public solver API.
   *
   * @param coefficients square coefficient matrix
   * @param rightHandSides right-hand side columns
   * @return computed solution
   * @throws Exception if reflection fails
   */
  private static DMatrixRMaj solveDense(double[][] coefficients, double[][] rightHandSides) throws Exception {
    Method method = ChemicalEquilibrium.class.getDeclaredMethod("solveDense", DMatrixRMaj.class, DMatrixRMaj.class);
    method.setAccessible(true);
    try {
      return (DMatrixRMaj) method.invoke(null, new DMatrixRMaj(coefficients), new DMatrixRMaj(rightHandSides));
    } catch (InvocationTargetException failure) {
      if (failure.getCause() instanceof RuntimeException) {
        throw (RuntimeException) failure.getCause();
      }
      throw failure;
    }
  }
}
