package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemGEWilson;

/**
 * Checks ownership of mutable activity references in ordinary and copied phases.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class PhaseReferenceCloneOwnershipTest {
  /** Checks evaluating a cloned Furst phase cannot modify the source's cached reference state. */
  @Test
  void furstCloneOwnsActivityReferencePhases() {
    SystemFurstElectrolyteEos system = new SystemFurstElectrolyteEos(298.15, 1.01325);
    system.addComponent("water", 1.0);
    system.addComponent("Na+", 0.001);
    system.addComponent("Cl-", 0.001);
    system.setMixingRule(4);
    system.init(0);
    system.init(1);
    PhaseInterface source = system.getPhase(1);
    double gamma = source.getActivityCoefficient(1, 0);
    assertTrue(Double.isFinite(gamma) && gamma > 0.0);
    PhaseInterface reference = source.getRefPhase(1);
    PhaseInterface copy = source.clone();
    assertNotSame(source.getRefPhase(), copy.getRefPhase());
    assertNotSame(reference, copy.getRefPhase(1));
    assertNotSame(reference.getComponent(0), copy.getRefPhase(1).getComponent(0));
    copy.setTemperature(323.15);
    copy.setPressure(5.0);
    copy.init(copy.getNumberOfMolesInPhase(), copy.getNumberOfComponents(), 1, copy.getType(), 1.0);
    assertTrue(Double.isFinite(copy.getActivityCoefficient(1, 0)));
    assertEquals(298.15, reference.getTemperature(), 0.0);
    assertEquals(1.01325, reference.getPressure(), 0.0);
    assertEquals(gamma, source.getActivityCoefficient(1, 0), Math.abs(gamma) * 1e-12);
  }

  /** Checks explicitly supplied reference parameters survive GE cloning without mutable aliases. */
  @Test
  void geClonePreservesCustomReferenceValuesAndNullEntries() {
    SystemGEWilson system = new SystemGEWilson(298.15, 1.01325);
    system.addComponent("methanol", 0.1);
    system.addComponent("water", 0.9);
    system.setMixingRule("classic");
    system.init(0);
    PhaseInterface source = system.getPhase(1);
    PhaseSrkEos customReference = new PhaseSrkEos();
    customReference.addComponent("methanol", 1.0, 1.0, 0);
    customReference.getComponent(0).setPC(123.456);
    customReference.setTemperature(307.0);
    source.setRefPhase(new PhaseInterface[] {customReference, null});
    PhaseInterface copy = source.clone();
    assertNotSame(source.getRefPhase(), copy.getRefPhase());
    assertNotSame(customReference, copy.getRefPhase(0));
    assertNull(copy.getRefPhase(1));
    assertEquals(123.456, copy.getRefPhase(0).getComponent(0).getPC(), 0.0);
    assertEquals(307.0, copy.getRefPhase(0).getTemperature(), 0.0);
    copy.getRefPhase(0).getComponent(0).setPC(12.0);
    copy.getRefPhase(0).setTemperature(350.0);
    assertEquals(123.456, customReference.getComponent(0).getPC(), 0.0);
    assertEquals(307.0, customReference.getTemperature(), 0.0);
  }

  /** Checks a user-supplied self reference remains a single independently owned phase. */
  @Test
  void clonePreservesSelfReferenceWithoutRecursion() {
    PhaseSrkEos source = methanePhase(190.0);
    source.setRefPhase(new PhaseInterface[] {source});
    PhaseInterface copy = source.clone();
    assertNotSame(source, copy);
    assertSame(copy, copy.getRefPhase(0));
    assertNotSame(source.getComponent(0), copy.getComponent(0));
    copy.getComponent(0).setTC(200.0);
    assertEquals(190.0, source.getComponent(0).getTC(), 0.0);
  }

  /** Checks repeated cloning preserves cycles and shared descendants without growing the graph. */
  @Test
  void repeatedClonePreservesCyclicReferenceGraphAndConfiguration() {
    PhaseSrkEos source = methanePhase(190.0);
    PhaseSrkEos reference = methanePhase(210.0);
    source.setRefPhase(new PhaseInterface[] {reference, reference, null});
    reference.setRefPhase(new PhaseInterface[] {source});
    PhaseInterface current = source;
    for (int iteration = 0; iteration < 30; iteration++) {
      PhaseInterface copy = current.clone();
      assertNotSame(current, copy);
      assertNotSame(current.getRefPhase(0), copy.getRefPhase(0));
      assertSame(copy.getRefPhase(0), copy.getRefPhase(1));
      assertSame(copy, copy.getRefPhase(0).getRefPhase(0));
      assertNull(copy.getRefPhase(2));
      assertEquals(190.0, copy.getComponent(0).getTC(), 0.0);
      assertEquals(210.0, copy.getRefPhase(0).getComponent(0).getTC(), 0.0);
      current = copy;
    }
    current.getRefPhase(0).getComponent(0).setTC(220.0);
    assertEquals(210.0, reference.getComponent(0).getTC(), 0.0);
  }

  /** Checks the compact cubic stability workspace also owns a bounded cyclic reference graph. */
  @Test
  void stabilityWorkspacePreservesReferenceGraphOwnership() {
    PhaseSrkEos source = methanePhase(190.0);
    source.setRefPhase(new PhaseInterface[] {source});
    PhaseEos copy = source.copyForStabilityTrial();
    assertSame(copy, copy.getRefPhase(0));
    assertNotSame(source.getComponent(0), copy.getComponent(0));
    copy.getComponent(0).setTC(200.0);
    assertEquals(190.0, source.getComponent(0).getTC(), 0.0);
  }

  /**
   * Builds a minimal configured cubic phase for reference graph ownership checks.
   *
   * @param criticalTemperature configured methane critical temperature in kelvin
   * @return a one-component SRK phase
   */
  private static PhaseSrkEos methanePhase(double criticalTemperature) {
    PhaseSrkEos phase = new PhaseSrkEos();
    phase.addComponent("methane", 1.0, 1.0, 0);
    phase.getComponent(0).setTC(criticalTemperature);
    phase.setMixingRule(1);
    return phase;
  }
}
