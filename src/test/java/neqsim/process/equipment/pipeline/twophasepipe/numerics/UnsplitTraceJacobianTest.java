package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Analytic derivatives distinguish a local trace-phase Jacobian from a finite jump to a different phase inertia. */
class UnsplitTraceJacobianTest {
  private static final double TIME_STEP = 0.125;

  @Test
  void quadraticDragDerivativesRemainLocalAcrossSmallPositivePhaseMasses() {
    for (UnsplitTransientSolver.TimeIntegrationMethod method : UnsplitTransientSolver.TimeIntegrationMethod.values()) {
      double theta = method == UnsplitTransientSolver.TimeIntegrationMethod.BACKWARD_EULER ? 1.0 : 0.5;
      for (double mass : new double[] { 1.0e-4, 1.0e-8, 1.0e-14, 1.0e-100 }) {
        double[][] state = { { mass, 800.0, 0.0, 3.0 * mass, 0.0, 0.0, 7.0 } };
        double[] pressure = { 1.0e5 };
        double area = 1.0 + mass / 10.0;
        UnsplitTransientSolver solver = new UnsplitTransientSolver();
        solver.setTimeIntegrationMethod(method);
        double[][] jacobian = solver.scaledJacobian(state, pressure, state, pressure, new double[] { area }, TIME_STEP,
            0.0, Double.NaN, false, quadraticDragModel(1));

        assertEquals(-TIME_STEP * theta * 9.0, jacobian[3][0], 3.0e-6, "gas drag derivative by phase mass");
        assertEquals(TIME_STEP * theta * 9.0, jacobian[4][0], 3.0e-6, "equal opposite receiving drag derivative");
        assertEquals(1.0 + TIME_STEP * theta * 6.0, jacobian[3][3], 3.0e-6, "gas momentum derivative");
        assertEquals(-TIME_STEP * theta * 6.0, jacobian[4][3], 3.0e-6, "receiving momentum derivative");
        assertEquals(1.0, jacobian[0][0], 1.0e-14);
        assertEquals(1.0 / (10.0 * area), jacobian[6][0], 1.0e-14,
            "The trace volume derivative must survive addition to a much larger liquid volume");
        assertArrayEquals(new double[] { mass, 800.0, 0.0, 3.0 * mass, 0.0, 0.0, 7.0 }, state[0], 0.0);
      }
    }
  }

  @Test
  void coloredAndFullColumnsAgreeForDifferentPhaseScalesInEveryCell() {
    int count = 9;
    double[][] state = new double[count][7];
    double[] pressure = new double[count];
    double[] area = new double[count];
    for (int cell = 0; cell < count; cell++) {
      double mass = Math.pow(10.0, -2.0 * cell - 2.0);
      area[cell] = 0.5 + 0.13 * cell;
      state[cell] = new double[] { mass, 800.0 * (area[cell] - mass / 10.0), 0.0, 3.0 * mass, 0.0, 0.0, 7.0 };
      pressure[cell] = 1.0e5;
    }
    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    solver.setTimeIntegrationMethod(UnsplitTransientSolver.TimeIntegrationMethod.BACKWARD_EULER);
    double[][] colored = solver.scaledJacobian(state, pressure, state, pressure, area, TIME_STEP, 0.0, Double.NaN,
        false, quadraticDragModel(count));
    solver.setCellStencilHalfWidth(count);
    double[][] full = solver.scaledJacobian(state, pressure, state, pressure, area, TIME_STEP, 0.0, Double.NaN, false,
        quadraticDragModel(count));
    for (int row = 0; row < full.length; row++) {
      assertArrayEquals(full[row], colored[row], 0.0);
    }
  }

  @Test
  void volumeDifferenceRetainsDensityDependenceInsteadOfAssumingAnIncompressiblePhase() {
    double mass = 0.1;
    double[][] state = { { mass, 800.0, 0.0, 0.0, 0.0, 0.0, 7.0 } };
    double[] pressure = { 1.0e5 };
    double density = 10.0 * (1.0 + 5.0 * mass);
    double area = 1.0 + mass / density;
    UnsplitTransientSolver.Model model = (evaluationState, evaluationPressure, endpointState, endpointPressure, time,
        outletPressure, outletPressureFixed) -> new UnsplitTransientSolver.Evaluation(new double[1][6], new double[][] {
            { 10.0 * (1.0 + 5.0 * endpointState[0][0]) * endpointPressure[0] / 1.0e5 }, { 800.0 }, { 1000.0 } });
    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    double[][] jacobian = solver.scaledJacobian(state, pressure, state, pressure, new double[] { area }, TIME_STEP, 0.0,
        Double.NaN, false, model);
    assertEquals(1.0 / (10.0 * Math.pow(1.0 + 5.0 * mass, 2.0) * area), jacobian[6][0], 1.0e-7);
    assertEquals(-mass / (density * area), jacobian[6][6], 1.0e-8);
  }

  @Test
  void volumeDifferenceIncludesAnotherPhaseInANeighboringCell() {
    double[][] state = { { 0.1, 800.0, 0.02, 0.0, 0.0, 0.0, 7.0 }, { 0.2, 800.0, 0.03, 0.0, 0.0, 0.0, 7.0 } };
    double[] pressure = { 1.0e5, 1.0e5 };
    double[] area = { 1.0 + 0.1 / 10.6 + 0.02 / 1000.0, 1.0 + 0.2 / 10.4 + 0.03 / 1000.0 };
    UnsplitTransientSolver.Model model = (evaluationState, evaluationPressure, endpointState, endpointPressure, time,
        outletPressure, outletPressureFixed) -> new UnsplitTransientSolver.Evaluation(new double[2][6],
            new double[][] { { 10.0 * (1.0 + 2.0 * endpointState[1][2]), 10.0 * (1.0 + 2.0 * endpointState[0][2]) },
                { 800.0, 800.0 }, { 1000.0, 1000.0 } });
    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    double[][] jacobian = solver.scaledJacobian(state, pressure, state, pressure, area, TIME_STEP, 0.0, Double.NaN,
        false, model);
    assertEquals(-0.1 * 20.0 / (10.6 * 10.6 * area[0]), jacobian[6][9], 1.0e-8);
    assertEquals(-0.2 * 20.0 / (10.4 * 10.4 * area[1]), jacobian[13][2], 1.0e-8);
  }

  @Test
  void representablePerturbationsStayFiniteAtFloatingPointRangeLimits() {
    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    for (UnsplitTransientSolver.TimeIntegrationMethod method : UnsplitTransientSolver.TimeIntegrationMethod.values()) {
      solver.setTimeIntegrationMethod(method);
      for (double mass : new double[] { Double.MIN_NORMAL, Double.MIN_VALUE }) {
        double[][] state = { { mass, 800.0, 0.0, 0.0, 0.0, 0.0, 7.0 } };
        double[][] jacobian = solver.scaledJacobian(state, new double[] { 1.0e5 }, state, new double[] { 1.0e5 },
            new double[] { 1.0 }, TIME_STEP, 0.0, Double.NaN, false, quadraticDragModel(1));
        assertFinite(jacobian);
        assertEquals(1.0, jacobian[0][0], 0.0);
      }
    }
    double[][] state = { { 1.0, 800.0, 0.0, 0.0, 0.0, 0.0, 7.0 } };
    UnsplitTransientSolver.Model model = (evaluationState, evaluationPressure, endpointState, endpointPressure, time,
        outletPressure, outletPressureFixed) -> new UnsplitTransientSolver.Evaluation(new double[1][6],
            new double[][] { { endpointPressure[0] }, { 800.0 }, { 1000.0 } });
    assertFinite(solver.scaledJacobian(state, new double[] { 1.0e5 }, state, new double[] { 1.0e100 },
        new double[] { 1.0 }, TIME_STEP, 0.0, Double.NaN, false, model));
  }

  private static void assertFinite(double[][] matrix) {
    for (double[] row : matrix) {
      for (double value : row) {
        assertTrue(Double.isFinite(value));
      }
    }
  }

  private static UnsplitTransientSolver.Model quadraticDragModel(int count) {
    return (evaluationState, evaluationPressure, endpointState, endpointPressure, time, outletPressure,
        outletPressureFixed) -> {
      double[][] rates = new double[count][6];
      double[][] densities = new double[3][count];
      for (int cell = 0; cell < count; cell++) {
        double mass = evaluationState[cell][0];
        double momentum = evaluationState[cell][3];
        double force = mass > 0.0 ? momentum * (momentum / mass) : 0.0;
        rates[cell][3] = -force;
        rates[cell][4] = force;
        densities[0][cell] = 10.0;
        densities[1][cell] = 800.0;
        densities[2][cell] = 1000.0;
      }
      return new UnsplitTransientSolver.Evaluation(rates, densities);
    };
  }
}
