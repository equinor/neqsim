package neqsim.process.diagnostics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the result of a root cause analysis for an equipment issue.
 *
 * <p>
 * Contains a ranked list of hypotheses with evidence and confidence scores, plus metadata about the analysis (equipment
 * name, symptom, timestamp). Output can be rendered as JSON or plain text.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class RootCauseReport implements Serializable {

  private static final long serialVersionUID = 1000L;

  /** Equipment name that was diagnosed. */
  private String equipmentName;

  /** Equipment type classification. */
  private String equipmentType;

  /** Reported symptom. */
  private Symptom symptom;

  /** Ranked hypotheses (highest confidence first). */
  private List<Hypothesis> rankedHypotheses;

  /** Analysis timestamp. */
  private long analysisTimestamp;

  /** Summary of analysis. */
  private String analysisSummary;

  /** Number of historian data points analyzed. */
  private int dataPointsAnalyzed;

  /** Number of parameters analyzed. */
  private int parametersAnalyzed;

  /** Gson serializer for stable pretty-printed JSON output. */
  private static final transient Gson GSON = new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues()
      .create();

  /**
   * Creates a root cause report.
   *
   * @param equipmentName equipment name
   * @param equipmentType equipment type classification
   * @param symptom reported symptom
   */
  public RootCauseReport(String equipmentName, String equipmentType, Symptom symptom) {
    this.equipmentName = equipmentName;
    this.equipmentType = equipmentType;
    this.symptom = symptom;
    this.rankedHypotheses = new ArrayList<>();
    this.analysisTimestamp = System.currentTimeMillis();
  }

  /**
   * Sets the ranked hypotheses.
   *
   * @param hypotheses list of hypotheses (will be sorted by confidence descending)
   */
  public void setHypotheses(List<Hypothesis> hypotheses) {
    this.rankedHypotheses = new ArrayList<>(hypotheses);
    Collections.sort(this.rankedHypotheses);
  }

  /**
   * Sets the analysis summary.
   *
   * @param summary analysis summary text
   */
  public void setAnalysisSummary(String summary) {
    this.analysisSummary = summary;
  }

  /**
   * Returns the count of historian data points analyzed.
   *
   * @return number of data points
   */
  public int getDataPointsAnalyzed() {
    return dataPointsAnalyzed;
  }

  /**
   * Sets the count of historian data points analyzed.
   *
   * @param count number of data points
   */
  public void setDataPointsAnalyzed(int count) {
    this.dataPointsAnalyzed = count;
  }

  /**
   * Returns the count of parameters analyzed.
   *
   * @return number of parameters
   */
  public int getParametersAnalyzed() {
    return parametersAnalyzed;
  }

  /**
   * Sets the count of parameters analyzed.
   *
   * @param count number of parameters
   */
  public void setParametersAnalyzed(int count) {
    this.parametersAnalyzed = count;
  }

  /**
   * Returns the most likely root cause (highest confidence hypothesis).
   *
   * @return top hypothesis, or null if no hypotheses
   */
  public Hypothesis getTopHypothesis() {
    return rankedHypotheses.isEmpty() ? null : rankedHypotheses.get(0);
  }

  /**
   * Returns all ranked hypotheses.
   *
   * @return unmodifiable list of hypotheses sorted by confidence
   */
  public List<Hypothesis> getRankedHypotheses() {
    return Collections.unmodifiableList(rankedHypotheses);
  }

  /**
   * Returns the equipment name.
   *
   * @return equipment name
   */
  public String getEquipmentName() {
    return equipmentName;
  }

  /**
   * Returns the equipment type.
   *
   * @return equipment type
   */
  public String getEquipmentType() {
    return equipmentType;
  }

  /**
   * Returns the symptom.
   *
   * @return symptom
   */
  public Symptom getSymptom() {
    return symptom;
  }

  /**
   * Returns the analysis timestamp.
   *
   * @return timestamp in milliseconds since epoch
   */
  public long getAnalysisTimestamp() {
    return analysisTimestamp;
  }

  /**
   * Returns hypotheses above a confidence threshold.
   *
   * @param minConfidence minimum confidence score (0-1)
   * @return filtered list of hypotheses
   */
  public List<Hypothesis> getHypothesesAboveThreshold(double minConfidence) {
    List<Hypothesis> filtered = new ArrayList<>();
    for (Hypothesis h : rankedHypotheses) {
      if (h.getConfidenceScore() >= minConfidence) {
        filtered.add(h);
      }
    }
    return filtered;
  }

  /**
   * Converts the report to a JSON string.
   *
   * <p>
   * The JSON contains the full report metadata and all ranked hypotheses with evidence.
   * </p>
   *
   * @return JSON string representation of the report
   */
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("equipment", equipmentName);
    root.addProperty("equipmentType", equipmentType);
    root.addProperty("symptom", symptom.name());
    root.addProperty("timestamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date(analysisTimestamp)));
    root.addProperty("dataPointsAnalyzed", dataPointsAnalyzed);
    root.addProperty("parametersAnalyzed", parametersAnalyzed);

    if (analysisSummary != null) {
      root.addProperty("summary", analysisSummary);
    }

    JsonArray hypothesesArray = new JsonArray();
    for (int i = 0; i < rankedHypotheses.size(); i++) {
      Hypothesis hypothesis = rankedHypotheses.get(i);
      JsonObject hypothesisJson = new JsonObject();

      hypothesisJson.addProperty("rank", i + 1);
      hypothesisJson.addProperty("name", hypothesis.getName());
      hypothesisJson.addProperty("category", hypothesis.getCategory().name());
      hypothesisJson.addProperty("confidenceScore", hypothesis.getConfidenceScore());
      hypothesisJson.addProperty("priorProbability", hypothesis.getPriorProbability());
      hypothesisJson.addProperty("likelihoodScore", hypothesis.getLikelihoodScore());
      hypothesisJson.addProperty("verificationScore", hypothesis.getVerificationScore());

      if (hypothesis.getDescription() != null) {
        hypothesisJson.addProperty("description", hypothesis.getDescription());
      }
      if (hypothesis.getSimulationSummary() != null) {
        hypothesisJson.addProperty("simulationSummary", hypothesis.getSimulationSummary());
      }

      JsonArray evidenceArray = new JsonArray();
      List<Hypothesis.Evidence> evidenceList = hypothesis.getEvidenceList();
      for (int j = 0; j < evidenceList.size(); j++) {
        Hypothesis.Evidence evidence = evidenceList.get(j);
        JsonObject evidenceJson = new JsonObject();
        evidenceJson.addProperty("parameter", evidence.getParameter());
        evidenceJson.addProperty("observation", evidence.getObservation());
        evidenceJson.addProperty("strength", evidence.getStrength().name());
        evidenceJson.addProperty("source", evidence.getSource());
        evidenceJson.addProperty("supporting", evidence.isSupporting());
        evidenceJson.addProperty("weight", evidence.getWeight());
        evidenceJson.addProperty("sourceReference", evidence.getSourceReference());
        evidenceArray.add(evidenceJson);
      }
      hypothesisJson.add("evidence", evidenceArray);

      JsonArray actionsArray = new JsonArray();
      List<String> actions = hypothesis.getRecommendedActions();
      for (int j = 0; j < actions.size(); j++) {
        actionsArray.add(actions.get(j));
      }
      hypothesisJson.add("recommendedActions", actionsArray);

      hypothesesArray.add(hypothesisJson);
    }

    root.add("hypotheses", hypothesesArray);
    return GSON.toJson(root);
  }

  /**
   * Generates a plain text report.
   *
   * @return formatted text report
   */
  public String toTextReport() {
    StringBuilder sb = new StringBuilder();
    String line = "========================================================================";
    String subline = "------------------------------------------------------------------------";

    sb.append(line).append("\n");
    sb.append("ROOT CAUSE ANALYSIS REPORT\n");
    sb.append(line).append("\n\n");

    sb.append("Equipment:    ").append(equipmentName).append("\n");
    sb.append("Type:         ").append(equipmentType).append("\n");
    sb.append("Symptom:      ").append(symptom.name()).append(" - ").append(symptom.getDescription()).append("\n");
    sb.append("Timestamp:    ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(analysisTimestamp)))
        .append("\n");
    sb.append("Data points:  ").append(dataPointsAnalyzed).append("\n");
    sb.append("Parameters:   ").append(parametersAnalyzed).append("\n\n");

    if (analysisSummary != null) {
      sb.append("Summary: ").append(analysisSummary).append("\n\n");
    }

    sb.append(subline).append("\n");
    sb.append("RANKED HYPOTHESES\n");
    sb.append(subline).append("\n\n");

    for (int i = 0; i < rankedHypotheses.size(); i++) {
      Hypothesis h = rankedHypotheses.get(i);
      sb.append(String.format("#%d  %s  (Confidence: %.1f%%)\n", i + 1, h.getName(), h.getConfidenceScore() * 100));
      sb.append(String.format("    Category: %s\n", h.getCategory().name()));
      sb.append(String.format("    Prior: %.3f | Likelihood: %.3f | Verification: %.3f\n", h.getPriorProbability(),
          h.getLikelihoodScore(), h.getVerificationScore()));

      if (h.getDescription() != null) {
        sb.append("    Description: ").append(h.getDescription()).append("\n");
      }

      List<Hypothesis.Evidence> evList = h.getEvidenceList();
      if (!evList.isEmpty()) {
        sb.append("    Evidence:\n");
        for (Hypothesis.Evidence e : evList) {
          String direction = e.isSupporting() ? "supports" : "contradicts";
          sb.append(String.format("      [%s/%s w=%.1f] %s: %s (%s)\n", e.getStrength().name(), direction,
              e.getWeight(), e.getParameter(), e.getObservation(), e.getSource()));
        }
      }

      if (h.getSimulationSummary() != null) {
        sb.append("    Simulation: ").append(h.getSimulationSummary()).append("\n");
      }

      List<String> actions = h.getRecommendedActions();
      if (!actions.isEmpty()) {
        sb.append("    Recommended Actions:\n");
        for (int j = 0; j < actions.size(); j++) {
          sb.append(String.format("      %d. %s\n", j + 1, actions.get(j)));
        }
      }

      sb.append("\n");
    }

    sb.append(line).append("\n");
    return sb.toString();
  }

  /**
   * Returns a results map suitable for inclusion in a task results.json.
   *
   * @return map with report data
   */
  public Map<String, Object> toResultsMap() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("equipment", equipmentName);
    result.put("equipmentType", equipmentType);
    result.put("symptom", symptom.name());
    result.put("dataPointsAnalyzed", dataPointsAnalyzed);
    result.put("parametersAnalyzed", parametersAnalyzed);

    Hypothesis top = getTopHypothesis();
    if (top != null) {
      Map<String, Object> topResult = new LinkedHashMap<>();
      topResult.put("name", top.getName());
      topResult.put("category", top.getCategory().name());
      topResult.put("confidence", top.getConfidenceScore());
      topResult.put("confidenceScore", top.getConfidenceScore());
      topResult.put("evidenceCount", top.getEvidenceList().size());
      topResult.put("contradictoryEvidenceCount", countContradictoryEvidence(top));
      result.put("topHypothesis", topResult);
    }

    result.put("totalHypotheses", rankedHypotheses.size());
    result.put("hypothesesAbove50pct", getHypothesesAboveThreshold(0.5).size());

    return result;
  }

  /**
   * Counts contradictory evidence for a hypothesis.
   *
   * @param hypothesis hypothesis to inspect
   * @return number of contradictory evidence items
   */
  private int countContradictoryEvidence(Hypothesis hypothesis) {
    int count = 0;
    for (Hypothesis.Evidence evidence : hypothesis.getEvidenceList()) {
      if (!evidence.isSupporting() || evidence.getStrength() == Hypothesis.EvidenceStrength.CONTRADICTORY) {
        count++;
      }
    }
    return count;
  }

}
