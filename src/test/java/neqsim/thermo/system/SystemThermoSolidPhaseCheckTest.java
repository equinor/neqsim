package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Regression coverage for disabling solid checks on fresh and reused fluids (issue #3623). */
class SystemThermoSolidPhaseCheckTest extends neqsim.NeqSimTest {
  private static Stream<SystemInterface> fluids() {
    return Stream.of(new SystemPrEos(308.15, 9.0), new SystemSrkEos(308.15, 9.0), new SystemSrkCPAstatoil(308.15, 9.0));
  }

  @ParameterizedTest
  @MethodSource("fluids")
  void disablingOnFreshFluidIsSafeAndIdempotent(SystemInterface fluid) {
    fluid.addComponent("methane", 1.0);
    SystemInterface before = fluid.clone();

    fluid.setSolidPhaseCheck(false);
    fluid.setSolidPhaseCheck(false);

    assertNull(fluid.getPhases()[3], "Disabling must not allocate a solid phase");
    assertSolidChecks(fluid, false);
    assertUnchangedPhaseState(before, fluid);
  }

  @ParameterizedTest
  @MethodSource("fluids")
  void enableDisableDisableSequenceClearsCachedPhases(SystemInterface fluid) {
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("CO2", 0.05);
    fluid.setSolidPhaseCheck(true);
    SystemInterface before = fluid.clone();

    fluid.setSolidPhaseCheck(false);
    fluid.setSolidPhaseCheck(false);

    assertSolidChecks(fluid, false);
    assertUnchangedPhaseState(before, fluid);

    fluid.setSolidPhaseCheck(true);
    assertSolidChecks(fluid, true);
    assertEquals(before.getNumberOfPhases(), fluid.getNumberOfPhases());
  }

  @Test
  void disablingPreservesThreePhaseFlashState() {
    SystemInterface fluid = new SystemPrEos(308.15, 40.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("n-decane", 0.1);
    fluid.addComponent("water", 0.1);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);
    new ThermodynamicOperations(fluid).TPflash();
    assertEquals(3, fluid.getNumberOfPhases(), "Fixture must contain gas, oil and aqueous phases");
    SystemInterface before = fluid.clone();

    fluid.setSolidPhaseCheck(false);
    fluid.setSolidPhaseCheck(false);

    assertNull(fluid.getPhases()[3]);
    assertSolidChecks(fluid, false);
    assertUnchangedPhaseState(before, fluid);
  }

  @Test
  void disablingClearsInactiveSolidWithReorderedPhaseIndices() {
    SystemInterface fluid = new SystemPrEos(308.15, 9.0);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("CO2", 0.05);
    fluid.setSolidPhaseCheck(true);
    assertTrue(fluid.getPhases()[3].getComponent("CO2").doSolidCheck());
    fluid.setPhaseIndex(0, 1);
    fluid.setPhaseIndex(1, 0);
    fluid.setPhaseIndex(2, 3);
    fluid.setPhaseIndex(3, 2);
    SystemInterface before = fluid.clone();

    fluid.setSolidPhaseCheck(false);
    fluid.setSolidPhaseCheck(false);

    assertSolidChecks(fluid, false);
    assertUnchangedPhaseState(before, fluid);
  }

  @Test
  void disablingClearsComponentSelectedChecks() {
    SystemInterface fluid = new SystemPrEos(308.15, 9.0);
    fluid.addComponent("methane", 0.999);
    fluid.addComponent("S8", 0.001);
    fluid.setSolidPhaseCheck("S8");
    assertTrue(fluid.getComponent("S8").doSolidCheck());
    assertFalse(fluid.getComponent("methane").doSolidCheck());
    SystemInterface before = fluid.clone();

    fluid.setSolidPhaseCheck(false);
    fluid.setSolidPhaseCheck(false);

    assertSolidChecks(fluid, false);
    assertUnchangedPhaseState(before, fluid);
  }

  private static void assertSolidChecks(SystemInterface fluid, boolean expected) {
    assertEquals(expected, fluid.doSolidPhaseCheck());
    for (PhaseInterface phase : fluid.getPhases()) {
      if (phase != null) {
        for (int component = 0; component < phase.getNumberOfComponents(); component++) {
          assertEquals(expected, phase.getComponent(component).doSolidCheck());
        }
      }
    }
  }

  private static void assertUnchangedPhaseState(SystemInterface before, SystemInterface after) {
    assertEquals(before.getNumberOfPhases(), after.getNumberOfPhases());
    assertEquals(before.getMaxNumberOfPhases(), after.getMaxNumberOfPhases());
    assertEquals(before.doMultiPhaseCheck(), after.doMultiPhaseCheck());
    assertEquals(before.getTotalNumberOfMoles(), after.getTotalNumberOfMoles(), 0.0);
    for (int index = 0; index < before.getPhases().length; index++) {
      assertEquals(before.getPhaseIndex(index), after.getPhaseIndex(index));
      PhaseInterface expected = before.getPhases()[index];
      PhaseInterface actual = after.getPhases()[index];
      if (expected == null) {
        assertNull(actual);
        continue;
      }
      assertEquals(expected.getType(), actual.getType());
      assertEquals(expected.getTemperature(), actual.getTemperature(), 0.0);
      assertEquals(expected.getPressure(), actual.getPressure(), 0.0);
      assertEquals(expected.getBeta(), actual.getBeta(), 0.0);
      assertEquals(expected.getNumberOfMolesInPhase(), actual.getNumberOfMolesInPhase(), 0.0);
      assertEquals(expected.getNumberOfComponents(), actual.getNumberOfComponents());
      for (int component = 0; component < expected.getNumberOfComponents(); component++) {
        ComponentInterface expectedComponent = expected.getComponent(component);
        ComponentInterface actualComponent = actual.getComponent(component);
        assertEquals(expectedComponent.getComponentName(), actualComponent.getComponentName());
        assertEquals(expectedComponent.getNumberOfmoles(), actualComponent.getNumberOfmoles(), 0.0);
        assertEquals(expectedComponent.getNumberOfMolesInPhase(), actualComponent.getNumberOfMolesInPhase(), 0.0);
        assertEquals(expectedComponent.getz(), actualComponent.getz(), 0.0);
        assertEquals(expectedComponent.getx(), actualComponent.getx(), 0.0);
      }
    }
  }
}
