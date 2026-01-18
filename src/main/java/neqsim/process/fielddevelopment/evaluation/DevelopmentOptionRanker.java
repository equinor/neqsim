package neqsim.process.fielddevelopment.evaluation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Decision support tool for ranking and scoring development options.
 *
 * <p>
 * Provides multi-criteria decision analysis (MCDA) for comparing field development alternatives.
 * Supports weighted scoring across technical, economic, environmental, and strategic criteria.
 * </p>
 *
 * <h2>Scoring Dimensions</h2>
 * <ul>
 * <li><b>Economic</b>: NPV, IRR, payback, capital efficiency</li>
 * <li><b>Technical</b>: Complexity, technology risk, reservoir uncertainty</li>
 * <li><b>Environmental</b>: CO₂ intensity, emissions, environmental impact</li>
 * <li><b>Strategic</b>: Synergies, optionality, infrastructure value</li>
 * <li><b>Risk</b>: HSE risk, execution risk, commercial risk</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * 
 * <pre>{@code
 * DevelopmentOptionRanker ranker = new DevelopmentOptionRanker();
 * 
 * // Define criteria weights
 * ranker.setWeight(Criterion.NPV, 0.25);
 * ranker.setWeight(Criterion.CO2_INTENSITY, 0.15);
 * ranker.setWeight(Criterion.TECHNICAL_RISK, 0.20);
 * ranker.setWeight(Criterion.STRATEGIC_FIT, 0.15);
 * ranker.setWeight(Criterion.EXECUTION_RISK, 0.25);
 * 
 * // Add development options
 * DevelopmentOption opt1 = ranker.addOption("FPSO Development");
 * opt1.setScore(Criterion.NPV, 850.0); // MUSD
 * opt1.setScore(Criterion.CO2_INTENSITY, 12.0); // kg/boe
 * opt1.setScore(Criterion.TECHNICAL_RISK, 0.7); // 0-1, higher=riskier
 * opt1.setScore(Criterion.STRATEGIC_FIT, 0.9); // 0-1
 * opt1.setScore(Criterion.EXECUTION_RISK, 0.6);
 * 
 * DevelopmentOption opt2 = ranker.addOption("Tieback to Platform A");
 * opt2.setScore(Criterion.NPV, 420.0);
 * opt2.setScore(Criterion.CO2_INTENSITY, 8.0);
 * opt2.setScore(Criterion.TECHNICAL_RISK, 0.3);
 * opt2.setScore(Criterion.STRATEGIC_FIT, 0.7);
 * opt2.setScore(Criterion.EXECUTION_RISK, 0.3);
 * 
 * // Rank options
 * RankingResult result = ranker.rank();
 * System.out.println(result.generateReport());
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class DevelopmentOptionRanker implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Logger instance. */
  private static final Logger logger = LogManager.getLogger(DevelopmentOptionRanker.class);

  /**
   * Decision criteria for ranking.
   */
  public enum Criterion {
    // Economic criteria
    /** Net present value (MUSD). Higher is better. */
    NPV("NPV", "MUSD", true),
    /** Internal rate of return (%). Higher is better. */
    IRR("IRR", "%", true),
    /** Payback period (years). Lower is better. */
    PAYBACK("Payback", "years", false),
    /** Capital efficiency (NPV/CAPEX). Higher is better. */
    CAPITAL_EFFICIENCY("Capital Efficiency", "ratio", true),
    /** Breakeven oil price (USD/bbl). Lower is better. */
    BREAKEVEN_PRICE("Breakeven Price", "USD/bbl", false),

    // Technical criteria
    /** Technical complexity (0-1). Lower is better. */
    TECHNICAL_COMPLEXITY("Technical Complexity", "0-1", false),
    /** Technology risk (0-1). Lower is better. */
    TECHNICAL_RISK("Technical Risk", "0-1", false),
    /** Reservoir uncertainty (0-1). Lower is better. */
    RESERVOIR_UNCERTAINTY("Reservoir Uncertainty", "0-1", false),
    /** Recovery factor (%). Higher is better. */
    RECOVERY_FACTOR("Recovery Factor", "%", true),

    // Environmental criteria
    /** CO₂ intensity (kg CO₂/boe). Lower is better. */
    CO2_INTENSITY("CO2 Intensity", "kg/boe", false),
    /** Total emissions (kt CO₂/year). Lower is better. */
    TOTAL_EMISSIONS("Total Emissions", "kt/year", false),
    /** Environmental impact score (0-1). Lower is better. */
    ENVIRONMENTAL_IMPACT("Environmental Impact", "0-1", false),

    // Strategic criteria
    /** Strategic fit (0-1). Higher is better. */
    STRATEGIC_FIT("Strategic Fit", "0-1", true),
    /** Infrastructure synergies (0-1). Higher is better. */
    INFRASTRUCTURE_SYNERGY("Infrastructure Synergy", "0-1", true),
    /** Optionality value (0-1). Higher is better. */
    OPTIONALITY("Optionality", "0-1", true),
    /** Schedule flexibility (0-1). Higher is better. */
    SCHEDULE_FLEXIBILITY("Schedule Flexibility", "0-1", true),

    // Risk criteria
    /** HSE risk (0-1). Lower is better. */
    HSE_RISK("HSE Risk", "0-1", false),
    /** Execution risk (0-1). Lower is better. */
    EXECUTION_RISK("Execution Risk", "0-1", false),
    /** Commercial risk (0-1). Lower is better. */
    COMMERCIAL_RISK("Commercial Risk", "0-1", false),
    /** Regulatory risk (0-1). Lower is better. */
    REGULATORY_RISK("Regulatory Risk", "0-1", false);

    private final String displayName;
    private final String unit;
    private final boolean higherIsBetter;

    Criterion(String displayName, String unit, boolean higherIsBetter) {
      this.displayName = displayName;
      this.unit = unit;
      this.higherIsBetter = higherIsBetter;
    }

    /**
     * Get display name.
     *
     * @return the display name for this criterion
     */
    public String getDisplayName() {
      return displayName;
    }

    /**
     * Get unit.
     *
     * @return the unit of measurement for this criterion
     */
    public String getUnit() {
      return unit;
    }

    /**
     * Check if higher values are better.
     *
     * @return true if higher values are better for this criterion
     */
    public boolean isHigherBetter() {
      return higherIsBetter;
    }
  }

  /**
   * A development option with scores for each criterion.
   */
  public static class DevelopmentOption implements Serializable {
    private static final long serialVersionUID = 1001L;

    private String name;
    private String description;
    private Map<Criterion, Double> scores;
    private Map<Criterion, Double> normalizedScores;
    private double weightedScore;
    private int rank;

    /**
     * Creates a new development option.
     *
     * @param name option name
     */
    public DevelopmentOption(String name) {
      this.name = name;
      this.description = "";
      this.scores = new HashMap<Criterion, Double>();
      this.normalizedScores = new HashMap<Criterion, Double>();
    }

    /**
     * Get option name.
     *
     * @return the option name
     */
    public String getName() {
      return name;
    }

    /**
     * Get description.
     *
     * @return the option description
     */
    public String getDescription() {
      return description;
    }

    /**
     * Set description.
     *
     * @param description the option description
     */
    public void setDescription(String description) {
      this.description = description;
    }

    /**
     * Set score for a criterion.
     *
     * @param criterion the criterion
     * @param value the raw score value
     */
    public void setScore(Criterion criterion, double value) {
      scores.put(criterion, Double.valueOf(value));
    }

    /**
     * Get raw score for a criterion.
     *
     * @param criterion the criterion
     * @return raw score, or NaN if not set
     */
    public double getScore(Criterion criterion) {
      Double value = scores.get(criterion);
      return value != null ? value.doubleValue() : Double.NaN;
    }

    /**
     * Get normalized score for a criterion (0-1, higher is better).
     *
     * @param criterion the criterion
     * @return normalized score
     */
    public double getNormalizedScore(Criterion criterion) {
      Double value = normalizedScores.get(criterion);
      return value != null ? value.doubleValue() : 0.0;
    }

    /**
     * Set normalized score.
     *
     * @param criterion the criterion
     * @param value the normalized score value (0-1)
     */
    void setNormalizedScore(Criterion criterion, double value) {
      normalizedScores.put(criterion, Double.valueOf(value));
    }

    /**
     * Get weighted total score.
     *
     * @return the weighted total score
     */
    public double getWeightedScore() {
      return weightedScore;
    }

    /**
     * Set weighted score.
     *
     * @param score the weighted total score
     */
    void setWeightedScore(double score) {
      this.weightedScore = score;
    }

    /**
     * Get rank.
     *
     * @return the rank (1 = best)
     */
    public int getRank() {
      return rank;
    }

    /**
     * Set rank.
     *
     * @param rank the rank (1 = best)
     */
    void setRank(int rank) {
      this.rank = rank;
    }

    /**
     * Get all raw scores.
     *
     * @return map of criterion to raw score
     */
    public Map<Criterion, Double> getScores() {
      return scores;
    }
  }

  /**
   * Result of ranking analysis.
   */
  public static class RankingResult implements Serializable {
    private static final long serialVersionUID = 1002L;

    private List<DevelopmentOption> rankedOptions;
    private Map<Criterion, Double> weights;
    private Map<Criterion, Double> minValues;
    private Map<Criterion, Double> maxValues;

    /**
     * Creates a new ranking result.
     */
    public RankingResult() {
      this.rankedOptions = new ArrayList<DevelopmentOption>();
      this.weights = new HashMap<Criterion, Double>();
      this.minValues = new HashMap<Criterion, Double>();
      this.maxValues = new HashMap<Criterion, Double>();
    }

    /**
     * Get ranked options (best first).
     *
     * @return list of development options sorted by rank
     */
    public List<DevelopmentOption> getRankedOptions() {
      return rankedOptions;
    }

    /**
     * Get the best option.
     *
     * @return the highest ranked development option, or null if none
     */
    public DevelopmentOption getBestOption() {
      return rankedOptions.isEmpty() ? null : rankedOptions.get(0);
    }

    /**
     * Get weights used.
     *
     * @return map of criterion to weight
     */
    public Map<Criterion, Double> getWeights() {
      return weights;
    }

    /**
     * Generate a formatted report.
     *
     * @return report string
     */
    public String generateReport() {
      StringBuilder sb = new StringBuilder();
      sb.append("=== DEVELOPMENT OPTION RANKING ===\n\n");

      // Summary table
      sb.append("RANKING SUMMARY:\n");
      sb.append(String.format("%-5s %-25s %12s%n", "Rank", "Option", "Score"));
      sb.append("------------------------------------------------\n");

      for (DevelopmentOption opt : rankedOptions) {
        sb.append(String.format("%-5d %-25s %12.3f%n", opt.getRank(), opt.getName(),
            opt.getWeightedScore()));
      }

      sb.append("\n");

      // Criteria weights
      sb.append("CRITERIA WEIGHTS:\n");
      for (Map.Entry<Criterion, Double> entry : weights.entrySet()) {
        if (entry.getValue().doubleValue() > 0) {
          sb.append(String.format("  %-25s: %.0f%%%n", entry.getKey().getDisplayName(),
              entry.getValue() * 100));
        }
      }

      sb.append("\n");

      // Detailed scores
      sb.append("DETAILED SCORES:\n");
      for (DevelopmentOption opt : rankedOptions) {
        sb.append(String.format("\n%d. %s (Score: %.3f)%n", opt.getRank(), opt.getName(),
            opt.getWeightedScore()));
        for (Map.Entry<Criterion, Double> entry : opt.getScores().entrySet()) {
          Criterion c = entry.getKey();
          Double weight = weights.get(c);
          if (weight != null && weight.doubleValue() > 0) {
            sb.append(String.format("   %-25s: %8.2f %s (normalized: %.2f)%n", c.getDisplayName(),
                entry.getValue(), c.getUnit(), opt.getNormalizedScore(c)));
          }
        }
      }

      return sb.toString();
    }

    /**
     * Get sensitivity analysis for a criterion.
     *
     * @param criterion the criterion to analyze
     * @return map of weight to best option name
     */
    public Map<Double, String> sensitivityAnalysis(Criterion criterion) {
      // Placeholder for sensitivity analysis
      Map<Double, String> sensitivity = new HashMap<Double, String>();
      // Would re-rank at different weight values
      return sensitivity;
    }
  }

  // ============================================================================
  // INSTANCE VARIABLES
  // ============================================================================

  /** Development options to rank. */
  private List<DevelopmentOption> options;

  /** Criterion weights (sum should equal 1.0). */
  private Map<Criterion, Double> weights;

  // ============================================================================
  // CONSTRUCTORS
  // ============================================================================

  /**
   * Creates a new ranker with default equal weights.
   */
  public DevelopmentOptionRanker() {
    this.options = new ArrayList<DevelopmentOption>();
    this.weights = new HashMap<Criterion, Double>();

    // Default weights
    setWeight(Criterion.NPV, 0.20);
    setWeight(Criterion.CAPITAL_EFFICIENCY, 0.10);
    setWeight(Criterion.CO2_INTENSITY, 0.15);
    setWeight(Criterion.TECHNICAL_RISK, 0.15);
    setWeight(Criterion.EXECUTION_RISK, 0.15);
    setWeight(Criterion.STRATEGIC_FIT, 0.10);
    setWeight(Criterion.HSE_RISK, 0.15);
  }

  // ============================================================================
  // OPTION MANAGEMENT
  // ============================================================================

  /**
   * Add a development option.
   *
   * @param name option name
   * @return the created option for score setting
   */
  public DevelopmentOption addOption(String name) {
    DevelopmentOption opt = new DevelopmentOption(name);
    options.add(opt);
    return opt;
  }

  /**
   * Add a development option directly.
   *
   * @param option the option to add
   */
  public void addOption(DevelopmentOption option) {
    options.add(option);
  }

  /**
   * Get all options.
   *
   * @return list of options
   */
  public List<DevelopmentOption> getOptions() {
    return options;
  }

  /**
   * Clear all options.
   */
  public void clearOptions() {
    options.clear();
  }

  // ============================================================================
  // WEIGHT MANAGEMENT
  // ============================================================================

  /**
   * Set weight for a criterion.
   *
   * @param criterion the criterion
   * @param weight weight (0-1)
   */
  public void setWeight(Criterion criterion, double weight) {
    weights.put(criterion, Double.valueOf(weight));
  }

  /**
   * Get weight for a criterion.
   *
   * @param criterion the criterion
   * @return weight value
   */
  public double getWeight(Criterion criterion) {
    Double value = weights.get(criterion);
    return value != null ? value.doubleValue() : 0.0;
  }

  /**
   * Normalize weights to sum to 1.0.
   */
  public void normalizeWeights() {
    double sum = 0.0;
    for (Double w : weights.values()) {
      sum += w.doubleValue();
    }
    if (sum > 0) {
      for (Map.Entry<Criterion, Double> entry : weights.entrySet()) {
        entry.setValue(Double.valueOf(entry.getValue().doubleValue() / sum));
      }
    }
  }

  /**
   * Set preset weight profile.
   *
   * @param profile profile name ("economic", "environmental", "balanced", "risk")
   */
  public void setWeightProfile(String profile) {
    weights.clear();

    if ("economic".equalsIgnoreCase(profile)) {
      setWeight(Criterion.NPV, 0.35);
      setWeight(Criterion.IRR, 0.20);
      setWeight(Criterion.CAPITAL_EFFICIENCY, 0.15);
      setWeight(Criterion.BREAKEVEN_PRICE, 0.15);
      setWeight(Criterion.EXECUTION_RISK, 0.15);

    } else if ("environmental".equalsIgnoreCase(profile)) {
      setWeight(Criterion.CO2_INTENSITY, 0.30);
      setWeight(Criterion.TOTAL_EMISSIONS, 0.20);
      setWeight(Criterion.ENVIRONMENTAL_IMPACT, 0.20);
      setWeight(Criterion.NPV, 0.15);
      setWeight(Criterion.HSE_RISK, 0.15);

    } else if ("risk".equalsIgnoreCase(profile)) {
      setWeight(Criterion.TECHNICAL_RISK, 0.20);
      setWeight(Criterion.EXECUTION_RISK, 0.20);
      setWeight(Criterion.HSE_RISK, 0.20);
      setWeight(Criterion.COMMERCIAL_RISK, 0.15);
      setWeight(Criterion.REGULATORY_RISK, 0.10);
      setWeight(Criterion.NPV, 0.15);

    } else { // balanced (default)
      setWeight(Criterion.NPV, 0.20);
      setWeight(Criterion.CO2_INTENSITY, 0.15);
      setWeight(Criterion.TECHNICAL_RISK, 0.15);
      setWeight(Criterion.EXECUTION_RISK, 0.15);
      setWeight(Criterion.STRATEGIC_FIT, 0.10);
      setWeight(Criterion.HSE_RISK, 0.15);
      setWeight(Criterion.CAPITAL_EFFICIENCY, 0.10);
    }
  }

  // ============================================================================
  // RANKING
  // ============================================================================

  /**
   * Rank all options using weighted scoring.
   *
   * @return ranking result
   */
  public RankingResult rank() {
    logger.info("Ranking {} development options", options.size());

    RankingResult result = new RankingResult();
    result.getWeights().putAll(weights);

    if (options.isEmpty()) {
      return result;
    }

    // Find min/max for each criterion for normalization
    Map<Criterion, Double> minValues = new HashMap<Criterion, Double>();
    Map<Criterion, Double> maxValues = new HashMap<Criterion, Double>();

    for (Criterion c : Criterion.values()) {
      double min = Double.MAX_VALUE;
      double max = Double.MIN_VALUE;

      for (DevelopmentOption opt : options) {
        double score = opt.getScore(c);
        if (!Double.isNaN(score)) {
          min = Math.min(min, score);
          max = Math.max(max, score);
        }
      }

      if (min != Double.MAX_VALUE) {
        minValues.put(c, Double.valueOf(min));
        maxValues.put(c, Double.valueOf(max));
      }
    }

    result.minValues.putAll(minValues);
    result.maxValues.putAll(maxValues);

    // Normalize scores and calculate weighted totals
    for (DevelopmentOption opt : options) {
      double weightedTotal = 0.0;
      double totalWeight = 0.0;

      for (Map.Entry<Criterion, Double> entry : weights.entrySet()) {
        Criterion c = entry.getKey();
        double weight = entry.getValue().doubleValue();

        if (weight <= 0) {
          continue;
        }

        double rawScore = opt.getScore(c);
        if (Double.isNaN(rawScore)) {
          continue;
        }

        // Normalize to 0-1
        Double minVal = minValues.get(c);
        Double maxVal = maxValues.get(c);
        double normalized = 0.5; // default if no range

        if (minVal != null && maxVal != null) {
          double min = minVal.doubleValue();
          double max = maxVal.doubleValue();
          double range = max - min;

          if (range > 0) {
            normalized = (rawScore - min) / range;
          } else {
            normalized = 1.0; // all same value
          }
        }

        // Invert if lower is better
        if (!c.isHigherBetter()) {
          normalized = 1.0 - normalized;
        }

        opt.setNormalizedScore(c, normalized);
        weightedTotal += normalized * weight;
        totalWeight += weight;
      }

      // Normalize by total weight used
      if (totalWeight > 0) {
        opt.setWeightedScore(weightedTotal / totalWeight);
      }
    }

    // Sort by weighted score (descending)
    List<DevelopmentOption> sorted = new ArrayList<DevelopmentOption>(options);
    Collections.sort(sorted, new Comparator<DevelopmentOption>() {
      @Override
      public int compare(DevelopmentOption a, DevelopmentOption b) {
        return Double.compare(b.getWeightedScore(), a.getWeightedScore());
      }
    });

    // Assign ranks
    for (int i = 0; i < sorted.size(); i++) {
      sorted.get(i).setRank(i + 1);
    }

    result.getRankedOptions().addAll(sorted);

    logger.info("Ranking complete. Best option: {}", result.getBestOption().getName());

    return result;
  }

  /**
   * Quick rank by a single criterion.
   *
   * @param criterion criterion to rank by
   * @return ranked options
   */
  public List<DevelopmentOption> rankByCriterion(Criterion criterion) {
    List<DevelopmentOption> sorted = new ArrayList<DevelopmentOption>(options);
    final boolean higherBetter = criterion.isHigherBetter();

    Collections.sort(sorted, new Comparator<DevelopmentOption>() {
      @Override
      public int compare(DevelopmentOption a, DevelopmentOption b) {
        double scoreA = a.getScore(criterion);
        double scoreB = b.getScore(criterion);
        if (higherBetter) {
          return Double.compare(scoreB, scoreA);
        } else {
          return Double.compare(scoreA, scoreB);
        }
      }
    });

    return sorted;
  }
}
