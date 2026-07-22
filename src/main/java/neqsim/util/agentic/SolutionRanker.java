package neqsim.util.agentic;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Weighted multi-criteria ranking of candidate engineering solutions.
 *
 * <p>
 * After a diagnostic or optimization study identifies a problem, a senior-engineer deliverable proposes and <i>ranks
 * several candidate solutions</i> rather than a single fix. This class turns a small set of candidate options (for
 * example: do nothing, change operating setpoints, modify equipment, replace equipment) and a set of weighted decision
 * criteria (effectiveness, cost, risk, lead time, feasibility) into a transparent, reproducible ranked decision matrix.
 * </p>
 *
 * <p>
 * Each criterion has a weight and a direction: benefit criteria (higher raw value is better, for example effectiveness)
 * or cost criteria (lower raw value is better, for example cost, risk, lead time). Raw scores are min-max normalized
 * within each criterion across the candidate solutions, the direction is applied so that 1.0 is always best, and the
 * weighted average across criteria gives an overall score in the range [0, 1]. Solutions are ranked by overall score
 * (descending).
 * </p>
 *
 * <h2>Usage</h2>
 *
 * <pre>
 * {@code
 * SolutionRanker r = new SolutionRanker();
 * r.addCriterion("effectiveness", 0.4, true);
 * r.addCriterion("cost", 0.3, false);
 * r.addCriterion("risk", 0.2, false);
 * r.addCriterion("lead_time", 0.1, false);
 * r.addSolution("S1", "Increase suction temperature setpoint");
 * r.setScore("S1", "effectiveness", 0.7);
 * r.setScore("S1", "cost", 0.1);
 * r.setScore("S1", "risk", 0.2);
 * r.setScore("S1", "lead_time", 0.1);
 * String json = r.toJson();
 * }
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class SolutionRanker implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Schema version emitted in {@link #toJson()}. */
  public static final String SCHEMA_VERSION = "1.0";

  private final Map<String, Criterion> criteria = new LinkedHashMap<String, Criterion>();
  private final Map<String, String> solutions = new LinkedHashMap<String, String>();
  private final Map<String, Map<String, Double>> scores = new LinkedHashMap<String, Map<String, Double>>();

  /** A decision criterion with a weight and a direction. */
  private static final class Criterion implements Serializable {
    private static final long serialVersionUID = 1L;
    private final double weight;
    private final boolean benefit;

    /**
     * Create a criterion.
     *
     * @param weight the non-negative weight
     * @param benefit {@code true} if a higher raw value is better, {@code false} if a lower raw value is better
     */
    private Criterion(double weight, boolean benefit) {
      this.weight = weight;
      this.benefit = benefit;
    }
  }

  /** Ranked result for one candidate solution. */
  public static final class RankedSolution implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id;
    private final String description;
    private final double overall;
    private final int rank;
    private final Map<String, Double> normalized;
    private final List<String> missingCriteria;

    /**
     * Create a ranked-solution result.
     *
     * @param id solution id
     * @param description solution description
     * @param overall the weighted overall score in [0, 1]
     * @param rank the 1-based rank (1 is best)
     * @param normalized the per-criterion normalized (direction-applied) scores
     * @param missingCriteria criteria for which the solution had no raw score
     */
    private RankedSolution(String id, String description, double overall, int rank, Map<String, Double> normalized,
        List<String> missingCriteria) {
      this.id = id;
      this.description = description;
      this.overall = overall;
      this.rank = rank;
      this.normalized = normalized;
      this.missingCriteria = missingCriteria;
    }

    /**
     * Get the solution id.
     *
     * @return the solution id
     */
    public String getId() {
      return id;
    }

    /**
     * Get the weighted overall score in [0, 1].
     *
     * @return the overall score
     */
    public double getOverall() {
      return overall;
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
   * Register a weighted decision criterion.
   *
   * @param name a unique criterion name (for example {@code "cost"})
   * @param weight the non-negative weight (relative importance)
   * @param benefit {@code true} if a higher raw value is better, {@code false} if a lower raw value is better (cost,
   * risk, lead time)
   * @throws IllegalArgumentException if the name is blank, already registered, or the weight is negative or NaN
   */
  public void addCriterion(String name, double weight, boolean benefit) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("criterion name must be provided");
    }
    if (criteria.containsKey(name)) {
      throw new IllegalArgumentException("criterion already registered: " + name);
    }
    if (weight < 0.0 || Double.isNaN(weight)) {
      throw new IllegalArgumentException("weight must be non-negative");
    }
    criteria.put(name, new Criterion(weight, benefit));
  }

  /**
   * Register a candidate solution.
   *
   * @param id a unique solution id (for example {@code "S1"})
   * @param description a human-readable description of the option
   * @throws IllegalArgumentException if the id is blank or already registered
   */
  public void addSolution(String id, String description) {
    if (id == null || id.trim().isEmpty()) {
      throw new IllegalArgumentException("solution id must be provided");
    }
    if (solutions.containsKey(id)) {
      throw new IllegalArgumentException("solution already registered: " + id);
    }
    solutions.put(id, description == null ? "" : description);
    scores.put(id, new LinkedHashMap<String, Double>());
  }

  /**
   * Set a candidate solution's raw score for a criterion.
   *
   * @param solutionId the solution id
   * @param criterion the criterion name
   * @param rawScore the raw score (any finite scale; normalized internally across solutions)
   * @throws IllegalArgumentException if the solution or criterion is unknown, or the score is not finite
   */
  public void setScore(String solutionId, String criterion, double rawScore) {
    if (!solutions.containsKey(solutionId)) {
      throw new IllegalArgumentException("unknown solution id: " + solutionId);
    }
    if (!criteria.containsKey(criterion)) {
      throw new IllegalArgumentException("unknown criterion: " + criterion);
    }
    if (Double.isNaN(rawScore) || Double.isInfinite(rawScore)) {
      throw new IllegalArgumentException("rawScore must be finite");
    }
    scores.get(solutionId).put(criterion, rawScore);
  }

  /**
   * Rank all candidate solutions by their weighted overall score.
   *
   * @return the ranked solutions (best first); empty if no solutions were registered
   * @throws IllegalStateException if no criteria were registered or the total weight is zero
   */
  public List<RankedSolution> rank() {
    if (criteria.isEmpty()) {
      throw new IllegalStateException("at least one criterion must be registered");
    }
    double totalWeight = 0.0;
    for (Criterion criterion : criteria.values()) {
      totalWeight += criterion.weight;
    }
    if (totalWeight <= 0.0) {
      throw new IllegalStateException("total criterion weight must be positive");
    }

    Map<String, Double> minByCriterion = new LinkedHashMap<String, Double>();
    Map<String, Double> maxByCriterion = new LinkedHashMap<String, Double>();
    for (String criterion : criteria.keySet()) {
      double min = Double.POSITIVE_INFINITY;
      double max = Double.NEGATIVE_INFINITY;
      for (String solutionId : solutions.keySet()) {
        Double raw = scores.get(solutionId).get(criterion);
        if (raw != null) {
          min = Math.min(min, raw);
          max = Math.max(max, raw);
        }
      }
      minByCriterion.put(criterion, min);
      maxByCriterion.put(criterion, max);
    }

    List<RankedSolution> unranked = new ArrayList<RankedSolution>();
    for (Map.Entry<String, String> entry : solutions.entrySet()) {
      String id = entry.getKey();
      Map<String, Double> normalized = new LinkedHashMap<String, Double>();
      List<String> missing = new ArrayList<String>();
      double weighted = 0.0;
      for (Map.Entry<String, Criterion> criterionEntry : criteria.entrySet()) {
        String name = criterionEntry.getKey();
        Criterion criterion = criterionEntry.getValue();
        Double raw = scores.get(id).get(name);
        double norm;
        if (raw == null) {
          norm = 0.0;
          missing.add(name);
        } else {
          norm = normalize(raw, minByCriterion.get(name), maxByCriterion.get(name), criterion.benefit);
        }
        normalized.put(name, round(norm));
        weighted += norm * criterion.weight;
      }
      double overall = weighted / totalWeight;
      unranked.add(new RankedSolution(id, entry.getValue(), round(overall), 0, normalized, missing));
    }

    sortByOverall(unranked);
    List<RankedSolution> ranked = new ArrayList<RankedSolution>();
    int position = 1;
    for (RankedSolution item : unranked) {
      ranked.add(
          new RankedSolution(item.id, item.description, item.overall, position, item.normalized, item.missingCriteria));
      position++;
    }
    return ranked;
  }

  /**
   * Min-max normalize a raw score within a criterion and apply its direction.
   *
   * @param raw the raw score
   * @param min the minimum raw score across solutions for this criterion
   * @param max the maximum raw score across solutions for this criterion
   * @param benefit {@code true} if a higher raw value is better
   * @return the normalized score in [0, 1] where 1.0 is best
   */
  private double normalize(double raw, double min, double max, boolean benefit) {
    if (max <= min) {
      return 0.5;
    }
    double fraction = (raw - min) / (max - min);
    return benefit ? fraction : 1.0 - fraction;
  }

  /**
   * Sort ranked solutions in place by overall score (descending).
   *
   * @param list the list to sort (modified in place)
   */
  private void sortByOverall(List<RankedSolution> list) {
    for (int i = 1; i < list.size(); i++) {
      RankedSolution key = list.get(i);
      int j = i - 1;
      while (j >= 0 && list.get(j).overall < key.overall) {
        list.set(j + 1, list.get(j));
        j--;
      }
      list.set(j + 1, key);
    }
  }

  /**
   * Produce a schema-versioned JSON decision matrix suitable for a task {@code results.json}.
   *
   * @return the ranked decision matrix as a pretty-printed JSON string
   * @throws IllegalStateException if no criteria were registered or the total weight is zero
   */
  public String toJson() {
    List<RankedSolution> ranked = rank();
    JsonObject root = new JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);
    JsonArray criteriaArray = new JsonArray();
    for (Map.Entry<String, Criterion> entry : criteria.entrySet()) {
      JsonObject node = new JsonObject();
      node.addProperty("name", entry.getKey());
      node.addProperty("weight", round(entry.getValue().weight));
      node.addProperty("benefit", entry.getValue().benefit);
      criteriaArray.add(node);
    }
    root.add("criteria", criteriaArray);

    JsonArray solutionArray = new JsonArray();
    for (RankedSolution item : ranked) {
      JsonObject node = new JsonObject();
      node.addProperty("id", item.id);
      node.addProperty("description", item.description);
      node.addProperty("rank", item.rank);
      node.addProperty("overall", round(item.overall));
      JsonObject normalized = new JsonObject();
      for (Map.Entry<String, Double> scoreEntry : item.normalized.entrySet()) {
        normalized.addProperty(scoreEntry.getKey(), scoreEntry.getValue());
      }
      node.add("normalizedScores", normalized);
      JsonArray missing = new JsonArray();
      for (String name : item.missingCriteria) {
        missing.add(name);
      }
      node.add("missingCriteria", missing);
      solutionArray.add(node);
    }
    root.add("solutions", solutionArray);
    if (!ranked.isEmpty()) {
      root.addProperty("recommendedSolutionId", ranked.get(0).id);
    } else {
      root.add("recommendedSolutionId", null);
    }
    return new GsonBuilder().setPrettyPrinting().create().toJson(root);
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
