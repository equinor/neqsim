package neqsim.thermo.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Empty outlet templates must not resurrect inactive phase inventories (issue #4427).
 *
 * @author Even Solbraa
 * @version 1.0
 */
class SystemThermoEmptySystemCloneTest {
  /** Removal of gas leaves a phase-index hole that must also be emptied. */
  @Test
  void removedGasSlotCannotSurviveEmptyCloneInitialization() {
    SystemInterface source = createFluid();
    new ThermodynamicOperations(source).TPflash();
    assertEquals(3, source.getNumberOfPhases());
    // Model the OIL/AQUEOUS mapping left when physical gas slot zero is removed.
    PhaseInterface oil = source.getPhase("oil").clone();
    PhaseInterface aqueous = source.getPhase("aqueous").clone();
    source.getPhases()[1] = oil;
    source.getPhases()[2] = aqueous;
    source.setNumberOfPhases(2);
    source.setPhaseIndex(0, 1);
    source.setPhaseIndex(1, 2);
    source.setPhaseIndex(2, 2);
    source.setPhaseType(0, PhaseType.OIL);
    source.setPhaseType(1, PhaseType.AQUEOUS);
    assertTrue(source.getPhaseIndex(0) != 0);
    double[] composition = source.getMolarComposition().clone();
    double sourceMoles = source.getTotalNumberOfMoles();
    SystemInterface empty = source.getEmptySystemClone();
    for (int slot = 0; slot < empty.getMaxNumberOfPhases(); slot++) {
      for (int component = 0; component < composition.length; component++) {
        assertEquals(composition[component],
            empty.getPhases()[slot].getComponent(component).getNumberOfmoles() / empty.getTotalNumberOfMoles(), 1.0e-12,
            "overall composition in physical slot " + slot);
      }
    }
    assertEquals(sourceMoles * 1.0e-30, empty.getTotalNumberOfMoles(), sourceMoles * 1.0e-42);
    empty.init(2);
    empty.setTotalFlowRate(1.0e-20, "kg/hr");
    new ThermodynamicOperations(empty).TPflash();
    assertTrue(empty.getFlowRate("kg/hr") < 1.0e-20);
    assertEquals(1.0, source.getFlowRate("kg/hr"), 1.0e-12, "source must remain unchanged");
  }

  /** A template can be filled again without corrupting its composition or its source. */
  @Test
  void emptyCloneCanBeRefilledAndFlashed() {
    SystemInterface source = createFluid();
    new ThermodynamicOperations(source).TPflash();
    SystemInterface refilled = source.getEmptySystemClone();
    refilled.setTotalFlowRate(10.0, "kg/hr");
    new ThermodynamicOperations(refilled).TPflash();
    assertEquals(10.0, refilled.getFlowRate("kg/hr"), 1.0e-10);
    assertEquals(3, refilled.getNumberOfPhases());
    for (int component = 0; component < source.getNumberOfComponents(); component++) {
      assertEquals(source.getComponent(component).getz(), refilled.getComponent(component).getz(), 1.0e-12);
    }
    assertEquals(1.0, source.getFlowRate("kg/hr"), 1.0e-12);
  }

  /** An already empty fluid must not acquire a positive inventory floor. */
  @Test
  void alreadyEmptySourceRemainsEmpty() {
    SystemInterface source = createFluid();
    source.setEmptyFluid();
    SystemInterface empty = source.getEmptySystemClone();
    assertEquals(0.0, empty.getTotalNumberOfMoles(), 0.0);
    assertEquals(0.0, empty.getFlowRate("kg/hr"), 0.0);
    for (int slot = 0; slot < empty.getMaxNumberOfPhases(); slot++) {
      for (int component = 0; component < empty.getNumberOfComponents(); component++) {
        assertEquals(0.0, empty.getPhases()[slot].getComponent(component).getNumberOfmoles(), 0.0);
      }
    }
  }

  /** Even an identity phase mapping must preserve normalized overall composition. */
  @Test
  void emptyCloneRetainsFeedCompositionInEveryStorageSlot() {
    SystemInterface source = createFluid();
    source.init(0);
    double[] composition = source.getMolarComposition().clone();
    SystemInterface empty = source.getEmptySystemClone();
    for (int slot = 0; slot < empty.getMaxNumberOfPhases(); slot++) {
      double sum = 0.0;
      for (int component = 0; component < composition.length; component++) {
        double fraction = empty.getPhases()[slot].getComponent(component).getNumberOfmoles()
            / empty.getTotalNumberOfMoles();
        assertEquals(composition[component], fraction, 1.0e-12);
        sum += fraction;
      }
      assertEquals(1.0, sum, 1.0e-12);
    }
  }

  /**
   * Creates a public PR78 water-rich placeholder feed with water last.
   *
   * @return a one-kg/hr feed at the reported separator conditions
   */
  private SystemInterface createFluid() {
    SystemInterface fluid = new SystemPrEos1978(293.90, 13.54325);
    fluid.addComponent("methane", 0.06);
    fluid.addComponent("nC10", 0.04);
    fluid.addComponent("water", 0.90);
    fluid.setMixingRule(2);
    fluid.setMultiPhaseCheck(true);
    fluid.setTotalFlowRate(1.0, "kg/hr");
    return fluid;
  }
}
