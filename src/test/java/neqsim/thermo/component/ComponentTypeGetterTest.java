package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Reading the reported component type must not change the stored database type. */
class ComponentTypeGetterTest {
  /**
   * {@code getComponentType()} used to overwrite the stored type with "normal", after which {@code isHydrocarbon()} and
   * {@code isInert()} returned false for database hydrocarbons and inert gases.
   */
  @Test
  void typeGetterLeavesHydrocarbonAndInertClassificationUnchanged() {
    SystemInterface fluid = new SystemSrkEos(288.15, 50.0);
    fluid.addComponent("methane", 0.8);
    fluid.addComponent("nitrogen", 0.05);
    fluid.addComponent("CO2", 0.05);
    fluid.addComponent("water", 0.05);
    fluid.addTBPfraction("C7", 0.05, 0.096, 0.74);
    fluid.setMixingRule("classic");
    boolean[] hydrocarbon = {true, false, false, false, true};
    boolean[] inert = {false, true, true, false, false};
    String[] reported = {"normal", "normal", "normal", "normal", "TBP"};
    for (int pass = 0; pass < 3; pass++) {
      SystemInterface probe = pass == 2 ? fluid.clone() : fluid;
      for (int component = 0; component < hydrocarbon.length; component++) {
        ComponentInterface item = probe.getPhase(0).getComponent(component);
        String name = item.getComponentName() + ", pass " + pass;
        assertEquals(hydrocarbon[component], item.isHydrocarbon(), name);
        assertEquals(inert[component], item.isInert(), name);
        assertEquals(reported[component], item.getComponentType(), name);
        assertEquals(hydrocarbon[component], item.isHydrocarbon(), name + " after the getter");
        assertEquals(inert[component], item.isInert(), name + " after the getter");
      }
    }
    assertTrue(fluid.getPhase(0).getComponent("methane").isHydrocarbon());
    assertFalse(fluid.getPhase(0).getComponent("nitrogen").isHydrocarbon());
  }
}
