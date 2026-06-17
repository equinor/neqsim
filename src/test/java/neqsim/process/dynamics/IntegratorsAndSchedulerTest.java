package neqsim.process.dynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for the dynamics integrators and event scheduler.
 */
class IntegratorsAndSchedulerTest {

  /** Exponential decay: dx/dt = -k x; analytic solution x(t) = x0 · exp(-k t). */
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
  void explicitEulerMatchesAnalyticForSmallStep() {
    IntegratorStrategy euler = new ExplicitEulerIntegrator();
    double k = 0.5;
    double x = 1.0;
    double t = 0.0;
    double dt = 0.001;
    Decay decay = new Decay(k);
    for (int i = 0; i < 1000; i++) {
      x = euler.step(t, x, decay, dt);
      t += dt;
    }
    double expected = Math.exp(-k * 1.0);
    assertEquals(expected, x, 1.0e-3, "explicit Euler should be within 1e-3 of analytic");
    assertEquals("Explicit Euler", euler.getName());
  }

  @Test
  void bdfMatchesAnalyticForStiffStep() {
    // BDF (implicit Euler) handles a much larger step than explicit Euler would tolerate.
    IntegratorStrategy bdf = new BDFIntegrator();
    double k = 100.0; // stiff
    double x = 1.0;
    double t = 0.0;
    double dt = 0.05;
    Decay decay = new Decay(k);
    for (int i = 0; i < 20; i++) {
      x = bdf.step(t, x, decay, dt);
      t += dt;
    }
    // Implicit Euler is monotone-stable; reading at t=1.0 should be a small positive number.
    assertTrue(x > 0.0, "implicit Euler must remain positive for monotone decay, got " + x);
    assertTrue(x < 1.0e-3, "implicit Euler should have decayed below 1e-3, got " + x);
    assertFalse(((BDFIntegrator) bdf).lastStepFellBack(),
        "BDF should converge on linear decay, not fall back to explicit");
  }

  @Test
  void explicitEulerGoesUnstableForStiff() {
    // For dt·k = 6 (>> 2), explicit Euler blows up — this documents the motivation for BDF.
    IntegratorStrategy euler = new ExplicitEulerIntegrator();
    double x = 1.0;
    double t = 0.0;
    Decay decay = new Decay(100.0);
    for (int i = 0; i < 10; i++) {
      x = euler.step(t, x, decay, 0.06);
      t += 0.06;
    }
    assertTrue(Math.abs(x) > 1.0, "explicit Euler should diverge for dt·k=6, got " + x);
  }

  @Test
  void bdfRejectsBadInputs() {
    BDFIntegrator bdf = new BDFIntegrator();
    Decay d = new Decay(1.0);
    assertThrows(IllegalArgumentException.class, () -> bdf.step(0.0, 1.0, null, 0.1));
    assertThrows(IllegalArgumentException.class, () -> bdf.step(0.0, 1.0, d, 0.0));
    assertThrows(IllegalArgumentException.class, () -> bdf.step(0.0, 1.0, d, -0.1));
    assertThrows(IllegalArgumentException.class, () -> new BDFIntegrator(0.0, 10, 1e-6));
    assertThrows(IllegalArgumentException.class, () -> new BDFIntegrator(1e-8, 0, 1e-6));
    assertThrows(IllegalArgumentException.class, () -> new BDFIntegrator(1e-8, 10, 0.0));
  }

  @Test
  void eventSchedulerFiresInTimeOrder() {
    EventScheduler sched = new EventScheduler();
    final int[] counter = new int[3];
    sched.scheduleEvent(2.0, "B", new Runnable() {
      @Override
      public void run() {
        counter[1] = 2;
      }
    });
    sched.scheduleEvent(1.0, "A", new Runnable() {
      @Override
      public void run() {
        counter[0] = 1;
      }
    });
    sched.scheduleEvent(3.0, "C", new Runnable() {
      @Override
      public void run() {
        counter[2] = 3;
      }
    });

    assertEquals(3, sched.getPendingEvents().size());

    // Fire at t=0.5 → nothing yet
    assertEquals(0, sched.fireDueEvents(0.5));

    // Fire at t=2.5 → A and B fire
    assertEquals(2, sched.fireDueEvents(2.5));
    assertEquals(1, counter[0]);
    assertEquals(2, counter[1]);
    assertEquals(0, counter[2]);

    // Fire at t=4.0 → C fires
    assertEquals(1, sched.fireDueEvents(4.0));
    assertEquals(3, counter[2]);

    assertEquals(0, sched.getPendingEvents().size());
    assertEquals(3, sched.getFiredEvents().size());
    // Fired in time order A, B, C
    assertEquals("A", sched.getFiredEvents().get(0).getLabel());
    assertEquals("B", sched.getFiredEvents().get(1).getLabel());
    assertEquals("C", sched.getFiredEvents().get(2).getLabel());
  }

  @Test
  void eventSchedulerRejectsBadInputs() {
    EventScheduler sched = new EventScheduler();
    Runnable r = new Runnable() {
      @Override
      public void run() {}
    };
    assertThrows(IllegalArgumentException.class, () -> sched.scheduleEvent(-1.0, "neg", r));
    assertThrows(IllegalArgumentException.class, () -> sched.scheduleEvent(Double.NaN, "nan", r));
    assertThrows(IllegalArgumentException.class, () -> sched.scheduleEvent(1.0, "null", null));
  }

  @Test
  void eventSchedulerSwallowsExceptions() {
    EventScheduler sched = new EventScheduler();
    sched.scheduleEvent(1.0, "bad", new Runnable() {
      @Override
      public void run() {
        throw new RuntimeException("boom");
      }
    });
    // Should not throw; should still log as fired.
    assertEquals(1, sched.fireDueEvents(2.0));
    assertEquals(1, sched.getFiredEvents().size());
    assertNotNull(sched.getFiredEvents().get(0));
  }
}
