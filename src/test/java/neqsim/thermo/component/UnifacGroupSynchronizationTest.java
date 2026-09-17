package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.atomelement.UNIFACgroup;
import neqsim.thermo.phase.PhaseGEUnifac;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemUNIFAC;
import neqsim.thermo.system.SystemUNIFACpsrk;

/** Regression coverage for issue #3769: group lists and indexed arrays must agree. */
class UnifacGroupSynchronizationTest extends neqsim.NeqSimTest {
  private ComponentGEUnifac component(int model, String name) {
    if (model == 1) {
      return new ComponentGEUnifacPSRK(name, 1.0, 1.0, 0);
    }
    if (model == 2) {
      return new ComponentGEUnifacUMRPRU(name, 1.0, 1.0, 0);
    }
    return new ComponentGEUnifac(name, 1.0, 1.0, 0);
  }

  private void assertSynchronized(ComponentGEUnifac component) {
    assertEquals(component.getNumberOfUNIFACgroups(), component.getUnifacGroups().length);
    for (int i = 0; i < component.getNumberOfUNIFACgroups(); i++) {
      assertSame(component.getUnifacGroups2().get(i), component.getUnifacGroup(i));
    }
  }

  @ParameterizedTest
  @ValueSource(ints = { 0, 1, 2 })
  void constructedComponentsExposeTheirGroups(int model) {
    ComponentGEUnifac methanol = component(model, "methanol");
    assertEquals(1, methanol.getNumberOfUNIFACgroups());
    assertSynchronized(methanol);
    assertEquals(15, methanol.getUnifacGroup(0).getSubGroup());
    assertEquals(1, methanol.getUnifacGroup(0).getN());
    assertEquals(1.4311, methanol.getR(), 1.0e-12);
    assertEquals(1.432, methanol.getQ(), 1.0e-12);
  }

  @ParameterizedTest
  @ValueSource(ints = { 0, 1, 2 })
  void replacementCanShrinkAndEmptyTheGroupArray(int model) {
    ComponentGEUnifac component = component(model, "methanol");
    component.addUNIFACgroup(16, 0);
    component.addUNIFACgroup(1, 0);
    assertSynchronized(component);
    ArrayList<UNIFACgroup> replacement = new ArrayList<UNIFACgroup>();
    replacement.add(new UNIFACgroup(16, 1));
    component.setUnifacGroups(replacement);
    assertSynchronized(component);
    assertEquals(1, component.getUnifacGroups().length);
    assertEquals(16, component.getUnifacGroup(0).getSubGroup());
    component.setUnifacGroups(new ArrayList<UNIFACgroup>());
    assertSynchronized(component);
    assertEquals(0, component.getUnifacGroups().length);
  }

  @ParameterizedTest
  @ValueSource(ints = { 0, 1, 2 })
  void pseudoComponentRebuildReplacesPaddedGroups(int model) {
    ComponentGEUnifac component = component(model, "C10_PC");
    assertSynchronized(component);
    component.addUNIFACgroup(16, 0);
    component.setMolarMass(0.140);
    component.initPCUNIFACGroups();
    assertSynchronized(component);
    assertEquals(2, component.getNumberOfUNIFACgroups());
    assertEquals(2, component.getUnifacGroup(0).getN());
    assertEquals(8, component.getUnifacGroup(1).getN());
    component.setMolarMass(0.196);
    component.initPCUNIFACGroups();
    assertSynchronized(component);
    assertEquals(12, component.getUnifacGroup(1).getN());
  }

  @ParameterizedTest
  @ValueSource(booleans = { false, true })
  void methanolWaterCanBeConfiguredAndInitializedRepeatedly(boolean psrk) {
    SystemInterface system = psrk ? new SystemUNIFACpsrk(298.15, 1.0) : new SystemUNIFAC(298.15, 1.0);
    system.addComponent("methanol", 0.5);
    system.addComponent("water", 0.5);
    system.createDatabase(true);
    system.setMixingRule("classic");
    system.init(0);
    double[] firstGamma = new double[2];
    PhaseGEUnifac liquid = (PhaseGEUnifac) system.getPhase(1);
    for (int repeat = 0; repeat < 3; repeat++) {
      liquid.checkGroups();
      system.setMixingRule("classic");
      system.init(0);
      for (int i = 0; i < 2; i++) {
        ComponentGEUnifac component = (ComponentGEUnifac) liquid.getComponent(i);
        assertSynchronized(component);
        assertEquals(2, component.getNumberOfUNIFACgroups());
        assertEquals(15, component.getUnifacGroup(0).getSubGroup());
        assertEquals(16, component.getUnifacGroup(1).getSubGroup());
        assertEquals(i == 0 ? 1 : 0, component.getUnifacGroup(0).getN());
        assertEquals(i == 1 ? 1 : 0, component.getUnifacGroup(1).getN());
        assertEquals(0, component.getUnifacGroup(0).getGroupIndex());
        assertEquals(1, component.getUnifacGroup(1).getGroupIndex());
        double gamma = component.getGamma(liquid, 2, 298.15, 1.0, liquid.getType());
        assertTrue(Double.isFinite(gamma) && gamma > 0.0);
        if (repeat == 0) {
          firstGamma[i] = gamma;
        } else {
          assertEquals(firstGamma[i], gamma, 1.0e-12);
        }
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = { false, true })
  void pureMethanolActivityCoefficientIsUnity(boolean psrk) {
    SystemInterface system = psrk ? new SystemUNIFACpsrk(300.0, 1.0) : new SystemUNIFAC(300.0, 1.0);
    system.addComponent("methanol", 1.0);
    system.setMixingRule("classic");
    system.init(0);
    PhaseInterface liquid = system.getPhase(1);
    ComponentGEUnifac methanol = (ComponentGEUnifac) liquid.getComponent(0);
    assertSynchronized(methanol);
    assertEquals(1.0, methanol.getGamma(liquid, 1, 300.0, 1.0, liquid.getType()), 1.0e-12);
  }

  @ParameterizedTest
  @ValueSource(booleans = { false, true })
  void checkGroupsReconcilesLegacyListEdits(boolean psrk) {
    SystemInterface system = psrk ? new SystemUNIFACpsrk(300.0, 1.0) : new SystemUNIFAC(300.0, 1.0);
    system.addComponent("methanol", 0.3);
    system.addComponent("water", 0.7);
    system.setMixingRule("classic");
    system.init(0);
    PhaseGEUnifac liquid = (PhaseGEUnifac) system.getPhase(1);
    ComponentGEUnifac methanol = (ComponentGEUnifac) liquid.getComponent(0);
    methanol.getUnifacGroups2().add(new UNIFACgroup(1, 0));
    for (int repeat = 0; repeat < 2; repeat++) {
      liquid.checkGroups();
      system.setMixingRule("classic");
      system.init(0);
      for (int i = 0; i < 2; i++) {
        ComponentGEUnifac component = (ComponentGEUnifac) liquid.getComponent(i);
        assertSynchronized(component);
        assertEquals(3, component.getNumberOfUNIFACgroups());
        assertEquals(1, component.getUnifacGroup(0).getSubGroup());
        assertEquals(0, component.getUnifacGroup(0).getN());
        double gamma = component.getGamma(liquid, 2, 300.0, 1.0, liquid.getType());
        assertTrue(Double.isFinite(gamma) && gamma > 0.0);
      }
    }
  }
}
