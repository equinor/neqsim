package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UnsplitTransientSolverTest {
  private static final double GAS_DENSITY = 10.0;
  private static final double OIL_DENSITY = 800.0;
  private static final double WATER_DENSITY = 1000.0;

  @Test
  void hydrostaticTerrainKinkIsAnInvariantCommonTimeLevel() {
    double[] elevations = { 0.0, -2.0, 4.0, 9.0 };
    double mixtureDensity = 0.2 * GAS_DENSITY + 0.8 * OIL_DENSITY;
    double[][] state = filledState(elevations.length);
    double[] pressure = new double[elevations.length];
    for (int cell = 0; cell < pressure.length; cell++) {
      pressure[cell] = 5.0e6 - mixtureDensity * 9.81 * elevations[cell];
    }
    final int[] linearizations = { 0, 0 };
    UnsplitTransientSolver.Model model = new UnsplitTransientSolver.Model() {
      @Override
      public UnsplitTransientSolver.Evaluation evaluate(double[][] midpointState, double[] midpointPressure,
          double[][] closureState, double[] closurePressure, double time, double outletPressure,
          boolean outletPressureFixed) {
        double[][] rates = new double[midpointState.length][6];
        for (int cell = 0; cell < midpointState.length; cell++) {
          double equilibrium = 5.0e6 - mixtureDensity * 9.81 * elevations[cell];
          rates[cell][3] = -(midpointPressure[cell] - equilibrium);
          rates[cell][4] = -(midpointPressure[cell] - equilibrium);
        }
        return new UnsplitTransientSolver.Evaluation(rates, constantDensities(midpointState.length));
      }

      @Override
      public void beginLinearization(double[][] midpointState, double[] midpointPressure) {
        linearizations[0]++;
      }

      @Override
      public void endLinearization() {
        linearizations[1]++;
      }
    };

    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    UnsplitTransientSolver.Result result = solver.solve(state, pressure, unitAreas(pressure.length), 0.1, 0.0, 4.0e6,
        true, model);

    assertTrue(result.isConverged(), "iterations=" + result.getIterations() + ", residual="
        + result.getMaximumScaledResidual() + ", evaluations=" + result.getModelEvaluations());
    assertEquals(0, result.getIterations());
    assertEquals(1, result.getModelEvaluations());
    assertEquals(0.0, result.getMaximumScaledResidual(), 1.0e-14);
    assertEquals(0, linearizations[0]);
    assertEquals(0, linearizations[1]);
    assertStateEquals(state, result.getState(), 0.0);
    for (int cell = 0; cell < pressure.length; cell++) {
      assertEquals(pressure[cell], result.getPressure()[cell], 0.0);
    }
  }

  @Test
  void implicitMidpointAdvancesMomentumWithoutDampingItAsBackwardEuler() {
    double[][] state = filledState(2);
    state[0][3] = 10.0;
    state[1][3] = -4.0;
    double[] pressure = { 5.0e6, 5.0e6 };
    final double decayRate = 2.0;
    UnsplitTransientSolver.Model model = (midpointState, midpointPressure, closureState, closurePressure, time,
        outletPressure, outletPressureFixed) -> {
      double[][] rates = new double[midpointState.length][6];
      for (int cell = 0; cell < midpointState.length; cell++) {
        rates[cell][3] = -decayRate * midpointState[cell][3];
      }
      double[][] densities = constantDensities(midpointState.length);
      for (int cell = 0; cell < midpointState.length; cell++) {
        densities[0][cell] += 1.0e-6 * (closurePressure[cell] - 5.0e6);
      }
      return new UnsplitTransientSolver.Evaluation(rates, densities);
    };

    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    double timeStep = 0.2;
    UnsplitTransientSolver.Result result = solver.solve(state, pressure, unitAreas(2), timeStep, 1.0, Double.NaN, false,
        model);

    double midpointFactor = (1.0 - 0.5 * decayRate * timeStep) / (1.0 + 0.5 * decayRate * timeStep);
    assertTrue(result.isConverged(), "iterations=" + result.getIterations() + ", residual="
        + result.getMaximumScaledResidual() + ", evaluations=" + result.getModelEvaluations());
    assertEquals(state[0][3] * midpointFactor, result.getState()[0][3], 1.0e-8);
    assertEquals(state[1][3] * midpointFactor, result.getState()[1][3], 1.0e-8);
    assertFalse(Math.abs(result.getState()[0][3] - state[0][3] / (1.0 + decayRate * timeStep)) < 1.0e-6,
        "The physical trajectory must not silently become backward Euler");
  }

  @Test
  void fixedOutletPressureActsAtBoundaryFaceAndRetainsLastCellClosure() {
    double[][] state = new double[][] { { 10.0, 0.0, 0.0, 0.0, 0.0, 0.0, 17.0 } };
    double[] pressure = { 5.0e6 };
    final double requestedOutletPressure = 4.0e6;
    final boolean[] boundaryObserved = { false };
    UnsplitTransientSolver.Model model = (midpointState, midpointPressure, closureState, closurePressure, time,
        outletPressure, outletPressureFixed) -> {
      boundaryObserved[0] = outletPressureFixed && outletPressure == requestedOutletPressure;
      double[][] rates = new double[1][6];
      rates[0][0] = -0.1;
      double gasDensity = GAS_DENSITY + 1.0e-5 * (closurePressure[0] - 5.0e6);
      return new UnsplitTransientSolver.Evaluation(rates,
          new double[][] { { gasDensity }, { OIL_DENSITY }, { WATER_DENSITY } });
    };

    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    UnsplitTransientSolver.Result result = solver.solve(state, pressure, new double[] { 1.0 }, 0.1, 0.0,
        requestedOutletPressure, true, model);

    assertTrue(result.isConverged());
    assertTrue(boundaryObserved[0]);
    assertEquals(9.99, result.getState()[0][0], 1.0e-9);
    assertEquals(4.999e6, result.getPressure()[0], 1.0e-2);
    assertFalse(Math.abs(result.getPressure()[0] - requestedOutletPressure) < 1.0,
        "The outlet face pressure must not replace the last-cell volume equation");
  }

  @Test
  void coloredJacobianMatchesIndependentDirectionalDifference() {
    int cellCount = 6;
    double[][] state = filledState(cellCount);
    double[] pressure = new double[cellCount];
    for (int cell = 0; cell < cellCount; cell++) {
      pressure[cell] = 5.0e6 + 1000.0 * cell;
      state[cell][3] = cell + 1.0;
    }
    UnsplitTransientSolver.Model model = (midpointState, midpointPressure, closureState, closurePressure, time,
        outletPressure, outletPressureFixed) -> {
      double[][] rates = new double[cellCount][6];
      for (int cell = 0; cell < cellCount; cell++) {
        int left = Math.max(0, cell - 1);
        int right = Math.min(cellCount - 1, cell + 1);
        rates[cell][3] = 0.2 * midpointState[left][3] - 0.5 * midpointState[cell][3] + 0.3 * midpointState[right][3]
            - 1.0e-6 * midpointPressure[cell];
      }
      double[][] densities = constantDensities(cellCount);
      for (int cell = 0; cell < cellCount; cell++) {
        densities[0][cell] += 1.0e-6 * (closurePressure[cell] - 5.0e6);
      }
      return new UnsplitTransientSolver.Evaluation(rates, densities);
    };
    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    double[][] jacobian = solver.scaledJacobian(state, pressure, state, pressure, unitAreas(cellCount), 0.05, 0.0,
        Double.NaN, false, model);

    double[] direction = new double[cellCount * UnsplitTransientSolver.BLOCK_SIZE];
    for (int index = 0; index < direction.length; index++) {
      direction[index] = Math.sin(index + 1.0);
    }
    double[] matrixProduct = multiply(jacobian, direction);
    double epsilon = 1.0e-5;
    double[][] perturbedState = copy(state);
    double[] perturbedPressure = pressure.clone();
    for (int cell = 0; cell < cellCount; cell++) {
      int offset = cell * UnsplitTransientSolver.BLOCK_SIZE;
      for (int variable = 0; variable < 6; variable++) {
        double scale = Math.max(1.0, Math.abs(state[cell][variable]));
        perturbedState[cell][variable] += epsilon * scale * direction[offset + variable];
      }
      double pressureScale = Math.max(1.0e5, Math.abs(pressure[cell]));
      perturbedPressure[cell] += epsilon * pressureScale * direction[offset + 6];
    }
    double[] baseResidual = solver.residual(state, pressure, state, pressure, unitAreas(cellCount), 0.05, 0.0,
        Double.NaN, false, model);
    double[] perturbedResidual = solver.residual(state, pressure, perturbedState, perturbedPressure,
        unitAreas(cellCount), 0.05, 0.0, Double.NaN, false, model);
    for (int row = 0; row < matrixProduct.length; row++) {
      double directionalDifference = (perturbedResidual[row] - baseResidual[row]) / epsilon;
      assertEquals(directionalDifference, matrixProduct[row], 2.0e-4,
          "Colored stencil derivative mismatch at row " + row);
    }
  }

  @Test
  void rejectsInvalidAcceptedStateAndFixedBoundary() {
    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    double[][] state = filledState(1);
    double[] pressure = { 5.0e6 };
    UnsplitTransientSolver.Model model = (midpointState, midpointPressure, closureState, closurePressure, time,
        outletPressure,
        outletPressureFixed) -> new UnsplitTransientSolver.Evaluation(new double[1][6], constantDensities(1));

    state[0][0] = -1.0;
    assertThrows(IllegalArgumentException.class,
        () -> solver.solve(state, pressure, unitAreas(1), 0.1, 0.0, Double.NaN, false, model));
    state[0][0] = 2.0;
    assertThrows(IllegalArgumentException.class,
        () -> solver.solve(state, pressure, unitAreas(1), 0.1, 0.0, Double.NaN, true, model));
  }

  @Test
  void refreshesChangedActiveSetBeforeDeclaringConvergence() {
    double[][] state = filledState(1);
    double[] pressure = { 5.0e6 };
    final boolean[] secondRegime = { false };
    final int[] updates = { 0 };
    UnsplitTransientSolver.Model model = new UnsplitTransientSolver.Model() {
      @Override
      public UnsplitTransientSolver.Evaluation evaluate(double[][] midpointState, double[] midpointPressure,
          double[][] closureState, double[] closurePressure, double time, double outletPressure,
          boolean outletPressureFixed) {
        double[][] rates = new double[1][6];
        rates[0][3] = secondRegime[0] ? 2.0 : 1.0;
        double[][] densities = constantDensities(1);
        densities[0][0] += 1.0e-6 * (closurePressure[0] - 5.0e6);
        return new UnsplitTransientSolver.Evaluation(rates, densities);
      }

      @Override
      public boolean updateActiveSet(double[][] midpointState, double[] midpointPressure) {
        updates[0]++;
        if (!secondRegime[0]) {
          secondRegime[0] = true;
          return true;
        }
        return false;
      }
    };

    UnsplitTransientSolver.Result result = solverWithTolerance(1.0e-10).solve(state, pressure, unitAreas(1), 0.1, 0.0,
        Double.NaN, false, model);

    assertTrue(result.isConverged());
    assertTrue(result.isActiveSetStable());
    assertTrue(updates[0] >= 2);
    assertEquals(0.2, result.getState()[0][3], 1.0e-9);
  }

  @Test
  void infeasibleMassWithdrawalStopsAtPositiveState() {
    double[][] state = new double[][] { { 1.0, 720.0, 0.0, 0.0, 0.0, 0.0, 17.0 } };
    double[] pressure = { 5.0e6 };
    UnsplitTransientSolver.Model model = (midpointState, midpointPressure, closureState, closurePressure, time,
        outletPressure, outletPressureFixed) -> {
      double[][] rates = new double[1][6];
      rates[0][0] = -20.0;
      double gasDensity = GAS_DENSITY + 1.0e-6 * (closurePressure[0] - 5.0e6);
      return new UnsplitTransientSolver.Evaluation(rates,
          new double[][] { { gasDensity }, { OIL_DENSITY }, { WATER_DENSITY } });
    };

    UnsplitTransientSolver.Result result = new UnsplitTransientSolver().solve(state, pressure, unitAreas(1), 0.1, 0.0,
        Double.NaN, false, model);

    assertFalse(result.isConverged());
    assertTrue(result.getState()[0][0] >= 0.0);
    assertTrue(result.getPressure()[0] >= 1.0);
    assertTrue(result.getMinimumAcceptedStepLength() < 1.0);
  }

  private static double[][] filledState(int cellCount) {
    double[][] state = new double[cellCount][7];
    for (int cell = 0; cell < cellCount; cell++) {
      state[cell][0] = 0.2 * GAS_DENSITY;
      state[cell][1] = 0.8 * OIL_DENSITY;
      state[cell][2] = 0.0;
      state[cell][6] = 1.0e6;
    }
    return state;
  }

  private static UnsplitTransientSolver solverWithTolerance(double tolerance) {
    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    solver.setRelativeTolerance(tolerance);
    return solver;
  }

  private static double[][] constantDensities(int cellCount) {
    double[][] density = new double[3][cellCount];
    for (int cell = 0; cell < cellCount; cell++) {
      density[0][cell] = GAS_DENSITY;
      density[1][cell] = OIL_DENSITY;
      density[2][cell] = WATER_DENSITY;
    }
    return density;
  }

  private static double[] unitAreas(int cellCount) {
    double[] areas = new double[cellCount];
    java.util.Arrays.fill(areas, 1.0);
    return areas;
  }

  private static void assertStateEquals(double[][] expected, double[][] actual, double tolerance) {
    for (int cell = 0; cell < expected.length; cell++) {
      for (int variable = 0; variable < expected[cell].length; variable++) {
        assertEquals(expected[cell][variable], actual[cell][variable], tolerance);
      }
    }
  }

  private static double[] multiply(double[][] matrix, double[] vector) {
    double[] result = new double[vector.length];
    for (int row = 0; row < result.length; row++) {
      for (int column = 0; column < vector.length; column++) {
        result[row] += matrix[row][column] * vector[column];
      }
    }
    return result;
  }

  private static double[][] copy(double[][] values) {
    double[][] result = new double[values.length][];
    for (int row = 0; row < values.length; row++) {
      result[row] = values[row].clone();
    }
    return result;
  }
}
