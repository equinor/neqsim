package neqsim.process.mechanicaldesign.subsea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.Map;
import java.util.List;
import java.util.Collections;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Regression checks for deterministic regional cost adjustment.
 *
 * @author ESOL
 * @version 1.0
 */
class SubseaRegionalCostRegressionTest {
  /** Checks documented regional totals for identical PLET inputs. */
  @Test
  void documentedPletComparisonHasRegionalCosts() {
    double[] factors = {1.35, 1.25, 1.0, 0.85, 1.1};
    int i = 0;
    for (SubseaCostEstimator.Region region : SubseaCostEstimator.Region.values()) {
      SubseaCostEstimator estimator = new SubseaCostEstimator(region);
      estimator.calculatePLETCost(25.0, 12.0, 350.0, true, false);
      assertEquals(3383875.0 * factors[i++], estimator.getTotalCost(), 1e-7, region.name());
    }
  }

  /** Checks repeated region setters do not compound hourly rates or change manhours. */
  @Test
  void repeatedRegionSelectionIsIdempotent() {
    SubseaCostEstimator estimator = new SubseaCostEstimator(SubseaCostEstimator.Region.GOM);
    estimator.calculatePLETCost(25.0, 12.0, 350.0, true, false);
    double baseCost = estimator.getTotalCost();
    double baseManhours = estimator.getTotalManhours();
    estimator.setRegion(SubseaCostEstimator.Region.NORWAY);
    assertEquals(baseCost * 1.35, estimator.getTotalCost(), 1e-7);
    estimator.setRegion(SubseaCostEstimator.Region.NORWAY);
    estimator.calculatePLETCost(25.0, 12.0, 350.0, true, false);
    assertEquals(baseCost * 1.35, estimator.getTotalCost(), 1e-7);
    assertEquals(baseManhours, estimator.getTotalManhours(), 1e-9);
    estimator.setRegion(SubseaCostEstimator.Region.GOM);
    assertEquals(baseCost, estimator.getTotalCost(), 1e-7);
  }

  /** Checks default and explicit Norway constructors share the same model. */
  @Test
  void defaultConstructorMatchesNorway() {
    SubseaCostEstimator implicit = new SubseaCostEstimator();
    SubseaCostEstimator explicit = new SubseaCostEstimator(SubseaCostEstimator.Region.NORWAY);
    implicit.calculatePLETCost(25.0, 12.0, 350.0, true, false);
    explicit.calculatePLETCost(25.0, 12.0, 350.0, true, false);
    assertEquals(explicit.getTotalCost(), implicit.getTotalCost(), 1e-9);
    assertEquals(explicit.getTotalManhours(), implicit.getTotalManhours(), 1e-9);
  }

  /** Checks component sums and contingency reconcile to the reported regional total. */
  @Test
  @SuppressWarnings("unchecked")
  void regionalBreakdownReconciles() {
    SubseaCostEstimator estimator = new SubseaCostEstimator(SubseaCostEstimator.Region.NORWAY);
    estimator.calculatePLETCost(25.0, 12.0, 350.0, true, false);
    Map<String, Object> costs = estimator.getCostBreakdown();
    Map<String, Object> direct = (Map<String, Object>) costs.get("directCosts");
    Map<String, Object> indirect = (Map<String, Object>) costs.get("indirectCosts");
    double sum = (Double) costs.get("contingencyUSD");
    for (Object value : direct.values()) {
      sum += (Double) value;
    }
    for (Object value : indirect.values()) {
      sum += (Double) value;
    }
    assertEquals(estimator.getTotalCost(), sum, 1e-8);
    assertEquals(1300000.0 * 1.35, estimator.getEquipmentCost(), 1e-8);
  }

  /** Checks every equipment estimator applies the same monetary factor and preserves effort. */
  @Test
  void allEquipmentCostMethodsUseRegionalAdjustment() {
    assertRegionalEstimate(estimator -> estimator.calculatePLETCost(25.0, 12.0, 350.0, true, false));
    assertRegionalEstimate(estimator -> estimator.calculatePLEMCost(45.0, 4, 8.0, 350.0, true));
    assertRegionalEstimate(estimator -> estimator.calculateTreeCost(10000.0, 7.0, 500.0, false, false));
    assertRegionalEstimate(estimator -> estimator.calculateManifoldCost(4, 80.0, 400.0, true));
    assertRegionalEstimate(estimator -> estimator.calculateJumperCost(100.0, 10.0, true, 350.0));
    assertRegionalEstimate(estimator -> estimator.calculateJumperCost(100.0, 10.0, false, 350.0));
    assertRegionalEstimate(estimator -> estimator.calculateUmbilicalCost(5.0, 4, 2, 6, 400.0, false));
    assertRegionalEstimate(estimator -> estimator.calculateFlexiblePipeCost(3000.0, 8.0, 350.0, true, true));
    assertRegionalEstimate(estimator -> estimator.calculateBoosterCost(5.0, true, 500.0, false));
  }

  /** Checks BOM quantities and monetary rates reconcile after region selection. */
  @Test
  void billOfMaterialsUsesRegionalUnitRates() {
    SubseaCostEstimator estimator = new SubseaCostEstimator(SubseaCostEstimator.Region.GOM);
    estimator.calculatePLETCost(25.0, 12.0, 350.0, true, false);
    List<Map<String, Object>> baseline = estimator.generateBillOfMaterials("PLET", Collections.emptyMap());
    estimator.setRegion(SubseaCostEstimator.Region.NORWAY);
    List<Map<String, Object>> regional = estimator.generateBillOfMaterials("PLET", Collections.emptyMap());
    for (int i = 0; i < baseline.size(); i++) {
      Map<String, Object> reference = baseline.get(i);
      Map<String, Object> actual = regional.get(i);
      assertEquals(reference.get("quantity"), actual.get("quantity"));
      assertEquals(((Number) reference.get("totalCostUSD")).doubleValue() * 1.35,
          ((Number) actual.get("totalCostUSD")).doubleValue(), 1e-7);
      assertEquals(((Number) actual.get("quantity")).doubleValue() * ((Number) actual.get("unitCostUSD")).doubleValue(),
          ((Number) actual.get("totalCostUSD")).doubleValue(), 1e-7);
    }
    List<Map<String, Object>> materialBom = estimator.generateBOM("PLET", 25.0, 350.0);
    estimator.setRegion(SubseaCostEstimator.Region.GOM);
    List<Map<String, Object>> baseMaterialBom = estimator.generateBOM("PLET", 25.0, 350.0);
    for (int i = 0; i < materialBom.size(); i++) {
      assertEquals(((Number) baseMaterialBom.get(i).get("totalCost")).doubleValue() * 1.35,
          ((Number) materialBom.get(i).get("totalCost")).doubleValue(), 1e-7);
    }
  }

  /**
   * Compares identical equipment estimates against the baseline regional model.
   *
   * @param calculation equipment-specific estimate operation
   */
  private void assertRegionalEstimate(Consumer<SubseaCostEstimator> calculation) {
    SubseaCostEstimator baseline = new SubseaCostEstimator(SubseaCostEstimator.Region.GOM);
    SubseaCostEstimator regional = new SubseaCostEstimator(SubseaCostEstimator.Region.NORWAY);
    calculation.accept(baseline);
    calculation.accept(regional);
    assertEquals(baseline.getTotalCost() * 1.35, regional.getTotalCost(), 1e-6);
    assertEquals(baseline.getEquipmentCost() * 1.35, regional.getEquipmentCost(), 1e-6);
    assertEquals(baseline.getFabricationCost() * 1.35, regional.getFabricationCost(), 1e-6);
    assertEquals(baseline.getInstallationCost() * 1.35, regional.getInstallationCost(), 1e-6);
    assertEquals(baseline.getVesselDays(), regional.getVesselDays(), 1e-10);
    assertEquals(baseline.getTotalManhours(), regional.getTotalManhours(), 1e-10);
    double originalCost = regional.getTotalCost();
    calculation.accept(regional);
    assertEquals(originalCost, regional.getTotalCost(), 1e-6);
  }

}
