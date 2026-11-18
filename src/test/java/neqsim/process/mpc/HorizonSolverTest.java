package neqsim.process.mpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/**
 * Analytical checks for multi-input constrained step-response optimization.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class HorizonSolverTest {
  /** Check the solution of a coupled two-by-two linear system, bounds and plan ownership. */
  @Test
  void solvesCrossCoupledSystemAndDefensivelyCopiesPlan() {
    ManipulatedVariable u = new ManipulatedVariable("u").setBounds(-4, 4);
    ManipulatedVariable v = new ManipulatedVariable("v").setBounds(-4, 4);
    ControlledVariable y = new ControlledVariable("y").setSetpoint(5);
    ControlledVariable z = new ControlledVariable("z").setSetpoint(1);
    double[][][] kernels = {{{1}, {2}}, {{2}, {-1}}};
    HorizonPlan plan = HorizonSolver.solve(Arrays.asList(u, v), Arrays.asList(y, z), new double[] {0, 0},
        new double[3][2], kernels, 2);
    assertEquals(HorizonPlan.Status.OPTIMAL, plan.getStatus());
    for (double[] move : plan.getPlannedMvs()) {
      assertEquals(1.4, move[0], 1e-6);
      assertEquals(1.8, move[1], 1e-6);
      move[0] = -4;
    }
    assertEquals(1.4, plan.getPlannedMvs()[0][0], 1e-6);
    assertEquals(1.0, plan.getPredictedCvs()[2][1], 1e-6);
  }

  /** Distinguish a per-sample slew from an absolute bound and a violation on each side. */
  @Test
  void asymmetricLimitsAndBothSoftSlacksUsePhysicalUnits() {
    ManipulatedVariable u = new ManipulatedVariable("u").setBounds(-2.0, 0.75).setRateLimit(-0.5, 1.0);
    ControlledVariable y = new ControlledVariable("y").setWeight(0).setSoftConstraints(-1, 1);
    HorizonPlan plan = HorizonSolver.solve(Collections.singletonList(u), Collections.singletonList(y), new double[] {0},
        new double[][] {{-4}, {4}}, new double[][][] {{{1}}}, 2);
    assertTrue(plan.isSuccessful());
    // The two nonzero errors compete under u1 >= u0 - 0.5. Their least-squares optimum is u0 = 0.25.
    assertEquals(0.25, plan.getPlannedMvs()[0][0], 1e-5);
    assertEquals(-0.25, plan.getPlannedMvs()[1][0], 1e-5);
    assertEquals(2.75, plan.getLowerSlack()[0][0], 1e-5);
    assertEquals(2.75, plan.getUpperSlack()[1][0], 1e-5);
    assertEquals(0, plan.getUpperSlack()[0][0]);
    assertEquals(0, plan.getLowerSlack()[1][0]);
  }

  /** An infeasible hard interval returns an empty plan, including when the held tail cannot satisfy the slew. */
  @Test
  void reportsInfeasibleInitiallyAndBeyondControlHorizon() {
    ManipulatedVariable u = new ManipulatedVariable("u").setBounds(2, 4).setRateLimit(-0.5, 1);
    ControlledVariable y = new ControlledVariable("y").setSetpoint(2);
    double[][][] kernels = {{{1}}};
    HorizonPlan initial = HorizonSolver.solve(Collections.singletonList(u), Collections.singletonList(y),
        new double[] {0}, new double[3][1], kernels, 2);
    assertEquals(HorizonPlan.Status.INFEASIBLE, initial.getStatus());
    assertFalse(initial.isSuccessful());
    assertEquals(0, initial.getPlannedMvs().length);
    u.setRateLimit(0.2, 1.0);
    assertEquals(HorizonPlan.Status.INFEASIBLE, HorizonSolver.solve(Collections.singletonList(u),
        Collections.singletonList(y), new double[] {2}, new double[3][1], kernels, 2).getStatus());
  }

  /**
   * Delayed step response, zone objective, preferred value, linear cost and move suppression have an analytic optimum.
   */
  @Test
  void horizonIncludesDelayedEffectAndHeldEconomicCosts() {
    ManipulatedVariable u = new ManipulatedVariable("u").setPreferredValue(1).setPreferredWeight(1).setCost(0.4)
        .setMoveWeight(1);
    ControlledVariable y = new ControlledVariable("y").setZone(4, 6);
    double[][][] delay = {{{0, 1}}};
    HorizonPlan shortPlan = HorizonSolver.solve(Collections.singletonList(u), Collections.singletonList(y),
        new double[] {0}, new double[1][1], delay, 1);
    HorizonPlan longPlan = HorizonSolver.solve(Collections.singletonList(u), Collections.singletonList(y),
        new double[] {0}, new double[3][1], delay, 1);
    // For the longer plan J = 2*(4-u)^2 + 3*(u-1)^2 + 1.2*u + u^2.
    assertEquals(0.4, shortPlan.getPlannedMvs()[0][0], 1e-6);
    assertEquals(20.8 / 12.0, longPlan.getPlannedMvs()[0][0], 1e-6);
    assertEquals(0, longPlan.getPredictedCvs()[0][0], 1e-9);
    assertEquals(longPlan.getPlannedMvs()[0][0], longPlan.getPredictedCvs()[2][0], 1e-9);
  }

  /** Reject explicit hard CV constraints before returning an applicable plan. */
  @Test
  void rejectsUnsupportedHardCvLimits() {
    ManipulatedVariable u = new ManipulatedVariable("u");
    ControlledVariable y = new ControlledVariable("y").setSetpoint(1).setHardConstraints(-1, 1);
    assertThrows(IllegalArgumentException.class, () -> HorizonSolver.solve(Collections.singletonList(u),
        Collections.singletonList(y), new double[] {0}, new double[2][1], new double[][][] {{{1}}}, 2));
  }
}
