package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemThermo;

/**
 * Qualifies fixed-component trial storage without weakening general clone ownership or capacity.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class StabilityTrialCopyTest {
  /**
   * Checks active derivative values and independently owned compact arrays through every copy layer.
   *
   * @param pr whether to use Peng-Robinson instead of SRK
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void compactTrialsRetainValuesAndNormalClonesRetainFullCapacity(boolean pr) {
    SystemThermo source = fluid(pr);
    int count = source.getNumberOfComponents();
    for (int phase = 0; phase < 2; phase++) {
      for (int component = 0; component < count; component++) {
        for (double[] buffer : buffers((ComponentEos) source.getPhase(phase).getComponent(component))) {
          for (int index = 0; index < buffer.length; index++) {
            buffer[index] = 1000.0 * phase + 100.0 * component + index;
          }
        }
      }
    }
    SystemThermo trial = source.copyForStabilityTrial();
    SystemThermo general = source.clone();
    for (int phase = 0; phase < 2; phase++) {
      for (int component = 0; component < count; component++) {
        double[][] original = buffers((ComponentEos) source.getPhase(phase).getComponent(component));
        double[][] compact = buffers((ComponentEos) trial.getPhase(phase).getComponent(component));
        double[][] normal = buffers((ComponentEos) general.getPhase(phase).getComponent(component));
        for (int buffer = 0; buffer < original.length; buffer++) {
          assertEquals(count, compact[buffer].length);
          assertEquals(ThermodynamicModelSettings.MAX_NUMBER_OF_COMPONENTS, normal[buffer].length);
          assertNotSame(original[buffer], compact[buffer]);
          assertNotSame(original[buffer], normal[buffer]);
          assertArrayEquals(original[buffer], normal[buffer], 0.0);
          for (int index = 0; index < count; index++) {
            assertEquals(original[buffer][index], compact[buffer][index], 0.0);
          }
          compact[buffer][0] = -11.0;
          normal[buffer][normal[buffer].length - 1] = -12.0;
          assertEquals(1000.0 * phase + 100.0 * component, original[buffer][0], 0.0);
          assertEquals(1000.0 * phase + 100.0 * component + original[buffer].length - 1,
              original[buffer][original[buffer].length - 1], 0.0);
        }
      }
    }
  }

  /**
   * Checks that model initialization and a tuned attractive term behave exactly as an ordinary detached clone.
   *
   * @param pr whether to use Peng-Robinson instead of SRK
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void trialInitializationMatchesNormalCopyWithoutChangingSource(boolean pr) {
    SystemThermo source = fluid(pr);
    double originalTc = source.getComponent(0).getTC();
    SystemThermo trial = source.copyForStabilityTrial();
    SystemThermo general = source.clone();
    for (int phase = 0; phase < 2; phase++) {
      trial.getPhase(phase).getComponent(0).setTC(originalTc + 3.0);
      general.getPhase(phase).getComponent(0).setTC(originalTc + 3.0);
    }
    for (int level : new int[] {0, 1, 3}) {
      trial.init(level);
      general.init(level);
      for (int phase = 0; phase < 2; phase++) {
        assertEquals(general.getPhase(phase).getZ(), trial.getPhase(phase).getZ(), 0.0);
        for (int component = 0; component < source.getNumberOfComponents(); component++) {
          ComponentEos expected = (ComponentEos) general.getPhase(phase).getComponent(component);
          ComponentEos actual = (ComponentEos) trial.getPhase(phase).getComponent(component);
          assertEquals(expected.getFugacityCoefficient(), actual.getFugacityCoefficient(), 0.0);
          double[][] expectedBuffers = buffers(expected);
          double[][] actualBuffers = buffers(actual);
          for (int buffer = 0; buffer < expectedBuffers.length; buffer++) {
            for (int index = 0; index < source.getNumberOfComponents(); index++) {
              assertEquals(expectedBuffers[buffer][index], actualBuffers[buffer][index], 0.0);
            }
          }
        }
      }
    }
    assertEquals(originalTc, source.getComponent(0).getTC(), 0.0);
  }

  /** Checks that the public entry points retain specialized and unknown virtual clone behavior. */
  @Test
  void specializedAndUnknownTypesUseNormalCopies() {
    SystemThermo cpa = new SystemSrkCPAstatoil(300.0, 50.0);
    cpa.addComponent("methane", 0.9);
    cpa.addComponent("water", 0.1);
    cpa.setMixingRule(10);
    assertFullCapacity(cpa.copyForStabilityTrial());
    PhaseEos phase = (PhaseEos) cpa.getPhase(0);
    assertEquals(ThermodynamicModelSettings.MAX_NUMBER_OF_COMPONENTS,
        ((Component) phase.copyForStabilityTrial().getComponent(0)).dfugdx.length);
    ComponentEos component = (ComponentEos) phase.getComponent(0);
    assertEquals(ThermodynamicModelSettings.MAX_NUMBER_OF_COMPONENTS, component.copyForStabilityTrial(2).dfugdx.length);
    SystemThermo unknown = new UnqualifiedSrkSystem();
    unknown.addComponent("methane", 0.8);
    unknown.addComponent("ethane", 0.2);
    unknown.setMixingRule("classic");
    assertEquals(UnqualifiedSrkSystem.class, unknown.copyForStabilityTrial().getClass());
    assertFullCapacity(unknown.copyForStabilityTrial());
  }

  /** Checks option gates without executing a specialized calculation on an unqualified compact workspace. */
  @Test
  void unsupportedOptionsUseNormalCopies() {
    SystemThermo numeric = fluid(false);
    numeric.setNumericDerivatives(true);
    assertFullCapacity(numeric.copyForStabilityTrial());
    SystemThermo locked = fluid(false);
    locked.allowPhaseShift(false);
    assertFullCapacity(locked.copyForStabilityTrial());
    SystemThermo forced = fluid(false);
    forced.setForcePhaseTypes(true);
    assertFullCapacity(forced.copyForStabilityTrial());
    SystemThermo multiphase = fluid(false);
    multiphase.setMultiPhaseCheck(true);
    multiphase.setNumberOfPhases(3);
    assertFullCapacity(multiphase.copyForStabilityTrial());
  }

  /** Checks that supported permutations remain compact while unsupported layouts retain general copying. */
  @Test
  void phaseMappingsAndComponentIndicesAreQualifiedBeforeCompaction() {
    SystemThermo swapped = fluid(false);
    swapped.setPhaseIndex(0, 1);
    swapped.setPhaseIndex(1, 0);
    assertEquals(3, ((Component) swapped.copyForStabilityTrial().getPhase(0).getComponent(0)).dfugdx.length);
    SystemThermo repeatedSlot = fluid(false);
    repeatedSlot.setPhaseIndex(1, 0);
    assertFullCapacity(repeatedSlot.copyForStabilityTrial());
    SystemThermo unexpectedComponentIndex = fluid(false);
    unexpectedComponentIndex.getPhase(0).getComponent(2).setComponentNumber(3);
    assertFullCapacity(unexpectedComponentIndex.copyForStabilityTrial());
    assertEquals(ThermodynamicModelSettings.MAX_NUMBER_OF_COMPONENTS,
        ((Component) ((PhaseEos) unexpectedComponentIndex.getPhase(0)).copyForStabilityTrial()
            .getComponent(2)).dfugdx.length);
  }

  /**
   * Checks that a dormant third cubic slot is detached and fixed-size without changing initialized trial results.
   *
   * @param pr whether to use Peng-Robinson instead of SRK
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void thirdStoredSlotHasOwnedBuffersAndMatchesNormalTrial(boolean pr) {
    SystemThermo source = fluid(pr);
    source.setMultiPhaseCheck(true);
    assertEquals(2, source.getNumberOfPhases());
    assertEquals(3, source.getMaxNumberOfPhases());
    int count = source.getNumberOfComponents();
    SystemThermo trial = source.copyForStabilityTrial();
    SystemThermo general = source.clone();
    for (int slot = 0; slot < source.getMaxNumberOfPhases(); slot++) {
      for (int component = 0; component < count; component++) {
        ComponentEos originalComponent = (ComponentEos) source.getPhases()[slot].getComponent(component);
        double[][] original = buffers(originalComponent);
        double[][] compact = buffers((ComponentEos) trial.getPhases()[slot].getComponent(component));
        double[][] normal = buffers((ComponentEos) general.getPhases()[slot].getComponent(component));
        for (int buffer = 0; buffer < original.length; buffer++) {
          assertEquals(count, compact[buffer].length);
          assertEquals(ThermodynamicModelSettings.MAX_NUMBER_OF_COMPONENTS, normal[buffer].length);
          assertNotSame(original[buffer], compact[buffer]);
          assertNotSame(original[buffer], normal[buffer]);
          for (int index = 0; index < count; index++) {
            assertEquals(original[buffer][index], compact[buffer][index], 0.0);
          }
          double originalValue = original[buffer][0];
          compact[buffer][0] = -111.0;
          assertEquals(originalValue, original[buffer][0], 0.0);
          compact[buffer][0] = originalValue;
        }
      }
    }
    for (int level : new int[] {0, 1, 3}) {
      trial.init(level);
      general.init(level);
      for (int phase = 0; phase < trial.getNumberOfPhases(); phase++) {
        assertEquals(general.getPhase(phase).getZ(), trial.getPhase(phase).getZ(), 0.0);
        for (int component = 0; component < count; component++) {
          assertEquals(general.getPhase(phase).getComponent(component).getFugacityCoefficient(),
              trial.getPhase(phase).getComponent(component).getFugacityCoefficient(), 0.0);
        }
      }
    }
    source.setPhaseIndex(0, 2);
    assertEquals(count, ((Component) source.copyForStabilityTrial().getPhase(0).getComponent(0)).dfugdx.length);
    source.getPhases()[2].getComponent(0).setComponentNumber(count);
    assertFullCapacity(source.copyForStabilityTrial());
  }

  /** Checks the component count before an internal copy can truncate an active derivative index. */
  @Test
  void invalidTrialCapacityIsRejected() {
    ComponentEos component = (ComponentEos) fluid(false).getComponent(2);
    assertThrows(IllegalArgumentException.class, () -> component.copyForStabilityTrial(2));
    assertThrows(IllegalArgumentException.class, () -> component.copyForStabilityTrial(0));
    assertThrows(IllegalArgumentException.class,
        () -> component.copyForStabilityTrial(ThermodynamicModelSettings.MAX_NUMBER_OF_COMPONENTS + 1));
  }

  /**
   * Checks the ordinary public derivative capacity on each active component.
   *
   * @param fluid copied fluid to inspect
   */
  private void assertFullCapacity(SystemThermo fluid) {
    for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        for (double[] buffer : buffers((ComponentEos) fluid.getPhase(phase).getComponent(component))) {
          assertEquals(ThermodynamicModelSettings.MAX_NUMBER_OF_COMPONENTS, buffer.length);
        }
      }
    }
  }

  /**
   * Gets the six owned EOS composition-derivative arrays.
   *
   * @param component component whose owned storage is inspected
   * @return derivative-array references for ownership assertions
   */
  private double[][] buffers(ComponentEos component) {
    return new double[][] {component.dfugdn, component.dfugdx, component.Aij, component.Bij, component.dAdndn,
        component.dBdndn};
  }

  /**
   * Creates an initialized ordinary dry cubic fluid.
   *
   * @param pr whether to use Peng-Robinson instead of SRK
   * @return initialized three-component fluid
   */
  private SystemThermo fluid(boolean pr) {
    SystemThermo fluid = pr ? new SystemPrEos(310.0, 45.0) : new SystemSrkEos(310.0, 45.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("ethane", 0.1);
    fluid.addComponent("n-decane", 0.1);
    fluid.setMixingRule("classic");
    fluid.init(0);
    fluid.init(3);
    return fluid;
  }

  /** Deliberately unqualified runtime subclass whose clone behavior must not be bypassed. */
  private static class UnqualifiedSrkSystem extends SystemSrkEos {
    private static final long serialVersionUID = 1L;

    /** Creates the unknown subclass with ordinary cubic input conditions. */
    UnqualifiedSrkSystem() {
      super(310.0, 45.0);
    }
  }
}
