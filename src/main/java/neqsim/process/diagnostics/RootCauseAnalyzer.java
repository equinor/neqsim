package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.processmodel.ProcessSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main orchestrator for root cause analysis of equipment issues.
 *
 * <p>
 * Integrates process simulation, multi-source reliability data, historian time-series, and STID
 * design conditions to produce a ranked list of failure hypotheses. The analysis follows a
 * Bayesian- inspired methodology:
 * </p>
 *
 * <ol>
 * <li><b>Prior</b> — OREDA failure mode frequencies set initial hypothesis probabilities</li>
 * <li><b>Likelihood</b> — historian data evidence updates hypothesis scores</li>
 * <li><b>Verification</b> — process simulation confirms if a hypothesized failure reproduces
 * observed symptoms</li>
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
 * System.out.println(report.toTextReport());
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
    designLimits.put(parameter, new double[] {lowLimit, highLimit});
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

    logger.info("Starting root cause analysis for '{}', symptom: {}", equipmentName,
        symptom.name());

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
      for (Hypothesis.Evidence e : evidence) {
        h.addEvidence(e);
      }
      double likelihood = collector.calculateLikelihoodScore(evidence);
      h.setLikelihoodScore(likelihood);
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
    report.setHypotheses(hypotheses);
    report.setDataPointsAnalyzed(totalDataPoints);
    report.setParametersAnalyzed(historianData.size());

    // Generate summary
    Hypothesis top = report.getTopHypothesis();
    String summary;
    if (top != null) {
      summary = String.format("Most likely root cause: %s (%.1f%% confidence, category: %s). "
          + "Analyzed %d parameters with %d data points. " + "%d hypotheses above 50%% confidence.",
          top.getName(), top.getConfidenceScore() * 100, top.getCategory().name(),
          historianData.size(), totalDataPoints, report.getHypothesesAboveThreshold(0.5).size());
    } else {
      summary = "No hypotheses could be generated for the given symptom and equipment.";
    }
    report.setAnalysisSummary(summary);

    logger.info("Root cause analysis complete. Top hypothesis: {}",
        top != null ? top.getName() : "none");

    return report;
  }

  /**
   * Normalizes confidence scores across all hypotheses using Bayesian posterior normalization.
   *
   * <p>
   * Each hypothesis's raw score (prior x likelihood x verification) is divided by the sum of all
   * raw scores, producing posterior probabilities that sum to 1.0. This prevents hypotheses from
   * having inflated or deflated absolute confidence scores.
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
