package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentSrkCPAMM;
import neqsim.thermo.system.SystemElectrolyteCPAMM;

/**
 * Checks exact derivative reuse against stateless evaluation after state and parameter changes. Independent finite
 * differences and thermodynamic identities are covered by the derivative consistency test.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ElectrolyteCPAMMDerivativeCacheTest {
  /** Verifies each mutable input family invalidates reuse and returning to an earlier state is safe. */
  @Test
  void changingInputsAndReturningToPreviousStateMatchesStatelessEvaluation() {
    PhaseElectrolyteCPAMM phase = phase();
    compare(phase);
    MMResidualDerivative.Cache initial = phase.getElectrolyteDerivativeCache();
    compare(phase);
    assertSame(initial, phase.getElectrolyteDerivativeCache());
    double temperature = phase.getTemperature();
    phase.setTemperature(temperature + 10);
    compareChanged(phase, initial);
    phase.setTemperature(temperature);
    compare(phase);
    double volume = phase.getMolarVolume();
    initial = phase.getElectrolyteDerivativeCache();
    phase.setMolarVolume(volume * 1.01);
    compareChanged(phase, initial);
    phase.setMolarVolume(volume);
    compare(phase);
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      double amount = phase.getComponent(i).getNumberOfMolesInPhase();
      initial = phase.getElectrolyteDerivativeCache();
      phase.getComponent(i).setNumberOfMolesInPhase(amount * 1.02);
      compareChanged(phase, initial);
      phase.getComponent(i).setNumberOfMolesInPhase(amount);
      compare(phase);
    }
    for (PhaseElectrolyteCPAMM.DielectricMixingRule rule : PhaseElectrolyteCPAMM.DielectricMixingRule.values()) {
      phase.setDielectricMixingRule(rule);
      compare(phase);
    }
    for (int mask = 0; mask < 8; mask++) {
      phase.setDebyeHuckelOn((mask & 1) != 0);
      phase.setBornOn((mask & 2) != 0);
      phase.setShortRangeOn((mask & 4) != 0);
      compare(phase);
    }
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      ComponentSrkCPAMM component = (ComponentSrkCPAMM) phase.getComponent(i);
      initial = phase.getElectrolyteDerivativeCache();
      component.setLennardJonesMolecularDiameter(component.getLennardJonesMolecularDiameter() * 1.01);
      compareChanged(phase, initial);
      initial = phase.getElectrolyteDerivativeCache();
      component.setCriticalVolume(component.getCriticalVolume() * 1.01);
      compareChanged(phase, initial);
      if (component.getIonicCharge() != 0) {
        initial = phase.getElectrolyteDerivativeCache();
        component.setBornRadius(component.getBornRadius() * 1.01);
        compareChanged(phase, initial);
      }
    }
    initial = phase.getElectrolyteDerivativeCache();
    phase.wij[0][2] += 1;
    compareChanged(phase, initial);
    initial = phase.getElectrolyteDerivativeCache();
    phase.wijT[0][2] += 0.01;
    compareChanged(phase, initial);
    initial = phase.getElectrolyteDerivativeCache();
    ((ComponentSrkCPAMM) phase.getComponent(2)).setU0_iw(21000);
    phase.initElectrolyteProperties();
    compareChanged(phase, initial);
  }

  /** A clone may evaluate other states without changing the original cache or results. */
  @Test
  void clonedPhasesNeverShareDerivativeStorage() {
    PhaseElectrolyteCPAMM phase = phase();
    compare(phase);
    MMResidualDerivative.Cache original = phase.getElectrolyteDerivativeCache();
    PhaseElectrolyteCPAMM copy = phase.clone();
    assertNull(copy.getElectrolyteDerivativeCache());
    copy.setTemperature(phase.getTemperature() + 10);
    compare(copy);
    assertNotSame(original, copy.getElectrolyteDerivativeCache());
    assertSame(original, phase.getElectrolyteDerivativeCache());
    compare(phase);
  }

  /**
   * Verifies a changed input creates a fresh derivative state.
   *
   * @param phase changed phase
   * @param previous previous derivative state
   */
  private static void compareChanged(PhaseElectrolyteCPAMM phase, MMResidualDerivative.Cache previous) {
    compare(phase);
    assertNotSame(previous, phase.getElectrolyteDerivativeCache());
  }

  /**
   * Compares all first and mixed second derivatives, volume third derivatives, and a third mixed derivative.
   *
   * @param phase current phase
   */
  private static void compare(PhaseElectrolyteCPAMM phase) {
    int count = phase.getNumberOfComponents();
    for (int i = 0; i < count + 2; i++) {
      close(MMResidualDerivative.derivativeUncached(phase, -1, i, -1, -1),
          MMResidualDerivative.derivative(phase, -1, i, -1, -1));
      for (int j = 0; j < count + 2; j++) {
        close(MMResidualDerivative.derivativeUncached(phase, -1, i, j, -1),
            MMResidualDerivative.derivative(phase, -1, i, j, -1));
      }
    }
    close(MMResidualDerivative.derivativeUncached(phase, -1, count, count, count),
        MMResidualDerivative.derivative(phase, -1, count, count, count));
    close(MMResidualDerivative.derivativeUncached(phase, -1, 0, 1, count),
        MMResidualDerivative.derivative(phase, -1, 0, 1, count));
  }

  /**
   * Allows only floating-point summation differences between directional and joint evaluation.
   *
   * @param expected stateless derivative
   * @param actual cached derivative
   */
  private static void close(double expected, double actual) {
    assertEquals(expected, actual, 2e-12 * Math.max(1, Math.abs(expected)));
  }

  /**
   * Creates a mixed-solvent, mixed-valence state exercising every input family.
   *
   * @return initialized phase
   */
  private static PhaseElectrolyteCPAMM phase() {
    SystemElectrolyteCPAMM fluid = new SystemElectrolyteCPAMM(313.15, 5);
    fluid.addComponent("water", 55.508);
    fluid.addComponent("MEG", 2);
    fluid.addComponent("Na+", 0.5);
    fluid.addComponent("Ca++", 0.2);
    fluid.addComponent("Cl-", 0.9);
    fluid.setMixingRule(10);
    fluid.init(0);
    fluid.init(3);
    return (PhaseElectrolyteCPAMM) fluid.getPhase(1);
  }
}
