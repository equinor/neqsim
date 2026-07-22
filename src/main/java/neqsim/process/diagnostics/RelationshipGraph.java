package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Unsupervised lead-lag relationship discovery across historian tags.
 *
 * <p>
 * Unlike {@link EvidenceCollector}, which only correlates tags a hypothesis already expects, this class scans
 * <b>all</b> tag pairs in a historian data set with no symptom and no hypothesis supplied, and reports which tags move
 * together and, crucially, <b>which moves first</b>. This gives an autonomous investigator the relationships it needs
 * to form hypotheses on its own instead of being told what to look for.
 * </p>
 *
 * <p>
 * For every unordered pair of tags {@code (X, Y)} the graph computes the Pearson correlation at a range of time lags
 * and keeps the lag with the strongest absolute correlation. A positive optimal lag means {@code X} leads {@code Y};
 * the resulting {@link Relationship} is stored as a directed edge from the leading (candidate cause) tag to the
 * following (candidate effect) tag. Lead-lag directionality is the key signal that distinguishes a driver from a
 * follower and lets downstream causal/topology reasoning promote a statistical edge to a causal candidate.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * RelationshipGraph graph = new RelationshipGraph();
 * graph.setTimestamps(timestamps); // optional, enables lag in seconds
 * graph.setMaxLagSamples(10);
 * graph.setMinAbsCorrelation(0.5);
 * List&lt;RelationshipGraph.Relationship&gt; edges = graph.analyze(historianData);
 * System.out.println(graph.toTextReport(edges));
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see EvidenceCollector
 * @see RootCauseAnalyzer
 */
public class RelationshipGraph implements Serializable {

  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(RelationshipGraph.class);

  /** Minimum number of overlapping non-NaN sample pairs required to compute a correlation. */
  private static final int MIN_VALID_POINTS = 5;

  /** Maximum lag, in samples, to search in each direction. */
  private int maxLagSamples = 10;

  /** Minimum absolute correlation for a relationship to be reported. */
  private double minAbsCorrelation = 0.3;

  /** When true, correlation is computed on rank-transformed data (Spearman) to capture monotonic non-linearity. */
  private boolean useRankCorrelation = false;

  /** Optional timestamps parallel to the historian arrays, used to express lag in seconds. */
  private double[] timestamps;

  /**
   * Creates a relationship graph with default settings (max lag 10 samples, minimum |r| 0.3).
   */
  public RelationshipGraph() {
  }

  /**
   * Sets the maximum lag, in samples, searched in each direction.
   *
   * @param maxLagSamples maximum lag in samples; values below 0 are clamped to 0
   */
  public void setMaxLagSamples(int maxLagSamples) {
    this.maxLagSamples = Math.max(0, maxLagSamples);
  }

  /**
   * Sets the minimum absolute correlation for a relationship to be reported.
   *
   * @param minAbsCorrelation threshold in range 0 to 1
   */
  public void setMinAbsCorrelation(double minAbsCorrelation) {
    this.minAbsCorrelation = minAbsCorrelation;
  }

  /**
   * Enables or disables rank-based (Spearman) correlation.
   *
   * <p>
   * When enabled, each series is rank-transformed before correlation, so strong monotonic but non-linear couplings
   * (e.g. a saturating or exponential response) are captured that a linear Pearson correlation would under-report.
   * </p>
   *
   * @param useRankCorrelation true to use rank (Spearman) correlation, false for linear (Pearson)
   */
  public void setUseRankCorrelation(boolean useRankCorrelation) {
    this.useRankCorrelation = useRankCorrelation;
  }

  /**
   * Sets timestamps parallel to the historian arrays so lag can be expressed in seconds.
   *
   * @param timestamps array of timestamps, or null to report lag only in samples
   */
  public void setTimestamps(double[] timestamps) {
    this.timestamps = timestamps;
  }

  /**
   * Discovers lead-lag relationships across all tag pairs in the historian data.
   *
   * @param data map of tag name to time-series values; may contain NaN gaps
   * @return relationships ranked by descending absolute correlation; empty when fewer than two usable tags exist
   */
  public List<Relationship> analyze(Map<String, double[]> data) {
    List<Relationship> relationships = new ArrayList<Relationship>();
    if (data == null || data.size() < 2) {
      return relationships;
    }

    Map<String, double[]> working = data;
    if (useRankCorrelation) {
      working = new java.util.LinkedHashMap<String, double[]>();
      for (Map.Entry<String, double[]> entry : data.entrySet()) {
        working.put(entry.getKey(), toRanks(entry.getValue()));
      }
    }

    List<String> tags = new ArrayList<String>(working.keySet());
    double medianDt = medianTimestepSeconds();

    for (int i = 0; i < tags.size(); i++) {
      for (int j = i + 1; j < tags.size(); j++) {
        String tagX = tags.get(i);
        String tagY = tags.get(j);
        double[] x = working.get(tagX);
        double[] y = working.get(tagY);
        if (x == null || y == null) {
          continue;
        }
        Relationship r = bestLaggedRelationship(tagX, x, tagY, y, medianDt);
        if (r != null && Math.abs(r.getCorrelation()) >= minAbsCorrelation) {
          relationships.add(r);
        }
      }
    }

    Collections.sort(relationships, new Comparator<Relationship>() {
      @Override
      public int compare(Relationship a, Relationship b) {
        return Double.compare(Math.abs(b.getCorrelation()), Math.abs(a.getCorrelation()));
      }
    });

    logger.info("RelationshipGraph discovered {} relationships from {} tags", relationships.size(), tags.size());
    return relationships;
  }

  /**
   * Finds the lag with the strongest absolute correlation for one tag pair and builds the directed relationship.
   *
   * @param tagX first tag name
   * @param x first time-series
   * @param tagY second tag name
   * @param y second time-series
   * @param medianDt median timestep in seconds, or NaN when unknown
   * @return the best relationship for the pair, or null when no lag yields a valid correlation
   */
  private Relationship bestLaggedRelationship(String tagX, double[] x, String tagY, double[] y, double medianDt) {
    double zeroLag = pearsonLagged(x, y, 0);
    double bestCorr = Double.NaN;
    int bestLag = 0;

    for (int lag = -maxLagSamples; lag <= maxLagSamples; lag++) {
      double corr = pearsonLagged(x, y, lag);
      if (Double.isNaN(corr)) {
        continue;
      }
      if (Double.isNaN(bestCorr) || Math.abs(corr) > Math.abs(bestCorr)) {
        bestCorr = corr;
        bestLag = lag;
      }
    }

    if (Double.isNaN(bestCorr)) {
      return null;
    }

    // Positive lag means X leads Y (X[i] aligns with Y[i+lag]).
    String leader = bestLag >= 0 ? tagX : tagY;
    String follower = bestLag >= 0 ? tagY : tagX;
    int lagSamples = Math.abs(bestLag);
    double lagSeconds = Double.isNaN(medianDt) ? Double.NaN : lagSamples * medianDt;
    Direction direction = lagSamples == 0 ? Direction.SYNCHRONOUS : Direction.LEADS;
    return new Relationship(leader, follower, direction, lagSamples, lagSeconds, bestCorr, zeroLag);
  }

  /**
   * Computes the Pearson correlation between two series at a given integer lag, ignoring NaN pairs.
   *
   * <p>
   * A positive lag aligns {@code a[i]} with {@code b[i + lag]}.
   * </p>
   *
   * @param a first series
   * @param b second series
   * @param lag integer lag in samples
   * @return correlation in range -1 to 1, or NaN when fewer than {@value #MIN_VALID_POINTS} valid pairs overlap
   */
  private double pearsonLagged(double[] a, double[] b, int lag) {
    if (a == null || b == null) {
      return Double.NaN;
    }
    double sumA = 0.0;
    double sumB = 0.0;
    double sumAB = 0.0;
    double sumA2 = 0.0;
    double sumB2 = 0.0;
    int n = 0;

    int len = Math.min(a.length, b.length);
    for (int i = 0; i < len; i++) {
      int k = i + lag;
      if (k < 0 || k >= len) {
        continue;
      }
      double av = a[i];
      double bv = b[k];
      if (Double.isNaN(av) || Double.isNaN(bv)) {
        continue;
      }
      sumA += av;
      sumB += bv;
      sumAB += av * bv;
      sumA2 += av * av;
      sumB2 += bv * bv;
      n++;
    }

    if (n < MIN_VALID_POINTS) {
      return Double.NaN;
    }

    double covariance = sumAB - sumA * sumB / n;
    double varA = sumA2 - sumA * sumA / n;
    double varB = sumB2 - sumB * sumB / n;
    if (varA <= 1e-20 || varB <= 1e-20) {
      return Double.NaN;
    }
    return covariance / Math.sqrt(varA * varB);
  }

  /**
   * Returns the median timestep, in seconds, from the timestamps, or NaN when timestamps are unavailable.
   *
   * @return median timestep in seconds, or NaN
   */
  private double medianTimestepSeconds() {
    if (timestamps == null || timestamps.length < 2) {
      return Double.NaN;
    }
    List<Double> steps = new ArrayList<Double>();
    for (int i = 1; i < timestamps.length; i++) {
      double dt = timestamps[i] - timestamps[i - 1];
      if (!Double.isNaN(dt) && dt > 0.0) {
        steps.add(dt);
      }
    }
    if (steps.isEmpty()) {
      return Double.NaN;
    }
    Collections.sort(steps);
    int mid = steps.size() / 2;
    if (steps.size() % 2 == 0) {
      return (steps.get(mid - 1) + steps.get(mid)) / 2.0;
    }
    return steps.get(mid);
  }

  /**
   * Rank-transforms a series for Spearman correlation, preserving NaN positions and averaging tied ranks.
   *
   * @param values the series to rank
   * @return a new array of the same length with fractional ranks in non-NaN positions and NaN elsewhere
   */
  private double[] toRanks(double[] values) {
    if (values == null) {
      return new double[0];
    }
    int n = values.length;
    double[] ranks = new double[n];
    List<Integer> idx = new ArrayList<Integer>();
    for (int i = 0; i < n; i++) {
      if (Double.isNaN(values[i])) {
        ranks[i] = Double.NaN;
      } else {
        idx.add(i);
      }
    }
    final double[] src = values;
    Collections.sort(idx, new Comparator<Integer>() {
      @Override
      public int compare(Integer a, Integer b) {
        return Double.compare(src[a], src[b]);
      }
    });
    int k = 0;
    while (k < idx.size()) {
      int start = k;
      double value = src[idx.get(k)];
      while (k + 1 < idx.size() && src[idx.get(k + 1)] == value) {
        k++;
      }
      // Average rank for the tie group (ranks are 1-based).
      double avgRank = (start + k) / 2.0 + 1.0;
      for (int m = start; m <= k; m++) {
        ranks[idx.get(m)] = avgRank;
      }
      k++;
    }
    return ranks;
  }

  /**
   * Renders a human-readable report of discovered relationships.
   *
   * @param relationships relationships to render, typically the output of {@link #analyze(Map)}
   * @return a formatted multi-line report
   */
  public String toTextReport(List<Relationship> relationships) {
    StringBuilder sb = new StringBuilder();
    sb.append("Discovered relationships (ranked by |correlation|):\n");
    if (relationships == null || relationships.isEmpty()) {
      sb.append("  (none above threshold |r| >= ").append(String.format("%.2f", minAbsCorrelation)).append(")\n");
      return sb.toString();
    }
    int rank = 1;
    for (Relationship r : relationships) {
      sb.append(String.format("  %d. %s%n", rank++, r.toString()));
    }
    return sb.toString();
  }

  /**
   * Direction of a discovered relationship.
   */
  public enum Direction {
    /** The source tag leads the target tag by a non-zero lag. */
    LEADS,
    /** The two tags are correlated with no detectable lag. */
    SYNCHRONOUS
  }

  /**
   * A directed lead-lag relationship discovered between two tags.
   *
   * <p>
   * The {@code source} tag is the candidate driver (it moves first for a {@link Direction#LEADS} edge); the
   * {@code target} tag is the candidate effect.
   * </p>
   */
  public static final class Relationship implements Serializable {

    private static final long serialVersionUID = 1000L;

    /** Candidate driver tag (moves first for a LEADS edge). */
    private final String source;

    /** Candidate effect tag. */
    private final String target;

    /** Relationship direction. */
    private final Direction direction;

    /** Lag magnitude in samples. */
    private final int lagSamples;

    /** Lag magnitude in seconds, or NaN when timestamps were not supplied. */
    private final double lagSeconds;

    /** Correlation at the best lag, in range -1 to 1. */
    private final double correlation;

    /** Correlation at zero lag, in range -1 to 1. */
    private final double zeroLagCorrelation;

    /**
     * Creates a relationship.
     *
     * @param source candidate driver tag
     * @param target candidate effect tag
     * @param direction relationship direction
     * @param lagSamples lag magnitude in samples
     * @param lagSeconds lag magnitude in seconds, or NaN
     * @param correlation correlation at the best lag
     * @param zeroLagCorrelation correlation at zero lag
     */
    public Relationship(String source, String target, Direction direction, int lagSamples, double lagSeconds,
        double correlation, double zeroLagCorrelation) {
      this.source = source;
      this.target = target;
      this.direction = direction;
      this.lagSamples = lagSamples;
      this.lagSeconds = lagSeconds;
      this.correlation = correlation;
      this.zeroLagCorrelation = zeroLagCorrelation;
    }

    /**
     * Returns the candidate driver tag.
     *
     * @return source tag name
     */
    public String getSource() {
      return source;
    }

    /**
     * Returns the candidate effect tag.
     *
     * @return target tag name
     */
    public String getTarget() {
      return target;
    }

    /**
     * Returns the relationship direction.
     *
     * @return direction
     */
    public Direction getDirection() {
      return direction;
    }

    /**
     * Returns the lag magnitude in samples.
     *
     * @return lag in samples
     */
    public int getLagSamples() {
      return lagSamples;
    }

    /**
     * Returns the lag magnitude in seconds.
     *
     * @return lag in seconds, or NaN when timestamps were not supplied
     */
    public double getLagSeconds() {
      return lagSeconds;
    }

    /**
     * Returns the correlation at the best lag.
     *
     * @return correlation in range -1 to 1
     */
    public double getCorrelation() {
      return correlation;
    }

    /**
     * Returns the correlation at zero lag.
     *
     * @return zero-lag correlation in range -1 to 1
     */
    public double getZeroLagCorrelation() {
      return zeroLagCorrelation;
    }

    /**
     * Returns a human-readable description of this relationship.
     *
     * @return formatted relationship description
     */
    @Override
    public String toString() {
      String sign = correlation >= 0.0 ? "+" : "-";
      if (direction == Direction.SYNCHRONOUS) {
        return String.format("%s <-> %s (r=%s%.2f, synchronous)", source, target, sign, Math.abs(correlation));
      }
      String lagStr = Double.isNaN(lagSeconds) ? String.format("%d samples", lagSamples)
          : String.format("%.0f s", lagSeconds);
      return String.format("%s -> %s (r=%s%.2f, leads by %s)", source, target, sign, Math.abs(correlation), lagStr);
    }
  }
}
