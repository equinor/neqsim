package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TimeIntegrator.
 * 
 * Tests RK4 time integration and CFL-based time step calculation.
 */
public class TimeIntegratorTest {
  private TimeIntegrator integrator;

  @BeforeEach
  void setUp() {
    integrator = new TimeIntegrator();
  }

  @Test
  void testDefaultConstruction() {
    assertNotNull(integrator);
  }

  @Test
  void testMethodConstruction() {
    TimeIntegrator rk4 = new TimeIntegrator(TimeIntegrator.Method.RK4);
    assertNotNull(rk4);

    TimeIntegrator euler = new TimeIntegrator(TimeIntegrator.Method.EULER);
    assertNotNull(euler);
  }

  @Test
  void testCFLNumberSetting() {
    integrator.setCflNumber(0.8);
    assertEquals(0.8, integrator.getCflNumber(), 1e-10);
  }

  @Test
  void testTimeStepLimits() {
    integrator.setMinTimeStep(1e-6);
    integrator.setMaxTimeStep(10.0);

    assertEquals(1e-6, integrator.getMinTimeStep(), 1e-12);
    assertEquals(10.0, integrator.getMaxTimeStep(), 1e-10);
  }

  @Test
  void testCalculateStableTimeStep() {
    integrator.setCflNumber(0.5);

    double maxWaveSpeed = 320.0; // |v| + c
    double dx = 10.0;

    double dt = integrator.calcStableTimeStep(maxWaveSpeed, dx);

    // dt should be limited by CFL: dt <= CFL * dx / maxWaveSpeed
    double expectedMaxDt = 0.5 * dx / maxWaveSpeed;

    assertTrue(dt <= expectedMaxDt + 1e-10, "Time step " + dt + " should be <= " + expectedMaxDt);
    assertTrue(dt > 0, "Time step should be positive");
  }

  @Test
  void testTimeStepConstraints() {
    integrator.setMinTimeStep(0.01);
    integrator.setMaxTimeStep(1.0);
    integrator.setCflNumber(0.5);

    // Very low wave speed - should be limited by maxTimeStep
    double dt = integrator.calcStableTimeStep(0.001, 10.0);
    assertTrue(dt <= 1.0, "Should be limited by max time step");

    // Very high wave speed - should be limited by minTimeStep
    dt = integrator.calcStableTimeStep(1e10, 10.0);
    assertTrue(dt >= 0.01, "Should be limited by min time step");
  }

  @Test
  void testStepConstantRHS() {
    // Test stepping with constant RHS (linear solution)
    double[][] U0 = {{1.0, 2.0}, {3.0, 4.0}};
    double dt = 0.1;

    // RHS that returns zeros - solution should stay constant
    TimeIntegrator.RHSFunction zeroRHS = (U, t) -> {
      double[][] result = new double[U.length][U[0].length];
      return result;
    };

    double[][] U1 = integrator.step(U0, zeroRHS, dt);

    for (int i = 0; i < U0.length; i++) {
      for (int j = 0; j < U0[i].length; j++) {
        assertEquals(U0[i][j], U1[i][j], 1e-10, "Constant solution should stay unchanged");
      }
    }
  }

  @Test
  void testStepLinearRHS() {
    // Test stepping with constant RHS = 1 (linear growth)
    double[][] U0 = {{0.0}};
    double dt = 0.1;

    TimeIntegrator.RHSFunction constantRHS = (U, t) -> new double[][] {{1.0}};

    double[][] U1 = integrator.step(U0, constantRHS, dt);

    // For constant RHS, all RK methods should give exact result
    assertEquals(dt, U1[0][0], 1e-10, "Linear growth should give U = dt");
  }

  @Test
  void testTimeAdvancement() {
    double initialTime = 0.0;
    integrator.setCurrentTime(initialTime);

    double[][] U = {{1.0}};
    TimeIntegrator.RHSFunction rhs = (state, t) -> new double[][] {{0.0}};

    double dt = 0.5;
    integrator.step(U, rhs, dt);
    integrator.setCurrentTime(integrator.getCurrentTime() + dt);

    assertEquals(0.5, integrator.getCurrentTime(), 1e-10);
  }

  @Test
  void testMultipleVariables() {
    // Test with multiple cells and variables
    double[][] U0 = {{1.0, 2.0, 3.0, 4.0}, // Cell 0: 4 conservative vars
        {5.0, 6.0, 7.0, 8.0}, // Cell 1
        {9.0, 10.0, 11.0, 12.0} // Cell 2
    };
    double dt = 0.01;

    TimeIntegrator.RHSFunction rhs = (U, t) -> {
      double[][] result = new double[U.length][U[0].length];
      for (int i = 0; i < U.length; i++) {
        for (int j = 0; j < U[i].length; j++) {
          result[i][j] = 0.1 * U[i][j]; // Simple growth
        }
      }
      return result;
    };

    double[][] U1 = integrator.step(U0, rhs, dt);

    // Verify all values are valid and have grown
    for (int i = 0; i < U1.length; i++) {
      for (int j = 0; j < U1[i].length; j++) {
        assertFalse(Double.isNaN(U1[i][j]), "Result should not be NaN");
        assertFalse(Double.isInfinite(U1[i][j]), "Result should not be infinite");
        assertTrue(U1[i][j] > U0[i][j], "Values should grow");
      }
    }
  }

  @Test
  void testTypicalPipelineConditions() {
    integrator.setCflNumber(0.5);
    integrator.setMinTimeStep(1e-4);
    integrator.setMaxTimeStep(1.0);

    // Typical offshore pipeline: 50m grid, 15 m/s gas, 350 m/s sound speed
    double maxWaveSpeed = 365.0; // max(|v| + c)
    double dx = 50.0;

    double dt = integrator.calcStableTimeStep(maxWaveSpeed, dx);

    // Should be around 0.5 * 50 / 365 ≈ 0.068 seconds
    assertTrue(dt > 0.01 && dt < 0.2, "Time step " + dt + " should be reasonable for pipeline");
  }
}
