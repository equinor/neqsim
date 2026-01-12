package neqsim.process.fielddevelopment.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.evaluation.DecommissioningEstimator.CostItem;
import neqsim.process.fielddevelopment.evaluation.DecommissioningEstimator.FacilityType;
import neqsim.process.fielddevelopment.evaluation.DecommissioningEstimator.PipelineStrategy;

/**
 * Unit tests for DecommissioningEstimator.
 *
 * <p>
 * Tests cost estimation for various facility types and decommissioning scenarios.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class DecommissioningEstimatorTest {
  private DecommissioningEstimator estimator;

  @BeforeEach
  void setUp() {
    estimator = new DecommissioningEstimator();
  }

  @Test
  void testDefaultConfiguration() {
    double cost = estimator.getTotalCostMUSD();

    assertTrue(cost > 0, "Default cost should be positive");
    // Typical small platform: 50-200 MUSD
    assertTrue(cost > 30 && cost < 500, "Default cost should be in typical range");
  }

  @Test
  void testFacilityTypeConfiguration() {
    estimator.setFacilityType(FacilityType.FIXED_JACKET).setWaterDepth(100).setTopsideWeight(15000)
        .setSubstructureWeight(12000).setNumberOfWells(8).setPipelineLength(30);

    double cost = estimator.getTotalCostMUSD();

    assertTrue(cost > 50, "Configured platform should have significant cost");
    assertTrue(cost < 500, "Cost should be reasonable");
  }

  @Test
  void testWellPACost() {
    estimator.setNumberOfWells(10).setAverageWellDepth(3000);

    double wellCost = estimator.getWellPACostMUSD();

    // 10 wells at ~12 MUSD each = 120 MUSD
    assertTrue(wellCost > 80, "10 wells should cost > 80 MUSD");
    assertTrue(wellCost < 200, "10 wells should cost < 200 MUSD");
  }

  @Test
  void testWellDepthImpact() {
    estimator.setNumberOfWells(5);

    // Shallow wells
    estimator.setAverageWellDepth(1500);
    double shallowCost = estimator.getWellPACostMUSD();

    // Deep wells
    estimator.setAverageWellDepth(5000);
    double deepCost = estimator.getWellPACostMUSD();

    assertTrue(deepCost > shallowCost, "Deep wells should cost more than shallow");
  }

  @Test
  void testWaterDepthImpact() {
    estimator.setNumberOfWells(6).setTopsideWeight(10000);

    // Shallow water
    estimator.setWaterDepth(50);
    double shallowCost = estimator.getTotalCostMUSD();

    // Deep water
    estimator.setWaterDepth(300);
    double deepCost = estimator.getTotalCostMUSD();

    assertTrue(deepCost > shallowCost, "Deep water should cost more");
  }

  @Test
  void testTopsideRemovalCost() {
    estimator.setTopsideWeight(20000).setFacilityType(FacilityType.FIXED_JACKET);

    double topsideCost = estimator.getTopsideRemovalCostMUSD();

    assertTrue(topsideCost > 0, "Topside cost should be positive");
    // 20,000 tonnes at ~0.015 MUSD/tonne = 300 MUSD, but with minimums and factors
    assertTrue(topsideCost > 100, "20kT topside should be expensive");
  }

  @Test
  void testFPSOCosts() {
    estimator.setFacilityType(FacilityType.FPSO).setTopsideWeight(30000).setNumberOfWells(12);

    double fpsoTotal = estimator.getTotalCostMUSD();
    double substructureCost = estimator.getSubstructureRemovalCostMUSD();

    // FPSO can sail away - substructure cost is minimal
    assertTrue(substructureCost < 30, "FPSO substructure cost should be low (sail away)");
    assertTrue(fpsoTotal > 0, "FPSO total should be positive");
  }

  @Test
  void testSubseaTiebackCosts() {
    estimator.setFacilityType(FacilityType.SUBSEA_TIEBACK).setNumberOfWells(4)
        .setNumberOfSubseaStructures(2).setPipelineLength(15);

    double subseaTotal = estimator.getTotalCostMUSD();

    // Subsea has no platform - should be cheaper
    assertTrue(subseaTotal > 30, "Subsea should have some cost");
    assertTrue(subseaTotal < 200, "Subsea should be less than fixed platform");
  }

  @Test
  void testPipelineStrategies() {
    estimator.setPipelineLength(50).setPipelineDiameter(24);

    double lipCost = estimator.getPipelineDecomCostMUSD(PipelineStrategy.LEAVE_IN_PLACE);
    double buryCost = estimator.getPipelineDecomCostMUSD(PipelineStrategy.TRENCH_BURY);
    double removeCost = estimator.getPipelineDecomCostMUSD(PipelineStrategy.FULL_REMOVAL);

    assertTrue(lipCost < buryCost, "Leave in place should be cheaper than bury");
    assertTrue(buryCost < removeCost, "Bury should be cheaper than removal");
  }

  @Test
  void testPipelineDiameterImpact() {
    estimator.setPipelineLength(20);

    estimator.setPipelineDiameter(8);
    double smallPipeCost = estimator.getPipelineDecomCostMUSD();

    estimator.setPipelineDiameter(36);
    double largePipeCost = estimator.getPipelineDecomCostMUSD();

    assertTrue(largePipeCost > smallPipeCost, "Larger diameter should cost more");
  }

  @Test
  void testCostBreakdown() {
    estimator.setFacilityType(FacilityType.FIXED_JACKET).setNumberOfWells(6).setTopsideWeight(12000)
        .setPipelineLength(25);

    List<CostItem> breakdown = estimator.getCostBreakdown();

    assertNotNull(breakdown, "Breakdown should not be null");
    assertEquals(5, breakdown.size(), "Should have 5 cost categories");

    double sum = 0;
    for (CostItem item : breakdown) {
      assertNotNull(item.getCategory(), "Category should not be null");
      assertTrue(item.getCostMUSD() >= 0, "Cost should be non-negative");
      sum += item.getCostMUSD();
    }

    assertEquals(estimator.getTotalCostMUSD(), sum, 0.1, "Breakdown should sum to total");
  }

  @Test
  void testScheduleEstimation() {
    estimator.setFacilityType(FacilityType.FIXED_JACKET).setNumberOfWells(8)
        .setTopsideWeight(15000);

    int months = estimator.getEstimatedDurationMonths();

    assertTrue(months > 12, "Large platform should take > 1 year");
    assertTrue(months < 60, "Should take < 5 years");
  }

  @Test
  void testGBSHigherCost() {
    estimator.setWaterDepth(100).setSubstructureWeight(50000);

    estimator.setFacilityType(FacilityType.FIXED_JACKET);
    double jacketCost = estimator.getSubstructureRemovalCostMUSD();

    estimator.setFacilityType(FacilityType.GRAVITY_BASED);
    double gbsCost = estimator.getSubstructureRemovalCostMUSD();

    assertTrue(gbsCost > jacketCost, "GBS should cost more than jacket");
  }

  @Test
  void testReportGeneration() {
    estimator.setFacilityType(FacilityType.FIXED_JACKET).setWaterDepth(120).setNumberOfWells(10)
        .setTopsideWeight(18000).setPipelineLength(40);

    String report = estimator.generateReport();

    assertNotNull(report, "Report should not be null");
    assertTrue(report.contains("Decommissioning"), "Report should have title");
    assertTrue(report.contains("Cost"), "Report should show costs");
    assertTrue(report.contains("Well"), "Report should mention wells");
    assertTrue(report.contains("MUSD"), "Report should show currency");
  }

  @Test
  void testFacilityTypeFactors() {
    // All facility types should have positive cost factors
    for (FacilityType type : FacilityType.values()) {
      assertTrue(type.getCostFactor() > 0, "Cost factor should be positive for " + type);
      assertNotNull(type.getDisplayName(), "Display name should not be null for " + type);
    }
  }

  @Test
  void testPipelineStrategyFactors() {
    for (PipelineStrategy strategy : PipelineStrategy.values()) {
      assertTrue(strategy.getCostFactor() > 0, "Cost factor should be positive for " + strategy);
      assertNotNull(strategy.getDisplayName(), "Display name should not be null for " + strategy);
    }
  }

  @Test
  void testTotalWithPipelineStrategy() {
    estimator.setPipelineLength(30);

    double totalLIP = estimator.getTotalCostMUSD(PipelineStrategy.LEAVE_IN_PLACE);
    double totalRemove = estimator.getTotalCostMUSD(PipelineStrategy.FULL_REMOVAL);

    assertTrue(totalRemove > totalLIP, "Full removal should cost more overall");
  }

  @Test
  void testCostItemProperties() {
    CostItem item = new CostItem("Test Category", 50.0, "Test notes");

    assertEquals("Test Category", item.getCategory());
    assertEquals(50.0, item.getCostMUSD(), 0.01);
    assertEquals("Test notes", item.getNotes());
  }

  @Test
  void testTypicalNCSPlatform() {
    // Typical medium-sized NCS platform
    estimator.setFacilityType(FacilityType.FIXED_JACKET).setWaterDepth(120).setTopsideWeight(20000)
        .setSubstructureWeight(15000).setNumberOfWells(15).setAverageWellDepth(3500)
        .setPipelineLength(50).setPipelineDiameter(20).setNumberOfRisers(4);

    double totalCost = estimator.getTotalCostMUSD();

    // Typical NCS decommissioning: 200-500 MUSD for medium platform
    assertTrue(totalCost > 150, "NCS platform should be > 150 MUSD");
    assertTrue(totalCost < 700, "NCS platform should be < 700 MUSD");

    // Well P&A often dominates
    double wellCost = estimator.getWellPACostMUSD();
    assertTrue(wellCost / totalCost > 0.2, "Wells should be significant fraction of cost");
  }
}
