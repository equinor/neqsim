package neqsim.mathlib.nonlinearsolver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.ejml.dense.row.CommonOps_DDRM;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression coverage for singular continuation solves after the EJML migration.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class SysNewtonRhapsonLinearSolveTest {
  /**
   * A failed Jacobian solve must not change the current continuation point.
   */
  @Test
  void singularJacobianStopsBeforeContinuationUpdate() {
    SysNewtonRhapson solver = solver();
    solver.Jac.zero();
    double[] initial = solver.u.getData().clone();

    assertThrows(IllegalStateException.class, () -> solver.calcInc(1));

    assertArrayEquals(initial, solver.u.getData());
  }

  /**
   * Repeated predictor coordinates produce a singular Vandermonde system and must not corrupt the state.
   */
  @Test
  void singularPredictorStopsBeforeContinuationUpdate() {
    SysNewtonRhapson solver = solver();
    CommonOps_DDRM.fill(solver.s, 1.0);
    CommonOps_DDRM.fill(solver.Xgij, 2.0);
    double[] initial = solver.u.getData().clone();

    assertThrows(IllegalStateException.class, () -> solver.calcInc2(5));

    assertArrayEquals(initial, solver.u.getData());
  }

  /**
   * A finite but overflowing solve must fail before updating the state as well.
   */
  @Test
  void overflowingJacobianStopsBeforeContinuationUpdate() {
    SysNewtonRhapson solver = solver();
    CommonOps_DDRM.setIdentity(solver.Jac);
    solver.Jac.set(solver.neq - 1, solver.neq - 1, Double.MIN_VALUE);
    double[] initial = solver.u.getData().clone();

    assertThrows(IllegalStateException.class, () -> solver.calcInc(1));

    assertArrayEquals(initial, solver.u.getData());
  }

  /**
   * Retains the established continuation increment when the Jacobian is well conditioned.
   */
  @Test
  void nonsingularJacobianRetainsContinuationIncrement() {
    SysNewtonRhapson solver = solver();
    CommonOps_DDRM.setIdentity(solver.Jac);
    double[] initial = solver.u.getData().clone();

    solver.calcInc(1);

    for (int index = 0; index < initial.length - 1; index++) {
      assertEquals(initial[index], solver.u.get(index, 0), 1e-14);
    }
    assertEquals(initial[initial.length - 1] + 0.01, solver.u.get(initial.length - 1, 0), 1e-14);
  }

  /**
   * Builds a small initialized continuation solver for linear-kernel checks.
   *
   * @return continuation solver
   */
  private static SysNewtonRhapson solver() {
    SystemSrkEos fluid = new SystemSrkEos(300.0, 10.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.2);
    fluid.setMixingRule("classic");
    fluid.init(0);
    return new SysNewtonRhapson(fluid, 2, fluid.getNumberOfComponents());
  }
}
