package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.ejml.data.DMatrixRMaj;
import org.junit.jupiter.api.Test;

/**
 * Linear-solve failure regressions for the retained public legacy TP-flash solver.
 *
 * @author Even Solbraa
 * @version 1.0
 */
@SuppressWarnings("deprecation")
class LegacyTpFlashLinearSolveTest {
  /**
   * A singular Jacobian must fail before the solver overwrites its state with infinities.
   */
  @Test
  void singularJacobianStopsBeforeStateUpdate() {
    FixedJacobianSolver solver = new FixedJacobianSolver(new double[][] {{1.0, -1.0}, {-1.0, 1.0}},
        new double[] {1.0, 1.0});
    double[] initial = solver.u.getData().clone();

    assertThrows(IllegalStateException.class, () -> solver.solve(0));

    assertArrayEquals(initial, solver.u.getData());
  }

  /**
   * Overflow from finite inputs must not become a successful Newton update.
   */
  @Test
  void overflowingJacobianStopsBeforeStateUpdate() {
    FixedJacobianSolver solver = new FixedJacobianSolver(new double[][] {{Double.MIN_VALUE}}, new double[] {2.0});
    double[] initial = solver.u.getData().clone();

    assertThrows(IllegalStateException.class, () -> solver.solve(0));

    assertArrayEquals(initial, solver.u.getData());
  }

  /**
   * The migrated kernel retains nonsymmetric coefficient orientation and leaves its inputs intact.
   */
  @Test
  void nonsingularStepPreservesOrientationAndInputs() {
    FixedJacobianSolver solver = new FixedJacobianSolver(new double[][] {{3.0, 1.0}, {2.0, 2.0}},
        new double[] {9.0, 10.0});

    DMatrixRMaj step = solver.solveJacobianStep();

    assertArrayEquals(new double[] {2.0, 3.0}, step.getData(), 1e-14);
    assertArrayEquals(new double[] {3.0, 1.0, 2.0, 2.0}, solver.Jac.getData());
    assertArrayEquals(new double[] {9.0, 10.0}, solver.fvec.getData());
  }

  /**
   * Supplies a fixed Newton system to exercise the public solve boundary without changing thermodynamic state.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  private static final class FixedJacobianSolver extends SysNewtonRhapsonTPflashNew {
    private static final long serialVersionUID = 1000;

    /**
     * Initializes fixed coefficient and residual data.
     *
     * @param coefficients square Jacobian
     * @param residual residual column values
     */
    private FixedJacobianSolver(double[][] coefficients, double[] residual) {
      super(null, 2, residual.length - 1);
      neq = residual.length;
      Jac = new DMatrixRMaj(coefficients);
      fvec = new DMatrixRMaj(neq, 1, true, residual);
      u = new DMatrixRMaj(neq, 1);
    }

    /** {@inheritDoc} */
    @Override
    public void init() {
      // The fixture holds a fixed linear system and has no thermodynamic model to initialize.
    }

    /** {@inheritDoc} */
    @Override
    public void setfvec() {
      // Retain the supplied residual.
    }

    /** {@inheritDoc} */
    @Override
    public void setJac() {
      // Retain the supplied Jacobian.
    }

    /** {@inheritDoc} */
    @Override
    public void setu() {
      // The fixture initializes its state directly after the superclass constructor.
    }
  }
}
