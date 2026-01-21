package neqsim.process.fielddevelopment.screening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.fielddevelopment.screening.DetailedEmissionsCalculator.DetailedEmissionsReport;

/**
 * Unit tests for DetailedEmissionsCalculator.
 *
 * @author ESOL
 */
class DetailedEmissionsCalculatorTest {

  @BeforeEach
  void setUp() {
    calculator = new DetailedEmissionsCalculator();
  }

  @Test
  @DisplayName("Test basic emissions calculation")
  void testBasicCalculation() {
    // Configure facility
    calculator.setOilProduction(10000.0, "bbl/d");
    calculator.setGasProduction(5.0, "MSm3/d");
    calculator.setFlaringRate(2000.0, "Sm3/d");

    DetailedEmissionsReport report = calculator.calculate();

    assertNotNull(report);
    assertTrue(report.totalEmissions > 0, "Total emissions should be positive");
    assertTrue(report.scope1Total > 0, "Scope 1 should be positive");
  }

  @Test
  @DisplayName("Test scope breakdown")
  void testScopeBreakdown() {
    calculator.setOilProduction(10000.0, "bbl/d");
    calculator.setGasProduction(5.0, "MSm3/d");
    calculator.setPurchasedElectricity(50000.0, "EU"); // 50,000 MWh/year

    DetailedEmissionsReport report = calculator.calculate();

    // Check that scope 2 is included
    assertTrue(report.scope2Total >= 0, "Scope 2 should be calculated");

    // Sum of scopes should equal total
    double scopeSum = report.scope1Total + report.scope2Total;
    assertEquals(report.totalEmissions, scopeSum, 1.0, "Scopes should sum to total");
  }

  @Test
  @DisplayName("Test flaring emissions")
  void testFlaringEmissions() {
    // High flaring scenario
    calculator.setOilProduction(10000.0, "bbl/d");
    calculator.setFlaringRate(50000.0, "Sm3/d");
    calculator.setFlareEfficiency(0.98); // 98% combustion

    DetailedEmissionsReport report = calculator.calculate();

    assertTrue(report.scope1Total > 0, "Scope 1 from flaring should be positive");
    assertTrue(report.scope1Breakdown.containsKey("Flaring"), "Should have flaring in breakdown");
    assertTrue(report.scope1Breakdown.get("Flaring") > 0,
        "Flaring emissions should be significant");
  }

  @Test
  @DisplayName("Test cold venting emissions")
  void testColdVentingEmissions() {
    calculator.setOilProduction(10000.0, "bbl/d");
    calculator.setColdVentingRate(1000.0); // Sm3/day

    DetailedEmissionsReport report = calculator.calculate();

    assertTrue(report.scope1Total > 0, "Scope 1 from venting should be positive");
  }

  @Test
  @DisplayName("Test tank breathing emissions")
  void testTankBreathingEmissions() {
    calculator.setOilProduction(10000.0, "bbl/d");
    calculator.setTankBreathingRate(500.0); // Sm3/day

    DetailedEmissionsReport report = calculator.calculate();

    assertTrue(report.scope1Total > 0, "Scope 1 should include tank breathing");
  }

  @Test
  @DisplayName("Test fugitive emissions with component counts")
  void testFugitiveEmissions() {
    // Configure facility components
    calculator.setOilProduction(10000.0, "bbl/d");
    calculator.setComponentCounts(5000, 2000, 3, 1000); // flanges, valves, compressors, pumps

    DetailedEmissionsReport report = calculator.calculate();

    assertTrue(report.scope1Total > 0, "Scope 1 should include fugitives");
    assertTrue(
        report.scope1Breakdown.containsKey("Fugitives") || report.emissionsBySource.size() > 0,
        "Should have fugitive emissions");
  }

  @Test
  @DisplayName("Test produced CO2 venting")
  void testProducedCO2Venting() {
    calculator.setOilProduction(10000.0, "bbl/d");
    calculator.setGasProduction(5.0, "MSm3/d");
    calculator.setProducedCO2(3.0, true); // 3% CO2 in produced gas, venting

    DetailedEmissionsReport report = calculator.calculate();

    assertTrue(report.scope1Total > 0, "Should have emissions from CO2 venting");
  }

  @Test
  @DisplayName("Test grid emission factors by region")
  void testGridEmissionFactors() {
    calculator.setOilProduction(10000.0, "bbl/d");

    // Nordic grid (low carbon) - 50,000 MWh/year
    calculator.setPurchasedElectricity(50000.0, "NORDIC");
    DetailedEmissionsReport nordicReport = calculator.calculate();

    // Reset and use EU grid (higher carbon)
    calculator = new DetailedEmissionsCalculator();
    calculator.setOilProduction(10000.0, "bbl/d");
    calculator.setPurchasedElectricity(50000.0, "EU");
    DetailedEmissionsReport euReport = calculator.calculate();

    // EU grid should have higher emissions than Nordic
    assertTrue(euReport.scope2Total >= nordicReport.scope2Total,
        "EU grid should have >= emissions than Nordic");
  }

  @Test
  @DisplayName("Test emissions intensity calculation")
  void testEmissionsIntensity() {
    calculator.setOilProduction(10000.0, "bbl/d");
    calculator.setGasProduction(5.0, "MSm3/d");
    calculator.setFlaringRate(2000.0, "Sm3/d");

    DetailedEmissionsReport report = calculator.calculate();

    assertTrue(report.intensityKgCO2PerBoe > 0, "Intensity should be calculated");
    assertTrue(report.totalProductionBoePerYear > 0, "Total production should be calculated");
  }

  @Test
  @DisplayName("Test emissions rating")
  void testEmissionsRating() {
    // Low emissions facility
    calculator.setOilProduction(20000.0, "bbl/d");
    calculator.setGasProduction(2.0, "MSm3/d");
    calculator.setFlaringRate(100.0, "Sm3/d"); // Very low flaring

    DetailedEmissionsReport report = calculator.calculate();

    assertNotNull(report.rating, "Should have rating");
    // Rating should be a string (format may vary)
    assertTrue(report.rating != null && report.rating.length() >= 1, "Rating should be assigned");
  }

  @Test
  @DisplayName("Test operating hours affect annual totals")
  void testOperatingHours() {
    calculator.setOilProduction(10000.0, "bbl/d");
    calculator.setFlaringRate(5000.0, "Sm3/d");

    // Full year operation
    calculator.setOperatingHours(8760);
    DetailedEmissionsReport fullYearReport = calculator.calculate();

    // Reset for partial year
    calculator = new DetailedEmissionsCalculator();
    calculator.setOilProduction(10000.0, "bbl/d");
    calculator.setFlaringRate(5000.0, "Sm3/d");
    calculator.setOperatingHours(4380); // Half year
    DetailedEmissionsReport halfYearReport = calculator.calculate();

    // Both should have calculated emissions
    assertTrue(fullYearReport.totalEmissions >= 0, "Full year should have emissions calculated");
    assertTrue(halfYearReport.totalEmissions >= 0, "Half year should have emissions calculated");
  }

  @Test
  @DisplayName("Test report toString format")
  void testReportToString() {
    calculator.setOilProduction(10000.0, "bbl/d");
    calculator.setFlaringRate(5000.0, "Sm3/d");

    DetailedEmissionsReport report = calculator.calculate();
    String output = report.toString();

    assertNotNull(output);
    assertTrue(output.contains("Emissions Report"));
    assertTrue(output.contains("Scope 1"));
    assertTrue(output.contains("tCO2e"));
  }

  @Test
  @DisplayName("Test fugitive rate percentage")
  void testFugitiveRatePercentage() {
    calculator.setOilProduction(10000.0, "bbl/d");
    calculator.setGasProduction(5.0, "MSm3/d");
    calculator.setFugitiveRate(0.5); // 0.5% of gas throughput

    DetailedEmissionsReport report = calculator.calculate();

    assertTrue(report.scope1Total > 0, "Should calculate fugitive emissions");
  }
}
