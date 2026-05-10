package neqsim.process.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the root cause analysis framework.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
class RootCauseAnalyzerTest {

  private ProcessSystem process;
  private String compressorName;

  /**
   * Sets up a simple process with a compressor for testing.
   */
  @BeforeEach
  void setUp() {
    SystemInterface gas = new SystemSrkEos(273.15 + 30.0, 30.0);
    gas.addComponent("methane", 0.85);
    gas.addComponent("ethane", 0.10);
    gas.addComponent("propane", 0.05);
    gas.setMixingRule("classic");

    Stream feed = new Stream("feed", gas);
    feed.setFlowRate(50000.0, "kg/hr");

    compressorName = "TestCompressor";
    Compressor comp = new Compressor(compressorName, feed);
    comp.setOutletPressure(80.0);

    process = new ProcessSystem();
    process.add(feed);
    process.add(comp);
    process.run();
  }

  /**
   * Tests that the analyzer rejects null process system.
   */
  @Test
  void testNullProcessThrows() {
    assertThrows(IllegalArgumentException.class, () -> new RootCauseAnalyzer(null, "name"));
  }

  /**
   * Tests that the analyzer rejects null equipment name.
   */
  @Test
  void testNullEquipmentNameThrows() {
    assertThrows(IllegalArgumentException.class, () -> new RootCauseAnalyzer(process, null));
  }

  /**
   * Tests that analyze() without symptom throws.
   */
  @Test
  void testAnalyzeWithoutSymptomThrows() {
    RootCauseAnalyzer rca = new RootCauseAnalyzer(process, compressorName);
    assertThrows(IllegalStateException.class, () -> rca.analyze());
  }

  /**
   * Tests basic analysis flow with compressor high vibration.
   */
  @Test
  void testCompressorHighVibrationAnalysis() {
    RootCauseAnalyzer rca = new RootCauseAnalyzer(process, compressorName);
    rca.setSymptom(Symptom.HIGH_VIBRATION);
    rca.setSimulationEnabled(false); // Faster test

    RootCauseReport report = rca.analyze();

    assertNotNull(report);
    assertEquals(compressorName, report.getEquipmentName());
    assertEquals(Symptom.HIGH_VIBRATION, report.getSymptom());
    assertFalse(report.getRankedHypotheses().isEmpty());
    assertNotNull(report.getTopHypothesis());
  }

  /**
   * Tests that historian data evidence adjusts confidence scores.
   */
  @Test
  void testHistorianDataAdjustsConfidence() {
    RootCauseAnalyzer rca = new RootCauseAnalyzer(process, compressorName);
    rca.setSymptom(Symptom.HIGH_VIBRATION);
    rca.setSimulationEnabled(false);

    // Simulate degrading vibration trend
    int n = 100;
    double[] vibration = new double[n];
    double[] timestamps = new double[n];
    for (int i = 0; i < n; i++) {
      vibration[i] = 3.0 + 0.05 * i + Math.random() * 0.5; // increasing trend
      timestamps[i] = i * 3600.0; // hourly
    }

    Map<String, double[]> historianData = new HashMap<>();
    historianData.put("vibration_mm_s", vibration);
    rca.setHistorianData(historianData, timestamps);
    rca.setDesignLimit("vibration_mm_s", Double.NaN, 7.1);

    RootCauseReport report = rca.analyze();

    assertNotNull(report);
    assertTrue(report.getDataPointsAnalyzed() > 0);
    assertEquals(1, report.getParametersAnalyzed());

    // Should have evidence about the vibration trend
    Hypothesis top = report.getTopHypothesis();
    assertNotNull(top);
    assertTrue(top.getConfidenceScore() > 0);
  }

  /**
   * Tests symptom enum has expected attributes.
   */
  @Test
  void testSymptomAttributes() {
    Symptom s = Symptom.HIGH_VIBRATION;
    assertNotNull(s.getDescription());
    assertFalse(s.getRelatedCategories().isEmpty());
    assertTrue(s.getRelatedCategories().contains(Hypothesis.Category.MECHANICAL));
  }

  /**
   * Tests hypothesis builder creates valid hypothesis.
   */
  @Test
  void testHypothesisBuilder() {
    Hypothesis h = new Hypothesis.Builder("bearing_wear", "Bearing wear")
        .description("Worn bearings causing vibration")
        .category(Hypothesis.Category.MECHANICAL)
        .priorProbability(0.3)
        .addRecommendedAction("Inspect bearings")
        .addRecommendedAction("Check lubrication")
        .build();

    assertEquals("bearing_wear", h.getName());
    assertEquals("Bearing wear", h.getDescription());
    assertEquals(Hypothesis.Category.MECHANICAL, h.getCategory());
    assertEquals(0.3, h.getPriorProbability(), 0.001);
    assertEquals(2, h.getRecommendedActions().size());
  }

  /**
   * Tests hypothesis confidence calculation.
   */
  @Test
  void testHypothesisConfidenceCalculation() {
    Hypothesis h = new Hypothesis.Builder("test", "Test")
        .priorProbability(0.4)
        .build();

    h.setLikelihoodScore(0.8);
    h.setVerificationScore(0.9);
    h.updateConfidence();

    // confidence = 0.4 * 0.8 * 0.9 = 0.288
    assertEquals(0.288, h.getConfidenceScore(), 0.001);
  }

  /**
   * Tests evidence collector trend analysis.
   */
  @Test
  void testEvidenceCollectorTrendAnalysis() {
    EvidenceCollector collector = new EvidenceCollector();

    int n = 50;
    double[] values = new double[n];
    double[] timestamps = new double[n];
    for (int i = 0; i < n; i++) {
      values[i] = 100.0 + 2.0 * i; // strong linear trend
      timestamps[i] = i * 60.0;
    }

    Map<String, double[]> data = new HashMap<>();
    data.put("temperature", values);
    collector.setHistorianData(data, timestamps);

    Hypothesis h = new Hypothesis.Builder("test", "Test").build();
    List<Hypothesis.Evidence> evidence = collector.collectEvidence(h);

    assertFalse(evidence.isEmpty());
    // Should detect the increasing trend
    boolean foundTrend = false;
    for (Hypothesis.Evidence e : evidence) {
      if (e.getSource().contains("trend")) {
        foundTrend = true;
        assertEquals(Hypothesis.EvidenceStrength.STRONG, e.getStrength());
      }
    }
    assertTrue(foundTrend, "Should detect strong increasing trend");
  }

  /**
   * Tests evidence collector likelihood calculation.
   */
  @Test
  void testLikelihoodScoreCalculation() {
    EvidenceCollector collector = new EvidenceCollector();

    Hypothesis.Evidence strong =
        new Hypothesis.Evidence("temp", "High", Hypothesis.EvidenceStrength.STRONG, "test");
    Hypothesis.Evidence moderate =
        new Hypothesis.Evidence("press", "OK", Hypothesis.EvidenceStrength.MODERATE, "test");

    List<Hypothesis.Evidence> evidence = Arrays.asList(strong, moderate);
    double score = collector.calculateLikelihoodScore(evidence);

    assertTrue(score > 0.5, "Score with strong + moderate evidence should be > 0.5");
    assertTrue(score < 1.0, "Score should be < 1.0");
  }

  /**
   * Tests hypothesis generator produces hypotheses for known equipment.
   */
  @Test
  void testHypothesisGeneratorForCompressor() {
    HypothesisGenerator gen = new HypothesisGenerator();

    Compressor comp = (Compressor) process.getUnit(compressorName);
    List<Hypothesis> hypotheses = gen.generate(comp, Symptom.HIGH_VIBRATION);

    assertFalse(hypotheses.isEmpty());
    // Should contain bearing wear hypothesis for compressor vibration
    boolean foundBearing = false;
    for (Hypothesis h : hypotheses) {
      if (h.getName().toLowerCase().contains("bearing")) {
        foundBearing = true;
      }
    }
    assertTrue(foundBearing, "Should include bearing-related hypothesis for vibration");
  }

  /**
   * Tests root cause report JSON output.
   */
  @Test
  void testReportJsonOutput() {
    RootCauseAnalyzer rca = new RootCauseAnalyzer(process, compressorName);
    rca.setSymptom(Symptom.HIGH_VIBRATION);
    rca.setSimulationEnabled(false);

    RootCauseReport report = rca.analyze();
    String json = report.toJson();

    assertNotNull(json);
    assertTrue(json.contains("\"equipment\""));
    assertTrue(json.contains("\"symptom\""));
    assertTrue(json.contains("\"hypotheses\""));
    assertTrue(json.contains(compressorName));
  }

  /**
   * Tests root cause report text output.
   */
  @Test
  void testReportTextOutput() {
    RootCauseAnalyzer rca = new RootCauseAnalyzer(process, compressorName);
    rca.setSymptom(Symptom.HIGH_VIBRATION);
    rca.setSimulationEnabled(false);

    RootCauseReport report = rca.analyze();
    String text = report.toTextReport();

    assertNotNull(text);
    assertTrue(text.contains("ROOT CAUSE ANALYSIS REPORT"));
    assertTrue(text.contains(compressorName));
    assertTrue(text.contains("HIGH_VIBRATION"));
  }

  /**
   * Tests full analysis with simulation verification.
   */
  @Test
  void testFullAnalysisWithSimulation() {
    RootCauseAnalyzer rca = new RootCauseAnalyzer(process, compressorName);
    rca.setSymptom(Symptom.LOW_EFFICIENCY);
    rca.setSimulationEnabled(true);

    // Add historian data suggesting efficiency degradation
    int n = 50;
    double[] efficiency = new double[n];
    double[] timestamps = new double[n];
    for (int i = 0; i < n; i++) {
      efficiency[i] = 85.0 - 0.3 * i; // decreasing efficiency
      timestamps[i] = i * 3600.0;
    }

    Map<String, double[]> data = new HashMap<>();
    data.put("polytropicEfficiency", efficiency);
    rca.setHistorianData(data, timestamps);

    RootCauseReport report = rca.analyze();

    assertNotNull(report);
    Hypothesis top = report.getTopHypothesis();
    assertNotNull(top);
    // With simulation, verification score should be set
    assertTrue(top.getVerificationScore() >= 0);
  }

  /**
   * Tests results map for task integration.
   */
  @Test
  void testReportToResultsMap() {
    RootCauseAnalyzer rca = new RootCauseAnalyzer(process, compressorName);
    rca.setSymptom(Symptom.HIGH_VIBRATION);
    rca.setSimulationEnabled(false);

    RootCauseReport report = rca.analyze();
    Map<String, Object> results = report.toResultsMap();

    assertNotNull(results);
    assertEquals(compressorName, results.get("equipment"));
    assertEquals("HIGH_VIBRATION", results.get("symptom"));
    assertNotNull(results.get("topHypothesis"));
  }
}
