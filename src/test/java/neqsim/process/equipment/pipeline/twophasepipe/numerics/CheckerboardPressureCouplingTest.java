package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Independent pressure-mode and conservation checks for collocated momentum interpolation. */
class CheckerboardPressureCouplingTest {
  @Test
  void disabledInterpolationRetainsLegacyVolumeExactCheckerboard() {
    CoupledPressureMomentumSolver solver = new CoupledPressureMomentumSolver();
    assertFalse(solver.isCheckerboardCorrectionEnabled());
    Fixture fixture = checkerboard(new double[] { 1.0, 0.0, 0.0 }, 16, 100.0);

    CoupledPressureMomentumSolver.Result result = solve(solver, fixture, 0.1);

    assertTrue(result.isConverged());
    assertEquals(0, result.getIterations());
    assertArrayEquals(fixture.pressure, result.getPressure(), 0.0);
    assertStateEquals(fixture.state, result.getState(), 0.0);
  }

  /**
   * For four equal cells with zero boundary correction flow, the linear Helmholtz equations can be solved by hand. With
   * initial pressures p0 + a*[1,-1,1,-1] and beta=(c*dt/dx)^2, the pressure corrections divided by a are [-beta,
   * 3*beta+2*beta^2, -3*beta-2*beta^2, beta] / (1+4*beta+2*beta^2). This finite-domain reference includes the one-sided
   * endpoint gradients; the periodic Fourier damping factor would be a different boundary problem.
   */
  @Test
  void pressureResponseMatchesLinearFiniteDomainReferenceAtLargeAcousticStep() {
    for (double beta : new double[] { 1.0, 900.0 }) {
      CoupledPressureMomentumSolver solver = enabledSolver();
      solver.setPressureRelaxation(1.0);
      solver.setRelativeVolumeTolerance(1.0e-12);
      Fixture fixture = checkerboard(new double[] { 1.0, 0.0, 0.0 }, 4, 1.0);
      double dt = Math.sqrt(beta) / fixture.soundSpeed[0][0];

      CoupledPressureMomentumSolver.Result result = solve(solver, fixture, dt);

      assertTrue(result.isConverged(), "The small-amplitude implicit pressure mode must converge at beta=" + beta);
      double denominator = 1.0 + 4.0 * beta + 2.0 * beta * beta;
      double endCorrection = -beta / denominator;
      double innerCorrection = (3.0 * beta + 2.0 * beta * beta) / denominator;
      double[] correction = { endCorrection, innerCorrection, -innerCorrection, -endCorrection };
      for (int cell = 0; cell < 4; cell++) {
        assertEquals(fixture.pressure[cell] + correction[cell], result.getPressure()[cell], 1.0e-4,
            "Pressure must follow the compact implicit face operator, cell=" + cell + ", beta=" + beta);
      }
      assertArrayEquals(phaseInventory(fixture.state, fixture.length),
          phaseInventory(result.getState(), fixture.length), 1.0e-10);
    }
  }

  @Test
  void initiallyVolumeExactPressureModeDampsAndConservesGasOilAndWater() {
    double[][] phaseFractions = { { 1.0, 0.0, 0.0 }, { 0.0, 1.0, 0.0 }, { 0.0, 0.0, 1.0 }, { 0.4, 0.3, 0.3 } };
    for (double[] fractions : phaseFractions) {
      CoupledPressureMomentumSolver solver = enabledSolver();
      Fixture fixture = checkerboard(fractions, 16, 100.0);
      double initialAmplitude = checkerboardAmplitude(fixture.pressure);

      // At dx=1 m this is acoustic Courant 30 for gas and 120 for water. Material velocity is initially zero.
      CoupledPressureMomentumSolver.Result result = solve(solver, fixture, 0.1);

      assertTrue(result.isConverged(),
          "The finite-volume pressure mode must converge for " + Arrays.toString(fractions));
      assertTrue(result.getIterations() > 0, "Zero initial volume residual must not skip the face-pressure forcing");
      assertTrue(checkerboardAmplitude(result.getPressure()) < 0.5 * initialAmplitude,
          "The alternating pressure mode must decay for " + Arrays.toString(fractions));
      assertArrayEquals(phaseInventory(fixture.state, fixture.length),
          phaseInventory(result.getState(), fixture.length), 1.0e-8);
      assertArrayEquals(new double[3], result.getOutletBoundaryMassCorrectionKg(), 0.0);
      assertFinitePositive(result);
      for (int phase = 0; phase < 3; phase++) {
        if (fractions[phase] == 0.0) {
          for (double[] cell : result.getState()) {
            assertEquals(0.0, cell[phase], 0.0, "The interpolation cannot create an absent phase");
          }
        }
      }
    }
  }

  /** This isolates the interpolation residual; the caller supplies the balancing gravity force in a pipe RHS. */
  @Test
  void linearHydrostaticPressureAddsNoCorrectionOnANonuniformMesh() {
    CoupledPressureMomentumSolver solver = enabledSolver();
    Fixture fixture = checkerboard(new double[] { 0.0, 0.0, 1.0 }, 6, 0.0);
    fixture.length = new double[] { 1.0, 2.0, 4.0, 2.0, 1.0, 2.0 };
    double leftFace = 0.0;
    for (int cell = 0; cell < fixture.state.length; cell++) {
      double center = leftFace + 0.5 * fixture.length[cell];
      // An exactly representable constant gradient avoids conflating interpolation with round-off.
      fixture.pressure[cell] = 2.0e6 - 1024.0 * center;
      leftFace += fixture.length[cell];
    }

    CoupledPressureMomentumSolver.Result result = solve(solver, fixture, 0.1);

    assertTrue(result.isConverged());
    assertArrayEquals(fixture.pressure, result.getPressure(), 0.0);
    assertStateEquals(fixture.state, result.getState(), 0.0);
  }

  @Test
  void constantPressureDensityAndPhaseContactRemainsAtRest() {
    CoupledPressureMomentumSolver solver = enabledSolver();
    Fixture fixture = checkerboard(new double[] { 0.4, 0.3, 0.3 }, 6, 0.0);
    for (int cell = 0; cell < fixture.state.length; cell++) {
      double[] fractions = cell < 3 ? new double[] { 0.0, 0.0, 1.0 } : new double[] { 0.6, 0.4, 0.0 };
      fixture.density[0][cell] = cell < 3 ? 0.02 : 20.0;
      fixture.density[1][cell] = cell < 3 ? 800.0 : 700.0;
      fixture.density[2][cell] = cell < 3 ? 1000.0 : 900.0;
      for (int phase = 0; phase < 3; phase++) {
        fixture.state[cell][phase] = fractions[phase] * fixture.density[phase][cell] * fixture.area[cell];
      }
    }

    CoupledPressureMomentumSolver.Result result = solve(solver, fixture, 0.1);

    assertTrue(result.isConverged());
    assertArrayEquals(fixture.pressure, result.getPressure(), 0.0);
    assertStateEquals(fixture.state, result.getState(), 0.0);
    assertArrayEquals(fixture.density[0], result.getGasDensity(), 0.0);
    assertArrayEquals(fixture.density[1], result.getOilDensity(), 0.0);
    assertArrayEquals(fixture.density[2], result.getWaterDensity(), 0.0);
  }

  @Test
  void discardedCorrectionCannotModifyInputsOrSubsequentRetry() {
    CoupledPressureMomentumSolver solver = enabledSolver();
    Fixture fixture = checkerboard(new double[] { 0.4, 0.3, 0.3 }, 16, 100.0);
    double[][] original = copy(fixture.state);
    double[] initialPressure = fixture.pressure.clone();
    double[][] initialDensity = copy(fixture.density);

    solve(solver, fixture, 0.1);
    CoupledPressureMomentumSolver.Result retry = solve(solver, fixture, 0.05);
    CoupledPressureMomentumSolver.Result fresh = solve(enabledSolver(), fixture, 0.05);

    assertStateEquals(original, fixture.state, 0.0);
    assertArrayEquals(initialPressure, fixture.pressure, 0.0);
    assertStateEquals(initialDensity, fixture.density, 0.0);
    assertStateEquals(fresh.getState(), retry.getState(), 0.0);
    assertArrayEquals(fresh.getPressure(), retry.getPressure(), 0.0);
    assertTrue(retry.isConverged());
  }

  private static CoupledPressureMomentumSolver enabledSolver() {
    CoupledPressureMomentumSolver solver = new CoupledPressureMomentumSolver();
    solver.setCheckerboardCorrectionEnabled(true);
    return solver;
  }

  private static CoupledPressureMomentumSolver.Result solve(CoupledPressureMomentumSolver solver, Fixture fixture,
      double dt) {
    return solver.correct(fixture.state, dt, fixture.pressure, fixture.area, fixture.length, fixture.density[0],
        fixture.density[1], fixture.density[2], fixture.soundSpeed[0], fixture.soundSpeed[1], fixture.soundSpeed[2],
        fixture.pressure[fixture.pressure.length - 1], false);
  }

  private static Fixture checkerboard(double[] fractions, int cellCount, double amplitude) {
    Fixture fixture = new Fixture();
    fixture.state = new double[cellCount][7];
    fixture.pressure = new double[cellCount];
    fixture.area = filled(cellCount, 1.0);
    fixture.length = filled(cellCount, 1.0);
    fixture.density = new double[3][cellCount];
    fixture.soundSpeed = new double[][] { filled(cellCount, 300.0), filled(cellCount, 1200.0),
        filled(cellCount, 1200.0) };
    double[] referenceDensity = { 10.0, 800.0, 1000.0 };
    for (int cell = 0; cell < cellCount; cell++) {
      double perturbation = cell % 2 == 0 ? amplitude : -amplitude;
      fixture.pressure[cell] = 1.0e6 + perturbation;
      for (int phase = 0; phase < 3; phase++) {
        double c = fixture.soundSpeed[phase][cell];
        fixture.density[phase][cell] = referenceDensity[phase] + perturbation / (c * c);
        fixture.state[cell][phase] = fractions[phase] * fixture.density[phase][cell] * fixture.area[cell];
      }
      fixture.state[cell][6] = 1.0e6;
    }
    return fixture;
  }

  private static double checkerboardAmplitude(double[] pressure) {
    double projection = 0.0;
    for (int cell = 0; cell < pressure.length; cell++) {
      projection += (cell % 2 == 0 ? 1.0 : -1.0) * pressure[cell];
    }
    return Math.abs(projection / pressure.length);
  }

  private static double[] phaseInventory(double[][] state, double[] length) {
    double[] inventory = new double[3];
    for (int cell = 0; cell < state.length; cell++) {
      for (int phase = 0; phase < 3; phase++) {
        inventory[phase] += state[cell][phase] * length[cell];
      }
    }
    return inventory;
  }

  private static void assertFinitePositive(CoupledPressureMomentumSolver.Result result) {
    for (double pressure : result.getPressure()) {
      assertTrue(Double.isFinite(pressure) && pressure > 0.0);
    }
    for (double[] cell : result.getState()) {
      for (double value : cell) {
        assertTrue(Double.isFinite(value));
      }
      for (int phase = 0; phase < 3; phase++) {
        assertTrue(cell[phase] >= 0.0);
      }
    }
  }

  private static void assertStateEquals(double[][] expected, double[][] actual, double tolerance) {
    assertEquals(expected.length, actual.length);
    for (int cell = 0; cell < expected.length; cell++) {
      assertArrayEquals(expected[cell], actual[cell], tolerance);
    }
  }

  private static double[][] copy(double[][] state) {
    double[][] result = new double[state.length][];
    for (int cell = 0; cell < state.length; cell++) {
      result[cell] = state[cell].clone();
    }
    return result;
  }

  private static double[] filled(int count, double value) {
    double[] result = new double[count];
    Arrays.fill(result, value);
    return result;
  }

  private static final class Fixture {
    private double[][] state;
    private double[] pressure;
    private double[] area;
    private double[] length;
    private double[][] density;
    private double[][] soundSpeed;
  }
}
