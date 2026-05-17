package neqsim.process.util.fielddevelopment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BiorefineryCostEstimator}.
 */
class BiorefineryCostEstimatorTest {

  @Test
  void testSingleDigesterCost() {
    BiorefineryCostEstimator est = new BiorefineryCostEstimator();
    est.addEquipment(BiorefineryCostEstimator.BiorefineryEquipment.ANAEROBIC_DIGESTER, 5000.0);
    est.calculate();

    // At base capacity, PEC should equal base cost (2500 kUSD) * location(1.0) * escalation(1.0)
    assertEquals(2500e3, est.getTotalPurchasedEquipmentCostUSD(), 1.0,
        "PEC at base capacity should be base cost");
    // Installed = PEC * 2.5 (installation factor for digester)
    assertEquals(2500e3 * 2.5, est.getTotalInstalledCostUSD(), 1.0);
    // CAPEX = installed * (1 + 15% contingency)
    assertEquals(2500e3 * 2.5 * 1.15, est.getTotalCapexUSD(), 1.0);
  }

  @Test
  void testScalingUpIncreaseCost() {
    BiorefineryCostEstimator small = new BiorefineryCostEstimator();
    small.addEquipment(BiorefineryCostEstimator.BiorefineryEquipment.BIOMASS_GASIFIER, 500.0);
    small.calculate();

    BiorefineryCostEstimator large = new BiorefineryCostEstimator();
    large.addEquipment(BiorefineryCostEstimator.BiorefineryEquipment.BIOMASS_GASIFIER, 2000.0);
    large.calculate();

    assertTrue(large.getTotalCapexUSD() > small.getTotalCapexUSD(),
        "Larger capacity should cost more");
    // Economy of scale: doubling capacity shouldn't double cost
    double ratio = large.getTotalCapexUSD() / small.getTotalCapexUSD();
    assertTrue(ratio < 4.0,
        "Economy of scale: 4x capacity should be less than 4x cost, got " + ratio);
  }

  @Test
  void testMultipleEquipment() {
    BiorefineryCostEstimator est = new BiorefineryCostEstimator();
    est.addEquipment(BiorefineryCostEstimator.BiorefineryEquipment.ANAEROBIC_DIGESTER, 8000.0);
    est.addEquipment(BiorefineryCostEstimator.BiorefineryEquipment.BIOGAS_UPGRADER, 750.0);
    est.addEquipment(BiorefineryCostEstimator.BiorefineryEquipment.FEEDSTOCK_HANDLING, 50000.0);
    est.calculate();

    assertTrue(est.getTotalCapexUSD() > 0.0, "CAPEX should be positive");
    assertTrue(est.getTotalInstalledCostUSD() > est.getTotalPurchasedEquipmentCostUSD(),
        "Installed cost should exceed purchased cost");
  }

  @Test
  void testFeedstockCost() {
    BiorefineryCostEstimator est = new BiorefineryCostEstimator();
    est.addEquipment(BiorefineryCostEstimator.BiorefineryEquipment.ANAEROBIC_DIGESTER, 5000.0);
    est.setBiomassPrice(40.0); // USD/tonne
    est.setBiomassConsumptionTonnesPerYear(50000.0);
    est.calculate();

    assertEquals(40.0 * 50000.0, est.getAnnualFeedstockCostUSD(), 1.0,
        "Feedstock cost = price * consumption");
    assertTrue(est.getAnnualOpexUSD() > est.getAnnualFeedstockCostUSD(),
        "Total OPEX should exceed feedstock cost alone");
  }

  @Test
  void testRevenueCalculation() {
    BiorefineryCostEstimator est = new BiorefineryCostEstimator();
    est.addEquipment(BiorefineryCostEstimator.BiorefineryEquipment.BIOGAS_UPGRADER, 500.0);
    est.setProductPrice(0.80);
    est.setAnnualProductionNm3(4.0e6);
    est.calculate();

    assertEquals(0.80 * 4.0e6, est.getAnnualRevenueUSD(), 1.0);
  }

  @Test
  void testLCOE() {
    BiorefineryCostEstimator est = new BiorefineryCostEstimator();
    est.addEquipment(BiorefineryCostEstimator.BiorefineryEquipment.ANAEROBIC_DIGESTER, 5000.0);
    est.addEquipment(BiorefineryCostEstimator.BiorefineryEquipment.BIOGAS_UPGRADER, 500.0);
    est.setBiomassPrice(30.0);
    est.setBiomassConsumptionTonnesPerYear(40000.0);
    est.setProductPrice(0.80);
    est.setAnnualProductionNm3(4.0e6);
    est.calculate();

    double lcoe = est.getLCOE();
    assertTrue(lcoe > 0.0, "LCOE should be positive");
    assertTrue(lcoe < 10.0, "LCOE should be reasonable (< 10 USD/Nm3), got " + lcoe);
  }

  @Test
  void testLocationFactor() {
    BiorefineryCostEstimator usgc = new BiorefineryCostEstimator();
    usgc.addEquipment(BiorefineryCostEstimator.BiorefineryEquipment.BIOMASS_GASIFIER, 1000.0);
    usgc.setLocationFactor(1.0);
    usgc.calculate();

    BiorefineryCostEstimator nordic = new BiorefineryCostEstimator();
    nordic.addEquipment(BiorefineryCostEstimator.BiorefineryEquipment.BIOMASS_GASIFIER, 1000.0);
    nordic.setLocationFactor(1.5); // Scandinavian cost premium
    nordic.calculate();

    assertTrue(nordic.getTotalCapexUSD() > usgc.getTotalCapexUSD(),
        "Higher location factor should give higher CAPEX");
    double ratio = nordic.getTotalCapexUSD() / usgc.getTotalCapexUSD();
    assertEquals(1.5, ratio, 0.01, "CAPEX ratio should equal location factor ratio");
  }

  @Test
  void testToDCFCalculator() {
    BiorefineryCostEstimator est = new BiorefineryCostEstimator();
    est.addEquipment(BiorefineryCostEstimator.BiorefineryEquipment.ANAEROBIC_DIGESTER, 5000.0);
    est.addEquipment(BiorefineryCostEstimator.BiorefineryEquipment.BIOGAS_UPGRADER, 500.0);
    est.setBiomassPrice(30.0);
    est.setBiomassConsumptionTonnesPerYear(40000.0);
    est.setProductPrice(0.80);
    est.setAnnualProductionNm3(4.0e6);
    est.calculate();

    DCFCalculator dcf = est.toDCFCalculator(20, 0.08);
    assertNotNull(dcf, "DCFCalculator should be created");
    dcf.calculate();

    // DCF should compute a finite NPV
    assertTrue(Double.isFinite(dcf.getNPV()), "NPV should be finite");
  }

  @Test
  void testGetResults() {
    BiorefineryCostEstimator est = new BiorefineryCostEstimator();
    est.addEquipment(BiorefineryCostEstimator.BiorefineryEquipment.PYROLYSIS_REACTOR, 500.0);
    est.setProduct(1.20, 10000.0, "tonnes");
    est.setBiomassPrice(25.0);
    est.setBiomassConsumptionTonnesPerYear(30000.0);
    est.calculate();

    Map<String, Object> results = est.getResults();
    assertNotNull(results);
    assertTrue(results.containsKey("totalCapex_USD"));
    assertTrue(results.containsKey("totalAnnualOpex_USD"));
    assertTrue(results.containsKey("LCOE_USDperUnit"));
    assertTrue(results.containsKey("equipmentBreakdown"));
  }

  @Test
  void testToJson() {
    BiorefineryCostEstimator est = new BiorefineryCostEstimator();
    est.addEquipment(BiorefineryCostEstimator.BiorefineryEquipment.CHP_ENGINE, 2000.0);
    est.setProductPrice(0.10);
    est.setAnnualProductionNm3(1.5e7);
    est.calculate();

    String json = est.toJson();
    assertNotNull(json);
    assertTrue(json.contains("totalCapex_USD"), "JSON should contain CAPEX");
    assertTrue(json.contains("CHP Engine"), "JSON should contain equipment name");
  }

  @Test
  void testToString() {
    BiorefineryCostEstimator est = new BiorefineryCostEstimator();

    // Before calculation
    String before = est.toString();
    assertTrue(before.contains("not yet calculated"), "Should say not yet calculated");

    // After calculation
    est.addEquipment(BiorefineryCostEstimator.BiorefineryEquipment.BIOMASS_DRYER, 5000.0);
    est.calculate();
    String after = est.toString();
    assertTrue(after.contains("CAPEX"), "Should contain CAPEX");
    assertTrue(after.contains("OPEX"), "Should contain OPEX");
  }

  @Test
  void testAllEquipmentTypes() {
    // Verify all equipment types produce reasonable costs
    for (BiorefineryCostEstimator.BiorefineryEquipment eq : BiorefineryCostEstimator.BiorefineryEquipment
        .values()) {
      BiorefineryCostEstimator est = new BiorefineryCostEstimator();
      est.addEquipment(eq, eq.getBaseCapacity());
      est.calculate();

      assertTrue(est.getTotalCapexUSD() > 0.0, eq.getDisplayName() + " should have positive CAPEX");
      assertTrue(est.getTotalInstalledCostUSD() > est.getTotalPurchasedEquipmentCostUSD(),
          eq.getDisplayName() + ": installed > purchased");
    }
  }

  @Test
  void testUtilityCost() {
    BiorefineryCostEstimator est = new BiorefineryCostEstimator();
    est.addEquipment(BiorefineryCostEstimator.BiorefineryEquipment.BIOGAS_UPGRADER, 500.0);
    est.setUtilityCost(0.10, 2.0e6); // 0.10 USD/kWh, 2 GWh/yr
    est.calculate();

    assertTrue(est.getAnnualOpexUSD() > 200000.0, "OPEX should include utility cost of ~200k USD");
  }
}
