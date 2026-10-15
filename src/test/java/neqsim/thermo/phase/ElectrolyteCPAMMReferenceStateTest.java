package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentSrkCPAMM;
import neqsim.thermo.system.SystemElectrolyteCPAMM;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Checks that activity references use the mixture's electrolyte model and parameters.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ElectrolyteCPAMMReferenceStateTest {
  /** Infinite-dilution normalization must hold for all term switches and custom ion parameters. */
  @Test
  void customModelAndIonParametersHaveUnitDiluteActivityLimit() {
    for (int mask = 0; mask < 8; mask++) {
      SystemElectrolyteCPAMM fluid = new SystemElectrolyteCPAMM(325, 5);
      fluid.addComponent("water", 55.508);
      fluid.addComponent("Na+", 1e-8);
      fluid.addComponent("Cl-", 1e-8);
      fluid.setMixingRule(10);
      fluid.setDebyeHuckelOn((mask & 1) != 0);
      fluid.setBornOn((mask & 2) != 0);
      fluid.setShortRangeOn((mask & 4) != 0);
      fluid.init(0);
      for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
        for (int i = 1; i < 3; i++) {
          ComponentSrkCPAMM ion = (ComponentSrkCPAMM) fluid.getPhase(p).getComponent(i);
          ion.setU0_iw(ion.getU0_iw() + 2000);
          ion.setUT_iw(ion.getUT_iw() + 5);
          ion.setBornRadius(ion.getBornRadius() * 1.2);
        }
      }
      fluid.init(3);
      PhaseElectrolyteCPAMM phase = (PhaseElectrolyteCPAMM) fluid.getPhase(1);
      assertEquals(1, phase.getMeanIonicActivity(1, 2), 5e-4, "Dilute reference for term mask " + mask);
      // Reuse the existing reference after changing model configuration and parameters.
      phase.setBornOn(!phase.isBornOn());
      phase.setDielectricMixingRule(PhaseElectrolyteCPAMM.DielectricMixingRule.LOOYENGA);
      ((ComponentSrkCPAMM) phase.getComponent(1)).setBornRadius(2.2);
      phase.init(phase.getNumberOfMolesInPhase(), phase.getNumberOfComponents(), 3, phase.getType(), 1);
      for (int i = 0; i < phase.getNumberOfComponents(); i++) {
        phase.getComponent(i).fugcoef(phase);
      }
      assertEquals(1, phase.getMeanIonicActivity(1, 2), 5e-4);
      PhaseElectrolyteCPAMM reference = (PhaseElectrolyteCPAMM) phase.getRefPhase(1);
      assertEquals(phase.isBornOn(), reference.isBornOn());
      assertEquals(phase.getDielectricMixingRule(), reference.getDielectricMixingRule());
      assertEquals(2.2, ((ComponentSrkCPAMM) reference.getComponent(0)).getBornRadius(), 0);
      PhaseElectrolyteCPAMM copy = phase.clone();
      copy.setBornOn(!phase.isBornOn());
      copy.init(copy.getNumberOfMolesInPhase(), copy.getNumberOfComponents(), 3, copy.getType(), 1);
      for (int i = 0; i < copy.getNumberOfComponents(); i++) {
        copy.getComponent(i).fugcoef(copy);
      }
      assertEquals(1, copy.getMeanIonicActivity(1, 2), 5e-4);
      assertNotSame(reference, copy.getRefPhase(1));
      assertEquals(phase.isBornOn(), reference.isBornOn(), "Cloned reference must not change the original model");
      assertTrue(Double.isFinite(phase.getOsmoticCoefficientOfWater()));
    }
  }

  /** Publicly supplied non-MM pure and binary references retain their model and custom parameters. */
  @Test
  void customOtherModelReferencesRemainSupported() {
    SystemElectrolyteCPAMM fluid = new SystemElectrolyteCPAMM(298.15, 1);
    fluid.addComponent("water", 55.508);
    fluid.addComponent("MEG", 1);
    fluid.setMixingRule(10);
    fluid.init(0);
    fluid.init(1);
    PhaseElectrolyteCPAMM phase = (PhaseElectrolyteCPAMM) fluid.getPhase(1);
    phase.initRefPhases(false);

    SystemSrkEos pure = new SystemSrkEos(298.15, 1);
    pure.addComponent("water", 1);
    pure.setMixingRule(2);
    pure.init(0);
    pure.getPhase(1).getComponent(0).setPC(123.456);
    pure.init(1);
    PhaseInterface pureReference = pure.getPhase(1).clone();
    double pureLogFugacity = pureReference.getComponent(0).getLogFugacityCoefficient();
    phase.setRefPhase(0, pureReference);
    assertEquals(pureLogFugacity, phase.getLogPureComponentFugacity(0), 1e-10);
    assertSame(pureReference, phase.getRefPhase(0));
    assertEquals(123.456, pureReference.getComponent(0).getPC(), 0);

    SystemSrkEos dilute = new SystemSrkEos(298.15, 1);
    dilute.addComponent("MEG", 1e-10);
    dilute.addComponent("water", 1);
    dilute.setMixingRule(2);
    dilute.init(0);
    dilute.getPhase(1).getComponent(0).setPC(89.123);
    dilute.init(1);
    PhaseInterface diluteReference = dilute.getPhase(1).clone();
    double diluteLogFugacity = diluteReference.getComponent(0).getLogFugacityCoefficient();
    phase.setRefPhase(1, diluteReference);
    assertEquals(diluteLogFugacity, phase.getLogInfiniteDiluteFugacity(1, 0), 1e-10);
    assertSame(diluteReference, phase.getRefPhase(1));
    assertEquals(89.123, diluteReference.getComponent(0).getPC(), 0);

    PhaseElectrolyteCPAMM copy = phase.clone();
    assertNotSame(pureReference, copy.getRefPhase(0));
    assertNotSame(diluteReference, copy.getRefPhase(1));
    assertEquals(pureLogFugacity, copy.getLogPureComponentFugacity(0), 1e-10);
    assertEquals(diluteLogFugacity, copy.getLogInfiniteDiluteFugacity(1, 0), 1e-10);
  }

  /** Explicit setters and exposed-array replacement preserve custom MM reference configurations. */
  @Test
  void explicitlySuppliedMMReferencesRetainTheirParameters() {
    for (int assignment = 0; assignment < 3; assignment++) {
      PhaseElectrolyteCPAMM phase = customReferenceFixture(assignment);
      assertCustomReferences(phase);
      PhaseElectrolyteCPAMM copy = phase.clone();
      assertNotSame(phase.getRefPhase(1), copy.getRefPhase(1));
      copy.setBornOn(false);
      assertCustomReferences(copy);
      assertCustomReferences(phase);
      if (assignment != 1) {
        assertAutomaticReferenceUpdates(copy, 2);
      } else {
        // Even unchanged entries in an explicitly supplied array are caller-owned.
        copy.setBornOn(false);
        assertTrue(Double.isFinite(copy.getLogInfiniteDiluteFugacity(2, 0)));
        assertEquals(true, ((PhaseElectrolyteCPAMM) copy.getRefPhase(2)).isBornOn());
      }
    }
  }

  /**
   * Serialized reference graph identity preserves both automatic and explicit reference behavior.
   *
   * @throws IOException if serialization fails
   * @throws ClassNotFoundException if a serialized class cannot be loaded
   */
  @Test
  void referenceProvenanceSurvivesSerialization() throws IOException, ClassNotFoundException {
    for (int assignment = 0; assignment < 3; assignment++) {
      PhaseElectrolyteCPAMM phase = roundTrip(customReferenceFixture(assignment));
      assertCustomReferences(phase);
      if (assignment != 1) {
        assertAutomaticReferenceUpdates(phase, 2);
      }
    }
  }

  /** Rebuilding the reference phases restores factory provenance after a custom array was installed. */
  @Test
  void automaticReferenceRebuildRestoresModelInheritance() {
    PhaseElectrolyteCPAMM phase = customReferenceFixture(1);
    PhaseInterface oldReference = phase.getRefPhase(1);
    phase.initRefPhases(false, "water");
    assertNotSame(oldReference, phase.getRefPhase(1));
    assertAutomaticReferenceUpdates(phase, 1);
    assertAutomaticReferenceUpdates(phase.clone(), 2);
  }

  /**
   * Creates custom pure-water and dilute-sodium references through each supported public assignment path.
   *
   * @param assignment zero for index setters, one for the array setter, two for exposed-array replacement
   * @return initialized phase containing the configured references
   */
  private PhaseElectrolyteCPAMM customReferenceFixture(int assignment) {
    SystemElectrolyteCPAMM fluid = new SystemElectrolyteCPAMM(298.15, 1);
    fluid.addComponent("water", 55.508);
    fluid.addComponent("Na+", 1);
    fluid.addComponent("Cl-", 1);
    fluid.setMixingRule(10);
    fluid.init(0);
    fluid.init(1);
    PhaseElectrolyteCPAMM phase = (PhaseElectrolyteCPAMM) fluid.getPhase(1);
    PhaseInterface[] references = phase.getRefPhase().clone();
    for (int i = 0; i < 2; i++) {
      // Passing the original automatic object explicitly must also transfer configuration ownership.
      PhaseElectrolyteCPAMM reference = (PhaseElectrolyteCPAMM) references[i];
      if (assignment != 0) {
        reference = reference.clone();
      }
      reference.setDebyeHuckelOn(false);
      reference.setBornOn(false);
      reference.setShortRangeOn(false);
      reference.setDielectricMixingRule(PhaseElectrolyteCPAMM.DielectricMixingRule.LOOYENGA);
      ComponentSrkCPAMM component = (ComponentSrkCPAMM) reference.getComponent(0);
      component.setU0_iw(12345);
      component.setUT_iw(12.345);
      component.setBornRadius(2.345);
      references[i] = reference;
      if (assignment == 0) {
        phase.setRefPhase(i, reference);
      } else if (assignment == 2) {
        phase.getRefPhase()[i] = reference;
      }
    }
    if (assignment == 1) {
      phase.setRefPhase(references);
    }
    return phase;
  }

  /**
   * Evaluates both custom reference forms and checks that model and ion parameters survive evaluation.
   *
   * @param phase phase containing explicit references
   */
  private void assertCustomReferences(PhaseElectrolyteCPAMM phase) {
    assertTrue(Double.isFinite(phase.getLogPureComponentFugacity(0, true)));
    assertTrue(Double.isFinite(phase.getLogInfiniteDiluteFugacity(1, 0)));
    for (int i = 0; i < 2; i++) {
      PhaseElectrolyteCPAMM reference = (PhaseElectrolyteCPAMM) phase.getRefPhase(i);
      assertEquals(false, reference.isDebyeHuckelOn());
      assertEquals(false, reference.isBornOn());
      assertEquals(false, reference.isShortRangeOn());
      assertEquals(PhaseElectrolyteCPAMM.DielectricMixingRule.LOOYENGA, reference.getDielectricMixingRule());
      ComponentSrkCPAMM component = (ComponentSrkCPAMM) reference.getComponent(0);
      assertEquals(12345, component.getU0_iw(), 0);
      assertEquals(12.345, component.getUT_iw(), 0);
      assertEquals(2.345, component.getBornRadius(), 0);
    }
  }

  /**
   * Checks that an automatic reference still follows a changed source phase after copying or rebuilding.
   *
   * @param phase source phase
   * @param component ion whose reference is still automatic
   */
  private void assertAutomaticReferenceUpdates(PhaseElectrolyteCPAMM phase, int component) {
    phase.setBornOn(!phase.isBornOn());
    ((ComponentSrkCPAMM) phase.getComponent(component)).setBornRadius(2.789);
    assertTrue(Double.isFinite(phase.getLogInfiniteDiluteFugacity(component, 0)));
    PhaseElectrolyteCPAMM reference = (PhaseElectrolyteCPAMM) phase.getRefPhase(component);
    assertEquals(phase.isBornOn(), reference.isBornOn());
    assertEquals(2.789, ((ComponentSrkCPAMM) reference.getComponent(0)).getBornRadius(), 0);
  }

  /**
   * Serializes a phase with its automatic and explicitly supplied reference graph.
   *
   * @param phase phase to copy
   * @return independent deserialized phase
   * @throws IOException if serialization fails
   * @throws ClassNotFoundException if a serialized class cannot be loaded
   */
  private PhaseElectrolyteCPAMM roundTrip(PhaseElectrolyteCPAMM phase) throws IOException, ClassNotFoundException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(phase);
    }
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (PhaseElectrolyteCPAMM) input.readObject();
    }
  }

}
