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
 * Unsupervised anomaly scanner that auto-detects abnormal historian tags and proposes candidate symptoms.
 *
 * <p>
 * This class removes the last manual input from autonomous root-cause work: instead of being told the symptom (e.g.
 * {@code Symptom.HIGH_VIBRATION}), it scans every tag against its own robust baseline and, when available, its STID
 * design envelope, and reports which tags are abnormal and how. Each abnormal tag is mapped to a candidate
 * {@link Symptom} by tag-name heuristics, so a caller can start a diagnosis from raw data alone.
 * </p>
 *
 * <p>
 * Detection methods per tag:
 * </p>
 * <ul>
 * <li><b>Threshold</b> — the latest value crosses a supplied design limit (highest severity).</li>
 * <li><b>Spike</b> — the latest value is a robust-z outlier versus the tag's median and MAD.</li>
 * <li><b>Trend</b> — a sustained increasing or decreasing drift (linear fit slope and R^2).</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * AnomalyScanner scanner = new AnomalyScanner();
 * scanner.setDesignLimit("Compressor-1.vibration", Double.NaN, 7.1);
 * List&lt;AnomalyScanner.Anomaly&gt; anomalies = scanner.scan(historianData);
 * Symptom candidate = scanner.suggestSymptom(anomalies);
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see RelationshipGraph
 * @see RootCauseAnalyzer
 */
public class AnomalyScanner implements Serializable {

  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(AnomalyScanner.class);

  /** Minimum number of non-NaN samples required to evaluate a tag. */
  private static final int MIN_VALID_POINTS = 6;

  /** Scale factor converting median absolute deviation to a standard-deviation estimate. */
  private static final double MAD_SCALE = 1.4826;

  /** Minimum robust z-score for a spike to be reported. */
  private double spikeZThreshold = 3.0;

  /** Minimum R^2 for a trend to be considered significant. */
  private double trendR2Threshold = 0.5;

  /** Minimum absolute percent change over the window for a trend to be reported. */
  private double trendMinChangePercent = 10.0;

  /** Design limits per tag: [lowLimit, highLimit], NaN meaning no limit. */
  private final java.util.Map<String, double[]> designLimits = new java.util.HashMap<String, double[]>();

  /**
   * Creates an anomaly scanner with default thresholds.
   */
  public AnomalyScanner() {
  }

  /**
   * Sets a design limit for a tag used by threshold detection.
   *
   * @param tag tag name
   * @param lowLimit low limit, or Double.NaN for none
   * @param highLimit high limit, or Double.NaN for none
   */
  public void setDesignLimit(String tag, double lowLimit, double highLimit) {
    designLimits.put(tag, new double[] { lowLimit, highLimit });
  }

  /**
   * Sets the minimum robust z-score for a spike to be reported.
   *
   * @param spikeZThreshold z-score threshold
   */
  public void setSpikeZThreshold(double spikeZThreshold) {
    this.spikeZThreshold = spikeZThreshold;
  }

  /**
   * Sets the minimum R^2 for a trend to be reported.
   *
   * @param trendR2Threshold R^2 threshold in range 0 to 1
   */
  public void setTrendR2Threshold(double trendR2Threshold) {
    this.trendR2Threshold = trendR2Threshold;
  }

  /**
   * Scans all tags and returns anomalies ranked by descending severity.
   *
   * @param data map of tag name to time-series values; may contain NaN gaps
   * @return anomalies ranked by descending severity; empty when nothing abnormal is found
   */
  public List<Anomaly> scan(Map<String, double[]> data) {
    List<Anomaly> anomalies = new ArrayList<Anomaly>();
    if (data == null || data.isEmpty()) {
      return anomalies;
    }

    for (Map.Entry<String, double[]> entry : data.entrySet()) {
      Anomaly a = scanTag(entry.getKey(), entry.getValue());
      if (a != null) {
        anomalies.add(a);
      }
    }

    Collections.sort(anomalies, new Comparator<Anomaly>() {
      @Override
      public int compare(Anomaly a, Anomaly b) {
        return Double.compare(b.getSeverity(), a.getSeverity());
      }
    });

    logger.info("AnomalyScanner flagged {} abnormal tags out of {}", anomalies.size(), data.size());
    return anomalies;
  }

  /**
   * Evaluates one tag and returns its most severe anomaly, or null when the tag looks normal.
   *
   * @param tag tag name
   * @param values time-series values
   * @return the most severe anomaly for the tag, or null
   */
  private Anomaly scanTag(String tag, double[] values) {
    if (values == null) {
      return null;
    }
    List<Double> valid = new ArrayList<Double>();
    for (double v : values) {
      if (!Double.isNaN(v)) {
        valid.add(v);
      }
    }
    if (valid.size() < MIN_VALID_POINTS) {
      return null;
    }

    double last = valid.get(valid.size() - 1);
    double median = median(valid);
    double mad = medianAbsoluteDeviation(valid, median);
    double sigma = mad > 1e-12 ? mad * MAD_SCALE : stdDev(valid);

    // 1) Threshold detection (highest priority).
    double[] limits = designLimits.get(tag);
    if (limits != null) {
      double lo = limits[0];
      double hi = limits[1];
      if (!Double.isNaN(hi) && last > hi) {
        return new Anomaly(tag, AnomalyKind.THRESHOLD_HIGH, 0.95, last,
            String.format("latest %.4g exceeds high limit %.4g", last, hi));
      }
      if (!Double.isNaN(lo) && last < lo) {
        return new Anomaly(tag, AnomalyKind.THRESHOLD_LOW, 0.95, last,
            String.format("latest %.4g below low limit %.4g", last, lo));
      }
    }

    // 2) Spike detection (robust z-score of the latest value).
    if (sigma > 1e-12) {
      double z = (last - median) / sigma;
      if (Math.abs(z) >= spikeZThreshold) {
        double severity = Math.min(0.9, 0.4 + Math.abs(z) / 12.0);
        AnomalyKind kind = z > 0 ? AnomalyKind.SPIKE_HIGH : AnomalyKind.SPIKE_LOW;
        return new Anomaly(tag, kind, severity, last,
            String.format("latest %.4g is %.1f robust-sigma from median %.4g", last, z, median));
      }
    }

    // 3) Trend detection (linear drift over the window).
    double[] fit = linearFit(valid);
    double slope = fit[0];
    double r2 = fit[1];
    double meanY = fit[2];
    double changePercent = meanY != 0.0 ? Math.abs(slope * (valid.size() - 1) / meanY) * 100.0 : 0.0;
    if (r2 >= trendR2Threshold && changePercent >= trendMinChangePercent) {
      double severity = Math.min(0.85, r2 * Math.min(1.0, changePercent / 50.0));
      AnomalyKind kind = slope > 0 ? AnomalyKind.TREND_UP : AnomalyKind.TREND_DOWN;
      return new Anomaly(tag, kind, severity, last, String.format("%s trend (R2=%.2f, ~%.0f%% change)",
          slope > 0 ? "increasing" : "decreasing", r2, changePercent));
    }

    return null;
  }

  /**
   * Suggests the most likely symptom from a ranked anomaly list by tag-name heuristics.
   *
   * @param anomalies anomalies, typically the output of {@link #scan(Map)}
   * @return the highest-severity symptom that maps from a tag name, or null when none maps
   */
  public Symptom suggestSymptom(List<Anomaly> anomalies) {
    if (anomalies == null) {
      return null;
    }
    for (Anomaly a : anomalies) {
      Symptom s = mapTagToSymptom(a.getTag(), a.getKind());
      if (s != null) {
        return s;
      }
    }
    return null;
  }

  /**
   * Maps a tag name and anomaly kind to a candidate symptom using keyword heuristics.
   *
   * @param tag tag name
   * @param kind detected anomaly kind
   * @return a candidate symptom, or null when no keyword matches
   */
  public Symptom mapTagToSymptom(String tag, AnomalyKind kind) {
    if (tag == null) {
      return null;
    }
    String t = tag.toLowerCase(java.util.Locale.ROOT);
    if (t.contains("vibration") || t.contains("vib")) {
      return Symptom.HIGH_VIBRATION;
    }
    if (t.contains("temp") || t.endsWith(".tt") || t.contains("_tt")) {
      return Symptom.HIGH_TEMPERATURE;
    }
    if (t.contains("power") || t.contains("current") || t.contains("motor") || t.contains("amp")) {
      return Symptom.HIGH_POWER;
    }
    if (t.contains("effic")) {
      return Symptom.LOW_EFFICIENCY;
    }
    if (t.contains("surge")) {
      return Symptom.SURGE_EVENT;
    }
    if (t.contains("level") || t.contains("carryover") || t.contains("interface")) {
      return Symptom.LIQUID_CARRYOVER;
    }
    if (t.contains("flow") || t.endsWith(".ft") || t.contains("_ft")) {
      return Symptom.FLOW_DEVIATION;
    }
    if (t.contains("pressure") || t.contains("press") || t.endsWith(".pt") || t.contains("_pt")) {
      return Symptom.PRESSURE_DEVIATION;
    }
    if (t.contains("noise") || t.contains("sound")) {
      return Symptom.ABNORMAL_NOISE;
    }
    return null;
  }

  /**
   * Computes the median of a value list (list is copied and sorted internally).
   *
   * @param values value list
   * @return median value
   */
  private double median(List<Double> values) {
    List<Double> copy = new ArrayList<Double>(values);
    Collections.sort(copy);
    int mid = copy.size() / 2;
    if (copy.size() % 2 == 0) {
      return (copy.get(mid - 1) + copy.get(mid)) / 2.0;
    }
    return copy.get(mid);
  }

  /**
   * Computes the median absolute deviation about a given center.
   *
   * @param values value list
   * @param center center value (typically the median)
   * @return median absolute deviation
   */
  private double medianAbsoluteDeviation(List<Double> values, double center) {
    List<Double> dev = new ArrayList<Double>(values.size());
    for (double v : values) {
      dev.add(Math.abs(v - center));
    }
    return median(dev);
  }

  /**
   * Computes the sample standard deviation of a value list.
   *
   * @param values value list
   * @return standard deviation
   */
  private double stdDev(List<Double> values) {
    double mean = 0.0;
    for (double v : values) {
      mean += v;
    }
    mean /= values.size();
    double ss = 0.0;
    for (double v : values) {
      ss += (v - mean) * (v - mean);
    }
    return Math.sqrt(ss / Math.max(1, values.size() - 1));
  }

  /**
   * Fits a straight line to a value series against its sample index.
   *
   * @param values value list
   * @return array of {slope, rSquared, meanY}
   */
  private double[] linearFit(List<Double> values) {
    int n = values.size();
    double sumX = 0.0;
    double sumY = 0.0;
    double sumXY = 0.0;
    double sumX2 = 0.0;
    for (int i = 0; i < n; i++) {
      double y = values.get(i);
      sumX += i;
      sumY += y;
      sumXY += i * y;
      sumX2 += (double) i * i;
    }
    double meanX = sumX / n;
    double meanY = sumY / n;
    double denom = sumX2 - n * meanX * meanX;
    if (Math.abs(denom) < 1e-20) {
      return new double[] { 0.0, 0.0, meanY };
    }
    double slope = (sumXY - n * meanX * meanY) / denom;
    double ssTot = 0.0;
    double ssRes = 0.0;
    for (int i = 0; i < n; i++) {
      double y = values.get(i);
      double predicted = meanY + slope * (i - meanX);
      ssTot += (y - meanY) * (y - meanY);
      ssRes += (y - predicted) * (y - predicted);
    }
    double r2 = ssTot > 0.0 ? 1.0 - ssRes / ssTot : 0.0;
    return new double[] { slope, r2, meanY };
  }

  /**
   * Kind of anomaly detected on a tag.
   */
  public enum AnomalyKind {
    /** Latest value above a design high limit. */
    THRESHOLD_HIGH,
    /** Latest value below a design low limit. */
    THRESHOLD_LOW,
    /** Latest value is a high outlier versus the tag baseline. */
    SPIKE_HIGH,
    /** Latest value is a low outlier versus the tag baseline. */
    SPIKE_LOW,
    /** Sustained increasing drift. */
    TREND_UP,
    /** Sustained decreasing drift. */
    TREND_DOWN
  }

  /**
   * A detected anomaly on a single tag.
   */
  public static final class Anomaly implements Serializable {

    private static final long serialVersionUID = 1000L;

    /** Tag name. */
    private final String tag;

    /** Anomaly kind. */
    private final AnomalyKind kind;

    /** Severity in range 0 to 1. */
    private final double severity;

    /** Latest observed value. */
    private final double latestValue;

    /** Human-readable description. */
    private final String description;

    /**
     * Creates an anomaly.
     *
     * @param tag tag name
     * @param kind anomaly kind
     * @param severity severity in range 0 to 1
     * @param latestValue latest observed value
     * @param description human-readable description
     */
    public Anomaly(String tag, AnomalyKind kind, double severity, double latestValue, String description) {
      this.tag = tag;
      this.kind = kind;
      this.severity = severity;
      this.latestValue = latestValue;
      this.description = description;
    }

    /**
     * Returns the tag name.
     *
     * @return tag name
     */
    public String getTag() {
      return tag;
    }

    /**
     * Returns the anomaly kind.
     *
     * @return anomaly kind
     */
    public AnomalyKind getKind() {
      return kind;
    }

    /**
     * Returns the severity.
     *
     * @return severity in range 0 to 1
     */
    public double getSeverity() {
      return severity;
    }

    /**
     * Returns the latest observed value.
     *
     * @return latest value
     */
    public double getLatestValue() {
      return latestValue;
    }

    /**
     * Returns the human-readable description.
     *
     * @return description
     */
    public String getDescription() {
      return description;
    }

    /**
     * Returns a human-readable summary of the anomaly.
     *
     * @return formatted summary
     */
    @Override
    public String toString() {
      return String.format("%s [%s, sev=%.2f]: %s", tag, kind, severity, description);
    }
  }
}
