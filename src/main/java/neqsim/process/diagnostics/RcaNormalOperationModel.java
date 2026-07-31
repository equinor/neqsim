package neqsim.process.diagnostics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normal-only, operating-regime-aware statistical model for process diagnostics.
 *
 * <p>
 * Training accepts only windows supplied by the caller as normal. No fault labels or faulty examples are used. Each
 * regime stores signal means, standard deviations, ranges, lag-one correlations and the full Pearson correlation
 * matrix. Test windows are matched by standardized Euclidean distance over their operating-condition coordinates.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class RcaNormalOperationModel implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  private static final double EPSILON = 1.0e-12;

  private final Map<String, RegimeStatistics> regimes;
  private final Map<String, Double> conditionScales;
  private final List<String> signalNames;

  private RcaNormalOperationModel(Map<String, RegimeStatistics> regimes, Map<String, Double> conditionScales,
      List<String> signalNames) {
    this.regimes = Collections.unmodifiableMap(new LinkedHashMap<String, RegimeStatistics>(regimes));
    this.conditionScales = Collections.unmodifiableMap(new LinkedHashMap<String, Double>(conditionScales));
    this.signalNames = Collections.unmodifiableList(new ArrayList<String>(signalNames));
  }

  /**
   * Fits a normal-operation model.
   *
   * @param normalWindows windows known to represent normal operation
   * @return fitted model
   */
  public static RcaNormalOperationModel fit(List<RcaProcessWindow> normalWindows) {
    if (normalWindows == null || normalWindows.isEmpty()) {
      throw new IllegalArgumentException("normalWindows must not be empty");
    }

    List<String> signalNames = normalWindows.get(0).getSignalNames();
    java.util.Set<String> operatingConditionNames = normalWindows.get(0).getOperatingConditions().keySet();
    Map<String, List<RcaProcessWindow>> grouped = new LinkedHashMap<String, List<RcaProcessWindow>>();
    for (RcaProcessWindow window : normalWindows) {
      if (window == null) {
        throw new IllegalArgumentException("normalWindows must not contain null");
      }
      if (!signalNames.equals(window.getSignalNames())) {
        throw new IllegalArgumentException("all normal windows must contain the same ordered signal schema");
      }
      if (!operatingConditionNames.equals(window.getOperatingConditions().keySet())) {
        throw new IllegalArgumentException("all normal windows must contain the same operating-condition schema");
      }
      List<RcaProcessWindow> regimeWindows = grouped.get(window.getRegimeId());
      if (regimeWindows == null) {
        regimeWindows = new ArrayList<RcaProcessWindow>();
        grouped.put(window.getRegimeId(), regimeWindows);
      }
      regimeWindows.add(window);
    }

    Map<String, RegimeStatistics> regimes = new LinkedHashMap<String, RegimeStatistics>();
    for (Map.Entry<String, List<RcaProcessWindow>> entry : grouped.entrySet()) {
      regimes.put(entry.getKey(), RegimeStatistics.calculate(entry.getKey(), entry.getValue(), signalNames));
    }
    Map<String, Double> scales = calculateConditionScales(regimes);
    return new RcaNormalOperationModel(regimes, scales, signalNames);
  }

  private static Map<String, Double> calculateConditionScales(Map<String, RegimeStatistics> regimes) {
    Map<String, Double> scales = new LinkedHashMap<String, Double>();
    for (RegimeStatistics regime : regimes.values()) {
      for (String condition : regime.operatingConditions.keySet()) {
        if (!scales.containsKey(condition)) {
          double min = Double.POSITIVE_INFINITY;
          double max = Double.NEGATIVE_INFINITY;
          for (RegimeStatistics candidate : regimes.values()) {
            Double value = candidate.operatingConditions.get(condition);
            if (value != null) {
              min = Math.min(min, value.doubleValue());
              max = Math.max(max, value.doubleValue());
            }
          }
          double scale = max - min;
          scales.put(condition, Double.valueOf(scale > EPSILON ? scale : 1.0));
        }
      }
    }
    return scales;
  }

  /**
   * Returns the known normal regime identifiers.
   *
   * @return unmodifiable regime list
   */
  public List<String> getRegimeIds() {
    return Collections.unmodifiableList(new ArrayList<String>(regimes.keySet()));
  }

  /**
   * Returns the ordered signal schema.
   *
   * @return unmodifiable signal-name list
   */
  public List<String> getSignalNames() {
    return signalNames;
  }

  /**
   * Calculates condition-specific statistical, temporal and correlation evidence for a test window.
   *
   * @param window test window
   * @return evidence report
   */
  public RcaEvidence analyze(RcaProcessWindow window) {
    validateWindow(window);
    RegimeMatch match = matchRegime(window);
    RegimeStatistics normal = match.statistics;
    Map<String, RcaEvidence.SignalEvidence> signalEvidence = new LinkedHashMap<String, RcaEvidence.SignalEvidence>();
    double overallAnomaly = 0.0;

    for (String signalName : signalNames) {
      double[] values = window.getSignalInternal(signalName);
      SignalStatistics observed = SignalStatistics.calculate(values);
      SignalStatistics reference = normal.signals.get(signalName);
      double meanZ = (observed.mean - reference.mean) / reference.safeStandardDeviation();
      double logVarianceRatio = Math.log((observed.variance + EPSILON) / (reference.variance + EPSILON));
      double logRangeRatio = Math.log((observed.range + EPSILON) / (reference.range + EPSILON));
      double normalizedSlope = observed.slope * Math.max(1.0, values.length - 1.0) / reference.safeStandardDeviation();
      double lagDifference = observed.lagOneCorrelation - reference.lagOneCorrelation;

      RcaEvidence.SignalEvidence evidence = new RcaEvidence.SignalEvidence(signalName, observed.mean, reference.mean,
          meanZ, logVarianceRatio, logRangeRatio, normalizedSlope, lagDifference);
      signalEvidence.put(signalName, evidence);
      overallAnomaly = Math.max(overallAnomaly, Math.abs(meanZ) / 3.0);
      overallAnomaly = Math.max(overallAnomaly, Math.abs(logVarianceRatio) / Math.log(4.0));
      overallAnomaly = Math.max(overallAnomaly, Math.abs(logRangeRatio) / Math.log(4.0));
      overallAnomaly = Math.max(overallAnomaly, Math.abs(normalizedSlope) / 3.0);
      overallAnomaly = Math.max(overallAnomaly, Math.abs(lagDifference) / 0.5);
    }

    List<RcaEvidence.CorrelationEvidence> correlations = new ArrayList<RcaEvidence.CorrelationEvidence>();
    for (int i = 0; i < signalNames.size(); i++) {
      for (int j = i + 1; j < signalNames.size(); j++) {
        String first = signalNames.get(i);
        String second = signalNames.get(j);
        double observedCorrelation = correlation(window.getSignalInternal(first), window.getSignalInternal(second));
        double normalCorrelation = normal.correlations[i][j];
        RcaEvidence.CorrelationEvidence evidence = new RcaEvidence.CorrelationEvidence(first, second, normalCorrelation,
            observedCorrelation);
        correlations.add(evidence);
        overallAnomaly = Math.max(overallAnomaly, Math.abs(evidence.getDifference()) / 0.5);
      }
    }

    return new RcaEvidence(normal.regimeId, match.distance, signalEvidence, correlations, overallAnomaly);
  }

  private void validateWindow(RcaProcessWindow window) {
    if (window == null) {
      throw new IllegalArgumentException("window must not be null");
    }
    if (!signalNames.equals(window.getSignalNames())) {
      throw new IllegalArgumentException("test window signal schema differs from the normal model");
    }
  }

  private RegimeMatch matchRegime(RcaProcessWindow window) {
    RegimeStatistics best = null;
    double bestDistance = Double.POSITIVE_INFINITY;
    for (RegimeStatistics candidate : regimes.values()) {
      double distanceSquared = 0.0;
      int coordinates = 0;
      for (Map.Entry<String, Double> condition : candidate.operatingConditions.entrySet()) {
        Double observed = window.getOperatingConditions().get(condition.getKey());
        if (observed == null) {
          throw new IllegalArgumentException("test window is missing operating condition " + condition.getKey());
        }
        double scale = conditionScales.get(condition.getKey()).doubleValue();
        double delta = (observed.doubleValue() - condition.getValue().doubleValue()) / scale;
        distanceSquared += delta * delta;
        coordinates++;
      }
      double distance = coordinates == 0 ? 0.0 : Math.sqrt(distanceSquared);
      if (distance < bestDistance) {
        best = candidate;
        bestDistance = distance;
      }
    }
    return new RegimeMatch(best, bestDistance);
  }

  private static double correlation(double[] first, double[] second) {
    double firstMean = mean(first);
    double secondMean = mean(second);
    double covariance = 0.0;
    double firstSquares = 0.0;
    double secondSquares = 0.0;
    for (int i = 0; i < first.length; i++) {
      double firstDelta = first[i] - firstMean;
      double secondDelta = second[i] - secondMean;
      covariance += firstDelta * secondDelta;
      firstSquares += firstDelta * firstDelta;
      secondSquares += secondDelta * secondDelta;
    }
    double denominator = Math.sqrt(firstSquares * secondSquares);
    return denominator <= EPSILON ? 0.0 : covariance / denominator;
  }

  private static double mean(double[] values) {
    double sum = 0.0;
    for (double value : values) {
      sum += value;
    }
    return sum / values.length;
  }

  private static final class RegimeMatch {
    private final RegimeStatistics statistics;
    private final double distance;

    private RegimeMatch(RegimeStatistics statistics, double distance) {
      this.statistics = statistics;
      this.distance = distance;
    }
  }

  private static final class RegimeStatistics implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    private final String regimeId;
    private final Map<String, Double> operatingConditions;
    private final Map<String, SignalStatistics> signals;
    private final double[][] correlations;

    private RegimeStatistics(String regimeId, Map<String, Double> operatingConditions,
        Map<String, SignalStatistics> signals, double[][] correlations) {
      this.regimeId = regimeId;
      this.operatingConditions = operatingConditions;
      this.signals = signals;
      this.correlations = correlations;
    }

    private static RegimeStatistics calculate(String regimeId, List<RcaProcessWindow> windows,
        List<String> signalNames) {
      Map<String, Double> conditions = calculateConditionMeans(windows);
      Map<String, SignalStatistics> signals = new LinkedHashMap<String, SignalStatistics>();
      Map<String, double[]> concatenated = new LinkedHashMap<String, double[]>();
      for (String signalName : signalNames) {
        int length = 0;
        for (RcaProcessWindow window : windows) {
          length += window.getSampleCount();
        }
        double[] values = new double[length];
        int offset = 0;
        for (RcaProcessWindow window : windows) {
          double[] source = window.getSignalInternal(signalName);
          System.arraycopy(source, 0, values, offset, source.length);
          offset += source.length;
        }
        concatenated.put(signalName, values);
        signals.put(signalName, SignalStatistics.calculate(values));
      }

      double[][] correlations = new double[signalNames.size()][signalNames.size()];
      for (int i = 0; i < signalNames.size(); i++) {
        correlations[i][i] = 1.0;
        for (int j = i + 1; j < signalNames.size(); j++) {
          double value = correlation(concatenated.get(signalNames.get(i)), concatenated.get(signalNames.get(j)));
          correlations[i][j] = value;
          correlations[j][i] = value;
        }
      }
      return new RegimeStatistics(regimeId, Collections.unmodifiableMap(conditions),
          Collections.unmodifiableMap(signals), correlations);
    }

    private static Map<String, Double> calculateConditionMeans(List<RcaProcessWindow> windows) {
      Map<String, Double> sums = new LinkedHashMap<String, Double>();
      Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
      for (RcaProcessWindow window : windows) {
        for (Map.Entry<String, Double> condition : window.getOperatingConditions().entrySet()) {
          Double sum = sums.get(condition.getKey());
          Integer count = counts.get(condition.getKey());
          sums.put(condition.getKey(),
              Double.valueOf((sum == null ? 0.0 : sum.doubleValue()) + condition.getValue().doubleValue()));
          counts.put(condition.getKey(), Integer.valueOf(count == null ? 1 : count.intValue() + 1));
        }
      }
      Map<String, Double> means = new LinkedHashMap<String, Double>();
      for (Map.Entry<String, Double> sum : sums.entrySet()) {
        means.put(sum.getKey(), Double.valueOf(sum.getValue().doubleValue() / counts.get(sum.getKey()).intValue()));
      }
      return means;
    }
  }

  private static final class SignalStatistics implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    private final double mean;
    private final double variance;
    private final double standardDeviation;
    private final double range;
    private final double slope;
    private final double lagOneCorrelation;

    private SignalStatistics(double mean, double variance, double standardDeviation, double range, double slope,
        double lagOneCorrelation) {
      this.mean = mean;
      this.variance = variance;
      this.standardDeviation = standardDeviation;
      this.range = range;
      this.slope = slope;
      this.lagOneCorrelation = lagOneCorrelation;
    }

    private static SignalStatistics calculate(double[] values) {
      double mean = mean(values);
      double squares = 0.0;
      double min = Double.POSITIVE_INFINITY;
      double max = Double.NEGATIVE_INFINITY;
      for (double value : values) {
        double delta = value - mean;
        squares += delta * delta;
        min = Math.min(min, value);
        max = Math.max(max, value);
      }
      double variance = squares / Math.max(1.0, values.length - 1.0);
      double slopeNumerator = 0.0;
      double slopeDenominator = 0.0;
      double timeMean = (values.length - 1.0) / 2.0;
      for (int i = 0; i < values.length; i++) {
        double timeDelta = i - timeMean;
        slopeNumerator += timeDelta * (values[i] - mean);
        slopeDenominator += timeDelta * timeDelta;
      }
      double slope = slopeDenominator <= EPSILON ? 0.0 : slopeNumerator / slopeDenominator;
      double[] first = new double[values.length - 1];
      double[] second = new double[values.length - 1];
      System.arraycopy(values, 0, first, 0, first.length);
      System.arraycopy(values, 1, second, 0, second.length);
      double lagOne = correlation(first, second);
      return new SignalStatistics(mean, variance, Math.sqrt(Math.max(0.0, variance)), max - min, slope, lagOne);
    }

    private double safeStandardDeviation() {
      return Math.max(standardDeviation, Math.max(Math.abs(mean) * 1.0e-9, 1.0e-9));
    }
  }
}
