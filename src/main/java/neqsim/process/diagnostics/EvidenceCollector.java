package neqsim.process.diagnostics;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.diagnostics.Hypothesis.ExpectedBehavior;
import neqsim.process.diagnostics.Hypothesis.ExpectedSignal;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Analyzes historian and STID data to collect evidence for or against each hypothesis.
 *
 * <p>
 * The collector compares observed historian behavior against the expected diagnostic signals stored
 * on each hypothesis. Evidence is only attached to a hypothesis when a tag matches one of its
 * fingerprints, unless the hypothesis has no fingerprints, in which case legacy generic evidence is
 * returned for backward compatibility.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.1
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
   * @param lowLimit low limit, or Double.NaN if no low limit applies
   * @param highLimit high limit, or Double.NaN if no high limit applies
   */
  public void setDesignLimit(String parameter, double lowLimit, double highLimit) {
    designLimits.put(parameter, new double[] {lowLimit, highLimit});
  }

  /**
   * Loads historian data from a CSV file.
   *
   * <p>
   * Expected format: first column is timestamp, remaining columns are parameter values. First row
   * is a header with parameter names.
   * </p>
   *
   * @param csvPath path to CSV file
   * @throws IOException if file cannot be read
   */
  public void loadFromCsv(String csvPath) throws IOException {
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(new FileInputStream(csvPath), StandardCharsets.UTF_8));
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

      int n = rows.size();
      this.timestamps = new double[n];
      for (int i = 0; i < n; i++) {
        this.timestamps[i] = rows.get(i)[0];
      }

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
   * Collects hypothesis-specific evidence by analyzing all available data.
   *
   * @param hypothesis the hypothesis to evaluate
   * @return list of evidence items found
   */
  public List<Hypothesis.Evidence> collectEvidence(Hypothesis hypothesis) {
    List<Hypothesis.Evidence> evidence = new ArrayList<>();

    for (Map.Entry<String, double[]> entry : historianData.entrySet()) {
      String param = entry.getKey();
      double[] values = entry.getValue();

      if (values == null || values.length < 3) {
        continue;
      }

      addIfNotNull(evidence, analyzeTrend(param, values, hypothesis));
      addIfNotNull(evidence, analyzeThreshold(param, values, hypothesis));
      addIfNotNull(evidence, analyzeRateOfChange(param, values, hypothesis));
      addIfNotNull(evidence, analyzeChangePoint(param, values, hypothesis));
      addIfNotNull(evidence, analyzeAcceleration(param, values, hypothesis));
    }

    evidence.addAll(analyzeCorrelations(hypothesis));
    evidence.addAll(analyzeMultiParameterPattern(hypothesis));
    evidence.addAll(crossReferenceStid(hypothesis));
    return evidence;
  }

  /**
   * Calculates the aggregate likelihood score from evidence items.
   *
   * @param evidenceList evidence items
   * @return score in range 0 to 1
   */
  public double calculateLikelihoodScore(List<Hypothesis.Evidence> evidenceList) {
    if (evidenceList.isEmpty()) {
      return 0.5;
    }

    double score = 0.0;
    double totalWeight = 0.0;
    for (Hypothesis.Evidence evidence : evidenceList) {
      double evidenceWeight = evidence.getWeight() * strengthWeight(evidence.getStrength());
      double contribution = evidenceContribution(evidence);
      score += evidenceWeight * contribution;
      totalWeight += evidenceWeight;
    }
    return totalWeight > 0.0 ? score / totalWeight : 0.5;
  }

  /**
   * Adds an evidence item when it is not null.
   *
   * @param evidenceList target evidence list
   * @param evidence evidence item, possibly null
   */
  private void addIfNotNull(List<Hypothesis.Evidence> evidenceList, Hypothesis.Evidence evidence) {
    if (evidence != null) {
      evidenceList.add(evidence);
    }
  }

  /**
   * Returns the numerical weight for an evidence strength.
   *
   * @param strength evidence strength
   * @return numerical weight
   */
  private double strengthWeight(Hypothesis.EvidenceStrength strength) {
    switch (strength) {
      case STRONG:
        return 3.0;
      case MODERATE:
        return 2.0;
      case WEAK:
        return 1.0;
      case CONTRADICTORY:
        return 2.5;
      default:
        return 1.0;
    }
  }

  /**
   * Returns the likelihood contribution for one evidence item.
   *
   * @param evidence evidence item
   * @return contribution in range 0 to 1
   */
  private double evidenceContribution(Hypothesis.Evidence evidence) {
    if (!evidence.isSupporting()
        || evidence.getStrength() == Hypothesis.EvidenceStrength.CONTRADICTORY) {
      return 0.1;
    }
    switch (evidence.getStrength()) {
      case STRONG:
        return 0.9;
      case MODERATE:
        return 0.7;
      case WEAK:
        return 0.5;
      default:
        return 0.5;
    }
  }

  /**
   * Analyzes trend in a time-series.
   *
   * @param param parameter name
   * @param values data values
   * @param hypothesis hypothesis being evaluated
   * @return evidence item or null if no significant trend matches the hypothesis
   */
  private Hypothesis.Evidence analyzeTrend(String param, double[] values, Hypothesis hypothesis) {
    int n = values.length;
    if (n < 5) {
      return null;
    }

    double sumX = 0.0;
    double sumY = 0.0;
    double sumXY = 0.0;
    double sumX2 = 0.0;
    int validCount = 0;
    for (int i = 0; i < n; i++) {
      if (!Double.isNaN(values[i])) {
        double x = regressionX(i);
        sumX += x;
        sumY += values[i];
        sumXY += x * values[i];
        sumX2 += x * x;
        validCount++;
      }
    }
    if (validCount < 5) {
      return null;
    }

    double meanX = sumX / validCount;
    double meanY = sumY / validCount;
    double denominator = sumX2 - validCount * meanX * meanX;
    if (Math.abs(denominator) < 1e-20) {
      return null;
    }
    double slope = (sumXY - validCount * meanX * meanY) / denominator;

    double ssTot = 0.0;
    double ssRes = 0.0;
    for (int i = 0; i < n; i++) {
      if (!Double.isNaN(values[i])) {
        double x = regressionX(i);
        double predicted = meanY + slope * (x - meanX);
        ssTot += (values[i] - meanY) * (values[i] - meanY);
        ssRes += (values[i] - predicted) * (values[i] - predicted);
      }
    }

    double rSquared = ssTot > 0.0 ? 1.0 - ssRes / ssTot : 0.0;
    if (rSquared < 0.3) {
      return null;
    }

    double xSpan = Math.max(1.0, regressionX(n - 1) - regressionX(0));
    double changePercent = meanY != 0.0 ? Math.abs(slope * xSpan / meanY) * 100.0 : 0.0;
    String direction = slope > 0.0 ? "increasing" : "decreasing";
    String observation = String.format("%s trend (slope=%.4g, R2=%.2f, ~%.1f%% change)", direction,
        slope, rSquared, changePercent);

    Hypothesis.EvidenceStrength strength = trendStrength(rSquared, changePercent);
    ExpectedBehavior observed = slope > 0.0 ? ExpectedBehavior.INCREASE : ExpectedBehavior.DECREASE;
    return createEvidenceForObservedBehavior(param, observation, strength, "historian-trend",
        observed, hypothesis);
  }

  /**
   * Determines trend evidence strength from fit quality and magnitude.
   *
   * @param rSquared regression coefficient of determination
   * @param changePercent approximate percent change over the window
   * @return evidence strength
   */
  private Hypothesis.EvidenceStrength trendStrength(double rSquared, double changePercent) {
    if (rSquared > 0.7 && changePercent > 10.0) {
      return Hypothesis.EvidenceStrength.STRONG;
    } else if (rSquared > 0.5) {
      return Hypothesis.EvidenceStrength.MODERATE;
    }
    return Hypothesis.EvidenceStrength.WEAK;
  }

  /**
   * Returns the x-coordinate used in trend regression.
   *
   * @param index sample index
   * @return timestamp if available, otherwise the sample index
   */
  private double regressionX(int index) {
    if (timestamps != null && index < timestamps.length && !Double.isNaN(timestamps[index])) {
      return timestamps[index];
    }
    return index;
  }

  /**
   * Analyzes design-limit exceedance.
   *
   * @param param parameter name
   * @param values data values
   * @param hypothesis hypothesis being evaluated
   * @return evidence item or null if limits are not exceeded or not relevant
   */
  private Hypothesis.Evidence analyzeThreshold(String param, double[] values,
      Hypothesis hypothesis) {
    double[] limits = designLimits.get(param);
    if (limits == null) {
      limits = findLimitByAlias(param);
    }
    if (limits == null) {
      return null;
    }

    double lowLimit = limits[0];
    double highLimit = limits[1];
    int highCount = 0;
    int lowCount = 0;
    int validCount = 0;
    for (double value : values) {
      if (!Double.isNaN(value)) {
        validCount++;
        if (!Double.isNaN(highLimit) && value > highLimit) {
          highCount++;
        }
        if (!Double.isNaN(lowLimit) && value < lowLimit) {
          lowCount++;
        }
      }
    }
    int exceedCount = highCount + lowCount;
    if (validCount == 0 || exceedCount == 0) {
      return null;
    }

    double exceedPct = (double) exceedCount / validCount * 100.0;
    ExpectedBehavior observed =
        highCount >= lowCount ? ExpectedBehavior.HIGH_LIMIT : ExpectedBehavior.LOW_LIMIT;
    String limitType = observed == ExpectedBehavior.HIGH_LIMIT ? "high" : "low";
    String observation = String.format("%s design limit exceeded %d times (%.1f%% of data)",
        limitType, exceedCount, exceedPct);

    Hypothesis.EvidenceStrength strength;
    if (exceedPct > 20.0) {
      strength = Hypothesis.EvidenceStrength.STRONG;
    } else if (exceedPct > 5.0) {
      strength = Hypothesis.EvidenceStrength.MODERATE;
    } else {
      strength = Hypothesis.EvidenceStrength.WEAK;
    }
    return createEvidenceForObservedBehavior(param, observation, strength, "historian-threshold",
        observed, hypothesis);
  }

  /**
   * Finds a design limit whose key aliases the parameter name.
   *
   * @param param parameter name
   * @return low/high limit pair, or null if not found
   */
  private double[] findLimitByAlias(String param) {
    for (Map.Entry<String, double[]> entry : designLimits.entrySet()) {
      if (matchesPattern(param, entry.getKey())) {
        return entry.getValue();
      }
    }
    return null;
  }

  /**
   * Analyzes rate of change to detect step changes.
   *
   * @param param parameter name
   * @param values data values
   * @param hypothesis hypothesis being evaluated
   * @return evidence item or null if no significant step change matches the hypothesis
   */
  private Hypothesis.Evidence analyzeRateOfChange(String param, double[] values,
      Hypothesis hypothesis) {
    int n = values.length;
    if (n < 10) {
      return null;
    }

    double maxRate = 0.0;
    int maxRateIndex = 0;
    double sumDiff = 0.0;
    double sumDiffSq = 0.0;
    int diffCount = 0;
    for (int i = 1; i < n; i++) {
      if (!Double.isNaN(values[i]) && !Double.isNaN(values[i - 1])) {
        double diff = Math.abs(values[i] - values[i - 1]);
        if (diff > maxRate) {
          maxRate = diff;
          maxRateIndex = i;
        }
        sumDiff += diff;
        sumDiffSq += diff * diff;
        diffCount++;
      }
    }
    if (diffCount < 5) {
      return null;
    }

    double meanDiff = sumDiff / diffCount;
    double variance = Math.max(0.0, sumDiffSq / diffCount - meanDiff * meanDiff);
    double stdDiff = Math.sqrt(variance);
    if (stdDiff > 0.0 && maxRate > 3.0 * stdDiff + meanDiff) {
      String observation =
          String.format("Step change at index %d (rate=%.4g, mean_rate=%.4g, 3-sigma=%.4g)",
              maxRateIndex, maxRate, meanDiff, 3.0 * stdDiff);
      return createEvidenceForObservedBehavior(param, observation,
          Hypothesis.EvidenceStrength.STRONG, "historian-rate", ExpectedBehavior.STEP_CHANGE,
          hypothesis);
    }
    return null;
  }

  /**
   * Detects change points by comparing the mean of the first and second halves of the data.
   *
   * <p>
   * A change point is flagged when the difference between half-means exceeds 2 standard deviations
   * of the full series, indicating a statistically significant shift in operating regime.
   * </p>
   *
   * @param param parameter name
   * @param values data values
   * @param hypothesis hypothesis being evaluated
   * @return evidence item or null if no significant change point found
   */
  private Hypothesis.Evidence analyzeChangePoint(String param, double[] values,
      Hypothesis hypothesis) {
    int n = values.length;
    if (n < 10) {
      return null;
    }

    // Split data into halves and compute means
    int mid = n / 2;
    double sumFirst = 0.0;
    int countFirst = 0;
    double sumSecond = 0.0;
    int countSecond = 0;
    double sumAll = 0.0;
    double sumAllSq = 0.0;
    int countAll = 0;

    for (int i = 0; i < n; i++) {
      if (!Double.isNaN(values[i])) {
        sumAll += values[i];
        sumAllSq += values[i] * values[i];
        countAll++;
        if (i < mid) {
          sumFirst += values[i];
          countFirst++;
        } else {
          sumSecond += values[i];
          countSecond++;
        }
      }
    }

    if (countFirst < 3 || countSecond < 3) {
      return null;
    }

    double meanFirst = sumFirst / countFirst;
    double meanSecond = sumSecond / countSecond;
    double meanAll = sumAll / countAll;
    double variance = Math.max(0.0, sumAllSq / countAll - meanAll * meanAll);
    double stdAll = Math.sqrt(variance);

    if (stdAll < 1e-12) {
      return null;
    }

    double shiftMagnitude = Math.abs(meanSecond - meanFirst) / stdAll;
    if (shiftMagnitude >= 2.0) {
      String direction = meanSecond > meanFirst ? "upward" : "downward";
      String observation = String.format(
          "Change point detected: %s shift of %.1f sigma (first-half mean=%.4g, second-half mean=%.4g)",
          direction, shiftMagnitude, meanFirst, meanSecond);
      Hypothesis.EvidenceStrength strength = shiftMagnitude > 3.0
          ? Hypothesis.EvidenceStrength.STRONG : Hypothesis.EvidenceStrength.MODERATE;
      ExpectedBehavior observed =
          meanSecond > meanFirst ? ExpectedBehavior.INCREASE : ExpectedBehavior.DECREASE;
      return createEvidenceForObservedBehavior(param, observation, strength,
          "historian-changepoint", observed, hypothesis);
    }
    return null;
  }

  /**
   * Detects acceleration (trend getting worse faster) by comparing slopes of the first and second
   * halves of the data.
   *
   * <p>
   * If the absolute slope doubles between the first and second halves, it indicates an accelerating
   * degradation which is a strong indicator of imminent failure.
   * </p>
   *
   * @param param parameter name
   * @param values data values
   * @param hypothesis hypothesis being evaluated
   * @return evidence item or null if no significant acceleration found
   */
  private Hypothesis.Evidence analyzeAcceleration(String param, double[] values,
      Hypothesis hypothesis) {
    int n = values.length;
    if (n < 20) {
      return null;
    }

    int mid = n / 2;
    double slopeFirst = computeSlope(values, 0, mid);
    double slopeSecond = computeSlope(values, mid, n);

    if (Math.abs(slopeFirst) < 1e-12) {
      return null;
    }

    double accelerationRatio = Math.abs(slopeSecond) / Math.abs(slopeFirst);
    boolean sameDirection = Math.signum(slopeFirst) == Math.signum(slopeSecond);

    if (sameDirection && accelerationRatio > 2.0) {
      String direction = slopeSecond > 0 ? "increasing" : "decreasing";
      String observation = String.format(
          "Accelerating %s trend: second-half slope %.2fx first-half (slope1=%.4g, slope2=%.4g)",
          direction, accelerationRatio, slopeFirst, slopeSecond);
      Hypothesis.EvidenceStrength strength = accelerationRatio > 3.0
          ? Hypothesis.EvidenceStrength.STRONG : Hypothesis.EvidenceStrength.MODERATE;
      ExpectedBehavior observed =
          slopeSecond > 0 ? ExpectedBehavior.INCREASE : ExpectedBehavior.DECREASE;
      return createEvidenceForObservedBehavior(param, observation, strength,
          "historian-acceleration", observed, hypothesis);
    }
    return null;
  }

  /**
   * Computes the linear regression slope over a sub-range of the data.
   *
   * @param values data values
   * @param from start index (inclusive)
   * @param to end index (exclusive)
   * @return slope, or 0 if insufficient data
   */
  private double computeSlope(double[] values, int from, int to) {
    double sumX = 0.0;
    double sumY = 0.0;
    double sumXY = 0.0;
    double sumX2 = 0.0;
    int count = 0;
    for (int i = from; i < to; i++) {
      if (!Double.isNaN(values[i])) {
        double x = regressionX(i);
        sumX += x;
        sumY += values[i];
        sumXY += x * values[i];
        sumX2 += x * x;
        count++;
      }
    }
    if (count < 3) {
      return 0.0;
    }
    double meanX = sumX / count;
    double meanY = sumY / count;
    double denom = sumX2 - count * meanX * meanX;
    return Math.abs(denom) < 1e-20 ? 0.0 : (sumXY - count * meanX * meanY) / denom;
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
          String observation =
              String.format("Correlated with %s (r=%.3f)", paramNames.get(j), corr);
          Hypothesis.EvidenceStrength strength =
              Math.abs(corr) > 0.9 ? Hypothesis.EvidenceStrength.STRONG
                  : Hypothesis.EvidenceStrength.MODERATE;
          Hypothesis.Evidence item =
              createEvidenceForObservedBehavior(paramNames.get(i), observation, strength,
                  "historian-correlation", ExpectedBehavior.CORRELATION, hypothesis);
          if (item != null) {
            evidence.add(item);
          }
        }
      }
    }
    return evidence;
  }

  /**
   * Analyzes multi-parameter patterns specific to the hypothesis's expected signals.
   *
   * <p>
   * When a hypothesis expects multiple parameters to move in specific directions simultaneously
   * (e.g., vibration increasing AND efficiency decreasing), this method checks whether the
   * historian data confirms the combined fingerprint. A hypothesis whose full signal set is present
   * gets a STRONG "pattern match" evidence item.
   * </p>
   *
   * @param hypothesis hypothesis being evaluated
   * @return list of multi-parameter evidence items
   */
  private List<Hypothesis.Evidence> analyzeMultiParameterPattern(Hypothesis hypothesis) {
    List<Hypothesis.Evidence> evidence = new ArrayList<>();
    List<Hypothesis.ExpectedSignal> signals = hypothesis.getExpectedSignals();
    if (signals.size() < 2) {
      return evidence;
    }

    int matchCount = 0;
    int checkedCount = 0;
    StringBuilder matchDetails = new StringBuilder();

    for (Hypothesis.ExpectedSignal signal : signals) {
      // Find historian parameter matching this signal
      String matchedParam = null;
      double[] matchedValues = null;
      for (Map.Entry<String, double[]> entry : historianData.entrySet()) {
        if (matchesPattern(entry.getKey(), signal.getParameterPattern())) {
          matchedParam = entry.getKey();
          matchedValues = entry.getValue();
          break;
        }
      }

      if (matchedValues == null || matchedValues.length < 5) {
        continue;
      }

      checkedCount++;
      ExpectedBehavior observed = classifyBehavior(matchedValues);
      if (observed != null
          && (behaviorMatches(signal.getExpectedBehavior(), observed)
              || signal.getExpectedBehavior() == ExpectedBehavior.ANY_CHANGE)) {
        matchCount++;
        if (matchDetails.length() > 0) {
          matchDetails.append("; ");
        }
        matchDetails.append(matchedParam).append("=").append(observed.name());
      }
    }

    if (checkedCount >= 2 && matchCount >= 2) {
      double matchPct = (double) matchCount / checkedCount * 100.0;
      Hypothesis.EvidenceStrength strength = matchPct > 80.0
          ? Hypothesis.EvidenceStrength.STRONG : Hypothesis.EvidenceStrength.MODERATE;
      String observation = String.format(
          "Multi-parameter fingerprint: %d/%d expected signals confirmed (%s)",
          matchCount, checkedCount, matchDetails.toString());
      evidence.add(new Hypothesis.Evidence("multi-parameter", observation, strength,
          "historian-pattern", true, 3.0, "multi-param-correlation"));
    }

    return evidence;
  }

  /**
   * Classifies the dominant behavior of a time series.
   *
   * @param values data values
   * @return INCREASE, DECREASE, or null if no clear trend
   */
  private ExpectedBehavior classifyBehavior(double[] values) {
    int n = values.length;
    if (n < 3) {
      return null;
    }
    double firstValid = Double.NaN;
    double lastValid = Double.NaN;
    for (int i = 0; i < n; i++) {
      if (!Double.isNaN(values[i])) {
        if (Double.isNaN(firstValid)) {
          firstValid = values[i];
        }
        lastValid = values[i];
      }
    }
    if (Double.isNaN(firstValid) || Double.isNaN(lastValid)
        || Math.abs(firstValid) < 1e-12) {
      return null;
    }
    double changePct = (lastValid - firstValid) / Math.abs(firstValid) * 100.0;
    if (changePct > 5.0) {
      return ExpectedBehavior.INCREASE;
    } else if (changePct < -5.0) {
      return ExpectedBehavior.DECREASE;
    }
    return null;
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
      double[] values = findHistorianValues(param);
      if (values == null || values.length == 0) {
        continue;
      }
      try {
        double designValue = Double.parseDouble(entry.getValue());
        double lastValue = latestValid(values);
        if (Double.isNaN(lastValue) || Math.abs(designValue) < 1e-12) {
          continue;
        }
        double signedDeviation = (lastValue - designValue) / Math.abs(designValue) * 100.0;
        double deviation = Math.abs(signedDeviation);
        if (deviation > 10.0) {
          ExpectedBehavior observed =
              signedDeviation > 0.0 ? ExpectedBehavior.INCREASE : ExpectedBehavior.DECREASE;
          Hypothesis.EvidenceStrength strength =
              deviation > 20.0 ? Hypothesis.EvidenceStrength.STRONG
                  : Hypothesis.EvidenceStrength.MODERATE;
          String observation = String.format("Current=%.3g vs STID design=%.3g (%.1f%% deviation)",
              lastValue, designValue, signedDeviation);
          Hypothesis.Evidence item = createEvidenceForObservedBehavior(param, observation, strength,
              "STID", observed, hypothesis);
          if (item != null) {
            evidence.add(item);
          }
        }
      } catch (NumberFormatException e) {
        logger.debug("Skipping non-numeric STID value for {}: {}", param, entry.getValue());
      }
    }
    return evidence;
  }

  /**
   * Finds historian values by exact key or alias match.
   *
   * @param parameter parameter alias
   * @return values or null if not found
   */
  private double[] findHistorianValues(String parameter) {
    double[] values = historianData.get(parameter);
    if (values != null) {
      return values;
    }
    for (Map.Entry<String, double[]> entry : historianData.entrySet()) {
      if (matchesPattern(entry.getKey(), parameter)) {
        return entry.getValue();
      }
    }
    return null;
  }

  /**
   * Returns the latest valid value from an array.
   *
   * @param values time-series values
   * @return latest non-NaN value, or NaN if none exists
   */
  private double latestValid(double[] values) {
    for (int i = values.length - 1; i >= 0; i--) {
      if (!Double.isNaN(values[i])) {
        return values[i];
      }
    }
    return Double.NaN;
  }

  /**
   * Converts an observed behavior into evidence for the supplied hypothesis.
   *
   * @param param parameter name
   * @param observation observed behavior text
   * @param strength base evidence strength
   * @param source source identifier
   * @param observedBehavior observed behavior classification
   * @param hypothesis hypothesis being evaluated
   * @return evidence item, or null if the observation is irrelevant to the hypothesis
   */
  private Hypothesis.Evidence createEvidenceForObservedBehavior(String param, String observation,
      Hypothesis.EvidenceStrength strength, String source, ExpectedBehavior observedBehavior,
      Hypothesis hypothesis) {
    if (hypothesis.getExpectedSignals().isEmpty()) {
      return new Hypothesis.Evidence(param, observation, strength, source, true, 1.0,
          "tag=" + param);
    }

    ExpectedSignal signal = findMatchingSignal(hypothesis, param, observedBehavior);
    if (signal == null) {
      return null;
    }

    boolean supporting = behaviorMatches(signal.getExpectedBehavior(), observedBehavior);
    boolean contradictory = isContradictory(signal.getExpectedBehavior(), observedBehavior);
    if (!supporting && !contradictory) {
      return null;
    }

    Hypothesis.EvidenceStrength finalStrength =
        supporting ? strength : Hypothesis.EvidenceStrength.CONTRADICTORY;
    String enrichedObservation = supporting ? observation
        : observation + "; expected " + signal.getExpectedBehavior().name() + " because "
            + signal.getRationale();
    return new Hypothesis.Evidence(param, enrichedObservation, finalStrength, source, supporting,
        signal.getWeight(), "tag=" + param);
  }

  /**
   * Finds the best expected signal for a parameter and observed behavior.
   *
   * @param hypothesis hypothesis containing expected signals
   * @param param observed parameter name
   * @param observedBehavior observed behavior
   * @return matching signal, or null if none matches the parameter
   */
  private ExpectedSignal findMatchingSignal(Hypothesis hypothesis, String param,
      ExpectedBehavior observedBehavior) {
    ExpectedSignal fallback = null;
    for (ExpectedSignal signal : hypothesis.getExpectedSignals()) {
      if (matchesPattern(param, signal.getParameterPattern())) {
        if (behaviorMatches(signal.getExpectedBehavior(), observedBehavior)
            || isContradictory(signal.getExpectedBehavior(), observedBehavior)) {
          return signal;
        }
        if (fallback == null) {
          fallback = signal;
        }
      }
    }
    return fallback;
  }

  /**
   * Checks if the observed behavior supports the expected behavior.
   *
   * @param expected expected behavior
   * @param observed observed behavior
   * @return true if observed behavior supports the expected behavior
   */
  private boolean behaviorMatches(ExpectedBehavior expected, ExpectedBehavior observed) {
    if (expected == ExpectedBehavior.ANY_CHANGE || expected == observed) {
      return true;
    }
    if (expected == ExpectedBehavior.HIGH_LIMIT && observed == ExpectedBehavior.INCREASE) {
      return true;
    }
    if (expected == ExpectedBehavior.LOW_LIMIT && observed == ExpectedBehavior.DECREASE) {
      return true;
    }
    return false;
  }

  /**
   * Checks whether the observed behavior contradicts the expected behavior.
   *
   * @param expected expected behavior
   * @param observed observed behavior
   * @return true if the observed behavior is a meaningful contradiction
   */
  private boolean isContradictory(ExpectedBehavior expected, ExpectedBehavior observed) {
    if (expected == ExpectedBehavior.ANY_CHANGE || observed == ExpectedBehavior.ANY_CHANGE) {
      return false;
    }
    if (expected == ExpectedBehavior.INCREASE || expected == ExpectedBehavior.HIGH_LIMIT) {
      return observed == ExpectedBehavior.DECREASE || observed == ExpectedBehavior.LOW_LIMIT;
    }
    if (expected == ExpectedBehavior.DECREASE || expected == ExpectedBehavior.LOW_LIMIT) {
      return observed == ExpectedBehavior.INCREASE || observed == ExpectedBehavior.HIGH_LIMIT;
    }
    return false;
  }

  /**
   * Checks if a parameter name matches a pipe-separated alias pattern.
   *
   * @param parameter parameter name
   * @param pattern alias pattern
   * @return true when any alias matches
   */
  private boolean matchesPattern(String parameter, String pattern) {
    String normalizedParameter = normalize(parameter);
    String[] aliases = pattern == null ? new String[0] : pattern.split("\\|");
    for (String alias : aliases) {
      String normalizedAlias = normalize(alias);
      if (!normalizedAlias.isEmpty() && (normalizedParameter.contains(normalizedAlias)
          || normalizedAlias.contains(normalizedParameter))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Normalizes a parameter name for alias matching.
   *
   * @param text input text
   * @return lower-case alphanumeric text
   */
  private String normalize(String text) {
    return text == null ? "" : text.toLowerCase().replaceAll("[^a-z0-9]", "");
  }

  /**
   * Calculates Pearson correlation coefficient between two arrays.
   *
   * @param x first array
   * @param y second array
   * @return correlation coefficient in range -1 to 1, or 0 if insufficient data
   */
  private double pearsonCorrelation(double[] x, double[] y) {
    int n = Math.min(x.length, y.length);
    if (n < 5) {
      return 0.0;
    }

    double sumX = 0.0;
    double sumY = 0.0;
    double sumXY = 0.0;
    double sumX2 = 0.0;
    double sumY2 = 0.0;
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
    double numerator = count * sumXY - sumX * sumY;
    double denominator = Math.sqrt((count * sumX2 - sumX * sumX) * (count * sumY2 - sumY * sumY));
    return denominator > 0.0 ? numerator / denominator : 0.0;
  }
}
