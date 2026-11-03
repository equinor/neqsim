package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.*;
import org.apache.commons.lang3.SerializationUtils;
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

  @Test
  void fourSiteSinglePolaritySchemesAreComplementaryAndSerializable() {
    CPAMixingRuleHandler handler = new CPAMixingRuleHandler();
    SystemSrkCPAstatoil system = new SystemSrkCPAstatoil(300.0, 20.0);
    system.addComponent("water", 1.0);
    system.addComponent("MEG", 1.0);
    system.setMixingRule(10);

    for (int phase = 0; phase < system.getNumberOfPhases(); phase++) {
      assertEquals(4, system.getPhase(phase).getComponent(0).getOrginalNumberOfAssociationSites());
      assertEquals(4, system.getPhase(phase).getComponent(1).getOrginalNumberOfAssociationSites());
      system.getPhase(phase).getComponent(0).setAssociationScheme("4P");
      system.getPhase(phase).getComponent(1).setAssociationScheme("4N");
    }

    int[][] positiveSelf = handler.setAssociationScheme(0, system.getPhase(0));
    int[][] negativeSelf = handler.setAssociationScheme(1, system.getPhase(0));
    int[][] positiveNegative = handler.setCrossAssociationScheme(0, 1, system.getPhase(0));
    int[][] negativePositive = handler.setCrossAssociationScheme(1, 0, system.getPhase(0));

    assertMatrixValue(positiveSelf, 4, 4, 0);
    assertMatrixValue(negativeSelf, 4, 4, 0);
    assertMatrixValue(positiveNegative, 4, 4, 1);
    assertMatrixValue(negativePositive, 4, 4, 1);

    CPAMixingRuleHandler restored = SerializationUtils.clone(handler);
    assertMatrixValue(restored.setAssociationScheme(0, system.getPhase(0)), 4, 4, 0);
    assertMatrixValue(restored.setCrossAssociationScheme(0, 1, system.getPhase(0)), 4, 4, 1);

    // Rebuild the phase matrices after the in-memory scheme round trip, then exercise
    // the ordinary CPA temperature/composition derivative initialization path.
    system.setMixingRule(10);
    system.init(0);
    system.init(1);
    system.init(2);
    system.init(3);
    assertTrue(Double.isFinite(system.getEnthalpy()));
    assertEquals("4P", system.getPhase(0).getComponent(0).getAssociationScheme());
    assertEquals("4N", system.getPhase(0).getComponent(1).getAssociationScheme());

    SystemSrkCPAstatoil restoredSystem = SerializationUtils.clone(system);
    restoredSystem.init(3);
    assertTrue(Double.isFinite(restoredSystem.getEnthalpy()));
    assertEquals("4P", restoredSystem.getPhase(0).getComponent(0).getAssociationScheme());
    assertEquals("4N", restoredSystem.getPhase(0).getComponent(1).getAssociationScheme());
  }

  private static void assertMatrixValue(int[][] matrix, int rows, int columns, int expected) {
    assertEquals(rows, matrix.length);
    for (int[] row : matrix) {
      assertEquals(columns, row.length);
      for (int value : row) {
        assertEquals(expected, value);
      }
    }
  }

}
