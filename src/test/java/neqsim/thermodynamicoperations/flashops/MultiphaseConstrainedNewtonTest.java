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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;

/**
 * Local multiphase Newton tests on a physically conserved, fixed fluid active set.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class MultiphaseConstrainedNewtonTest {
  /**
   * The same constrained equations converge with every logical aqueous placement.
   *
   * @param aqueousPhase logical aqueous phase
   */
  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void convergesWithPermutedAqueousHost(int aqueousPhase) {
    SystemInterface fluid = seed(aqueousPhase);
    double[] inventory = FluidEquilibriumChecks.inventory(fluid);
    SysNewtonRhapsonTPflash solver = new SysNewtonRhapsonTPflash(fluid, 3, fluid.getNumberOfComponents());
    assertEquals(6, solver.neq);
    for (int iteration = 0; iteration < 12 && residual(fluid) > 1e-8; iteration++) {
      double before = fluid.getGibbsEnergy();
      solver.solve();
      assertTrue(fluid.getGibbsEnergy() <= before + 128.0 * Math.ulp(Math.abs(before)));
      assertInventory(fluid, aqueousPhase);
    }
    FluidEquilibriumChecks.validate(fluid, inventory);
    assertTrue(residual(fluid) < 1e-8);
  }

  /**
   * Every reduced Hessian block differentiates the model chemical-potential residual.
   *
   * @throws Exception if reflective access fails
   */
  @Test
  void fullReducedHessianMatchesConservedTransferDifferences() throws Exception {
    SystemInterface fluid = seed(1);
    SysNewtonRhapsonTPflash solver = new SysNewtonRhapsonTPflash(fluid, 3, fluid.getNumberOfComponents());
    solver.init();
    solver.setJac();
    DMatrixRMaj analytical = ((DMatrixRMaj) field("jacMatrix").get(solver)).copy();
    double[] amounts = (double[]) field("uVector").get(solver);
    int[] components = (int[]) field("variableComponents").get(solver);
    int[] phases = (int[]) field("variablePhases").get(solver);
    Method reconstruct = SysNewtonRhapsonTPflash.class.getDeclaredMethod("reconstructReferenceAmounts", double[].class);
    reconstruct.setAccessible(true);
    int count = fluid.getNumberOfComponents();
    for (int column = 0; column < solver.neq; column++) {
      int index = phases[column] * count + components[column];
      double initial = amounts[index];
      double step = 1e-4 * Math.min(initial, amounts[2 * count + components[column]]);
      amounts[index] = initial + step;
      reconstruct.invoke(solver, (Object) amounts);
      solver.init();
      solver.setfvec();
      DMatrixRMaj plus = ((DMatrixRMaj) field("fvecVector").get(solver)).copy();
      amounts[index] = initial - step;
      reconstruct.invoke(solver, (Object) amounts);
      solver.init();
      solver.setfvec();
      DMatrixRMaj minus = ((DMatrixRMaj) field("fvecVector").get(solver)).copy();
      amounts[index] = initial;
      reconstruct.invoke(solver, (Object) amounts);
      for (int row = 0; row < solver.neq; row++) {
        double finiteDifference = (plus.get(row, 0) - minus.get(row, 0)) / (2.0 * step);
        assertEquals(finiteDifference, analytical.get(row, column), 2e-5 * Math.max(1.0, Math.abs(finiteDifference)),
            "Conserved-transfer Hessian entry " + row + ", " + column);
      }
    }
    solver.init();
  }

  /**
   * An infeasible full direction leaves all three phase inventories at the accepted state.
   *
   * @throws Exception if reflective access fails
   */
  @Test
  void failedMultiphaseDirectionRestoresAllPhaseAmounts() throws Exception {
    SystemInterface fluid = seed(2);
    SysNewtonRhapsonTPflash solver = new SysNewtonRhapsonTPflash(fluid, 3, fluid.getNumberOfComponents());
    solver.init();
    double[] accepted = ((double[]) field("uVector").get(solver)).clone();
    Object fake = Proxy.newProxyInstance(LinearSolverDense.class.getClassLoader(),
        new Class<?>[] {LinearSolverDense.class}, (proxy, method, arguments) -> {
          if ("setA".equals(method.getName())) {
            return true;
          }
          if ("solve".equals(method.getName())) {
            DMatrixRMaj gradient = (DMatrixRMaj) arguments[0];
            DMatrixRMaj correction = (DMatrixRMaj) arguments[1];
            for (int row = 0; row < gradient.numRows; row++) {
              correction.set(row, 0, 1e10 * gradient.get(row, 0));
            }
          }
          return null;
        });
    field("linearSolver").set(solver, fake);
    assertThrows(IllegalStateException.class, solver::solve);
    for (int phase = 0; phase < 3; phase++) {
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        assertEquals(accepted[phase * fluid.getNumberOfComponents() + component],
            fluid.getBeta(phase) * fluid.getPhase(phase).getComponent(component).getx(), 1e-15);
      }
    }
    assertInventory(fluid, 2);
  }

  /** A caller must explicitly rebind after changing the number of active phases. */
  @Test
  void changedActivePhaseCountIsRejectedBeforeMutation() {
    SystemInterface fluid = seed(1);
    SysNewtonRhapsonTPflash solver = new SysNewtonRhapsonTPflash(fluid, 3, fluid.getNumberOfComponents());
    fluid.setNumberOfPhases(2);
    double beta = fluid.getBeta(0);
    double composition = fluid.getPhase(0).getComponent(0).getx();
    assertThrows(IllegalStateException.class, solver::solve);
    assertEquals(beta, fluid.getBeta(0));
    assertEquals(composition, fluid.getPhase(0).getComponent(0).getx());
  }

  /**
   * Creates a projected split from a difficult three-phase propane/brine flash iterate.
   *
   * @param aqueousPhase requested logical aqueous phase
   * @return initialized, conserved three-phase fluid
   */
  private static SystemInterface seed(int aqueousPhase) {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, 50.0);
    fluid.addComponent("methane", 0.1);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("water", 1.0);
    fluid.addComponent("Na+", 0.018);
    fluid.addComponent("Cl-", 0.018);
    fluid.setMixingRule(10);
    fluid.setMultiPhaseCheck(true);
    fluid.init(0);
    fluid.setNumberOfPhases(3);
    double[] beta = {0.1169437995175415, 0.8741375144354216, 0.008918686047036795};
    double[][] fractions = {{0.6666687077285147, 0.3326448884381848, 6.86403833300461e-4, 0.0, 0.0},
        {7.233577669817025e-4, 4.320494762946061e-5, 0.9645087732213201, 0.017362332032034328, 0.017362332032034328},
        {0.19733237803757767, 0.8023723820437795, 2.9523991864286657e-4, 0.0, 0.0}};
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      double sum = 0.0;
      for (int phase = 0; phase < 3; phase++) {
        fractions[phase][component] *= beta[phase];
        sum += fractions[phase][component];
      }
      for (int phase = 0; phase < 3; phase++) {
        fractions[phase][component] *= fluid.getComponent(component).getz() / sum;
      }
    }
    for (int phase = 0; phase < 3; phase++) {
      int original = phase == aqueousPhase ? 1 : phase == 1 ? aqueousPhase : phase;
      fluid.setPhaseIndex(phase, original);
      fluid.setPhaseType(phase, original == 0 ? PhaseType.GAS : original == 1 ? PhaseType.AQUEOUS : PhaseType.OIL);
      double phaseAmount = 0.0;
      for (double amount : fractions[original]) {
        phaseAmount += amount;
      }
      fluid.setBeta(phase, phaseAmount);
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        fluid.getPhase(phase).getComponent(component).setx(fractions[original][component] / phaseAmount);
      }
    }
    fluid.init(3);
    return fluid;
  }

  /**
   * Checks species inventory, normalization, ion confinement and phase charge after every step.
   *
   * @param fluid current split
   * @param aqueousPhase captured ionic host
   */
  private static void assertInventory(SystemInterface fluid, int aqueousPhase) {
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      double recovered = 0.0;
      for (int phase = 0; phase < 3; phase++) {
        double fraction = fluid.getPhase(phase).getComponent(component).getx();
        recovered += fluid.getBeta(phase) * fraction;
        if (component >= 3 && phase != aqueousPhase) {
          assertEquals(0.0, fraction);
        }
      }
      assertEquals(fluid.getComponent(component).getz(), recovered, 1e-15);
    }
    double betaSum = 0.0;
    for (int phase = 0; phase < 3; phase++) {
      betaSum += fluid.getBeta(phase);
      double sum = 0.0;
      double charge = 0.0;
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        double fraction = fluid.getPhase(phase).getComponent(component).getx();
        sum += fraction;
        charge += fraction * fluid.getComponent(component).getIonicCharge();
      }
      assertEquals(1.0, sum, 1e-14);
      assertEquals(0.0, charge, 1e-15);
    }
    assertEquals(1.0, betaSum, 1e-14);
  }

  /**
   * Evaluates the maximum molecular equilibrium residual across the active phases.
   *
   * @param fluid initialized current split
   * @return maximum absolute log-fugacity mismatch
   */
  private static double residual(SystemInterface fluid) {
    double maximum = 0.0;
    for (int component = 0; component < 3; component++) {
      double reference = Math.log(fluid.getPhase(0).getComponent(component).getx())
          + fluid.getPhase(0).getComponent(component).getLogFugacityCoefficient();
      for (int phase = 1; phase < 3; phase++) {
        maximum = Math.max(maximum, Math.abs(reference - Math.log(fluid.getPhase(phase).getComponent(component).getx())
            - fluid.getPhase(phase).getComponent(component).getLogFugacityCoefficient()));
      }
    }
    return maximum;
  }

  /**
   * Opens a numerical state field for independent derivative and rollback verification.
   *
   * @param name field name
   * @return accessible field
   * @throws Exception if the field is unavailable
   */
  private static Field field(String name) throws Exception {
    Field result = SysNewtonRhapsonTPflash.class.getDeclaredField(name);
    result.setAccessible(true);
    return result;
  }
}
