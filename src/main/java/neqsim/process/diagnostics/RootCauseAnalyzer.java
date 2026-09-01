package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;

/**
 * Main orchestrator for root cause analysis of equipment issues.
 *
 * <p>
 * Integrates process simulation, multi-source reliability data, historian time-series, and STID design conditions to
 * produce a ranked list of failure hypotheses. The analysis follows a Bayesian- inspired methodology:
 * </p>
 *
 * <ol>
 * <li><b>Prior</b> — OREDA failure mode frequencies set initial hypothesis probabilities</li>
 * <li><b>Likelihood</b> — historian data evidence updates hypothesis scores</li>
 * <li><b>Verification</b> — process simulation confirms if a hypothesized failure reproduces observed symptoms</li>
 * </ol>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * RootCauseAnalyzer rca = new RootCauseAnalyzer(processSystem, "Compressor-1");
 * rca.setSymptom(Symptom.HIGH_VIBRATION);
 * rca.setHistorianData(historianMap, timestamps);
 * rca.setStidData(stidMap);
 * rca.setDesignLimit("vibration_mm_s", Double.NaN, 7.1);
 * RootCauseReport report = rca.analyze();
 * String textReport = report.toTextReport();
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see Symptom
 * @see Hypothesis
 * @see RootCauseReport
 */
public class RootCauseAnalyzer implements Serializable {

  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(RootCauseAnalyzer.class);

  /** Process system containing the equipment under investigation. */
  private final ProcessSystem processSystem;

  /** Name of the equipment being diagnosed. */
  private final String equipmentName;

  /** Reported symptom. */
  private Symptom symptom;

  /** Historian time-series data. */
  private Map<String, double[]> historianData;

  /** Timestamps for historian data. */
  private double[] timestamps;

  /** STID design data. */
  private Map<String, String> stidData;

  /** Design limits for threshold analysis. */
  private Map<String, double[]> designLimits;

  /** Whether to run simulation verification. */
  private boolean simulationEnabled;

  /** Hypothesis generator (can be customized). */
  private HypothesisGenerator generator;

  /** Anomalies detected by the most recent autonomous analysis. */
  private List<AnomalyScanner.Anomaly> lastAnomalies = new ArrayList<>();

  /** Relationships discovered by the most recent autonomous analysis. */
  private List<RelationshipGraph.Relationship> lastRelationships = new ArrayList<>();

  /** Topology-classified causal edges from the most recent autonomous analysis. */
  private List<CausalTopologyModel.CausalEdge> lastCausalEdges = new ArrayList<>();

  /** Reproducibility and provenance context for the analysis. */
  private DiagnosisCase diagnosisCase;

  /** Whether the current analysis should consume the most recent autonomous findings. */
  private transient boolean autonomousEvidenceActive;

  /**
   * Creates a root cause analyzer.
   *
   * @param processSystem the process system containing the equipment
   * @param equipmentName name of the equipment to diagnose
   * @throws IllegalArgumentException if processSystem or equipmentName is null
   */
  public RootCauseAnalyzer(ProcessSystem processSystem, String equipmentName) {
    if (processSystem == null) {
      throw new IllegalArgumentException("processSystem must not be null");
    }
    if (equipmentName == null || equipmentName.trim().isEmpty()) {
      throw new IllegalArgumentException("equipmentName must not be null or empty");
    }
    this.processSystem = processSystem;
    this.equipmentName = equipmentName;
    this.historianData = new HashMap<>();
    this.stidData = new HashMap<>();
    this.designLimits = new HashMap<>();
    this.simulationEnabled = true;
    this.generator = new HypothesisGenerator();
  }

  /**
   * Sets the symptom to investigate.
   *
   * @param symptom the reported symptom
   */
  public void setSymptom(Symptom symptom) {
    this.symptom = symptom;
  }

  /**
   * Sets historian time-series data for evidence analysis.
   *
   * @param data map of parameter name to time-series values
   * @param timestamps array of timestamps parallel to data arrays
   */
  public void setHistorianData(Map<String, double[]> data, double[] timestamps) {
    this.historianData = data != null ? data : new HashMap<String, double[]>();
    this.timestamps = timestamps;
  }

  /**
   * Sets STID equipment documentation data.
   *
   * @param stidData map of parameter to design value/description
   */
  public void setStidData(Map<String, String> stidData) {
    this.stidData = stidData != null ? stidData : new HashMap<String, String>();
  }

  /**
   * Sets a design limit for threshold analysis.
   *
   * @param parameter parameter name
   * @param lowLimit low limit (use Double.NaN for no low limit)
   * @param highLimit high limit (use Double.NaN for no high limit)
   */
  public void setDesignLimit(String parameter, double lowLimit, double highLimit) {
    designLimits.put(parameter, new double[] { lowLimit, highLimit });
  }

  /**
   * Enables or disables simulation verification.
   *
   * <p>
   * When disabled, the analysis skips the process simulation step (faster but less accurate).
   * </p>
   *
   * @param enabled true to enable simulation verification
   */
  public void setSimulationEnabled(boolean enabled) {
    this.simulationEnabled = enabled;
  }

  /**
   * Sets a custom hypothesis generator.
   *
   * @param generator custom generator with registered hypothesis libraries
   */
  public void setHypothesisGenerator(HypothesisGenerator generator) {
    this.generator = generator;
  }

  /**
   * Sets caller-supplied reproducibility and provenance context.
   *
   * @param diagnosisCase diagnosis case, or {@code null} to derive one from the historian data
   */
  public void setDiagnosisCase(DiagnosisCase diagnosisCase) {
    this.diagnosisCase = diagnosisCase;
  }

  /**
   * Returns the configured diagnosis case.
   *
   * @return diagnosis case, or {@code null} before it is configured or derived by analysis
   */
  public DiagnosisCase getDiagnosisCase() {
    return diagnosisCase;
  }

  /**
   * Runs an autonomous root cause analysis that discovers the symptom and relationships on its own.
   *
   * <p>
   * Unlike {@link #analyze()}, this method does not require a symptom to be set. It first scans the historian data with
   * {@link AnomalyScanner} to find abnormal tags and propose a candidate symptom, then discovers cross-tag lead-lag
   * relationships with {@link RelationshipGraph}, and finally runs the Bayesian hypothesis analysis with the
   * auto-detected symptom. If a symptom was set explicitly it is kept and used instead of the scanned one. The detected
   * anomalies and relationships are retained and are available via {@link #getLastAnomalies()} and
   * {@link #getLastRelationships()}. The tag-to-equipment map for topology classification is auto-derived, so
   * {@link #getLastCausalEdges()} is populated turnkey.
   * </p>
   *
   * @return root cause analysis report with ranked hypotheses
   * @throws IllegalStateException if no symptom was set and none could be inferred from the data
   */
  public RootCauseReport analyzeAutonomous() {
    return analyzeAutonomous(null);
  }

  /**
   * Runs an autonomous root cause analysis and promotes discovered relationships to causal candidates using the process
   * topology.
   *
   * @param tagToEquipment map of tag name to owning equipment name; when {@code null} the map is auto-derived from the
   * historian tag names and the process equipment (see {@link #inferTagToEquipment()}), so the topology classification
   * runs turnkey without a caller-supplied map. The classified relationships are available via
   * {@link #getLastCausalEdges()}
   * @return root cause analysis report with ranked hypotheses
   * @throws IllegalStateException if no symptom was set and none could be inferred from the data
   */
  public RootCauseReport analyzeAutonomous(Map<String, String> tagToEquipment) {
    // Step A: scan for anomalies and propose a symptom when none was set.
    AnomalyScanner scanner = new AnomalyScanner();
    for (Map.Entry<String, double[]> entry : designLimits.entrySet()) {
      scanner.setDesignLimit(entry.getKey(), entry.getValue()[0], entry.getValue()[1]);
    }
    this.lastAnomalies = scanner.scan(historianData);

    if (symptom == null) {
      Symptom suggested = scanner.suggestSymptom(lastAnomalies);
      if (suggested == null) {
        throw new IllegalStateException(
            "Autonomous analysis found no symptom-mapping anomaly; set a symptom explicitly "
                + "or supply design limits and named tags (e.g. containing 'vibration', 'temperature', 'pressure').");
      }
      logger.info("AnomalyScanner inferred symptom {} for '{}'", suggested.name(), equipmentName);
      this.symptom = suggested;
    }

    // Step B: discover lead-lag relationships across all tags.
    RelationshipGraph graph = new RelationshipGraph();
    graph.setTimestamps(timestamps);
    this.lastRelationships = graph.analyze(historianData);

    // Step C: promote relationships to causal candidates using topology. Auto-derive the tag-to-equipment map when the
    // caller did not supply one, so the topology verdict comes for free.
    Map<String, String> resolvedTagMap = tagToEquipment != null ? tagToEquipment : inferTagToEquipment();
    if (resolvedTagMap != null && !resolvedTagMap.isEmpty() && !lastRelationships.isEmpty()) {
      Map<String, java.util.Set<String>> adjacency = CausalTopologyModel.buildDownstreamAdjacency(processSystem);
      CausalTopologyModel topology = new CausalTopologyModel(adjacency, resolvedTagMap);
      this.lastCausalEdges = topology.classify(lastRelationships);
    } else {
      this.lastCausalEdges = new ArrayList<>();
    }

    // Step D: run the standard analysis while admitting relevant autonomous findings as evidence.
    this.autonomousEvidenceActive = true;
    try {
      return analyze();
    } finally {
      this.autonomousEvidenceActive = false;
    }
  }

  /**
   * Infers a tag-to-equipment map by matching each historian tag name against the process equipment names.
   *
   * <p>
   * A tag is assigned to the equipment whose name it starts with (before a dot/underscore separator) or, failing that,
   * the longest equipment name it contains (case-insensitive). Tags that match no equipment are left unmapped and are
   * simply skipped by the topology classifier.
   * </p>
   *
   * @return a map of tag name to owning equipment name; may be empty when no tags match any equipment
   */
  private Map<String, String> inferTagToEquipment() {
    Map<String, String> map = new HashMap<String, String>();
    if (historianData == null || historianData.isEmpty()) {
      return map;
    }
    List<String> equipmentNames = new ArrayList<String>();
    for (ProcessEquipmentInterface eq : processSystem.getUnitOperations()) {
      if (eq.getName() != null && !eq.getName().trim().isEmpty()) {
        equipmentNames.add(eq.getName());
      }
    }
    for (String tag : historianData.keySet()) {
      if (tag == null) {
        continue;
      }
      String prefix = tag;
      int sep = tag.indexOf('.');
      if (sep < 0) {
        sep = tag.indexOf('_');
      }
      if (sep > 0) {
        prefix = tag.substring(0, sep);
      }
      String best = null;
      for (String name : equipmentNames) {
        // Prefer an exact prefix match; otherwise the longest contained equipment name.
        boolean prefixMatch = prefix.equalsIgnoreCase(name);
        boolean containsMatch = tag.toLowerCase(java.util.Locale.ROOT)
            .contains(name.toLowerCase(java.util.Locale.ROOT));
        if (prefixMatch) {
          best = name;
          break;
        }
        if (containsMatch && (best == null || name.length() > best.length())) {
          best = name;
        }
      }
      if (best != null) {
        map.put(tag, best);
      }
    }
    return map;
  }

  /**
   * Returns the anomalies detected by the most recent autonomous analysis.
   *
   * @return list of anomalies, empty when none were detected or autonomous analysis has not been run
   */
  public List<AnomalyScanner.Anomaly> getLastAnomalies() {
    return lastAnomalies;
  }

  /**
   * Returns the lead-lag relationships discovered by the most recent autonomous analysis.
   *
   * @return list of relationships, empty when none were discovered or autonomous analysis has not been run
   */
  public List<RelationshipGraph.Relationship> getLastRelationships() {
    return lastRelationships;
  }

  /**
   * Returns the topology-classified causal edges from the most recent autonomous analysis.
   *
   * @return list of causal edges, empty when no tag-to-equipment mapping was supplied
   */
  public List<CausalTopologyModel.CausalEdge> getLastCausalEdges() {
    return lastCausalEdges;
  }

  /**
   * Runs the full root cause analysis.
   *
   * <p>
   * Steps:
   * </p>
   * <ol>
   * <li>Identify equipment and classify type</li>
   * <li>Generate candidate hypotheses based on symptom and equipment type</li>
   * <li>Adjust priors from reliability data (IOGP/SINTEF, CCPS, IEEE 493, Lees, etc.)</li>
   * <li>Collect evidence from historian and STID data</li>
   * <li>Update likelihood scores from evidence</li>
   * <li>Verify top hypotheses via process simulation (if enabled)</li>
   * <li>Compute final confidence scores and rank</li>
   * <li>Generate report</li>
   * </ol>
   *
   * @return root cause analysis report with ranked hypotheses
   * @throws IllegalStateException if symptom has not been set
   */
  public RootCauseReport analyze() {
    if (symptom == null) {
      throw new IllegalStateException("Symptom must be set before calling analyze()");
    }

    logger.info("Starting root cause analysis for '{}', symptom: {}", equipmentName, symptom.name());

    // Step 1: Find equipment
    ProcessEquipmentInterface equipment = findEquipment();
    if (equipment == null) {
      logger.warn("Equipment '{}' not found in process system", equipmentName);
    }

    // Step 2: Generate candidate hypotheses
    List<Hypothesis> hypotheses = generator.generate(equipment, symptom);
    logger.info("Generated {} candidate hypotheses", hypotheses.size());

    // Step 4 & 5: Collect evidence and update likelihood
    EvidenceCollector collector = new EvidenceCollector();
    collector.setHistorianData(historianData, timestamps);
    collector.setStidData(stidData);
    for (Map.Entry<String, double[]> entry : designLimits.entrySet()) {
      collector.setDesignLimit(entry.getKey(), entry.getValue()[0], entry.getValue()[1]);
    }

    int totalDataPoints = 0;
    for (double[] vals : historianData.values()) {
      totalDataPoints += vals.length;
    }

    for (Hypothesis h : hypotheses) {
      List<Hypothesis.Evidence> evidence = collector.collectEvidence(h);
      if (autonomousEvidenceActive) {
        evidence.addAll(collector.collectAutonomousEvidence(h, lastAnomalies, lastCausalEdges));
      }
      for (Hypothesis.Evidence e : evidence) {
        h.addEvidence(e);
      }
      if (evidence.isEmpty()) {
        h.setLikelihoodEvaluation(0.5, Hypothesis.EvaluationStatus.UNKNOWN);
      } else {
        double likelihood = collector.calculateLikelihoodScore(evidence);
        h.setLikelihoodEvaluation(likelihood, Hypothesis.EvaluationStatus.EVALUATED);
      }
    }

    // Step 6: Simulation verification (if enabled and process available)
    if (simulationEnabled) {
      SimulationVerifier verifier = new SimulationVerifier(processSystem, equipmentName);
      verifier.setHistorianData(historianData);

      // Verify top candidates (limit to top 5 for performance)
      List<Hypothesis> sorted = new ArrayList<>(hypotheses);
      java.util.Collections.sort(sorted);
      int verifyCount = Math.min(5, sorted.size());
      for (int i = 0; i < verifyCount; i++) {
        verifier.verify(sorted.get(i));
      }
    }

    // Step 7: Bayesian normalization — normalize confidence scores to sum to 1.0
    normalizeConfidenceScores(hypotheses);

    // Step 8: Build report
    String eqType = equipment != null ? generator.classifyEquipment(equipment) : "unknown";
    RootCauseReport report = new RootCauseReport(equipmentName, eqType, symptom);
    if (diagnosisCase == null) {
      diagnosisCase = DiagnosisCase.fromHistorian(equipmentName, timestamps, historianData)
          .setProcessModel(processSystem.getClass().getName(), "runtime-instance");
      if (!stidData.isEmpty()) {
        diagnosisCase.addDataSource("STID", "in-memory design metadata");
      }
      if (!designLimits.isEmpty()) {
        diagnosisCase.addDataSource("designLimits", "configured analysis limits");
      }
    }
    report.setDiagnosisCase(diagnosisCase);
    report.setHypotheses(hypotheses);
    report.setDataPointsAnalyzed(totalDataPoints);
    report.setParametersAnalyzed(historianData.size());

    // Generate summary
    Hypothesis top = report.getTopHypothesis();
    String summary;
    if (top != null) {
      summary = String.format(
          "Most likely root cause: %s (%.1f%% confidence, category: %s). "
              + "Analyzed %d parameters with %d data points. " + "%d hypotheses above 50%% confidence.",
          top.getName(), top.getConfidenceScore() * 100, top.getCategory().name(), historianData.size(),
          totalDataPoints, report.getHypothesesAboveThreshold(0.5).size());
    } else {
      summary = "No hypotheses could be generated for the given symptom and equipment.";
    }
    report.setAnalysisSummary(summary);

    logger.info("Root cause analysis complete. Top hypothesis: {}", top != null ? top.getName() : "none");

    return report;
  }

  /**
   * Normalizes confidence scores across all hypotheses using Bayesian posterior normalization.
   *
   * <p>
   * Each hypothesis's raw score (prior x likelihood x verification) is divided by the sum of all raw scores, producing
   * posterior probabilities that sum to 1.0. This prevents hypotheses from having inflated or deflated absolute
   * confidence scores.
   * </p>
   *
   * @param hypotheses list of hypotheses to normalize
   */
  private void normalizeConfidenceScores(List<Hypothesis> hypotheses) {
    if (hypotheses.isEmpty()) {
      return;
    }

    // Ensure all confidence scores are up to date
    for (Hypothesis h : hypotheses) {
      h.updateConfidence();
    }

    double totalScore = 0.0;
    for (Hypothesis h : hypotheses) {
      totalScore += h.getConfidenceScore();
    }

    if (totalScore > 1e-12) {
      for (Hypothesis h : hypotheses) {
        h.setConfidenceScore(h.getConfidenceScore() / totalScore);
      }
    }
  }

  /**
   * Finds the target equipment in the process system.
   *
   * @return equipment interface, or null if not found
   */
  private ProcessEquipmentInterface findEquipment() {
    for (ProcessEquipmentInterface eq : processSystem.getUnitOperations()) {
      if (eq.getName().equals(equipmentName)) {
        return eq;
      }
    }
    return null;
  }
}
