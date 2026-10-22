package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Compares local root reuse with independent pair trials and verifies candidate isolation.
 *
 * @author Even Solbraa
 * @version 1.0
 */
class TPflashFixedCompositionRootReuseTest {
  /**
   * Every root pair must give the same qualified minimum for PR, SRK, and CPA with fewer phase clones.
   *
   * @throws Exception if reflective invocation fails
   */
  @Test
  void rootReusePreservesQualifiedMinimumAndCandidateAcrossModels() throws Exception {
    for (int model = 0; model < 3; model++) {
      SystemInterface fluid = fluid(model);
      double[] original = snapshot(fluid);
      AtomicInteger independentClones = new AtomicInteger();
      AtomicInteger reusedClones = new AtomicInteger();
      double expected = independentPairMinimum(withCloneCounter(fluid, independentClones, false));
      assertTrue(Double.isFinite(expected), "A qualified root pair is required for model " + model);
      assertEquals(expected, reusedPairMinimum(withCloneCounter(fluid, reusedClones, false)), 0.0);
      assertTrue(reusedClones.get() < independentClones.get(), "Root reuse must reduce phase cloning");
      assertEquals(expected, reusedPairMinimum(fluid), 0.0);
      assertArrayEquals(original, snapshot(fluid), 0.0);
    }
  }

  /**
   * Balanced endpoints must not enter the transactional root search or allocate phase clones.
   *
   * @throws Exception if reflective invocation fails
   */
  @Test
  void balancedEndpointsSkipTransactionalRootSearchAndCloning() throws Exception {
    for (int model = 0; model < 3; model++) {
      SystemInterface fluid = fluid(model);
      double[] original = snapshot(fluid);
      AtomicInteger cloneAttempts = new AtomicInteger();
      SystemInterface counted = withCloneCounter(fluid, cloneAttempts, false);
      TPflash flash = new TPflash(counted);

      Method conservativeGibbs = TPflash.class.getDeclaredMethod("conservativeNeutralTwoPhaseGibbsEnergy");
      conservativeGibbs.setAccessible(true);
      assertTrue(Double.isNaN((Double) conservativeGibbs.invoke(flash)));

      Method finalRepair = TPflash.class.getDeclaredMethod("repairInvalidMultiphaseHydrocarbonEndpoint");
      finalRepair.setAccessible(true);
      finalRepair.invoke(flash);

      assertEquals(0, cloneAttempts.get(), "Balanced model " + model + " must not clone phase roots");
      assertArrayEquals(original, snapshot(fluid), 0.0);
    }
  }

  /**
   * A mismatched composition must remain rejected rather than supplying a low non-equilibrium Gibbs value.
   *
   * @throws Exception if reflective invocation fails
   */
  @Test
  void rootReuseRetainsFugacityRejection() throws Exception {
    SystemInterface fluid = fluid(0);
    fluid.getPhase(0).getComponent(0).setx(0.6);
    fluid.getPhase(0).getComponent(1).setx(0.4);
    fluid.init(1);
    double[] original = snapshot(fluid);
    assertTrue(Double.isNaN(independentPairMinimum(fluid)));
    assertTrue(Double.isNaN(reusedPairMinimum(fluid)));
    assertArrayEquals(original, snapshot(fluid), 0.0);
  }

  /**
   * A failed trial remains retryable so another root pair can still qualify.
   *
   * @throws Exception if reflective invocation fails
   */
  @Test
  void failedCloneDoesNotSuppressLaterQualifiedPair() throws Exception {
    SystemInterface fluid = fluid(1);
    double[] original = snapshot(fluid);
    double expected = independentPairMinimum(withCloneCounter(fluid, new AtomicInteger(), true));
    assertTrue(Double.isFinite(expected));
    assertEquals(expected, reusedPairMinimum(withCloneCounter(fluid, new AtomicInteger(), true)), 0.0);
    assertArrayEquals(original, snapshot(fluid), 0.0);
  }

  /**
   * Creates a dry, nontrivial equilibrium split for one EOS family.
   *
   * @param model zero for PR, one for SRK, two for CPA
   * @return equilibrated two-phase fluid
   */
  private SystemInterface fluid(int model) {
    SystemInterface fluid = model == 0 ? new SystemPrEos(280.0, 35.0)
        : model == 1 ? new SystemSrkEos(280.0, 35.0) : new SystemSrkCPAstatoil(280.0, 35.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-heptane", 0.2);
    fluid.setMixingRule(model == 2 ? 10 : 2);
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();
    assertEquals(2, fluid.getNumberOfPhases());
    return fluid;
  }

  /**
   * Invokes the private fixed-composition search without adding a production API.
   *
   * @param fluid conservative endpoint to inspect
   * @return qualified minimum, or NaN when no pair qualifies
   * @throws Exception if reflective invocation fails
   */
  private double reusedPairMinimum(SystemInterface fluid) throws Exception {
    Method search = TPflash.class.getDeclaredMethod("minimumQualifiedFixedCompositionRootGibbsEnergy",
        SystemInterface.class);
    search.setAccessible(true);
    return (Double) search.invoke(new TPflash(fluid), fluid);
  }

  /**
   * Evaluates each pair independently, retaining the unoptimized acceptance calculation.
   *
   * @param fluid conservative endpoint to inspect
   * @return qualified minimum, or NaN when no pair qualifies
   */
  private double independentPairMinimum(SystemInterface fluid) {
    double minimum = Double.NaN;
    for (PhaseType firstRoot : new PhaseType[] {PhaseType.GAS, PhaseType.LIQUID}) {
      for (PhaseType secondRoot : new PhaseType[] {PhaseType.GAS, PhaseType.LIQUID}) {
        try {
          PhaseInterface first = fluid.getPhase(0).clone();
          PhaseInterface second = fluid.getPhase(1).clone();
          first.init(fluid.getTotalNumberOfMoles(), first.getNumberOfComponents(), 1, firstRoot, fluid.getBeta(0));
          second.init(fluid.getTotalNumberOfMoles(), second.getNumberOfComponents(), 1, secondRoot, fluid.getBeta(1));
          for (int component = 0; component < first.getNumberOfComponents(); component++) {
            first.getComponent(component).fugcoef(first);
            second.getComponent(component).fugcoef(second);
          }
          double residual = 0.0;
          for (int component = 0; component < first.getNumberOfComponents(); component++) {
            if (fluid.getPhase(0).getComponent(component).getz() <= 1.0e-50) {
              continue;
            }
            double firstLog = Math.log(Math.max(first.getComponent(component).getx(), Double.MIN_NORMAL))
                + Math.log(first.getComponent(component).getFugacityCoefficient());
            double secondLog = Math.log(Math.max(second.getComponent(component).getx(), Double.MIN_NORMAL))
                + Math.log(second.getComponent(component).getFugacityCoefficient());
            if (!Double.isFinite(firstLog) || !Double.isFinite(secondLog)) {
              residual = Double.POSITIVE_INFINITY;
              break;
            }
            residual = Math.max(residual, Math.abs(firstLog - secondLog));
          }
          double gibbs = first.getGibbsEnergy() + second.getGibbsEnergy();
          if (residual < 1.0e-8 && Double.isFinite(gibbs) && (!Double.isFinite(minimum) || gibbs < minimum)) {
            minimum = gibbs;
          }
        } catch (Exception expectedTrialFailure) {
          // An unqualified root must not prevent the remaining independent trials.
        }
      }
    }
    return minimum;
  }

  /**
   * Records live amounts, compositions, fugacity coefficients, and roots for exact isolation checks.
   *
   * @param fluid endpoint to snapshot
   * @return numerical endpoint state
   */
  private double[] snapshot(SystemInterface fluid) {
    int components = fluid.getPhase(0).getNumberOfComponents();
    double[] state = new double[2 * (4 + 3 * components)];
    int index = 0;
    for (int phaseIndex = 0; phaseIndex < 2; phaseIndex++) {
      PhaseInterface phase = fluid.getPhase(phaseIndex);
      state[index++] = fluid.getBeta(phaseIndex);
      state[index++] = phase.getZ();
      state[index++] = phase.getType().ordinal();
      state[index++] = phase.getGibbsEnergy();
      for (int component = 0; component < components; component++) {
        state[index++] = phase.getComponent(component).getx();
        state[index++] = phase.getComponent(component).getFugacityCoefficient();
        state[index++] = phase.getComponent(component).getNumberOfMolesInPhase();
      }
    }
    return state;
  }

  /**
   * Counts phase cloning and optionally injects one failure while retaining the real EOS trials.
   *
   * @param fluid real endpoint whose phases are wrapped
   * @param cloneAttempts phase-clone counter
   * @param failFirstClone whether the first clone attempt should fail
   * @return delegating system with counted phase cloning
   */
  private SystemInterface withCloneCounter(SystemInterface fluid, AtomicInteger cloneAttempts, boolean failFirstClone) {
    PhaseInterface[] phases = new PhaseInterface[2];
    for (int phaseIndex = 0; phaseIndex < phases.length; phaseIndex++) {
      PhaseInterface phase = fluid.getPhase(phaseIndex);
      phases[phaseIndex] = (PhaseInterface) Proxy.newProxyInstance(PhaseInterface.class.getClassLoader(),
          new Class<?>[] {PhaseInterface.class}, (proxy, method, arguments) -> {
            if ("clone".equals(method.getName()) && cloneAttempts.getAndIncrement() == 0 && failFirstClone) {
              throw new IllegalStateException("Injected root-trial clone failure");
            }
            return method.invoke(phase, arguments);
          });
    }
    return (SystemInterface) Proxy.newProxyInstance(SystemInterface.class.getClassLoader(),
        new Class<?>[] {SystemInterface.class}, (proxy, method, arguments) -> {
          if ("getPhase".equals(method.getName()) && arguments.length == 1 && arguments[0] instanceof Integer
              && ((Integer) arguments[0]) >= 0 && ((Integer) arguments[0]) < phases.length) {
            return phases[(Integer) arguments[0]];
          }
          return method.invoke(fluid, arguments);
        });
  }
}
