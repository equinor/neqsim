package neqsim.process.dynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Quantitative reference and timestep-refinement evidence for the scalar BDF-1 integrator. */
class BDFIntegratorReferenceTest {
  private static final class LinearDecay implements IntegratorStrategy.Slope {
    private static final long serialVersionUID = 1L;
    private final double rate;

    private LinearDecay(double rate) {
      this.rate = rate;
    }

    @Override
    public double dxdt(double time, double state) {
      return -rate * state;
    }
  }

  @Test
  void linearDecayMatchesClosedFormBackwardEulerStep() {
    BDFIntegrator bdf = new BDFIntegrator(1.0e-11, 25, 1.0e-6);
    LinearDecay decay = new LinearDecay(100.0);
    double state = 1.0;
    double dt = 0.05;

    for (int step = 0; step < 20; step++) {
      double expected = state / (1.0 + 100.0 * dt);
      state = bdf.step(step * dt, state, decay, dt);
      assertEquals(expected, state, 1.0e-10,
          "BDF-1 must satisfy the closed-form backward-Euler result for linear decay");
      assertTrue(bdf.lastStepConverged());
      assertFalse(bdf.lastStepFellBack());
      assertTrue(bdf.getLastResidual() <= bdf.getTolerance());
    }
  }

  @Test
  void timestepHalvingShowsFirstOrderConvergenceToAnalyticDecay() {
    double coarseError = integrateError(0.2);
    double mediumError = integrateError(0.1);
    double fineError = integrateError(0.05);

    assertTrue(mediumError < coarseError, "halving dt must reduce the global error");
    assertTrue(fineError < mediumError, "a second dt halving must reduce the global error again");
    assertTrue(coarseError / mediumError > 1.7,
        "BDF-1 should approach first-order convergence under timestep refinement");
    assertTrue(mediumError / fineError > 1.7,
        "BDF-1 should retain first-order convergence on the finer refinement pair");
  }

  @Test
  void veryStiffSingleStepRemainsBoundedAndMatchesBackwardEulerReference() {
    BDFIntegrator bdf = new BDFIntegrator(1.0e-11, 25, 1.0e-6);
    LinearDecay decay = new LinearDecay(1000.0);
    double dt = 0.1;

    double state = bdf.step(0.0, 1.0, decay, dt);
    double expected = 1.0 / (1.0 + 1000.0 * dt);

    assertEquals(expected, state, 1.0e-10);
    assertTrue(state > 0.0 && state < 1.0, "L-stable BDF-1 decay must remain positive and bounded");
    assertTrue(bdf.lastStepConverged());
    assertFalse(bdf.lastStepFellBack());
  }

  private static double integrateError(double dt) {
    BDFIntegrator bdf = new BDFIntegrator(1.0e-11, 25, 1.0e-6);
    LinearDecay decay = new LinearDecay(1.0);
    double state = 1.0;
    double time = 0.0;
    int steps = (int) Math.round(1.0 / dt);
    for (int step = 0; step < steps; step++) {
      state = bdf.step(time, state, decay, dt);
      time += dt;
    }
    return Math.abs(state - Math.exp(-1.0));
  }
}
