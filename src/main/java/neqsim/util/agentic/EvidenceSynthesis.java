package neqsim.util.agentic;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Multi-source weight-of-evidence synthesis for agent-driven root-cause and diagnostic workflows.
 *
 * <p>
 * Engineering solvers (for example the PEPR solve-task workflow) gather evidence for competing hypotheses from many
 * independent source types — plant historian trends, SAP/maintenance history, STID design limits, TR2000 piping data,
 * published literature, reliability priors (OREDA), and NeqSim simulation results. Collecting that evidence is only
 * half the job: a defensible conclusion requires <i>combining</i> the evidence across sources and preferring hypotheses
 * that are corroborated by several independent source types over those supported by a single source.
 * </p>
 *
 * <p>
 * This class provides a small, deterministic engine that ranks hypotheses by a weight-of-evidence score and reports,
 * per hypothesis, how many <b>distinct</b> source types support it (multi-source corroboration). It is intentionally
 * simple and transparent so an agent can explain the reasoning: each evidence item carries a source type, a direction
 * (supporting or contradicting), and a strength in the range [0, 1]. The net score is the sum of supporting strengths
 * minus the sum of contradicting strengths.
 * </p>
 *
 * <h2>Confidence labels</h2>
 * <table>
 * <caption>Confidence label assigned to the ranked hypothesis</caption>
 * <tr>
 * <th>Label</th>
 * <th>Condition</th>
 * </tr>
 * <tr>
 * <td>UNSUPPORTED</td>
 * <td>net score not positive</td>
 * </tr>
 * <tr>
 * <td>DISPUTED</td>
 * <td>contradicting strength at least as large as supporting strength</td>
 * </tr>
 * <tr>
 * <td>WEAK</td>
 * <td>supported by a single source type</td>
 * </tr>
 * <tr>
 * <td>MODERATE</td>
 * <td>supported by two distinct source types</td>
 * </tr>
 * <tr>
 * <td>STRONG</td>
 * <td>supported by three or more distinct source types</td>
 * </tr>
 * </table>
 *
 * <h2>Usage</h2>
 *
 * <pre>
 * {@code
 * EvidenceSynthesis es = new EvidenceSynthesis();
 * es.addHypothesis("H1", "Elemental sulfur deposition at the compressor inlet");
 * es.addEvidence("H1", "historian", true, 0.8, "Rising dP trend across suction filter");
 * es.addEvidence("H1", "literature", true, 0.6, "S8 deposition at letdown per published study");
 * es.addEvidence("H1", "simulation", true, 0.7, "NeqSim predicts S8 saturation at inlet T,P");
 * String json = es.toJson();
 * }
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class EvidenceSynthesis implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Schema version emitted in {@link #toJson()}. */
  public static final String SCHEMA_VERSION = "1.0";

  private final Map<String, String> hypotheses = new LinkedHashMap<String, String>();
  private final List<Evidence> evidence = new ArrayList<Evidence>();

  /** Immutable record of one evidence item attached to a hypothesis. */
  private static final class Evidence implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String hypothesisId;
    private final String sourceType;
    private final boolean supporting;
    private final double strength;
    private final String note;

    /**
     * Create an evidence item.
     *
     * @param hypothesisId the hypothesis this evidence bears on
     * @param sourceType the source-type label (for example {@code "historian"})
     * @param supporting {@code true} if the evidence supports the hypothesis, {@code false} if it contradicts it
     * @param strength evidence strength in the range [0, 1]
     * @param note a short human-readable justification
     */
    private Evidence(String hypothesisId, String sourceType, boolean supporting, double strength, String note) {
      this.hypothesisId = hypothesisId;
      this.sourceType = sourceType;
      this.supporting = supporting;
      this.strength = strength;
      this.note = note;
    }
  }

  /** Ranked result for a single hypothesis after synthesis. */
  public static final class RankedHypothesis implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id;
    private final String description;
    private final double netScore;
    private final double supportPoints;
    private final double contradictPoints;
    private final List<String> supportingSources;
    private final List<String> contradictingSources;
    private final String confidence;
    private final int rank;

    /**
     * Create a ranked-hypothesis result.
     *
     * @param id hypothesis id
     * @param description hypothesis description
     * @param netScore supporting minus contradicting strength
     * @param supportPoints total supporting strength
     * @param contradictPoints total contradicting strength
     * @param supportingSources distinct source types that support the hypothesis
     * @param contradictingSources distinct source types that contradict the hypothesis
     * @param confidence the confidence label
     * @param rank 1-based rank (1 is best)
     */
    private RankedHypothesis(String id, String description, double netScore, double supportPoints,
        double contradictPoints, List<String> supportingSources, List<String> contradictingSources, String confidence,
        int rank) {
      this.id = id;
      this.description = description;
      this.netScore = netScore;
      this.supportPoints = supportPoints;
      this.contradictPoints = contradictPoints;
      this.supportingSources = supportingSources;
      this.contradictingSources = contradictingSources;
      this.confidence = confidence;
      this.rank = rank;
    }

    /**
     * Get the hypothesis id.
     *
     * @return the hypothesis id
     */
    public String getId() {
      return id;
    }

    /**
     * Get the net weight-of-evidence score (supporting minus contradicting strength).
     *
     * @return the net score
     */
    public double getNetScore() {
      return netScore;
    }

    /**
     * Get the number of distinct source types that support this hypothesis.
     *
     * @return the supporting distinct source-type count
     */
    public int getSupportingSourceCount() {
      return supportingSources.size();
    }

    /**
     * Whether the hypothesis is corroborated by at least two distinct source types.
     *
     * @return {@code true} when at least two distinct source types support it
     */
    public boolean isCorroborated() {
      return supportingSources.size() >= 2;
    }

    /**
     * Get the confidence label.
     *
     * @return the confidence label
     */
    public String getConfidence() {
      return confidence;
    }

    /**
     * Get the 1-based rank (1 is best).
     *
     * @return the rank
     */
    public int getRank() {
      return rank;
    }
  }

  /**
   * Register a competing hypothesis.
   *
   * @param id a short unique id (for example {@code "H1"})
   * @param description a human-readable description of the hypothesis
   * @throws IllegalArgumentException if the id is null/blank or already registered
   */
  public void addHypothesis(String id, String description) {
    if (id == null || id.trim().isEmpty()) {
      throw new IllegalArgumentException("hypothesis id must be provided");
    }
    if (hypotheses.containsKey(id)) {
      throw new IllegalArgumentException("hypothesis id already registered: " + id);
    }
    hypotheses.put(id, description == null ? "" : description);
  }

  /**
   * Attach one evidence item to a previously registered hypothesis.
   *
   * @param hypothesisId the target hypothesis id
   * @param sourceType the source-type label (for example {@code "historian"}, {@code "maintenance"}, {@code "stid"},
   * {@code "literature"}, {@code "reliability_prior"}, {@code "simulation"})
   * @param supporting {@code true} if the evidence supports the hypothesis, {@code false} if it contradicts it
   * @param strength evidence strength in the range [0, 1]
   * @param note a short human-readable justification (may be null)
   * @throws IllegalArgumentException if the hypothesis is unknown, the source type is blank, or the strength is outside
   * [0, 1]
   */
  public void addEvidence(String hypothesisId, String sourceType, boolean supporting, double strength, String note) {
    if (!hypotheses.containsKey(hypothesisId)) {
      throw new IllegalArgumentException("unknown hypothesis id: " + hypothesisId);
    }
    if (sourceType == null || sourceType.trim().isEmpty()) {
      throw new IllegalArgumentException("sourceType must be provided");
    }
    if (strength < 0.0 || strength > 1.0 || Double.isNaN(strength)) {
      throw new IllegalArgumentException("strength must be within [0, 1]");
    }
    evidence.add(
        new Evidence(hypothesisId, sourceType.trim().toLowerCase(), supporting, strength, note == null ? "" : note));
  }

  /**
   * Rank all registered hypotheses by weight of evidence.
   *
   * <p>
   * Hypotheses are ordered by net score (descending); ties are broken by the number of distinct supporting source types
   * (descending), then by registration order.
   * </p>
   *
   * @return the ranked hypotheses (best first); empty if no hypotheses were registered
   */
  public List<RankedHypothesis> rank() {
    List<RankedHypothesis> unranked = new ArrayList<RankedHypothesis>();
    for (Map.Entry<String, String> entry : hypotheses.entrySet()) {
      String id = entry.getKey();
      double supportPoints = 0.0;
      double contradictPoints = 0.0;
      Set<String> supportingSources = new LinkedHashSet<String>();
      Set<String> contradictingSources = new LinkedHashSet<String>();
      for (Evidence item : evidence) {
        if (!item.hypothesisId.equals(id)) {
          continue;
        }
        if (item.supporting) {
          supportPoints += item.strength;
          supportingSources.add(item.sourceType);
        } else {
          contradictPoints += item.strength;
          contradictingSources.add(item.sourceType);
        }
      }
      double netScore = supportPoints - contradictPoints;
      String confidence = classify(netScore, supportPoints, contradictPoints, supportingSources.size());
      unranked.add(new RankedHypothesis(id, entry.getValue(), netScore, supportPoints, contradictPoints,
          new ArrayList<String>(supportingSources), new ArrayList<String>(contradictingSources), confidence, 0));
    }
    sortByEvidence(unranked);
    List<RankedHypothesis> ranked = new ArrayList<RankedHypothesis>();
    int position = 1;
    for (RankedHypothesis item : unranked) {
      ranked.add(new RankedHypothesis(item.id, item.description, item.netScore, item.supportPoints,
          item.contradictPoints, item.supportingSources, item.contradictingSources, item.confidence, position));
      position++;
    }
    return ranked;
  }

  /**
   * Sort ranked hypotheses in place by net score, then supporting source count.
   *
   * @param list the list to sort (modified in place)
   */
  private void sortByEvidence(List<RankedHypothesis> list) {
    for (int i = 1; i < list.size(); i++) {
      RankedHypothesis key = list.get(i);
      int j = i - 1;
      while (j >= 0 && isWorse(list.get(j), key)) {
        list.set(j + 1, list.get(j));
        j--;
      }
      list.set(j + 1, key);
    }
  }

  /**
   * Determine whether hypothesis {@code a} ranks worse than {@code b}.
   *
   * @param a the first hypothesis
   * @param b the second hypothesis
   * @return {@code true} if {@code a} should sort after {@code b}
   */
  private boolean isWorse(RankedHypothesis a, RankedHypothesis b) {
    if (a.netScore < b.netScore) {
      return true;
    }
    if (a.netScore > b.netScore) {
      return false;
    }
    return a.supportingSources.size() < b.supportingSources.size();
  }

  /**
   * Assign a confidence label from the aggregated evidence for one hypothesis.
   *
   * @param netScore supporting minus contradicting strength
   * @param supportPoints total supporting strength
   * @param contradictPoints total contradicting strength
   * @param supportingSourceCount number of distinct supporting source types
   * @return the confidence label
   */
  private String classify(double netScore, double supportPoints, double contradictPoints, int supportingSourceCount) {
    if (netScore <= 0.0) {
      return "UNSUPPORTED";
    }
    if (contradictPoints >= supportPoints) {
      return "DISPUTED";
    }
    if (supportingSourceCount >= 3) {
      return "STRONG";
    }
    if (supportingSourceCount == 2) {
      return "MODERATE";
    }
    return "WEAK";
  }

  /**
   * Produce a schema-versioned JSON synthesis suitable for a task {@code results.json}.
   *
   * <p>
   * The object contains the ranked hypotheses (with per-source breakdown and confidence), the id of the top hypothesis,
   * and a {@code singleSourceWarning} flag that is {@code true} when the top hypothesis is supported by only one source
   * type — a prompt to gather corroborating evidence from another source before concluding.
   * </p>
   *
   * @return the synthesis as a pretty-printed JSON string
   */
  public String toJson() {
    List<RankedHypothesis> ranked = rank();
    JsonObject root = new JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);
    JsonArray array = new JsonArray();
    for (RankedHypothesis item : ranked) {
      JsonObject node = new JsonObject();
      node.addProperty("id", item.id);
      node.addProperty("description", item.description);
      node.addProperty("rank", item.rank);
      node.addProperty("netScore", round(item.netScore));
      node.addProperty("supportPoints", round(item.supportPoints));
      node.addProperty("contradictPoints", round(item.contradictPoints));
      node.addProperty("supportingSourceCount", item.supportingSources.size());
      node.addProperty("corroborated", item.isCorroborated());
      node.addProperty("confidence", item.confidence);
      node.add("supportingSources", toJsonArray(item.supportingSources));
      node.add("contradictingSources", toJsonArray(item.contradictingSources));
      array.add(node);
    }
    root.add("hypotheses", array);
    if (!ranked.isEmpty()) {
      RankedHypothesis top = ranked.get(0);
      root.addProperty("topHypothesisId", top.id);
      root.addProperty("singleSourceWarning", top.netScore > 0.0 && top.supportingSources.size() < 2);
    } else {
      root.add("topHypothesisId", null);
      root.addProperty("singleSourceWarning", false);
    }
    return new GsonBuilder().setPrettyPrinting().create().toJson(root);
  }

  /**
   * Convert a list of strings to a JSON array.
   *
   * @param values the values to convert
   * @return a JSON array of the values
   */
  private JsonArray toJsonArray(List<String> values) {
    JsonArray array = new JsonArray();
    for (String value : values) {
      array.add(value);
    }
    return array;
  }

  /**
   * Round a value to three decimal places for stable JSON output.
   *
   * @param value the value to round
   * @return the rounded value
   */
  private double round(double value) {
    return Math.round(value * 1000.0) / 1000.0;
  }
}
