package neqsim.process.mechanicaldesign.subsea;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SURFCostEstimator}.
 *
 * @author ESOL
 * @version 1.0
 */
class SURFCostEstimatorTest {

  /**
   * Test a typical NCS subsea tieback: 6 wells, 300m water depth, 10 km infield + 80 km export.
   */
  @Test
  void testNorwegianSubseaTieback() {
    SURFCostEstimator est = new SURFCostEstimator(6, 300.0, SubseaCostEstimator.Region.NORWAY);

    // Subsea trees
    est.setTreePressureRatingPsi(10000.0);
    est.setTreeBoreSizeInches(5.0);
    est.setHorizontalTrees(true);

    // Manifold — one 6-slot manifold
    est.setManifoldSlots(6);
    est.setManifoldWeightTonnes(140.0);

    // 2 PLETs (pipeline end terminations)
    est.setNumberOfPLETs(2);
    est.setPletWeightTonnes(25.0);

    // 6 jumpers (well to manifold)
    est.setNumberOfJumpers(6);
    est.setJumperLengthM(30.0);
    est.setJumperDiameterInches(6.0);

    // Control umbilical (10 km)
    est.setUmbilicalLengthKm(10.0);
    est.setUmbilicalDynamic(true);

    // Riser (flexible, 8")
    est.setIncludeRisers(true);
    est.setFlexibleRiser(true);
    est.setRiserDiameterInches(8.0);
    est.setNumberOfProductionRisers(1);

    // Infield flowline (rigid, 14", 10 km)
    est.setInfieldFlowlineLengthKm(10.0);
    est.setInfieldFlowlineDiameterInches(14.0);
    est.setInfieldFlowlineFlexible(false);

    // Export pipeline (rigid, 24", 80 km, X65, 165 bar)
    est.setExportPipelineLengthKm(80.0);
    est.setExportPipelineDiameterInches(24.0);
    est.setPipelineMaterialGrade("X65");
    est.setPipelineDesignPressureBar(165.0);
    est.setPipelineInstallMethod("S-lay");

    est.setContingencyPct(0.15);

    double totalUSD = est.calculate();

    // Total SURF should be in a reasonable range for a 6-well NCS tieback
    // Typically 300–700 MUSD for this configuration
    assertTrue(totalUSD > 100e6, "Total SURF should exceed 100 MUSD, got " + totalUSD / 1e6);
    assertTrue(totalUSD < 2000e6, "Total SURF should be below 2000 MUSD, got " + totalUSD / 1e6);

    // All four SURF categories should have non-zero costs
    assertTrue(est.getSubseaCostUSD() > 0, "Subsea cost should be > 0");
    assertTrue(est.getUmbilicalCostUSD() > 0, "Umbilical cost should be > 0");
    assertTrue(est.getRiserCostUSD() > 0, "Riser cost should be > 0");
    assertTrue(est.getFlowlineCostUSD() > 0, "Flowline cost should be > 0");

    // Sum of categories should equal total
    double categorySum = est.getSubseaCostUSD() + est.getUmbilicalCostUSD() + est.getRiserCostUSD()
        + est.getFlowlineCostUSD();
    assertEquals(totalUSD, categorySum, 1.0, "Category sum should equal total");

    // Flowlines typically dominate for long export pipelines (>50%)
    double flowlinePct = est.getFlowlineCostUSD() / totalUSD * 100;
    assertTrue(flowlinePct > 30, "Flowlines should be >30% of SURF, got " + flowlinePct + "%");

    System.out.println("=== SURF Cost Estimate (NCS 6-well tieback) ===");
    System.out.println("  S (Subsea) : " + String.format("%,.0f", est.getSubseaCostUSD()) + " USD");
    System.out
        .println("  U (Umbilicals): " + String.format("%,.0f", est.getUmbilicalCostUSD()) + " USD");
    System.out
        .println("  R (Risers)   : " + String.format("%,.0f", est.getRiserCostUSD()) + " USD");
    System.out
        .println("  F (Flowlines): " + String.format("%,.0f", est.getFlowlineCostUSD()) + " USD");
    System.out.println("  TOTAL SURF   : " + String.format("%,.0f", totalUSD) + " USD");
    System.out.println(
        "  TOTAL NOK    : " + String.format("%,.0f", est.getTotalCostInCurrency(10.5)) + " NOK");
  }

  /**
   * Test JSON output contains all required sections.
   */
  @Test
  void testJsonOutput() {
    SURFCostEstimator est = new SURFCostEstimator(4, 200.0, SubseaCostEstimator.Region.GOM);
    est.setNumberOfPLETs(2);
    est.setNumberOfJumpers(4);
    est.setExportPipelineLengthKm(30.0);
    est.setInfieldFlowlineLengthKm(5.0);
    est.calculate();

    String json = est.toJson();
    assertNotNull(json);
    assertTrue(json.contains("categorySummary"));
    assertTrue(json.contains("categoryPercentages"));
    assertTrue(json.contains("lineItems"));
    assertTrue(json.contains("fieldConfiguration"));
    assertTrue(json.contains("totalVesselDays"));
    assertTrue(json.contains("subsea_S_USD"));
    assertTrue(json.contains("umbilical_U_USD"));
    assertTrue(json.contains("riser_R_USD"));
    assertTrue(json.contains("flowline_F_USD"));
  }

  /**
   * Test cost breakdown map and line items.
   */
  @Test
  void testCostBreakdownMap() {
    SURFCostEstimator est = new SURFCostEstimator(4, 300.0, SubseaCostEstimator.Region.NORWAY);
    est.setNumberOfPLETs(2);
    est.setNumberOfJumpers(4);
    est.calculate();

    Map<String, Object> breakdown = est.getCostBreakdown();
    assertNotNull(breakdown.get("categorySummary"));
    assertNotNull(breakdown.get("lineItems"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) breakdown.get("lineItems");
    assertTrue(items.size() >= 4, "Should have at least 4 line items");

    // Check category filter
    List<Map<String, Object>> subseaItems = est.getCategoryLineItems("S");
    assertTrue(subseaItems.size() >= 2, "Should have trees + manifold at minimum");
  }

  /**
   * Test that region factors change the cost meaningfully.
   */
  @Test
  void testRegionCostAdjustment() {
    SURFCostEstimator estNorway =
        new SURFCostEstimator(4, 300.0, SubseaCostEstimator.Region.NORWAY);
    estNorway.setNumberOfJumpers(4);
    estNorway.setNumberOfPLETs(2);
    estNorway.calculate();

    SURFCostEstimator estGOM = new SURFCostEstimator(4, 300.0, SubseaCostEstimator.Region.GOM);
    estGOM.setNumberOfJumpers(4);
    estGOM.setNumberOfPLETs(2);
    estGOM.calculate();

    // Norway should be more expensive than GOM (factor 1.35 vs 1.0)
    assertTrue(estNorway.getTotalSURFCostUSD() > estGOM.getTotalSURFCostUSD(),
        "Norway SURF should be more expensive than GOM");
  }

  /**
   * Test currency conversion.
   */
  @Test
  void testCurrencyConversion() {
    SURFCostEstimator est = new SURFCostEstimator(4, 300.0, SubseaCostEstimator.Region.NORWAY);
    est.setNumberOfJumpers(4);
    est.setNumberOfPLETs(2);
    est.calculate();

    double totalUSD = est.getTotalSURFCostUSD();
    double totalNOK = est.getTotalCostInCurrency(10.5);
    assertEquals(totalUSD * 10.5, totalNOK, 1.0);
  }

  /**
   * Test that re-calling calculate() resets previous results.
   */
  @Test
  void testRecalculate() {
    SURFCostEstimator est = new SURFCostEstimator(4, 300.0, SubseaCostEstimator.Region.NORWAY);
    est.setNumberOfJumpers(4);
    est.setNumberOfPLETs(2);
    est.calculate();
    double first = est.getTotalSURFCostUSD();

    // Change a parameter and recalculate
    est.setNumberOfWells(8);
    est.setNumberOfJumpers(8);
    est.calculate();
    double second = est.getTotalSURFCostUSD();

    // More wells = more trees + jumpers = higher cost
    assertTrue(second > first, "8 wells should cost more than 4 wells");
  }
}
