package neqsim.pvtsimulation.flowassurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;

/**
 * Unit tests for AsphalteneStabilityAnalyzer class.
 *
 * @author Even Solbraa
 */
public class AsphalteneStabilityAnalyzerTest {
  private SystemInterface testFluid;

  @BeforeEach
  void setUp() {
    // Create a typical crude oil system
    testFluid = new SystemSrkCPAstatoil(350.0, 100.0);
    testFluid.addComponent("methane", 0.20);
    testFluid.addComponent("ethane", 0.05);
    testFluid.addComponent("propane", 0.05);
    testFluid.addComponent("n-butane", 0.05);
    testFluid.addComponent("n-pentane", 0.05);
    testFluid.addComponent("n-hexane", 0.10);
    testFluid.addComponent("n-heptane", 0.15);
    testFluid.addComponent("n-octane", 0.15);
    testFluid.addComponent("nC10", 0.20);
    testFluid.setMixingRule("classic");
  }

  @Test
  void testConstructorWithFluid() {
    AsphalteneStabilityAnalyzer analyzer = new AsphalteneStabilityAnalyzer(testFluid);
    assertNotNull(analyzer);
    assertNotNull(analyzer.getSARAData());
  }

  @Test
  void testDefaultConstructor() {
    AsphalteneStabilityAnalyzer analyzer = new AsphalteneStabilityAnalyzer();
    assertNotNull(analyzer);
    assertNotNull(analyzer.getSARAData());
  }

  @Test
  void testSetSARAFractions() {
    AsphalteneStabilityAnalyzer analyzer = new AsphalteneStabilityAnalyzer(testFluid);

    // Set SARA fractions
    analyzer.setSARAFractions(0.4, 0.35, 0.15, 0.1);

    // CII = (0.4 + 0.1) / (0.35 + 0.15) = 0.5 / 0.5 = 1.0
    double expectedCII = (0.4 + 0.1) / (0.35 + 0.15);
    assertEquals(expectedCII, analyzer.getColloidalInstabilityIndex(), 0.001);
  }

  @Test
  void testResinToAsphalteneRatio() {
    AsphalteneStabilityAnalyzer analyzer = new AsphalteneStabilityAnalyzer(testFluid);

    // Set SARA fractions
    analyzer.setSARAFractions(0.5, 0.3, 0.15, 0.05);

    // R/A = Resins / Asphaltenes = 0.15 / 0.05 = 3.0
    assertEquals(3.0, analyzer.getResinToAsphalteneRatio(), 0.001);
  }

  @Test
  void testDeBoerScreeningStable() {
    AsphalteneStabilityAnalyzer analyzer = new AsphalteneStabilityAnalyzer(testFluid);

    // Low undersaturation (low risk)
    // reservoirP = 200, satP = 180, density = 850 kg/m3
    AsphalteneStabilityAnalyzer.AsphalteneRisk risk = analyzer.deBoerScreening(200.0, 180.0, 850.0);

    assertNotNull(risk);
    // With small undersaturation and moderate density, should be stable/low risk
    assertTrue(risk == AsphalteneStabilityAnalyzer.AsphalteneRisk.STABLE
        || risk == AsphalteneStabilityAnalyzer.AsphalteneRisk.LOW_RISK);
  }

  @Test
  void testDeBoerScreeningHighRisk() {
    AsphalteneStabilityAnalyzer analyzer = new AsphalteneStabilityAnalyzer(testFluid);

    // High undersaturation with light oil (high risk)
    // reservoirP = 800, satP = 100, density = 700 kg/m3 (light oil)
    AsphalteneStabilityAnalyzer.AsphalteneRisk risk = analyzer.deBoerScreening(800.0, 100.0, 700.0);

    assertNotNull(risk);
    // Large undersaturation with light oil should give higher risk
    assertTrue(
        risk.ordinal() >= AsphalteneStabilityAnalyzer.AsphalteneRisk.MODERATE_RISK.ordinal());
  }

  @Test
  void testAsphalteneRiskEnum() {
    // Verify AsphalteneRisk enum values exist and are ordered
    AsphalteneStabilityAnalyzer.AsphalteneRisk[] levels =
        AsphalteneStabilityAnalyzer.AsphalteneRisk.values();

    assertEquals(5, levels.length);
    assertEquals(AsphalteneStabilityAnalyzer.AsphalteneRisk.STABLE, levels[0]);
    assertEquals(AsphalteneStabilityAnalyzer.AsphalteneRisk.LOW_RISK, levels[1]);
    assertEquals(AsphalteneStabilityAnalyzer.AsphalteneRisk.MODERATE_RISK, levels[2]);
    assertEquals(AsphalteneStabilityAnalyzer.AsphalteneRisk.HIGH_RISK, levels[3]);
    assertEquals(AsphalteneStabilityAnalyzer.AsphalteneRisk.SEVERE_RISK, levels[4]);
  }

  @Test
  void testComprehensiveAssessment() {
    AsphalteneStabilityAnalyzer analyzer = new AsphalteneStabilityAnalyzer(testFluid);

    // Set SARA fractions for the assessment
    analyzer.setSARAFractions(0.5, 0.3, 0.15, 0.05);

    // Reservoir: 500 bara, 373 K
    // Wellhead: 50 bara, 320 K
    String assessment = analyzer.comprehensiveAssessment(500.0, 373.0, 50.0, 320.0);

    assertNotNull(assessment);
    assertTrue(assessment.length() > 0);
    assertTrue(assessment.contains("CII"));
    assertTrue(assessment.contains("R/A"));
    assertTrue(assessment.contains("Recommendations"));
  }

  @Test
  void testOnsetPressureCalculation() {
    AsphalteneStabilityAnalyzer analyzer = new AsphalteneStabilityAnalyzer(testFluid);

    double onsetP = analyzer.calculateOnsetPressure(373.0);

    // Result should be either NaN (not found) or a valid pressure
    assertTrue(Double.isNaN(onsetP) || onsetP > 0);
  }

  @Test
  void testOnsetTemperatureCalculation() {
    AsphalteneStabilityAnalyzer analyzer = new AsphalteneStabilityAnalyzer(testFluid);

    double onsetT = analyzer.calculateOnsetTemperature(100.0);

    // Result should be either NaN (not found) or a valid temperature
    assertTrue(Double.isNaN(onsetT) || onsetT > 0);
  }

  @Test
  void testSetSystem() {
    AsphalteneStabilityAnalyzer analyzer = new AsphalteneStabilityAnalyzer(testFluid);

    // Create a new fluid
    SystemInterface newFluid = new SystemSrkCPAstatoil(400.0, 150.0);
    newFluid.addComponent("methane", 0.5);
    newFluid.addComponent("n-heptane", 0.5);
    newFluid.setMixingRule("classic");

    analyzer.setSystem(newFluid);

    // The analyzer should now use the new system
    // Test by running a calculation
    double onsetP = analyzer.calculateOnsetPressure(400.0);
    assertTrue(Double.isNaN(onsetP) || onsetP > 0);
  }

  @Test
  void testGeneratePrecipitationEnvelope() {
    AsphalteneStabilityAnalyzer analyzer = new AsphalteneStabilityAnalyzer(testFluid);

    double[][] envelope = analyzer.generatePrecipitationEnvelope(300.0, 400.0, 5);

    assertNotNull(envelope);
    assertEquals(3, envelope.length); // 3 arrays: temps, upper onset, lower onset
    assertEquals(5, envelope[0].length); // 5 points
  }

  @Test
  void testEvaluateSARAStability() {
    AsphalteneStabilityAnalyzer analyzer = new AsphalteneStabilityAnalyzer(testFluid);

    // Set stable SARA fractions (low CII)
    analyzer.setSARAFractions(0.3, 0.4, 0.25, 0.05);

    AsphalteneStabilityAnalyzer.AsphalteneRisk risk = analyzer.evaluateSARAStability();

    assertNotNull(risk);
    // Low CII should give stable rating
    assertEquals(AsphalteneStabilityAnalyzer.AsphalteneRisk.STABLE, risk);
  }

  @Test
  void testBubblePointCalculation() {
    AsphalteneStabilityAnalyzer analyzer = new AsphalteneStabilityAnalyzer(testFluid);

    double bubbleP = analyzer.calculateBubblePointPressure();

    // Should return a valid bubble point or NaN
    assertTrue(Double.isNaN(bubbleP) || bubbleP > 0);
  }
}
