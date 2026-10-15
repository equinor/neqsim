package neqsim.thermo.phase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;
import neqsim.thermo.system.SystemInterface;

/**
 * Association derivative workspaces must track component registration as well as the number of association sites.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class ElectrolyteCPAComponentRegistrationTest extends neqsim.NeqSimTest {
  /** An absent non-associating component still requires a derivative row and column. */
  @Test
  void addingAbsentMolecularComponentRebuildsDerivativeWorkspace() {
    SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, 10.0);
    fluid.addComponent("water", 1.0);
    fluid.addComponent("methane", 0.1);
    fluid.addComponent("Na+", 0.001);
    fluid.addComponent("Cl-", 0.001);
    fluid.setMixingRule(10);
    fluid.init(0);
    fluid.init(3);
    int sites = ((PhaseElectrolyteCPA) fluid.getPhase(0)).getTotalNumberOfAccociationSites();
    fluid.addComponent("nitrogen", 0.0);
    fluid.setMixingRule(10);
    fluid.init(0);
    fluid.init(3);
    assertEquals(sites, ((PhaseElectrolyteCPA) fluid.getPhase(0)).getTotalNumberOfAccociationSites(),
        "the new component must exercise unchanged association-site count");
    assertEquals(0.0, fluid.getComponent("nitrogen").getNumberOfmoles(), 0.0);
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
        assertTrue(Double.isFinite(fluid.getPhase(phase).getComponent(i).getLogFugacityCoefficient()));
        for (int j = 0; j < fluid.getNumberOfComponents(); j++) {
          assertTrue(Double.isFinite(fluid.getPhase(phase).getComponent(i).getdfugdn(j)));
        }
      }
    }
  }
}
