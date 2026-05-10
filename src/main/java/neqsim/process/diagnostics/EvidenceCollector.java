package neqsim.process.diagnostics;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Analyzes historian and STID data to collect evidence for or against each hypothesis.
 *
 * <p>
 * The collector performs four types of analysis on time-series data:
 * </p>
 * <ul>
 * <li><b>Trend analysis</b> — detects drifting parameters (linear regression slope)</li>
 * <li><b>Correlation analysis</b> — identifies parameters that changed together</li>
 * <li><b>Threshold exceedance</b> — checks when parameters exceeded design limits</li>
 * <li><b>Rate-of-change detection</b> — distinguishes sudden vs gradual changes</li>
 * </ul>
 *
 * <p>
 * Evidence strength is assigned as STRONG, MODERATE, WEAK, or CONTRADICTORY based on statistical
 * significance and physical relevance.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class EvidenceCollector implements Serializable {

  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(EvidenceCollector.class);

  /** Historian time-series data keyed by logical parameter name. */
  private Map<String, double[]> historianData;

  /** Timestamps parallel to historian data arrays (Unix epoch seconds). */
  private double[] timestamps;

  /** STID design conditions keyed by parameter name. */
  private Map<String, String> stidData;

  /** Design limits (high/low) keyed by parameter name. */
  private Map<String, double[]> designLimits;

  /**
   * Creates an evidence collector.
   */
  public EvidenceCollector() {
    this.historianData = new HashMap<>();
    this.stidData = new HashMap<>();
    this.designLimits = new HashMap<>();
  }

  /**
   * Sets the historian time-series data.
   *
   * @param data map of parameter name to time-series values
   * @param timestamps array of timestamps (Unix epoch seconds) parallel to data arrays
   */
  public void setHistorianData(Map<String, double[]> data, double[] timestamps) {
    this.historianData = data != null ? data : new HashMap<String, double[]>();
    this.timestamps = timestamps;
  }

  /**
   * Sets STID metadata.
   *
   * @param stidData map of parameter to STID value/description
   */
  public void setStidData(Map<String, String> stidData) {
    this.stidData = stidData != null ? stidData : new HashMap<String, String>();
  }

  /**
   * Sets a design limit for a parameter.
   *
   * @param parameter parameter name
   * @param lowLimit low limit (use Double.NaN if no low limit)
   * @param highLimit high limit (use Double.NaN if no high limit)
   */
  public void setDesignLimit(String parameter, double lowLimit, double highLimit) {
    designLimits.put(parameter, new double[] {lowLimit, highLimit});
  }

  /**
   * Loads historian data from a CSV file.
   *
   * <p>
   * Expected format: first column is timestamp (epoch seconds or ISO), remaining columns are
   * parameter values. First row is header with parameter names.
   * </p>
   *
   * @param csvPath path to CSV file
   * @throws IOException if file cannot be read
   */
  public void loadFromCsv(String csvPath) throws IOException {
    BufferedReader reader = new BufferedReader(new FileReader(csvPath));
    try {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        return;
      }

      String[] headers = headerLine.split(",");
      List<double[]> rows = new ArrayList<>();
      String line;
      while ((line = reader.readLine()) != null) {
        String[] parts = line.split(",");
        double[] values = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
          try {
            values[i] = Double.parseDouble(parts[i].trim());
          } catch (NumberFormatException e) {
            values[i] = Double.NaN;
          }
        }
        rows.add(values);
      }

      if (rows.isEmpty()) {
        return;
      }

      // First column = timestamps
      int n = rows.size();
      this.timestamps = new double[n];
      for (int i = 0; i < n; i++) {
        this.timestamps[i] = rows.get(i)[0];
      }

      // Remaining columns = parameters
      for (int col = 1; col < headers.length; col++) {
        double[] values = new double[n];
        for (int i = 0; i < n; i++) {
          values[i] = col < rows.get(i).length ? rows.get(i)[col] : Double.NaN;
        }
        historianData.put(headers[col].trim(), values);
      }

      logger.info("Loaded {} parameters, {} data points from CSV", headers.length - 1, n);
    } finally {
      reader.close();
    }
  }

  /**
   * Collects evidence for a hypothesis by analyzing all available data.
   *
   * @param hypothesis the hypothesis to evaluate
   * @return list of evidence items found
   */
  public List<Hypothesis.Evidence> collectEvidence(Hypothesis hypothesis) {
    List<Hypothesis.Evidence> evidence = new ArrayList<>();

    for (Map.Entry<String, double[]> entry : historianData.entrySet()) {
      String param = entry.getKey();
      double[] values = entry.getValue();

      if (values.length < 3) {
        continue;
      }

      // Trend analysis
      Hypothesis.Evidence trendEvidence = analyzeTrend(param, values, hypothesis);
      if (trendEvidence != null) {
        evidence.add(trendEvidence);
      }

      // Threshold exceedance
      Hypothesis.Evidence threshEvidence = analyzeThreshold(param, values, hypothesis);
      if (threshEvidence != null) {
        evidence.add(threshEvidence);
      }

      // Rate of change
      Hypothesis.Evidence rateEvidence = analyzeRateOfChange(param, values, hypothesis);
      if (rateEvidence != null) {
        evidence.add(rateEvidence);
      }
    }

    // Correlation analysis across parameters
    List<Hypothesis.Evidence> correlationEvidence = analyzeCorrelations(hypothesis);
    evidence.addAll(correlationEvidence);

    // STID cross-reference
    List<Hypothesis.Evidence> stidEvidence = crossReferenceStid(hypothesis);
    evidence.addAll(stidEvidence);

    return evidence;
  }

  /**
   * Calculates the aggregate likelihood score from a set of evidence items.
   *
   * @param evidenceList evidence items
   * @return score in range 0 to 1
   */
  public double calculateLikelihoodScore(List<Hypothesis.Evidence> evidenceList) {
    if (evidenceList.isEmpty()) {
      return 0.5; // Neutral when no evidence
    }

    double score = 0.0;
    double totalWeight = 0.0;

    for (Hypothesis.Evidence e : evidenceList) {
      double weight;
      double contribution;
      switch (e.getStrength()) {
        case STRONG:
          weight = 3.0;
          contribution = 0.9;
          break;
        case MODERATE:
          weight = 2.0;
          contribution = 0.7;
          break;
        case WEAK:
          weight = 1.0;
          contribution = 0.5;
          break;
        case CONTRADICTORY:
          weight = 2.0;
          contribution = 0.1;
          break;
        default:
          weight = 1.0;
          contribution = 0.5;
      }
      score += weight * contribution;
      totalWeight += weight;
    }

    return totalWeight > 0 ? score / totalWeight : 0.5;
  }

  // ===== Analysis methods =====

  /**
   * Analyzes trend in a time-series.
   *
   * @param param parameter name
   * @param values data values
   * @param hypothesis hypothesis being evaluated
   * @return evidence item or null if no significant trend
   */
  private Hypothesis.Evidence analyzeTrend(String param, double[] values,
      Hypothesis hypothesis) {
    int n = values.length;
    if (n < 5) {
      return null;
    }

    // Simple linear regression: y = a + b*x
    double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
    int validCount = 0;
    for (int i = 0; i < n; i++) {
      if (!Double.isNaN(values[i])) {
        sumX += i;
        sumY += values[i];
        sumXY += i * values[i];
        sumX2 += (double) i * i;
        validCount++;
      }
    }

    if (validCount < 5) {
      return null;
    }

    double meanX = sumX / validCount;
    double meanY = sumY / validCount;
    double slope = (sumXY - validCount * meanX * meanY) / (sumX2 - validCount * meanX * meanX);

    // Calculate R-squared
    double ssTot = 0, ssRes = 0;
    for (int i = 0; i < n; i++) {
      if (!Double.isNaN(values[i])) {
        double predicted = meanY + slope * (i - meanX);
        ssTot += (values[i] - meanY) * (values[i] - meanY);
        ssRes += (values[i] - predicted) * (values[i] - predicted);
      }
    }
    double rSquared = ssTot > 0 ? 1.0 - ssRes / ssTot : 0;

    // Only report significant trends (R^2 > 0.3)
    if (rSquared < 0.3) {
      return null;
    }

    double changePercent = meanY != 0 ? Math.abs(slope * n / meanY) * 100 : 0;
    String direction = slope > 0 ? "increasing" : "decreasing";
    String observation =
        String.format("%s trend (slope=%.4f, R2=%.2f, ~%.1f%% change)", direction, slope, rSquared,
            changePercent);

    Hypothesis.EvidenceStrength strength;
    if (rSquared > 0.7 && changePercent > 10) {
      strength = Hypothesis.EvidenceStrength.STRONG;
    } else if (rSquared > 0.5) {
      strength = Hypothesis.EvidenceStrength.MODERATE;
    } else {
      strength = Hypothesis.EvidenceStrength.WEAK;
    }

    return new Hypothesis.Evidence(param, observation, strength, "historian-trend");
  }

  /**
   * Analyzes threshold exceedance.
   *
   * @param param parameter name
   * @param values data values
   * @param hypothesis hypothesis being evaluated
   * @return evidence item or null if within limits
   */
  private Hypothesis.Evidence analyzeThreshold(String param, double[] values,
      Hypothesis hypothesis) {
    double[] limits = designLimits.get(param);
    if (limits == null) {
      return null;
    }

    double lowLimit = limits[0];
    double highLimit = limits[1];
    int exceedCount = 0;
    int n = values.length;

    for (int i = 0; i < n; i++) {
      if (!Double.isNaN(values[i])) {
        if ((!Double.isNaN(highLimit) && values[i] > highLimit)
            || (!Double.isNaN(lowLimit) && values[i] < lowLimit)) {
          exceedCount++;
        }
      }
    }

    if (exceedCount == 0) {
      return null;
    }

    double exceedPct = (double) exceedCount / n * 100;
    String observation =
        String.format("Design limit exceeded %d times (%.1f%% of data)", exceedCount, exceedPct);

    Hypothesis.EvidenceStrength strength;
    if (exceedPct > 20) {
      strength = Hypothesis.EvidenceStrength.STRONG;
    } else if (exceedPct > 5) {
      strength = Hypothesis.EvidenceStrength.MODERATE;
    } else {
      strength = Hypothesis.EvidenceStrength.WEAK;
    }

    return new Hypothesis.Evidence(param, observation, strength, "historian-threshold");
  }

  /**
   * Analyzes rate of change to detect sudden vs gradual changes.
   *
   * @param param parameter name
   * @param values data values
   * @param hypothesis hypothesis being evaluated
   * @return evidence item or null if no significant rate of change
   */
  private Hypothesis.Evidence analyzeRateOfChange(String param, double[] values,
      Hypothesis hypothesis) {
    int n = values.length;
    if (n < 10) {
      return null;
    }

    // Calculate max absolute rate of change
    double maxRate = 0;
    int maxRateIdx = 0;
    double mean = 0;
    int count = 0;
    for (int i = 0; i < n; i++) {
      if (!Double.isNaN(values[i])) {
        mean += values[i];
        count++;
      }
    }
    mean = count > 0 ? mean / count : 0;

    for (int i = 1; i < n; i++) {
      if (!Double.isNaN(values[i]) && !Double.isNaN(values[i - 1])) {
        double rate = Math.abs(values[i] - values[i - 1]);
        if (rate > maxRate) {
          maxRate = rate;
          maxRateIdx = i;
        }
      }
    }

    // Compute standard deviation of differences
    double sumDiffSq = 0;
    double sumDiff = 0;
    int diffCount = 0;
    for (int i = 1; i < n; i++) {
      if (!Double.isNaN(values[i]) && !Double.isNaN(values[i - 1])) {
        double diff = Math.abs(values[i] - values[i - 1]);
        sumDiff += diff;
        sumDiffSq += diff * diff;
        diffCount++;
      }
    }

    if (diffCount < 5) {
      return null;
    }

    double meanDiff = sumDiff / diffCount;
    double stdDiff = Math.sqrt(sumDiffSq / diffCount - meanDiff * meanDiff);

    // Detect step change: max rate > 3 sigma
    if (stdDiff > 0 && maxRate > 3 * stdDiff + meanDiff) {
      String observation = String.format(
          "Step change detected at index %d (rate=%.4f, mean_rate=%.4f, 3-sigma=%.4f)", maxRateIdx,
          maxRate, meanDiff, 3 * stdDiff);
      return new Hypothesis.Evidence(param, observation, Hypothesis.EvidenceStrength.STRONG,
          "historian-rate");
    }

    return null;
  }

  /**
   * Analyzes correlations between parameters.
   *
   * @param hypothesis hypothesis being evaluated
   * @return list of correlation evidence
   */
  private List<Hypothesis.Evidence> analyzeCorrelations(Hypothesis hypothesis) {
    List<Hypothesis.Evidence> evidence = new ArrayList<>();
    List<String> paramNames = new ArrayList<>(historianData.keySet());

    for (int i = 0; i < paramNames.size(); i++) {
      for (int j = i + 1; j < paramNames.size(); j++) {
        double corr = pearsonCorrelation(historianData.get(paramNames.get(i)),
            historianData.get(paramNames.get(j)));

        if (Math.abs(corr) > 0.7) {
          String observation = String.format("Correlated with %s (r=%.3f)", paramNames.get(j),
              corr);
          Hypothesis.EvidenceStrength strength =
              Math.abs(corr) > 0.9 ? Hypothesis.EvidenceStrength.STRONG
                  : Hypothesis.EvidenceStrength.MODERATE;
          evidence.add(new Hypothesis.Evidence(paramNames.get(i), observation, strength,
              "historian-correlation"));
        }
      }
    }

    return evidence;
  }

  /**
   * Cross-references STID design conditions.
   *
   * @param hypothesis hypothesis being evaluated
   * @return list of STID-based evidence
   */
  private List<Hypothesis.Evidence> crossReferenceStid(Hypothesis hypothesis) {
    List<Hypothesis.Evidence> evidence = new ArrayList<>();

    for (Map.Entry<String, String> entry : stidData.entrySet()) {
      String param = entry.getKey();
      String stidValue = entry.getValue();

      double[] values = historianData.get(param);
      if (values == null || values.length == 0) {
        continue;
      }

      // Try to parse STID value as a number for comparison
      try {
        double designValue = Double.parseDouble(stidValue);
        double lastValue = values[values.length - 1];
        double deviation = Math.abs(lastValue - designValue) / Math.abs(designValue) * 100;

        if (deviation > 20) {
          String observation =
              String.format("Current=%.2f vs STID design=%.2f (%.1f%% deviation)", lastValue,
                  designValue, deviation);
          evidence.add(new Hypothesis.Evidence(param, observation,
              Hypothesis.EvidenceStrength.STRONG, "STID"));
        } else if (deviation > 10) {
          String observation =
              String.format("Current=%.2f vs STID design=%.2f (%.1f%% deviation)", lastValue,
                  designValue, deviation);
          evidence.add(new Hypothesis.Evidence(param, observation,
              Hypothesis.EvidenceStrength.MODERATE, "STID"));
        }
      } catch (NumberFormatException e) {
        // STID value is text, skip numeric comparison
      }
    }

    return evidence;
  }

  /**
   * Calculates Pearson correlation coefficient between two arrays.
   *
   * @param x first array
   * @param y second array
   * @return correlation coefficient (-1 to 1), or 0 if insufficient data
   */
  private double pearsonCorrelation(double[] x, double[] y) {
    int n = Math.min(x.length, y.length);
    if (n < 5) {
      return 0.0;
    }

    double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;
    int count = 0;

    for (int i = 0; i < n; i++) {
      if (!Double.isNaN(x[i]) && !Double.isNaN(y[i])) {
        sumX += x[i];
        sumY += y[i];
        sumXY += x[i] * y[i];
        sumX2 += x[i] * x[i];
        sumY2 += y[i] * y[i];
        count++;
      }
    }

    if (count < 5) {
      return 0.0;
    }

    double num = count * sumXY - sumX * sumY;
    double den = Math.sqrt((count * sumX2 - sumX * sumX) * (count * sumY2 - sumY * sumY));

    return den > 0 ? num / den : 0.0;
  }
}
