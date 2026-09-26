package neqsim.physicalproperties.mixingrule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/** Tests that the tabulated liquid-viscosity interaction is actually loaded. */
class PhysicalPropertyMixingRuleDatabaseTest {
  @Test
  void loadsWaterTegInteractionInEitherComponentOrder() {
    for (boolean reverse : new boolean[] {false, true}) {
      SystemInterface fluid = new SystemSrkEos(298.15, 1.0);
      fluid.addComponent(reverse ? "TEG" : "water", 1.0);
      fluid.addComponent(reverse ? "water" : "TEG", 1.0);
      fluid.init(0);
      PhysicalPropertyMixingRule rule = new PhysicalPropertyMixingRule();
      rule.initMixingRules(fluid.getPhase(0));
      assertEquals(0.8, rule.getViscosityGij(0, 1), 1e-12);
      assertEquals(rule.getViscosityGij(0, 1), rule.getViscosityGij(1, 0), 0.0);
      assertEquals(0.0, rule.getViscosityGij(0, 0), 0.0);
    }
  }
}
