package neqsim.process.dynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RK4Integrator} and {@link AdaptiveRK45Integrator}.
 */
class AdvancedIntegratorsTest {

  /** Exponential decay: dx/dt = -k x; analytic x(t) = x0 · exp(-k t). */
  private static final class Decay implements IntegratorStrategy.Slope {
    private static final long serialVersionUID = 1L;
    private final double k;

    Decay(double k) {
      this.k = k;
    }

    @Override
    public double dxdt(double time, double state) {
      return -k * state;
    }
  }

  @Test
  void rk4MatchesAnalyticToHighAccuracy() {
    IntegratorStrategy rk4 = new RK4Integrator();
    double k = 0.5;
    double x = 1.0;
    double t = 0.0;
    double dt = 0.01;
    Decay decay = new Decay(k);
    for (int i = 0; i < 100; i++) {
      x = rk4.step(t, x, decay, dt);
      t += dt;
    }
    double expected = Math.exp(-k * 1.0);
    // RK4 is 4th order: error per step ~ dt^5, so error after 100 steps with dt=0.01 is ~ 1e-9.
    assertEquals(expected, x, 1.0e-8, "RK4 should match analytic to 1e-8");
    assertTrue(rk4.getName().contains("RK4"));
  }

  @Test
  void rk4RejectsNullSlopeAndNonPositiveDt() {
    IntegratorStrategy rk4 = new RK4Integrator();
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        rk4.step(0.0, 1.0, null, 0.01);
      }
    });
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        rk4.step(0.0, 1.0, new Decay(0.5), 0.0);
      }
    });
  }

  @Test
  void adaptiveRK45MatchesAnalyticToHighAccuracy() {
    AdaptiveRK45Integrator rk = new AdaptiveRK45Integrator();
    double k = 1.0;
    double x = 1.0;
    double t = 0.0;
    double dt = 0.1;
    Decay decay = new Decay(k);
    for (int i = 0; i < 10; i++) {
      x = rk.step(t, x, decay, dt);
      t += dt;
    }
    double expected = Math.exp(-k * 1.0);
    assertEquals(expected, x, 1.0e-6, "AdaptiveRK45 should match analytic to 1e-6");
    assertTrue(rk.getLastSubSteps() >= 1, "should have taken at least one sub-step");
    assertTrue(rk.getName().toLowerCase(java.util.Locale.ROOT).contains("rk45")
        || rk.getName().toLowerCase(java.util.Locale.ROOT).contains("cash"));
  }

  @Test
  void adaptiveRK45HonorsToleranceConfig() {
    AdaptiveRK45Integrator rk = new AdaptiveRK45Integrator();
    rk.setAbsoluteTolerance(1.0e-10);
    rk.setRelativeTolerance(1.0e-8);
    assertEquals(1.0e-10, rk.getAbsoluteTolerance(), 0.0);
    assertEquals(1.0e-8, rk.getRelativeTolerance(), 0.0);
  }

  @Test
  void adaptiveRK45ValidatesConstructorArguments() {
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        new AdaptiveRK45Integrator().setAbsoluteTolerance(0.0);
      }
    });
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        new AdaptiveRK45Integrator().setRelativeTolerance(-1.0);
      }
    });
    assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
      @Override
      public void execute() {
        new AdaptiveRK45Integrator().setMaxSubSteps(0);
      }
    });
  }
}
