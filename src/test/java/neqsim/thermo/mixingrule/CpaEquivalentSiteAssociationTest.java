package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import neqsim.thermo.component.ComponentSrkCPA;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/** Equivalent sites must admit bonds between molecules of the same component. */
class CpaEquivalentSiteAssociationTest {
  @Test
  void oneAndTwoEquivalentSitesAdmitSelfAssociation() {
    CPAMixingRuleHandler handler = new CPAMixingRuleHandler();
    for (String name : new String[] {"asphaltene", "H2S"}) {
      SystemSrkCPAstatoil system = new SystemSrkCPAstatoil(300.0, 20.0);
      system.addComponent(name, 1.0);
      system.setMixingRule(10);
      int[][] matrix = handler.setAssociationScheme(0, system.getPhase(0));
      int sites = system.getPhase(0).getComponent(0).getNumberOfAssociationSites();
      assertTrue(sites > 0);
      assertEquals(sites, matrix.length);
      for (int i = 0; i < sites; i++) {
        for (int j = 0; j < sites; j++) {
          assertEquals(1, matrix[i][j]);
          assertEquals(1, handler.setCrossAssociationScheme(0, 0, system.getPhase(0))[i][j]);
        }
      }
      system.init(0);
      system.init(2);
      ComponentSrkCPA component = (ComponentSrkCPA) system.getPhase(1).getComponent(0);
      for (int i = 0; i < sites; i++) {
        assertTrue(component.getXsite()[i] > 0 && component.getXsite()[i] < 1,
            name + " must have a nonzero bonded fraction");
      }
    }
  }

  @Test
  void donorAcceptorSchemesRetainSelectionRules() {
    CPAMixingRuleHandler handler = new CPAMixingRuleHandler();
    SystemSrkCPAstatoil system = new SystemSrkCPAstatoil(300.0, 20.0);
    system.addComponent("water", 1.0);
    system.addComponent("methanol", 1.0);
    system.setMixingRule(10);
    assertArrayEquals(new int[] {0, 0, 1, 1}, handler.setAssociationScheme(0, system.getPhase(0))[0]);
    assertArrayEquals(new int[] {0, 1}, handler.setAssociationScheme(1, system.getPhase(0))[0]);
    assertArrayEquals(new int[] {0, 1}, handler.setCrossAssociationScheme(0, 1, system.getPhase(0))[0]);
  }
}
