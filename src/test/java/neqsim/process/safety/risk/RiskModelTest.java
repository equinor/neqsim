package neqsim.process.safety.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import neqsim.process.safety.InitiatingEvent;
import neqsim.process.safety.risk.RiskEvent.ConsequenceCategory;

/**
 * Tests for the probabilistic risk analysis framework.
 */
class RiskModelTest {
  private RiskModel riskModel;

  @BeforeEach
  void setUp() {
    riskModel = new RiskModel("Test Risk Study");
  }

  @Test
  @DisplayName("Test RiskEvent creation and properties")
  void testRiskEvent() {
    RiskEvent event = RiskEvent.builder().name("Small Leak").description("5mm hole in HP separator")
        .initiatingEvent(InitiatingEvent.LEAK_SMALL).frequency(1e-3)
        .consequenceCategory(ConsequenceCategory.MINOR).build();

    assertEquals("Small Leak", event.getName());
    assertEquals(InitiatingEvent.LEAK_SMALL, event.getInitiatingEvent());
    assertEquals(1e-3, event.getFrequency(), 1e-10);
    assertEquals(ConsequenceCategory.MINOR, event.getConsequenceCategory());
    assertTrue(event.isInitiatingEvent());
    assertEquals(1e-3, event.getAbsoluteFrequency(), 1e-10);
  }

  @Test
  @DisplayName("Test conditional event chain")
  void testConditionalEvents() {
    RiskEvent initiating = RiskEvent.builder().name("Gas Release")
        .initiatingEvent(InitiatingEvent.LEAK_MEDIUM).frequency(1e-4).build();

    RiskEvent ignition = RiskEvent.builder().name("Delayed Ignition")
        .initiatingEvent(InitiatingEvent.FIRE_EXPOSURE).parentEvent(initiating)
        .conditionalProbability(0.1).consequenceCategory(ConsequenceCategory.MAJOR).build();

    assertEquals(1e-4, initiating.getAbsoluteFrequency(), 1e-12);
    assertEquals(1e-5, ignition.getAbsoluteFrequency(), 1e-12);
    assertFalse(ignition.isInitiatingEvent());
  }

  @Test
  @DisplayName("Test risk index calculation")
  void testRiskIndex() {
    RiskEvent event = RiskEvent.builder().name("Major Leak").frequency(1e-4)
        .consequenceCategory(ConsequenceCategory.MAJOR).build();

    double expectedRisk = 1e-4 * ConsequenceCategory.MAJOR.getSeverity();
    assertEquals(expectedRisk, event.getRiskIndex(), 1e-12);
  }

  @Test
  @DisplayName("Test RiskModel deterministic analysis")
  void testDeterministicAnalysis() {
    riskModel.addInitiatingEvent("Small Leak", 1e-3, ConsequenceCategory.MINOR);
    riskModel.addInitiatingEvent("Medium Leak", 1e-4, ConsequenceCategory.MODERATE);
    riskModel.addInitiatingEvent("Large Leak", 1e-5, ConsequenceCategory.MAJOR);

    RiskResult result = riskModel.runDeterministicAnalysis();

    assertNotNull(result);
    assertTrue(result.getTotalRiskIndex() > 0);
    assertTrue(result.getTotalFrequency() > 0);
    assertEquals(3, result.getEventResults().size());
  }

  @Test
  @DisplayName("Test RiskModel Monte Carlo analysis")
  void testMonteCarloAnalysis() {
    riskModel.setRandomSeed(42);
    riskModel.addInitiatingEvent("Leak A", 1e-3, ConsequenceCategory.MINOR);
    riskModel.addInitiatingEvent("Leak B", 1e-4, ConsequenceCategory.MODERATE);

    RiskResult result = riskModel.runMonteCarloAnalysis(1000);

    assertNotNull(result);
    assertEquals(1000, result.getIterations());
    assertTrue(result.getTotalRiskIndex() > 0);
    assertTrue(result.getPercentile95() >= result.getMeanConsequence());
    assertTrue(result.getPercentile99() >= result.getPercentile95());
  }

  @Test
  @DisplayName("Test sensitivity analysis")
  void testSensitivityAnalysis() {
    riskModel.addInitiatingEvent("Event 1", 1e-3, ConsequenceCategory.MODERATE);
    riskModel.addInitiatingEvent("Event 2", 1e-4, ConsequenceCategory.MAJOR);

    SensitivityResult result = riskModel.runSensitivityAnalysis(0.1, 10.0, 5);

    assertNotNull(result);
    assertTrue(result.getBaseRiskIndex() > 0);
    assertNotNull(result.getParameterNames());
    assertTrue(result.getParameterNames().length > 0);
  }

  @Test
  @DisplayName("Test RiskResult export")
  void testRiskResultExport() {
    riskModel.addInitiatingEvent("Test Event", 1e-3, ConsequenceCategory.MINOR);
    RiskResult result = riskModel.runDeterministicAnalysis();

    String summary = result.getSummary();
    assertNotNull(summary);
    assertTrue(summary.contains("Test Risk Study"));
    assertTrue(summary.contains("Risk Index"));
  }

  @Test
  @DisplayName("Test consequence categories")
  void testConsequenceCategories() {
    assertEquals(1, ConsequenceCategory.NEGLIGIBLE.getSeverity());
    assertEquals(2, ConsequenceCategory.MINOR.getSeverity());
    assertEquals(3, ConsequenceCategory.MODERATE.getSeverity());
    assertEquals(4, ConsequenceCategory.MAJOR.getSeverity());
    assertEquals(5, ConsequenceCategory.CATASTROPHIC.getSeverity());
  }

  @Test
  @DisplayName("Test F-N curve data generation")
  void testFNCurveData() {
    riskModel.addInitiatingEvent("Minor", 1e-2, ConsequenceCategory.MINOR);
    riskModel.addInitiatingEvent("Moderate", 1e-3, ConsequenceCategory.MODERATE);
    riskModel.addInitiatingEvent("Major", 1e-4, ConsequenceCategory.MAJOR);

    RiskResult result = riskModel.runDeterministicAnalysis();
    double[][] fnData = result.getFNCurveData();

    assertNotNull(fnData);
    assertEquals(ConsequenceCategory.values().length, fnData.length);

    // Cumulative frequency should increase as severity decreases
    for (int i = 0; i < fnData.length - 1; i++) {
      assertTrue(fnData[i][1] >= fnData[i + 1][1]);
    }
  }

  @Test
  @DisplayName("Test builder pattern")
  void testBuilderPattern() {
    RiskModel model = RiskModel.builder().name("Custom Study").seed(12345)
        .frequencyUncertaintyFactor(2.0).build();

    assertNotNull(model);
    assertEquals("Custom Study", model.getName());
  }
}
