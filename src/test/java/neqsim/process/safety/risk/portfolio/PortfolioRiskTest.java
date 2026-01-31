package neqsim.process.safety.risk.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Portfolio Risk Analysis package.
 */
class PortfolioRiskTest {

  private PortfolioRiskAnalyzer analyzer;

  @BeforeEach
  void setUp() {
    analyzer = new PortfolioRiskAnalyzer("North Sea Portfolio");

    // Add assets
    PortfolioRiskAnalyzer.Asset assetA = analyzer.addAsset("A", "Platform Alpha", 50000);
    assetA.setRegion("Northern North Sea");
    assetA.setSystemAvailability(0.95);
    assetA.setInsuranceValue(500e6);

    PortfolioRiskAnalyzer.Asset assetB = analyzer.addAsset("B", "Platform Beta", 30000);
    assetB.setRegion("Northern North Sea");
    assetB.setSystemAvailability(0.92);
    assetB.setInsuranceValue(300e6);

    PortfolioRiskAnalyzer.Asset assetC = analyzer.addAsset("C", "Platform Gamma", 40000);
    assetC.setRegion("Central North Sea");
    assetC.setSystemAvailability(0.94);
    assetC.setInsuranceValue(400e6);
  }

  @Test
  void testAssetCreation() {
    assertEquals(3, analyzer.getAssets().size());

    PortfolioRiskAnalyzer.Asset asset = analyzer.getAssets().get(0);
    assertEquals("A", asset.getAssetId());
    assertEquals("Platform Alpha", asset.getAssetName());
    assertEquals(50000, asset.getMaxProduction(), 0.01);
    assertEquals(0.95, asset.getSystemAvailability(), 0.01);
  }

  @Test
  void testExpectedProduction() {
    PortfolioRiskAnalyzer.Asset asset = analyzer.getAssets().get(0);
    double expected = asset.getExpectedProduction();

    // Expected = max * availability
    assertEquals(50000 * 0.95, expected, 0.01);
  }

  @Test
  void testCommonCauseScenario() {
    PortfolioRiskAnalyzer.CommonCauseScenario weather =
        analyzer.createRegionalWeatherScenario("Northern North Sea", 0.5, 5.0);

    assertNotNull(weather);
    assertEquals(0.5, weather.getFrequency(), 0.01);
    assertEquals(5.0, weather.getDuration(), 0.01);

    // Should affect platforms A and B (both in Northern North Sea)
    assertEquals(2, weather.getAffectedAssetIds().size());
    assertTrue(weather.getAffectedAssetIds().contains("A"));
    assertTrue(weather.getAffectedAssetIds().contains("B"));
  }

  @Test
  void testCustomCommonCause() {
    PortfolioRiskAnalyzer.CommonCauseScenario cyber =
        new PortfolioRiskAnalyzer.CommonCauseScenario("CYBER-001", "Regional cyber attack",
            PortfolioRiskAnalyzer.CommonCauseScenario.CommonCauseType.CYBER, 0.1);
    cyber.setDuration(3.0);
    cyber.addAffectedAsset("A", 0.8);
    cyber.addAffectedAsset("B", 0.8);
    cyber.addAffectedAsset("C", 0.8);

    analyzer.addCommonCauseScenario(cyber);

    assertEquals(1, analyzer.getCommonCauseScenarios().size());
    assertEquals(0.8, cyber.getAssetImpact("A"), 0.01);
  }

  @Test
  void testPortfolioSimulation() {
    analyzer.createRegionalWeatherScenario("Northern North Sea", 0.5, 5.0);
    analyzer.setNumberOfSimulations(1000);
    analyzer.setSimulationPeriodYears(1.0);

    PortfolioRiskResult result = analyzer.run();

    assertNotNull(result);
    assertTrue(result.getTotalMaxProduction() > 0);
    assertTrue(result.getTotalExpectedProduction() > 0);
    assertTrue(result.getPortfolioAvailability() > 0 && result.getPortfolioAvailability() <= 1);
  }

  @Test
  void testPortfolioLossStatistics() {
    analyzer.createRegionalWeatherScenario("Northern North Sea", 0.5, 5.0);
    analyzer.setNumberOfSimulations(1000);

    PortfolioRiskResult result = analyzer.run();

    assertTrue(result.getExpectedPortfolioLoss() >= 0);
    assertTrue(result.getP10PortfolioLoss() <= result.getP50PortfolioLoss());
    assertTrue(result.getP50PortfolioLoss() <= result.getP90PortfolioLoss());
    assertTrue(result.getP90PortfolioLoss() <= result.getP99PortfolioLoss());
  }

  @Test
  void testCommonCauseContribution() {
    analyzer.createRegionalWeatherScenario("Northern North Sea", 0.5, 5.0);
    analyzer.setNumberOfSimulations(1000);

    PortfolioRiskResult result = analyzer.run();

    assertTrue(result.getExpectedCommonCauseLoss() >= 0);
    assertTrue(result.getCommonCauseFraction() >= 0 && result.getCommonCauseFraction() <= 1);
  }

  @Test
  void testDiversificationBenefit() {
    analyzer.createRegionalWeatherScenario("Northern North Sea", 0.5, 5.0);
    analyzer.setNumberOfSimulations(1000);

    PortfolioRiskResult result = analyzer.run();

    // Diversification benefit can be positive or negative
    assertNotNull(result.getDiversificationBenefit());
  }

  @Test
  void testAssetResults() {
    analyzer.createRegionalWeatherScenario("Northern North Sea", 0.5, 5.0);
    analyzer.setNumberOfSimulations(1000);

    PortfolioRiskResult result = analyzer.run();

    assertEquals(3, result.getAssetResults().size());

    PortfolioRiskResult.AssetResult ar = result.getAssetResults().get(0);
    assertNotNull(ar.getAssetId());
    assertTrue(ar.getMaxProduction() > 0);
    assertTrue(ar.getExpectedProduction() > 0);
    assertTrue(ar.getContributionToPortfolioRisk() >= 0);
  }

  @Test
  void testValueAtRisk() {
    analyzer.createRegionalWeatherScenario("Northern North Sea", 0.5, 5.0);
    analyzer.setNumberOfSimulations(1000);

    PortfolioRiskResult result = analyzer.run();

    double var90 = result.getValueAtRisk(90);
    double var99 = result.getValueAtRisk(99);

    assertTrue(var90 <= var99, "VaR99 should be >= VaR90");
  }

  @Test
  void testReport() {
    analyzer.createRegionalWeatherScenario("Northern North Sea", 0.5, 5.0);
    analyzer.setNumberOfSimulations(1000);

    PortfolioRiskResult result = analyzer.run();
    String report = result.toReport();

    assertNotNull(report);
    assertTrue(report.contains("PORTFOLIO RISK ANALYSIS REPORT"));
    assertTrue(report.contains("Total Capacity"));
    assertTrue(report.contains("ASSET CONTRIBUTIONS"));
  }

  @Test
  void testJsonSerialization() {
    analyzer.createRegionalWeatherScenario("Northern North Sea", 0.5, 5.0);
    analyzer.setNumberOfSimulations(100);

    PortfolioRiskResult result = analyzer.run();
    String json = result.toJson();

    assertNotNull(json);
    assertTrue(json.contains("portfolioLoss"));
    assertTrue(json.contains("commonCause"));
    assertTrue(json.contains("assetResults"));
  }

  @Test
  void testNoCommonCause() {
    // Run without any common cause scenarios
    analyzer.setNumberOfSimulations(100);

    PortfolioRiskResult result = analyzer.run();

    assertNotNull(result);
    // Should still have individual asset losses
    assertTrue(result.getExpectedPortfolioLoss() >= 0);
  }

  @Test
  void testMultipleCommonCauses() {
    analyzer.createRegionalWeatherScenario("Northern North Sea", 0.5, 5.0);
    analyzer.createRegionalWeatherScenario("Central North Sea", 0.3, 3.0);

    PortfolioRiskAnalyzer.CommonCauseScenario pandemic =
        new PortfolioRiskAnalyzer.CommonCauseScenario("PANDEMIC", "Pandemic impact",
            PortfolioRiskAnalyzer.CommonCauseScenario.CommonCauseType.PANDEMIC, 0.05);
    pandemic.setDuration(30.0);
    pandemic.addAffectedAsset("A", 0.3);
    pandemic.addAffectedAsset("B", 0.3);
    pandemic.addAffectedAsset("C", 0.3);
    analyzer.addCommonCauseScenario(pandemic);

    assertEquals(3, analyzer.getCommonCauseScenarios().size());

    analyzer.setNumberOfSimulations(500);
    PortfolioRiskResult result = analyzer.run();

    assertNotNull(result);
    assertTrue(result.getCommonCauseFraction() > 0);
  }
}
