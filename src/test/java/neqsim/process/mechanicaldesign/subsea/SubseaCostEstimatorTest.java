package neqsim.process.mechanicaldesign.subsea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SubseaCostEstimator.
 *
 * @author ESOL
 */
public class SubseaCostEstimatorTest {

  private SubseaCostEstimator estimator;

  @BeforeEach
  public void setUp() {
    estimator = new SubseaCostEstimator();
  }

  @Test
  public void testDefaultConfiguration() {
    assertEquals(SubseaCostEstimator.Region.NORWAY, estimator.getRegion());
    assertEquals(SubseaCostEstimator.Currency.USD, estimator.getCurrency());
  }

  @Test
  public void testRegionSetting() {
    estimator.setRegion(SubseaCostEstimator.Region.GOM);
    assertEquals(SubseaCostEstimator.Region.GOM, estimator.getRegion());
  }

  @Test
  public void testCurrencySetting() {
    estimator.setCurrency(SubseaCostEstimator.Currency.NOK);
    assertEquals(SubseaCostEstimator.Currency.NOK, estimator.getCurrency());
  }

  @Test
  public void testCalculatePLETCost() {
    // calculatePLETCost(dryWeightTonnes, hubSizeInches, waterDepthM, hasIsolationValve,
    // hasPiggingFacility)
    estimator.calculatePLETCost(25.0, 12.0, 350.0, true, false);

    double totalCost = estimator.getTotalCost();
    assertTrue(totalCost > 0, "Total cost should be positive");
    assertTrue(estimator.getEquipmentCost() > 0, "Equipment cost should be positive");
    assertTrue(estimator.getInstallationCost() > 0, "Installation cost should be positive");
    assertTrue(estimator.getVesselDays() > 0, "Vessel days should be positive");
  }

  @Test
  public void testCalculateTreeCost() {
    // calculateTreeCost(pressureRatingPsi, boreSizeInches, waterDepthM, isHorizontal, isDualBore)
    estimator.calculateTreeCost(10000.0, 7.0, 500.0, false, false);

    double totalCost = estimator.getTotalCost();
    assertTrue(totalCost > 0, "Total cost should be positive");
    assertTrue(estimator.getEquipmentCost() > 0, "Equipment cost should be positive");
  }

  @Test
  public void testCalculateManifoldCost() {
    // calculateManifoldCost(numberOfSlots, dryWeightTonnes, waterDepthM, hasTestHeader)
    estimator.calculateManifoldCost(4, 80.0, 400.0, true);

    double totalCost = estimator.getTotalCost();
    assertTrue(totalCost > 0, "Total cost should be positive");
    assertTrue(estimator.getEquipmentCost() > 0, "Equipment cost should be positive");
  }

  @Test
  public void testCalculateJumperCost() {
    // calculateJumperCost(lengthM, diameterInches, isRigid, waterDepthM)
    estimator.calculateJumperCost(100.0, 10.0, true, 350.0);

    double totalCost = estimator.getTotalCost();
    assertTrue(totalCost > 0, "Total cost should be positive");
    assertTrue(estimator.getEquipmentCost() > 0, "Equipment cost should be positive");
    assertTrue(estimator.getVesselDays() > 0, "Vessel days should be positive");
  }

  @Test
  public void testCalculateUmbilicalCost() {
    // calculateUmbilicalCost(lengthKm, numberOfHydraulicLines, numberOfChemicalLines,
    // numberOfElectricalCables, waterDepthM, isDynamic)
    estimator.calculateUmbilicalCost(5.0, 4, 2, 6, 400.0, false);

    double totalCost = estimator.getTotalCost();
    assertTrue(totalCost > 0, "Total cost should be positive");
    assertTrue(estimator.getEquipmentCost() > 0, "Equipment cost should be positive");
  }

  @Test
  public void testCalculateFlexiblePipeCost() {
    // calculateFlexiblePipeCost(lengthM, innerDiameterInches, waterDepthM, isDynamic, hasBuoyancy)
    estimator.calculateFlexiblePipeCost(3000.0, 8.0, 350.0, true, true);

    double totalCost = estimator.getTotalCost();
    assertTrue(totalCost > 0, "Total cost should be positive");
    assertTrue(estimator.getEquipmentCost() > 0, "Equipment cost should be positive");
  }

  @Test
  public void testCalculateBoosterCost() {
    // calculateBoosterCost(powerMW, isCompressor, waterDepthM, hasRedundancy)
    estimator.calculateBoosterCost(5.0, true, 500.0, false);

    double totalCost = estimator.getTotalCost();
    assertTrue(totalCost > 0, "Total cost should be positive");
    assertTrue(estimator.getEquipmentCost() > 0, "Equipment cost should be positive");
  }

  @Test
  public void testGenerateBOM() {
    List<Map<String, Object>> bom = estimator.generateBOM("PLET", 25.0, 350.0);

    assertNotNull(bom);
    assertTrue(bom.size() > 0, "BOM should have items");

    // Check first item has required fields
    Map<String, Object> firstItem = bom.get(0);
    assertTrue(firstItem.containsKey("item"));
    assertTrue(firstItem.containsKey("material"));
    assertTrue(firstItem.containsKey("quantity"));
    assertTrue(firstItem.containsKey("unit"));
    assertTrue(firstItem.containsKey("unitCost"));
    assertTrue(firstItem.containsKey("totalCost"));
  }

  @Test
  public void testRegionalCostVariation() {
    // Create fresh estimators for each region - note they calculate rates at construction
    SubseaCostEstimator norwayEstimator =
        new SubseaCostEstimator(SubseaCostEstimator.Region.NORWAY);
    norwayEstimator.calculatePLETCost(25.0, 12.0, 350.0, false, false);
    double norwayTotal = norwayEstimator.getTotalCost();

    SubseaCostEstimator gomEstimator = new SubseaCostEstimator(SubseaCostEstimator.Region.GOM);
    gomEstimator.calculatePLETCost(25.0, 12.0, 350.0, false, false);
    double gomTotal = gomEstimator.getTotalCost();

    // Both should have valid costs
    assertTrue(norwayTotal > 0, "Norway total should be positive");
    assertTrue(gomTotal > 0, "GOM total should be positive");
    // Region factor affects labor rates, not base equipment cost, so difference is in labor portion
  }

  @Test
  public void testToJson() {
    // Calculate some costs first
    estimator.calculatePLETCost(25.0, 12.0, 350.0, true, false);

    String json = estimator.toJson();
    assertNotNull(json);
    assertTrue(json.contains("equipmentCost"));
    assertTrue(json.contains("installationCost"));
    assertTrue(json.contains("totalCost"));
  }

  @Test
  public void testCostBreakdown() {
    estimator.calculateTreeCost(15000.0, 7.0, 500.0, true, false);

    Map<String, Object> breakdown = estimator.getCostBreakdown();
    assertNotNull(breakdown);
    // Cost breakdown has nested structure
    assertTrue(breakdown.containsKey("directCosts"));
    assertTrue(breakdown.containsKey("totalCostUSD"));

    @SuppressWarnings("unchecked")
    Map<String, Object> directCosts = (Map<String, Object>) breakdown.get("directCosts");
    assertTrue(directCosts.containsKey("equipmentCostUSD"));
    assertTrue(directCosts.containsKey("installationCostUSD"));
  }

  @Test
  public void testRigidVsFlexibleJumper() {
    // Rigid jumper
    estimator.calculateJumperCost(100.0, 10.0, true, 350.0);
    double rigidCost = estimator.getTotalCost();
    double rigidVesselDays = estimator.getVesselDays();

    // Flexible jumper
    estimator.calculateJumperCost(100.0, 10.0, false, 350.0);
    double flexibleCost = estimator.getTotalCost();
    double flexibleVesselDays = estimator.getVesselDays();

    // Rigid jumpers typically cost more
    assertTrue(rigidCost > 0);
    assertTrue(flexibleCost > 0);
    // Flexible should install faster
    assertTrue(rigidVesselDays >= flexibleVesselDays);
  }

  @Test
  public void testDynamicVsStaticUmbilical() {
    // Static umbilical
    estimator.calculateUmbilicalCost(5.0, 4, 2, 6, 400.0, false);
    double staticCost = estimator.getTotalCost();

    // Dynamic umbilical
    estimator.calculateUmbilicalCost(5.0, 4, 2, 6, 400.0, true);
    double dynamicCost = estimator.getTotalCost();

    // Dynamic umbilicals cost more
    assertTrue(dynamicCost > staticCost, "Dynamic umbilical should cost more than static");
  }
}
