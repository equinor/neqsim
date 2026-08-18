package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CoupledPressureMomentumSolverTest {
  @Test
  void uniformStateIsInvariant() {
    CoupledPressureMomentumSolver solver = new CoupledPressureMomentumSolver();
    double[][] state = uniformState();
    double[][] original = copy(state);
    double[] pressure = filled(4, 5.0e6);
    double[] area = filled(4, 1.0);
    double[] length = filled(4, 10.0);
    double[] gasDensity = filled(4, 10.0);
    double[] oilDensity = filled(4, 800.0);
    double[] waterDensity = filled(4, 1000.0);
    double[] gasSoundSpeed = filled(4, 300.0);
    double[] liquidSoundSpeed = filled(4, 1200.0);

    CoupledPressureMomentumSolver.Result result = solver.correct(state, 0.1, pressure, area, length, gasDensity,
        oilDensity, waterDensity, gasSoundSpeed, liquidSoundSpeed, liquidSoundSpeed, 5.0e6, true);

    assertTrue(result.isConverged());
    assertEquals(0, result.getIterations());
    for (int cell = 0; cell < state.length; cell++) {
      assertArrayEquals(original[cell], result.getState()[cell], 0.0);
    }
    assertArrayEquals(pressure, result.getPressure(), 0.0);
  }

  @Test
  void correctionReducesVolumeResidualAndConservesEachPhase() {
    CoupledPressureMomentumSolver solver = new CoupledPressureMomentumSolver();
    solver.setMaximumIterations(20);
    double[][] state = uniformState();
    state[1][0] -= 0.5;
    state[2][0] += 0.5;
    double[] initialPhaseMass = totalPhaseMass(state);
    double[] pressure = filled(4, 5.0e6);
    double[] area = filled(4, 1.0);
    double[] length = filled(4, 10.0);
    double[] gasDensity = filled(4, 10.0);
    double[] oilDensity = filled(4, 800.0);
    double[] waterDensity = filled(4, 1000.0);
    double[] gasSoundSpeed = filled(4, 300.0);
    double[] liquidSoundSpeed = filled(4, 1200.0);
    double initialResidual = 0.05;

    CoupledPressureMomentumSolver.Result result = solver.correct(state, 0.1, pressure, area, length, gasDensity,
        oilDensity, waterDensity, gasSoundSpeed, liquidSoundSpeed, liquidSoundSpeed, 5.0e6, false);

    assertTrue(result.isConverged());
    assertTrue(result.getMaximumRelativeVolumeResidual() < initialResidual * 1.0e-4);
    assertArrayEquals(initialPhaseMass, totalPhaseMass(result.getState()), 1.0e-10);
    assertTrue(Math.abs(result.getState()[1][3]) > 0.0);
    assertTrue(Math.abs(result.getState()[2][3]) > 0.0);
  }

  private static double[][] uniformState() {
    double[][] state = new double[4][7];
    for (int cell = 0; cell < state.length; cell++) {
      state[cell][0] = 4.0;
      state[cell][1] = 480.0;
      state[cell][2] = 0.0;
      state[cell][3] = 4.0;
      state[cell][4] = 480.0;
      state[cell][5] = 0.0;
      state[cell][6] = 1.0e6;
    }
    return state;
  }

  private static double[] totalPhaseMass(double[][] state) {
    double[] result = new double[3];
    for (double[] cell : state) {
      for (int phase = 0; phase < result.length; phase++) {
        result[phase] += cell[phase];
      }
    }
    return result;
  }

  private static double[] filled(int size, double value) {
    double[] result = new double[size];
    for (int index = 0; index < size; index++) {
      result[index] = value;
    }
    return result;
  }

  private static double[][] copy(double[][] state) {
    double[][] result = new double[state.length][];
    for (int row = 0; row < state.length; row++) {
      result[row] = state[row].clone();
    }
    return result;
  }
}
