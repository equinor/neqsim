package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
  void solveReevaluatesTheUnperturbedBaseAfterFreezing() {
    double[][] state = filledState(1);
    double[] pressure = { 5.0e6 };
    final boolean[] frozen = { false };
    final boolean[] expectingBase = { false };
    final double[][][] frozenState = { null };
    final double[] selectedRate = { 1.0 };
    UnsplitTransientSolver.Model model = new UnsplitTransientSolver.Model() {
      @Override
      public UnsplitTransientSolver.Evaluation evaluate(double[][] midpointState, double[] midpointPressure,
          double[][] closureState, double[] closurePressure, double time, double outletPressure,
          boolean outletPressureFixed) {
        if (expectingBase[0]) {
          assertStateEquals(frozenState[0], midpointState, 0.0);
          expectingBase[0] = false;
        }
        double[][] rates = new double[1][6];
        rates[0][3] = selectedRate[0];
        return new UnsplitTransientSolver.Evaluation(rates, pressureDependentDensities(closurePressure));
      }

      @Override
      public void beginLinearization(double[][] midpointState, double[] midpointPressure) {
        assertFalse(frozen[0]);
        frozen[0] = true;
        expectingBase[0] = true;
        frozenState[0] = copy(midpointState);
        selectedRate[0] = 2.0;
      }

      @Override
      public void endLinearization() {
        assertFalse(expectingBase[0], "The base must be evaluated inside the frozen linearization");
        frozen[0] = false;
      }
    };

    UnsplitTransientSolver.Result result = solverWithTolerance(1.0e-9).solve(state, pressure, unitAreas(1), 0.1, 0.0,
        Double.NaN, false, model);

    assertTrue(result.isConverged());
    assertFalse(frozen[0]);
    assertEquals(0.2, result.getState()[0][3], 1.0e-9,
        "Both Jacobian differencing and its right-hand side must use the frozen base residual");
  }

  @Test
  void reevaluatesEveryActiveSetSwitchAboveTolerance() {
    double[][] state = filledState(1);
    double[] pressure = { 5.0e6 };
    final int[] activeVersion = { 0 };
    final int[] evaluatedVersion = { -1 };
    final boolean[] frozen = { false };
    final int[] evaluations = { 0 };
    UnsplitTransientSolver.Model model = new UnsplitTransientSolver.Model() {
      @Override
      public UnsplitTransientSolver.Evaluation evaluate(double[][] midpointState, double[] midpointPressure,
          double[][] closureState, double[] closurePressure, double time, double outletPressure,
          boolean outletPressureFixed) {
        evaluations[0]++;
        evaluatedVersion[0] = activeVersion[0];
        double[][] rates = new double[1][6];
        rates[0][3] = 1.0 + activeVersion[0] + midpointState[0][3] * midpointState[0][3];
        return new UnsplitTransientSolver.Evaluation(rates, pressureDependentDensities(closurePressure));
      }

      @Override
      public boolean updateActiveSet(double[][] midpointState, double[] midpointPressure) {
        assertFalse(frozen[0], "Refresh is only allowed after releasing the linearization");
        assertEquals(activeVersion[0], evaluatedVersion[0], "Every switch requires a fresh residual");
        if (midpointState[0][3] > 0.04 && activeVersion[0] < 2) {
          double residual = 2.0 * midpointState[0][3]
              - 0.1 * (1.0 + activeVersion[0] + midpointState[0][3] * midpointState[0][3]);
          assertTrue(Math.abs(residual) > 1.0e-8, "Exercise switches away from the convergence gate");
          activeVersion[0]++;
          return true;
        }
        return false;
      }

      @Override
      public void beginLinearization(double[][] midpointState, double[] midpointPressure) {
        assertEquals(activeVersion[0], evaluatedVersion[0], "Do not reuse a stale active-set residual");
        frozen[0] = true;
      }

      @Override
      public void endLinearization() {
        frozen[0] = false;
      }
    };

    UnsplitTransientSolver.Result result = solverWithTolerance(1.0e-10).solve(state, pressure, unitAreas(1), 0.1, 0.0,
        Double.NaN, false, model);

    assertTrue(result.isConverged());
    assertTrue(result.isActiveSetStable());
    assertEquals(2, activeVersion[0]);
    assertEquals(evaluations[0], result.getModelEvaluations());
    assertEquals((1.0 - Math.sqrt(0.97)) / 0.05, result.getState()[0][3], 1.0e-9);
  }

  @Test
  void countsEveryResidualProbeIncludingTheFrozenBase() {
    double[][] state = filledState(1);
    double[] pressure = { 5.0e6 };
    final int[] evaluations = { 0 };
    UnsplitTransientSolver.Model model = (midpointState, midpointPressure, closureState, closurePressure, time,
        outletPressure, outletPressureFixed) -> {
      evaluations[0]++;
      double[][] rates = new double[1][6];
      rates[0][3] = 1.0;
      return new UnsplitTransientSolver.Evaluation(rates, pressureDependentDensities(closurePressure));
    };

    UnsplitTransientSolver.Result result = new UnsplitTransientSolver().solve(state, pressure, unitAreas(1), 0.1, 0.0,
        Double.NaN, false, model);

    assertTrue(result.isConverged());
    assertEquals(1, result.getIterations());
    assertEquals(10, evaluations[0], "Initial residual, frozen base, seven colored probes and one accepted trial");
    assertEquals(evaluations[0], result.getModelEvaluations());
    assertEquals(UnsplitTransientSolver.TerminationReason.CONVERGED, result.getTerminationReason());
  }

  @Test
  void releasesPartiallyInitializedLinearizationsWhenBeginThrows() {
    for (boolean diagnostic : new boolean[] { false, true }) {
      double[][] state = filledState(1);
      double[] pressure = { 5.0e6 };
      final boolean[] frozen = { false };
      final int[] releases = { 0 };
      IllegalStateException failure = new IllegalStateException("Injected begin failure");
      UnsplitTransientSolver.Model model = new UnsplitTransientSolver.Model() {
        @Override
        public UnsplitTransientSolver.Evaluation evaluate(double[][] midpointState, double[] midpointPressure,
            double[][] closureState, double[] closurePressure, double time, double outletPressure,
            boolean outletPressureFixed) {
          double[][] rates = new double[1][6];
          rates[0][3] = 1.0;
          return new UnsplitTransientSolver.Evaluation(rates, pressureDependentDensities(closurePressure));
        }

        @Override
        public void beginLinearization(double[][] midpointState, double[] midpointPressure) {
          frozen[0] = true;
          throw failure;
        }

        @Override
        public void endLinearization() {
          frozen[0] = false;
          releases[0]++;
        }
      };
      UnsplitTransientSolver solver = new UnsplitTransientSolver();

      IllegalStateException actual = assertThrows(IllegalStateException.class, () -> {
        if (diagnostic) {
          solver.scaledJacobian(state, pressure, state, pressure, unitAreas(1), 0.1, 0.0, Double.NaN, false, model);
        } else {
          solver.solve(state, pressure, unitAreas(1), 0.1, 0.0, Double.NaN, false, model);
        }
      });

      assertSame(failure, actual);
      assertFalse(frozen[0]);
      assertEquals(1, releases[0]);
      assertStateEquals(filledState(1), state, 0.0);
      assertEquals(5.0e6, pressure[0], 0.0);
    }
  }

  @Test
  void preservesProbeFailureWhenLinearizationCleanupAlsoFails() {
    for (boolean diagnostic : new boolean[] { false, true }) {
      double[][] state = filledState(1);
      double[] pressure = { 5.0e6 };
      final boolean[] frozen = { false };
      IllegalStateException failure = new IllegalStateException("Injected probe failure");
      IllegalStateException cleanupFailure = new IllegalStateException("Injected cleanup failure");
      UnsplitTransientSolver.Model model = new UnsplitTransientSolver.Model() {
        @Override
        public UnsplitTransientSolver.Evaluation evaluate(double[][] midpointState, double[] midpointPressure,
            double[][] closureState, double[] closurePressure, double time, double outletPressure,
            boolean outletPressureFixed) {
          if (frozen[0]) {
            throw failure;
          }
          double[][] rates = new double[1][6];
          rates[0][3] = 1.0;
          return new UnsplitTransientSolver.Evaluation(rates, pressureDependentDensities(closurePressure));
        }

        @Override
        public void beginLinearization(double[][] midpointState, double[] midpointPressure) {
          frozen[0] = true;
        }

        @Override
        public void endLinearization() {
          frozen[0] = false;
          throw cleanupFailure;
        }
      };
      UnsplitTransientSolver solver = new UnsplitTransientSolver();

      IllegalStateException actual = assertThrows(IllegalStateException.class, () -> {
        if (diagnostic) {
          solver.scaledJacobian(state, pressure, state, pressure, unitAreas(1), 0.1, 0.0, Double.NaN, false, model);
        } else {
          solver.solve(state, pressure, unitAreas(1), 0.1, 0.0, Double.NaN, false, model);
        }
      });

      assertSame(failure, actual);
      assertEquals(1, actual.getSuppressed().length);
      assertSame(cleanupFailure, actual.getSuppressed()[0]);
      assertFalse(frozen[0]);
    }
  }

  @Test
  void diagnosticJacobianStabilizesRepeatedChangesBeforeFreezing() {
    double[][] state = filledState(1);
    state[0][3] = 1.0;
    double[] pressure = { 5.0e6 };
    final int[] version = { 0 };
    final int[] evaluatedVersion = { -1 };
    final int[] frozenEvaluations = { 0 };
    final int[] evaluations = { 0 };
    final boolean[] frozen = { false };
    UnsplitTransientSolver.Model model = new UnsplitTransientSolver.Model() {
      @Override
      public UnsplitTransientSolver.Evaluation evaluate(double[][] midpointState, double[] midpointPressure,
          double[][] closureState, double[] closurePressure, double time, double outletPressure,
          boolean outletPressureFixed) {
        evaluations[0]++;
        evaluatedVersion[0] = version[0];
        if (frozen[0] && frozenEvaluations[0]++ == 0) {
          assertStateEquals(state, midpointState, 0.0);
        }
        double[][] rates = new double[1][6];
        rates[0][3] = version[0] * midpointState[0][3];
        return new UnsplitTransientSolver.Evaluation(rates, pressureDependentDensities(closurePressure));
      }

      @Override
      public boolean updateActiveSet(double[][] midpointState, double[] midpointPressure) {
        assertFalse(frozen[0]);
        assertEquals(version[0], evaluatedVersion[0]);
        if (version[0] < 3) {
          version[0]++;
          return true;
        }
        return false;
      }

      @Override
      public void beginLinearization(double[][] midpointState, double[] midpointPressure) {
        assertEquals(3, version[0]);
        frozen[0] = true;
      }

      @Override
      public void endLinearization() {
        frozen[0] = false;
      }
    };

    double[][] jacobian = new UnsplitTransientSolver().scaledJacobian(state, pressure, state, pressure, unitAreas(1),
        0.1, 0.0, Double.NaN, false, model);

    assertEquals(0.85, jacobian[3][3], 1.0e-9);
    assertEquals(8, frozenEvaluations[0], "The base and seven columns must share the frozen operator");
    assertEquals(12, evaluations[0], "Initial residual, three refresh residuals and eight frozen probes");
    assertFalse(frozen[0]);
  }

  @Test
  void cyclingActiveSetsStopWithinBudgetWithAFreshFinalResidual() {
    for (boolean diagnostic : new boolean[] { false, true }) {
      double[][] state = filledState(1);
      double[] pressure = { 5.0e6 };
      final int[] updates = { 0 };
      final int[] evaluations = { 0 };
      UnsplitTransientSolver.Model model = new UnsplitTransientSolver.Model() {
        @Override
        public UnsplitTransientSolver.Evaluation evaluate(double[][] midpointState, double[] midpointPressure,
            double[][] closureState, double[] closurePressure, double time, double outletPressure,
            boolean outletPressureFixed) {
          evaluations[0]++;
          double[][] rates = new double[1][6];
          rates[0][3] = 1.0 + updates[0] % 2;
          return new UnsplitTransientSolver.Evaluation(rates, pressureDependentDensities(closurePressure));
        }

        @Override
        public boolean updateActiveSet(double[][] midpointState, double[] midpointPressure) {
          updates[0]++;
          return true;
        }

        @Override
        public void beginLinearization(double[][] midpointState, double[] midpointPressure) {
          throw new AssertionError("Cycling choices must not reach a Newton linearization");
        }
      };
      UnsplitTransientSolver solver = new UnsplitTransientSolver();
      assertEquals(20, solver.getMaximumActiveSetUpdates());
      assertThrows(IllegalArgumentException.class, () -> solver.setMaximumActiveSetUpdates(0));
      assertThrows(IllegalArgumentException.class, () -> solver.setMaximumActiveSetUpdates(-1));
      solver.setMaximumActiveSetUpdates(3);

      if (diagnostic) {
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> solver.scaledJacobian(state,
            pressure, state, pressure, unitAreas(1), 0.1, 0.0, Double.NaN, false, model));
        assertTrue(failure.getMessage().contains("3 refresh attempts"));
      } else {
        UnsplitTransientSolver.Result result = solver.solve(state, pressure, unitAreas(1), 0.1, 0.0, Double.NaN, false,
            model);
        assertFalse(result.isConverged());
        assertFalse(result.isActiveSetStable());
        assertEquals(UnsplitTransientSolver.TerminationReason.ACTIVE_SET_UPDATE_LIMIT, result.getTerminationReason());
        assertEquals(0, result.getIterations());
        assertEquals(0.2, result.getMaximumScaledResidual(), 1.0e-15,
            "The final switch must be reevaluated even when no refresh attempts remain");
        assertEquals(evaluations[0], result.getModelEvaluations());
        assertStateEquals(state, result.getState(), 0.0);
      }
      assertEquals(3, updates[0]);
      assertEquals(4, evaluations[0]);
    }
  }

  @Test
  void stabilityOnTheLastRefreshAttemptIsAccepted() {
    double[][] state = filledState(1);
    double[] pressure = { 5.0e6 };
    final int[] updates = { 0 };
    UnsplitTransientSolver.Model model = new UnsplitTransientSolver.Model() {
      @Override
      public UnsplitTransientSolver.Evaluation evaluate(double[][] midpointState, double[] midpointPressure,
          double[][] closureState, double[] closurePressure, double time, double outletPressure,
          boolean outletPressureFixed) {
        double[][] rates = new double[1][6];
        rates[0][3] = updates[0] >= 2 ? 0.0 : 1.0;
        return new UnsplitTransientSolver.Evaluation(rates, pressureDependentDensities(closurePressure));
      }

      @Override
      public boolean updateActiveSet(double[][] midpointState, double[] midpointPressure) {
        updates[0]++;
        return updates[0] < 3;
      }
    };
    UnsplitTransientSolver solver = new UnsplitTransientSolver();
    solver.setMaximumActiveSetUpdates(3);

    UnsplitTransientSolver.Result result = solver.solve(state, pressure, unitAreas(1), 0.1, 0.0, Double.NaN, false,
        model);

    assertTrue(result.isConverged());
    assertEquals(3, updates[0]);
    assertEquals(3, result.getModelEvaluations());
    assertEquals(0, result.getIterations());
    assertEquals(UnsplitTransientSolver.TerminationReason.CONVERGED, result.getTerminationReason());
  }

  @Test
  void reportsSingularJacobianWithoutClaimingAnAcceptedStep() {
    double[][] state = filledState(1);
    double[] pressure = { 5.0e6 };
    final int[] evaluations = { 0 };
    UnsplitTransientSolver.Model model = (midpointState, midpointPressure, closureState, closurePressure, time,
        outletPressure, outletPressureFixed) -> {
      evaluations[0]++;
      double[][] rates = new double[1][6];
      rates[0][3] = 1.0;
      return new UnsplitTransientSolver.Evaluation(rates, constantDensities(1));
    };

    UnsplitTransientSolver.Result result = new UnsplitTransientSolver().solve(state, pressure, unitAreas(1), 0.1, 0.0,
        Double.NaN, false, model);

    assertFalse(result.isConverged());
    assertEquals(UnsplitTransientSolver.TerminationReason.SINGULAR_JACOBIAN, result.getTerminationReason());
    assertEquals(1, result.getIterations());
    assertEquals(9, evaluations[0]);
    assertEquals(evaluations[0], result.getModelEvaluations());
    assertStateEquals(state, result.getState(), 0.0);
  }

  @Test
  void reportsNoAdmissibleStepWhenAnAbsentPhaseWouldBeWithdrawn() {
    double[][] state = filledState(1);
    double[] pressure = { 5.0e6 };
    UnsplitTransientSolver.Model model = (midpointState, midpointPressure, closureState, closurePressure, time,
        outletPressure, outletPressureFixed) -> {
      double[][] rates = new double[1][6];
      rates[0][2] = -1.0;
      return new UnsplitTransientSolver.Evaluation(rates, pressureDependentDensities(closurePressure));
    };

    UnsplitTransientSolver.Result result = new UnsplitTransientSolver().solve(state, pressure, unitAreas(1), 0.1, 0.0,
        Double.NaN, false, model);

    assertFalse(result.isConverged());
    assertEquals(UnsplitTransientSolver.TerminationReason.NO_ADMISSIBLE_STEP, result.getTerminationReason());
    assertEquals(9, result.getModelEvaluations());
    assertStateEquals(state, result.getState(), 0.0);
  }

  @Test
  void reportsExhaustedLineSearchAndCountsRejectedProbes() {
    double[][] state = filledState(1);
    double[] pressure = { 5.0e6 };
    final int[] evaluations = { 0 };
    UnsplitTransientSolver.Model model = (midpointState, midpointPressure, closureState, closurePressure, time,
        outletPressure, outletPressureFixed) -> {
      evaluations[0]++;
      double midpointMomentum = midpointState[0][3];
      double[][] rates = new double[1][6];
      // With dt = 1 and zero previous momentum the residual is 1 + q^2.
      rates[0][3] = 2.0 * midpointMomentum - 1.0 - 4.0 * midpointMomentum * midpointMomentum;
      return new UnsplitTransientSolver.Evaluation(rates, pressureDependentDensities(closurePressure));
    };

    UnsplitTransientSolver.Result result = new UnsplitTransientSolver().solve(state, pressure, unitAreas(1), 1.0, 0.0,
        Double.NaN, false, model);

    assertFalse(result.isConverged());
    assertEquals(UnsplitTransientSolver.TerminationReason.LINE_SEARCH_FAILED, result.getTerminationReason());
    assertEquals(21, evaluations[0], "Initial residual, eight frozen probes and twelve rejected trials");
    assertEquals(evaluations[0], result.getModelEvaluations());
    assertStateEquals(state, result.getState(), 0.0);
    assertEquals(1.0, result.getMaximumScaledResidual(), 0.0);
  }

  @Test
  void reportsExhaustedNewtonBudgetWithItsFinalAdmissibleIterate() {
    double[][] state = filledState(1);
    double[] pressure = { 5.0e6 };
    UnsplitTransientSolver.Model model = (midpointState, midpointPressure, closureState, closurePressure, time,
        outletPressure, outletPressureFixed) -> {
      double[][] rates = new double[1][6];
      rates[0][3] = 1.0 + midpointState[0][3] * midpointState[0][3];
      return new UnsplitTransientSolver.Evaluation(rates, pressureDependentDensities(closurePressure));
    };
    UnsplitTransientSolver solver = solverWithTolerance(1.0e-10);
    solver.setMaximumIterations(1);

    UnsplitTransientSolver.Result result = solver.solve(state, pressure, unitAreas(1), 0.1, 0.0, Double.NaN, false,
        model);

    assertFalse(result.isConverged());
    assertTrue(result.isActiveSetStable());
    assertEquals(UnsplitTransientSolver.TerminationReason.MAXIMUM_ITERATIONS, result.getTerminationReason());
    assertEquals(1, result.getIterations());
    assertEquals(0.1, result.getState()[0][3], 1.0e-8);
    assertTrue(result.getMaximumScaledResidual() > solver.getRelativeTolerance());
    assertEquals(10, result.getModelEvaluations());
  }

  @Test
  void serializedResultPreservesDiagnosticsAndDefensiveArrays() throws Exception {
    double[][] state = filledState(1);
    double[] pressure = { 5.0e6 };
    UnsplitTransientSolver.Model model = (midpointState, midpointPressure, closureState, closurePressure, time,
        outletPressure, outletPressureFixed) -> new UnsplitTransientSolver.Evaluation(new double[1][6],
            pressureDependentDensities(closurePressure));
    UnsplitTransientSolver.Result original = new UnsplitTransientSolver().solve(state, pressure, unitAreas(1), 0.1, 0.0,
        Double.NaN, false, model);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(original);
    }
    UnsplitTransientSolver.Result restored;
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (UnsplitTransientSolver.Result) input.readObject();
    }

    assertEquals(original.getTerminationReason(), restored.getTerminationReason());
    assertEquals(original.getModelEvaluations(), restored.getModelEvaluations());
    assertEquals(original.getIterations(), restored.getIterations());
    assertEquals(original.isActiveSetStable(), restored.isActiveSetStable());
    assertEquals(original.isConverged(), restored.isConverged());
    assertEquals(original.getMaximumScaledResidual(), restored.getMaximumScaledResidual(), 0.0);
    assertEquals(original.getMinimumAcceptedStepLength(), restored.getMinimumAcceptedStepLength(), 0.0);
    restored.getState()[0][0] = -1.0;
    restored.getPressure()[0] = -1.0;
    assertStateEquals(state, restored.getState(), 0.0);
    assertEquals(pressure[0], restored.getPressure()[0], 0.0);
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

  private static double[][] pressureDependentDensities(double[] pressure) {
    double[][] densities = constantDensities(pressure.length);
    for (int cell = 0; cell < pressure.length; cell++) {
      densities[0][cell] += 1.0e-6 * (pressure[cell] - 5.0e6);
    }
    return densities;
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
