package neqsim.chemicalreactions.chemicalequilibrium;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Arrays;
import org.ejml.data.DMatrixRMaj;
import org.junit.jupiter.api.Test;
import neqsim.mathlib.linearalgebra.LinearAlgebraException;

/**
 * Chemical-equilibrium solver tests, including the H/N/O reference problem and rank-deficient SVD fallback.
 *
 * @author asmf
 * @version 1.0
 */
class ChemEqTest {
  /**
   * Element-species matrix with rows H, N, O for the species H, H2, H2O, N, N2, NH, NO, O, O2, OH.
   *
   * @return an array of type double
   */
  private static double[][] elementMatrix() {
    return new double[][] {{1, 2, 2, 0, 0, 1, 0, 0, 0, 1}, {0, 0, 0, 1, 2, 1, 1, 0, 0, 0},
        {0, 0, 1, 0, 0, 0, 1, 1, 2, 1}};
  }

  /**
   * Reduced Gibbs energies g/RT of the ten species at 3500 K.
   *
   * @return an array of type double
   */
  private static double[] reducedGibbsEnergies() {
    return new double[] {-6.089, -17.164, -34.054, -5.914, -24.721, -14.986, -24.1, -10.708, -26.662, -22.179};
  }

  /**
   * Uniform initial mole numbers.
   *
   * @return an array of type double
   */
  private static double[] initialMoles() {
    double[] moles = new double[10];
    Arrays.fill(moles, 0.1);
    return moles;
  }

  /**
   * Verifies that the element balance is the element matrix times the mole numbers.
   */
  @Test
  void solveComputesElementBalanceFromMoleNumbers() {
    ChemEq equilibrium = new ChemEq(3500.0, 51.0, elementMatrix(), initialMoles(), reducedGibbsEnergies(),
        new double[] {2.0, 1.0, 1.0});

    equilibrium.solve(3500.0, 51.0, initialMoles(), reducedGibbsEnergies());

    assertArrayEquals(new double[] {0.7, 0.5, 0.6}, equilibrium.b_element, 1.0e-15);
  }

  /**
   * Verifies that a singular Newton system is reported rather than producing non-finite mole numbers.
   */
  @Test
  void singularNewtonSystemIsReported() {
    ChemEq equilibrium = new ChemEq(3500.0, 51.0, elementMatrix(), initialMoles(), reducedGibbsEnergies(),
        new double[] {2.0, 1.0, 1.0});

    assertThrows(LinearAlgebraException.class, equilibrium::solve);
  }

  /**
   * Verifies the ChemicalEquilibrium pseudoinverse retains its relative singular-value cutoff and multiple RHS support.
   */
  @Test
  void chemicalEquilibriumSvdFallbackRetainsRelativeCutoffAndMultipleRightHandSides() {
    DMatrixRMaj coefficients = new DMatrixRMaj(new double[][] {{1.0, 0.0}, {0.0, 1e-14}});
    DMatrixRMaj rightHandSides = new DMatrixRMaj(new double[][] {{2.0, 4.0}, {3.0, 5.0}});

    DMatrixRMaj solution = ChemicalEquilibrium.solveLeastSquares(coefficients, rightHandSides);

    assertArrayEquals(new double[] {2.0, 4.0, 0.0, 0.0}, solution.getData(), 1e-12);
  }
}
