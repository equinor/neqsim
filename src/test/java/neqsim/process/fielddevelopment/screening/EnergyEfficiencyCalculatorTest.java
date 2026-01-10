package neqsim.process.fielddevelopment.screening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.screening.EnergyEfficiencyCalculator.EnergyReport;
import neqsim.process.fielddevelopment.screening.EnergyEfficiencyCalculator.FacilityType;

/**
 * Unit tests for EnergyEfficiencyCalculator.
 *
 * @author ESOL
 */
class EnergyEfficiencyCalculatorTest {

  private EnergyEfficiencyCalculator calculator;

  @BeforeEach
  void setUp() {
    calculator = new EnergyEfficiencyCalculator();
  }

  @Test
  @DisplayName("Test basic energy efficiency calculation")
  void testBasicCalculation() {
    // Configure calculator
    calculator.setOilProduction(10000, "bbl/day");
    calculator.setGasProduction(2.0, "MMSm3/day");
    calculator.setCompressorPower(5000, "kW");
    calculator.setCompressorEfficiency(0.75);
    calculator.setPumpPower(1000, "kW");
    calculator.setHeatingDuty(8000, "kW");
    calculator.setFlaringRate(0.05, "MMSm3/day");

    // Calculate
    EnergyReport report = calculator.calculate();

    // Verify results
    assertNotNull(report);
    assertTrue(report.totalProductionBoePerDay > 0, "Production should be positive");
    assertTrue(report.totalElectricPowerKW > 0, "Power should be positive");
    assertTrue(report.specificEnergyConsumption > 0, "SEC should be positive");
  }

  @Test
  @DisplayName("Test SEC calculation")
  void testSpecificEnergyConsumption() {
    // 10000 bbl/day oil, 5000 kW power
    calculator.setOilProduction(10000, "bbl/day");
    calculator.setCompressorPower(5000, "kW");

    EnergyReport report = calculator.calculate();

    // SEC = (5000 kW × 24 hr) / 10000 boe = 12 kWh/boe
    double expectedSEC = 5000.0 * 24.0 / 10000.0;
    assertEquals(expectedSEC, report.specificEnergyConsumption, 0.1,
        "SEC should be calculated correctly");
  }

  @Test
  @DisplayName("Test Energy Efficiency Index calculation")
  void testEnergyEfficiencyIndex() {
    calculator.setOilProduction(10000, "bbl/day");
    calculator.setCompressorPower(5000, "kW");
    calculator.setFacilityType(FacilityType.PLATFORM); // Reference = 50 kWh/boe

    EnergyReport report = calculator.calculate();

    // EEI = actual SEC / reference SEC
    double expectedEEI = report.specificEnergyConsumption / 50.0;
    assertEquals(expectedEEI, report.energyEfficiencyIndex, 0.01,
        "EEI should be correctly calculated");
  }

  @Test
  @DisplayName("Test efficiency rating classification")
  void testEfficiencyRating() {
    // Low power = good rating
    calculator.setOilProduction(20000, "bbl/day"); // High production
    calculator.setCompressorPower(2000, "kW"); // Low power
    calculator.setFacilityType(FacilityType.PLATFORM);

    EnergyReport report = calculator.calculate();

    // Low EEI should give good rating
    assertTrue(report.energyEfficiencyIndex < 1.0, "Should have EEI below reference");
    assertTrue(
        report.getEfficiencyRating().startsWith("A") || report.getEfficiencyRating().startsWith("B")
            || report.getEfficiencyRating().startsWith("C"),
        "Should have good efficiency rating");
  }

  @Test
  @DisplayName("Test power breakdown")
  void testPowerBreakdown() {
    calculator.setCompressorPower(3000, "kW");
    calculator.setPumpPower(1000, "kW");
    calculator.setElectricalLoad(500);
    calculator.setOilProduction(5000, "bbl/day");

    EnergyReport report = calculator.calculate();

    // Check breakdown
    assertNotNull(report.powerBreakdown);
    assertEquals(3000.0, report.powerBreakdown.get("Compression"), 0.1);
    assertEquals(1000.0, report.powerBreakdown.get("Pumping"), 0.1);
    assertEquals(500.0, report.powerBreakdown.get("Electrical Load"), 0.1);

    // Total should match
    double expectedTotal = 3000 + 1000 + 500;
    assertEquals(expectedTotal, report.totalElectricPowerKW, 0.1);
  }

  @Test
  @DisplayName("Test energy losses calculation")
  void testEnergyLosses() {
    calculator.setCompressorPower(5000, "kW");
    calculator.setCompressorEfficiency(0.75); // 25% loss
    calculator.setPumpPower(1000, "kW");
    calculator.setPumpEfficiency(0.70); // 30% loss
    calculator.setOilProduction(10000, "bbl/day");

    EnergyReport report = calculator.calculate();

    // Check losses are calculated
    assertTrue(report.energyLosses.containsKey("Compressor inefficiency"),
        "Should have compressor losses");
    assertTrue(report.energyLosses.containsKey("Pump inefficiency"), "Should have pump losses");

    // Compressor loss = 5000 * (1 - 0.75) = 1250 kW
    assertEquals(1250.0, report.energyLosses.get("Compressor inefficiency"), 0.1);
  }

  @Test
  @DisplayName("Test flaring losses")
  void testFlaringLosses() {
    calculator.setFlaringRate(0.1, "MMSm3/day"); // 100,000 Sm3/day
    calculator.setOilProduction(5000, "bbl/day");

    EnergyReport report = calculator.calculate();

    assertTrue(report.energyLosses.containsKey("Flaring"), "Should track flaring losses");
    assertTrue(report.energyLosses.get("Flaring") > 0, "Flaring losses should be positive");
  }

  @Test
  @DisplayName("Test waste heat sources identification")
  void testWasteHeatSources() {
    calculator.setCompressorPower(10000, "kW");
    calculator.setDriverType(EnergyEfficiencyCalculator.DriverType.GAS_TURBINE);
    calculator.setOilProduction(10000, "bbl/day");

    EnergyReport report = calculator.calculate();

    // Should identify waste heat sources
    assertTrue(report.wasteHeatSources.containsKey("Compressor discharge"),
        "Should identify compressor discharge heat");
    assertTrue(report.wasteHeatSources.containsKey("Gas turbine exhaust"),
        "Should identify turbine exhaust heat");
    assertTrue(report.totalAvailableWasteHeatKW > 0, "Should have available waste heat");
  }

  @Test
  @DisplayName("Test improvement recommendations")
  void testRecommendations() {
    // Set up inefficient facility
    calculator.setCompressorPower(10000, "kW");
    calculator.setCompressorEfficiency(0.65); // Below reference 0.80
    calculator.setPumpPower(2000, "kW");
    calculator.setPumpEfficiency(0.60); // Below reference 0.75
    calculator.setFlaringRate(0.1, "MMSm3/day");
    calculator.setHasWasteHeatRecovery(false);
    calculator.setOilProduction(10000, "bbl/day");

    EnergyReport report = calculator.calculate();

    // Should have recommendations
    assertNotNull(report.recommendations);
    assertTrue(report.recommendations.size() >= 1, "Should have improvement recommendations");
    assertTrue(report.totalPotentialSavingsKW > 0, "Should have potential savings identified");
  }

  @Test
  @DisplayName("Test facility type affects reference SEC")
  void testFacilityTypeReference() {
    calculator.setOilProduction(10000, "bbl/day");
    calculator.setCompressorPower(5000, "kW");

    // Platform reference
    calculator.setFacilityType(FacilityType.PLATFORM);
    EnergyReport platformReport = calculator.calculate();

    // FPSO reference (higher)
    calculator.setFacilityType(FacilityType.FPSO);
    EnergyReport fpsoReport = calculator.calculate();

    // Same SEC, different EEI due to different reference
    assertEquals(platformReport.specificEnergyConsumption, fpsoReport.specificEnergyConsumption,
        0.1, "SEC should be the same");
    assertTrue(platformReport.referenceSEC < fpsoReport.referenceSEC,
        "Platform reference should be lower than FPSO");
  }

  @Test
  @DisplayName("Test gas production included in boe")
  void testGasProductionBoe() {
    // Only gas production
    calculator.setGasProduction(10.0, "MMSm3/day"); // 10 MMSm3/d = ~1.67 million Sm3/d
    calculator.setCompressorPower(5000, "kW");

    EnergyReport report = calculator.calculate();

    // Gas should contribute to boe
    // 10 MMSm3/d = 10,000 MSm3/d = 10,000 * 180 = 1,800,000 boe/d
    assertTrue(report.totalProductionBoePerDay > 0, "Gas should contribute to production");
  }

  @Test
  @DisplayName("Test report toString format")
  void testReportToString() {
    calculator.setOilProduction(10000, "bbl/day");
    calculator.setCompressorPower(5000, "kW");

    EnergyReport report = calculator.calculate();

    String output = report.toString();
    assertNotNull(output);
    assertTrue(output.contains("Energy Efficiency Report"));
    assertTrue(output.contains("SEC:"));
    assertTrue(output.contains("EEI:"));
    assertTrue(output.contains("Rating:"));
  }

  @Test
  @DisplayName("Test unit conversions")
  void testUnitConversions() {
    // Oil in Sm3/day
    calculator.setOilProduction(1589.0, "Sm3/day"); // ~10000 bbl/day

    // Power in MW
    calculator.setCompressorPower(5.0, "MW"); // 5000 kW

    // Gas in MMSm3/day
    calculator.setGasProduction(0.002, "MMSm3/day"); // 2 MSm3/day

    EnergyReport report = calculator.calculate();

    assertNotNull(report);
    assertTrue(report.totalProductionBoePerDay > 0);
  }
}
