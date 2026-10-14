package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.ejml.data.DMatrixRMaj;
import org.ejml.interfaces.linsol.LinearSolverDense;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Verifies feasible accepted Newton steps and transactional rejection at the two-phase boundary.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class TPflashNewtonSafeguardTest {
  /** A local Newton correction must improve the physical residual instead of taking an untested tiny step. */
  @Test
  void localCorrectionUsesTheTrialGibbsEnergy() {
    SystemInterface fluid = perturbedFluid();
    double before = residual(fluid);
    double beforeQ = gibbs(fluid);
    SysNewtonRhapsonTPflash solver = new SysNewtonRhapsonTPflash(fluid, 2, 2);
    solver.solve();
    solver.init();
    assertTrue(residual(fluid) < before * 0.1, "A well-conditioned local Newton step must reduce the residual");
    assertTrue(gibbs(fluid) < beforeQ, "The accepted physical Gibbs objective must decrease");
    assertBalanced(fluid);
  }

  /**
   * The reduced Gibbs objective must have the physical fugacity residual as its directional derivative.
   *
   * @throws Exception if reflective access to the solver fails
   */
  @Test
  void trialObjectiveHasTheThermodynamicDirectionalDerivative() throws Exception {
    SystemInterface fluid = perturbedFluid();
    SysNewtonRhapsonTPflash solver = new SysNewtonRhapsonTPflash(fluid, 2, 2);
    solver.init();
    double expectedDerivative = 0.0;
    double[] amounts = new double[2];
    double[] direction = {0.1, -0.1};
    for (int component = 0; component < 2; component++) {
      amounts[component] = fluid.getBeta(0) * fluid.getPhase(0).getComponent(component).getx();
      expectedDerivative += direction[component] * (Math.log(fluid.getPhase(0).getComponent(component).getx())
          + fluid.getPhase(0).getComponent(component).getLogFugacityCoefficient()
          - Math.log(fluid.getPhase(1).getComponent(component).getx())
          - fluid.getPhase(1).getComponent(component).getLogFugacityCoefficient());
    }
    Method trial = SysNewtonRhapsonTPflash.class.getDeclaredMethod("setTrialAndComputeFugacities", double[].class);
    trial.setAccessible(true);
    Method objective;
    try {
      objective = SysNewtonRhapsonTPflash.class.getDeclaredMethod("computeQ", double[].class);
    } catch (NoSuchMethodException baseline) {
      objective = SysNewtonRhapsonTPflash.class.getDeclaredMethod("computeQ");
    }
    objective.setAccessible(true);
    double step = 1.0e-6;
    double[] values = new double[2];
    for (int sign = 0; sign < 2; sign++) {
      double[] perturbed = amounts.clone();
      for (int component = 0; component < 2; component++) {
        perturbed[component] += (sign == 0 ? step : -step) * direction[component];
      }
      trial.invoke(solver, (Object) perturbed);
      values[sign] = objective.getParameterTypes().length == 0 ? (Double) objective.invoke(solver)
          : (Double) objective.invoke(solver, (Object) perturbed);
    }
    assertEquals(expectedDerivative, (values[0] - values[1]) / (2.0 * step), 1.0e-7);
  }

  /** Rebinding after a caller's fallback must refresh the solver's retained component inventory. */
  @Test
  void reboundSolverStartsFromTheLiveSplit() {
    SysNewtonRhapsonTPflash reused = new SysNewtonRhapsonTPflash(perturbedFluid(), 2, 2);
    reused.solve();
    SystemInterface live = perturbedFluid();
    SystemInterface reference = live.clone();
    reused.setSystem(live);
    SysNewtonRhapsonTPflash fresh = new SysNewtonRhapsonTPflash(reference, 2, 2);
    fresh.solve();
    reused.solve();
    assertSnapshot(snapshot(reference), live);
  }

  /**
   * An exhausted feasibility search must restore both public and solver-owned state.
   *
   * @throws Exception if reflective access to the solver fails
   */
  @Test
  void exhaustedSearchNeverCommitsAnUntestedStep() throws Exception {
    SystemInterface fluid = perturbedFluid();
    SysNewtonRhapsonTPflash solver = new SysNewtonRhapsonTPflash(fluid, 2, 2);
    solver.init();
    double[] before = snapshot(fluid);
    injectStep(solver, 1.0e6);
    assertThrows(IllegalStateException.class, solver::solve);
    assertSnapshot(before, fluid);
    solver.init();
    assertSnapshot(before, fluid);
  }

  /** Failed feasible EOS trials must roll back the mutated public state before caller fallback. */
  @Test
  void failedFeasibleTrialsRestorePhaseStateAndRetainedAmounts() {
    SystemInterface fluid = perturbedFluid();
    int[] trials = {0};
    SystemInterface failing = (SystemInterface) Proxy.newProxyInstance(SystemInterface.class.getClassLoader(),
        new Class<?>[] {SystemInterface.class}, (proxy, method, args) -> {
          Object value = method.invoke(fluid, args);
          if ("init".equals(method.getName()) && args != null && args.length == 2
              && Integer.valueOf(1).equals(args[0])) {
            trials[0]++;
            throw new IllegalStateException("Injected EOS failure after trial mutation");
          }
          return value;
        });
    SysNewtonRhapsonTPflash solver = new SysNewtonRhapsonTPflash(failing, 2, 2);
    solver.init();
    double[] before = snapshot(fluid);
    assertThrows(IllegalStateException.class, solver::solve);
    assertEquals(8, trials[0]);
    assertSnapshot(before, fluid);
    solver.init();
    assertSnapshot(before, fluid);
  }

  /**
   * A failed factorization must not consume a stale linear-solver direction.
   *
   * @throws Exception if reflective access to the solver fails
   */
  @Test
  void singularJacobianIsRejectedBeforeSolving() throws Exception {
    SystemInterface fluid = perturbedFluid();
    SysNewtonRhapsonTPflash solver = new SysNewtonRhapsonTPflash(fluid, 2, 2);
    solver.init();
    double[] before = snapshot(fluid);
    Object linear = Proxy.newProxyInstance(LinearSolverDense.class.getClassLoader(),
        new Class<?>[] {LinearSolverDense.class}, (proxy, method, args) -> {
          if ("setA".equals(method.getName())) {
            return false;
          }
          throw new AssertionError("A failed factorization must stop before " + method.getName());
        });
    Field field = SysNewtonRhapsonTPflash.class.getDeclaredField("linearSolver");
    field.setAccessible(true);
    field.set(solver, linear);
    assertThrows(IllegalStateException.class, solver::solve);
    assertSnapshot(before, fluid);
  }

  /**
   * A direction of increasing Gibbs energy cannot satisfy a valid descent search.
   *
   * @throws Exception if reflective access to the solver fails
   */
  @Test
  void nonDescentDirectionIsRejectedWithoutChangingTheSplit() throws Exception {
    SystemInterface fluid = perturbedFluid();
    SysNewtonRhapsonTPflash solver = new SysNewtonRhapsonTPflash(fluid, 2, 2);
    solver.init();
    double[] before = snapshot(fluid);
    injectStep(solver, -1.0);
    assertThrows(IllegalStateException.class, solver::solve);
    assertSnapshot(before, fluid);
  }

  /**
   * Non-finite Newton directions must not enter the accepted iteration state.
   *
   * @throws Exception if reflective access to the solver fails
   */
  @Test
  void nonFiniteDirectionIsRejectedWithoutChangingTheSplit() throws Exception {
    SystemInterface fluid = perturbedFluid();
    SysNewtonRhapsonTPflash solver = new SysNewtonRhapsonTPflash(fluid, 2, 2);
    solver.init();
    double[] before = snapshot(fluid);
    injectStep(solver, Double.NaN);
    assertThrows(IllegalStateException.class, solver::solve);
    assertSnapshot(before, fluid);
  }

  /**
   * Injects a solver direction proportional to the actual gradient to exercise exceptional acceptance paths.
   *
   * @param solver Newton solver
   * @param scale gradient multiplier used as the candidate direction
   * @throws Exception if reflection fails
   */
  private void injectStep(SysNewtonRhapsonTPflash solver, double scale) throws Exception {
    Object linear = Proxy.newProxyInstance(LinearSolverDense.class.getClassLoader(),
        new Class<?>[] {LinearSolverDense.class}, (proxy, method, args) -> {
          if ("setA".equals(method.getName())) {
            return true;
          }
          if ("solve".equals(method.getName())) {
            DMatrixRMaj gradient = (DMatrixRMaj) args[0];
            DMatrixRMaj step = (DMatrixRMaj) args[1];
            for (int row = 0; row < gradient.numRows; row++) {
              step.set(row, 0, scale * gradient.get(row, 0));
            }
            return null;
          }
          throw new UnsupportedOperationException(method.getName());
        });
    Field field = SysNewtonRhapsonTPflash.class.getDeclaredField("linearSolver");
    field.setAccessible(true);
    field.set(solver, linear);
  }

  /**
   * Creates a conserved perturbation of a real SRK gas/oil split in its local Newton basin.
   *
   * @return finite two-phase methane/heptane state
   */
  private SystemInterface perturbedFluid() {
    SystemInterface fluid = new SystemSrkEos(300.0, 20.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("n-heptane", 0.5);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    assertEquals(2, fluid.getNumberOfPhases());
    double[] amount = new double[2];
    for (int component = 0; component < 2; component++) {
      amount[component] = fluid.getBeta(0) * fluid.getPhase(0).getComponent(component).getx();
    }
    amount[0] -= 0.002;
    amount[1] += 0.0001;
    double beta = amount[0] + amount[1];
    fluid.setBeta(0, beta);
    fluid.setBeta(1, 1.0 - beta);
    for (int component = 0; component < 2; component++) {
      fluid.getPhase(0).getComponent(component).setx(amount[component] / beta);
      fluid.getPhase(1).getComponent(component).setx((0.5 - amount[component]) / (1.0 - beta));
    }
    fluid.init(3);
    return fluid;
  }

  /**
   * Calculates the maximum physical log-fugacity mismatch.
   *
   * @param fluid state to check
   * @return maximum residual
   */
  private double residual(SystemInterface fluid) {
    double maximum = 0.0;
    for (int component = 0; component < 2; component++) {
      double first = fluid.getPhase(0).getComponent(component).getx()
          * fluid.getPhase(0).getComponent(component).getFugacityCoefficient();
      double second = fluid.getPhase(1).getComponent(component).getx()
          * fluid.getPhase(1).getComponent(component).getFugacityCoefficient();
      maximum = Math.max(maximum, Math.abs(Math.log(first / second)));
    }
    return maximum;
  }

  /**
   * Independently evaluates the reduced Gibbs objective using the actual phase inventories.
   *
   * @param fluid state to check
   * @return dimensionless molar Gibbs objective
   */
  private double gibbs(SystemInterface fluid) {
    double value = 0.0;
    for (int phase = 0; phase < 2; phase++) {
      for (int component = 0; component < 2; component++) {
        double x = fluid.getPhase(phase).getComponent(component).getx();
        value += fluid.getBeta(phase) * x
            * Math.log(x * fluid.getPhase(phase).getComponent(component).getFugacityCoefficient());
      }
    }
    return value;
  }

  /**
   * Captures the physical split and its K values.
   *
   * @param fluid state to capture
   * @return snapshot
   */
  private double[] snapshot(SystemInterface fluid) {
    double[] values = new double[20];
    int index = 0;
    for (int phase = 0; phase < 2; phase++) {
      values[index++] = fluid.getBeta(phase);
      values[index++] = fluid.getPhaseIndex(phase);
      values[index++] = fluid.getPhaseType(phase).ordinal();
      values[index++] = fluid.getPhase(phase).getZ();
      for (int component = 0; component < 2; component++) {
        values[index++] = fluid.getPhase(phase).getComponent(component).getx();
        values[index++] = fluid.getPhase(phase).getComponent(component).getK();
        values[index++] = fluid.getPhase(phase).getComponent(component).getFugacityCoefficient();
      }
    }
    return values;
  }

  /**
   * Checks that a rejected operation retained its complete physical split.
   *
   * @param before original split
   * @param fluid returned state
   */
  private void assertSnapshot(double[] before, SystemInterface fluid) {
    double[] after = snapshot(fluid);
    for (int value = 0; value < before.length; value++) {
      assertEquals(before[value], after[value], 1.0e-14, "Snapshot value " + value);
    }
    assertBalanced(fluid);
  }

  /**
   * Checks component conservation and phase normalization.
   *
   * @param fluid returned state
   */
  private void assertBalanced(SystemInterface fluid) {
    assertEquals(1.0, fluid.getBeta(0) + fluid.getBeta(1), 1.0e-14);
    for (int component = 0; component < 2; component++) {
      double inventory = 0.0;
      for (int phase = 0; phase < 2; phase++) {
        inventory += fluid.getBeta(phase) * fluid.getPhase(phase).getComponent(component).getx();
      }
      assertEquals(fluid.getComponent(component).getz(), inventory, 1.0e-14);
    }
  }
}
