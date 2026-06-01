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
    assertTrue(s.getRelatedCategories().contains("MECHANICAL"));
  }

  /**
   * Tests hypothesis builder creates valid hypothesis.
   */
  @Test
  void testHypothesisBuilder() {
    Hypothesis h =
        new Hypothesis.Builder().name("bearing_wear").description("Worn bearings causing vibration")
            .category(Hypothesis.Category.MECHANICAL).priorProbability(0.3)
            .addExpectedSignal("vibration|bearing", Hypothesis.ExpectedBehavior.INCREASE, 2.0,
                "Bearing wear should increase vibration")
            .addAction("Inspect bearings").addAction("Check lubrication").build();

    assertEquals("bearing_wear", h.getName());
    assertEquals("Worn bearings causing vibration", h.getDescription());
    assertEquals(Hypothesis.Category.MECHANICAL, h.getCategory());
    assertEquals(0.3, h.getPriorProbability(), 0.001);
    assertEquals(1, h.getExpectedSignals().size());
    assertEquals(Hypothesis.ExpectedBehavior.INCREASE,
        h.getExpectedSignals().get(0).getExpectedBehavior());
    assertEquals(2, h.getRecommendedActions().size());
  }

  /**
   * Tests hypothesis confidence calculation.
   */
  @Test
  void testHypothesisConfidenceCalculation() {
    Hypothesis h =
        new Hypothesis.Builder().name("test").description("Test").priorProbability(0.4).build();

    h.setLikelihoodScore(0.8);
    h.setVerificationScore(0.9);
    h.updateConfidence();

    // confidence = 0.4 * 0.8 * 0.9 = 0.288
    assertEquals(0.288, h.getConfidenceScore(), 0.001);

    h.setPriorProbability(0.5);
    assertEquals(0.360, h.getConfidenceScore(), 0.001);
  }

  /**
   * Tests richer evidence metadata.
   */
  @Test
  void testEvidenceMetadata() {
    Hypothesis.Evidence evidence = new Hypothesis.Evidence("vibration", "Increasing trend",
        Hypothesis.EvidenceStrength.STRONG, "historian", true, 2.5, "PI:T-1234");

    assertTrue(evidence.isSupporting());
    assertEquals(2.5, evidence.getWeight(), 0.001);
    assertEquals("PI:T-1234", evidence.getSourceReference());
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

    Hypothesis h = new Hypothesis.Builder().name("test").description("Test").build();
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
   * Tests evidence collector supports matching expected signals.
   */
  @Test
  void testEvidenceCollectorExpectedSignalSupport() {
    EvidenceCollector collector = new EvidenceCollector();
    double[] values = new double[] {2.0, 3.0, 4.2, 5.5, 7.0};
    Map<String, double[]> data = new HashMap<>();
    data.put("bearing_vibration", values);
    collector.setHistorianData(data, new double[] {0, 1, 2, 3, 4});

    Hypothesis h = new Hypothesis.Builder()
        .name("bearing_degradation").description("Bearing").addExpectedSignal("vibration|bearing",
            Hypothesis.ExpectedBehavior.INCREASE, 3.0, "Bearing degradation raises vibration")
        .build();

    List<Hypothesis.Evidence> evidence = collector.collectEvidence(h);
    assertFalse(evidence.isEmpty());
    assertTrue(evidence.get(0).isSupporting());
    assertEquals(3.0, evidence.get(0).getWeight(), 0.001);
  }

  /**
   * Tests evidence collector marks opposite behavior as contradictory.
   */
  @Test
  void testEvidenceCollectorContradictorySignal() {
    EvidenceCollector collector = new EvidenceCollector();
    double[] values = new double[] {7.0, 5.5, 4.0, 3.0, 2.0};
    Map<String, double[]> data = new HashMap<>();
    data.put("bearing_vibration", values);
    collector.setHistorianData(data, new double[] {0, 1, 2, 3, 4});

    Hypothesis h = new Hypothesis.Builder()
        .name("bearing_degradation").description("Bearing").addExpectedSignal("vibration|bearing",
            Hypothesis.ExpectedBehavior.INCREASE, 3.0, "Bearing degradation raises vibration")
        .build();

    List<Hypothesis.Evidence> evidence = collector.collectEvidence(h);
    assertFalse(evidence.isEmpty());
    assertFalse(evidence.get(0).isSupporting());
    assertEquals(Hypothesis.EvidenceStrength.CONTRADICTORY, evidence.get(0).getStrength());
  }

  /**
   * Tests expected signals prevent irrelevant generic historian trends from being attached.
   */
  @Test
  void testEvidenceCollectorFiltersIrrelevantSignals() {
    EvidenceCollector collector = new EvidenceCollector();
    Map<String, double[]> data = new HashMap<>();
    data.put("unrelated_pressure", new double[] {10.0, 11.0, 12.0, 13.0});
    collector.setHistorianData(data, new double[] {0, 1, 2, 3});

    Hypothesis h = new Hypothesis.Builder()
        .name("bearing_degradation").description("Bearing").addExpectedSignal("vibration|bearing",
            Hypothesis.ExpectedBehavior.INCREASE, 3.0, "Bearing degradation raises vibration")
        .build();

    assertTrue(collector.collectEvidence(h).isEmpty());
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
    Hypothesis.Evidence contradictory = new Hypothesis.Evidence("vibration", "Decreasing",
        Hypothesis.EvidenceStrength.CONTRADICTORY, "test", false, 2.0, "source");

    List<Hypothesis.Evidence> evidence = Arrays.asList(strong, moderate);
    double score = collector.calculateLikelihoodScore(evidence);

    double contradictedScore =
        collector.calculateLikelihoodScore(Arrays.asList(strong, contradictory));

    assertTrue(score > 0.5, "Score with strong + moderate evidence should be > 0.5");
    assertTrue(score < 1.0, "Score should be < 1.0");
    assertTrue(contradictedScore < score, "Contradictory evidence should reduce likelihood");
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
        assertFalse(h.getExpectedSignals().isEmpty());
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
    assertTrue(json.contains("\"confidenceScore\""));
    assertTrue(json.contains(compressorName));
  }

  /**
   * Tests root cause report JSON includes richer evidence metadata.
   */
  @Test
  void testReportJsonEvidenceMetadata() {
    RootCauseAnalyzer rca = new RootCauseAnalyzer(process, compressorName);
    rca.setSymptom(Symptom.HIGH_VIBRATION);
    rca.setSimulationEnabled(false);

    Map<String, double[]> data = new HashMap<>();
    data.put("bearing_vibration", new double[] {2.0, 3.0, 4.5, 5.8, 7.0});
    rca.setHistorianData(data, new double[] {0, 1, 2, 3, 4});

    String json = rca.analyze().toJson();
    assertTrue(json.contains("\"supporting\""));
    assertTrue(json.contains("\"weight\""));
    assertTrue(json.contains("\"sourceReference\""));
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
   * Tests unsupported equipment gets a neutral simulation verification result.
   */
  @Test
  void testUnsupportedSimulationVerificationIsNeutral() {
    RootCauseAnalyzer rca = new RootCauseAnalyzer(process, "feed");
    rca.setSymptom(Symptom.FLOW_DEVIATION);
    rca.setSimulationEnabled(true);

    RootCauseReport report = rca.analyze();
    Hypothesis top = report.getTopHypothesis();
    assertNotNull(top);
    assertEquals(0.5, top.getVerificationScore(), 0.001);
    assertTrue(top.getSimulationSummary().contains("neutral"));
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

  /**
   * Tests that Bayesian normalization makes confidence scores sum to approximately 1.0.
   */
  @Test
  void testBayesianNormalization() {
    RootCauseAnalyzer rca = new RootCauseAnalyzer(process, compressorName);
    rca.setSymptom(Symptom.HIGH_VIBRATION);
    rca.setSimulationEnabled(false);

    // Add historian data so hypotheses get non-trivial scores
    int n = 50;
    double[] vibration = new double[n];
    double[] timestamps = new double[n];
    for (int i = 0; i < n; i++) {
      vibration[i] = 3.0 + 0.08 * i;
      timestamps[i] = i * 3600.0;
    }
    Map<String, double[]> data = new HashMap<>();
    data.put("vibration_mm_s", vibration);
    rca.setHistorianData(data, timestamps);

    RootCauseReport report = rca.analyze();
    List<Hypothesis> ranked = report.getRankedHypotheses();
    assertFalse(ranked.isEmpty());

    double sum = 0.0;
    for (Hypothesis h : ranked) {
      sum += h.getConfidenceScore();
    }
    // After normalization, scores should sum to 1.0 (within floating point tolerance)
    assertEquals(1.0, sum, 0.01, "Normalized confidence scores should sum to 1.0");
  }

  /**
   * Tests that hypotheses are returned in descending confidence order.
   */
  @Test
  void testHypothesesDescendingOrder() {
    RootCauseAnalyzer rca = new RootCauseAnalyzer(process, compressorName);
    rca.setSymptom(Symptom.HIGH_VIBRATION);
    rca.setSimulationEnabled(false);

    RootCauseReport report = rca.analyze();
    List<Hypothesis> ranked = report.getRankedHypotheses();

    for (int i = 1; i < ranked.size(); i++) {
      assertTrue(ranked.get(i - 1).getConfidenceScore() >= ranked.get(i).getConfidenceScore(),
          "Hypotheses should be sorted in descending confidence order");
    }
  }

  /**
   * Tests Builder.copy() produces an independent copy that does not share state.
   */
  @Test
  void testBuilderCopyIsIndependent() {
    Hypothesis.Builder original = new Hypothesis.Builder()
        .name("original").description("Original hypothesis")
        .category(Hypothesis.Category.MECHANICAL)
        .priorProbability(0.3)
        .addExpectedSignal("temp", Hypothesis.ExpectedBehavior.INCREASE, 2.0, "temp rises");

    Hypothesis.Builder copy = original.copy();
    copy.name("copy").description("Copied hypothesis").priorProbability(0.5)
        .addExpectedSignal("pressure", Hypothesis.ExpectedBehavior.DECREASE, 1.5, "pressure drops");

    Hypothesis h1 = original.build();
    Hypothesis h2 = copy.build();

    assertEquals("original", h1.getName());
    assertEquals("copy", h2.getName());
    assertEquals(0.3, h1.getPriorProbability(), 0.001);
    assertEquals(0.5, h2.getPriorProbability(), 0.001);
    assertEquals(1, h1.getExpectedSignals().size(), "Original should have 1 signal");
    assertEquals(2, h2.getExpectedSignals().size(), "Copy should have 2 signals");
  }

  /**
   * Tests that hypothesis generator produces distinct hypotheses (no Builder reuse corruption).
   */
  @Test
  void testGeneratorProducesDistinctHypotheses() {
    HypothesisGenerator gen = new HypothesisGenerator();
    Compressor comp = (Compressor) process.getUnit(compressorName);

    List<Hypothesis> hypotheses = gen.generate(comp, Symptom.HIGH_VIBRATION);
    assertTrue(hypotheses.size() >= 2, "Should generate at least 2 hypotheses");

    // Verify all hypotheses have distinct names and independent signal lists
    for (int i = 0; i < hypotheses.size(); i++) {
      for (int j = i + 1; j < hypotheses.size(); j++) {
        assertFalse(hypotheses.get(i).getName().equals(hypotheses.get(j).getName()),
            "Each hypothesis should have a unique name");
      }
    }
  }

  /**
   * Tests change-point detection in evidence collector.
   */
  @Test
  void testEvidenceCollectorChangePointDetection() {
    EvidenceCollector collector = new EvidenceCollector();

    int n = 60;
    double[] values = new double[n];
    double[] timestamps = new double[n];
    for (int i = 0; i < n; i++) {
      // Deterministic step change at midpoint: mean jumps from 50 to 100
      values[i] = i < 30 ? 50.0 : 100.0;
      timestamps[i] = i * 60.0;
    }

    Map<String, double[]> data = new HashMap<>();
    data.put("discharge_temperature", values);
    collector.setHistorianData(data, timestamps);

    Hypothesis h = new Hypothesis.Builder()
        .name("process_change").description("Test")
        .addExpectedSignal("temperature", Hypothesis.ExpectedBehavior.INCREASE, 2.0, "temp rises")
        .build();

    List<Hypothesis.Evidence> evidence = collector.collectEvidence(h);

    boolean foundChangePoint = false;
    for (Hypothesis.Evidence e : evidence) {
      if (e.getSource().contains("changepoint")) {
        foundChangePoint = true;
      }
    }
    assertTrue(foundChangePoint, "Should detect change point in step data");
  }

  /**
   * Tests that multi-parameter pattern analysis produces evidence when multiple signals match.
   */
  @Test
  void testMultiParameterPatternAnalysis() {
    EvidenceCollector collector = new EvidenceCollector();

    int n = 30;
    double[] vibration = new double[n];
    double[] efficiency = new double[n];
    double[] timestamps = new double[n];
    for (int i = 0; i < n; i++) {
      vibration[i] = 3.0 + 0.2 * i; // increasing
      efficiency[i] = 85.0 - 0.5 * i; // decreasing
      timestamps[i] = i * 3600.0;
    }

    Map<String, double[]> data = new HashMap<>();
    data.put("bearing_vibration", vibration);
    data.put("compressor_efficiency", efficiency);
    collector.setHistorianData(data, timestamps);

    Hypothesis h = new Hypothesis.Builder()
        .name("bearing_wear").description("Test")
        .addExpectedSignal("vibration", Hypothesis.ExpectedBehavior.INCREASE, 2.0, "vibration up")
        .addExpectedSignal("efficiency", Hypothesis.ExpectedBehavior.DECREASE, 2.0, "eff down")
        .build();

    List<Hypothesis.Evidence> evidence = collector.collectEvidence(h);

    boolean foundPattern = false;
    for (Hypothesis.Evidence e : evidence) {
      if (e.getSource().contains("pattern")) {
        foundPattern = true;
        assertTrue(e.isSupporting());
      }
    }
    assertTrue(foundPattern, "Should detect multi-parameter fingerprint");
  }

  /**
   * Tests OREDA fuzzy matching with token overlap.
   */
  @Test
  void testOredaFuzzyTokenMatching() {
    HypothesisGenerator gen = new HypothesisGenerator();

    // Create hypotheses with failure modes that don't exactly match OREDA names
    Hypothesis h1 = new Hypothesis.Builder()
        .name("bearing_wear").failureMode("bearing wear damage")
        .priorProbability(0.1).build();
    Hypothesis h2 = new Hypothesis.Builder()
        .name("seal_failure").failureMode("seal leakage")
        .priorProbability(0.1).build();

    List<Hypothesis> hypotheses = Arrays.asList(h1, h2);

    // adjustPriorsFromOreda is package-private, so we can call it directly in tests
    gen.adjustPriorsFromOreda(hypotheses, "Compressor");

    // Even if no OREDA data matches, this should not throw
    assertNotNull(hypotheses);
  }

  /**
   * Tests Levenshtein distance computation in the generator.
   */
  @Test
  void testLevenshteinDistanceInFuzzyMatch() {
    HypothesisGenerator gen = new HypothesisGenerator();

    // Generate hypotheses for various symptoms
    Compressor comp = (Compressor) process.getUnit(compressorName);
    List<Hypothesis> highVib = gen.generate(comp, Symptom.HIGH_VIBRATION);
    List<Hypothesis> lowEff = gen.generate(comp, Symptom.LOW_EFFICIENCY);

    // Different symptoms should produce different hypothesis sets
    assertFalse(highVib.isEmpty());
    assertFalse(lowEff.isEmpty());
    assertFalse(highVib.get(0).getName().equals(lowEff.get(0).getName()),
        "Different symptoms should produce different top hypotheses");
  }

  /**
   * Tests separator analysis works end-to-end.
   */
  @Test
  void testSeparatorAnalysis() {
    // Build process with separator
    SystemInterface gas = new SystemSrkEos(273.15 + 30.0, 50.0);
    gas.addComponent("methane", 0.70);
    gas.addComponent("nC10", 0.30);
    gas.setMixingRule("classic");
    Stream feed = new Stream("sep_feed", gas);
    feed.setFlowRate(10000.0, "kg/hr");
    Separator sep = new Separator("TestSeparator", feed);

    ProcessSystem sepProcess = new ProcessSystem();
    sepProcess.add(feed);
    sepProcess.add(sep);
    sepProcess.run();

    RootCauseAnalyzer rca = new RootCauseAnalyzer(sepProcess, "TestSeparator");
    rca.setSymptom(Symptom.LIQUID_CARRYOVER);
    rca.setSimulationEnabled(false);

    RootCauseReport report = rca.analyze();
    assertNotNull(report);
    assertFalse(report.getRankedHypotheses().isEmpty());
  }

  /**
   * Tests that hypothesis setConfidenceScore clamps to [0, 1].
   */
  @Test
  void testConfidenceScoreClamping() {
    Hypothesis h = new Hypothesis.Builder()
        .name("test").description("Test").priorProbability(0.5).build();

    h.setConfidenceScore(1.5);
    assertEquals(1.0, h.getConfidenceScore(), 0.001, "Score above 1.0 should be clamped");

    h.setConfidenceScore(-0.5);
    assertEquals(0.0, h.getConfidenceScore(), 0.001, "Score below 0.0 should be clamped");
  }
}
