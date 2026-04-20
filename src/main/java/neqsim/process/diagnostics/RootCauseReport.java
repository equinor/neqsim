package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Report summarising the root cause analysis of a trip event.
 *
 * <p>
 * Contains the ranked hypothesis results, the failure propagation chain, the event timeline
 * summary, and actionable recommendations for restart.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class RootCauseReport implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private final TripEvent tripEvent;
  private final List<HypothesisResult> rankedHypotheses;
  private final FailurePropagationTracer.PropagationChain propagationChain;
  private final UnifiedEventTimeline timeline;
  private final ProcessStateSnapshot snapshot;

  /**
   * Constructs a root cause report.
   *
   * @param tripEvent the trip event being analysed
   * @param rankedHypotheses hypotheses sorted by confidence score (highest first)
   * @param propagationChain the failure propagation chain
   * @param timeline the unified event timeline
   * @param snapshot the process state snapshot
   */
  public RootCauseReport(TripEvent tripEvent, List<HypothesisResult> rankedHypotheses,
      FailurePropagationTracer.PropagationChain propagationChain, UnifiedEventTimeline timeline,
      ProcessStateSnapshot snapshot) {
    this.tripEvent = tripEvent;
    this.rankedHypotheses = new ArrayList<>(rankedHypotheses);
    this.propagationChain = propagationChain;
    this.timeline = timeline;
    this.snapshot = snapshot;
  }

  /**
   * Returns the trip event.
   *
   * @return trip event
   */
  public TripEvent getTripEvent() {
    return tripEvent;
  }

  /**
   * Returns all hypothesis results ranked by confidence (highest first).
   *
   * @return unmodifiable list of ranked hypotheses
   */
  public List<HypothesisResult> getRankedHypotheses() {
    return Collections.unmodifiableList(rankedHypotheses);
  }

  /**
   * Returns the most likely hypothesis, or null if no hypotheses were evaluated.
   *
   * @return the hypothesis with the highest confidence score
   */
  public HypothesisResult getMostLikelyHypothesis() {
    if (rankedHypotheses.isEmpty()) {
      return null;
    }
    return rankedHypotheses.get(0);
  }

  /**
   * Returns all hypotheses at or above the given confidence level.
   *
   * @param minConfidence minimum confidence level
   * @return list of matching hypotheses
   */
  public List<HypothesisResult> getHypothesesAbove(HypothesisResult.Confidence minConfidence) {
    List<HypothesisResult> result = new ArrayList<>();
    for (HypothesisResult hr : rankedHypotheses) {
      if (hr.getScore() >= minConfidence.getThreshold()) {
        result.add(hr);
      }
    }
    return result;
  }

  /**
   * Returns the failure propagation chain.
   *
   * @return propagation chain
   */
  public FailurePropagationTracer.PropagationChain getPropagationChain() {
    return propagationChain;
  }

  /**
   * Returns the event timeline.
   *
   * @return timeline
   */
  public UnifiedEventTimeline getTimeline() {
    return timeline;
  }

  /**
   * Returns the process state snapshot.
   *
   * @return snapshot
   */
  public ProcessStateSnapshot getSnapshot() {
    return snapshot;
  }

  /**
   * Generates a consolidated list of recommended actions from all likely+ hypotheses.
   *
   * @return list of unique recommended actions
   */
  public List<String> getRecommendedActions() {
    List<String> actions = new ArrayList<>();
    for (HypothesisResult hr : rankedHypotheses) {
      if (hr.getScore() >= HypothesisResult.Confidence.POSSIBLE.getThreshold()
          && hr.getRecommendedAction() != null && !hr.getRecommendedAction().isEmpty()
          && !actions.contains(hr.getRecommendedAction())) {
        actions.add(hr.getRecommendedAction());
      }
    }
    return actions;
  }

  /**
   * Serialises the report to JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    Map<String, Object> map = new LinkedHashMap<>();

    // Trip event summary
    Map<String, Object> tripMap = new LinkedHashMap<>();
    tripMap.put("eventId", tripEvent.getEventId());
    tripMap.put("timestamp", tripEvent.getTimestamp());
    tripMap.put("equipment", tripEvent.getInitiatingEquipment());
    tripMap.put("tripType", tripEvent.getTripType().name());
    tripMap.put("severity", tripEvent.getSeverity().name());
    tripMap.put("description", tripEvent.getDescription());
    map.put("tripEvent", tripMap);

    // Ranked hypotheses
    List<Map<String, Object>> hypothesisList = new ArrayList<>();
    for (HypothesisResult hr : rankedHypotheses) {
      Map<String, Object> hrMap = new LinkedHashMap<>();
      hrMap.put("hypothesis", hr.getHypothesisName());
      hrMap.put("confidence", hr.getConfidence().name());
      hrMap.put("score", hr.getScore());
      hrMap.put("evidence", hr.getEvidence());
      hrMap.put("recommendedAction", hr.getRecommendedAction());
      hypothesisList.add(hrMap);
    }
    map.put("rankedHypotheses", hypothesisList);

    // Propagation chain
    if (propagationChain != null) {
      map.put("propagationChain", propagationChain.getEquipmentNames());
    }

    // State deviations summary
    if (snapshot != null) {
      ProcessStateSnapshot.StateDiff diff = snapshot.diff();
      map.put("totalDeviations", diff.size());
      map.put("highDeviations",
          diff.getDeviationsBySignificance(ProcessStateSnapshot.DeviationSignificance.HIGH).size());
    }

    // Recommended actions
    map.put("recommendedActions", getRecommendedActions());

    return GSON.toJson(map);
  }

  /**
   * Generates a human-readable text summary of the report.
   *
   * @return multi-line text summary
   */
  public String toTextSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== ROOT CAUSE ANALYSIS REPORT ===\n\n");

    // Trip summary
    sb.append("Trip Event: ").append(tripEvent.getDescription()).append("\n");
    sb.append("Equipment: ").append(tripEvent.getInitiatingEquipment()).append("\n");
    sb.append("Time: ").append(String.format("%.2f s", tripEvent.getTimestamp())).append("\n");
    sb.append("Type: ").append(tripEvent.getTripType().getDisplayName()).append("\n\n");

    // Top hypotheses
    sb.append("--- Ranked Hypotheses ---\n");
    for (int i = 0; i < rankedHypotheses.size(); i++) {
      HypothesisResult hr = rankedHypotheses.get(i);
      sb.append(String.format("%d. %s [%s, %.0f%%]\n", i + 1, hr.getHypothesisName(),
          hr.getConfidence(), hr.getScore() * 100.0));
      for (String ev : hr.getEvidence()) {
        sb.append("   - ").append(ev).append("\n");
      }
    }

    // Propagation
    if (propagationChain != null && propagationChain.size() > 1) {
      sb.append("\n--- Failure Propagation ---\n");
      sb.append(propagationChain.toString()).append("\n");
    }

    // Actions
    List<String> actions = getRecommendedActions();
    if (!actions.isEmpty()) {
      sb.append("\n--- Recommended Actions ---\n");
      for (int i = 0; i < actions.size(); i++) {
        sb.append(String.format("%d. %s\n", i + 1, actions.get(i)));
      }
    }

    return sb.toString();
  }

  @Override
  public String toString() {
    HypothesisResult top = getMostLikelyHypothesis();
    return String.format("RootCauseReport{trip=%s, topHypothesis=%s}",
        tripEvent.getInitiatingEquipment(), top != null ? top.getHypothesisName() : "none");
  }
}
