package neqsim.thermo.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAMM;
import neqsim.thermo.system.SystemSrkCPA;

/** CPA component subclasses must use the CPA parachor database column. */
class CpaParachorSelectionTest {
  @Test
  void mariboMogensenCpaUsesCpaParachorForMethanolAndWater() {
    SystemElectrolyteCPAMM electrolyte = new SystemElectrolyteCPAMM(298.15, 1.0);
    SystemSrkCPA base = new SystemSrkCPA(298.15, 1.0);
    for (String component : new String[] {"methanol", "water"}) {
      electrolyte.addComponent(component, 1.0);
      base.addComponent(component, 1.0);
      assertEquals(base.getPhase(0).getComponent(component).getParachorParameter(),
          electrolyte.getPhase(0).getComponent(component).getParachorParameter(), 1e-10);
    }
    assertEquals(87.543852818, electrolyte.getPhase(0).getComponent("methanol").getParachorParameter(), 1e-8);
  }
}
