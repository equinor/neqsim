package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.component.ComponentEos;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Verifies ownership, root selection and cache boundaries shared by bounded flash trials.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class FlashPhaseStateTest {
  /** An operation-local solid check must bypass compact storage even when the system flag is false. */
  @Test
  void operationLocalSolidCheckKeepsFullCapacityTrial() {
    SystemInterface fluid = reversedSplit();
    assertFalse(fluid.doSolidPhaseCheck());
    TPflash ordinary = new TPflash(fluid, false);
    TPflash solidOperation = new TPflash(fluid, true);
    assertEquals(ordinary.findLowestGibbsEnergyPhase(), solidOperation.findLowestGibbsEnergyPhase());
    assertFalse(fluid.doSolidPhaseCheck(), "The constructor option must be tested independently of the system flag");
    for (int phase = 0; phase < 2; phase++) {
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        ComponentEos original = (ComponentEos) fluid.getPhase(phase).getComponent(component);
        ComponentEos compact = (ComponentEos) ordinary.minimumGibbsEnergySystem.getPhase(phase).getComponent(component);
        ComponentEos full = (ComponentEos) solidOperation.minimumGibbsEnergySystem.getPhase(phase)
            .getComponent(component);
        assertEquals(fluid.getNumberOfComponents(), compact.Aij.length);
        assertEquals(ThermodynamicModelSettings.MAX_NUMBER_OF_COMPONENTS, full.Aij.length);
        assertEquals(ThermodynamicModelSettings.MAX_NUMBER_OF_COMPONENTS, original.Aij.length);
        assertNotSame(original.Aij, compact.Aij);
        assertNotSame(original.Aij, full.Aij);
        assertEquals(compact.getFugacityCoefficient(), full.getFugacityCoefficient(), 0.0);
      }
    }
  }

  /** A rejected trial restores each phase's own K-values and its original physical slot. */
  @Test
  void rollbackRestoresMappedRootsAndIndependentKValuesRepeatedly() {
    SystemInterface fluid = reversedSplit();
    setDistinctKValues(fluid);
    SystemInterface expected = fluid.clone();
    FlashPhaseState saved = FlashPhaseState.captureWithGibbsEnergy(fluid);
    assertEquals(expected.getGibbsEnergy(), saved.getGibbsEnergy(), 0.0);
    assertEquals(PhaseType.LIQUID, saved.getRootType(0));
    assertEquals(PhaseType.OIL, saved.getPhaseType(0));
    assertNotEquals(saved.getRootType(0), saved.getPhaseType(0));

    for (int attempt = 0; attempt < 3; attempt++) {
      fluid.setPhaseIndex(0, 0);
      fluid.setPhaseIndex(1, 1);
      mutateSplit(fluid);
      fluid.setPhaseType(0, PhaseType.GAS);
      fluid.setPhaseType(1, PhaseType.GAS);
      fluid.init(1);
      fluid.setNumberOfPhases(1);
      int[] calls = new int[2];
      saved.restore(countInitialization(fluid, calls));
      assertEquals(1, calls[0], "A rollback initializes fugacity caches exactly once");
      assertEquals(1, calls[1], "A rollback normalizes fractions exactly once");
      assertSplit(expected, fluid, true);
      assertTrue(saved.hasSamePhaseTypes(fluid));
    }
  }

  /** Restoring logical mappings also works when phase selectors cannot be changed by the caller. */
  @Test
  void rollbackRetainsPhysicalSelectorsWhenPhaseShiftsAreDisabled() {
    SystemInterface fluid = reversedSplit();
    fluid.allowPhaseShift(false);
    setDistinctKValues(fluid);
    SystemInterface expected = fluid.clone();
    FlashPhaseState saved = FlashPhaseState.capture(fluid);
    fluid.setPhaseIndex(0, 0);
    fluid.setPhaseIndex(1, 1);
    mutateSplit(fluid);
    saved.restorePhaseValues(fluid);
    assertSplit(expected, fluid, true);
    assertTrue(!fluid.allowPhaseShift());
  }

  /** Multiphase rollback restores all retained slots when a three-phase trial removes a phase. */
  @Test
  void rollbackRestoresThreePhaseWorkspaceAfterPhaseRemoval() {
    SystemInterface fluid = reversedSplit();
    fluid.setMultiPhaseCheck(true);
    fluid.setNumberOfPhases(3);
    fluid.setBeta(0, 0.4);
    fluid.setBeta(1, 0.5);
    fluid.setBeta(2, 0.1);
    fluid.setPhaseType(2, PhaseType.LIQUID);
    fluid.getPhase(2).getComponent(0).setx(0.3);
    fluid.getPhase(2).getComponent(1).setx(0.7);
    fluid.init(1);
    setDistinctKValues(fluid);
    SystemInterface expected = fluid.clone();
    FlashPhaseState saved = FlashPhaseState.capture(fluid);
    fluid.setPhaseIndex(0, 2);
    fluid.setPhaseIndex(1, 1);
    fluid.setPhaseIndex(2, 0);
    mutateSplit(fluid);
    fluid.setNumberOfPhases(2);
    saved.restore(fluid);
    assertEquals(3, fluid.getNumberOfPhases());
    assertSplit(expected, fluid, true);
  }

  /** Cache-preserving adoption must not reevaluate an accepted root or share derivative buffers. */
  @Test
  void acceptedTransferPreservesInitializedCachesAndOwnsMutableBuffers() {
    SystemInterface source = reversedSplit();
    source.init(3);
    setDistinctKValues(source);
    SystemInterface expected = source.clone();
    SystemInterface target = source.clone();
    mutateSplit(target);
    int[] calls = new int[2];
    FlashPhaseState.copyInitializedPhases(source, countInitialization(target, calls));
    assertEquals(0, calls[0], "Accepted cached phases must not be reinitialized");
    assertEquals(0, calls[1], "Accepted cached fractions must not be normalized again");
    assertSplit(expected, target, false);
    assertSplit(expected, source, true);
    for (int phase = 0; phase < source.getNumberOfPhases(); phase++) {
      assertEquals(phase, target.getPhaseIndex(phase));
      assertNotSame(source.getPhase(phase), target.getPhase(phase));
      for (int component = 0; component < source.getNumberOfComponents(); component++) {
        assertNotSame(source.getPhase(phase).getComponent(component), target.getPhase(phase).getComponent(component));
        double derivative = source.getPhase(phase).getComponent(component).getdfugdn(0);
        assertEquals(derivative, target.getPhase(phase).getComponent(component).getdfugdn(0), 0.0);
        target.getPhase(phase).getComponent(component).setdfugdn(0, derivative + 1.0);
        assertEquals(derivative, source.getPhase(phase).getComponent(component).getdfugdn(0), 0.0);
      }
    }
  }

  /** General adoption retains the existing normalization and fugacity-initialization boundary. */
  @Test
  void ordinaryTransferReinitializesExactlyOnceAndLeavesSourceUntouched() {
    SystemInterface source = reversedSplit();
    setDistinctKValues(source);
    SystemInterface expected = source.clone();
    SystemInterface target = source.clone();
    mutateSplit(target);
    target.setNumberOfPhases(1);
    int[] calls = new int[2];
    FlashPhaseState.copyAndReinitializePhases(source, countInitialization(target, calls));
    assertEquals(1, calls[0]);
    assertEquals(1, calls[1]);
    assertSplit(expected, target, false);
    assertSplit(expected, source, true);
  }

  /** Both adoption boundaries leave a fluid reusable by unchanged and changed-state flash calls. */
  @Test
  void acceptedCopiesRemainEquivalentAcrossRepeatedAndChangedFlashes() {
    SystemInterface source = reversedSplit();
    SystemInterface cached = source.clone();
    SystemInterface rebuilt = source.clone();
    FlashPhaseState.copyInitializedPhases(source, cached);
    FlashPhaseState.copyAndReinitializePhases(source, rebuilt);
    double[] temperatures = {298.15, 298.15, 303.15, 298.15};
    for (double temperature : temperatures) {
      source.setTemperature(temperature);
      cached.setTemperature(temperature);
      rebuilt.setTemperature(temperature);
      new ThermodynamicOperations(source).TPflash();
      new ThermodynamicOperations(cached).TPflash();
      new ThermodynamicOperations(rebuilt).TPflash();
      assertSplit(source, cached, false);
      assertSplit(source, rebuilt, false);
      assertEquals(source.getGibbsEnergy(), cached.getGibbsEnergy(), 1.0e-8);
      assertEquals(source.getGibbsEnergy(), rebuilt.getGibbsEnergy(), 1.0e-8);
    }
  }

  /**
   * Creates an equilibrated hydrocarbon split with reversed physical mapping and a liquid root selector.
   *
   * @return initialized split whose liquid classification differs from its configured selector
   */
  private SystemInterface reversedSplit() {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 0.7);
    fluid.addComponent("n-heptane", 0.3);
    fluid.setMixingRule("classic");
    new ThermodynamicOperations(fluid).TPflash();
    assertEquals(2, fluid.getNumberOfPhases());
    assertEquals(PhaseType.GAS, fluid.getPhase(0).getType());
    assertEquals(PhaseType.OIL, fluid.getPhase(1).getType());
    fluid.setPhaseType(1, PhaseType.LIQUID);
    int first = fluid.getPhaseIndex(0);
    fluid.setPhaseIndex(0, fluid.getPhaseIndex(1));
    fluid.setPhaseIndex(1, first);
    fluid.init(1);
    return fluid;
  }

  /**
   * Gives the two phases different retained iteration estimates to detect a one-vector rollback.
   *
   * @param fluid split whose K-values should be distinguishable
   */
  private void setDistinctKValues(SystemInterface fluid) {
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        fluid.getPhase(phase).getComponent(component).setK(2.0 + 3.0 * phase + component);
      }
    }
  }

  /**
   * Simulates phase-value mutations from an unsuccessful refinement without replacing its physical slots.
   *
   * @param fluid split to perturb
   */
  private void mutateSplit(SystemInterface fluid) {
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      fluid.setBeta(phase, 0.5);
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        fluid.getPhase(phase).getComponent(component).setx(0.5);
        fluid.getPhase(phase).getComponent(component).setK(20.0 + phase + component);
      }
    }
  }

  /**
   * Counts initialization boundaries while executing the real fluid's methods.
   *
   * @param fluid real thermodynamic system
   * @param calls counters for initialization and normalization, respectively
   * @return forwarding system with observable boundary calls
   */
  private SystemInterface countInitialization(SystemInterface fluid, int[] calls) {
    return (SystemInterface) Proxy.newProxyInstance(SystemInterface.class.getClassLoader(),
        new Class<?>[] {SystemInterface.class}, (proxy, method, arguments) -> {
          if ("init".equals(method.getName())) {
            calls[0]++;
          } else if ("normalizeBeta".equals(method.getName())) {
            calls[1]++;
          }
          try {
            return method.invoke(fluid, arguments);
          } catch (InvocationTargetException ex) {
            throw ex.getCause();
          }
        });
  }

  /**
   * Compares physical equilibrium and retained iteration values after transfer or rollback.
   *
   * @param expected independent reference split
   * @param actual transferred or restored split
   * @param compareMapping whether physical slots must also be unchanged
   */
  private void assertSplit(SystemInterface expected, SystemInterface actual, boolean compareMapping) {
    assertEquals(expected.getNumberOfPhases(), actual.getNumberOfPhases());
    for (int phase = 0; phase < expected.getNumberOfPhases(); phase++) {
      if (compareMapping) {
        assertEquals(expected.getPhaseIndex(phase), actual.getPhaseIndex(phase));
      }
      assertEquals(expected.getPhaseType(phase), actual.getPhaseType(phase));
      assertEquals(expected.getPhase(phase).getType(), actual.getPhase(phase).getType());
      assertEquals(expected.getBeta(phase), actual.getBeta(phase), 1.0e-12);
      assertEquals(expected.getPhase(phase).getZ(), actual.getPhase(phase).getZ(), 1.0e-12);
      for (int component = 0; component < expected.getNumberOfComponents(); component++) {
        assertEquals(expected.getPhase(phase).getComponent(component).getx(),
            actual.getPhase(phase).getComponent(component).getx(), 1.0e-12);
        assertEquals(expected.getPhase(phase).getComponent(component).getK(),
            actual.getPhase(phase).getComponent(component).getK(), 1.0e-12);
        assertEquals(expected.getPhase(phase).getComponent(component).getLogFugacityCoefficient(),
            actual.getPhase(phase).getComponent(component).getLogFugacityCoefficient(), 1.0e-12);
      }
    }
  }
}
