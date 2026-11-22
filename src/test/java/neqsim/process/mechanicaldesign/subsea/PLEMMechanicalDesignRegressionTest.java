package neqsim.process.mechanicaldesign.subsea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.subsea.PLEM;

/**
 * Regression checks that PLEM sizing and cost use PLEM assumptions.
 *
 * @author ESOL
 * @version 1.0
 */
class PLEMMechanicalDesignRegressionTest {
  /** Checks the PLEM header requirement at low pressure. */
  @Test
  void plemHeaderUsesOwnMinimumWall() {
    PLEM plem = new PLEM("Low pressure PLEM", 2);
    plem.setDesignPressure(1.0);
    PLEMMechanicalDesign design = plem.getMechanicalDesign();
    design.calcDesign();
    assertEquals(19.05, design.getHeaderWallThickness(), 1e-12);
  }

  /** Checks that changing slots selects PLEM cost bands independently of header diameter. */
  @Test
  void plemCostUsesOwnSlotBands() {
    double[] baseCosts = {800000.0, 1500000.0, 2500000.0};
    int[] slots = {2, 4, 6};
    for (int i = 0; i < slots.length; i++) {
      PLEM plem = new PLEM("PLEM cost band", slots[i]);
      plem.setDryWeight(0.0);
      plem.setBranchIsolationValves(false);
      PLEMMechanicalDesign design = plem.getMechanicalDesign();
      design.setRegion(SubseaCostEstimator.Region.GOM);
      design.calculateCostEstimate();
      assertEquals(baseCosts[i], design.getEquipmentCostUSD(), 1e-9, "slots=" + slots[i]);
    }
  }

  /** Checks the field-level SURF estimator uses the same PLEM slot and valve assumptions. */
  @Test
  void surfPlemLineUsesDedicatedPlemEstimate() {
    SURFCostEstimator surf = new SURFCostEstimator(1, 350.0, SubseaCostEstimator.Region.GOM);
    surf.setNumberOfPLEMs(2);
    surf.setPlemSlots(6);
    surf.setPlemWeightTonnes(45.0);
    surf.setPlemBranchSizeInches(8.0);
    surf.calculate();
    SubseaCostEstimator direct = new SubseaCostEstimator(SubseaCostEstimator.Region.GOM);
    direct.calculatePLEMCost(45.0, 6, 8.0, 350.0, true);
    for (Map<String, Object> item : surf.getLineItems()) {
      if ("PLEMs".equals(item.get("description"))) {
        assertEquals(direct.getTotalCost() * 2, ((Number) item.get("totalCostUSD")).doubleValue(), 1e-7);
        return;
      }
    }
    throw new AssertionError("Missing PLEM cost line");
  }

  /** Checks band boundaries do not depend on the header diameter and invalid slots fail. */
  @Test
  void dedicatedPlemBandBoundariesAndBranchValveCount() {
    SubseaCostEstimator estimator = new SubseaCostEstimator(SubseaCostEstimator.Region.GOM);
    int[] slots = {1, 2, 3, 4, 5, 6};
    double[] bases = {800000, 800000, 1500000, 1500000, 2500000, 2500000};
    for (int i = 0; i < slots.length; i++) {
      estimator.calculatePLEMCost(10.0, slots[i], 8.0, 350.0, true);
      assertEquals(bases[i] + 80000.0 + slots[i] * 8.0 * 25000.0, estimator.getEquipmentCost(), 1e-8);
    }
    assertThrows(IllegalArgumentException.class, () -> estimator.calculatePLEMCost(10.0, 0, 8.0, 350.0, true));
  }

}
