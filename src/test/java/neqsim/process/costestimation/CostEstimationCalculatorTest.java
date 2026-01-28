package neqsim.process.costestimation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Test class for CostEstimationCalculator.
 *
 * @author AGAS
 * @version 1.0
 */
public class CostEstimationCalculatorTest {

  @Test
  void testVerticalVesselCost() {
    CostEstimationCalculator calc = new CostEstimationCalculator();

    // Test vessel cost for 1000 kg shell weight
    // Turton correlation for vertical vessels (CEPCI 2025 escalated)
    // These are PURCHASED EQUIPMENT COSTS for industrial-grade vessels
    double cost = calc.calcVerticalVesselCost(1000.0);
    System.out.println("1 tonne vessel cost (2025 USD): " + cost);
    assertTrue(cost > 0, "Vessel cost should be positive");
    assertTrue(cost > 100000, "1 tonne industrial vessel should cost > $100k");
    assertTrue(cost < 2000000, "1 tonne vessel should cost less than $2M (sanity check)");

    // Test smaller vessel (250 kg min)
    double smallCost = calc.calcVerticalVesselCost(250.0);
    assertTrue(smallCost < cost, "Smaller vessel should cost less");

    // Test larger vessel (5000 kg)
    double largeCost = calc.calcVerticalVesselCost(5000.0);
    assertTrue(largeCost > cost, "Larger vessel should cost more");
  }

  @Test
  void testHorizontalVesselCost() {
    CostEstimationCalculator calc = new CostEstimationCalculator();

    double cost = calc.calcHorizontalVesselCost(1000.0);
    assertTrue(cost > 0, "Vessel cost should be positive");
  }

  @Test
  void testShellTubeHeatExchangerCost() {
    CostEstimationCalculator calc = new CostEstimationCalculator();

    // Test heat exchanger cost for 50 m2 area
    double cost = calc.calcShellTubeHeatExchangerCost(50.0);
    assertTrue(cost > 0, "Heat exchanger cost should be positive");
    assertTrue(cost > 5000, "50 m2 exchanger should cost more than $5000");
  }

  @Test
  void testColumnTrayCost() {
    CostEstimationCalculator calc = new CostEstimationCalculator();

    // Test sieve trays cost for 2m diameter, 20 trays
    double cost = calc.calcSieveTraysCost(2.0, 20);
    assertTrue(cost > 0, "Tray cost should be positive");
    assertTrue(cost > 5000, "20 sieve trays should cost more than $5000");
  }

  @Test
  void testCentrifugalCompressorCost() {
    CostEstimationCalculator calc = new CostEstimationCalculator();

    // Test compressor cost for 500 kW
    double cost = calc.calcCentrifugalCompressorCost(500.0);
    assertTrue(cost > 0, "Compressor cost should be positive");
    assertTrue(cost > 50000, "500 kW compressor should cost more than $50000");
  }

  @Test
  void testPressureFactor() {
    double fp5 = CostEstimationCalculator.getPressureFactor(5.0);
    double fp50 = CostEstimationCalculator.getPressureFactor(50.0);
    double fp100 = CostEstimationCalculator.getPressureFactor(100.0);

    assertEquals(1.0, fp5, "Pressure factor at 5 barg should be 1.0");
    assertTrue(fp50 > fp5, "Higher pressure should have higher factor");
    assertTrue(fp100 > fp50, "Even higher pressure should have even higher factor");
  }

  @Test
  void testMaterialFactor() {
    CostEstimationCalculator calc = new CostEstimationCalculator();

    calc.setMaterialOfConstruction("Carbon Steel");
    assertEquals(1.0, calc.getMaterialFactor(), 0.01, "CS factor should be 1.0");

    calc.setMaterialOfConstruction("SS316");
    assertEquals(2.1, calc.getMaterialFactor(), 0.01, "SS316 factor should be 2.1");

    calc.setMaterialOfConstruction("Titanium");
    assertEquals(4.5, calc.getMaterialFactor(), 0.01, "Titanium factor should be 4.5");
  }

  @Test
  void testBareModuleCost() {
    CostEstimationCalculator calc = new CostEstimationCalculator();

    double purchasedCost = 100000.0;
    double bareModuleCost = calc.calcBareModuleCost(purchasedCost, 10.0); // 10 barg

    assertTrue(bareModuleCost > purchasedCost, "BMC should be greater than PEC");
    assertTrue(bareModuleCost < purchasedCost * 5, "BMC should be reasonable multiple of PEC");
  }

  @Test
  void testTotalModuleCost() {
    CostEstimationCalculator calc = new CostEstimationCalculator();

    double bareModuleCost = 200000.0;
    double totalModuleCost = calc.calcTotalModuleCost(bareModuleCost);

    assertTrue(totalModuleCost > bareModuleCost, "TMC should be greater than BMC");
    // With 15% contingency and 10% engineering
    assertEquals(bareModuleCost * 1.25, totalModuleCost, 0.01,
        "TMC should be 1.25x BMC with default factors");
  }

  @Test
  void testCepciScaling() {
    CostEstimationCalculator calc2019 =
        new CostEstimationCalculator(CostEstimationCalculator.CEPCI_2019);
    CostEstimationCalculator calc2025 =
        new CostEstimationCalculator(CostEstimationCalculator.CEPCI_2025);

    double cost2019 = calc2019.calcVerticalVesselCost(1000.0);
    double cost2025 = calc2025.calcVerticalVesselCost(1000.0);

    assertTrue(cost2025 > cost2019, "2025 costs should be higher than 2019 due to inflation");
  }

  @Test
  void testInstallationManHours() {
    CostEstimationCalculator calc = new CostEstimationCalculator();

    double vesselHours = calc.calcInstallationManHours(5000, "vessel");
    double compressorHours = calc.calcInstallationManHours(5000, "compressor");

    assertTrue(vesselHours > 0, "Installation hours should be positive");
    assertTrue(compressorHours > vesselHours, "Compressor should need more hours than vessel");
  }

  @Test
  void testPipingCost() {
    CostEstimationCalculator calc = new CostEstimationCalculator();

    // Cost for 6-inch pipe, 100m, schedule 40
    double cost = calc.calcPipingCost(0.1524, 100, 40);
    assertTrue(cost > 0, "Piping cost should be positive");

    // Higher schedule should cost more
    double costSch80 = calc.calcPipingCost(0.1524, 100, 80);
    assertTrue(costSch80 > cost, "Higher schedule should cost more");
  }

  @Test
  void testToJson() {
    CostEstimationCalculator calc = new CostEstimationCalculator();
    calc.calculateCostEstimate(50000, 50, 1000, "vessel");

    String json = calc.toJson();
    assertTrue(json.contains("purchasedEquipmentCost_USD"), "JSON should contain PEC");
    assertTrue(json.contains("bareModuleCost_USD"), "JSON should contain BMC");
    assertTrue(json.contains("totalModuleCost_USD"), "JSON should contain TMC");
    assertTrue(json.contains("grassRootsCost_USD"), "JSON should contain grass roots cost");
  }
}
